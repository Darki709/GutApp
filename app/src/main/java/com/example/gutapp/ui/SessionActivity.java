package com.example.gutapp.ui;

import android.content.Intent;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.example.gutapp.session.NetworkClient;
import com.example.gutapp.session.SessionCallback;
import com.example.gutapp.session.background.NetworkService;

public abstract class SessionActivity extends AppCompatActivity implements SessionCallback {

    @Override
    protected void onStart() {
        super.onStart();
        // Start the foreground service (alert monitoring) if not already running
        Intent serviceIntent = new Intent(this, NetworkService.class);
        startForegroundService(serviceIntent);
    }

    @Override
    protected void onResume() {
        super.onResume();
        NetworkClient.getInstance(this).getSessionManager().setUiCallback(this);
    }

    // in case of a reconnect to the server this will be called
    abstract protected void refreshNetwork();

    /**
     * @Params 0 means network reconnect
     * @Params 1 means lost connection
     */
    @Override
    public void onActionRequired(int actionType, @Nullable Object data) {
        switch (actionType) {
            case 0:
                refreshNetwork();
                break;
            case 1:
                runOnUiThread(() ->
                        Toast.makeText(this, "Lost connection to server, reconnecting now",
                                Toast.LENGTH_SHORT).show());
                break;
        }
    }
}
