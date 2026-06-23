package com.example.gutapp.data.indicators.impl;

import android.graphics.Color;
import com.example.gutapp.data.indicators.Indicator;
import com.example.gutapp.data.models.Candle;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineDataSet;
import java.util.ArrayList;
import java.util.List;

public class TrueRangeIndicator extends Indicator {

    public TrueRangeIndicator() {
        setColor(Color.parseColor("#795548"));
    }

    @Override public String getId() { return "true_range"; }
    @Override public String getDisplayName() { return "True Range"; }
    @Override public String getTag() { return "TR"; }
    @Override public boolean isSubChart() { return true; }

    @Override
    public Indicator newInstance() { return new TrueRangeIndicator(); }

    @Override
    public Result compute(ArrayList<Candle> candles) {
        Result r = new Result();
        if (candles == null || candles.size() < 2) return r;

        List<Entry> entries = new ArrayList<>();
        for (int i = 1; i < candles.size(); i++) {
            Candle curr = candles.get(i);
            Candle prev = candles.get(i - 1);
            double hl = curr.high - curr.low;
            double hpc = Math.abs(curr.high - prev.close);
            double lpc = Math.abs(curr.low - prev.close);
            double tr = Math.max(hl, Math.max(hpc, lpc));
            entries.add(new Entry(i, (float) tr));
        }

        LineDataSet set = makeLineSet(entries, "TR", getColor(), 1.4f);
        set.setAxisDependency(YAxis.AxisDependency.RIGHT);
        r.subChartLines.add(set);
        return r;
    }

    @Override
    public int calculateBias(ArrayList<Candle> data) {
        return 50; // Pure volatility measure, neutral default
    }
}