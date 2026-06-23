package com.example.gutapp.data.indicators.impl;

import android.graphics.Color;
import com.example.gutapp.data.indicators.Indicator;
import com.example.gutapp.data.models.Candle;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineDataSet;
import java.util.ArrayList;
import java.util.List;

public class TsiIndicator extends Indicator {

    public TsiIndicator() {
        params.add(new Param("longPeriod", "Long EMA", Param.Type.INTEGER, 5, 50, 25));
        params.add(new Param("shortPeriod", "Short EMA", Param.Type.INTEGER, 2, 30, 13));
        setColor(Color.parseColor("#29B6F6")); // Sky Blue
    }

    @Override public String getId()          { return "tsi"; }
    @Override public String getDisplayName() { return "True Strength Index"; }
    @Override public String getTag()         { return "TSI"; }
    @Override public boolean isSubChart()     { return true; }
    @Override public Indicator newInstance()  { return new TsiIndicator(); }

    @Override
    public Result compute(ArrayList<Candle> candles) {
        Result r = new Result();
        int longP = (int) getParam("longPeriod");
        int shortP = (int) getParam("shortPeriod");

        if (candles == null || candles.size() < longP + shortP + 2) return r;

        int size = candles.size();
        double[] diff = new double[size];
        double[] absDiff = new double[size];

        for (int i = 1; i < size; i++) {
            diff[i] = candles.get(i).close - candles.get(i - 1).close;
            absDiff[i] = Math.abs(diff[i]);
        }

        double[] smoothDiff = applyEmaFilter(diff, longP);
        double[] doubleSmoothDiff = applyEmaFilter(smoothDiff, shortP);

        double[] smoothAbsDiff = applyEmaFilter(absDiff, longP);
        double[] doubleSmoothAbsDiff = applyEmaFilter(smoothAbsDiff, shortP);

        List<Entry> tsiEntries = new ArrayList<>();
        List<Entry> zeroLine = new ArrayList<>();

        int startingValidIndex = longP + shortP;
        for (int i = startingValidIndex; i < size; i++) {
            float tsiVal = 0f;
            if (doubleSmoothAbsDiff[i] != 0) {
                tsiVal = (float) ((doubleSmoothDiff[i] / doubleSmoothAbsDiff[i]) * 100.0);
            }
            tsiEntries.add(new Entry(i, tsiVal));
            zeroLine.add(new Entry(i, 0f));
        }

        LineDataSet tsiSet = makeLineSet(tsiEntries, getTag(), getColor(), 1.4f);
        LineDataSet baseSet = makeDashedLineSet(zeroLine, "Zero Base", Color.parseColor("#409E9E9E"));

        tsiSet.setAxisDependency(YAxis.AxisDependency.RIGHT);
        baseSet.setAxisDependency(YAxis.AxisDependency.RIGHT);

        r.subChartLines.add(tsiSet);
        r.subChartLines.add(baseSet);
        return r;
    }

    @Override
    public int calculateBias(ArrayList<Candle> data) {
        int longP = (int) getParam("longPeriod");
        int shortP = (int) getParam("shortPeriod");
        if (data == null || data.size() < longP + shortP + 2) return 50;

        int size = data.size();
        double[] diff = new double[size];
        double[] absDiff = new double[size];

        for (int i = 1; i < size; i++) {
            diff[i] = data.get(i).close - data.get(i - 1).close;
            absDiff[i] = Math.abs(diff[i]);
        }

        double[] doubleSmoothDiff = applyEmaFilter(applyEmaFilter(diff, longP), shortP);
        double[] doubleSmoothAbsDiff = applyEmaFilter(applyEmaFilter(absDiff, longP), shortP);

        int lastIdx = size - 1;
        if (doubleSmoothAbsDiff[lastIdx] == 0) return 50;

        double rawTsi = (doubleSmoothDiff[lastIdx] / doubleSmoothAbsDiff[lastIdx]) * 100.0;

        // Smooth raw range metrics of [-100, +100] smoothly inside [0, 100]
        int score = (int) Math.round((rawTsi + 100.0) / 2.0);
        return Math.max(0, Math.min(100, score));
    }

    private double[] applyEmaFilter(double[] input, int period) {
        double[] output = new double[input.length];
        double multiplier = 2.0 / (period + 1);

        double seedSum = 0;
        for (int i = 1; i <= period; i++) {
            seedSum += input[i];
        }
        output[period] = seedSum / period;

        for (int i = period + 1; i < input.length; i++) {
            output[i] = ((input[i] - output[i - 1]) * multiplier) + output[i - 1];
        }
        return output;
    }
}