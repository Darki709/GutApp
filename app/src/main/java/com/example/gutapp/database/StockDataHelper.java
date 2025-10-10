package com.example.gutapp.database;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

import com.github.mikephil.charting.data.CandleEntry;

import java.text.ParseException;
import java.util.ArrayList;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class StockDataHelper implements Table{
    private static final String TABLE_NAME = "stock_data";
    private static final String COLUMN_ID = "_id";
    private static final String COLUMN_SYMBOL = "symbol";
    private static final String COLUMN_NAME = "name";
    private static final String COLUMN_DATE = "date";
    private static final String COLUMN_TIMEFRAME = "timeframe";
    private static final String COLUMN_OPEN = "open";
    private static final String COLUMN_HIGH = "high";
    private static final String COLUMN_LOW = "low";
    private static final String COLUMN_CLOSE = "close";
    private static final String COLUMN_VOLUME = "volume";

    private DB_Helper DB_HELPER;

    public StockDataHelper(DB_Helper db_helper) {
    DB_HELPER = db_helper;
        SQLiteDatabase db = DB_HELPER.getWritableDatabase();
    }

    public ArrayList<CandleEntry> getCachedStockData(String symbol) throws Exception {
        ArrayList<CandleEntry> stockData = new ArrayList<>();
        Log.i(DB_HELPER.DB_LOG_TAG, "start fetching stock data");
        SQLiteDatabase db = DB_HELPER.getReadableDatabase();
        String[] columns = {COLUMN_DATE, COLUMN_OPEN, COLUMN_HIGH, COLUMN_LOW, COLUMN_CLOSE};
        String selection = COLUMN_SYMBOL + " = ?";
        String[] selectionArgs = {symbol};
        try{
            Cursor cursor = db.query(TABLE_NAME, columns, selection, selectionArgs, null, null, null);
            cursor.moveToFirst();
            while(!cursor.isAfterLast()){
                //retrieve data from cursor
                String dateStr = (String)cursor.getString((int)cursor.getColumnIndexOrThrow(COLUMN_DATE));
                float open = (float)cursor.getFloat((int)cursor.getColumnIndexOrThrow(COLUMN_OPEN));
                float high = (float)cursor.getFloat((int)cursor.getColumnIndexOrThrow(COLUMN_HIGH));
                float low = (float)cursor.getFloat((int)cursor.getColumnIndexOrThrow(COLUMN_LOW));
                float close = (float)cursor.getFloat((int)cursor.getColumnIndexOrThrow(COLUMN_CLOSE));
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);
                Date date = sdf.parse(dateStr);         // parse string to Date
                float xValue = (float)date.getTime();
                CandleEntry entry = new CandleEntry(xValue, open, high, low, close);
                stockData.add(entry);//add single candlestick data to the list
                cursor.moveToNext();
            }
        }
        catch (Exception e){
            Log.e(DB_HELPER.DB_LOG_TAG, "error fetching stock data " + e.getMessage());
            throw e;
        }
        Log.i(DB_HELPER.DB_LOG_TAG, "end fetching stock data");
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
