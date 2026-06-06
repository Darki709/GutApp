package com.example.gutapp.database;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

import androidx.annotation.Nullable;

public class DB_Helper extends SQLiteOpenHelper {
    private static final String DB_NAME = "Gut.db";
    // Bumped to 3 so existing installs run onUpgrade and get the chart_state cache table
    private static final int DB_VERSION = 3;

    private static DB_Helper instance;
    public static final String DB_LOG_TAG = "GutDB";

    private final String[] table_initialize_query = {
        StockDataHelper.createTable(),
        LastFetchCacheHelper.createTable(),
        AlertDBHelper.createTable(),
        ChartStateDao.createTable()
    };

    public static synchronized DB_Helper getInstance(@Nullable Context context) {
        if (instance == null) {
            if (context == null) throw new IllegalStateException("Context cannot be null");
            instance = new DB_Helper(context.getApplicationContext());
        }
        return instance;
    }

    private DB_Helper(@Nullable Context context) {
        super(context, DB_NAME, null, DB_VERSION);
        Log.i(DB_LOG_TAG, "db helper created");
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        Log.i(DB_LOG_TAG, "start create db");
        for (String query : table_initialize_query) {
            try {
                db.execSQL(query);
            } catch (Exception e) {
                Log.e(DB_LOG_TAG, "error running query " + query + " error:" + e.getMessage());
                throw e;
            }
        }
        Log.i(DB_LOG_TAG, "end create db");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2) {
            // Version 1->2: alerts table added
            try {
                db.execSQL(AlertDBHelper.createTable());
                Log.i(DB_LOG_TAG, "onUpgrade v2: alerts table created");
            } catch (Exception e) {
                Log.e(DB_LOG_TAG, "onUpgrade v2 failed: " + e.getMessage());
            }
        }
        if (oldVersion < 3) {
            // Version 2->3: chart_state cache table (drawings + indicators + presets)
            try {
                db.execSQL(ChartStateDao.createTable());
                Log.i(DB_LOG_TAG, "onUpgrade v3: chart_state table created");
            } catch (Exception e) {
                Log.e(DB_LOG_TAG, "onUpgrade v3 failed: " + e.getMessage());
            }
        }
    }
}
