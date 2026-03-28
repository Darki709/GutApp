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


    public void insertSymbol(String symbol, String name) {
        SQLiteDatabase db = db_helper.getWritableDatabase();
        String sql = "INSERT OR REPLACE INTO  " + TABLE_NAME +  " (" + COLUMN_SYMBOL + ", " + COLUMN_NAME + ") VALUES (?, ?)";
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
        insertSymbol("AAPL", "Apple Inc.");
        insertSymbol("MSFT", "Microsoft Corporation");
        insertSymbol("GOOGL", "Alphabet Inc.");
        insertSymbol("AMZN", "Amazon.com, Inc.");
        insertSymbol("META", "Meta Platforms, Inc.");
        insertSymbol("TSLA", "Tesla, Inc.");
        insertSymbol("NVDA", "NVIDIA Corporation");
        insertSymbol("NFLX", "Netflix, Inc.");
        insertSymbol("AMD", "Advanced Micro Devices, Inc.");
        insertSymbol("INTC", "Intel Corporation");
        insertSymbol("ORCL", "Oracle Corporation");
        insertSymbol("CRM", "Salesforce, Inc.");
        insertSymbol("ADBE", "Adobe Inc.");
        insertSymbol("CSCO", "Cisco Systems, Inc.");
        insertSymbol("AVGO", "Broadcom Inc.");
        insertSymbol("PYPL", "PayPal Holdings, Inc.");
        insertSymbol("SQ", "Block, Inc.");
        insertSymbol("UBER", "Uber Technologies, Inc.");
        insertSymbol("ABNB", "Airbnb, Inc.");
        insertSymbol("SHOP", "Shopify Inc.");
        insertSymbol("JPM", "JPMorgan Chase & Co.");
        insertSymbol("BAC", "Bank of America Corp.");
        insertSymbol("V", "Visa Inc.");
        insertSymbol("MA", "Mastercard Incorporated");
        insertSymbol("DIS", "The Walt Disney Company");
        insertSymbol("KO", "The Coca-Cola Company");
        insertSymbol("PEP", "PepsiCo, Inc.");
        insertSymbol("NKE", "NIKE, Inc.");
        insertSymbol("SBUX", "Starbucks Corporation");
        insertSymbol("WMT", "Walmart Inc.");
        insertSymbol("COST", "Costco Wholesale Corp.");
        insertSymbol("TGT", "Target Corporation");
        insertSymbol("PFE", "Pfizer Inc.");
        insertSymbol("JNJ", "Johnson & Johnson");
        insertSymbol("MRNA", "Moderna, Inc.");
        insertSymbol("BTC-USD", "Bitcoin");
        insertSymbol("ETH-USD", "Ethereum");
        insertSymbol("SOL-USD", "Solana");
        insertSymbol("BNB-USD", "Binance Coin");
        insertSymbol("XRP-USD", "XRP");
        insertSymbol("ADA-USD", "Cardano");
        insertSymbol("DOGE-USD", "Dogecoin");
        insertSymbol("DOT-USD", "Polkadot");
        insertSymbol("MATIC-USD", "Polygon");
        insertSymbol("LINK-USD", "Chainlink");
        insertSymbol("SPY", "SPDR S&P 500 ETF Trust");
        insertSymbol("QQQ", "Invesco QQQ Trust");
        insertSymbol("VOO", "Vanguard S&P 500 ETF");
        insertSymbol("ARKK", "ARK Innovation ETF");
        insertSymbol("GLD", "SPDR Gold Shares");
        }
    }

    public static String createTable() {
        return "CREATE TABLE " + TABLE_NAME + " (" +
                COLUMN_SYMBOL + " TEXT, " +
                COLUMN_NAME + " TEXT, " +
                COLUMN_INTERVAL + " TEXT, " +
                COLUMN_LAST_FETCH + " BIGINT, " + //saved in unix ts
                "PRIMARY KEY (" + COLUMN_SYMBOL + ", " + COLUMN_INTERVAL + "))";
    }

    public String getName() {
        return TABLE_NAME;
    }


    //loading all the symbol and name data mainly used for home activity
    public Cursor getStocks(){
        SQLiteDatabase db = db_helper.getReadableDatabase();
        String sql = "SELECT DISTINCT " + COLUMN_SYMBOL + ", " + COLUMN_NAME + " FROM " + TABLE_NAME + " LIMIT 50";
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
