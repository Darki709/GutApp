package com.example.gutapp.session;


import static com.example.gutapp.session.Connection.NETWORK_LOG_TAG;

import android.util.Log;

import androidx.lifecycle.ViewModelProvider;

import com.example.gutapp.session.Requests.HandShakeHello;
import com.example.gutapp.session.Requests.HandShakeVerify;
import com.example.gutapp.session.Responses.HandShakeHelloResponse;
import com.example.gutapp.session.Responses.HandShakeVerifyResponse;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayDeque;
import java.util.Queue;

public class SessionManager implements Runnable {
    Connection connection; //connection to server
    Queue<Request> outgoinQueue;
    private volatile boolean running = true;

    private CryptoUtility.CryptoContext ctx;


    public SessionManager() {
        this.outgoinQueue = new ArrayDeque<Request>();
        this.ctx = new CryptoUtility.CryptoContext();
    }

    @Override
    public void run() {
        while (running && !Thread.currentThread().isInterrupted()) {
            try{
                Log.i(NETWORK_LOG_TAG, "Connecting to server");
                //create a tcp connection
                connection = new Connection();

                //perform key exchange with the server
                performHandshake();

                //start sending and receiving messages
                work();

            }
            catch (IOException e) {
                Log.i(NETWORK_LOG_TAG, "Error connecting to server: " + e + " retrying in 5 seconds");
                try {
                    Thread.sleep(5000);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                }
            } catch (RuntimeException e) {
                Log.e(NETWORK_LOG_TAG, "Error connecting to server: " + e);
            }
            catch (NoSuchAlgorithmException e) {
                Log.e(NETWORK_LOG_TAG, "cannot find encryption/decryption algorithm: " + e);
                this.running = false;
                Thread.currentThread().interrupt();
                //terminates network thread due to a fatal system requirements error
            }
        }
    }

    //orchestrates the handshake with the server sends and receives the handshake requests and blocks until it is completed
    //if handshake fails the server sever's the connection and the session manager will reconnect to the server and try to perform key exchange again
    private void performHandshake() throws NoSuchAlgorithmException, IOException, RuntimeException {
        //generate public&private key pair
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
        handShakeVerifyRequest.handle(handShakeVerifyResponse);
        Log.i(NETWORK_LOG_TAG, "Handshake successful, secret key is: " + String.format("%0" + (ctx.aesKey.getEncoded().length * 2) + "x", new BigInteger(1, ctx.aesKey.getEncoded())).toUpperCase());
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

    //the loop where the session manager sends and receives messages
    private void work() throws IOException, RuntimeException{
        while(true);
    }


    public void stop() {
        this.running = false;
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
            int reqId    = buffer.getInt();       // 4B (Assuming this is inside your payload)

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
}
