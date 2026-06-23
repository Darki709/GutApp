package com.example.gutapp.data;


import android.content.Context;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.gutapp.database.AlertDBHelper;
import com.example.gutapp.database.ChartStateDao;
import com.example.gutapp.database.ChartStateMigration;
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
        // Drawings + indicators + presets now live in the SQLite cache (synced per-user with the
        // server, which is the source of truth and re-populates on next login). Wipe the local
        // cache + any legacy-prefs residue so a different user on this device starts clean.
        new ChartStateDao(DB_Helper.getInstance(context)).clearAll();
        ChartStateMigration.clearLegacyPrefs(context);
        AlertDBHelper alertDB = new AlertDBHelper(DB_Helper.getInstance(context));
        alertDB.clear();
    }
}
