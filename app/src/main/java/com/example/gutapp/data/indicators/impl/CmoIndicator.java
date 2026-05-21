package com.example.gutapp.data.indicators.impl;

import android.graphics.Color;
import com.example.gutapp.data.indicators.Indicator;
import com.example.gutapp.data.models.Candle;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineDataSet;
import java.util.ArrayList;
import java.util.List;

public class CmoIndicator extends Indicator {

    public CmoIndicator() {
        params.add(new Param("period", "Period", Param.Type.INTEGER, 2, 100, 14));
        setColor(Color.parseColor("#E91E63")); // Deep Pink
    }

    @Override public String getId()          { return "cmo"; }
    @Override public String getDisplayName() { return "Chande Momentum Oscillator"; }
    @Override public String getTag()         { return "CMO"; }
    @Override public boolean isSubChart()     { return true; }
    @Override public Indicator newInstance()  { return new CmoIndicator(); }

    @Override
    public Result compute(ArrayList<Candle> candles) {
        Result r = new Result();
        int period = (int) getParam("period");
        if (candles == null || candles.size() < period + 1) return r;

        List<Entry> cmoEntries = new ArrayList<>();
        List<Entry> zeroEntries = new ArrayList<>();

        for (int i = period; i < candles.size(); i++) {
            double higherClosesSum = 0;
            double lowerClosesSum = 0;

            for (int j = i - period + 1; j <= i; j++) {
                double diff = candles.get(j).close - candles.get(j - 1).close;
                if (diff > 0) {
                    higherClosesSum += diff;
                } else if (diff < 0) {
                    lowerClosesSum += Math.abs(diff);
                }
            }

            double totalStrength = higherClosesSum + lowerClosesSum;
            float cmoValue = 0f;
            if (totalStrength != 0) {
                cmoValue = (float) (((higherClosesSum - lowerClosesSum) / totalStrength) * 100.0);
            }

            cmoEntries.add(new Entry(i, cmoValue));
            zeroEntries.add(new Entry(i, 0f));
        }

        // Lock Y-Axis scale limits for clear view tracking
        r.subChartMin = -100f;
        r.subChartMax = 100f;

        LineDataSet cmoSet = makeLineSet(cmoEntries, "CMO", getColor(), 1.4f);
        LineDataSet centerLine = makeDashedLineSet(zeroEntries, "Center", Color.parseColor("#709E9E9E"));

        cmoSet.setAxisDependency(YAxis.AxisDependency.RIGHT);
        centerLine.setAxisDependency(YAxis.AxisDependency.RIGHT);

        r.subChartLines.add(cmoSet);
        r.subChartLines.add(centerLine);
        return r;
    }

    @Override
    public int calculateBias(ArrayList<Candle> data) {
        int period = (int) getParam("period");
        if (data == null || data.size() < period + 1) return 50;

        int lastIdx = data.size() - 1;
        double higherClosesSum = 0;
        double lowerClosesSum = 0;

        for (int j = lastIdx - period + 1; j <= lastIdx; j++) {
            double diff = data.get(j).close - data.get(j - 1).close;
            if (diff > 0) {
                higherClosesSum += diff;
            } else if (diff < 0) {
                lowerClosesSum += Math.abs(diff);
            }
        }

        double totalStrength = higherClosesSum + lowerClosesSum;
        if (totalStrength == 0) return 50;

        // Raw value runs from -100.0 to +100.0
        double rawCmo = ((higherClosesSum - lowerClosesSum) / totalStrength) * 100.0;

        // Shift scale from [-100, 100] to [0, 100]
        int score = (int) Math.round((rawCmo + 100.0) / 2.0);
        return Math.max(0, Math.min(100, score));
    }
}