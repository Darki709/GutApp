package com.example.gutapp.session;

import android.content.Context;

import lombok.Getter;

public class NetworkClient {
    private static NetworkClient instance;

    @Getter
    private SessionManager sessionManager;
    private Thread networkThread;
    private Context appContext;


    private NetworkClient(Context context) {
        this.appContext = context.getApplicationContext();
        sessionManager = new SessionManager(appContext);
    }

    public static synchronized NetworkClient getInstance(Context context) {
        if (instance == null) {
            instance = new NetworkClient(context);
        }
        return instance;
    }

    public synchronized void start() {
        // SAFETY CHECK: If the thread is already alive, don't start another!
        if (networkThread != null && networkThread.isAlive()) {
            return;
        }

        networkThread = new Thread(sessionManager);
        networkThread.setName("PrimaryNetworkThread");
        networkThread.start();
    }

    public synchronized void stop() {
        if (sessionManager != null) {
            sessionManager.stop(); // Sets volatile running = false
            instance = null;
        }
        if (networkThread != null) {
            networkThread.interrupt();
            networkThread = null;
        }
    }
}
