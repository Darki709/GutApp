package com.example.gutapp.data;


import android.content.Context;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.gutapp.data.drawing.DrawingPersistence;
import com.example.gutapp.data.indicators.PresetRepository;
import com.example.gutapp.database.AlertDBHelper;
import com.example.gutapp.database.DB_Helper;

import java.util.concurrent.atomic.AtomicReference;

//utility class to store logged in user data for global access across the app
 public class UserGlobals {
    public static String USER_NAME;
    public static boolean LOGGED_IN = false;
    private static MutableLiveData<Double> BALANCE = new MutableLiveData<>(0.0);

    public static LiveData<Double> getBalance() {
        return BALANCE;
    }

    public static void updateBalance(double delta) {
        // Since LiveData doesn't have "getAndUpdate", we fetch, calculate, and post.
        // We use getValue() to get the current balance.
        Double currentBalance = BALANCE.getValue();
        if (currentBalance == null) currentBalance = 0.0;

        // Use postValue() because your socket is likely on a background thread.
        // This is internally thread-safe for Android.
        BALANCE.postValue(currentBalance + delta);
    }

    public static void setBalance(double balance) {
        BALANCE.postValue(balance);
    }

    public static void clearUserData(Context context) {
        context.getSharedPreferences(PresetRepository.PREFS_NAME, Context.MODE_PRIVATE).edit().clear().apply();
        context.getSharedPreferences(DrawingPersistence.PREFS_NAME, Context.MODE_PRIVATE).edit().clear().apply();
        AlertDBHelper alertDB = new AlertDBHelper(DB_Helper.getInstance(context));
        alertDB.clear();
    }
}
