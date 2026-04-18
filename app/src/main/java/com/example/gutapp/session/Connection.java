package com.example.gutapp.session;

import android.util.Log;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;


//class to wrap the socket api
public class Connection implements AutoCloseable{
    public static final String NETWORK_LOG_TAG = "GutNetwork";
    final static int PORT = 6767;
    final static String SERVER_IP = "192.168.1.5"; //make sure you connect to the right host when running
    private Socket socket;

    private DataInputStream incoming;
    private DataOutputStream outgoing;

    public Connection() throws IOException {
        try {
            socket = new Socket(SERVER_IP, PORT);
            Log.i(NETWORK_LOG_TAG, "Connected to server");
            incoming = new DataInputStream(socket.getInputStream());
            outgoing = new DataOutputStream(socket.getOutputStream());
        } catch (IOException e) {
            Log.e(NETWORK_LOG_TAG, "Error connecting to server: " + e.getMessage());
            throw e;
        }
    }

    //expects a framed message in raw bytes (encrypted if tunnel has started)
    public void send(byte[] data) throws IOException { //sends in network byte order big endian
        if(socket == null || socket.isClosed())
            throw new IOException("Server is not connected");

        outgoing.write(data);
        outgoing.flush();
        Log.i(NETWORK_LOG_TAG, "Sent message to server");
    }

    //receives raw bytes of a message from server message should be unframed and decrypted depending on the flag
    public byte[] receive() throws IOException { //receives in network byte order big endian
        if(socket == null || socket.isClosed())
            throw new IOException("Server is not connected");

        //read four byte length prefix
        int length = incoming.readInt();

        //read rest of the response, blocks until full response is received
        //server sends responses sequentially so no more than one response is expected at a time
        byte[] payload = new byte[length];
        incoming.readFully(payload);
        Log.i(NETWORK_LOG_TAG, "Recieved a message from server");
        return payload;
    }

    public void reconnect() //session manager will use this to reconnect to server if connection fails mid runtime.
    {
        try {
            if(socket != null && !socket.isClosed()){ //in case the session manager called reconnect while the connection is still alive
                socket.close();
            }
            socket = new Socket(SERVER_IP, PORT);
            Log.i(NETWORK_LOG_TAG, "Reconnected to server");
            incoming = new DataInputStream(socket.getInputStream());
            outgoing = new DataOutputStream(socket.getOutputStream());
        } catch (IOException e) {
            Log.e(NETWORK_LOG_TAG, "Error connecting to server: " + e.getMessage());
            throw new RuntimeException(e);
        }
    }

    @Override
    public void close() {
        try {
            if (socket != null && !socket.isClosed()) {
                socket.shutdownOutput();
                if (outgoing != null) outgoing.close();
                if (incoming != null) incoming.close();
                socket.close();
            }
        } catch (IOException e) {
            Log.e(NETWORK_LOG_TAG, "Error closing connection: " + e.getMessage());
        }
    }




}
