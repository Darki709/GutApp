package com.example.gutapp.database;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

import com.github.mikephil.charting.data.CandleEntry;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

public class StockDataHelper implements Table {
    private static final String TABLE_NAME = "stock_data";
    private static final String COLUMN_ID = "_id";
    private static final String COLUMN_SYMBOL = "symbol";

    //should be removed keep until you start loading data on you own
    private static final String COLUMN_NAME = "name";
    //
    private static final String COLUMN_DATE = "date";
    private static final String COLUMN_TIMEFRAME = "timeframe";
    private static final String COLUMN_OPEN = "open";
    private static final String COLUMN_HIGH = "high";
    private static final String COLUMN_LOW = "low";
    private static final String COLUMN_CLOSE = "close";
    private static final String COLUMN_VOLUME = "volume";

    private DB_Helper DB_HELPER;
    private Context context;

    public enum Timeframe {
        FIVE_MIN("5m"),
        FIFTEEN_MIN("15m"),
        HOURLY("1h"),
        DAILY("1d");

        private final String value;

        Timeframe(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }
    }

    public StockDataHelper(Context context, DB_Helper db_helper) {
        this.context = context;
        DB_HELPER = db_helper;
    }

    public void loadStockDataFromAssets() {
        SQLiteDatabase db = DB_HELPER.getWritableDatabase();
        try {
            InputStream inputStream = context.getAssets().open("Gut_db-stock_data.sql");
            BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream));
            String line;
            db.beginTransaction();
            while ((line = bufferedReader.readLine()) != null) {
                db.execSQL(line);
            }
            db.setTransactionSuccessful();
            db.endTransaction();
            Log.i(DB_HELPER.DB_LOG_TAG, "Successfully loaded stock data from assets.");
        } catch (IOException e) {
            Log.e(DB_HELPER.DB_LOG_TAG, "Error loading stock data from assets: " + e.getMessage());
        }
    }

    public ArrayList<CandleEntry> getCachedStockData(String symbol, Timeframe timeframe) throws Exception {
        ArrayList<CandleEntry> stockData = new ArrayList<>();
        Log.i(DB_HELPER.DB_LOG_TAG, "Fetching data for timeframe: " + timeframe.getValue());
        SQLiteDatabase db = DB_HELPER.getReadableDatabase();
        String[] columns = {COLUMN_DATE, COLUMN_OPEN, COLUMN_HIGH, COLUMN_LOW, COLUMN_CLOSE};
        String selection = COLUMN_SYMBOL + " = ? AND " + COLUMN_TIMEFRAME + " = ?";
        String[] selectionArgs = {symbol, timeframe.getValue()};

        try (Cursor cursor = db.query(TABLE_NAME, columns, selection, selectionArgs, null, null, COLUMN_DATE + " ASC")) {
            int i = 0;
            if (cursor.moveToFirst()) {
                do {
                    String dateStr = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_DATE));
                    float open = cursor.getFloat(cursor.getColumnIndexOrThrow(COLUMN_OPEN));
                    float high = cursor.getFloat(cursor.getColumnIndexOrThrow(COLUMN_HIGH));
                    float low = cursor.getFloat(cursor.getColumnIndexOrThrow(COLUMN_LOW));
                    float close = cursor.getFloat(cursor.getColumnIndexOrThrow(COLUMN_CLOSE));

                    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);
                    Date date = sdf.parse(dateStr);
                    long timestamp = date.getTime();

                    CandleEntry entry = new CandleEntry(i, high, low, open, close);
                    entry.setData(timestamp); // Store the actual timestamp
                    stockData.add(entry);
                    i++;
                } while (cursor.moveToNext());
            }
        }
        Log.i(DB_HELPER.DB_LOG_TAG, "Finished fetching data. Found " + stockData.size() + " entries.");
        return stockData;
    }

    @Override
    public String createTable() {
        return "CREATE TABLE " + TABLE_NAME + " (" +
                COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                COLUMN_SYMBOL + " TEXT, " +
                COLUMN_NAME + " TEXT, " +
                COLUMN_DATE + " TEXT, " +
                COLUMN_TIMEFRAME + " TEXT, " +
                COLUMN_OPEN + " REAL, " +
                COLUMN_HIGH + " REAL, " +
                COLUMN_LOW + " REAL, " +
                COLUMN_CLOSE + " REAL, " +
                COLUMN_VOLUME + " INTEGER)";
    }

    @Override
    public String getName() {
        return TABLE_NAME;
    }
}
