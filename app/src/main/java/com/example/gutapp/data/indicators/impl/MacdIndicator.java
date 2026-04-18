package com.example.gutapp.data.indicators.impl;

import android.graphics.Color;

import com.example.gutapp.data.indicators.Indicator;
import com.example.gutapp.data.models.Candle;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineDataSet;

import java.util.ArrayList;
import java.util.List;

/**
 * MACD indicator — outputs 3 sub-chart lines:
 *  - MACD line (fast EMA - slow EMA)
 *  - Signal line (EMA of MACD)
 *  - Histogram (MACD - Signal), represented as a line dataset
 *    (rendered as a bar via CombinedChart's BarData in StockChart)
 */
public class MacdIndicator extends Indicator {

    public MacdIndicator() {
        params.add(new Param("fast",   "Fast EMA",   Param.Type.INTEGER, 2,  50,  12));
        params.add(new Param("slow",   "Slow EMA",   Param.Type.INTEGER, 5,  200, 26));
        params.add(new Param("signal", "Signal EMA", Param.Type.INTEGER, 2,  50,  9));
    }

    @Override public String getId()          { return "macd"; }
    @Override public String getDisplayName() { return "MACD"; }
    @Override public String getTag()         { return "MACD"; }
    @Override public boolean isSubChart()    { return true; }

    @Override
    public Result compute(ArrayList<Candle> candles) {
        int fast   = (int) getParam("fast");
        int slow   = (int) getParam("slow");
        int signal = (int) getParam("signal");

        if (candles.size() < slow + signal) return new Result();

        // Compute fast and slow EMAs
        double[] fastEma = computeEma(candles, fast);
        double[] slowEma = computeEma(candles, slow);

        // MACD line = fast - slow (only valid from slow-1 onwards)
        List<Entry> macdEntries = new ArrayList<>();
        for (int i = slow - 1; i < candles.size(); i++) {
            macdEntries.add(new Entry(i, (float)(fastEma[i] - slowEma[i])));
        }

        // Signal = EMA of MACD line
        double sigMultiplier = 2.0 / (signal + 1);
        double sigEma = macdEntries.get(0).getY();
        List<Entry> signalEntries = new ArrayList<>();
        List<Entry> histEntries   = new ArrayList<>();

        for (int i = 0; i < macdEntries.size(); i++) {
            sigEma = (macdEntries.get(i).getY() - sigEma) * sigMultiplier + sigEma;
            if (i >= signal - 1) {
                float x    = macdEntries.get(i).getX();
                float hist = macdEntries.get(i).getY() - (float) sigEma;
                signalEntries.add(new Entry(x, (float) sigEma));
                histEntries.add(  new Entry(x, hist));
            }
        }

        Result r = new Result();

        LineDataSet macdSet   = makeLineSet(macdEntries,   "MACD",   Color.parseColor("#2196F3"), 1.4f);
        LineDataSet signalSet = makeLineSet(signalEntries, "Signal", Color.parseColor("#FF9800"), 1.2f);
        // Histogram — colored green/red per bar
        LineDataSet histSet   = makeLineSet(histEntries, "Hist", Color.parseColor("#546E7A"), 1f);

        // All on right axis so they share their own Y scale
        macdSet.setAxisDependency(YAxis.AxisDependency.RIGHT);
        signalSet.setAxisDependency(YAxis.AxisDependency.RIGHT);
        histSet.setAxisDependency(YAxis.AxisDependency.RIGHT);

        r.subChartLines.add(macdSet);
        r.subChartLines.add(signalSet);
        r.subChartLines.add(histSet);
        return r;
    }

    private double[] computeEma(ArrayList<Candle> candles, int period) {
        double[] ema = new double[candles.size()];
        double mult = 2.0 / (period + 1);
        ema[0] = candles.get(0).close;
        for (int i = 1; i < candles.size(); i++) {
            ema[i] = (candles.get(i).close - ema[i-1]) * mult + ema[i-1];
        }
        return ema;
    }
}