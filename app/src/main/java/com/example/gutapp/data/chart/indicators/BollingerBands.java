package com.example.gutapp.data.chart.indicators;

import android.database.Cursor;
import android.graphics.Color;
import android.util.Log;

import com.example.gutapp.data.chart.Indicator;
import com.example.gutapp.data.chart.IndicatorUtil;
import com.example.gutapp.data.chart.Indicators;
import com.example.gutapp.database.DB_Helper;
import com.example.gutapp.database.DB_Index;
import com.example.gutapp.database.StockDataHelper;
import com.example.gutapp.database.indicatorHelpers.BollingerBands_DBHelper;
import com.example.gutapp.ui.ChartActivity;
import com.github.mikephil.charting.charts.CombinedChart;
import com.github.mikephil.charting.data.CombinedData;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet;

import java.util.ArrayList;
import java.util.List;

public class BollingerBands extends Indicator {

    private int period;
    private float stdDevMultiplier;
    private float width;
    private BollingerBands_DBHelper dbHelper;
    private DB_Helper db_helper;

    private String middleBandId;
    private String upperBandId;
    private String lowerBandId;

    public BollingerBands(DB_Helper db_helper, int color, int period,
                          float stdDevMultiplier, float width, String id, Indicators type,
                          String symbol, StockDataHelper.Timeframe timeframe) {
        super(id, type, timeframe, true, symbol, color);
        this.period = period;
        this.stdDevMultiplier = stdDevMultiplier;
        this.width = width;
        this.db_helper = db_helper;
        this.dbHelper = new BollingerBands_DBHelper(db_helper);

        this.middleBandId = id + "_middle";
        this.upperBandId = id + "_upper";
        this.lowerBandId = id + "_lower";
    }

    private List<LineDataSet> calculateBollingerBands(String symbol, int period,
                                                    float stdDevMultiplier,
                                                    StockDataHelper.Timeframe timeframe) {
        List<List<Entry>> cachedData = dbHelper.fetchBollingerBands(symbol, period, stdDevMultiplier, timeframe);

        if (!cachedData.get(0).isEmpty()) {
            Log.i(ChartActivity.CHART_LOG_TAG,
                    "Returning cached Bollinger Bands data for " + symbol + " " + timeframe.name() +
                    " size: " + cachedData.get(0).size());
            List<LineDataSet> dataSets = new ArrayList<>();
            dataSets.add(new LineDataSet(cachedData.get(0), middleBandId));
            dataSets.add(new LineDataSet(cachedData.get(1), upperBandId));
            dataSets.add(new LineDataSet(cachedData.get(2), lowerBandId));
            return dataSets;
        } else {
            Log.i(ChartActivity.CHART_LOG_TAG,
                    "Calculating Bollinger Bands for " + symbol + " " + timeframe.name());

            List<float[]> prices = new ArrayList<>();

            try (Cursor cursor = ((StockDataHelper) db_helper.getHelper(DB_Index.STOCK_TABLE))
                    .readFromDB(
                            new String[]{"date", "close"},
                            "symbol = ? AND timeframe = ?",
                            new String[]{symbol, timeframe.getValue()},
                            "date ASC",
                            null)) {

                if (cursor != null && cursor.moveToFirst()) {
                    while (!cursor.isAfterLast()) {
                        float date = cursor.getFloat(0);
                        float close = cursor.getFloat(1);
                        prices.add(new float[]{date, close});
                        cursor.moveToNext();
                    }
                    Log.i(DB_Helper.DB_LOG_TAG,
                            "Fetched " + prices.size() + " entries for symbol " + symbol);

                    return IndicatorUtil.bollingerBandsDataSet(
                            db_helper.getWritableDatabase(),
                            prices,
                            period,
                            stdDevMultiplier,
                            symbol,
                            getID(),
                            timeframe
                    );
                }
            } catch (Exception e) {
                Log.e(DB_Helper.DB_LOG_TAG,
                        "Error fetching stock data for Bollinger Bands: " + e.getMessage(), e);
            }
            List<LineDataSet> emptyDataSets = new ArrayList<>();
            emptyDataSets.add(new LineDataSet(new ArrayList<>(), middleBandId + "_error"));
            emptyDataSets.add(new LineDataSet(new ArrayList<>(), upperBandId + "_error"));
            emptyDataSets.add(new LineDataSet(new ArrayList<>(), lowerBandId + "_error"));
            return emptyDataSets;
        }
    }

