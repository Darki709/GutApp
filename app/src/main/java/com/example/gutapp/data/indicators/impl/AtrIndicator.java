package com.example.gutapp.data.indicators.impl;

import android.graphics.Color;
import com.example.gutapp.data.indicators.Indicator;
import com.example.gutapp.data.models.Candle;
import com.github.mikephil.charting.data.Entry;
import java.util.ArrayList;
import java.util.List;

public class AtrIndicator extends Indicator {

    public AtrIndicator() {
        params.add(new Param("period", "Period", Param.Type.INTEGER, 2, 100, 14));
        setColor(Color.parseColor("#FF5722")); // Deep Orange
    }

    @Override public String getId() { return "atr"; }
    @Override public String getDisplayName() { return "Average True Range"; }
    @Override public String getTag() { return "ATR"; }
    @Override public boolean isSubChart() { return true; }
    @Override public Indicator newInstance() { return new AtrIndicator(); }

    @Override
    public Result compute(ArrayList<Candle> candles) {
        int period = (int) getParam("period");
        Result res = new Result();

        if (candles.size() < period) return res;

        List<Float> trValues = new ArrayList<>();
        List<Entry> atrEntries = new ArrayList<>();

        // 1. Calculate True Range (TR)
        for (int i = 0; i < candles.size(); i++) {
            Candle c = candles.get(i);
            if (i == 0) {
                trValues.add((float) (c.high - c.low));
            } else {
                float prevClose = (float) candles.get(i - 1).close;
                float hL = (float) (c.high - c.low);
                float hC = Math.abs((float) c.high - prevClose);
                float lC = Math.abs((float) c.low - prevClose);
                trValues.add(Math.max(hL, Math.max(hC, lC)));
            }
        }

        // 2. Calculate Initial ATR (Simple Moving Average of first 'period' TR values)
        float currentSum = 0;
        for (int i = 0; i < period; i++) {
            currentSum += trValues.get(i);
        }
        float prevAtr = currentSum / period;
        atrEntries.add(new Entry(period - 1, prevAtr));

        // 3. Wilder's Smoothing for subsequent values
        for (int i = period; i < trValues.size(); i++) {
            float currentAtr = (prevAtr * (period - 1) + trValues.get(i)) / period;
            atrEntries.add(new Entry(i, currentAtr));
            prevAtr = currentAtr;
        }

        res.subChartLines.add(makeLineSet(atrEntries, "ATR", getColor(), 1.2f));

        // Dynamic Min/Max for subchart scaling
        res.subChartMin = 0f;
        float maxVal = 0f;
        for (Entry e : atrEntries) if (e.getY() > maxVal) maxVal = e.getY();
        res.subChartMax = maxVal * 1.1f;

        return res;
    }

    @Override
    public int calculateBias(ArrayList<Candle> candles) {
        if (candles == null || candles.size() < 2) return 50;

        Result res = compute(candles);
        if (res.subChartLines.isEmpty()) return 50;

        int entryCount = res.subChartLines.get(0).getEntryCount();
        if (entryCount < 2) return 50;

        try {
            int currentIdx = entryCount - 1;
            int previousIdx = entryCount - 2;

            float currentAtr = res.subChartLines.get(0).getEntryForIndex(currentIdx).getY();
            float prevAtr = res.subChartLines.get(0).getEntryForIndex(previousIdx).getY();

            // ATR Bias Logic:
            // Volatility Expansion (> 5% increase) suggests high conviction in the current move.
            // Volatility Contraction suggests the market is "resting" or consolidating.

            if (currentAtr > prevAtr * 1.05f) {
                return 65; // Volatility Rising (Expanding Bias)
            } else if (currentAtr < prevAtr * 0.95f) {
                return 40; // Volatility Falling (Consolidation)
            }

            return 50; // Stable Volatility

        } catch (Exception e) {
            return 50;
        }
    }
}