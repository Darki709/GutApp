package com.example.gutapp.data.indicators.impl;

import android.graphics.Color;
import com.example.gutapp.data.indicators.Indicator;
import com.example.gutapp.data.models.Candle;
import com.github.mikephil.charting.data.Entry;
import java.util.ArrayList;
import java.util.List;

public class ChandeKrollStopIndicator extends Indicator {

    public ChandeKrollStopIndicator() {
        params.add(new Param("atrPeriod",  "ATR Period",  Param.Type.INTEGER, 3, 50, 10));
        params.add(new Param("atrScale",   "ATR Scale",   Param.Type.FLOAT,   1.0f, 6.0f, 3.0f));
        params.add(new Param("stopPeriod", "Stop Period", Param.Type.INTEGER, 5, 100, 20));
        setColor(Color.parseColor("#FF9100"));
    }

    @Override public String getId()          { return "chande_kroll_stop"; }
    @Override public String getDisplayName() { return "Chande Kroll Stop"; }
    @Override public String getTag()         { return "CKS"; }
    @Override public boolean isSubChart()     { return false; }
    @Override public Indicator newInstance()  { return new ChandeKrollStopIndicator(); }

    @Override
    public Result compute(ArrayList<Candle> candles) {
        Result r = new Result();
        int atrPeriod = (int) getParam("atrPeriod");
        float atrScale = getParam("atrScale");
        int stopPeriod = (int) getParam("stopPeriod");

        int required = Math.max(atrPeriod, stopPeriod) + atrPeriod;
        if (candles == null || candles.size() < required) return r;

        int len = candles.size();
        double[] atr = calculateATRInternal(candles, atrPeriod);

        double[] intermediateHighStop = new double[len];
        double[] intermediateLowStop = new double[len];

        for (int i = atrPeriod; i < len; i++) {
            intermediateHighStop[i] = candles.get(i).high - (atrScale * atr[i]);
            intermediateLowStop[i]  = candles.get(i).low + (atrScale * atr[i]);
        }

        List<Entry> longEntries = new ArrayList<>();
        List<Entry> shortEntries = new ArrayList<>();

        int startIdx = Math.max(atrPeriod, stopPeriod);

        for (int i = startIdx; i < len; i++) {
            double maxHighStop = intermediateHighStop[i - stopPeriod + 1];
            double minLowStop = intermediateLowStop[i - stopPeriod + 1];

            for (int j = i - stopPeriod + 1; j <= i; j++) {
                if (intermediateHighStop[j] > maxHighStop) maxHighStop = intermediateHighStop[j];
                if (intermediateLowStop[j] < minLowStop) minLowStop = intermediateLowStop[j];
            }

            longEntries.add(new Entry(i, (float) maxHighStop));
            shortEntries.add(new Entry(i, (float) minLowStop));
        }

        r.overlayLines.add(makeLineSet(longEntries, "CKS Long", Color.parseColor("#00E676"), 1.2f));
        r.overlayLines.add(makeLineSet(shortEntries, "CKS Short", Color.parseColor("#FF1744"), 1.2f));
        return r;
    }

    @Override
    public int calculateBias(ArrayList<Candle> data) {
        if (data == null || data.size() < 3) return 50;
        double close = data.get(data.size() - 1).close;
        double open = data.get(data.size() - 1).open;
        if (close > open) return 75;
        if (close < open) return 25;
        return 50;
    }

    private double[] calculateATRInternal(ArrayList<Candle> candles, int period) {
        double[] atr = new double[candles.size()];
        double[] tr = new double[candles.size()];
        tr[0] = candles.get(0).high - candles.get(0).low;

        for (int i = 1; i < candles.size(); i++) {
            double hl = candles.get(i).high - candles.get(i).low;
            double hpc = Math.abs(candles.get(i).high - candles.get(i - 1).close);
            double lpc = Math.abs(candles.get(i).low - candles.get(i - 1).close);
            tr[i] = Math.max(hl, Math.max(hpc, lpc));
        }

        double sum = 0;
        for (int i = 0; i < period; i++) sum += tr[i];
        atr[period] = sum / period;

        for (int i = period + 1; i < candles.size(); i++) {
            atr[i] = ((atr[i - 1] * (period - 1)) + tr[i]) / period;
        }
        return atr;
    }
}