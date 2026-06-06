package com.example.gutapp.data.indicators.impl;

import android.graphics.Color;

import com.example.gutapp.data.indicators.Indicator;
import com.example.gutapp.data.models.Candle;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineDataSet;

import java.util.ArrayList;
import java.util.List;

public class TrixIndicator extends Indicator {

    public TrixIndicator() {
        params.add(new Param("period", "Period", Param.Type.INTEGER, 2, 100, 15));
        setColor(Color.parseColor("#FF5722"));
    }

    @Override public String  getId()          { return "trix"; }
    @Override public String  getDisplayName() { return "TRIX"; }
    @Override public String  getTag()         { return "TRIX"; }
    @Override public boolean isSubChart()     { return true; }

    @Override
    public Indicator newInstance() { return new TrixIndicator(); }

    @Override
    public Result compute(ArrayList<Candle> candles) {
        Result r = new Result();
        int period = (int) getParam("period");

        if (candles == null || candles.size() < period * 3) return r;

        double multiplier = 2.0 / (period + 1.0);

        // Helper to calculate EMA over an array
        double[] ema1 = calculateEma(extractCloses(candles), period, multiplier);
        double[] ema2 = calculateEma(ema1, period, multiplier);
        double[] ema3 = calculateEma(ema2, period, multiplier);

        List<Entry> trixEntries = new ArrayList<>();
        List<Entry> zeroLine = new ArrayList<>();

        for (int i = period * 3; i < candles.size(); i++) {
            double prevEma3 = ema3[i - 1];
            double currEma3 = ema3[i];

            double trix = prevEma3 == 0 ? 0 : ((currEma3 - prevEma3) / prevEma3) * 10000.0; // Scaled up slightly for readability

            trixEntries.add(new Entry(i, (float) trix));
            zeroLine.add(new Entry(i, 0f));
        }

        LineDataSet trixSet = makeLineSet(trixEntries, "TRIX", getColor(), 1.4f);
        LineDataSet zeroSet = makeDashedLineSet(zeroLine, "Zero", Color.GRAY);

        trixSet.setAxisDependency(YAxis.AxisDependency.RIGHT);
        zeroSet.setAxisDependency(YAxis.AxisDependency.RIGHT);

        r.subChartLines.add(trixSet);
        r.subChartLines.add(zeroSet);

        return r;
    }

    private double[] extractCloses(ArrayList<Candle> candles) {
        double[] closes = new double[candles.size()];
        for (int i = 0; i < candles.size(); i++) closes[i] = candles.get(i).close;
        return closes;
    }

    private double[] calculateEma(double[] data, int period, double multiplier) {
        double[] ema = new double[data.length];
        if (data.length < period) return ema;

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
        int period = (int) getParam("period");
        if (data == null || data.size() < period * 3) return 50;

        Result result = compute(data);
        if (result.subChartLines.isEmpty()) return 50;

        LineDataSet trixSet = result.subChartLines.get(0);
        if (trixSet.getEntryCount() < 2) return 50;

        float currentTrix = trixSet.getEntryForIndex(trixSet.getEntryCount() - 1).getY();

        // TRIX crossing zero indicates trend change
        if (currentTrix > 0) return 75;
        if (currentTrix < 0) return 25;

        return 50;
    }
}