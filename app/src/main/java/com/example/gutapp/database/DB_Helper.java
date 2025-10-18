package com.example.gutapp.database;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

import androidx.annotation.Nullable;

import java.util.ArrayList;

public class DB_Helper extends SQLiteOpenHelper {
    private static final String DB_NAME = "Gut";
    private static final int DB_VERSION = 1;

    private ArrayList<Table> tables = new ArrayList<>();
    public static final String DB_LOG_TAG = "GutDB";


    public DB_Helper(@Nullable Context context) {
        super(context, DB_NAME, null, DB_VERSION);
        //instatciate table helpers and store them inside he object
        //context is needed for a temporary loading method because of database content erased when switching emulators
        StockDataHelper stockDataHelper = new StockDataHelper(context, this);
        tables.add(stockDataHelper);
        UserTableHelper userTableHelper = new UserTableHelper(this);
        tables.add(userTableHelper);
        SymbolsTableHelper symbolsTableHelper = new SymbolsTableHelper(this);
        tables.add(symbolsTableHelper);
        ChartPresetHelper chartPresetHelper = new ChartPresetHelper(this);
        tables.add(chartPresetHelper);
        IndicatorDBHelper indicatorDBHelper = new IndicatorDBHelper(this);
        tables.add(indicatorDBHelper);
        Log.i(DB_LOG_TAG, "db helper created " + tables.toString());
    }

    public Table getHelper(DB_Index index){
        return tables.get(index.ordinal());
    }


    @Override
    public void onCreate(SQLiteDatabase sqLiteDatabase) {

        Log.i(DB_LOG_TAG, "start create db " + tables.toString());
        for(Table table: tables){
            try {
                String createStockTable = table.createTable();
                sqLiteDatabase.execSQL(createStockTable);
                Log.i(DB_LOG_TAG, "finished create table " + table.getName());
            }
            catch (Exception e){
                Log.e(DB_LOG_TAG, "error create table " + table.getName() + e.getMessage());
                throw e;
            }
        }
        Log.i(DB_LOG_TAG, "end create db");
        ((StockDataHelper) this.getHelper(DB_Index.STOCK_TABLE)).loadStockDataFromAssets(sqLiteDatabase);
        ((SymbolsTableHelper) this.getHelper(DB_Index.SYMBOL_TABLE)).loadDefaultSymbols(sqLiteDatabase);
    }

    @Override
    public void onUpgrade(SQLiteDatabase sqLiteDatabase, int i, int i1) {

    }
}
