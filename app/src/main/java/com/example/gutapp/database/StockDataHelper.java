package com.example.gutapp.database;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteStatement;
import android.text.TextUtils;
import android.util.Log;

import com.example.gutapp.data.models.Candle;

import java.util.ArrayList;

public class StockDataHelper {
    private static final String TABLE_NAME = "stock_data";
    public static final String COLUMN_SYMBOL = "symbol";
    public static final String COLUMN_DATE = "date";
    public static final String COLUMN_TIMEFRAME = "timeframe";
    public static final String COLUMN_OPEN = "open";
    public static final String COLUMN_HIGH = "high";
    public static final String COLUMN_LOW = "low";
    public static final String COLUMN_CLOSE = "close";
    public static final String COLUMN_VOLUME = "volume";

    private DB_Helper db_helper;

    public enum Timeframe {
        ONE_MIN("1m", 60),
        FIVE_MIN("5m", 300),
        FIFTEEN_MIN("15m", 900),
        HOURLY("1h", 3600),
        DAILY("1d", 86400);

        public final String value;
        public final int interval;


        Timeframe(String value, int interval) {
            this.value = value;
            this.interval = interval;
        }
    }

    public StockDataHelper(DB_Helper db_helper) {
        this.db_helper = db_helper;
    }

    public ArrayList<Candle> getCachedStockData(String symbol, Timeframe timeframe) throws Exception {
        ArrayList<Candle> stockData = new ArrayList<>();
        Log.i(db_helper.DB_LOG_TAG, "Fetching data for timeframe: " + timeframe.value);
        SQLiteDatabase db = db_helper.getReadableDatabase();
        String[] columns = {COLUMN_DATE, COLUMN_OPEN, COLUMN_HIGH, COLUMN_LOW, COLUMN_CLOSE, COLUMN_VOLUME};
        String selection = COLUMN_SYMBOL + " = ? AND " + COLUMN_TIMEFRAME + " = ?";
        String[] selectionArgs = {symbol, timeframe.value};

        try (Cursor cursor = db.query(TABLE_NAME, columns, selection, selectionArgs, null, null, COLUMN_DATE + " ASC")) {
            if (cursor.moveToFirst()) {
                do {
                    long date_ts = cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_DATE));
                    float open = cursor.getFloat(cursor.getColumnIndexOrThrow(COLUMN_OPEN));
                    float high = cursor.getFloat(cursor.getColumnIndexOrThrow(COLUMN_HIGH));
                    float low = cursor.getFloat(cursor.getColumnIndexOrThrow(COLUMN_LOW));
                    float close = cursor.getFloat(cursor.getColumnIndexOrThrow(COLUMN_CLOSE));
                    int volume = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_VOLUME));


                    Candle candle = new Candle(date_ts, open, high, low, close, volume);
                    stockData.add(candle);
                } while (cursor.moveToNext());
            }
        }
        Log.i(db_helper.DB_LOG_TAG, "Finished fetching data. Found " + stockData.size() + " entries.");
        return stockData;
    }

    //inserts data and updates last fetch time
    public void saveStockData(String symbol, Timeframe timeframe, ArrayList<Candle> stockData) {
        if (stockData == null || stockData.isEmpty()) return;
        SQLiteDatabase db = db_helper.getWritableDatabase();
        LastFetchCacheHelper cacheHelper = new LastFetchCacheHelper(db_helper);
        String symbol_name = cacheHelper.getSymbolName(symbol);

        db.beginTransaction();

        try {
            String candleSql = "INSERT OR REPLACE INTO " + TABLE_NAME + " (" + COLUMN_SYMBOL + ", " + COLUMN_DATE + ", " + COLUMN_TIMEFRAME + ", " + COLUMN_OPEN + ", " + COLUMN_HIGH + "," + COLUMN_LOW + ","
                    + COLUMN_CLOSE + "," + COLUMN_VOLUME + ") VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
            SQLiteStatement statement = db.compileStatement(candleSql);
            for (Candle candle : stockData) {
                statement.clearBindings();
                statement.bindString(1, symbol);
                statement.bindLong(2, candle.timestamp);
                statement.bindString(3, timeframe.value);
                statement.bindDouble(4, candle.open);
                statement.bindDouble(5, candle.high);
                statement.bindDouble(6, candle.low);
                statement.bindDouble(7, candle.close);
                statement.bindLong(8, candle.volume);
                statement.executeInsert();
            }

            //the timestamp of the last entry in the new data is the fetch time
            long last_update = stockData.get(stockData.size() - 1).timestamp;
            long recorded_last_fetch = cacheHelper.getLastFetchTime(symbol, timeframe);
            if (last_update > recorded_last_fetch)
            {
                String query = "INSERT OR REPLACE INTO " + LastFetchCacheHelper.TABLE_NAME + " (" + LastFetchCacheHelper.COLUMN_SYMBOL + ", " + LastFetchCacheHelper.COLUMN_INTERVAL + ", " +
                     LastFetchCacheHelper.COLUMN_NAME + ", " + LastFetchCacheHelper.COLUMN_LAST_FETCH + ") VALUES (?, ?, ?, ?)";
                SQLiteStatement metaStmt = db.compileStatement(query);
                metaStmt.bindString(1, symbol);
                metaStmt.bindString(2, timeframe.value);
                metaStmt.bindString(3, symbol_name);
                metaStmt.bindLong(4, last_update);
                metaStmt.executeInsert();
            }

            db.setTransactionSuccessful();
        }catch (Exception e){
            Log.e(db_helper.DB_LOG_TAG, "Error saving stock data: " + e.getMessage());
        }
        finally {
            db.endTransaction();
            Log.i(db_helper.DB_LOG_TAG, "Finished saving data for symbol " + symbol + " and timeframe " + timeframe.value);
        }
    }



    public static String createTable() {
        return "CREATE TABLE " + TABLE_NAME + " (" +
                COLUMN_SYMBOL + " TEXT, " +
                COLUMN_DATE + " BIGINT NOT NULL, " +
                COLUMN_TIMEFRAME + " TEXT, " +
                COLUMN_OPEN + " REAL, " +
                COLUMN_HIGH + " REAL, " +
                COLUMN_LOW + " REAL, " +
                COLUMN_CLOSE + " REAL, " +
                COLUMN_VOLUME + " INTEGER, " + // Added comma and space here
                "PRIMARY KEY (" + COLUMN_SYMBOL + ", " + COLUMN_TIMEFRAME + ", " + COLUMN_DATE + "))";
    }

    public static String getName() {
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
        return db_helper.getReadableDatabase().rawQuery(query.toString(), selectionArgs);
    }


    public double getLatestPrice(String symbol) {
        try {
            Cursor cursor = readFromDB(new String[]{StockDataHelper.COLUMN_CLOSE}, "symbol = ?",
                    new String[]{symbol}, "date DESC", 2);
            cursor.moveToFirst();
            Double current = cursor.getDouble(0);
            if(!cursor.isLast()) cursor.moveToNext();
            else return current; //there might be only one entry so the default will be green

            Double before = cursor.getDouble(0);
            Log.i(DB_Helper.DB_LOG_TAG, "Getting latest price for " + symbol + " : " + current + " vs " + before);
            return (current > before) ? current : -1 * current;
        }
        catch (Exception e){
            Log.e(DB_Helper.DB_LOG_TAG, "Error getting latest price: " + e.getMessage());
            return 0; //meaning no price data
        }
    }



}
