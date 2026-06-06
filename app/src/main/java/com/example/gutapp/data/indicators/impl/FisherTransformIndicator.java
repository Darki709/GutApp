package com.example.gutapp.data.indicators.impl;

import android.graphics.Color;
import com.example.gutapp.data.indicators.Indicator;
import com.example.gutapp.data.models.Candle;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineDataSet;
import java.util.ArrayList;
import java.util.List;

public class FisherTransformIndicator extends Indicator {

    public FisherTransformIndicator() {
        params.add(new Param("period", "Period", Param.Type.INTEGER, 2, 50, 9));
        setColor(Color.parseColor("#2196F3"));
    }

    @Override public String getId() { return "fisher"; }
    @Override public String getDisplayName() { return "Fisher Transform"; }
    @Override public String getTag() { return "Fisher"; }
    @Override public boolean isSubChart() { return true; }

    @Override
    public Indicator newInstance() { return new FisherTransformIndicator(); }

    @Override
    public Result compute(ArrayList<Candle> candles) {
        Result r = new Result();
        int period = (int) getParam("period");
        if (candles == null || candles.size() < period) return r;

        List<Entry> fisherEntries = new ArrayList<>();
        List<Entry> triggerEntries = new ArrayList<>();
        List<Entry> zeroLine = new ArrayList<>();

        double[] val1 = new double[candles.size()];
        double[] fisher = new double[candles.size()];

        for (int i = period - 1; i < candles.size(); i++) {
            double highestHigh = Double.MIN_VALUE;
            double lowestLow = Double.MAX_VALUE;

            for (int j = i - period + 1; j <= i; j++) {
                double hlPrice = (candles.get(j).high + candles.get(j).low) / 2.0;
                if (hlPrice > highestHigh) highestHigh = hlPrice;
                if (hlPrice < lowestLow) lowestLow = hlPrice;
            }

            double currentHl = (candles.get(i).high + candles.get(i).low) / 2.0;
            double range = highestHigh - lowestLow;
            if (range == 0) range = 0.001;

            double v = 0.66 * ((currentHl - lowestLow) / range - 0.5) + 0.67 * (i > 0 ? val1[i - 1] : 0);
            if (v > 0.99) v = 0.999;
            if (v < -0.99) v = -0.999;
            val1[i] = v;

            fisher[i] = 0.5 * Math.log((1 + v) / (1 - v)) + 0.5 * (i > 0 ? fisher[i - 1] : 0);

            fisherEntries.add(new Entry(i, (float) fisher[i]));
            triggerEntries.add(new Entry(i, (float) (i > 0 ? fisher[i - 1] : 0)));
            zeroLine.add(new Entry(i, 0f));
        }

        LineDataSet fSet = makeLineSet(fisherEntries, "Fisher", getColor(), 1.4f);
        LineDataSet tSet = makeLineSet(triggerEntries, "Trigger", Color.parseColor("#FF5252"), 1.2f);
        LineDataSet zSet = makeDashedLineSet(zeroLine, "0", Color.GRAY);

        fSet.setAxisDependency(YAxis.AxisDependency.RIGHT);
        tSet.setAxisDependency(YAxis.AxisDependency.RIGHT);
        zSet.setAxisDependency(YAxis.AxisDependency.RIGHT);

        r.subChartLines.add(fSet);
        r.subChartLines.add(tSet);
        r.subChartLines.add(zSet);
        return r;
    }

    @Override
    public int calculateBias(ArrayList<Candle> data) {
        int period = (int) getParam("period");
        if (data == null || data.size() < period) return 50;
        Result r = compute(data);
        if (r.subChartLines.size() < 2) return 50;

        float fisher = r.subChartLines.get(0).getEntryForIndex(r.subChartLines.get(0).getEntryCount() - 1).getY();
        float trigger = r.subChartLines.get(1).getEntryForIndex(r.subChartLines.get(1).getEntryCount() - 1).getY();

        if (fisher > trigger && fisher > 0) return 75;
        if (fisher < trigger && fisher < 0) return 25;
        return 50;
    }
}