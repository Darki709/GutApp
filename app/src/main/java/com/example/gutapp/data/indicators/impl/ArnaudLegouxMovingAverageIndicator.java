package com.example.gutapp.data.indicators.impl;

import android.graphics.Color;
import com.example.gutapp.data.indicators.Indicator;
import com.example.gutapp.data.models.Candle;
import com.github.mikephil.charting.data.Entry;
import java.util.ArrayList;
import java.util.List;

public class ArnaudLegouxMovingAverageIndicator extends Indicator {

    public ArnaudLegouxMovingAverageIndicator() {
        params.add(new Param("period", "Window size", Param.Type.INTEGER, 3, 100, 9));
        params.add(new Param("offset", "Offset Shift", Param.Type.FLOAT,   0.01f, 1.0f, 0.85f));
        params.add(new Param("sigma",  "Sigma",        Param.Type.FLOAT,   1.0f, 20.0f, 6.0f));
        setColor(Color.parseColor("#E040FB"));
    }

    @Override public String getId()          { return "alma"; }
    @Override public String getDisplayName() { return "Arnaud Legoux Moving Average"; }
    @Override public String getTag()         { return "ALMA"; }
    @Override public boolean isSubChart()     { return false; }
    @Override public Indicator newInstance()  { return new ArnaudLegouxMovingAverageIndicator(); }

    @Override
    public Result compute(ArrayList<Candle> candles) {
        Result r = new Result();
        int period = (int) getParam("period");
        float offset = getParam("offset");
        float sigma = getParam("sigma");

        if (candles == null || candles.size() < period) return r;

        double m = offset * (period - 1);
        double s = period / sigma;
        double[] weights = new double[period];
        double weightSum = 0.0;

        for (int i = 0; i < period; i++) {
            weights[i] = Math.exp(-Math.pow(i - m, 2) / (2 * Math.pow(s, 2)));
            weightSum += weights[i];
        }

        List<Entry> entries = new ArrayList<>();

        for (int i = period - 1; i < candles.size(); i++) {
            double sum = 0.0;
            int wIdx = 0;
            for (int j = i - period + 1; j <= i; j++) {
                sum += candles.get(j).close * weights[wIdx++];
            }
            entries.add(new Entry(i, (float) (sum / weightSum)));
        }

        r.overlayLines.add(makeLineSet(entries, getTag(), getColor(), 1.6f));
        return r;
    }

    @Override
    public int calculateBias(ArrayList<Candle> data) {
        int period = (int) getParam("period");
        if (data == null || data.size() < period + 1) return 50;

        // Compare current close against the last evaluated element position
        int n = data.size();
        if (data.get(n - 1).close > data.get(n - 2).close) return 75;
        if (data.get(n - 1).close < data.get(n - 2).close) return 25;
        return 50;
    }
}