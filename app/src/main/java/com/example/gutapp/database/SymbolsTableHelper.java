package com.example.gutapp.database;

import android.database.Cursor;
import android.util.Log;
import android.database.sqlite.SQLiteDatabase;

public class SymbolsTableHelper implements Table{
    private static final String TABLE_NAME = "symbols";
    private static final String COLUMN_ID = "_id";
    private static final String COLUMN_SYMBOL = "symbol";
    private static final String COLUMN_NAME = "name";

    private DB_Helper db_helper;

    public SymbolsTableHelper(DB_Helper db_helper) {
        this.db_helper = db_helper;
    }



    public void insertSymbol(String symbol, String name, SQLiteDatabase db) {
        String sql = "INSERT INTO " + TABLE_NAME + " (" + COLUMN_SYMBOL + ", " + COLUMN_NAME + ") VALUES (?, ?)";
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
        insertSymbol("IBM", "International Business Machines", db);
        insertSymbol("TSLA", "Tesla, Inc.", db);
        insertSymbol("GOOG", "Alphabet Inc.", db);
        insertSymbol("AAPL", "Apple Inc.", db);
        insertSymbol("^GSPC", "S&P 500", db);
        insertSymbol("AD.AS", "Ahold Delhaize", db);
    }


    @Override
    public String createTable() {
        return "CREATE TABLE " + TABLE_NAME + " (" +
                COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT," +
                COLUMN_SYMBOL + " TEXT," +
                COLUMN_NAME + " TEXT" +
                ")";
    }

    @Override
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
}
