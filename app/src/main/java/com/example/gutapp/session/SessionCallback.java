package com.example.gutapp.session;

import androidx.annotation.Nullable;

public interface SessionCallback {
    // Delivers a parsed message from the server (Market Data, Orders, etc.)
    void onDataReceived(DataType msgType, Object parsedData);

    // Asks the UI to perform an action (e.g., show a login screen)
    void onActionRequired(int actionType, @Nullable Object data);
}
