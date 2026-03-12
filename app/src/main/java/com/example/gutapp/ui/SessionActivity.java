package com.example.gutapp.ui;

import androidx.appcompat.app.AppCompatActivity;

import com.example.gutapp.session.NetworkClient;
import com.example.gutapp.session.SessionCallback;

public abstract class SessionActivity extends AppCompatActivity implements SessionCallback {
    @Override
    protected void onResume() {
        super.onResume();
        NetworkClient.getInstance(this).getSessionManager().setCallback(this);
    }


}
