package com.example.gutapp.data.indicators.impl;

import android.graphics.Color;

import com.example.gutapp.data.indicators.Indicator;
import com.example.gutapp.data.models.Candle;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineDataSet;

import java.util.ArrayList;
import java.util.List;

public class RsiIndicator extends Indicator {

    public RsiIndicator() {
        params.add(new Param("period", "Period", Param.Type.INTEGER, 2, 50, 14));
        params.add(new Param("overbought", "Overbought", Param.Type.INTEGER, 50, 95, 70));
        params.add(new Param("oversold",   "Oversold",   Param.Type.INTEGER, 5,  50, 30));
    }

    @Override public String getId()          { return "rsi"; }
    @Override public String getDisplayName() { return "RSI"; }
    @Override public String getTag()         { return "RSI"; }
    @Override public boolean isSubChart()    { return true; }

    @Override
    public Result compute(ArrayList<Candle> candles) {
        int period = (int) getParam("period");
        if (candles.size() < period + 1) return new Result();

        double avgGain = 0, avgLoss = 0;
        for (int i = 1; i <= period; i++) {
            double change = candles.get(i).close - candles.get(i-1).close;
            if (change > 0) avgGain += change;
            else avgLoss += Math.abs(change);
        }
        avgGain /= period;
        avgLoss /= period;

        List<Entry> entries = new ArrayList<>();
        for (int i = period + 1; i < candles.size(); i++) {
            double change = candles.get(i).close - candles.get(i-1).close;
            double gain   = change > 0 ? change : 0;
            double loss   = change < 0 ? Math.abs(change) : 0;
            avgGain = (avgGain * (period - 1) + gain) / period;
            avgLoss = (avgLoss * (period - 1) + loss) / period;
            double rs  = avgLoss == 0 ? 100 : avgGain / avgLoss;
            double rsi = 100 - (100 / (1 + rs));
            entries.add(new Entry(i, (float) rsi));
        }

        // Overbought/oversold level lines
        float ob = getParam("overbought");
        float os = getParam("oversold");
        List<Entry> obEntries = new ArrayList<>(), osEntries = new ArrayList<>();
        for (Entry e : entries) {
            obEntries.add(new Entry(e.getX(), ob));
            osEntries.add(new Entry(e.getX(), os));
        }

        Result r = new Result();
        r.subChartMin = 0f;
        r.subChartMax = 100f;

        LineDataSet rsiSet = makeLineSet(entries, "RSI(" + period + ")", Color.parseColor("#7C4DFF"), 1.4f);
        LineDataSet obSet  = makeDashedLineSet(obEntries, "OB", Color.argb(100, 239, 83, 80));
        LineDataSet osSet  = makeDashedLineSet(osEntries, "OS", Color.argb(100, 38,  166, 154));
        obSet.setAxisDependency(com.github.mikephil.charting.components.YAxis.AxisDependency.RIGHT);
        osSet.setAxisDependency(com.github.mikephil.charting.components.YAxis.AxisDependency.RIGHT);
        rsiSet.setAxisDependency(com.github.mikephil.charting.components.YAxis.AxisDependency.RIGHT);

        r.subChartLines.add(rsiSet);
        r.subChartLines.add(obSet);
        r.subChartLines.add(osSet);
        return r;
    }
}