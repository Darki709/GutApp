package com.example.gutapp;

import android.app.Application;

import com.example.gutapp.data.alerts.AlertManager;
import com.google.firebase.FirebaseApp;

public class GutApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        FirebaseApp.initializeApp(this);
        // Initialise AlertManager with app context so DB is ready
        // before any Activity or Service calls addAlert().
        AlertManager.getInstance().init(this);
    }
}
