package com.example.gutapp.data.indicators.impl;

import android.graphics.Color;
import com.example.gutapp.data.indicators.Indicator;
import com.example.gutapp.data.models.Candle;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineDataSet;
import java.util.ArrayList;
import java.util.List;

public class MoneyFlowIndexIndicator extends Indicator {

    public MoneyFlowIndexIndicator() {
        params.add(new Param("period", "Period", Param.Type.INTEGER, 2, 100, 14));
        setColor(Color.parseColor("#4CAF50"));
    }

    @Override public String getId() { return "mfi"; }
    @Override public String getDisplayName() { return "Money Flow Index"; }
    @Override public String getTag() { return "MFI"; }
    @Override public boolean isSubChart() { return true; }

    @Override
    public Indicator newInstance() { return new MoneyFlowIndexIndicator(); }

    @Override
    public Result compute(ArrayList<Candle> candles) {
        Result r = new Result();
        int period = (int) getParam("period");
        if (candles == null || candles.size() < period + 1) return r;

        r.subChartMin = 0f;
        r.subChartMax = 100f;

        List<Entry> mfiEntries = new ArrayList<>();
        List<Entry> upper = new ArrayList<>();
        List<Entry> lower = new ArrayList<>();

        for (int i = period; i < candles.size(); i++) {
            double posFlow = 0, negFlow = 0;
            for (int j = i - period + 1; j <= i; j++) {
                double typPrice = (candles.get(j).high + candles.get(j).low + candles.get(j).close) / 3.0;
                double prevTypPrice = (candles.get(j-1).high + candles.get(j-1).low + candles.get(j-1).close) / 3.0;
                double moneyFlow = typPrice * candles.get(j).volume;

                if (typPrice > prevTypPrice) posFlow += moneyFlow;
                else if (typPrice < prevTypPrice) negFlow += moneyFlow;
            }
            double mfi = (negFlow == 0) ? 100.0 : 100.0 - (100.0 / (1.0 + (posFlow / negFlow)));

            mfiEntries.add(new Entry(i, (float) mfi));
            upper.add(new Entry(i, 80f));
            lower.add(new Entry(i, 20f));
        }

        LineDataSet mfiSet = makeLineSet(mfiEntries, "MFI", getColor(), 1.4f);
        LineDataSet uSet = makeDashedLineSet(upper, "80", Color.argb(120, 255, 0, 0));
        LineDataSet lSet = makeDashedLineSet(lower, "20", Color.argb(120, 0, 255, 0));

        mfiSet.setAxisDependency(YAxis.AxisDependency.RIGHT);
        uSet.setAxisDependency(YAxis.AxisDependency.RIGHT);
        lSet.setAxisDependency(YAxis.AxisDependency.RIGHT);

        r.subChartLines.add(mfiSet);
        r.subChartLines.add(uSet);
        r.subChartLines.add(lSet);
        return r;
    }

    @Override
    public int calculateBias(ArrayList<Candle> data) {
        int period = (int) getParam("period");
        if (data == null || data.size() < period + 1) return 50;

        Result res = compute(data);
        if (res.subChartLines.isEmpty()) return 50;

        LineDataSet set = res.subChartLines.get(0);
        float lastMfi = set.getEntryForIndex(set.getEntryCount() - 1).getY();

        if (lastMfi > 80) return 25; // Overbought
        if (lastMfi < 20) return 75; // Oversold
        return 50;
    }
}