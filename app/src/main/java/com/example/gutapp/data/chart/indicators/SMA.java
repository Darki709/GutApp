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
import com.example.gutapp.database.IndicatorDBHelper;
import com.example.gutapp.ui.ChartActivity;
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
    private IndicatorDBHelper indicatorDBHelper;
    private DB_Helper db_helper;

    public SMA(DB_Helper db_helper,int color,int period, float width,String id, Indicators type, String symbol, StockDataHelper.Timeframe timeframe) {
        // Pass it to the super constructor
        super(id, type, timeframe, true, symbol, color);
        this.period = period;
        this.width = width;
        this.db_helper = db_helper;
        this.indicatorDBHelper = new IndicatorDBHelper(db_helper);
    }

//    public LineDataSet calculateSMA(String symbol, int period) {
//            List<Entry> entries = sma_dbHelper.fetchSMA(symbol, period);
//            List<float[]> prices = new ArrayList<>();;
//            Cursor cursor;
//            if(entries.isEmpty()){
//                try{
//                cursor = ((StockDataHelper)db_helper.getHelper(DB_Index.STOCK_TABLE)).readFromDB(new String[]{"date", "close"}, "symbol = ?", new String[]{symbol}, "date ASC", null);
//                cursor.moveToFirst();
//                while (!cursor.isAfterLast()) {
//                    float date = cursor.getFloat(0);
//                    float close = cursor.getFloat(1);
//                    prices.add(new float[]{date, close});
//                    cursor.moveToNext();}
//                }catch(Exception e){
//                    Log.e(db_helper.DB_LOG_TAG, "Error fetching stock data: " + e.getMessage());
//                    throw e;
//                }
//                cursor.close();
//                Log.i(DB_Helper.DB_LOG_TAG, "Fetched " + prices.size() + " entries for symbol" + symbol);
//                return IndicatorUtil.movingAverageDataSet(db_helper.getWritableDatabase(),prices , period, symbol, getID());
//            }
//            return new LineDataSet(entries, getID());
//    }
//
//    // In SMA.java
public LineDataSet calculateSMA(String symbol, int period, StockDataHelper.Timeframe timeframe) {
    // Pass timeframe to the database helper
    List<Entry> entries = indicatorDBHelper.fetchIndicatorData(symbol, period, timeframe, "SMA");
    if (entries.isEmpty()) {
        Log.i(ChartActivity.CHART_LOG_TAG, "Calculating SMA for" + symbol + " " + timeframe.name());
        List<float[]> prices = new ArrayList<>();
        // Use try-with-resources to automatically close the cursor
        try (Cursor cursor = ((StockDataHelper) db_helper.getHelper(DB_Index.STOCK_TABLE)).readFromDB(
                new String[]{"close"},
                // IMPORTANT: Update query to filter by timeframe
                "symbol = ? AND timeframe = ?",
                new String[]{symbol, timeframe.getValue()}, // Use timeframe in query args
                "date ASC",
                null)) {
            Log.i(DB_Helper.DB_LOG_TAG, "Fetched " + prices.size() + " entries for symbol " + symbol);
            int i = 0;
            if (cursor != null && cursor.moveToFirst()) {
                while (!cursor.isAfterLast()) {
                    float date = i++;
                    float close = cursor.getFloat(0);
                    prices.add(new float[]{date, close});
                    cursor.moveToNext();
                }
                Log.i(DB_Helper.DB_LOG_TAG, "Fetched " + prices.size() + " entries for symbol " + symbol);
                // Pass timeframe to the calculation utility
                return IndicatorUtil.movingAverageDataSet(db_helper.getWritableDatabase(), prices, period, symbol, getID(), timeframe, "SMA");
            }
        } catch (Exception e) {
            Log.e(DB_Helper.DB_LOG_TAG, "Error fetching stock data for SMA: " + e.getMessage(), e);
            return new LineDataSet(new ArrayList<>(), getID() + "_error");
        }
    }
        LineDataSet data= new LineDataSet(entries, getID());
        Log.i(ChartActivity.CHART_LOG_TAG, "Returning cached SMA for " + symbol + " "  + timeframe.name() + " " + period + " size: " + data.getEntryCount());
    return data ;
}



    @Override
    public void draw(CombinedChart combinedChart) {
        // --- FIX: Always remove the old line before drawing a new one ---
        remove(combinedChart);

        LineDataSet indicatorDataSet = calculateSMA(this.symbol, this.period, this.timeframe);

        indicatorDataSet.setColor(this.color);
        indicatorDataSet.setLineWidth(this.width);
        // Essential for performance and to avoid label clutter
        indicatorDataSet.setDrawCircles(false);
        indicatorDataSet.setDrawValues(false);

        CombinedData combinedData = combinedChart.getData();
        if (combinedData == null) {
            // This case should not happen if the chart already has stock data, but it's a good safeguard
            Log.e(ChartActivity.CHART_LOG_TAG, "CombinedData is null. Cannot draw indicator.");
            return;
        }
        LineData lineData = combinedData.getLineData();
        if (lineData == null) {
            lineData = new LineData();
        }
        lineData.addDataSet(indicatorDataSet);
        combinedData.setData(lineData);
        combinedChart.setData(combinedData);


        // No need to call combinedChart.setData() again, just notify of the change
        combinedChart.notifyDataSetChanged();
        combinedChart.invalidate();
    }


    @Override
    public void changeSettings(float[] params, CombinedChart combinedChart) {
        this.remove(combinedChart);
        this.color = (int)params[0];
        this.period = (int) params[1];
        this.width = params[2];
        this.draw(combinedChart);
    }

    @Override
    public void remove(CombinedChart combinedChart) {
        // Get the combined data from the chart
        CombinedData data = combinedChart.getData();
        if (data == null) {
            return; // No data on the chart, nothing to remove
        }

        // Get the line data container
        LineData lineData = data.getLineData();
        if (lineData == null) {
            return; // No line data on the chart, nothing to remove
        }

        // Find the specific dataset by its label (which should be the indicator's unique ID)
        ILineDataSet set = lineData.getDataSetByLabel(getID(), false);

        if (set != null) {
            // Remove the dataset from the line data
            lineData.removeDataSet(set);
            // Refresh the chart to reflect the change
            combinedChart.notifyDataSetChanged();
            combinedChart.invalidate();
        }
    }
    @Override
    public String getParams(){
        return Integer.toString(this.color) + ":" + Integer.toString(this.period) + ":" + Float.toString(this.width);
    }
}
