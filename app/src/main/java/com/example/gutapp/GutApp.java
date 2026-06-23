package com.example.gutapp;

import android.app.Application;

import com.google.firebase.FirebaseApp;

import lombok.Getter;

public class GutApp extends Application {

    @Getter
    private static GutApp instance;


    @Override
    public void onCreate() {
        super.onCreate();
        FirebaseApp.initializeApp(this);
        instance = this;
    }
}
