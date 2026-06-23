package com.example.gutapp.ui;

import android.content.Intent;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.example.gutapp.session.NetworkClient;
import com.example.gutapp.session.SessionCallback;
import com.example.gutapp.session.background.NetworkService;


//wrapper to make callback from network to ui easier
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
        Log.d(HomeActivity.HOME_LOG_TAG, "set callback for activity");
    }

    // in case of a reconnect to the server this will be called
    abstract protected void networkReconnect();

    abstract protected void networkDisconnect();

    /**
     * @Params 0 means network reconnect
     * @Params 1 means lost connection
     */
    @Override
    public void onActionRequired(int actionType, @Nullable Object data) {
        Log.d(HomeActivity.HOME_LOG_TAG, "Network called on activity");
        switch (actionType) {
            case 0:
                runOnUiThread(() -> {
                        networkReconnect();
                        Toast.makeText(this, "Connection to server restored",
                                Toast.LENGTH_SHORT).show();
                });
                break;
            case 1:
                runOnUiThread(() ->{
                        networkDisconnect();
                        Toast.makeText(this, "Lost connection to server, reconnecting now",
                                Toast.LENGTH_SHORT).show();
                });
                break;
        }
    }
}
