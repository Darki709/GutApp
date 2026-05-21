package com.example.gutapp.data.indicators.impl;

import android.graphics.Color;
import com.example.gutapp.data.indicators.Indicator;
import com.example.gutapp.data.models.Candle;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineDataSet;
import java.util.ArrayList;
import java.util.List;

public class WilliamsRIndicator extends Indicator {

    public WilliamsRIndicator() {
        params.add(new Param("period", "Lookback Period", Param.Type.INTEGER, 2, 100, 14));
        setColor(Color.parseColor("#9C27B0")); // Purple
    }

    @Override public String getId()          { return "williams_r"; }
    @Override public String getDisplayName() { return "Williams %R"; }
    @Override public String getTag()         { return "W%R"; }
    @Override public boolean isSubChart()     { return true; }
    @Override public Indicator newInstance()  { return new WilliamsRIndicator(); }

    @Override
    public Result compute(ArrayList<Candle> candles) {
        Result r = new Result();
        int period = (int) getParam("period");
        if (candles == null || candles.size() < period) return r;

        List<Entry> wrEntries = new ArrayList<>();
        List<Entry> obEntries = new ArrayList<>();
        List<Entry> osEntries = new ArrayList<>();

        for (int i = period - 1; i < candles.size(); i++) {
            double highestHigh = Double.MIN_VALUE;
            double lowestLow = Double.MAX_VALUE;

            for (int j = i - period + 1; j <= i; j++) {
                Candle c = candles.get(j);
                if (c.high > highestHigh) highestHigh = c.high;
                if (c.low < lowestLow) lowestLow = c.low;
            }

            double range = highestHigh - lowestLow;
            float wrValue = -50f; // Neutral default fallback for flat ranges
            if (range != 0) {
                wrValue = (float) (((highestHigh - candles.get(i).close) / range) * -100.0);
            }

            wrEntries.add(new Entry(i, wrValue));
            obEntries.add(new Entry(i, -20f));
            osEntries.add(new Entry(i, -80f));
        }

        r.subChartMin = -100f;
        r.subChartMax = 0f;

        LineDataSet wrSet = makeLineSet(wrEntries, getTag(), getColor(), 1.4f);
        LineDataSet obSet = makeDashedLineSet(obEntries, "OB (-20)", Color.parseColor("#40EF5350"));
        LineDataSet osSet = makeDashedLineSet(osEntries, "OS (-80)", Color.parseColor("#404CAF50"));

        for (LineDataSet set : new LineDataSet[]{wrSet, obSet, osSet}) {
            set.setAxisDependency(YAxis.AxisDependency.RIGHT);
        }

        r.subChartLines.add(wrSet);
        r.subChartLines.add(obSet);
        r.subChartLines.add(osSet);
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

        double range = highestHigh - lowestLow;
        if (range == 0) return 50;

        double wrValue = ((highestHigh - data.get(lastIdx).close) / range) * -100.0;

        // Shift native scale [-100, 0] cleanly to score scale [0, 100]
        int score = (int) Math.round(wrValue + 100.0);
        return Math.max(0, Math.min(100, score));
    }
}