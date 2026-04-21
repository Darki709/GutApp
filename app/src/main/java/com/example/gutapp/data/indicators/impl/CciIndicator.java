package com.example.gutapp.data.indicators.impl;

import android.graphics.Color;
import com.example.gutapp.data.indicators.Indicator;
import com.example.gutapp.data.models.Candle;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import java.util.ArrayList;
import java.util.List;

public class CciIndicator extends Indicator {
    public CciIndicator() {
        params.add(new Param("period", "Period", Param.Type.INTEGER, 2, 100, 20));
        setColor(Color.parseColor("#CDDC39")); // Lime
    }

    @Override public String getId() { return "cci"; }
    @Override public String getDisplayName() { return "Commodity Channel Index"; }
    @Override public String getTag() { return "CCI"; }
    @Override public boolean isSubChart() { return true; }
    @Override public Indicator newInstance() { return new CciIndicator(); }

    @Override
    public Result compute(ArrayList<Candle> candles) {
        int period = (int) getParam("period");
        if (candles.size() < period) return new Result();

        List<Entry> entries = new ArrayList<>();
        double constant = 0.015;

        for (int i = period - 1; i < candles.size(); i++) {
            double[] typicalPrices = new double[period];
            double tpSum = 0;

            for (int j = 0; j < period; j++) {
                Candle c = candles.get(i - j);
                typicalPrices[j] = (c.high + c.low + c.close) / 3.0;
                tpSum += typicalPrices[j];
            }

            double smaTp = tpSum / period;
            double meanDeviation = 0;
            for (double tp : typicalPrices) meanDeviation += Math.abs(smaTp - tp);
            meanDeviation /= period;

            double cci = (typicalPrices[0] - smaTp) / (constant * meanDeviation);
            entries.add(new Entry(i, (float) cci));
        }

        Result r = new Result();
        r.subChartLines.add(makeLineSet(entries, "CCI", getColor(), 1.4f));
        r.subChartLines.get(0).setAxisDependency(YAxis.AxisDependency.RIGHT);

        return r;
    }

    @Override
    public int calculateBias(ArrayList<Candle> candles) {
        Result res = compute(candles);
        if (res.subChartLines.isEmpty() || res.subChartLines.get(0).getEntryCount() == 0) {
            return 50; // Neutral fallback
        }

        // Get the latest CCI value
        int lastIdx = res.subChartLines.get(0).getEntryCount() - 1;
        float currentCci = res.subChartLines.get(0).getEntryForIndex(lastIdx).getY();

        // Logic for CCI Bias:
        // CCI > +100: Overbought (Potential Bearish Reversal)
        // CCI < -100: Oversold (Potential Bullish Reversal)
        // CCI 0 to 100: Bullish Momentum
        // CCI -100 to 0: Bearish Momentum

        if (currentCci >= 100) {
            return 25; // Bearish (Overbought territory)
        } else if (currentCci <= -100) {
            return 75; // Bullish (Oversold territory)
        } else if (currentCci > 0) {
            return 60; // Mild Bullish (Positive momentum)
        } else if (currentCci < 0) {
            return 40; // Mild Bearish (Negative momentum)
        }

        return 50; // Neutral at zero
    }
}