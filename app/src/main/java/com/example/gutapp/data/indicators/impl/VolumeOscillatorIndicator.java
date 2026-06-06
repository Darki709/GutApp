package com.example.gutapp.data.indicators.impl;

import android.graphics.Color;

import com.example.gutapp.data.indicators.Indicator;
import com.example.gutapp.data.models.Candle;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineDataSet;

import java.util.ArrayList;
import java.util.List;

public class VolumeOscillatorIndicator extends Indicator {

    public VolumeOscillatorIndicator() {
        params.add(new Param("shortPeriod", "Short MA", Param.Type.INTEGER, 2, 50, 14));
        params.add(new Param("longPeriod", "Long MA", Param.Type.INTEGER, 5, 100, 28));
        setColor(Color.parseColor("#009688"));
    }

    @Override public String  getId()          { return "vol_osc"; }
    @Override public String  getDisplayName() { return "Volume Oscillator"; }
    @Override public String  getTag()         { return "VO"; }
    @Override public boolean isSubChart()     { return true; }

    @Override
    public Indicator newInstance() { return new VolumeOscillatorIndicator(); }

    @Override
    public Result compute(ArrayList<Candle> candles) {
        Result r = new Result();
        int shortPeriod = (int) getParam("shortPeriod");
        int longPeriod = (int) getParam("longPeriod");

        if (candles == null || candles.size() < longPeriod) return r;

        List<Entry> entries = new ArrayList<>();
        List<Entry> zeroLine = new ArrayList<>();

        for (int i = longPeriod - 1; i < candles.size(); i++) {
            double shortSum = 0;
            for (int j = i - shortPeriod + 1; j <= i; j++) shortSum += candles.get(j).volume;
            double shortSma = shortSum / shortPeriod;

            double longSum = 0;
            for (int j = i - longPeriod + 1; j <= i; j++) longSum += candles.get(j).volume;
            double longSma = longSum / longPeriod;

            double vo = longSma == 0 ? 0 : ((shortSma - longSma) / longSma) * 100.0;
            entries.add(new Entry(i, (float) vo));
            zeroLine.add(new Entry(i, 0f));
        }

        LineDataSet set = makeLineSet(entries, "VO", getColor(), 1.4f);
        LineDataSet zeroSet = makeDashedLineSet(zeroLine, "Zero", Color.GRAY);

        set.setAxisDependency(YAxis.AxisDependency.RIGHT);
        zeroSet.setAxisDependency(YAxis.AxisDependency.RIGHT);

        // Add lines to sub-chart
        r.subChartLines.add(set);
        r.subChartLines.add(zeroSet);
        return r;
    }

    @Override
    public int calculateBias(ArrayList<Candle> data) { return 50; } // Volume doesn't strictly give directional bias
}