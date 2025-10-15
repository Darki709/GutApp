package com.example.gutapp.data.chart.indicators;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

import com.example.gutapp.data.chart.Indicator;
import com.example.gutapp.data.chart.IndicatorUtil;
import com.example.gutapp.data.chart.Indicators;
import com.example.gutapp.database.DB_Helper;
import com.example.gutapp.database.DB_Index;
import com.example.gutapp.database.StockDataHelper;
import com.example.gutapp.database.indicatorHelpers.SMA_DBHelper;
import com.github.mikephil.charting.charts.CombinedChart;
import com.github.mikephil.charting.data.CombinedData;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet;

import java.util.ArrayList;
import java.util.List;

public class SMA extends Indicator {

    //num 1 parameter
    private int period;
    //num 2 parameter
    private float width;
    private SMA_DBHelper sma_dbHelper;
    private DB_Helper db_helper;

    public SMA(DB_Helper db_helper,int color,int period, float width,String id, Indicators type, String symbol) {
        super(id, type, true, symbol, color);
        this.period = period;
        sma_dbHelper = (SMA_DBHelper) db_helper.getHelper(DB_Index.SMA_TABLE);
    }

    public LineDataSet calculateSMA(String symbol, int period) {
            List<Entry> entries = sma_dbHelper.fetchSMA(symbol, period);
            List<float[]> prices = new ArrayList<>();;
            Cursor cursor;
            if(entries.isEmpty()){
                try{
                cursor = ((StockDataHelper)db_helper.getHelper(DB_Index.STOCK_TABLE)).readFromDB(new String[]{"date", "close"}, "symbol = ?", new String[]{symbol}, "date ASC", null);
                cursor.moveToFirst();
                while (!cursor.isAfterLast()) {
                    float date = cursor.getFloat(0);
                    float close = cursor.getFloat(1);
                    prices.add(new float[]{date, close});
                    cursor.moveToNext();}
                }catch(Exception e){
                    Log.e(db_helper.DB_LOG_TAG, "Error fetching stock data: " + e.getMessage());
                    throw e;
                }
                cursor.close();
                Log.i(DB_Helper.DB_LOG_TAG, "Fetched " + prices.size() + " entries for symbol" + symbol);
                return IndicatorUtil.movingAverageDataSet(db_helper.getWritableDatabase(),prices , period, symbol, getID());
            }
            return new LineDataSet(entries, getID());
    }



    @Override
    public void draw(CombinedChart combinedChart) {
        LineDataSet indicatorDataSet = calculateSMA(this.symbol, this.period);
        indicatorDataSet.setColor(this.color);
        indicatorDataSet.setLineWidth(this.width);
        CombinedData combinedData = combinedChart.getData();
        combinedData.addDataSet(indicatorDataSet);
        combinedChart.setData(combinedData);
        combinedChart.invalidate();
    }

    @Override
    public void changeSettings(float[] params) {

    }
}
