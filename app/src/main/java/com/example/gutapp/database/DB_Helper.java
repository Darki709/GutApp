package com.example.gutapp.database;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

import androidx.annotation.Nullable;

public class DB_Helper extends SQLiteOpenHelper {
    private static final String DB_NAME = "Gut.db";
    private static final int DB_VERSION = 1;

    private static DB_Helper instance;


    public static final String DB_LOG_TAG = "GutDB";

    private final String[] table_initialize_query = {
        StockDataHelper.createTable(), LastFetchCacheHelper.createTable()
    };

    public static synchronized DB_Helper getInstance(@Nullable Context context) {
        if (instance == null) {
            if (context == null) {
                throw new IllegalStateException("Context cannot be null");
            }
            instance = new DB_Helper(context.getApplicationContext());
        }
        return instance;
    }

    private DB_Helper(@Nullable Context context) {
        super(context, DB_NAME, null, DB_VERSION);
        Log.i(DB_LOG_TAG, "db helper created");
    }


    @Override
    public void onCreate(SQLiteDatabase sqLiteDatabase) {

        Log.i(DB_LOG_TAG, "start create db");
        for(String query : table_initialize_query){
            try {
                sqLiteDatabase.execSQL(query);
            }
            catch (Exception e){
                Log.e(DB_LOG_TAG, "error running query " + query + " error:" + e.getMessage());
                throw e;
            }
        }
        Log.i(DB_LOG_TAG, "end create db");
    }

    @Override
    public void onUpgrade(SQLiteDatabase sqLiteDatabase, int i, int i1) {

    }
}
