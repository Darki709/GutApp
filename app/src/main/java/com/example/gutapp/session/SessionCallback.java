package com.example.gutapp.session;

import androidx.annotation.Nullable;

public interface SessionCallback {
    // Delivers a parsed message from the server (Market Data, Orders, etc.)
    void onDataReceived(int msgType, Object parsedData);

    //
    void onActionRequired(int actionType, @Nullable Object data);
}
