package com.example.gutapp.session;

public interface SessionCallback {
    // Delivers a parsed message from the server (Market Data, Orders, etc.)
    void onDataReceived(int msgType, Object parsedData);

    //
    void onActionRequired(int actionType);
}
