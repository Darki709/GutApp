package com.example.gutapp.data.indicators.impl;

import android.graphics.Color;
import com.example.gutapp.data.indicators.Indicator;
import com.example.gutapp.data.models.Candle;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineDataSet;
import java.util.ArrayList;
import java.util.List;

public class McGinleyDynamicIndicator extends Indicator {

    public McGinleyDynamicIndicator() {
        params.add(new Param("period", "Period", Param.Type.INTEGER, 2, 100, 14));
        setColor(Color.parseColor("#9C27B0"));
    }

    @Override public String getId() { return "mcginley"; }
    @Override public String getDisplayName() { return "McGinley Dynamic"; }
    @Override public String getTag() { return "MD"; }
    @Override public boolean isSubChart() { return false; }

    @Override
    public Indicator newInstance() { return new McGinleyDynamicIndicator(); }

    @Override
    public Result compute(ArrayList<Candle> candles) {
        Result r = new Result();
        int period = (int) getParam("period");
        if (candles == null || candles.size() < period) return r;

        List<Entry> entries = new ArrayList<>();

        // Initial MD is just a simple moving average
        double sum = 0;
        for (int i = 0; i < period; i++) sum += candles.get(i).close;
        double md = sum / period;
        entries.add(new Entry(period - 1, (float) md));

        for (int i = period; i < candles.size(); i++) {
            double close = candles.get(i).close;
            double factor = Math.pow(close / md, 4);
            // Protect against division by zero
            if (factor == 0) factor = 1;

            md = md + ((close - md) / (period * factor));
            entries.add(new Entry(i, (float) md));
        }

        r.overlayLines.add(makeLineSet(entries, "MD", getColor(), 1.5f));
        return r;
    }

    @Override
    public int calculateBias(ArrayList<Candle> data) {
        int period = (int) getParam("period");
        if (data == null || data.size() < period) return 50;
        Result r = compute(data);
        if (r.overlayLines.isEmpty()) return 50;

        float currentMd = r.overlayLines.get(0).getEntryForIndex(r.overlayLines.get(0).getEntryCount() - 1).getY();
        double currentClose = data.get(data.size() - 1).close;

        if (currentClose > currentMd) return 75;
        if (currentClose < currentMd) return 25;
        return 50;
    }
}