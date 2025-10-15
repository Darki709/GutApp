package com.example.gutapp.database;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.text.TextUtils;
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
    public static final String COLUMN_ID = "_id";
    public static final String COLUMN_SYMBOL = "symbol";

    //should be removed keep until you start loading data on you own
    public static final String COLUMN_NAME = "name";
    //
    public static final String COLUMN_DATE = "date";
    public static final String COLUMN_TIMEFRAME = "timeframe";
    public static final String COLUMN_OPEN = "open";
    public static final String COLUMN_HIGH = "high";
    public static final String COLUMN_LOW = "low";
    public static final String COLUMN_CLOSE = "close";
    public static final String COLUMN_VOLUME = "volume";

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



    //modular retrieval from database method
    public Cursor readFromDB(
            String[] columns,
            String selection,
            String[] selectionArgs,
            String orderBy,
            Integer limit) {


        // Validate column names
        if (columns != null) {
            for (String col : columns) {
                if (!col.matches("[A-Za-z0-9_]+")) {
                    throw new IllegalArgumentException("Invalid column name: " + col);
                }
            }
        }

        // Validate ORDER BY (optional, must be safe keyword or column)
        if (orderBy != null && !orderBy.isEmpty()) {
            // Allow only column names and ASC/DESC keywords
            if (!orderBy.matches("[A-Za-z0-9_]+(\\s+(ASC|DESC))?")) {
                throw new IllegalArgumentException("Invalid orderBy clause: " + orderBy);
            }
        }

        //Build the query string safely
        StringBuilder query = new StringBuilder();
        query.append("SELECT ");

        if (columns != null && columns.length > 0) {
            query.append(TextUtils.join(", ", columns));
        } else {
            query.append("*");
        }

        query.append(" FROM ").append(TABLE_NAME);

        if (selection != null && !selection.isEmpty()) {
            query.append(" WHERE ").append(selection);
        }

        if (orderBy != null && !orderBy.isEmpty()) {
            query.append(" ORDER BY ").append(orderBy);
        }

        if (limit != null && limit > 0) {
            query.append(" LIMIT ").append(limit);
        }

        // Execute safely — placeholders handled via selectionArgs
        return DB_HELPER.getReadableDatabase().rawQuery(query.toString(), selectionArgs);
    }


    public double getLatestPrice(String symbol) {
        try {
            Cursor cursor = readFromDB(new String[]{StockDataHelper.COLUMN_CLOSE}, "symbol = ?",
                    new String[]{symbol}, "date DESC", 2);
            cursor.moveToFirst();
            int current = cursor.getInt(0);
            cursor.moveToNext();
            int before = cursor.getInt(0);
            return (current > before) ? current : -1 * current;
        }
        catch (Exception e){
            Log.e(DB_Helper.DB_LOG_TAG, "Error getting latest price: " + e.getMessage());
            throw e;
        }
    }



}
