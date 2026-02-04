package com.example.gutapp.session;


import static com.example.gutapp.session.Connection.NETWORK_LOG_TAG;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;

import com.example.gutapp.session.Requests.HandShakeHello;
import com.example.gutapp.session.Requests.HandShakeVerify;
import com.example.gutapp.session.Requests.LoginRequest;
import com.example.gutapp.session.Requests.RegisterRequest;
import com.example.gutapp.session.Responses.HandShakeHelloResponse;
import com.example.gutapp.session.Responses.HandShakeVerifyResponse;
import com.example.gutapp.session.Responses.LoginResponse;
import com.example.gutapp.session.Responses.RegisterResponse;

import java.io.IOException;
import java.lang.ref.ReferenceQueue;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

public class SessionManager implements Runnable {
    Connection connection; //connection to server, holding the socket
    Queue<Request> outgoinQueue; //this is where the other threads put requests for the server
    private volatile boolean running = true;
    private volatile boolean working = false;

    private CryptoUtility.CryptoContext ctx; // holds the encryption data
    private Context appContext; //needed for shared preference

    private final Object callbackLock = new Object(); // Prevents race conditions
    private SessionCallback currentCallback;        // The currently active Activity
    private final Handler uiHandler = new Handler(Looper.getMainLooper()); // The UI bridge

    private final BlockingQueue<AuthStruct> authQueue = new LinkedBlockingQueue<>();

    private LinkedBlockingQueue<AsyncRequest> requestQueue;
    private ConcurrentHashMap<Integer, AsyncRequest> pendingRequests;

    private Thread sendThread = null;
    private Thread recvThread = null;

    public class AuthStruct {
        public String username;
        public String password;

        public int mode; //0 register, 1 login

        public AuthStruct(String username, String password, int mode) {
            this.username = username;
            this.password = password;
            this.mode = mode;
        }
    }

    private static final String PREF_NAME = "gut_session_prefs";
    private static final String KEY_USER = "saved_username";
    private static final String KEY_PASS = "saved_password";



    //callback response types

    // Actions: Manager asking the Activity for help
    public static final int ACTION_SHOW_LOGIN_UI = 1;

    // Data Types: Manager delivering results to the Activity
    public static final int TYPE_ERROR = -1;
    public static final int TYPE_REGISTER_ERROR = -2;
    public static final int TYPE_LOGIN_ERROR = -3;
    public static final int TYPE_AUTH_SUCCESS = 100;
    public static final int TYPE_MARKET_DATA = 101;



    public SessionManager(Context context, SessionCallback cb) {
        this.outgoinQueue = new ArrayDeque<Request>();
        this.ctx = new CryptoUtility.CryptoContext();
        this.appContext = context;
        this.setCallback(cb);
    }

