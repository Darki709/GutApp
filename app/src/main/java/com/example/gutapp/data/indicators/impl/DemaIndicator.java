package com.example.gutapp.data.indicators.impl;

import android.graphics.Color;
import com.example.gutapp.data.indicators.Indicator;
import com.example.gutapp.data.models.Candle;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineDataSet;
import java.util.ArrayList;
import java.util.List;

public class DemaIndicator extends Indicator {

    public DemaIndicator() {
        params.add(new Param("period", "Period", Param.Type.INTEGER, 2, 200, 14));
        setColor(Color.parseColor("#9C27B0"));
    }

    @Override public String getId() { return "dema"; }
    @Override public String getDisplayName() { return "Double EMA (DEMA)"; }
    @Override public String getTag() { return "DEMA"; }
    @Override public boolean isSubChart() { return false; }

    @Override
    public Indicator newInstance() { return new DemaIndicator(); }

    @Override
    public Result compute(ArrayList<Candle> candles) {
        Result r = new Result();
        int period = (int) getParam("period");
        if (candles == null || candles.size() < period * 2) return r;

        double[] prices = new double[candles.size()];
        for(int i=0; i<candles.size(); i++) prices[i] = candles.get(i).close;

        double[] ema1 = calculateEma(prices, period);
        double[] ema2 = calculateEma(ema1, period);

        List<Entry> entries = new ArrayList<>();
        for (int i = period * 2 - 1; i < candles.size(); i++) {
            double dema = 2 * ema1[i] - ema2[i];
            entries.add(new Entry(i, (float) dema));
        }

        r.overlayLines.add(makeLineSet(entries, "DEMA", getColor(), 1.5f));
        return r;
    }

    private double[] calculateEma(double[] data, int period) {
        double[] ema = new double[data.length];
        if (data.length < period) return ema;
        double multiplier = 2.0 / (period + 1);
        double sum = 0;
        for (int i = 0; i < period; i++) sum += data[i];
        ema[period - 1] = sum / period;
        for (int i = period; i < data.length; i++) {
            ema[i] = (data[i] - ema[i - 1]) * multiplier + ema[i - 1];
        }
        return ema;
    }

    @Override
    public int calculateBias(ArrayList<Candle> data) {
        int period = (int) getParam("period");
        if (data == null || data.size() < period * 2) return 50;

        Result res = compute(data);
        if(res.overlayLines.isEmpty()) return 50;

        LineDataSet set = res.overlayLines.get(0);
        float dema = set.getEntryForIndex(set.getEntryCount()-1).getY();
        float close = (float) data.get(data.size()-1).close;

        if (close > dema) return 75;
        if (close < dema) return 25;
        return 50;
    }
}