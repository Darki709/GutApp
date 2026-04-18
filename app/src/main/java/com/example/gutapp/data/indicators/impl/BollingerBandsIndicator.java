package com.example.gutapp.data.indicators.impl;

import android.graphics.Color;

import com.example.gutapp.data.indicators.Indicator;
import com.example.gutapp.data.models.Candle;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineDataSet;

import java.util.ArrayList;
import java.util.List;

public class BollingerBandsIndicator extends Indicator {

    public BollingerBandsIndicator() {
        params.add(new Param("period", "Period",     Param.Type.INTEGER, 5,  50,  20));
        params.add(new Param("stddev", "Std Dev",    Param.Type.FLOAT,   1f, 3f, 2f));
    }

    @Override public String getId()          { return "bb"; }
    @Override public String getDisplayName() { return "Bollinger Bands"; }
    @Override public String getTag()         { return "BB"; }
    @Override public boolean isSubChart()    { return false; }

    @Override
    public Result compute(ArrayList<Candle> candles) {
        int period     = (int) getParam("period");
        double stdMult = getParam("stddev");

        List<Entry> upper = new ArrayList<>(), mid = new ArrayList<>(), lower = new ArrayList<>();
        for (int i = period - 1; i < candles.size(); i++) {
            double sum = 0;
            for (int j = i - period + 1; j <= i; j++) sum += candles.get(j).close;
            double ma = sum / period;
            double variance = 0;
            for (int j = i - period + 1; j <= i; j++) {
                double diff = candles.get(j).close - ma;
                variance += diff * diff;
            }
            double stdDev = Math.sqrt(variance / period);
            upper.add(new Entry(i, (float)(ma + stdMult * stdDev)));
            mid.add(  new Entry(i, (float) ma));
            lower.add(new Entry(i, (float)(ma - stdMult * stdDev)));
        }

        int bbColor = Color.parseColor("#4DD0E1");
        int midColor = Color.argb(120, 77, 208, 225);

        Result r = new Result();
        r.overlayLines.add(makeDashedLineSet(upper, "BB Upper", bbColor));
        r.overlayLines.add(makeDashedLineSet(lower, "BB Lower", bbColor));
        r.overlayLines.add(makeDashedLineSet(mid,   "BB Mid",   midColor));
        return r;
    }
}