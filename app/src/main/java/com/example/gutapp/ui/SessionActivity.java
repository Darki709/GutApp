package com.example.gutapp.ui;

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
    * */
    @Override
    public void onActionRequired(int actionType, @Nullable Object data){
        if(actionType == 0){
        refreshNetwork();}
    }
}
