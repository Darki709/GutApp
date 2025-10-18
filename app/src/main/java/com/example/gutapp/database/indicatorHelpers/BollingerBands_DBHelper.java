package com.example.gutapp.database.indicatorHelpers;

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

public class BollingerBands_DBHelper implements Table {
    private static final String TABLE_NAME = "bollinger_bands_data";
    public static final String COLUMN_ID = "_id";
    public static final String COLUMN_SYMBOL = "symbol";
    public static final String COLUMN_DATE = "date"; // This will now store the sequential index, not timestamp
    public static final String COLUMN_MIDDLE_BAND_VALUE = "middle_band_value";
    public static final String COLUMN_UPPER_BAND_VALUE = "upper_band_value";
    public static final String COLUMN_LOWER_BAND_VALUE = "lower_band_value";
    public static final String COLUMN_PERIOD = "period";
    public static final String COLUMN_STD_DEV_MULTIPLIER = "std_dev_multiplier";
    public static final String COLUMN_TIMEFRAME = "timeframe";

    private DB_Helper db_helper;

    public BollingerBands_DBHelper(DB_Helper db_helper) {
        this.db_helper = db_helper;
    }

    public static void insertBollingerBands(SQLiteDatabase db, String symbol,
                                          float date, float middleBandValue,
                                          float upperBandValue, float lowerBandValue,
                                          int period, float stdDevMultiplier,
                                          StockDataHelper.Timeframe timeframe) {
        ContentValues values = new ContentValues();
        values.put(COLUMN_SYMBOL, symbol);
        values.put(COLUMN_DATE, date);
        values.put(COLUMN_MIDDLE_BAND_VALUE, middleBandValue);
        values.put(COLUMN_UPPER_BAND_VALUE, upperBandValue);
        values.put(COLUMN_LOWER_BAND_VALUE, lowerBandValue);
        values.put(COLUMN_PERIOD, period);
        values.put(COLUMN_STD_DEV_MULTIPLIER, stdDevMultiplier);
        values.put(COLUMN_TIMEFRAME, timeframe.getValue());

        try {
            db.insert(TABLE_NAME, null, values);
            Log.i(DB_Helper.DB_LOG_TAG, "Inserted Bollinger Bands data for symbol " + symbol + " period " + period + " stdDev " + stdDevMultiplier + " timeframe " + timeframe.name() + " x-value: " + date);
        } catch (Exception e) {
            Log.e(DB_Helper.DB_LOG_TAG, "Error inserting Bollinger Bands data: " + e.getMessage());
            throw e;
        }
    }

    public List<List<Entry>> fetchBollingerBands(String symbol, int period,
                                                float stdDevMultiplier,
                                                StockDataHelper.Timeframe timeframe) {
        SQLiteDatabase db = db_helper.getReadableDatabase();
        String query = "SELECT " + COLUMN_DATE + ", " + COLUMN_MIDDLE_BAND_VALUE + ", " +
                      COLUMN_UPPER_BAND_VALUE + ", " + COLUMN_LOWER_BAND_VALUE +
                      " FROM " + TABLE_NAME +
                      " WHERE " + COLUMN_SYMBOL + " = ? AND " +
                      COLUMN_PERIOD + " = ? AND " +
                      COLUMN_STD_DEV_MULTIPLIER + " = ? AND " +
                      COLUMN_TIMEFRAME + " = ?";
        String[] args = {symbol, String.valueOf(period), String.valueOf(stdDevMultiplier), timeframe.getValue()};

        List<Entry> middleBandData = new ArrayList<>();
        List<Entry> upperBandData = new ArrayList<>();
        List<Entry> lowerBandData = new ArrayList<>();
        List<List<Entry>> allBandsData = new ArrayList<>();

        try (Cursor cursor = db.rawQuery(query, args)) {
            if (cursor != null && cursor.moveToFirst()) {
                while (!cursor.isAfterLast()) {
                    float date = cursor.getFloat(0); // This 'date' is now the sequential index
                    middleBandData.add(new Entry(date, cursor.getFloat(1)));
                    upperBandData.add(new Entry(date, cursor.getFloat(2)));
                    lowerBandData.add(new Entry(date, cursor.getFloat(3)));
                    cursor.moveToNext();
                }
            }
            Log.i(DB_Helper.DB_LOG_TAG, "Fetched " + middleBandData.size() + " Bollinger Bands entries for symbol " + symbol + " period " + period + " stdDev " + stdDevMultiplier + " timeframe " + timeframe.name());
        } catch (Exception e) {
            Log.e(DB_Helper.DB_LOG_TAG, "Error fetching Bollinger Bands data: " + e.getMessage(), e);
        }
        allBandsData.add(middleBandData);
        allBandsData.add(upperBandData);
        allBandsData.add(lowerBandData);
        return allBandsData;
    }

    public static void clearAllBollingerBands(SQLiteDatabase db) {
        try {
            db.delete(TABLE_NAME, null, null);
            Log.i(DB_Helper.DB_LOG_TAG, "Cleared all data from " + TABLE_NAME);
        } catch (Exception e) {
            Log.e(DB_Helper.DB_LOG_TAG, "Error clearing " + TABLE_NAME + ": " + e.getMessage());
        }
    }

    @Override
    public String createTable() {
        return "CREATE TABLE " + TABLE_NAME + " (" +
                COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                COLUMN_SYMBOL + " TEXT NOT NULL, " +
                COLUMN_DATE + " REAL NOT NULL, " +
                COLUMN_MIDDLE_BAND_VALUE + " REAL NOT NULL, " +
                COLUMN_UPPER_BAND_VALUE + " REAL NOT NULL, " +
                COLUMN_LOWER_BAND_VALUE + " REAL NOT NULL, " +
                COLUMN_PERIOD + " INTEGER NOT NULL, " +
                COLUMN_STD_DEV_MULTIPLIER + " REAL NOT NULL, " +
                COLUMN_TIMEFRAME + " TEXT NOT NULL" +
                ");" +
                "CREATE INDEX idx_bollinger_bands_lookup ON " + TABLE_NAME +
                " (" + COLUMN_SYMBOL + ", " + COLUMN_PERIOD + ", " +
                COLUMN_STD_DEV_MULTIPLIER + ", " + COLUMN_TIMEFRAME + ");";
    }

    @Override
    public String getName() {
        return TABLE_NAME;
    }
}
