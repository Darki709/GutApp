package com.example.gutapp.database;

import android.content.ContentValues;
import android.database.Cursor;
import android.util.Log;

import com.example.gutapp.data.alerts.Alert;
import com.example.gutapp.data.alerts.Condition;
import com.google.gson.Gson;

import java.util.ArrayList;
import java.util.List;

public class AlertDBHelper {
    public static final String TABLE_ALERTS = "alerts";
    public static final String COLUMN_ID = "_id";
    public static final String COLUMN_SYMBOL = "symbol";
    public static final String COLUMN_STATUS = "status";
    public static final String COLUMN_CONDITION_NAME = "condition_name";
    public static final String COLUMN_CONDITION_JSON = "condition_json";

    private DB_Helper db_helper;

    private Gson gson;


    public AlertDBHelper(DB_Helper db_helper){
        this.db_helper = db_helper;
        this.gson = new Gson();
    }

    public static String createTable() {
        return "CREATE TABLE " + TABLE_ALERTS + " (" +
                COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                COLUMN_SYMBOL + " TEXT NOT NULL, " +
                COLUMN_STATUS + " INTEGER NOT NULL, " +
                COLUMN_CONDITION_NAME + " TEXT NOT NULL, " +
                COLUMN_CONDITION_JSON + " TEXT NOT NULL)";
    }

    public void insertAlert(Alert alert) {
        ContentValues values = new ContentValues();
        values.put(COLUMN_SYMBOL, alert.getSymbol());
        values.put(COLUMN_STATUS, alert.getStatus().name());

        // Save the class name so we know which Condition implementation to use later
        values.put(COLUMN_CONDITION_NAME, alert.getCondition().getClass().getName());
        values.put(COLUMN_CONDITION_JSON, alert.getCondition().serialize());

        try {
            db_helper.getWritableDatabase().insert(TABLE_ALERTS, null, values);
        }catch (Exception e){
            Log.e(DB_Helper.DB_LOG_TAG, "Error inserting alert", e);
        }
    }

    public List<Alert> getActiveAlerts() {
        List<Alert> alerts = new ArrayList<>();
        Cursor cursor = db_helper.getReadableDatabase().query(TABLE_ALERTS, null,
                COLUMN_STATUS + "=?", new String[]{"ACTIVE"},
                null, null, null);

        if (cursor.moveToFirst()) {
            do {
                String symbol = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_SYMBOL));
                String typeName = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_CONDITION_NAME));
                String json = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_CONDITION_JSON));

                try {
                    // Use Reflection to turn JSON back into the specific Condition class
                    Class<?> clazz = Class.forName(typeName);
                    Condition condition = (Condition) gson.fromJson(json, clazz);
                    alerts.add(new Alert(symbol, condition));
                } catch (Exception e) {
                    Log.e("AlertDB", "Failed to deserialize condition", e);
                }
            } while (cursor.moveToNext());
        }
        cursor.close();
        return alerts;
    }

    /**
     * Update only the status (e.g., mark as TRIGGERED or INACTIVE).
     * Used by AlertManager when a condition is met.
     */
    public void updateAlertStatus(Alert alert) {
        ContentValues values = new ContentValues();
        values.put(COLUMN_STATUS, alert.getStatus().name());

        try {
            db_helper.getWritableDatabase().update(TABLE_ALERTS, values,
                    COLUMN_SYMBOL + "=? AND " + COLUMN_CONDITION_JSON + "=?",
                    new String[]{alert.getSymbol(), alert.getCondition().serialize()});
        } catch (Exception e) {
            Log.e(DB_Helper.DB_LOG_TAG, "Error updating alert status", e);
        }
    }

    /**
     * Deletes a specific alert.
     */
    public void deleteAlert(Alert alert) {
        try {
            db_helper.getWritableDatabase().delete(TABLE_ALERTS,
                    COLUMN_SYMBOL + "=? AND " + COLUMN_CONDITION_JSON + "=?",
                    new String[]{alert.getSymbol(), alert.getCondition().serialize()});
        } catch (Exception e) {
            Log.e(DB_Helper.DB_LOG_TAG, "Error deleting alert", e);
        }
    }
}