    @Override
    public void draw(CombinedChart combinedChart) {
        Log.i(ChartActivity.CHART_LOG_TAG, "BollingerBands: draw() called for ID: " + getID());
        remove(combinedChart);

        List<LineDataSet> bollingerBandsDataSets = calculateBollingerBands(
                this.symbol,
                this.period,
                this.stdDevMultiplier,
                this.timeframe
        );

        if (bollingerBandsDataSets == null || bollingerBandsDataSets.isEmpty() || bollingerBandsDataSets.get(0).getEntryCount() == 0) {
            Log.w(ChartActivity.CHART_LOG_TAG, "BollingerBands: No data to draw for ID: " + getID());
            return;
        }
        Log.d(ChartActivity.CHART_LOG_TAG, "BollingerBands: Data sets returned. Middle Band entries: " + bollingerBandsDataSets.get(0).getEntryCount());

        // Configure visual properties for Middle Band
        LineDataSet middleBandDataSet = bollingerBandsDataSets.get(0);
        middleBandDataSet.setColor(color);
        middleBandDataSet.setLineWidth(this.width);
        middleBandDataSet.setDrawCircles(false);
        middleBandDataSet.setDrawValues(false);
        middleBandDataSet.setHighlightEnabled(false);
        Log.d(ChartActivity.CHART_LOG_TAG, "BollingerBands: Middle Band configured. Color: " + this.color + ", Width: " + this.width);

        // Configure visual properties for Upper Band
        LineDataSet upperBandDataSet = bollingerBandsDataSets.get(1);
        upperBandDataSet.setColor(color);
        upperBandDataSet.setLineWidth(this.width);
        upperBandDataSet.setDrawCircles(false);
        upperBandDataSet.setDrawValues(false);
        upperBandDataSet.setHighlightEnabled(false);
        Log.d(ChartActivity.CHART_LOG_TAG, "BollingerBands: Upper Band configured. Color: " + Color.BLUE + ", Width: " + this.width);

        // Configure visual properties for Lower Band
        LineDataSet lowerBandDataSet = bollingerBandsDataSets.get(2);
        lowerBandDataSet.setColor(color);
        lowerBandDataSet.setLineWidth(this.width);
        lowerBandDataSet.setDrawCircles(false);
        lowerBandDataSet.setDrawValues(false);
        lowerBandDataSet.setHighlightEnabled(false);
        Log.d(ChartActivity.CHART_LOG_TAG, "BollingerBands: Lower Band configured. Color: " + Color.BLUE + ", Width: " + this.width);

        CombinedData combinedData = combinedChart.getData();
        if (combinedData == null) {
            Log.e(ChartActivity.CHART_LOG_TAG, "BollingerBands: CombinedData is null. Cannot draw indicator.");
            return;
        }
        Log.d(ChartActivity.CHART_LOG_TAG, "BollingerBands: CombinedData is not null.");

        LineData lineData = combinedData.getLineData();
        if (lineData == null) {
            lineData = new LineData();
            Log.d(ChartActivity.CHART_LOG_TAG, "BollingerBands: LineData was null, new one created.");
        }

        lineData.addDataSet(middleBandDataSet);
        lineData.addDataSet(upperBandDataSet);
        lineData.addDataSet(lowerBandDataSet);
        Log.d(ChartActivity.CHART_LOG_TAG, "BollingerBands: DataSets added to LineData. Total LineData sets: " + lineData.getDataSetCount());

        combinedData.setData(lineData);
        Log.d(ChartActivity.CHART_LOG_TAG, "BollingerBands: LineData set to CombinedData.");
        combinedChart.setData(combinedData);
        Log.d(ChartActivity.CHART_LOG_TAG, "BollingerBands: CombinedData set to chart.");

        combinedChart.notifyDataSetChanged();
        combinedChart.invalidate();
        Log.i(ChartActivity.CHART_LOG_TAG, "BollingerBands: Chart notified and invalidated for ID: " + getID());
    }

    @Override
    public void remove(CombinedChart combinedChart) {
        Log.i(ChartActivity.CHART_LOG_TAG, "BollingerBands: remove() called for ID: " + getID());
        CombinedData data = combinedChart.getData();
        if (data == null) {
            Log.d(ChartActivity.CHART_LOG_TAG, "BollingerBands: remove() - CombinedData is null.");
            return;
        }

        LineData lineData = data.getLineData();
        if (lineData == null) {
            Log.d(ChartActivity.CHART_LOG_TAG, "BollingerBands: remove() - LineData is null.");
            return;
        }

        ILineDataSet middleSet = lineData.getDataSetByLabel(middleBandId, false);
        ILineDataSet upperSet = lineData.getDataSetByLabel(upperBandId, false);
        ILineDataSet lowerSet = lineData.getDataSetByLabel(lowerBandId, false);

        if (middleSet != null) {
            lineData.removeDataSet(middleSet);
            Log.d(ChartActivity.CHART_LOG_TAG, "BollingerBands: Removed Middle Band: " + middleBandId);
        }
        if (upperSet != null) {
            lineData.removeDataSet(upperSet);
            Log.d(ChartActivity.CHART_LOG_TAG, "BollingerBands: Removed Upper Band: " + upperBandId);
        }
        if (lowerSet != null) {
            lineData.removeDataSet(lowerSet);
            Log.d(ChartActivity.CHART_LOG_TAG, "BollingerBands: Removed Lower Band: " + lowerBandId);
        }

        combinedChart.notifyDataSetChanged();
        combinedChart.invalidate();
        Log.i(ChartActivity.CHART_LOG_TAG, "BollingerBands: Chart refreshed after remove for ID: " + getID());
    }

    @Override
    public void changeSettings(float[] params, CombinedChart combinedChart) {
        Log.i(ChartActivity.CHART_LOG_TAG, "BollingerBands: changeSettings() called for ID: " + getID());
        this.remove(combinedChart);

        this.color = (int) params[0];
        this.period = (int) params[1];
        this.stdDevMultiplier = params[2];
        this.width = params[3];

        this.draw(combinedChart);
        Log.i(ChartActivity.CHART_LOG_TAG, "BollingerBands: Settings changed and redrawn for ID: " + getID());
    }

    @Override
    public String getParams() {
        return Integer.toString(this.color) + ":" +
                Integer.toString(this.period) + ":" +
                Float.toString(this.stdDevMultiplier) + ":" +
                Float.toString(this.width);
    }
}
