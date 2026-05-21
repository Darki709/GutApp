package com.example.gutapp.data.indicators.impl;

import android.graphics.Color;
import com.example.gutapp.data.indicators.Indicator;
import com.example.gutapp.data.models.Candle;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineDataSet;
import java.util.ArrayList;
import java.util.List;

public class ApoIndicator extends Indicator {

    public ApoIndicator() {
        params.add(new Param("fastPeriod", "Fast EMA", Param.Type.INTEGER, 2, 50, 10));
        params.add(new Param("slowPeriod", "Slow EMA", Param.Type.INTEGER, 5, 200, 20));
        setColor(Color.parseColor("#FF5722")); // Deep Orange
    }

    @Override public String getId()          { return "apo"; }
    @Override public String getDisplayName() { return "Absolute Price Oscillator"; }
    @Override public String getTag()         { return "APO"; }
    @Override public boolean isSubChart()     { return true; }
    @Override public Indicator newInstance()  { return new ApoIndicator(); }

    @Override
    public Result compute(ArrayList<Candle> candles) {
        Result r = new Result();
        int fastP = (int) getParam("fastPeriod");
        int slowP = (int) getParam("slowPeriod");

        int maxRequired = Math.max(fastP, slowP);
        if (candles == null || candles.size() < maxRequired) return r;

        double fastK = 2.0 / (fastP + 1);
        double slowK = 2.0 / (slowP + 1);

        double fastEma = candles.get(0).close;
        double slowEma = candles.get(0).close;

        List<Entry> apoEntries = new ArrayList<>();
        List<Entry> zeroEntries = new ArrayList<>();

        for (int i = 0; i < candles.size(); i++) {
            double close = candles.get(i).close;
            fastEma = (close * fastK) + (fastEma * (1.0 - fastK));
            slowEma = (close * slowK) + (slowEma * (1.0 - slowK));

            if (i >= maxRequired - 1) {
                float apoValue = (float) (fastEma - slowEma);
                apoEntries.add(new Entry(i, apoValue));
                zeroEntries.add(new Entry(i, 0f));
            }
        }

        LineDataSet apoSet = makeLineSet(apoEntries, getTag(), getColor(), 1.4f);
        LineDataSet zeroSet = makeDashedLineSet(zeroEntries, "Center Line", Color.parseColor("#409E9E9E"));

        apoSet.setAxisDependency(YAxis.AxisDependency.RIGHT);
        zeroSet.setAxisDependency(YAxis.AxisDependency.RIGHT);

        r.subChartLines.add(apoSet);
        r.subChartLines.add(zeroSet);
        return r;
    }

    @Override
    public int calculateBias(ArrayList<Candle> data) {
        int fastP = (int) getParam("fastPeriod");
        int slowP = (int) getParam("slowPeriod");
        int maxRequired = Math.max(fastP, slowP);

        if (data == null || data.size() < maxRequired) return 50;

        double fastK = 2.0 / (fastP + 1);
        double slowK = 2.0 / (slowP + 1);

        double fastEma = data.get(0).close;
        double slowEma = data.get(0).close;

        int lastIdx = data.size() - 1;
        for (int i = 0; i <= lastIdx; i++) {
            double close = data.get(i).close;
            fastEma = (close * fastK) + (fastEma * (1.0 - fastK));
            slowEma = (close * slowK) + (slowEma * (1.0 - slowK));
        }

        double apo = fastEma - slowEma;
        double assetPrice = data.get(lastIdx).close;
        if (assetPrice == 0) return 50;

        // Percentage tracking difference
        double percentageDiff = (apo / assetPrice) * 100.0;

        // Map +/- 2.5% structural distance divergence onto the 0 to 100 spectrum
        double normalized = ((percentageDiff + 2.5) / 5.0) * 100.0;

        int score = (int) Math.round(normalized);
        return Math.max(0, Math.min(100, score));
    }
}