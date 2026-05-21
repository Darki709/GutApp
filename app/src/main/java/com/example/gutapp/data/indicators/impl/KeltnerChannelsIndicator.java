package com.example.gutapp.data.indicators.impl;

import android.graphics.Color;
import com.example.gutapp.data.indicators.Indicator;
import com.example.gutapp.data.models.Candle;
import com.github.mikephil.charting.data.Entry;
import java.util.ArrayList;
import java.util.List;

public class KeltnerChannelsIndicator extends Indicator {

    public KeltnerChannelsIndicator() {
        params.add(new Param("emaPeriod", "EMA Period", Param.Type.INTEGER, 2, 100, 20));
        params.add(new Param("atrPeriod", "ATR Period", Param.Type.INTEGER, 2, 100, 10));
        params.add(new Param("multiplier", "Multiplier", Param.Type.FLOAT, 0.5f, 5.0f, 2.0f));
        setColor(Color.parseColor("#00E676")); // Vibrant Green
    }

    @Override public String getId()          { return "keltner_channels"; }
    @Override public String getDisplayName() { return "Keltner Channels"; }
    @Override public String getTag()         { return "KC"; }
    @Override public boolean isSubChart()     { return false; }
    @Override public Indicator newInstance()  { return new KeltnerChannelsIndicator(); }

    @Override
    public Result compute(ArrayList<Candle> candles) {
        Result r = new Result();
        int emaPeriod = (int) getParam("emaPeriod");
        int atrPeriod = (int) getParam("atrPeriod");
        float multiplier = (float) getParam("multiplier");

        int maxRequired = Math.max(emaPeriod, atrPeriod) + 1;
        if (candles == null || candles.size() < maxRequired) return r;

        List<Entry> upperEntries = new ArrayList<>();
        List<Entry> middleEntries = new ArrayList<>();
        List<Entry> lowerEntries = new ArrayList<>();

        // 1. Calculate True Range array
        double[] tr = new double[candles.size()];
        tr[0] = candles.get(0).high - candles.get(0).low;
        for (int i = 1; i < candles.size(); i++) {
            double hl = candles.get(i).high - candles.get(i).low;
            double hpc = Math.abs(candles.get(i).high - candles.get(i - 1).close);
            double lpc = Math.abs(candles.get(i).low - candles.get(i - 1).close);
            tr[i] = Math.max(hl, Math.max(hpc, lpc));
        }

        // 2. Initialize ATR (Wilder's Smoothing)
        double atr = 0;
        for (int i = 0; i < atrPeriod; i++) atr += tr[i];
        atr /= atrPeriod;

        // 3. Initialize EMA
        double k = 2.0 / (emaPeriod + 1);
        double emaSum = 0;
        for (int i = 0; i < emaPeriod; i++) {
            emaSum += candles.get(i).close;
        }
        double ema = emaSum / emaPeriod;

        // Loop through metrics
        for (int i = 0; i < candles.size(); i++) {
            // Smooth ATR step-by-step
            if (i >= atrPeriod) {
                atr = (atr * (atrPeriod - 1) + tr[i]) / atrPeriod;
            }
            // Smooth EMA step-by-step
            if (i >= emaPeriod) {
                ema = (candles.get(i).close * k) + (ema * (1.0 - k));
            }

            // Only plot when both components have matured
            if (i >= maxRequired - 1) {
                float middle = (float) ema;
                float upper = (float) (ema + (multiplier * atr));
                float lower = (float) (ema - (multiplier * atr));

                middleEntries.add(new Entry(i, middle));
                upperEntries.add(new Entry(i, upper));
                lowerEntries.add(new Entry(i, lower));
            }
        }

        int c = getColor();
        r.overlayLines.add(makeDashedLineSet(upperEntries, "KC Upper", c));
        r.overlayLines.add(makeLineSet(middleEntries, "KC Basis", c, 1.2f));
        r.overlayLines.add(makeDashedLineSet(lowerEntries, "KC Lower", c));
        return r;
    }

    @Override
    public int calculateBias(ArrayList<Candle> data) {
        int emaPeriod = (int) getParam("emaPeriod");
        int atrPeriod = (int) getParam("atrPeriod");
        float multiplier = (float) getParam("multiplier");
        int maxRequired = Math.max(emaPeriod, atrPeriod) + 1;

        if (data == null || data.size() < maxRequired) return 50; // Neutral baseline

        int lastIdx = data.size() - 1;

        // 1. Trace True Range & ATR up to the last candle
        double[] tr = new double[data.size()];
        tr[0] = data.get(0).high - data.get(0).low;
        for (int i = 1; i <= lastIdx; i++) {
            double hl = data.get(i).high - data.get(i).low;
            double hpc = Math.abs(data.get(i).high - data.get(i - 1).close);
            double lpc = Math.abs(data.get(i).low - data.get(i - 1).close);
            tr[i] = Math.max(hl, Math.max(hpc, lpc));
        }

        double atr = 0;
        for (int i = 0; i < atrPeriod; i++) atr += tr[i];
        atr /= atrPeriod;

        // 2. Trace Baseline EMA
        double k = 2.0 / (emaPeriod + 1);
        double emaSum = 0;
        for (int i = 0; i < emaPeriod; i++) emaSum += data.get(i).close;
        double ema = emaSum / emaPeriod;

        for (int i = atrPeriod; i <= lastIdx; i++) {
            if (i >= atrPeriod) atr = (atr * (atrPeriod - 1) + tr[i]) / atrPeriod;
            if (i >= emaPeriod) ema = (data.get(i).close * k) + (ema * (1.0 - k));
        }

        double latestClose = data.get(lastIdx).close;
        double upperBand = ema + (multiplier * atr);
        double lowerBand = ema - (multiplier * atr);
        double totalChannelWidth = upperBand - lowerBand;

        if (totalChannelWidth == 0) return 50;

        // Percentage location inside (or outside) the bands
        double pct = (latestClose - lowerBand) / totalChannelWidth; // 0.0 at lower, 1.0 at upper
        int score = (int) Math.round(pct * 100.0);

        // Clamp score tightly between 0 and 100
        return Math.max(0, Math.min(100, score));
    }
}