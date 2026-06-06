package com.example.gutapp.data.indicators.impl;

import android.graphics.Color;
import com.example.gutapp.data.indicators.Indicator;
import com.example.gutapp.data.models.Candle;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineDataSet;
import java.util.ArrayList;
import java.util.List;

public class SmmaIndicator extends Indicator {

    public SmmaIndicator() {
        params.add(new Param("period", "Period", Param.Type.INTEGER, 2, 200, 20));
        setColor(Color.parseColor("#F44336"));
    }

    @Override public String getId() { return "smma"; }
    @Override public String getDisplayName() { return "Smoothed MA (SMMA)"; }
    @Override public String getTag() { return "SMMA"; }
    @Override public boolean isSubChart() { return false; }

    @Override
    public Indicator newInstance() { return new SmmaIndicator(); }

    @Override
    public Result compute(ArrayList<Candle> candles) {
        Result r = new Result();
        int period = (int) getParam("period");
        if (candles == null || candles.size() < period) return r;

        List<Entry> entries = new ArrayList<>();
        double sum = 0;
        for (int i = 0; i < period; i++) sum += candles.get(i).close;

        double prevSmma = sum / period;
        entries.add(new Entry(period - 1, (float) prevSmma));

        for (int i = period; i < candles.size(); i++) {
            double currentSmma = (prevSmma * (period - 1) + candles.get(i).close) / period;
            entries.add(new Entry(i, (float) currentSmma));
            prevSmma = currentSmma;
        }

        r.overlayLines.add(makeLineSet(entries, "SMMA", getColor(), 1.5f));
        return r;
    }

    @Override
    public int calculateBias(ArrayList<Candle> data) {
        int period = (int) getParam("period");
        if (data == null || data.size() < period) return 50;

        Result res = compute(data);
        if(res.overlayLines.isEmpty()) return 50;

        LineDataSet set = res.overlayLines.get(0);
        float smma = set.getEntryForIndex(set.getEntryCount()-1).getY();
        float close = (float) data.get(data.size()-1).close;

        if (close > smma) return 70;
        if (close < smma) return 30;
        return 50;
    }
}