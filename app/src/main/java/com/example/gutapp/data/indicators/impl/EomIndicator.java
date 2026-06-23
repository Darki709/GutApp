package com.example.gutapp.data.indicators.impl;

import android.graphics.Color;
import com.example.gutapp.data.indicators.Indicator;
import com.example.gutapp.data.models.Candle;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineDataSet;
import java.util.ArrayList;
import java.util.List;

public class EomIndicator extends Indicator {

    public EomIndicator() {
        params.add(new Param("period", "Smoothing Period", Param.Type.INTEGER, 2, 50, 14));
        params.add(new Param("divisor", "Volume Divisor", Param.Type.FLOAT, 1000f, 10000000f, 10000f));
        setColor(Color.parseColor("#AB47BC"));
    }

    @Override public String getId()          { return "ease_of_movement"; }
    @Override public String getDisplayName() { return "Ease of Movement"; }
    @Override public String getTag()         { return "EOM"; }
    @Override public boolean isSubChart()     { return true; }
    @Override public Indicator newInstance()  { return new EomIndicator(); }

    @Override
    public Result compute(ArrayList<Candle> candles) {
        Result r = new Result();
        int period = (int) getParam("period");
        float divisor = getParam("divisor");
        if (candles == null || candles.size() < Math.max(2, period)) return r;

        int len = candles.size();
        double[] rawEom = new double[len];

        for (int i = 1; i < len; i++) {
            Candle curr = candles.get(i);
            Candle prev = candles.get(i - 1);

            double midpointMove = ((curr.high + curr.low) / 2.0) - ((prev.high + prev.low) / 2.0);
            double boxRatio = 0.0;
            double range = curr.high - curr.low;

            if (range != 0.0) {
                boxRatio = (curr.volume / divisor) / range;
            }

            rawEom[i] = boxRatio != 0.0 ? midpointMove / boxRatio : 0.0;
        }

        // Apply Simple Moving Average smoothing to clean up raw values
        List<Entry> smoothedEntries = new ArrayList<>();
        List<Entry> baselineEntries = new ArrayList<>();

        for (int i = period; i < len; i++) {
            double sum = 0.0;
            for (int j = i - period + 1; j <= i; j++) {
                sum += rawEom[j];
            }
            smoothedEntries.add(new Entry(i, (float) (sum / period)));
            baselineEntries.add(new Entry(i, 0f));
        }

        LineDataSet set = makeLineSet(smoothedEntries, getTag(), getColor(), 1.4f);
        LineDataSet base = makeDashedLineSet(baselineEntries, "Zero", Color.argb(80, 255, 255, 255));

        set.setAxisDependency(YAxis.AxisDependency.RIGHT);
        base.setAxisDependency(YAxis.AxisDependency.RIGHT);

        r.subChartLines.add(set);
        r.subChartLines.add(base);
        return r;
    }

    @Override
    public int calculateBias(ArrayList<Candle> data) {
        if (data == null || data.size() < 2) return 50;
        Candle curr = data.get(data.size() - 1);
        Candle prev = data.get(data.size() - 2);
        return ((curr.high + curr.low) / 2.0) > ((prev.high + prev.low) / 2.0) ? 75 : 25;
    }
}