package com.example.gutapp.data.indicators.impl;

import android.graphics.Color;
import com.example.gutapp.data.indicators.Indicator;
import com.example.gutapp.data.models.Candle;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineDataSet;
import java.util.ArrayList;
import java.util.List;

public class ObvIndicator extends Indicator {

    public ObvIndicator() {
        setColor(Color.parseColor("#1976D2"));
    }

    @Override public String getId() { return "obv"; }
    @Override public String getDisplayName() { return "On-Balance Volume"; }
    @Override public String getTag() { return "OBV"; }
    @Override public boolean isSubChart() { return true; }

    @Override
    public Indicator newInstance() { return new ObvIndicator(); }

    @Override
    public Result compute(ArrayList<Candle> candles) {
        Result r = new Result();
        if (candles == null || candles.isEmpty()) return r;

        List<Entry> entries = new ArrayList<>();
        double obv = 0;
        entries.add(new Entry(0, 0f));

        for (int i = 1; i < candles.size(); i++) {
            double close = candles.get(i).close;
            double prevClose = candles.get(i - 1).close;
            double volume = candles.get(i).volume;

            if (close > prevClose) obv += volume;
            else if (close < prevClose) obv -= volume;

            entries.add(new Entry(i, (float) obv));
        }

        LineDataSet set = makeLineSet(entries, "OBV", getColor(), 1.4f);
        set.setAxisDependency(YAxis.AxisDependency.RIGHT);
        r.subChartLines.add(set);
        return r;
    }

    @Override
    public int calculateBias(ArrayList<Candle> data) {
        if (data == null || data.size() < 2) return 50;
        Result r = compute(data);
        if (r.subChartLines.isEmpty()) return 50;
        LineDataSet set = r.subChartLines.get(0);
        if (set.getEntryCount() < 2) return 50;

        float currentObv = set.getEntryForIndex(set.getEntryCount() - 1).getY();
        float prevObv = set.getEntryForIndex(set.getEntryCount() - 2).getY();

        if (currentObv > prevObv) return 60; // Slightly bullish
        if (currentObv < prevObv) return 40; // Slightly bearish
        return 50;
    }
}