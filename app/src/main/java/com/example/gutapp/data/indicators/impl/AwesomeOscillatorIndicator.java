package com.example.gutapp.data.indicators.impl;

import android.graphics.Color;
import com.example.gutapp.data.indicators.Indicator;
import com.example.gutapp.data.models.Candle;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineDataSet;
import java.util.ArrayList;
import java.util.List;

public class AwesomeOscillatorIndicator extends Indicator {

    public AwesomeOscillatorIndicator() {
        setColor(Color.parseColor("#FF007F"));
    }

    @Override public String getId()          { return "awesome_oscillator"; }
    @Override public String getDisplayName() { return "Awesome Oscillator"; }
    @Override public String getTag()         { return "AO"; }
    @Override public boolean isSubChart()     { return true; }
    @Override public Indicator newInstance()  { return new AwesomeOscillatorIndicator(); }

    @Override
    public Result compute(ArrayList<Candle> candles) {
        Result r = new Result();
        if (candles == null || candles.size() < 34) return r;

        int length = candles.size();
        double[] midpoints = new double[length];
        for (int i = 0; i < length; i++) {
            midpoints[i] = (candles.get(i).high + candles.get(i).low) / 2.0;
        }

        List<Entry> aoEntries = new ArrayList<>();
        for (int i = 33; i < length; i++) {
            double sum5 = 0.0;
            for (int j = i - 5 + 1; j <= i; j++) sum5 += midpoints[j];
            double sma5 = sum5 / 5.0;

            double sum34 = 0.0;
            for (int j = i - 34 + 1; j <= i; j++) sum34 += midpoints[j];
            double sma34 = sum34 / 34.0;

            aoEntries.add(new Entry(i, (float) (sma5 - sma34)));
        }

        LineDataSet set = makeLineSet(aoEntries, getTag(), getColor(), 1.5f);
        set.setAxisDependency(YAxis.AxisDependency.RIGHT);
        r.subChartLines.add(set);
        return r;
    }

    @Override
    public int calculateBias(ArrayList<Candle> data) {
        if (data == null || data.size() < 35) return 50;

        int n = data.size();
        double[] midpoints = new double[n];
        for (int i = 0; i < n; i++) midpoints[i] = (data.get(i).high + data.get(i).low) / 2.0;

        double currentAO = calculateSingleAO(midpoints, n - 1);
        double prevAO = calculateSingleAO(midpoints, n - 2);

        if (currentAO > prevAO) return 75;
        if (currentAO < prevAO) return 25;
        return 50;
    }

    private double calculateSingleAO(double[] midpoints, int idx) {
        double sum5 = 0.0;
        for (int j = idx - 5 + 1; j <= idx; j++) sum5 += midpoints[j];
        double sum34 = 0.0;
        for (int j = idx - 34 + 1; j <= idx; j++) sum34 += midpoints[j];
        return (sum5 / 5.0) - (sum34 / 34.0);
    }
}