    @Override
    public void run() {
        try {
            while (running && !Thread.currentThread().isInterrupted()) {
                try {
                    //make sure that any thread is closed before reconnecting
                    this.working = false;
                    if (sendThread != null) sendThread.interrupt();
                    if (recvThread != null) recvThread.interrupt();


                    Log.i(NETWORK_LOG_TAG, "Connecting to server");
                    //create a tcp connection
                    if (connection != null) connection.close();
                    connection = new Connection();

                    //perform key exchange with the server
                    performHandshake();

                    //check shared preferences for user credentials
                    int result = tryAutoLogin();

                    //if authentication fails try to login manually
                    while (result != 0 && running && !Thread.currentThread().isInterrupted()) {
                        //tell activity to show login ui
                        notifyUI(cb -> cb.onActionRequired(ACTION_SHOW_LOGIN_UI));
                        AuthStruct authStruct = authQueue.take();
                        byte[] buffer;
                        if (authStruct.mode == 0) {
                            //register
                            RegisterRequest registerRequest = new RegisterRequest(authStruct.username, authStruct.password);
                            byte[] registerRequestBytes = encrypt(registerRequest.getBytes());
                            debugLogEncryptedMessage(NETWORK_LOG_TAG, registerRequestBytes);
                            connection.send(registerRequestBytes);
                            buffer = connection.receive();
                            buffer = unframe(buffer);
                            debugLogResponseDecrypted(NETWORK_LOG_TAG, buffer);
                            RegisterResponse registerResponse = new RegisterResponse(buffer);
                            if (registerResponse.getState() != RegisterResponse.SUCCESS) {
                                notifyUI(cb -> cb.onDataReceived(TYPE_REGISTER_ERROR, registerResponse.getFeedback()));
                                continue; //user needs to check register credentials so thread must wait for more input
                            }
                        }
                        LoginRequest loginRequest = new LoginRequest(authStruct.username, authStruct.password);
                        byte[] loginRequestBytes = loginRequest.getBytes();
                        debugLogMessage(NETWORK_LOG_TAG, loginRequestBytes);
                        connection.send(encrypt(loginRequestBytes));
                        buffer = connection.receive();
                        buffer = unframe(buffer);
                        debugLogResponseDecrypted(NETWORK_LOG_TAG, buffer);
                        LoginResponse loginResponse = new LoginResponse(buffer);
                        try {
                            loginRequest.handle(loginResponse);
                        } catch (Exception e) {
                            Log.e(NETWORK_LOG_TAG, "Error logging in: " + e.getMessage());
                        }
                        if (loginResponse.getState() != LoginResponse.LoginState.SUCCESS) {
                            notifyUI(cb -> cb.onDataReceived(TYPE_LOGIN_ERROR, loginResponse.getState() == LoginResponse.LoginState.INVALIDUSER ? "no such user" : "wrong password"));
                        } else {
                            result = 0;
                            saveCredentials(authStruct.username, authStruct.password);
                        }
                    }
                    //notify login page that login is successful
                    notifyUI(cb -> cb.onDataReceived(TYPE_AUTH_SUCCESS, null));


                    //start sending and receiving messages
                    work();

                } catch (IOException e) {
                    Log.i(NETWORK_LOG_TAG, "Error connecting to server: " + e + " retrying in 5 seconds");
                    try {
                        Thread.sleep(5000);
                    } catch (InterruptedException ex) {
                        Thread.currentThread().interrupt();
                    }
                } catch (RuntimeException e) {
                    Log.e(NETWORK_LOG_TAG, "Error connecting to server: " + e.getStackTrace());
                } catch (NoSuchAlgorithmException e) {
                    Log.e(NETWORK_LOG_TAG, "cannot find encryption/decryption algorithm: " + e);
                    this.running = false;
                    Thread.currentThread().interrupt();
                    //terminates network thread due to a fatal system requirements error
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        finally {
            if (connection != null) connection.close();
        }
    }

    //orchestrates the handshake with the server sends and receives the handshake requests and blocks until it is completed
    //if handshake fails the server sever's the connection and the session manager will reconnect to the server and try to perform key exchange again
    private void performHandshake() throws NoSuchAlgorithmException, IOException, RuntimeException {
        //generate public&private key pair and format nonces (the user might be trying to reconnect and we need new nonces)
        this.ctx = new CryptoUtility.CryptoContext();
        this.ctx.keyPair = CryptoUtility.generateKeyPair();
        //form a handshake request
        Request handshakeRequest = new HandShakeHello(ctx);
        byte[] handshakeRequestBytes = handshakeRequest.getBytes();
        Log.i(NETWORK_LOG_TAG, "Handshake request: ");
        debugLogMessage(NETWORK_LOG_TAG,handshakeRequestBytes);
        //send it
        connection.send(handshakeRequestBytes);
        //wait for a response, if the connection will break an exception with be thrown and everything will be redone
        byte[] buffer = connection.receive();//first byte is the plain text flag
        debugLogResponse(NETWORK_LOG_TAG, buffer);
        buffer = unframe(buffer); //unframe the buffer
        //parse the response
        Response handShakeResponse = new HandShakeHelloResponse(buffer);
        //decrypts the aes key and sets it as the aes key in ctx
        handshakeRequest.handle(handShakeResponse);
        //send a hand shake verify request to get server approval
        Request handShakeVerifyRequest = new HandShakeVerify(ctx);
        byte[] handShakeVerifyRequestBytes = handShakeVerifyRequest.getBytes();
        debugLogMessage(NETWORK_LOG_TAG,handShakeVerifyRequestBytes);
        connection.send(handShakeVerifyRequestBytes);
        buffer = connection.receive();
        debugLogResponse(NETWORK_LOG_TAG, buffer);
        buffer = unframe(buffer);
        debugLogResponseDecrypted(NETWORK_LOG_TAG, buffer);
        Response handShakeVerifyResponse = new HandShakeVerifyResponse(buffer);
        handShakeVerifyRequest.handle(handShakeVerifyResponse);//if failed these throw exception the handshake retries
        Log.i(NETWORK_LOG_TAG, "Handshake successful, secret key is: " + String.format("%0" + (ctx.aesKey.getEncoded().length * 2) + "x", new BigInteger(1, ctx.aesKey.getEncoded())).toUpperCase() + " recv_nonce: " + ctx.recvNonce + " send_nonce: " + ctx.sendNonce);
    }

    //checks the flag and calls a decrypt if needed
    private byte[] unframe(byte[] buffer){
        byte[] unframed = new byte[buffer.length - 1];
        System.arraycopy(buffer, 1, unframed, 0, unframed.length);
        byte flag = buffer[0]; // 0x00 if not encrypted, 0x01 if encrypted
        if(flag == 0x01){
            try {
                unframed = CryptoUtility.decryptAESGCM(unframed, ctx);
            }
            catch (Exception e){
                Log.e(NETWORK_LOG_TAG, "Error decrypting message: " + e.getMessage());
                throw new RuntimeException(e);
            }
        }
        return unframed;
    }

    private byte[] encrypt(byte[] buffer) {
        // 1. Extract the plaintext (everything after the 5-byte header)
        int plaintextLength = buffer.length - 5;
        byte[] plaintext = new byte[plaintextLength];
        System.arraycopy(buffer, 5, plaintext, 0, plaintextLength);

        byte[] encrypted;
        try {
            // GCM encryption: result is plaintext + 16 bytes (Auth Tag)
            encrypted = CryptoUtility.encryptAESGCM(plaintext, ctx);
        } catch (Exception e) {
            throw new RuntimeException("Encryption failed", e);
        }

        // 2. Create a new buffer for the 5-byte header + the NEW encrypted length
        byte[] finalBuffer = new byte[5 + encrypted.length];

        // 3. Copy the existing header (Old Length + Type) to the new buffer
        System.arraycopy(buffer, 0, finalBuffer, 0, 5);

        // 4. Update the 4-byte length field in the new header (indices 0-3)
        // The server needs to know EXACTLY how many bytes to read and decrypt
        int newPayloadSize = encrypted.length+1;
        java.nio.ByteBuffer.wrap(finalBuffer).putInt(0, newPayloadSize);

        // 5. Copy the encrypted data (ciphertext + tag) into the final buffer
        System.arraycopy(encrypted, 0, finalBuffer, 5, encrypted.length);
        Log.i(NETWORK_LOG_TAG, "Encrypted message length: " + finalBuffer.length);
        return finalBuffer;
    }

    //authentication method returns 0 on success
    private int tryAutoLogin() throws IOException{
        SharedPreferences vault = getVault();
        if(vault == null)
            return -1;
        //get cached user credentials
        String username = vault.getString(KEY_USER, null);
        String password = vault.getString(KEY_PASS, null);
        if(username == null || password == null)
            return -1;
        //check if credentials are valid
        LoginRequest loginRequest = new LoginRequest(username, password);
        byte[] loginRequestBytes = loginRequest.getBytes();
        //send bytes
        debugLogMessage(NETWORK_LOG_TAG, loginRequestBytes);
        connection.send(encrypt(loginRequestBytes));
        byte[] buffer = connection.receive();
        buffer = unframe(buffer);
        debugLogResponseDecrypted(NETWORK_LOG_TAG, buffer);
        Response loginResponse = new LoginResponse(buffer);
        try{
            loginRequest.handle(loginResponse);
        }
        catch (Exception e){
            return -1;
        }
        return 0;
    }

    //the loop where the session manager sends and receives messages
    private void work() throws IOException, RuntimeException{
        Log.i(NETWORK_LOG_TAG, "Starting work loop");
        this.requestQueue = new LinkedBlockingQueue<>();
        this.pendingRequests = new ConcurrentHashMap<>();
        this.working = true;
        sendThread = new Thread(this::sendLoop);
        recvThread = new Thread(this::recvLoop);
        sendThread.start();
        recvThread.start();
        while(running && working && !Thread.currentThread().isInterrupted()){
            try {
                Thread.sleep(100); // Check 10 times per second instead of millions
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        if(sendThread != null) sendThread.interrupt();
        if(recvThread != null) recvThread.interrupt();
    }

    private void sendLoop(){
        Log.i(NETWORK_LOG_TAG, "Starting send loop");
        while (running && working && !Thread.currentThread().isInterrupted()){
            AsyncRequest request = null;
            try{
                request = requestQueue.take();
                byte[] buffer = request.getBytes();
                debugLogMessage(NETWORK_LOG_TAG, buffer);
                buffer = encrypt(buffer);
                debugLogEncryptedMessage(NETWORK_LOG_TAG, buffer);
                connection.send(buffer);
                pendingRequests.putIfAbsent(request.getReqId(), request);
            }
            catch (Exception e){
                Log.e(NETWORK_LOG_TAG, "Error sending message: " + e.getMessage());
                if(request != null) request.getCaller().onDataReceived(TYPE_ERROR, e.getMessage());
                this.working = false;
                Thread.currentThread().interrupt();
            }
        }
    }

    private void recvLoop(){
        Log.i(NETWORK_LOG_TAG, "Starting recv loop");
        while (running && working && !Thread.currentThread().isInterrupted()) {
            try{
                byte[] buffer = connection.receive();
                buffer = unframe(buffer);
                //debugLogResponseDecrypted(NETWORK_LOG_TAG, buffer);
                AsyncResponse response = ResponseFactory.createResponse(buffer);
                AsyncRequest request = pendingRequests.get(response.getReqId());
                if(request != null){
                    request.handle(response);
                    if(request.isDone()){
                        pendingRequests.remove(request.getReqId());
                    }
                }
                else {
                    // 3. Log it and move on. Don't let the thread die.
                    Log.w(NETWORK_LOG_TAG, "Discarding packet for unknown ReqID: " + response.getReqId());
                }
            }
             catch (Exception e) {
                Log.e(NETWORK_LOG_TAG, "Error receiving message: " + e.getMessage());
                this.working = false;
                Thread.currentThread().interrupt();
            }
        }
    }

    private SharedPreferences getVault() {
        try {
            MasterKey masterKey = new MasterKey.Builder(this.appContext)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build();

            return EncryptedSharedPreferences.create(
                    this.appContext,
                    PREF_NAME,
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            );
        } catch (Exception e) {
            return null; // Vault failed to open
        }
    }

    private void saveCredentials(String username, String password) {
        // context is passed in via constructor or a setter
        SharedPreferences prefs = getVault();
        if (prefs == null)
        {
            Log.e(NETWORK_LOG_TAG, "Could not save: Vault failed to open.");
            return;
        }

        SharedPreferences.Editor editor = prefs.edit();

        editor.putString(KEY_USER, username);
        editor.putString(KEY_PASS, password);
        // Use apply() for asynchronous saving (better for performance)
        editor.apply();
    }


    public void stop() {
        this.running = false;
    }

    //callback methods
    // Called when an Activity comes to the foreground
    public void setCallback(SessionCallback callback) {
        synchronized (callbackLock) {
            this.currentCallback = callback;
        }
    }

    // Called when an Activity goes to the background
    public void removeCallback() {
        synchronized (callbackLock) {
            this.currentCallback = null;
        }
    }

    private void notifyUI(Consumer<SessionCallback> action) {
        // 1. Move the execution from the Network Thread to the UI Thread
        uiHandler.post(() -> {
            // 2. Lock the callback so it doesn't get removed mid-call
            synchronized (callbackLock) {
                // 3. The "Null Check" - Is an Activity currently attached?
                if (currentCallback != null) {
                    // 4. THE CONNECTION:
                    // We take our Activity (currentCallback) and plug it into
                    // the instruction we sent (action).
                    action.accept(currentCallback);
                }
            }
        });
    }

    public void pushCredentials(String username, String password, int mode) {
        try {
            // We use .put() because it is thread-safe and ensures the message is added.
            // Even if the queue were full (unlikely here), it would wait safely.
            authQueue.put(new AuthStruct(username, password, mode));
        } catch (InterruptedException e) {
            // If the thread is interrupted during the put, reset the interrupt flag
            Thread.currentThread().interrupt();
            Log.e(NETWORK_LOG_TAG, "Failed to push credentials to queue", e);
        }
    }

    public void pushRequest(AsyncRequest request){
        try{
            if(!requestQueue.offer(request)){
                request.getCaller().onDataReceived(TYPE_ERROR, "Failed to send request, retry later");
            }
        }catch (Exception e){
            Log.e(NETWORK_LOG_TAG, "Failed to push request to queue", e);
            request.getCaller().onDataReceived(TYPE_ERROR, e.getMessage());
        }
    }































    //debug function
    public void debugLogMessage(String tag, byte[] data) {
        if (data == null || data.length < 6) {
            Log.e(tag, "Packet too short to be valid! (Length: " + (data == null ? 0 : data.length) + ")");
            return;
        }

        try {
            ByteBuffer buffer = ByteBuffer.wrap(data);

            // Parsing based on your protocol
            int length   = buffer.getInt();       // 4B
            byte flag    = buffer.get();          // 1B
            byte type    = buffer.get();          // 1B
            long reqId    = buffer.getInt() & 0xFFFFFFFFL;       // 4B (Assuming this is inside your payload)

            Log.d(tag, "┌─────────────── NETWORK PACKET ───────────────┐");
            Log.d(tag, String.format("│ Length:  %-34d │", length));
            Log.d(tag, String.format("│ Flag:    0x%02X (%-28s) │", flag, (flag == 0 ? "PLAIN" : "AES-GCM")));
            Log.d(tag, String.format("│ Type:    0x%02X (%-28s) │", type, getMsgTypeName(type)));
            Log.d(tag, String.format("│ ReqID:   %-34d │", reqId));
            Log.d(tag, "├────────────────── PAYLOAD ──────────────────┤");

            // Print Payload Hex in 16-byte rows
            byte[] payload = new byte[buffer.remaining()];
            buffer.get(payload);

            StringBuilder hexRow = new StringBuilder();
            for (int i = 0; i < payload.length; i++) {
                hexRow.append(String.format("%02X ", payload[i]));
                if ((i + 1) % 16 == 0) {
                    Log.d(tag, "│ " + hexRow.toString() + " │");
                    hexRow.setLength(0);
                }
            }
            // Print remaining bytes
            if (hexRow.length() > 0) {
                Log.d(tag, String.format("│ %-47s │", hexRow.toString()));
            }
            Log.d(tag, "└─────────────────────────────────────────────┘");

        } catch (Exception e) {
            Log.e(tag, "Error parsing debug message: " + e.getMessage());
        }
    }

    public void debugLogResponse(String tag, byte[] data) {
        if (data == null || data.length < 5) {
            Log.e(tag, "Packet too short to be valid! (Length: " + (data == null ? 0 : data.length) + ")");
            return;
        }

        try {
            ByteBuffer buffer = ByteBuffer.wrap(data);

            // Parsing based on your protocol
            byte flag    = buffer.get();          // 1B
            byte type    = buffer.get();          // 1B

            Log.d(tag, "┌─────────────── NETWORK PACKET ───────────────┐");
            Log.d(tag, String.format("│ Flag:    0x%02X (%-28s) │", flag, (flag == 0 ? "PLAIN" : "AES-GCM")));
            Log.d(tag, String.format("│ Type:    0x%02X (%-28s) │", type, getMsgTypeName(type)));
            Log.d(tag, "├────────────────── PAYLOAD ──────────────────┤");

            // Print Payload Hex in 16-byte rows
            byte[] payload = new byte[buffer.remaining()];
            buffer.get(payload);

            StringBuilder hexRow = new StringBuilder();
            for (int i = 0; i < payload.length; i++) {
                hexRow.append(String.format("%02X ", payload[i]));
                if ((i + 1) % 16 == 0) {
                    Log.d(tag, "│ " + hexRow.toString() + " │");
                    hexRow.setLength(0);
                }
            }
            // Print remaining bytes
            if (hexRow.length() > 0) {
                Log.d(tag, String.format("│ %-47s │", hexRow.toString()));
            }
            Log.d(tag, "└─────────────────────────────────────────────┘");

        } catch (Exception e) {
            Log.e(tag, "Error parsing debug message: " + e.getMessage());
        }
    }

    public void debugLogResponseDecrypted(String tag, byte[] data) {
        if (data == null || data.length < 1) {
            Log.e(tag, "Packet too short to be valid! (Length: " + (data == null ? 0 : data.length) + ")");
            return;
        }

        try {
            ByteBuffer buffer = ByteBuffer.wrap(data);

            // Parsing based on your protocol
            byte type    = buffer.get();          // 1B

            Log.d(tag, "┌─────────────── NETWORK PACKET ───────────────┐");
            Log.d(tag, String.format("│ Type:    0x%02X (%-28s) │", type, getMsgTypeName(type)));
            Log.d(tag, "├────────────────── PAYLOAD ──────────────────┤");

            // Print Payload Hex in 16-byte rows
            byte[] payload = new byte[buffer.remaining()];
            buffer.get(payload);

            StringBuilder hexRow = new StringBuilder();
            for (int i = 0; i < payload.length; i++) {
                hexRow.append(String.format("%02X ", payload[i]));
                if ((i + 1) % 16 == 0) {
                    Log.d(tag, "│ " + hexRow.toString() + " │");
                    hexRow.setLength(0);
                }
            }
            // Print remaining bytes
            if (hexRow.length() > 0) {
                Log.d(tag, String.format("│ %-47s │", hexRow.toString()));
            }
            Log.d(tag, "└─────────────────────────────────────────────┘");

        } catch (Exception e) {
            Log.e(tag, "Error parsing debug message: " + e.getMessage());
        }
    }

    private String getMsgTypeName(byte type) {
        switch(type) {
            case 0: return "HANDSHAKE_HELLO/KEY";
            case 1: return "HANDSHAKE_VERIFY";
            default: return "DATA_PACKET";
        }
    }

    public void debugLogEncryptedMessage(String tag, byte[] data) {
        if (data == null || data.length < 6) {
            Log.e(tag, "Packet too short to be valid! (Length: " + (data == null ? 0 : data.length) + ")");
            return;
        }

        try {
            ByteBuffer buffer = ByteBuffer.wrap(data);

            // Parsing based on your protocol
            int length   = buffer.getInt();       // 4B
            byte flag    = buffer.get();          // 1B

            Log.d(tag, "┌─────────────── NETWORK PACKET ───────────────┐");
            Log.d(tag, String.format("│ Length:  %-34d │", length));
            Log.d(tag, String.format("│ Flag:    0x%02X (%-28s) │", flag, (flag == 0 ? "PLAIN" : "AES-GCM")));
            Log.d(tag, "├────────────────── PAYLOAD ──────────────────┤");

            // Print Payload Hex in 16-byte rows
            byte[] payload = new byte[buffer.remaining()];
            buffer.get(payload);

            StringBuilder hexRow = new StringBuilder();
            for (int i = 0; i < payload.length; i++) {
                hexRow.append(String.format("%02X ", payload[i]));
                if ((i + 1) % 16 == 0) {
                    Log.d(tag, "│ " + hexRow.toString() + " │");
                    hexRow.setLength(0);
                }
            }
            // Print remaining bytes
            if (hexRow.length() > 0) {
                Log.d(tag, String.format("│ %-47s │", hexRow.toString()));
            }
            Log.d(tag, "└─────────────────────────────────────────────┘");

        } catch (Exception e) {
            Log.e(tag, "Error parsing debug message: " + e.getMessage());
        }
    }
}
