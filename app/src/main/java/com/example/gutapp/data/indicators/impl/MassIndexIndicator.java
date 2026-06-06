package com.example.gutapp.data.indicators.impl;

import android.graphics.Color;
import com.example.gutapp.data.indicators.Indicator;
import com.example.gutapp.data.models.Candle;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineDataSet;
import java.util.ArrayList;
import java.util.List;

public class MassIndexIndicator extends Indicator {

    public MassIndexIndicator() {
        params.add(new Param("emaPeriod", "EMA Period", Param.Type.INTEGER, 2, 50, 9));
        params.add(new Param("sumPeriod", "Sum Period", Param.Type.INTEGER, 2, 100, 25));
        setColor(Color.parseColor("#795548"));
    }

    @Override public String getId() { return "mass_index"; }
    @Override public String getDisplayName() { return "Mass Index"; }
    @Override public String getTag() { return "MI"; }
    @Override public boolean isSubChart() { return true; }

    @Override
    public Indicator newInstance() { return new MassIndexIndicator(); }

    @Override
    public Result compute(ArrayList<Candle> candles) {
        Result r = new Result();
        int emaP = (int) getParam("emaPeriod");
        int sumP = (int) getParam("sumPeriod");

        if (candles == null || candles.size() < emaP * 2 + sumP) return r;

        double[] ranges = new double[candles.size()];
        for (int i = 0; i < candles.size(); i++) ranges[i] = candles.get(i).high - candles.get(i).low;

        double[] ema1 = calculateEma(ranges, emaP);
        double[] ema2 = calculateEma(ema1, emaP);

        double[] ratio = new double[candles.size()];
        for(int i = emaP * 2; i < candles.size(); i++) {
            ratio[i] = ema2[i] == 0 ? 0 : ema1[i] / ema2[i];
        }

        List<Entry> entries = new ArrayList<>();
        List<Entry> bulgeLine = new ArrayList<>();

        for (int i = (emaP * 2) + sumP - 1; i < candles.size(); i++) {
            double massIndex = 0;
            for(int j = i - sumP + 1; j <= i; j++) {
                massIndex += ratio[j];
            }
            entries.add(new Entry(i, (float) massIndex));
            bulgeLine.add(new Entry(i, 27f)); // Commonly used "bulge" threshold for 25-period
        }

        LineDataSet set = makeLineSet(entries, "MI", getColor(), 1.4f);
        LineDataSet bulgeSet = makeDashedLineSet(bulgeLine, "Bulge 27", Color.GRAY);

        set.setAxisDependency(YAxis.AxisDependency.RIGHT);
        bulgeSet.setAxisDependency(YAxis.AxisDependency.RIGHT);

        r.subChartLines.add(set);
        r.subChartLines.add(bulgeSet);
        return r;
    }

    private double[] calculateEma(double[] data, int period) {
        double[] ema = new double[data.length];
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
        return 50; // Reversal identifier, hard to define continuous bias direction
    }
}