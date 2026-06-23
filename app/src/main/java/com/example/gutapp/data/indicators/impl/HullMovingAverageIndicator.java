package com.example.gutapp.data.indicators.impl;

import android.graphics.Color;
import com.example.gutapp.data.indicators.Indicator;
import com.example.gutapp.data.models.Candle;
import com.github.mikephil.charting.data.Entry;
import java.util.ArrayList;
import java.util.List;

public class HullMovingAverageIndicator extends Indicator {

    public HullMovingAverageIndicator() {
        params.add(new Param("period", "HMA Period", Param.Type.INTEGER, 4, 300, 20));
        setColor(Color.parseColor("#00B0FF")); // Bright Electric Blue
    }

    @Override public String getId()          { return "hma"; }
    @Override public String getDisplayName() { return "Hull Moving Average"; }
    @Override public String getTag()         { return "HMA"; }
    @Override public boolean isSubChart()     { return false; }
    @Override public Indicator newInstance()  { return new HullMovingAverageIndicator(); }

    @Override
    public Result compute(ArrayList<Candle> candles) {
        Result r = new Result();
        int period = (int) getParam("period");
        if (candles == null || candles.size() < period) return r;

        int size = candles.size();
        double[] rawCloses = new double[size];
        for (int i = 0; i < size; i++) rawCloses[i] = candles.get(i).close;

        // 1. Calculate WMA(Period) and WMA(Period / 2)
        double[] wmaFull = computeWma(rawCloses, period);
        double[] wmaHalf = computeWma(rawCloses, period / 2);

        // 2. Generate raw combined series: 2 * WMA(n/2) - WMA(n)
        double[] combinedSeries = new double[size];
        for (int i = 0; i < size; i++) {
            combinedSeries[i] = (2.0 * wmaHalf[i]) - wmaFull[i];
        }

        // 3. Final HMA layer is WMA of the combined series using SQRT(Period)
        int sqrtPeriod = (int) Math.sqrt(period);
        double[] finalHma = computeWma(combinedSeries, sqrtPeriod);

        List<Entry> hmaEntries = new ArrayList<>();
        int validStartingIdx = period + sqrtPeriod;
        for (int i = validStartingIdx; i < size; i++) {
            hmaEntries.add(new Entry(i, (float) finalHma[i]));
        }

        r.overlayLines.add(makeLineSet(hmaEntries, getTag(), getColor(), 1.6f));
        return r;
    }

    @Override
    public int calculateBias(ArrayList<Candle> data) {
        int period = (int) getParam("period");
        int sqrtPeriod = (int) Math.sqrt(period);
        if (data == null || data.size() < period + sqrtPeriod + 2) return 50;

        int size = data.size();
        double[] rawCloses = new double[size];
        for (int i = 0; i < size; i++) rawCloses[i] = data.get(i).close;

        double[] combined = new double[size];
        double[] wmaF = computeWma(rawCloses, period);
        double[] wmaH = computeWma(rawCloses, period / 2);
        for (int i = 0; i < size; i++) combined[i] = (2.0 * wmaH[i]) - wmaF[i];

        double[] hmaValues = computeWma(combined, sqrtPeriod);

        int lastIdx = size - 1;
        double currentHma = hmaValues[lastIdx];
        double previousHma = hmaValues[lastIdx - 1];

        // Binary trend trajectory tracking
        return (currentHma > previousHma) ? 100 : 0;
    }

    private double[] computeWma(double[] src, int period) {
        double[] out = new double[src.length];
        if (period < 1) return out;

        int weightDenom = (period * (period + 1)) / 2;

        for (int i = period - 1; i < src.length; i++) {
            double sum = 0;
            int weight = 1;
            for (int j = i - period + 1; j <= i; j++) {
                sum += (src[j] * weight);
                weight++;
            }
            out[i] = sum / weightDenom;
        }
        return out;
    }
}