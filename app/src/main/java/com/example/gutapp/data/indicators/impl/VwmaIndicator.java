package com.example.gutapp.data.indicators.impl;

import android.graphics.Color;
import com.example.gutapp.data.indicators.Indicator;
import com.example.gutapp.data.models.Candle;
import com.github.mikephil.charting.data.Entry;
import java.util.ArrayList;
import java.util.List;

public class VwmaIndicator extends Indicator {

    public VwmaIndicator() {
        params.add(new Param("period", "Period", Param.Type.INTEGER, 2, 200, 20));
        setColor(Color.parseColor("#FFCA28"));
    }

    @Override public String getId()          { return "vwma"; }
    @Override public String getDisplayName() { return "Volume-Weighted Moving Average"; }
    @Override public String getTag()         { return "VWMA"; }
    @Override public boolean isSubChart()     { return false; }
    @Override public Indicator newInstance()  { return new VwmaIndicator(); }

    @Override
    public Result compute(ArrayList<Candle> candles) {
        Result r = new Result();
        int period = (int) getParam("period");
        if (candles == null || candles.size() < period) return r;

        List<Entry> entries = new ArrayList<>();

        for (int i = period - 1; i < candles.size(); i++) {
            double priceVolumeSum = 0.0;
            double totalVolume = 0.0;

            for (int j = i - period + 1; j <= i; j++) {
                Candle c = candles.get(j);
                priceVolumeSum += c.close * c.volume;
                totalVolume += c.volume;
            }

            float vwmaValue = totalVolume != 0.0 ? (float) (priceVolumeSum / totalVolume) : (float) candles.get(i).close;
            entries.add(new Entry(i, vwmaValue));
        }

        r.overlayLines.add(makeLineSet(entries, getTag(), getColor(), 1.5f));
        return r;
    }

    @Override
    public int calculateBias(ArrayList<Candle> data) {
        int period = (int) getParam("period");
        if (data == null || data.size() < period) return 50;

        int n = data.size();
        double priceVolumeSum = 0.0;
        double totalVolume = 0.0;

        for (int j = n - period; j < n; j++) {
            Candle c = data.get(j);
            priceVolumeSum += c.close * c.volume;
            totalVolume += c.volume;
        }

        double vwma = totalVolume != 0.0 ? priceVolumeSum / totalVolume : data.get(n - 1).close;
        return data.get(n - 1).close > vwma ? 75 : 25;
    }
}