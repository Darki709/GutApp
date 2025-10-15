package com.example.gutapp.database;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

import com.example.gutapp.data.UserGlobals;
import com.example.gutapp.data.chart.Indicator;
import com.example.gutapp.data.chart.Indicators;

import java.util.Map;

public class ChartPresetHelper implements Table {
    private static final String TABLE_NAME = "chart_presets";
    private static final String COLUMN_USER_ID = "user_id";
    private static final String COLUMN_PRESET_ID = "preset_id";
    private static final String COLUMN_SYMBOL = "symbol";
    private static final String COLUMN_TYPE = "type";
    private static final String COLUMN_PARAMS = "params";
    private DB_Helper db_helper;


    public ChartPresetHelper(DB_Helper db_helper) {
        this.db_helper = db_helper;
    }

    public Cursor fetchPresets(String user_id, int preset_id, String symbol){
        SQLiteDatabase db = db_helper.getReadableDatabase();
        Cursor cursor;
        String sql = "SELECT " + COLUMN_SYMBOL + ", " + COLUMN_TYPE + ", " + COLUMN_PARAMS +
            " FROM " + TABLE_NAME + " WHERE " + COLUMN_USER_ID + " = ? AND " + COLUMN_PRESET_ID + " = ? AND " + COLUMN_SYMBOL + " = ?";
        try{
            cursor = db.rawQuery(sql, new String[]{user_id, Integer.toString(preset_id), symbol});
            Log.i(DB_Helper.DB_LOG_TAG, "fetched presets for " + symbol);
            return cursor;
        }
        catch (Exception e){
            Log.e(DB_Helper.DB_LOG_TAG, "Error fetching presets for " + symbol +" error:"+ e.getMessage());
        }
        return null;
    }

    public boolean storePresets(String user_id, String symbol, Map<String, Indicator>[] presets){
        SQLiteDatabase db = db_helper.getWritableDatabase();
        //delete old data
        String delClause = COLUMN_USER_ID + " = ? AND " +
                COLUMN_SYMBOL + " = ?";
        String[] delArgs = {user_id, symbol};
        db.delete(TABLE_NAME, delClause, delArgs);
        //this is where the insertion is happening
        try{
            db.beginTransaction();
            Map<String, Indicator> preset;//pointer for the loop
            for (int i = 0; i < presets.length; i++) {
                preset = presets[i];
                Log.i(DB_Helper.DB_LOG_TAG, "Storing presets for " + preset.toString());
                if (preset != null) {
                    this.insertPreset(db,i+1, symbol, preset, user_id);
                }
            }
            db.setTransactionSuccessful();
        }
        catch(Exception e){
        Log.e(DB_Helper.DB_LOG_TAG, "Error storing presets for " + symbol + " error:" + e.getMessage());
            return false;
        }
        finally {
            db.endTransaction();
        }
        Log.i(DB_Helper.DB_LOG_TAG, "Stored presets for " + symbol);
        return true;
    }


    public void insertPreset(SQLiteDatabase db, int preset_id, String symbol, Map<String, Indicator> indicators, String user_id){
        try{
            for(Map.Entry<String, Indicator> entry : indicators.entrySet()){
                if(entry.getValue() == null) continue;
                ContentValues values = new ContentValues();
                values.put(COLUMN_USER_ID, user_id);
                values.put(COLUMN_PRESET_ID, preset_id);
                values.put(COLUMN_SYMBOL, symbol);
                values.put(COLUMN_TYPE, entry.getValue().getType().name());
                values.put(COLUMN_PARAMS, entry.getValue().getParams());
                db.insert(TABLE_NAME, null, values);
            }
        }
        catch (Exception e){
            Log.e(DB_Helper.DB_LOG_TAG, "Error inserting preset for " + symbol + " error:" + e.getMessage());
            throw e;
        }
    }

    @Override
    public String createTable() {
        return "CREATE TABLE " + TABLE_NAME + " (" +
                COLUMN_USER_ID + " TEXT NOT NULL, " +
                COLUMN_PRESET_ID + " INTEGER NOT NULL, " +
                COLUMN_SYMBOL + " TEXT NOT NULL, " +
                COLUMN_TYPE + " TEXT NOT NULL, " +
                COLUMN_PARAMS + " TEXT NOT NULL" +
                ");";
    }

    @Override
    public String getName() {
        return TABLE_NAME;
    }
}
