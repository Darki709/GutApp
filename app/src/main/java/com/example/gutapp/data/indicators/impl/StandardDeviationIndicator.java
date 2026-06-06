package com.example.gutapp.data.indicators.impl;

import android.graphics.Color;

import com.example.gutapp.data.indicators.Indicator;
import com.example.gutapp.data.models.Candle;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineDataSet;

import java.util.ArrayList;
import java.util.List;

public class StandardDeviationIndicator extends Indicator {

    public StandardDeviationIndicator() {
        params.add(new Param("period", "Period", Param.Type.INTEGER, 2, 200, 20));
        setColor(Color.parseColor("#9C27B0"));
    }

    @Override public String  getId()          { return "stdev"; }
    @Override public String  getDisplayName() { return "Standard Deviation"; }
    @Override public String  getTag()         { return "StDev"; }
    @Override public boolean isSubChart()     { return true; }

    @Override
    public Indicator newInstance() { return new StandardDeviationIndicator(); }

    @Override
    public Result compute(ArrayList<Candle> candles) {
        Result r = new Result();
        int period = (int) getParam("period");

        if (candles == null || candles.size() < period) return r;

        List<Entry> entries = new ArrayList<>();

        for (int i = period - 1; i < candles.size(); i++) {
            double sum = 0;
            for (int j = i - period + 1; j <= i; j++) {
                sum += candles.get(j).close;
            }
            double mean = sum / period;

            double sqDiffSum = 0;
            for (int j = i - period + 1; j <= i; j++) {
                double diff = candles.get(j).close - mean;
                sqDiffSum += diff * diff;
            }
            double variance = sqDiffSum / period;
            double stDev = Math.sqrt(variance);

            entries.add(new Entry(i, (float) stDev));
        }

        LineDataSet set = makeLineSet(entries, "StDev", getColor(), 1.4f);
        set.setAxisDependency(YAxis.AxisDependency.RIGHT);

        // No subChartMin/Max assigned so it auto-scales
        r.subChartLines.add(set);

        return r;
    }

    // Volatility measures do not indicate direction, so return safe default 50
    @Override
    public int calculateBias(ArrayList<Candle> data) { return 50; }
}