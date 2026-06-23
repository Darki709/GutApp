package com.example.gutapp.data.indicators.impl;

import android.graphics.Color;
import com.example.gutapp.data.indicators.Indicator;
import com.example.gutapp.data.models.Candle;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineDataSet;
import java.util.ArrayList;
import java.util.List;

public class DisparityIndexIndicator extends Indicator {

    public DisparityIndexIndicator() {
        params.add(new Param("period", "MA Period", Param.Type.INTEGER, 2, 200, 14));
        setColor(Color.parseColor("#00BCD4"));
    }

    @Override public String getId() { return "disparity_index"; }
    @Override public String getDisplayName() { return "Disparity Index"; }
    @Override public String getTag() { return "DI"; }
    @Override public boolean isSubChart() { return true; }

    @Override
    public Indicator newInstance() { return new DisparityIndexIndicator(); }

    @Override
    public Result compute(ArrayList<Candle> candles) {
        Result r = new Result();
        int period = (int) getParam("period");
        if (candles == null || candles.size() < period) return r;

        List<Entry> entries = new ArrayList<>();
        List<Entry> zeroLine = new ArrayList<>();

        for (int i = period - 1; i < candles.size(); i++) {
            double sum = 0;
            for (int j = i - period + 1; j <= i; j++) sum += candles.get(j).close;
            double sma = sum / period;

            double disparity = sma == 0 ? 0 : ((candles.get(i).close - sma) / sma) * 100.0;

            entries.add(new Entry(i, (float) disparity));
            zeroLine.add(new Entry(i, 0f));
        }

        LineDataSet set = makeLineSet(entries, "DI", getColor(), 1.4f);
        LineDataSet zeroSet = makeDashedLineSet(zeroLine, "0", Color.GRAY);
        set.setAxisDependency(YAxis.AxisDependency.RIGHT);
        zeroSet.setAxisDependency(YAxis.AxisDependency.RIGHT);

        r.subChartLines.add(set);
        r.subChartLines.add(zeroSet);
        return r;
    }

    @Override
    public int calculateBias(ArrayList<Candle> data) {
        int period = (int) getParam("period");
        if (data == null || data.size() < period) return 50;
        Result r = compute(data);
        if (r.subChartLines.isEmpty()) return 50;

        float currentDi = r.subChartLines.get(0).getEntryForIndex(r.subChartLines.get(0).getEntryCount() - 1).getY();
        if (currentDi > 0) return 65;
        if (currentDi < 0) return 35;
        return 50;
    }
}