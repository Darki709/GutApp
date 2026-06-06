package com.example.gutapp.data.indicators.impl;

import android.graphics.Color;
import com.example.gutapp.data.indicators.Indicator;
import com.example.gutapp.data.models.Candle;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineDataSet;
import java.util.ArrayList;
import java.util.List;

public class CmfIndicator extends Indicator {

    public CmfIndicator() {
        params.add(new Param("period", "Period", Param.Type.INTEGER, 5, 100, 20));
        setColor(Color.parseColor("#00E676"));
    }

    @Override public String getId()          { return "cmf"; }
    @Override public String getDisplayName() { return "Chaikin Money Flow"; }
    @Override public String getTag()         { return "CMF"; }
    @Override public boolean isSubChart()     { return true; }
    @Override public Indicator newInstance()  { return new CmfIndicator(); }

    @Override
    public Result compute(ArrayList<Candle> candles) {
        Result r = new Result();
        int period = (int) getParam("period");
        if (candles == null || candles.size() < period) return r;

        List<Entry> entries = new ArrayList<>();
        List<Entry> zeroEntries = new ArrayList<>();

        for (int i = period - 1; i < candles.size(); i++) {
            double moneyFlowVolumeSum = 0.0;
            double totalVolumeSum = 0.0;

            for (int j = i - period + 1; j <= i; j++) {
                Candle c = candles.get(j);
                double highMinusLow = c.high - c.low;
                double mfm = 0.0;
                if (highMinusLow != 0.0) {
                    mfm = ((c.close - c.low) - (c.high - c.close)) / highMinusLow;
                }
                moneyFlowVolumeSum += mfm * c.volume;
                totalVolumeSum += c.volume;
            }

            float cmfValue = totalVolumeSum != 0.0 ? (float) (moneyFlowVolumeSum / totalVolumeSum) : 0f;
            entries.add(new Entry(i, cmfValue));
            zeroEntries.add(new Entry(i, 0f));
        }

        r.subChartMin = -1.0f;
        r.subChartMax = 1.0f;

        LineDataSet set = makeLineSet(entries, getTag(), getColor(), 1.4f);
        LineDataSet zeroSet = makeDashedLineSet(zeroEntries, "Baseline", Color.argb(100, 255, 255, 255));

        set.setAxisDependency(YAxis.AxisDependency.RIGHT);
        zeroSet.setAxisDependency(YAxis.AxisDependency.RIGHT);

        r.subChartLines.add(set);
        r.subChartLines.add(zeroSet);
        return r;
    }

    @Override
    public int calculateBias(ArrayList<Candle> data) {
        int period = (int) getParam("period");
        if (data == null || data.size() < period) return 50;

        int n = data.size();
        double moneyFlowVolumeSum = 0.0;
        double totalVolumeSum = 0.0;

        for (int j = n - period; j < n; j++) {
            Candle c = data.get(j);
            double highMinusLow = c.high - c.low;
            double mfm = highMinusLow != 0.0 ? ((c.close - c.low) - (c.high - c.close)) / highMinusLow : 0.0;
            moneyFlowVolumeSum += mfm * c.volume;
            totalVolumeSum += c.volume;
        }

        double cmf = totalVolumeSum != 0.0 ? moneyFlowVolumeSum / totalVolumeSum : 0.0;
        if (cmf > 0.05) return 75;
        if (cmf < -0.05) return 25;
        return 50;
    }
}