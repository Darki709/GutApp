package com.example.gutapp.ui;

import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.example.gutapp.session.NetworkClient;
import com.example.gutapp.session.SessionCallback;

public abstract class SessionActivity extends AppCompatActivity implements SessionCallback {
    @Override
    protected void onResume() {
        super.onResume();
        NetworkClient.getInstance(this).getSessionManager().setCallback(this);
    }

    //in case pf a reconnect to the server this will be called
    abstract protected void refreshNetwork();


    /*
    * @Params 0 means network reconnect
    * @Params 1 means lost connection
    * */
    @Override
    public void onActionRequired(int actionType, @Nullable Object data){
        switch(actionType){
            case 0:
                refreshNetwork();
                break;
            case 1:
               runOnUiThread( () -> {
                Toast.makeText(this, "Lost connection to server, reconnecting now", Toast.LENGTH_SHORT).show();});
                break;
        }

    }
}
