package com.example.gutapp.data.indicators.impl;

import android.graphics.Color;
import com.example.gutapp.data.indicators.Indicator;
import com.example.gutapp.data.models.Candle;
import com.github.mikephil.charting.data.Entry;
import java.util.ArrayList;
import java.util.List;

public class WmaIndicator extends Indicator {
    public WmaIndicator() {
        params.add(new Param("period", "Period", Param.Type.INTEGER, 2, 200, 14));
        setColor(Color.parseColor("#E91E63")); // Pink
    }

    @Override public String getId() { return "wma"; }
    @Override public String getDisplayName() { return "Weighted Moving Average"; }
    @Override public String getTag() { return "WMA"; }
    @Override public boolean isSubChart() { return false; }
    @Override public Indicator newInstance() { return new WmaIndicator(); }

    @Override
    public Result compute(ArrayList<Candle> candles) {
        int period = (int) getParam("period");
        List<Entry> entries = new ArrayList<>();
        if (candles.size() < period) return new Result();

        int sumWeights = (period * (period + 1)) / 2;

        for (int i = period - 1; i < candles.size(); i++) {
            double weightedSum = 0;
            for (int j = 0; j < period; j++) {
                weightedSum += candles.get(i - j).close * (period - j);
            }
            entries.add(new Entry(i, (float) (weightedSum / sumWeights)));
        }

        Result r = new Result();
        r.overlayLines.add(makeLineSet(entries, "WMA(" + period + ")", getColor(), 1.5f));
        return r;
    }

    @Override
    public int calculateBias(ArrayList<Candle> candles) {
        int period = (int) getParam("period");
        if (candles.size() < period + 2) return 50;

        Result res = compute(candles);
        if (res.overlayLines.isEmpty() || res.overlayLines.get(0).getEntryCount() < 2) {
            return 50;
        }

        List<com.github.mikephil.charting.data.Entry> entries = res.overlayLines.get(0).getValues();
        float currentWma = entries.get(entries.size() - 1).getY();
        float prevWma = entries.get(entries.size() - 2).getY();
        float lastClose = (float) candles.get(candles.size() - 1).close;

        int score = 50;

        // 1. Price Position (Trend)
        // If price is above WMA, it's bullish.
        if (lastClose > currentWma) score += 20;
        else score -= 20;

        // 2. Slope of the WMA (Momentum)
        // If the WMA itself is pointing up, the trend is accelerating.
        if (currentWma > prevWma) score += 15;
        else score -= 15;

        // 3. Distance Factor (Overextension)
        // If price is more than 5% away from WMA, it might be overextended.
        float deviation = Math.abs(lastClose - currentWma) / currentWma;
        if (deviation > 0.05f) {
            // Dampen the score slightly if overextended (reversion risk)
            score = (score > 50) ? score - 10 : score + 10;
        }

        return Math.max(0, Math.min(100, score));
    }
}