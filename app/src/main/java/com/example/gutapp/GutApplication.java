package com.example.gutapp;

import android.app.Application;
import com.google.firebase.FirebaseApp;

public class GutApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        // This initializes Firebase for the entire app process
        FirebaseApp.initializeApp(this);
    }
}
