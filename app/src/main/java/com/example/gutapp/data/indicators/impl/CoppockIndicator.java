package com.example.gutapp.data.indicators.impl;

import android.graphics.Color;
import com.example.gutapp.data.indicators.Indicator;
import com.example.gutapp.data.models.Candle;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineDataSet;
import java.util.ArrayList;
import java.util.List;

public class CoppockIndicator extends Indicator {

    public CoppockIndicator() {
        params.add(new Param("wmaPeriod",  "WMA Smooth",   Param.Type.INTEGER, 2, 50, 10));
        params.add(new Param("longRoc",    "Long ROC",     Param.Type.INTEGER, 5, 100, 14));
        params.add(new Param("shortRoc",   "Short ROC",    Param.Type.INTEGER, 3, 50, 11));
        setColor(Color.parseColor("#EC407A"));
    }

    @Override public String getId()          { return "coppock_curve"; }
    @Override public String getDisplayName() { return "Coppock Curve"; }
    @Override public String getTag()         { return "COPC"; }
    @Override public boolean isSubChart()     { return true; }
    @Override public Indicator newInstance()  { return new CoppockIndicator(); }

    @Override
    public Result compute(ArrayList<Candle> candles) {
        Result r = new Result();
        int wmaPeriod = (int) getParam("wmaPeriod");
        int longRoc = (int) getParam("longRoc");
        int shortRoc = (int) getParam("shortRoc");

        int required = Math.max(longRoc, shortRoc);
        if (candles == null || candles.size() < (required + wmaPeriod)) return r;

        int len = candles.size();
        double[] rawCoppock = new double[len];

        for (int i = required; i < len; i++) {
            double close = candles.get(i).close;
            double longPastClose = candles.get(i - longRoc).close;
            double shortPastClose = candles.get(i - shortRoc).close;

            double roc1 = longPastClose != 0.0 ? ((close - longPastClose) / longPastClose) * 100.0 : 0.0;
            double roc2 = shortPastClose != 0.0 ? ((close - shortPastClose) / shortPastClose) * 100.0 : 0.0;

            rawCoppock[i] = roc1 + roc2;
        }

        List<Entry> curveEntries = new ArrayList<>();
        List<Entry> zeroEntries = new ArrayList<>();

        for (int i = required + wmaPeriod - 1; i < len; i++) {
            double weightedSum = 0.0;
            double weightTotal = 0.0;
            int currentWeight = 1;

            for (int j = i - wmaPeriod + 1; j <= i; j++) {
                weightedSum += rawCoppock[j] * currentWeight;
                weightTotal += currentWeight;
                currentWeight++;
            }

            curveEntries.add(new Entry(i, (float) (weightedSum / weightTotal)));
            zeroEntries.add(new Entry(i, 0f));
        }

        LineDataSet set = makeLineSet(curveEntries, getTag(), getColor(), 1.5f);
        LineDataSet base = makeDashedLineSet(zeroEntries, "Zero", Color.argb(80, 255, 255, 255));

        set.setAxisDependency(YAxis.AxisDependency.RIGHT);
        base.setAxisDependency(YAxis.AxisDependency.RIGHT);

        r.subChartLines.add(set);
        r.subChartLines.add(base);
        return r;
    }

    @Override
    public int calculateBias(ArrayList<Candle> data) {
        if (data == null || data.size() < 2) return 50;
        return data.get(data.size() - 1).close > data.get(data.size() - 2).close ? 75 : 25;
    }
}