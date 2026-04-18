package com.example.gutapp.data.indicators.impl;

import android.graphics.Color;

import com.example.gutapp.data.indicators.Indicator;
import com.example.gutapp.data.models.Candle;
import com.github.mikephil.charting.data.Entry;

import java.util.ArrayList;
import java.util.List;

public class VwapIndicator extends Indicator {

    public VwapIndicator() {
        // No params — VWAP has no configurable period
    }

    @Override public String getId()          { return "vwap"; }
    @Override public String getDisplayName() { return "VWAP"; }
    @Override public String getTag()         { return "VWAP"; }
    @Override public boolean isSubChart()    { return false; }

    @Override
    public Result compute(ArrayList<Candle> candles) {
        List<Entry> entries = new ArrayList<>();
        double cumTPV = 0, cumVol = 0;
        for (int i = 0; i < candles.size(); i++) {
            Candle c = candles.get(i);
            double tp = (c.high + c.low + c.close) / 3.0;
            cumTPV += tp * c.volume;
            cumVol += c.volume;
            if (cumVol > 0) entries.add(new Entry(i, (float)(cumTPV / cumVol)));
        }
        Result r = new Result();
        r.overlayLines.add(makeLineSet(entries, "VWAP", Color.parseColor("#AB47BC"), 1.6f));
        return r;
    }
}