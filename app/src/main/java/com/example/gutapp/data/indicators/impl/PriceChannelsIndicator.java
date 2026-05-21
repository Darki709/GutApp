package com.example.gutapp.data.indicators.impl;

import android.graphics.Color;
import com.example.gutapp.data.indicators.Indicator;
import com.example.gutapp.data.models.Candle;
import com.github.mikephil.charting.data.Entry;
import java.util.ArrayList;
import java.util.List;

public class PriceChannelsIndicator extends Indicator {

    public PriceChannelsIndicator() {
        params.add(new Param("period", "Channel Window", Param.Type.INTEGER, 2, 100, 20));
        setColor(Color.parseColor("#FF9800")); // Amber Orange
    }

    @Override public String getId()          { return "price_channels"; }
    @Override public String getDisplayName() { return "Price Channels"; }
    @Override public String getTag()         { return "PC"; }
    @Override public boolean isSubChart()     { return false; }
    @Override public Indicator newInstance()  { return new PriceChannelsIndicator(); }

    @Override
    public Result compute(ArrayList<Candle> candles) {
        Result r = new Result();
        int period = (int) getParam("period");
        if (candles == null || candles.size() < period) return r;

        List<Entry> upperBounds = new ArrayList<>();
        List<Entry> lowerBounds = new ArrayList<>();

        for (int i = period - 1; i < candles.size(); i++) {
            double highestHigh = Double.MIN_VALUE;
            double lowestLow = Double.MAX_VALUE;

            for (int j = i - period + 1; j <= i; j++) {
                Candle c = candles.get(j);
                if (c.high > highestHigh) highestHigh = c.high;
                if (c.low < lowestLow) lowestLow = c.low;
            }

            upperBounds.add(new Entry(i, (float) highestHigh));
            lowerBounds.add(new Entry(i, (float) lowestLow));
        }

        r.overlayLines.add(makeLineSet(upperBounds, "PC Upper", getColor(), 1.2f));
        r.overlayLines.add(makeLineSet(lowerBounds, "PC Lower", getColor(), 1.2f));
        return r;
    }

    @Override
    public int calculateBias(ArrayList<Candle> data) {
        int period = (int) getParam("period");
        if (data == null || data.size() < period) return 50;

        int lastIdx = data.size() - 1;
        double highestHigh = Double.MIN_VALUE;
        double lowestLow = Double.MAX_VALUE;

        for (int j = lastIdx - period + 1; j <= lastIdx; j++) {
            Candle c = data.get(j);
            if (c.high > highestHigh) highestHigh = c.high;
            if (c.low < lowestLow) lowestLow = c.low;
        }

        double width = highestHigh - lowestLow;
        if (width == 0) return 50;

        double positionRatio = (data.get(lastIdx).close - lowestLow) / width;
        int score = (int) Math.round(positionRatio * 100.0);
        return Math.max(0, Math.min(100, score));
    }
}