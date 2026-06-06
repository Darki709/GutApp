package com.example.gutapp.data.indicators.impl;

import android.graphics.Color;

import com.example.gutapp.data.indicators.Indicator;
import com.example.gutapp.data.models.Candle;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineDataSet;

import java.util.ArrayList;
import java.util.List;

public class EnvelopesIndicator extends Indicator {

    public EnvelopesIndicator() {
        // Defines parameters for period and percentage shift
        params.add(new Param("period", "Period", Param.Type.INTEGER, 2, 200, 20));
        params.add(new Param("shift", "Shift %", Param.Type.FLOAT, 0.1f, 20f, 5f));
        setColor(Color.parseColor("#3F51B5"));
    }

    @Override public String  getId()          { return "envelopes"; }
    @Override public String  getDisplayName() { return "MA Envelopes"; }
    @Override public String  getTag()         { return "ENV"; }
    @Override public boolean isSubChart()     { return false; }

    @Override
    public Indicator newInstance() { return new EnvelopesIndicator(); }

    @Override
    public Result compute(ArrayList<Candle> candles) {
        Result r = new Result();
        int period = (int) getParam("period");
        float shift = getParam("shift") / 100f;

        // Return empty result if data is insufficient
        if (candles == null || candles.size() < period) return r;

        List<Entry> midEntries = new ArrayList<>();
        List<Entry> upperEntries = new ArrayList<>();
        List<Entry> lowerEntries = new ArrayList<>();

        for (int i = period - 1; i < candles.size(); i++) {
            double sum = 0;
            for (int j = i - period + 1; j <= i; j++) {
                sum += candles.get(j).close;
            }
            double sma = sum / period;
            double upper = sma * (1.0 + shift);
            double lower = sma * (1.0 - shift);

            // Use array index 'i' for X coordinate
            midEntries.add(new Entry(i, (float) sma));
            upperEntries.add(new Entry(i, (float) upper));
            lowerEntries.add(new Entry(i, (float) lower));
        }

        int mainColor = getColor();
        int bandColor = Color.argb(150, Color.red(mainColor), Color.green(mainColor), Color.blue(mainColor));

        // Add to overlayLines
        r.overlayLines.add(makeLineSet(midEntries, "ENV Mid", mainColor, 1.4f));
        r.overlayLines.add(makeDashedLineSet(upperEntries, "ENV Upper", bandColor));
        r.overlayLines.add(makeDashedLineSet(lowerEntries, "ENV Lower", bandColor));

        return r;
    }

    @Override
    public int calculateBias(ArrayList<Candle> data) {
        int period = (int) getParam("period");
        if (data == null || data.size() < period) return 50;

        int lastIdx = data.size() - 1;
        double sum = 0;
        for (int i = lastIdx - period + 1; i <= lastIdx; i++) {
            sum += data.get(i).close;
        }
        double sma = sum / period;
        double close = data.get(lastIdx).close;

        // Bias scoring based on relation to the moving average
        if (close > sma) return 75;
        if (close < sma) return 25;
        return 50;
    }
}