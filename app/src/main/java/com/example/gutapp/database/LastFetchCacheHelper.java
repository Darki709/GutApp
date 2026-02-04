package com.example.gutapp.database;

import android.database.Cursor;
import android.util.Log;
import android.database.sqlite.SQLiteDatabase;

public class LastFetchCacheHelper{
    public static final String TABLE_NAME = "symbols";
    public static final String COLUMN_SYMBOL = "symbol";
    public static final String COLUMN_NAME = "name";
    public static final String COLUMN_INTERVAL = "interval";
    public static final String COLUMN_LAST_FETCH = "last_fetch";

    private DB_Helper db_helper;

    public LastFetchCacheHelper(DB_Helper db_helper) {
        this.db_helper = db_helper;
        loadDefaultSymbols(db_helper.getWritableDatabase());
    }



    public void insertSymbol(String symbol, String name, SQLiteDatabase db) {
        String sql = "INSERT INTO " + TABLE_NAME + " (" + COLUMN_SYMBOL + ", " + COLUMN_NAME + "," + COLUMN_LAST_FETCH +") VALUES (?, ?, 0)";
        try {
            db.execSQL(sql, new String[]{symbol, name});
        } catch (Exception e) {
            Log.e(DB_Helper.DB_LOG_TAG, "Error inserting symbol", e);
        }
    }

    public String getSymbolName(String symbol) {
        SQLiteDatabase db = db_helper.getReadableDatabase();
        String sql = "SELECT " + COLUMN_NAME + " FROM " + TABLE_NAME + " WHERE " + COLUMN_SYMBOL + " = ?";
        String[] selectionArgs = {symbol};
        try (Cursor cursor = db.rawQuery(sql, selectionArgs)) {
            if (cursor.moveToFirst()) {
                return cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_NAME));
            }
        } catch (Exception e) {
            Log.e("SymbolsTableHelper", "Error getting symbol name", e);
            throw e;
        }
        return null;
    }

    public void loadDefaultSymbols(SQLiteDatabase db) {
        Cursor cursor = db.rawQuery("SELECT COUNT(*) FROM " + TABLE_NAME, null);
        cursor.moveToFirst();
        int count = cursor.getInt(0);
        cursor.close();

        if (count == 0) {
        insertSymbol("AAPL", "Apple Inc.", db);
        insertSymbol("MSFT", "Microsoft Corporation", db);
        insertSymbol("GOOGL", "Alphabet Inc.", db);
        insertSymbol("AMZN", "Amazon.com, Inc.", db);
        insertSymbol("META", "Meta Platforms, Inc.", db);
        insertSymbol("TSLA", "Tesla, Inc.", db);
        insertSymbol("NVDA", "NVIDIA Corporation", db);
        insertSymbol("NFLX", "Netflix, Inc.", db);
        insertSymbol("AMD", "Advanced Micro Devices, Inc.", db);
        insertSymbol("INTC", "Intel Corporation", db);
        insertSymbol("ORCL", "Oracle Corporation", db);
        insertSymbol("CRM", "Salesforce, Inc.", db);
        insertSymbol("ADBE", "Adobe Inc.", db);
        insertSymbol("CSCO", "Cisco Systems, Inc.", db);
        insertSymbol("AVGO", "Broadcom Inc.", db);
        insertSymbol("PYPL", "PayPal Holdings, Inc.", db);
        insertSymbol("SQ", "Block, Inc.", db);
        insertSymbol("UBER", "Uber Technologies, Inc.", db);
        insertSymbol("ABNB", "Airbnb, Inc.", db);
        insertSymbol("SHOP", "Shopify Inc.", db);
        insertSymbol("JPM", "JPMorgan Chase & Co.", db);
        insertSymbol("BAC", "Bank of America Corp.", db);
        insertSymbol("V", "Visa Inc.", db);
        insertSymbol("MA", "Mastercard Incorporated", db);
        insertSymbol("DIS", "The Walt Disney Company", db);
        insertSymbol("KO", "The Coca-Cola Company", db);
        insertSymbol("PEP", "PepsiCo, Inc.", db);
        insertSymbol("NKE", "NIKE, Inc.", db);
        insertSymbol("SBUX", "Starbucks Corporation", db);
        insertSymbol("WMT", "Walmart Inc.", db);
        insertSymbol("COST", "Costco Wholesale Corp.", db);
        insertSymbol("TGT", "Target Corporation", db);
        insertSymbol("PFE", "Pfizer Inc.", db);
        insertSymbol("JNJ", "Johnson & Johnson", db);
        insertSymbol("MRNA", "Moderna, Inc.", db);
        insertSymbol("BTC-USD", "Bitcoin", db);
        insertSymbol("ETH-USD", "Ethereum", db);
        insertSymbol("SOL-USD", "Solana", db);
        insertSymbol("BNB-USD", "Binance Coin", db);
        insertSymbol("XRP-USD", "XRP", db);
        insertSymbol("ADA-USD", "Cardano", db);
        insertSymbol("DOGE-USD", "Dogecoin", db);
        insertSymbol("DOT-USD", "Polkadot", db);
        insertSymbol("MATIC-USD", "Polygon", db);
        insertSymbol("LINK-USD", "Chainlink", db);
        insertSymbol("SPY", "SPDR S&P 500 ETF Trust", db);
        insertSymbol("QQQ", "Invesco QQQ Trust", db);
        insertSymbol("VOO", "Vanguard S&P 500 ETF", db);
        insertSymbol("ARKK", "ARK Innovation ETF", db);
        insertSymbol("GLD", "SPDR Gold Shares", db);
        }
    }

    public static String createTable() {
        return "CREATE TABLE " + TABLE_NAME + " (" +
                COLUMN_SYMBOL + " TEXT PRIMARY KEY," +
                COLUMN_NAME + " TEXT, " +
                COLUMN_INTERVAL + " TEXT, " +
                COLUMN_LAST_FETCH + " BIGINT" + //saved in unix ts
                ")";
    }

    public String getName() {
        return TABLE_NAME;
    }


    //loading all the symbol and name data mainly used for home activity
    public Cursor getStocks(){
        SQLiteDatabase db = db_helper.getReadableDatabase();
        String sql = "SELECT " + COLUMN_SYMBOL + ", " + COLUMN_NAME + " FROM " + TABLE_NAME + " LIMIT 50";
        try{
            Cursor cursor = db.rawQuery(sql, null);
            Log.i(DB_Helper.DB_LOG_TAG, "Successfully fetched symbols and names");
            return cursor;
        }
        catch (Exception e){
            Log.e(DB_Helper.DB_LOG_TAG, "Error getting symbols and names", e);
            throw e;
        }

    }

    public long getLastFetchTime(String symbol, StockDataHelper.Timeframe timeframe) {
        SQLiteDatabase db = db_helper.getReadableDatabase();
        String sql = "SELECT " + COLUMN_LAST_FETCH + " FROM " + TABLE_NAME + " WHERE " + COLUMN_SYMBOL + " = ? AND " + COLUMN_INTERVAL + " = ?";
        String[] selectionArgs = {symbol, timeframe.value};
        try (Cursor cursor = db.rawQuery(sql, selectionArgs)) {
            if (cursor.moveToFirst() && cursor.getCount() > 0) {
                return cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_LAST_FETCH));
            }
        } catch (Exception e) {
            Log.e("SymbolsTableHelper", "Error getting last fetch time", e);

        }
        return 0;
    }
}
