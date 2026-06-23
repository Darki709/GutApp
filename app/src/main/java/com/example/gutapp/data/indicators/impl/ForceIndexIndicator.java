package com.example.gutapp.data.indicators.impl;

import android.graphics.Color;
import com.example.gutapp.data.indicators.Indicator;
import com.example.gutapp.data.models.Candle;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineDataSet;
import java.util.ArrayList;
import java.util.List;

public class ForceIndexIndicator extends Indicator {

    public ForceIndexIndicator() {
        params.add(new Param("period", "Smoothing Period", Param.Type.INTEGER, 1, 50, 13));
        setColor(Color.parseColor("#FF9800"));
    }

    @Override public String getId() { return "force_index"; }
    @Override public String getDisplayName() { return "Force Index"; }
    @Override public String getTag() { return "FI"; }
    @Override public boolean isSubChart() { return true; }

    @Override
    public Indicator newInstance() { return new ForceIndexIndicator(); }

    @Override
    public Result compute(ArrayList<Candle> candles) {
        Result r = new Result();
        int period = (int) getParam("period");
        if (candles == null || candles.size() < period + 1) return r;

        double[] rawFi = new double[candles.size()];
        for (int i = 1; i < candles.size(); i++) {
            rawFi[i] = (candles.get(i).close - candles.get(i - 1).close) * candles.get(i).volume;
        }

        // EMA smoothing
        double multiplier = 2.0 / (period + 1);
        double[] smoothedFi = new double[candles.size()];
        double sum = 0;
        for (int i = 1; i <= period; i++) sum += rawFi[i];
        smoothedFi[period] = sum / period;

        List<Entry> entries = new ArrayList<>();
        List<Entry> zeroLine = new ArrayList<>();
        entries.add(new Entry(period, (float) smoothedFi[period]));
        zeroLine.add(new Entry(period, 0f));

        for (int i = period + 1; i < candles.size(); i++) {
            smoothedFi[i] = (rawFi[i] - smoothedFi[i - 1]) * multiplier + smoothedFi[i - 1];
            entries.add(new Entry(i, (float) smoothedFi[i]));
            zeroLine.add(new Entry(i, 0f));
        }

        LineDataSet set = makeLineSet(entries, "FI", getColor(), 1.4f);
        LineDataSet zSet = makeDashedLineSet(zeroLine, "0", Color.GRAY);
        set.setAxisDependency(YAxis.AxisDependency.RIGHT);
        zSet.setAxisDependency(YAxis.AxisDependency.RIGHT);

        r.subChartLines.add(set);
        r.subChartLines.add(zSet);
        return r;
    }

    @Override
    public int calculateBias(ArrayList<Candle> data) {
        int period = (int) getParam("period");
        if (data == null || data.size() < period + 1) return 50;
        Result res = compute(data);
        if(res.subChartLines.isEmpty()) return 50;
        float fi = res.subChartLines.get(0).getEntryForIndex(res.subChartLines.get(0).getEntryCount()-1).getY();
        if (fi > 0) return 75;
        if (fi < 0) return 25;
        return 50;
    }
}