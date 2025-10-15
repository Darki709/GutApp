package com.example.gutapp.data.chart;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

import com.example.gutapp.data.UserGlobals;
import com.example.gutapp.database.ChartPresetHelper;
import com.example.gutapp.database.DB_Helper;
import com.example.gutapp.database.DB_Index;
import com.example.gutapp.database.StockDataHelper;
import com.example.gutapp.ui.ChartActivity;

import java.util.HashMap;
import java.util.Map;

public class PresetManager {
    private final Map<String, Indicator>[] presets;
    private DB_Helper db_helper;
    private String symbol;
    private String user_id;

    //constructor
    public PresetManager(DB_Helper db_helper, String symbol) {
        this.db_helper = db_helper;
        this.symbol = symbol;
        presets = new Map[5]; //maximum 5 presets per user
        for (int i = 0; i < presets.length; i++) {
            presets[i] = new HashMap<String, Indicator>();
        } //maximum 5 presets per user
        this.user_id = UserGlobals.ID;
    }

    //loads presets from the data base
    public void loadPresets() {
        ChartPresetHelper presetHelper = new ChartPresetHelper(db_helper);
        int j;
        for (int i = 0; i < presets.length; i++) {
            Cursor cursor = presetHelper.fetchPresets(user_id, i+1, symbol);
            if (cursor != null && cursor.getCount() != 0) {
                if (cursor.moveToFirst()) {
                    j = 0;
                    do {
                        int type = cursor.getInt(cursor.getColumnIndexOrThrow("type"));
                        String params = cursor.getString(cursor.getColumnIndexOrThrow("params"));
                        float[] paramsArray = parseParams(params);
                        Indicator indicator = IndicatorFactory.createIndicator(Indicators.fromInt(type),
                                Integer.toString(i), this.symbol, StockDataHelper.Timeframe.DAILY, paramsArray, this.db_helper);
                        presets[i].put(Integer.toString(j), indicator);
                        j++;
                    } while (cursor.moveToNext());
                }
                cursor.close();
            }
        }
        Log.i(ChartActivity.CHART_LOG_TAG, "All presets loaded" + presets.toString());
    }

    //stores all presets into the db
    public void storePresets(){
        ChartPresetHelper presetHelper = (ChartPresetHelper)db_helper.getHelper(DB_Index.CHART_PRESET_TABLE);
        presetHelper.storePresets(this.user_id,  this.symbol ,this.presets);
        Log.i(ChartActivity.CHART_LOG_TAG, "All presets saved");
    }

    private float[] parseParams(String params) {
        String[] paramsArray = params.split(":");
        float[] paramsArrayF = new float[paramsArray.length];
        for(int i = 0; i < paramsArray.length; i++){
            paramsArrayF[i] = Float.parseFloat(paramsArray[i]);
        }
        return paramsArrayF;
    }

    public Map<String, Indicator> getPreset(int preset_id) {
        Log.i(ChartActivity.CHART_LOG_TAG, "Preset loaded successfully to indicator manager" + presets[preset_id-1].toString());
        return presets[preset_id-1];
    }
}

