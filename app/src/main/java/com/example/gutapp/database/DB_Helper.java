package com.example.gutapp.database;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

import androidx.annotation.Nullable;

public class DB_Helper extends SQLiteOpenHelper {
    private static final String DB_NAME = "Gut.db";
    // Bumped to 4 so existing installs run onUpgrade and get the alert sync columns
    // (uuid/updated_at/dirty/deleted) on the alerts table.
    private static final int DB_VERSION = 4;

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
        if (oldVersion < 4) {
            // Version 3->4: alerts table gains sync metadata columns. Each ALTER is guarded
            // independently so an upgrade path where the column already exists (e.g. a fresh
            // alerts table created by the v2 branch) does not abort the rest.
            String[] alters = {
                "ALTER TABLE " + AlertDBHelper.TABLE_ALERTS + " ADD COLUMN " + AlertDBHelper.COL_UUID + " TEXT",
                "ALTER TABLE " + AlertDBHelper.TABLE_ALERTS + " ADD COLUMN " + AlertDBHelper.COL_UPDATED_AT + " INTEGER NOT NULL DEFAULT 0",
                "ALTER TABLE " + AlertDBHelper.TABLE_ALERTS + " ADD COLUMN " + AlertDBHelper.COL_DIRTY + " INTEGER NOT NULL DEFAULT 0",
                "ALTER TABLE " + AlertDBHelper.TABLE_ALERTS + " ADD COLUMN " + AlertDBHelper.COL_DELETED + " INTEGER NOT NULL DEFAULT 0"
            };
            for (String q : alters) {
                try { db.execSQL(q); }
                catch (Exception e) { Log.w(DB_LOG_TAG, "onUpgrade v4 (column may already exist): " + e.getMessage()); }
            }
            // Backfill identity + stamp so pre-existing alerts get a stable UUID and are
            // pushed to the server on the first sync (dirty=1). randomblob(16) → 32-hex id.
            try {
                db.execSQL("UPDATE " + AlertDBHelper.TABLE_ALERTS +
                        " SET " + AlertDBHelper.COL_UUID + " = lower(hex(randomblob(16)))" +
                        " WHERE " + AlertDBHelper.COL_UUID + " IS NULL OR " + AlertDBHelper.COL_UUID + " = ''");
                db.execSQL("UPDATE " + AlertDBHelper.TABLE_ALERTS +
                        " SET " + AlertDBHelper.COL_UPDATED_AT + " = (CAST(strftime('%s','now') AS INTEGER) * 1000), " +
                        AlertDBHelper.COL_DIRTY + " = 1" +
                        " WHERE " + AlertDBHelper.COL_UPDATED_AT + " = 0");
                Log.i(DB_LOG_TAG, "onUpgrade v4: alerts sync columns added + backfilled");
            } catch (Exception e) {
                Log.e(DB_LOG_TAG, "onUpgrade v4 backfill failed: " + e.getMessage());
            }
        }
    }
}
