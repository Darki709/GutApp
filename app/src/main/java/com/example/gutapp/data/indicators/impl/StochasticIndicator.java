package com.example.gutapp.data.indicators.impl;

import android.graphics.Color;
import com.example.gutapp.data.indicators.Indicator;
import com.example.gutapp.data.models.Candle;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import java.util.ArrayList;
import java.util.List;

public class StochasticIndicator extends Indicator {
    public StochasticIndicator() {
        params.add(new Param("k_period", "%K Period", Param.Type.INTEGER, 1, 100, 14));
        params.add(new Param("d_period", "%D Period", Param.Type.INTEGER, 1, 50, 3));
        setColor(Color.parseColor("#03A9F4")); // Light Blue
    }

    @Override public String getId() { return "stoch"; }
    @Override public String getDisplayName() { return "Stochastic Oscillator"; }
    @Override public String getTag() { return "STOCH"; }
    @Override public boolean isSubChart() { return true; }
    @Override public Indicator newInstance() { return new StochasticIndicator(); }

    @Override
    public Result compute(ArrayList<Candle> candles) {
        int kPeriod = (int) getParam("k_period");
        int dPeriod = (int) getParam("d_period");
        if (candles.size() < kPeriod + dPeriod) return new Result();

        List<Entry> kEntries = new ArrayList<>();
        // Calculate %K
        for (int i = kPeriod - 1; i < candles.size(); i++) {
            double low = Double.MAX_VALUE;
            double high = Double.MIN_VALUE;
            for (int j = i - kPeriod + 1; j <= i; j++) {
                low = Math.min(low, candles.get(j).low);
                high = Math.max(high, candles.get(j).high);
            }
            double k = (high - low == 0) ? 50 : 100 * (candles.get(i).close - low) / (high - low);
            kEntries.add(new Entry(i, (float) k));
        }

        // Calculate %D (Moving Average of %K)
        List<Entry> dEntries = new ArrayList<>();
        for (int i = dPeriod - 1; i < kEntries.size(); i++) {
            float sum = 0;
            for (int j = i - dPeriod + 1; j <= i; j++) sum += kEntries.get(j).getY();
            dEntries.add(new Entry(kEntries.get(i).getX(), sum / dPeriod));
        }

        Result r = new Result();
        r.subChartMin = 0f; r.subChartMax = 100f;

        r.subChartLines.add(makeLineSet(kEntries, "%K", getColor(), 1.2f));
        r.subChartLines.add(makeLineSet(dEntries, "%D", Color.parseColor("#FF9800"), 1.2f)); // Orange for Signal

        for(com.github.mikephil.charting.data.LineDataSet s : r.subChartLines)
            s.setAxisDependency(YAxis.AxisDependency.RIGHT);

        return r;
    }

    @Override
    public int calculateBias(ArrayList<Candle> candles) {
        // 1. Initial Data Check: Ensure we have enough candles to even run the math
        if (candles == null || candles.size() < 2) {
            return 50;
        }

        Result res = compute(candles);

        // 2. Structure Check: Ensure the indicator returned the expected %K and %D lines
        if (res.subChartLines == null || res.subChartLines.size() < 2) {
            return 50;
        }

        // 3. Entry Count Check: Stochastic needs at least 2 points to determine direction/crossovers
        int entryCount = res.subChartLines.get(0).getEntryCount();
        if (entryCount < 2) {
            return 50;
        }

        try {
            // 4. Safe Indexing: Use 'length - 1' for current and 'length - 2' for previous
            int currentIdx = entryCount - 1;
            int previousIdx = entryCount - 2;

            float currentK = res.subChartLines.get(0).getEntryForIndex(currentIdx).getY();
            float currentD = res.subChartLines.get(1).getEntryForIndex(currentIdx).getY(); // Using ID is safer

            float prevK = res.subChartLines.get(0).getEntryForIndex(previousIdx).getY();
            float prevD = res.subChartLines.get(1).getEntryForIndex(previousIdx).getY();

            // --- BIAS LOGIC ---

            // A. Overbought/Oversold Reversals (High Priority)
            if (currentK < 20 && currentK > prevK) return 85; // Leaving oversold
            if (currentK > 80 && currentK < prevK) return 15; // Leaving overbought

            // B. Crossover Detection
            boolean wasBelow = prevK <= prevD;
            boolean isAbove = currentK > currentD;

            if (wasBelow && isAbove) return 80; // Bullish Cross
            if (!wasBelow && !isAbove) return 20; // Bearish Cross

            // C. General Momentum
            return (currentK > currentD) ? 65 : 35;

        } catch (Exception e) {
            // 5. Final Safety: If anything goes wrong (null entries, etc.), return Neutral
            return 50;
        }
    }
}