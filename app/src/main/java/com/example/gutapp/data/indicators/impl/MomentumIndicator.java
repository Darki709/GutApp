package com.example.gutapp.data.indicators.impl;

import android.graphics.Color;

import com.example.gutapp.data.indicators.Indicator;
import com.example.gutapp.data.models.Candle;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineDataSet;

import java.util.ArrayList;
import java.util.List;

public class MomentumIndicator extends Indicator {

    public MomentumIndicator() {
        // Defines the period parameter, defaulting to 10
        params.add(new Param("period", "Period", Param.Type.INTEGER, 2, 100, 10));
        setColor(Color.parseColor("#00BCD4"));
    }

    @Override public String  getId()          { return "momentum"; }
    @Override public String  getDisplayName() { return "Momentum"; }
    @Override public String  getTag()         { return "MOM"; }
    @Override public boolean isSubChart()     { return true; }

    @Override
    public Indicator newInstance() { return new MomentumIndicator(); }

    @Override
    public Result compute(ArrayList<Candle> candles) {
        Result r = new Result();
        int period = (int) getParam("period");

        // Return empty result if insufficient data
        if (candles == null || candles.size() < period + 1) return r;

        List<Entry> entries = new ArrayList<>();
        for (int i = period; i < candles.size(); i++) {
            double momentum = candles.get(i).close - candles.get(i - period).close;
            // X coordinate must be the array index, not the timestamp
            entries.add(new Entry(i, (float) momentum));
        }

        // Create line set and set axis dependency for sub-chart
        LineDataSet set = makeLineSet(entries, "MOM", getColor(), 1.4f);
        set.setAxisDependency(YAxis.AxisDependency.RIGHT);
        r.subChartLines.add(set);

        return r;
    }

    @Override
    public int calculateBias(ArrayList<Candle> data) {
        int period = (int) getParam("period");
        if (data == null || data.size() < period + 1) return 50;

        double currentClose = data.get(data.size() - 1).close;
        double pastClose = data.get(data.size() - 1 - period).close;

        // Returns 0-100 range for the bias dashboard based on momentum direction
        if (currentClose > pastClose) return 75;
        if (currentClose < pastClose) return 25;
        return 50;
    }
}