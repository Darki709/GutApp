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

    private static ArrayList<Table> tables = new ArrayList<>();
    public static final String DB_LOG_TAG = "GutDB";


    public DB_Helper(@Nullable Context context) {
        super(context, DB_NAME, null, DB_VERSION);
        StockDataHelper stockDataHelper = new StockDataHelper(context, this);
        tables.add(stockDataHelper);

    }

    public Table getHelper(DB_Index index){
        return tables.get(index.ordinal());
    }


    @Override
    public void onCreate(SQLiteDatabase sqLiteDatabase) {

        Log.i(DB_LOG_TAG, "start create db");
        for(Table table:this.tables){
            try {
                String createStockTable = table.createTable();
                sqLiteDatabase.execSQL(createStockTable);
            }
            catch (Exception e){
                Log.e(DB_LOG_TAG, "error create table " + table.getName() + e.getMessage());
                throw e;
            }
        }
        Log.i(DB_LOG_TAG, "end create db");
    }

    @Override
    public void onUpgrade(SQLiteDatabase sqLiteDatabase, int i, int i1) {

    }
}
