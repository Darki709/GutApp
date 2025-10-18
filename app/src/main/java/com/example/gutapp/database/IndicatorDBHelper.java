package com.example.gutapp.database;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

import com.example.gutapp.database.DB_Helper;
import com.example.gutapp.database.StockDataHelper;
import com.example.gutapp.database.Table;
import com.github.mikephil.charting.data.Entry;

import java.util.ArrayList;
import java.util.List;

public class IndicatorDBHelper implements Table {
    private static final String TABLE_NAME = "indicator_data";
    public static final String COLUMN_ID = "_id";
    public static final String COLUMN_SYMBOL = "symbol";
    public static final String COLUMN_DATE = "date";
    public static final String COLUMN_INDICATOR_VALUE = "indicator_value";
    public static final String COLUMN_INDICATOR_PERIOD = "indicator_period";
    public static final String COLUMN_TIMEFRAME = "timeframe";
    public static final String COLUMN_INDICATOR_NAME = "indicator_name"; // New column

    private DB_Helper db_helper;

    //constructor
    public IndicatorDBHelper(DB_Helper db_helper) {
        this.db_helper = db_helper;
    }


    public static void insertIndicatorData(SQLiteDatabase db, String symbol, float date, float value, int period, StockDataHelper.Timeframe timeframe, String indicatorName) {
        ContentValues values = new ContentValues();
        values.put(COLUMN_SYMBOL, symbol);
        values.put(COLUMN_DATE, date);
        values.put(COLUMN_INDICATOR_VALUE, value);
        values.put(COLUMN_INDICATOR_PERIOD, period);
        values.put(COLUMN_TIMEFRAME, timeframe.getValue());
        values.put(COLUMN_INDICATOR_NAME, indicatorName);
        try{
            db.insert(TABLE_NAME, null, values);
            Log.i(DB_Helper.DB_LOG_TAG, "Inserted " + indicatorName + " data for symbol " + symbol + " and period " + period);
        }
        catch (Exception e){
            Log.e(DB_Helper.DB_LOG_TAG, "Error inserting " + indicatorName + " data: " + e.getMessage());
            throw e;
        }
    }

    //returns Indicator data for a given symbol and period and timeframe
    public List<Entry> fetchIndicatorData(String symbol, int period, StockDataHelper.Timeframe timeframe, String indicatorName) {
        SQLiteDatabase db = db_helper.getReadableDatabase();
        String query = "SELECT " + COLUMN_DATE + ", " + COLUMN_INDICATOR_VALUE + " FROM " + TABLE_NAME +
                " WHERE " + COLUMN_SYMBOL + " = ? AND " + COLUMN_INDICATOR_PERIOD + " = ? AND " + COLUMN_TIMEFRAME + " = ? AND " + COLUMN_INDICATOR_NAME + " = ?";
        String[] args = {symbol, String.valueOf(period), timeframe.getValue(), indicatorName};
        List<Entry> indicatorData = new ArrayList<>();

        // Use try-with-resources to ensure the cursor is always closed.
        try (Cursor cursor = db.rawQuery(query, args)) {
            if (cursor != null && cursor.moveToFirst()) {
                int i = period - 1;
                while (!cursor.isAfterLast()) {
                    indicatorData.add(new Entry(i++, cursor.getFloat(1)));
                    cursor.moveToNext();
                }
            }
            Log.i(DB_Helper.DB_LOG_TAG, "Fetched " + indicatorData.size() + " " + indicatorName + " entries for symbol " + symbol);
        } catch (Exception e) {
            // Log the error and return an empty list instead of crashing.
            Log.e(DB_Helper.DB_LOG_TAG, "Error fetching " + indicatorName + " data: " + e.getMessage(), e);
        }
        return indicatorData;
    }


    @Override
    public String createTable() {
        return "CREATE TABLE " + TABLE_NAME + " (" +
                COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                COLUMN_SYMBOL + " TEXT NOT NULL, " +
                COLUMN_DATE + " REAL NOT NULL, " +
                COLUMN_INDICATOR_VALUE + " REAL NOT NULL, " +
                COLUMN_INDICATOR_PERIOD + " INTEGER NOT NULL, " +
                COLUMN_TIMEFRAME + " TEXT NOT NULL," +
                COLUMN_INDICATOR_NAME + " TEXT NOT NULL" +
                ");";
    }

    @Override
    public String getName() {
        return TABLE_NAME;
    }
}
