package com.example.gutapp.data.indicators.impl;

import android.graphics.Color;
import com.example.gutapp.data.indicators.Indicator;
import com.example.gutapp.data.models.Candle;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineDataSet;
import java.util.ArrayList;
import java.util.List;

public class ParabolicSARIndicator extends Indicator {

    public ParabolicSARIndicator() {
        params.add(new Param("step",    "Acceleration Step", Param.Type.FLOAT, 0.005f, 0.1f, 0.02f));
        params.add(new Param("maxStep", "Max Acceleration",  Param.Type.FLOAT, 0.01f,  0.5f, 0.20f));
        setColor(Color.parseColor("#00E5FF"));
    }

    @Override public String getId()          { return "parabolic_sar"; }
    @Override public String getDisplayName() { return "Parabolic SAR"; }
    @Override public String getTag()         { return "SAR"; }
    @Override public boolean isSubChart()     { return false; }
    @Override public Indicator newInstance()  { return new ParabolicSARIndicator(); }

    @Override
    public Result compute(ArrayList<Candle> candles) {
        Result r = new Result();
        float step = getParam("step");
        float maxStep = getParam("maxStep");

        if (candles == null || candles.size() < 3) return r;

        int len = candles.size();
        double[] sar = new double[len];

        boolean isLong = candles.get(1).high > candles.get(0).high;
        sar[1] = isLong ? candles.get(0).low : candles.get(0).high;

        double ep = isLong ? candles.get(1).high : candles.get(1).low;
        double af = step;

        List<Entry> entries = new ArrayList<>();
        entries.add(new Entry(1, (float) sar[1]));

        for (int i = 2; i < len; i++) {
            Candle curr = candles.get(i);
            Candle prev = candles.get(i - 1);
            sar[i] = sar[i - 1] + af * (ep - sar[i - 1]);

            if (isLong) {
                if (sar[i] > curr.low || sar[i] > prev.low) {
                    sar[i] = Math.min(curr.low, prev.low);
                }
                if (curr.high > ep) {
                    ep = curr.high;
                    af = Math.min(af + step, maxStep);
                }
                if (curr.low < sar[i]) {
                    isLong = false;
                    sar[i] = ep;
                    ep = curr.low;
                    af = step;
                }
            } else {
                if (sar[i] < curr.high || sar[i] < prev.high) {
                    sar[i] = Math.max(curr.high, prev.high);
                }
                if (curr.low < ep) {
                    ep = curr.low;
                    af = Math.min(af + step, maxStep);
                }
                if (curr.high > sar[i]) {
                    isLong = true;
                    sar[i] = ep;
                    ep = curr.high;
                    af = step;
                }
            }
            entries.add(new Entry(i, (float) sar[i]));
        }

        LineDataSet set = makeLineSet(entries, getTag(), getColor(), 1.0f);
        set.setDrawCircles(true);
        set.setCircleColor(getColor());
        set.setCircleRadius(1.5f);
        set.setDrawCircleHole(false);
        set.setLineWidth(0f); // Render as standalone dots

        r.overlayLines.add(set);
        return r;
    }

    @Override
    public int calculateBias(ArrayList<Candle> data) {
        if (data == null || data.size() < 2) return 50;
        double close = data.get(data.size() - 1).close;

        // Recompute standard trend switch flag estimation
        boolean isBullish = data.get(data.size() - 1).close > data.get(data.size() - 2).close;
        return isBullish ? 75 : 25;
    }
}