package com.example.gutapp.data.indicators.impl;

import android.graphics.Color;
import com.example.gutapp.data.indicators.Indicator;
import com.example.gutapp.data.models.Candle;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineDataSet;
import java.util.ArrayList;
import java.util.List;

public class RocIndicator extends Indicator {

    public RocIndicator() {
        params.add(new Param("period", "ROC Period", Param.Type.INTEGER, 2, 100, 14));
        setColor(Color.parseColor("#E91E63")); // Vibrant Pink
    }

    @Override public String getId()          { return "roc"; }
    @Override public String getDisplayName() { return "Rate of Change"; }
    @Override public String getTag()         { return "ROC"; }
    @Override public boolean isSubChart()     { return true; }
    @Override public Indicator newInstance()  { return new RocIndicator(); }

    @Override
    public Result compute(ArrayList<Candle> candles) {
        Result r = new Result();
        int period = (int) getParam("period");
        if (candles == null || candles.size() < period) return r;

        List<Entry> rocEntries = new ArrayList<>();
        List<Entry> zeroEntries = new ArrayList<>();

        for (int i = period; i < candles.size(); i++) {
            double historicalClose = candles.get(i - period).close;
            float rocValue = 0f;
            if (historicalClose != 0) {
                rocValue = (float) (((candles.get(i).close - historicalClose) / historicalClose) * 100.0);
            }
            rocEntries.add(new Entry(i, rocValue));
            zeroEntries.add(new Entry(i, 0f));
        }

        LineDataSet rocSet = makeLineSet(rocEntries, getTag(), getColor(), 1.4f);
        LineDataSet zeroSet = makeDashedLineSet(zeroEntries, "Baseline", Color.parseColor("#509E9E9E"));

        rocSet.setAxisDependency(YAxis.AxisDependency.RIGHT);
        zeroSet.setAxisDependency(YAxis.AxisDependency.RIGHT);

        r.subChartLines.add(rocSet);
        r.subChartLines.add(zeroSet);
        return r;
    }

    @Override
    public int calculateBias(ArrayList<Candle> data) {
        int period = (int) getParam("period");
        if (data == null || data.size() < period) return 50;

        int lastIdx = data.size() - 1;
        double historicalClose = data.get(lastIdx - period).close;
        if (historicalClose == 0) return 50;

        double rocValue = ((data.get(lastIdx).close - historicalClose) / historicalClose) * 100.0;

        // Map a +/- 10% rate of change movement smoothly onto a 0 to 100 range scale
        double normalized = ((rocValue + 10.0) / 20.0) * 100.0;

        int score = (int) Math.round(normalized);
        return Math.max(0, Math.min(100, score));
    }
}