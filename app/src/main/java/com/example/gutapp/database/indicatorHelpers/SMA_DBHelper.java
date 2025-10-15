package com.example.gutapp.database.indicatorHelpers;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.CursorIndexOutOfBoundsException;
import android.database.sqlite.SQLiteDatabase;
import android.telephony.emergency.EmergencyNumber;
import android.util.Log;

import com.example.gutapp.database.DB_Helper;
import com.example.gutapp.database.StockDataHelper;
import com.example.gutapp.database.Table;
import com.github.mikephil.charting.data.Entry;

import java.util.ArrayList;
import java.util.List;

public class SMA_DBHelper implements Table {
    private static final String TABLE_NAME = "sma_data";
    public static final String COLUMN_ID = "_id";
    public static final String COLUMN_SYMBOL = "symbol";
    public static final String COLUMN_DATE = "date";
    public static final String COLUMN_SMA = "sma";
    public static final String COLUMN_SMA_PERIOD = "sma_period";
    public static final String COLUMN_TIMEFRAME = "timeframe";

    private SQLiteDatabase db;

    //constructor
    public SMA_DBHelper(DB_Helper db_helper) {
        this.db = db_helper.getWritableDatabase();
    }


    public static void insertSMA(SQLiteDatabase db, String symbol, float date, float sma, int smaPeriod, StockDataHelper.Timeframe timeframe) {
        ContentValues values = new ContentValues();
        values.put(COLUMN_SYMBOL, symbol);
        values.put(COLUMN_DATE, date);
        values.put(COLUMN_SMA, sma);
        values.put(COLUMN_SMA_PERIOD, smaPeriod);
        values.put(COLUMN_TIMEFRAME, timeframe.getValue());
        try{
            db.insert(TABLE_NAME, null, values);
            Log.i(DB_Helper.DB_LOG_TAG, "Inserted SMA data for symbol " + symbol + " and period " + smaPeriod);
        }
        catch (Exception e){
            Log.e(DB_Helper.DB_LOG_TAG, "Error inserting SMA data: " + e.getMessage());
            throw e;
        }
    }

    //returns SMA data for a given symbol and period and timeframe
    public List<Entry> fetchSMA(String symbol, int smaPeriod, StockDataHelper.Timeframe timeframe) {
        String query = "SELECT " + COLUMN_DATE + ", " + COLUMN_SMA + " FROM " + TABLE_NAME +
                " WHERE " + COLUMN_SYMBOL + " = ? AND " + COLUMN_SMA_PERIOD + " = ? AND " + COLUMN_TIMEFRAME + " = ?";
        String[] args = {symbol, String.valueOf(smaPeriod), timeframe.getValue()};
        List<Entry> smaData = new ArrayList<>();

        // Use try-with-resources to ensure the cursor is always closed.
        try (Cursor cursor = db.rawQuery(query, args)) {
            if (cursor != null && cursor.moveToFirst()) {
                int i = smaPeriod - 1;
                while (!cursor.isAfterLast()) {
                    smaData.add(new Entry(i++, cursor.getFloat(1)));
                    cursor.moveToNext();
                }
            }
            Log.i(DB_Helper.DB_LOG_TAG, "Fetched " + smaData.size() + " SMA entries for symbol " + symbol);
        } catch (Exception e) {
            // Log the error and return an empty list instead of crashing.
            Log.e(DB_Helper.DB_LOG_TAG, "Error fetching SMA data: " + e.getMessage(), e);
        }
        return smaData;
    }


    @Override
    public String createTable() {
        return "CREATE TABLE " + TABLE_NAME + " (" +
                COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                COLUMN_SYMBOL + " TEXT NOT NULL, " +
                COLUMN_DATE + " REAL NOT NULL, " +
                COLUMN_SMA + " REAL NOT NULL, " +
                COLUMN_SMA_PERIOD + " INTEGER NOT NULL, " +
                COLUMN_TIMEFRAME + " TEXT NOT NULL" + // <-- ADD NEW COLUMN TO SCHEMA
                ");";
    }

    @Override
    public String getName() {
        return TABLE_NAME;
    }
}
