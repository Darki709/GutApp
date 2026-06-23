package com.example.gutapp.data.indicators.impl;

import android.graphics.Color;

import com.example.gutapp.data.indicators.Indicator;
import com.example.gutapp.data.models.Candle;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineDataSet;

import java.util.ArrayList;
import java.util.List;

public class ChoppinessIndicator extends Indicator {

    public ChoppinessIndicator() {
        params.add(new Param("period", "Period", Param.Type.INTEGER, 2, 100, 14));
        setColor(Color.parseColor("#E91E63"));
    }

    @Override public String  getId()          { return "choppiness"; }
    @Override public String  getDisplayName() { return "Choppiness Index"; }
    @Override public String  getTag()         { return "CHOP"; }
    @Override public boolean isSubChart()     { return true; }

    @Override
    public Indicator newInstance() { return new ChoppinessIndicator(); }

    @Override
    public Result compute(ArrayList<Candle> candles) {
        Result r = new Result();
        int period = (int) getParam("period");

        if (candles == null || candles.size() < period + 1) return r;

        // Fix the sub-chart min and max since it is an oscillator
        r.subChartMin = 0f;
        r.subChartMax = 100f;

        List<Entry> chopEntries = new ArrayList<>();
        List<Entry> upperBand = new ArrayList<>();
        List<Entry> lowerBand = new ArrayList<>();

        for (int i = period; i < candles.size(); i++) {
            double sumTr = 0;
            double highestHigh = Double.MIN_VALUE;
            double lowestLow = Double.MAX_VALUE;

            for (int j = i - period + 1; j <= i; j++) {
                double hl = candles.get(j).high - candles.get(j).low;
                double hpc = Math.abs(candles.get(j).high - candles.get(j - 1).close);
                double lpc = Math.abs(candles.get(j).low - candles.get(j - 1).close);
                double tr = Math.max(hl, Math.max(hpc, lpc));
                sumTr += tr;

                if (candles.get(j).high > highestHigh) highestHigh = candles.get(j).high;
                if (candles.get(j).low < lowestLow) lowestLow = candles.get(j).low;
            }

            double range = highestHigh - lowestLow;
            double chop = range == 0 ? 50f : 100.0 * Math.log10(sumTr / range) / Math.log10(period);

            chopEntries.add(new Entry(i, (float) chop));
            upperBand.add(new Entry(i, 61.8f));
            lowerBand.add(new Entry(i, 38.2f));
        }

        LineDataSet chopSet = makeLineSet(chopEntries, "CHOP", getColor(), 1.4f);
        LineDataSet upperSet = makeDashedLineSet(upperBand, "61.8", Color.argb(140, 239, 83, 80));
        LineDataSet lowerSet = makeDashedLineSet(lowerBand, "38.2", Color.argb(140, 76, 175, 80));

        for (LineDataSet s : new LineDataSet[]{chopSet, upperSet, lowerSet}) {
            s.setAxisDependency(YAxis.AxisDependency.RIGHT);
            r.subChartLines.add(s);
        }

        return r;
    }

    @Override
    public int calculateBias(ArrayList<Candle> data) { return 50; } // CHOP measures trendiness, not direction
}