package com.example.gutapp.data.chart.indicators;

import android.database.Cursor;
import android.graphics.Color;

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
            List<LineDataSet> dataSets = new ArrayList<>();
            dataSets.add(new LineDataSet(cachedData.get(0), middleBandId));
            dataSets.add(new LineDataSet(cachedData.get(1), upperBandId));
            dataSets.add(new LineDataSet(cachedData.get(2), lowerBandId));
            return dataSets;
        } else {
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
        remove(combinedChart);

        List<LineDataSet> bollingerBandsDataSets = calculateBollingerBands(
                this.symbol,
                this.period,
                this.stdDevMultiplier,
                this.timeframe
        );

        if (bollingerBandsDataSets == null || bollingerBandsDataSets.isEmpty() || bollingerBandsDataSets.get(0).getEntryCount() == 0) {
            return;
        }

        // Configure visual properties for Middle Band
        LineDataSet middleBandDataSet = bollingerBandsDataSets.get(0);
        middleBandDataSet.setColor(color);
        middleBandDataSet.setLineWidth(this.width);
        middleBandDataSet.setDrawCircles(false);
        middleBandDataSet.setDrawValues(false);
        middleBandDataSet.setHighlightEnabled(false);

        // Configure visual properties for Upper Band
        LineDataSet upperBandDataSet = bollingerBandsDataSets.get(1);
        upperBandDataSet.setColor(color);
        upperBandDataSet.setLineWidth(this.width);
        upperBandDataSet.setDrawCircles(false);
        upperBandDataSet.setDrawValues(false);
        upperBandDataSet.setHighlightEnabled(false);

        // Configure visual properties for Lower Band
        LineDataSet lowerBandDataSet = bollingerBandsDataSets.get(2);
        lowerBandDataSet.setColor(color);
        lowerBandDataSet.setLineWidth(this.width);
        lowerBandDataSet.setDrawCircles(false);
        lowerBandDataSet.setDrawValues(false);
        lowerBandDataSet.setHighlightEnabled(false);

        CombinedData combinedData = combinedChart.getData();
        if (combinedData == null) {
            return;
        }

        LineData lineData = combinedData.getLineData();
        if (lineData == null) {
            lineData = new LineData();
        }

        lineData.addDataSet(middleBandDataSet);
        lineData.addDataSet(upperBandDataSet);
        lineData.addDataSet(lowerBandDataSet);

        combinedData.setData(lineData);
        combinedChart.setData(combinedData);

        combinedChart.notifyDataSetChanged();
        combinedChart.invalidate();
    }

    @Override
    public void remove(CombinedChart combinedChart) {
        CombinedData data = combinedChart.getData();
        if (data == null) {
            return;
        }

        LineData lineData = data.getLineData();
        if (lineData == null) {
            return;
        }

        ILineDataSet middleSet = lineData.getDataSetByLabel(middleBandId, false);
        ILineDataSet upperSet = lineData.getDataSetByLabel(upperBandId, false);
        ILineDataSet lowerSet = lineData.getDataSetByLabel(lowerBandId, false);

        if (middleSet != null) {
            lineData.removeDataSet(middleSet);
        }
        if (upperSet != null) {
            lineData.removeDataSet(upperSet);
        }
        if (lowerSet != null) {
            lineData.removeDataSet(lowerSet);
        }

        combinedChart.notifyDataSetChanged();
        combinedChart.invalidate();
    }

    @Override
    public void changeSettings(float[] params, CombinedChart combinedChart) {
        this.remove(combinedChart);

        this.color = (int) params[0];
        this.period = (int) params[1];
        this.stdDevMultiplier = params[2];
        this.width = params[3];

        this.draw(combinedChart);
    }

    @Override
    public String getParams() {
        return Integer.toString(this.color) + ":" +
                Integer.toString(this.period) + ":" +
                Float.toString(this.stdDevMultiplier) + ":" +
                Float.toString(this.width);
    }
}
