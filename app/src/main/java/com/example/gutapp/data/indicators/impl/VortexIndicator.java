package com.example.gutapp.data.indicators.impl;

import android.graphics.Color;
import com.example.gutapp.data.indicators.Indicator;
import com.example.gutapp.data.models.Candle;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineDataSet;
import java.util.ArrayList;
import java.util.List;

public class VortexIndicator extends Indicator {

    public VortexIndicator() {
        params.add(new Param("period", "Period", Param.Type.INTEGER, 2, 100, 14));
        setColor(Color.parseColor("#4CAF50")); // VI+ Color
    }

    @Override public String getId() { return "vortex"; }
    @Override public String getDisplayName() { return "Vortex Indicator"; }
    @Override public String getTag() { return "VI"; }
    @Override public boolean isSubChart() { return true; }

    @Override
    public Indicator newInstance() { return new VortexIndicator(); }

    @Override
    public Result compute(ArrayList<Candle> candles) {
        Result r = new Result();
        int period = (int) getParam("period");
        if (candles == null || candles.size() < period + 1) return r;

        List<Entry> viPlusEntries = new ArrayList<>();
        List<Entry> viMinusEntries = new ArrayList<>();

        for (int i = period; i < candles.size(); i++) {
            double sumTr = 0;
            double sumVmPlus = 0;
            double sumVmMinus = 0;

            for (int j = i - period + 1; j <= i; j++) {
                double tr = Math.max(candles.get(j).high - candles.get(j).low,
                        Math.max(Math.abs(candles.get(j).high - candles.get(j - 1).close),
                                Math.abs(candles.get(j).low - candles.get(j - 1).close)));
                sumTr += tr;
                sumVmPlus += Math.abs(candles.get(j).high - candles.get(j - 1).low);
                sumVmMinus += Math.abs(candles.get(j).low - candles.get(j - 1).high);
            }

            double viPlus = sumTr == 0 ? 0 : sumVmPlus / sumTr;
            double viMinus = sumTr == 0 ? 0 : sumVmMinus / sumTr;

            viPlusEntries.add(new Entry(i, (float) viPlus));
            viMinusEntries.add(new Entry(i, (float) viMinus));
        }

        LineDataSet plusSet = makeLineSet(viPlusEntries, "VI+", getColor(), 1.4f);
        LineDataSet minusSet = makeLineSet(viMinusEntries, "VI-", Color.parseColor("#F44336"), 1.4f);

        plusSet.setAxisDependency(YAxis.AxisDependency.RIGHT);
        minusSet.setAxisDependency(YAxis.AxisDependency.RIGHT);

        r.subChartLines.add(plusSet);
        r.subChartLines.add(minusSet);
        return r;
    }

    @Override
    public int calculateBias(ArrayList<Candle> data) {
        int period = (int) getParam("period");
        if (data == null || data.size() < period + 1) return 50;
        Result r = compute(data);
        if (r.subChartLines.size() < 2) return 50;

        LineDataSet plusSet = r.subChartLines.get(0);
        LineDataSet minusSet = r.subChartLines.get(1);

        float lastPlus = plusSet.getEntryForIndex(plusSet.getEntryCount() - 1).getY();
        float lastMinus = minusSet.getEntryForIndex(minusSet.getEntryCount() - 1).getY();

        if (lastPlus > lastMinus) return 70;
        if (lastPlus < lastMinus) return 30;
        return 50;
    }
}