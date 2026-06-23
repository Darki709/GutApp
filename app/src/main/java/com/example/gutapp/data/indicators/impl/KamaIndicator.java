package com.example.gutapp.data.indicators.impl;

import android.graphics.Color;
import com.example.gutapp.data.indicators.Indicator;
import com.example.gutapp.data.models.Candle;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineDataSet;
import java.util.ArrayList;
import java.util.List;

public class KamaIndicator extends Indicator {

    public KamaIndicator() {
        params.add(new Param("period", "Efficiency Ratio Period", Param.Type.INTEGER, 2, 100, 10));
        params.add(new Param("fast", "Fast EMA", Param.Type.INTEGER, 2, 50, 2));
        params.add(new Param("slow", "Slow EMA", Param.Type.INTEGER, 2, 100, 30));
        setColor(Color.parseColor("#FFC107"));
    }

    @Override public String getId() { return "kama"; }
    @Override public String getDisplayName() { return "Kaufman's Adaptive MA"; }
    @Override public String getTag() { return "KAMA"; }
    @Override public boolean isSubChart() { return false; }

    @Override
    public Indicator newInstance() { return new KamaIndicator(); }

    @Override
    public Result compute(ArrayList<Candle> candles) {
        Result r = new Result();
        int period = (int) getParam("period");
        int fast = (int) getParam("fast");
        int slow = (int) getParam("slow");

        if (candles == null || candles.size() < period + 1) return r;

        double fastSC = 2.0 / (fast + 1);
        double slowSC = 2.0 / (slow + 1);

        List<Entry> entries = new ArrayList<>();
        double kama = candles.get(period - 1).close;
        entries.add(new Entry(period - 1, (float) kama));

        for (int i = period; i < candles.size(); i++) {
            double change = Math.abs(candles.get(i).close - candles.get(i - period).close);
            double volatility = 0;
            for (int j = i - period + 1; j <= i; j++) {
                volatility += Math.abs(candles.get(j).close - candles.get(j - 1).close);
            }

            double er = volatility == 0 ? 0 : change / volatility;
            double sc = Math.pow(er * (fastSC - slowSC) + slowSC, 2);

            kama = kama + sc * (candles.get(i).close - kama);
            entries.add(new Entry(i, (float) kama));
        }

        r.overlayLines.add(makeLineSet(entries, "KAMA", getColor(), 1.5f));
        return r;
    }

    @Override
    public int calculateBias(ArrayList<Candle> data) {
        int period = (int) getParam("period");
        if (data == null || data.size() < period + 1) return 50;
        Result r = compute(data);
        if (r.overlayLines.isEmpty()) return 50;

        float currentKama = r.overlayLines.get(0).getEntryForIndex(r.overlayLines.get(0).getEntryCount() - 1).getY();
        double currentClose = data.get(data.size() - 1).close;

        return currentClose > currentKama ? 70 : (currentClose < currentKama ? 30 : 50);
    }
}