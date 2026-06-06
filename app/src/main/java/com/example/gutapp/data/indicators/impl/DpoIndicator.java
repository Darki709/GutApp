package com.example.gutapp.data.indicators.impl;

import android.graphics.Color;
import com.example.gutapp.data.indicators.Indicator;
import com.example.gutapp.data.models.Candle;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineDataSet;
import java.util.ArrayList;
import java.util.List;

public class DpoIndicator extends Indicator {

    public DpoIndicator() {
        params.add(new Param("period", "Lookback Period", Param.Type.INTEGER, 3, 100, 20));
        setColor(Color.parseColor("#26A69A"));
    }

    @Override public String getId()          { return "dpo"; }
    @Override public String getDisplayName() { return "Detrended Price Oscillator"; }
    @Override public String getTag()         { return "DPO"; }
    @Override public boolean isSubChart()     { return true; }
    @Override public Indicator newInstance()  { return new DpoIndicator(); }

    @Override
    public Result compute(ArrayList<Candle> candles) {
        Result r = new Result();
        int period = (int) getParam("period");
        int shift = (period / 2) + 1;

        if (candles == null || candles.size() < (period + shift)) return r;

        List<Entry> dpoEntries = new ArrayList<>();
        List<Entry> baseLine = new ArrayList<>();

        for (int i = period - 1; i < candles.size() - shift; i++) {
            double sum = 0.0;
            for (int j = i - period + 1; j <= i; j++) {
                sum += candles.get(j).close;
            }
            double sma = sum / period;
            int targetCandleIdx = i + shift;

            dpoEntries.add(new Entry(targetCandleIdx, (float) (candles.get(targetCandleIdx).close - sma)));
            baseLine.add(new Entry(targetCandleIdx, 0f));
        }

        LineDataSet set = makeLineSet(dpoEntries, getTag(), getColor(), 1.4f);
        LineDataSet line = makeDashedLineSet(baseLine, "Zero Base", Color.argb(90, 255, 255, 255));

        set.setAxisDependency(YAxis.AxisDependency.RIGHT);
        line.setAxisDependency(YAxis.AxisDependency.RIGHT);

        r.subChartLines.add(set);
        r.subChartLines.add(line);
        return r;
    }

    @Override
    public int calculateBias(ArrayList<Candle> data) {
        if (data == null || data.size() < 2) return 50;
        return data.get(data.size() - 1).close > data.get(data.size() - 2).close ? 70 : 30;
    }
}