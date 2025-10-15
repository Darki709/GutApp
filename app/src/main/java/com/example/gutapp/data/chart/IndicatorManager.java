package com.example.gutapp.data.chart;

import android.util.Log;

import com.example.gutapp.database.DB_Helper;
import com.example.gutapp.ui.ChartActivity;
import com.github.mikephil.charting.charts.CombinedChart;

import java.util.ArrayList;
import java.util.List;

public class IndicatorManager {
    private CombinedChart combinedChart;
    private List<Indicator> indicators;
    private DB_Helper db_helper;
    private String symbol;

    private int count;

    public IndicatorManager(CombinedChart combinedChart, List<Indicator> indicators, DB_Helper db_helper, String symbol) {
        this.symbol = symbol;
        this.combinedChart = combinedChart;
        this.indicators = indicators;
        this.db_helper = db_helper;
        this.indicators = new ArrayList<Indicator>();
        count = 0;
    }


    public Indicator getIndicator(int id){
        return indicators.get(id);
    }

    public void createIndicator(Indicators type, float[] params){
        Indicator indicator = IndicatorFactory.createIndicator(type, Integer.toString(this.count + 1), this.symbol, params, this.db_helper);
        addIndicator2Graph(indicator);
    }


    public void addIndicator2Graph(Indicator indicator) {
        indicators.add(indicator);
        count++;
        try{
            indicator.draw(combinedChart);
        }
        catch (Exception e){
            Log.e(ChartActivity.CHART_LOG_TAG, "Error drawing indicator: " + e.getMessage());
            throw e;
        }
    }

    public void changeSettings(String id, float[] params){
        try{
            indicators.get(Integer.parseInt(id)).changeSettings(params);
        }
        catch (Exception e) {
            Log.e(ChartActivity.CHART_LOG_TAG, "Error changing settings: " + e.getMessage());
            throw e;
        }
    }
}
