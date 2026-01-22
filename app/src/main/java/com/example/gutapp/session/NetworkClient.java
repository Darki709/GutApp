package com.example.gutapp.session;

public class NetworkClient {
    private static NetworkClient instance;
    private SessionManager sessionManager;
    private Thread networkThread;

    private NetworkClient() {}

    public static synchronized NetworkClient getInstance() {

        if (instance == null) {
            instance = new NetworkClient();
        }
        return instance;
    }

    public synchronized void start() {
        // SAFETY CHECK: If the thread is already alive, don't start another!
        if (networkThread != null && networkThread.isAlive()) {
            return;
        }

        sessionManager = new SessionManager();
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
