package com.example.gutapp.data.indicators.impl;

import android.graphics.Color;
import com.example.gutapp.data.indicators.Indicator;
import com.example.gutapp.data.models.Candle;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineDataSet;
import java.util.ArrayList;
import java.util.List;

public class LinearRegressionSlopeIndicator extends Indicator {

    public LinearRegressionSlopeIndicator() {
        params.add(new Param("period", "Slope Lookback", Param.Type.INTEGER, 5, 100, 14));
        setColor(Color.parseColor("#00E676")); // Vibrant Lime Green
    }

    @Override public String getId()          { return "linreg_slope"; }
    @Override public String getDisplayName() { return "Linear Regression Slope"; }
    @Override public String getTag()         { return "SLOPE"; }
    @Override public boolean isSubChart()     { return true; }
    @Override public Indicator newInstance()  { return new LinearRegressionSlopeIndicator(); }

    @Override
    public Result compute(ArrayList<Candle> candles) {
        Result r = new Result();
        int period = (int) getParam("period");
        if (candles == null || candles.size() < period) return r;

        List<Entry> slopeEntries = new ArrayList<>();
        List<Entry> baselineEntries = new ArrayList<>();

        // Precompute statistical constant denominators based on period length
        double sumX = 0;
        double sumX2 = 0;
        for (int x = 0; x < period; x++) {
            sumX += x;
            sumX2 += (x * x);
        }
        double denominator = (period * sumX2) - (sumX * sumX);

        for (int i = period - 1; i < candles.size(); i++) {
            double sumY = 0;
            double sumXY = 0;
            int count = 0;

            for (int j = i - period + 1; j <= i; j++) {
                double y = candles.get(j).close;
                sumY += y;
                sumXY += (count * y);
                count++;
            }

            float slope = 0f;
            if (denominator != 0) {
                slope = (float) (((period * sumXY) - (sumX * sumY)) / denominator);
            }

            slopeEntries.add(new Entry(i, slope));
            baselineEntries.add(new Entry(i, 0f));
        }

        LineDataSet slopeSet = makeLineSet(slopeEntries, getTag(), getColor(), 1.4f);
        LineDataSet baseSet = makeDashedLineSet(baselineEntries, "Neutral", Color.parseColor("#509E9E9E"));

        slopeSet.setAxisDependency(YAxis.AxisDependency.RIGHT);
        baseSet.setAxisDependency(YAxis.AxisDependency.RIGHT);

        r.subChartLines.add(slopeSet);
        r.subChartLines.add(baseSet);
        return r;
    }

    @Override
    public int calculateBias(ArrayList<Candle> data) {
        int period = (int) getParam("period");
        if (data == null || data.size() < period) return 50;

        int lastIdx = data.size() - 1;
        double sumX = 0;
        double sumX2 = 0;
        for (int x = 0; x < period; x++) {
            sumX += x;
            sumX2 += (x * x);
        }
        double denominator = (period * sumX2) - (sumX * sumX);
        if (denominator == 0) return 50;

        double sumY = 0;
        double sumXY = 0;
        int count = 0;
        for (int j = lastIdx - period + 1; j <= lastIdx; j++) {
            double y = data.get(j).close;
            sumY += y;
            sumXY += (count * y);
            count++;
        }

        double slope = ((period * sumXY) - (sumX * sumY)) / denominator;

        // Base normalization: map slope value relative to asset price scale
        double currentPrice = data.get(lastIdx).close;
        if (currentPrice == 0) return 50;

        double normalizedSlopePercent = (slope / currentPrice) * 100.0;

        // Target an expected maximum slope tracking limit of +/- 0.5% per bar
        double scaledRatio = (normalizedSlopePercent + 0.5) / 1.0;

        int score = (int) Math.round(scaledRatio * 100.0);
        return Math.max(0, Math.min(100, score));
    }
}