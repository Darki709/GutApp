package com.example.gutapp.data.indicators.impl;

import android.graphics.Color;
import com.example.gutapp.data.indicators.Indicator;
import com.example.gutapp.data.models.Candle;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineDataSet;
import java.util.ArrayList;
import java.util.List;

public class HistoricalVolatilityIndicator extends Indicator {

    public HistoricalVolatilityIndicator() {
        params.add(new Param("period", "Period", Param.Type.INTEGER, 2, 100, 20));
        params.add(new Param("annual", "Annualization Factor", Param.Type.INTEGER, 1, 365, 252));
        setColor(Color.parseColor("#FF5722"));
    }

    @Override public String getId() { return "hist_volatility"; }
    @Override public String getDisplayName() { return "Historical Volatility"; }
    @Override public String getTag() { return "HV"; }
    @Override public boolean isSubChart() { return true; }

    @Override
    public Indicator newInstance() { return new HistoricalVolatilityIndicator(); }

    @Override
    public Result compute(ArrayList<Candle> candles) {
        Result r = new Result();
        int period = (int) getParam("period");
        int annualFactor = (int) getParam("annual");
        if (candles == null || candles.size() < period + 1) return r;

        List<Entry> entries = new ArrayList<>();
        double[] returns = new double[candles.size()];

        for (int i = 1; i < candles.size(); i++) {
            returns[i] = Math.log(candles.get(i).close / candles.get(i - 1).close);
        }

        for (int i = period; i < candles.size(); i++) {
            double sumReturns = 0;
            for (int j = i - period + 1; j <= i; j++) sumReturns += returns[j];
            double meanReturn = sumReturns / period;

            double sqDiffSum = 0;
            for (int j = i - period + 1; j <= i; j++) {
                sqDiffSum += Math.pow(returns[j] - meanReturn, 2);
            }

            double variance = sqDiffSum / (period - 1);
            double hv = Math.sqrt(variance) * Math.sqrt(annualFactor) * 100.0;

            entries.add(new Entry(i, (float) hv));
        }

        LineDataSet set = makeLineSet(entries, "HV", getColor(), 1.4f);
        set.setAxisDependency(YAxis.AxisDependency.RIGHT);
        r.subChartLines.add(set);
        return r;
    }

    @Override
    public int calculateBias(ArrayList<Candle> data) {
        return 50; // Volatility doesn't dictate direction
    }
}