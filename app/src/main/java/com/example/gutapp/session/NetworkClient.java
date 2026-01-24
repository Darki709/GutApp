package com.example.gutapp.session;

import android.content.Context;

public class NetworkClient {
    private static NetworkClient instance;
    private SessionManager sessionManager;
    private Thread networkThread;
    private Context appContext;

    private NetworkClient(Context context) {
        this.appContext = context.getApplicationContext();
    }

    public static synchronized NetworkClient getInstance(Context context) {
        if (instance == null) {
            instance = new NetworkClient(context);
        }
        return instance;
    }

    public SessionManager getSessionManager(){
        return this.sessionManager;
    }

    public synchronized void start(SessionCallback cb) {
        // SAFETY CHECK: If the thread is already alive, don't start another!
        if (networkThread != null && networkThread.isAlive()) {
            return;
        }

        sessionManager = new SessionManager(appContext, cb);
        networkThread = new Thread(sessionManager);
        networkThread.setName("PrimaryNetworkThread");
        networkThread.start();
    }

    public synchronized void stop() {
        if (sessionManager != null) {
            sessionManager.stop(); // Sets volatile running = false
        }
        if (networkThread != null) {
            networkThread.interrupt();
        }
    }
}
