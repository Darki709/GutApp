package com.example.gutapp.data.indicators.impl;

import android.graphics.Color;
import com.example.gutapp.data.indicators.Indicator;
import com.example.gutapp.data.models.Candle;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineDataSet;
import java.util.ArrayList;
import java.util.List;

public class AdlIndicator extends Indicator {

    public AdlIndicator() {
        setColor(Color.parseColor("#2196F3"));
    }

    @Override public String getId()          { return "adl"; }
    @Override public String getDisplayName() { return "Accumulation/Distribution Line"; }
    @Override public String getTag()         { return "ADL"; }
    @Override public boolean isSubChart()     { return true; }
    @Override public Indicator newInstance()  { return new AdlIndicator(); }

    @Override
    public Result compute(ArrayList<Candle> candles) {
        Result r = new Result();
        if (candles == null || candles.isEmpty()) return r;

        List<Entry> entries = new ArrayList<>();
        double cumulativeSum = 0.0;

        for (int i = 0; i < candles.size(); i++) {
            Candle c = candles.get(i);
            double highMinusLow = c.high - c.low;
            double moneyFlowMultiplier = 0.0;

            if (highMinusLow != 0.0) {
                moneyFlowMultiplier = ((c.close - c.low) - (c.high - c.close)) / highMinusLow;
            }

            double moneyFlowVolume = moneyFlowMultiplier * c.volume;
            cumulativeSum += moneyFlowVolume;
            entries.add(new Entry(i, (float) cumulativeSum));
        }

        LineDataSet set = makeLineSet(entries, getTag(), getColor(), 1.4f);
        set.setAxisDependency(YAxis.AxisDependency.RIGHT);
        r.subChartLines.add(set);
        return r;
    }

    @Override
    public int calculateBias(ArrayList<Candle> data) {
        if (data == null || data.size() < 3) return 50;

        // Check the slope of the ADL over the last 3 candles
        int n = data.size();
        double currentMultiplier = data.get(n - 1).high != data.get(n - 1).low ?
                ((data.get(n - 1).close - data.get(n - 1).low) - (data.get(n - 1).high - data.get(n - 1).close)) / (data.get(n - 1).high - data.get(n - 1).low) : 0;

        if (currentMultiplier > 0.2) return 75;
        if (currentMultiplier < -0.2) return 25;
        return 50;
    }
}