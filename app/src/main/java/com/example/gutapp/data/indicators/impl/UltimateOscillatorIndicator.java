package com.example.gutapp.data.indicators.impl;

import android.graphics.Color;
import com.example.gutapp.data.indicators.Indicator;
import com.example.gutapp.data.models.Candle;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineDataSet;
import java.util.ArrayList;
import java.util.List;

public class UltimateOscillatorIndicator extends Indicator {

    public UltimateOscillatorIndicator() {
        params.add(new Param("p1", "Short Period",  Param.Type.INTEGER, 2, 20,  7));
        params.add(new Param("p2", "Medium Period", Param.Type.INTEGER, 5, 50,  14));
        params.add(new Param("p3", "Long Period",   Param.Type.INTEGER, 10, 100, 28));
        setColor(Color.parseColor("#7C4DFF"));
    }

    @Override public String getId()          { return "ultimate_oscillator"; }
    @Override public String getDisplayName() { return "Ultimate Oscillator"; }
    @Override public String getTag()         { return "ULTOSC"; }
    @Override public boolean isSubChart()     { return true; }
    @Override public Indicator newInstance()  { return new UltimateOscillatorIndicator(); }

    @Override
    public Result compute(ArrayList<Candle> candles) {
        Result r = new Result();
        int p1 = (int) getParam("p1");
        int p2 = (int) getParam("p2");
        int p3 = (int) getParam("p3");

        int maxPeriod = Math.max(p1, Math.max(p2, p3));
        if (candles == null || candles.size() < maxPeriod + 1) return r;

        int len = candles.size();
        double[] buyingPressure = new double[len];
        double[] trueRange = new double[len];

        for (int i = 1; i < len; i++) {
            Candle curr = candles.get(i);
            Candle prev = candles.get(i - 1);

            double minLowOrPrevClose = Math.min(curr.low, prev.close);
            double maxHighOrPrevClose = Math.max(curr.high, prev.close);

            buyingPressure[i] = curr.close - minLowOrPrevClose;
            trueRange[i] = maxHighOrPrevClose - minLowOrPrevClose;
        }

        List<Entry> entries = new ArrayList<>();

        for (int i = maxPeriod; i < len; i++) {
            double avg1 = getPeriodAverage(buyingPressure, trueRange, i, p1);
            double avg2 = getPeriodAverage(buyingPressure, trueRange, i, p2);
            double avg3 = getPeriodAverage(buyingPressure, trueRange, i, p3);

            float ultOscValue = (float) (100.0 * ((4.0 * avg1) + (2.0 * avg2) + avg3) / (4.0 + 2.0 + 1.0));
            entries.add(new Entry(i, ultOscValue));
        }

        r.subChartMin = 0f;
        r.subChartMax = 100f;

        LineDataSet set = makeLineSet(entries, getTag(), getColor(), 1.4f);
        set.setAxisDependency(YAxis.AxisDependency.RIGHT);
        r.subChartLines.add(set);
        return r;
    }

    private double getPeriodAverage(double[] bp, double[] tr, int currentIdx, int period) {
        double bpSum = 0.0;
        double trSum = 0.0;
        for (int i = currentIdx - period + 1; i <= currentIdx; i++) {
            bpSum += bp[i];
            trSum += tr[i];
        }
        return trSum != 0.0 ? bpSum / trSum : 0.0;
    }

    @Override
    public int calculateBias(ArrayList<Candle> data) {
        if (data == null || data.size() < 2) return 50;
        return data.get(data.size() - 1).close > data.get(data.size() - 2).close ? 75 : 25;
    }
}