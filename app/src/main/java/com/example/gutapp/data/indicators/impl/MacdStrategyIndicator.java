package com.example.gutapp.data.indicators.impl;

import android.graphics.Color;
import com.example.gutapp.data.drawing.ChartDrawing;
import com.example.gutapp.data.drawing.ChartDrawing.DrawingStyle;
import com.example.gutapp.data.drawing.ChartDrawing.Source;
import com.example.gutapp.data.indicators.Indicator;
import com.example.gutapp.data.models.Candle;
import com.github.mikephil.charting.data.Entry;
import java.util.ArrayList;
import java.util.List;

/**
 * Moving Average Convergence Divergence (MACD) Histogram Reversal Strategy.
 * Identifies momentum exhaustions and structural pivots around the zero center line
 * to execute directional breakout entries.
 */
public class MacdStrategyIndicator extends Indicator {

    /**
     * Initializes the MACD Strategy configuration parameters and baseline styling elements.
     */
    public MacdStrategyIndicator() {
        params.add(new Param("fastPeriod", "Fast EMA Period", Param.Type.INTEGER, 3, 50, 12));
        params.add(new Param("slowPeriod", "Slow EMA Period", Param.Type.INTEGER, 10, 100, 26));
        params.add(new Param("signalPeriod", "Signal Period", Param.Type.INTEGER, 2, 30, 9));
        params.add(new Param("showMacdLines", "Show Trend Lines (0=No, 1=Yes)", Param.Type.INTEGER, 0, 1, 1));

        setColor(Color.parseColor("#FF5722"));
    }

    @Override public String  getId()          { return "macd_strategy"; }
    @Override public String  getDisplayName() { return "MACD Strategy"; }
    @Override public String  getTag()         { return "MACD_STRAT"; }
    @Override public boolean isSubChart()     { return false; }

    @Override
    public Indicator newInstance() {
        return new MacdStrategyIndicator();
    }

    /**
     * Calculates double Exponential Moving Averages, extracts the standard Signal line tracking vector,
     * and derives conditional trend annotations.
     *
     * @param candles The historical series of chart data.
     * @return The computed chart lines and drawing annotations.
     */
    @Override
    public Result compute(ArrayList<Candle> candles) {
        Result r = new Result();
        int fastP = (int) getParam("fastPeriod");
        int slowP = (int) getParam("slowPeriod");
        int signalP = (int) getParam("signalPeriod");
        int showMacdLines = (int) getParam("showMacdLines");

        if (candles == null || candles.size() < slowP + signalP + 2) {
            return r;
        }

        int len = candles.size();
        double[] fastEma = calculateEMA(candles, fastP);
        double[] slowEma = calculateEMA(candles, slowP);

        double[] macdLine = new double[len];
        for (int i = 0; i < len; i++) {
            macdLine[i] = fastEma[i] - slowEma[i];
        }

        double[] signalLine = calculateEMAForSeries(macdLine, signalP, slowP);
        double[] histogram = new double[len];
        for (int i = slowP; i < len; i++) {
            histogram[i] = macdLine[i] - signalLine[i];
        }

        if (showMacdLines == 1) {
            List<Entry> basisEntries = new ArrayList<>();
            double sum = 0;
            for (int i = 0; i < len; i++) {
                sum += candles.get(i).close;
                if (i >= slowP) {
                    basisEntries.add(new Entry(i, (float) (sum / (i + 1))));
                }
            }
            r.overlayLines.add(makeLineSet(basisEntries, "MACD Anchor Basis", getColor(), 1.2f));
        }

        int evaluationOffset = slowP + signalP;
        for (int i = evaluationOffset; i < len; i++) {
            double currentHist = histogram[i];
            double prevHist = histogram[i - 1];

            Candle currCandle = candles.get(i);
            long candleTs = currCandle.timestamp;
            double padding = (currCandle.high - currCandle.low) * 0.4;
            if (padding == 0) padding = currCandle.close * 0.003;

            if (prevHist < 0 && currentHist > prevHist && histogram[i - 2] >= prevHist) {
                double targetYPrice = currCandle.low - padding;
                DrawingStyle buyStyle = DrawingStyle.solid(Color.parseColor("#2E7D32"), 1.5f);
                r.drawings.add(new ChartDrawing.TextAnnotation(candleTs, targetYPrice, "▲ BUY", buyStyle, Source.INDICATOR));
            }
            else if (prevHist > 0 && currentHist < prevHist && histogram[i - 2] <= prevHist) {
                double targetYPrice = currCandle.high + padding;
                DrawingStyle sellStyle = DrawingStyle.solid(Color.parseColor("#C62828"), 1.5f);
                r.drawings.add(new ChartDrawing.TextAnnotation(candleTs, targetYPrice, "▼ SELL", sellStyle, Source.INDICATOR));
            }
        }

        return r;
    }

    private double[] calculateEMA(ArrayList<Candle> candles, int period) {
        double[] ema = new double[candles.size()];
        if (candles.isEmpty()) return ema;

        double multiplier = 2.0 / (period + 1);
        ema[0] = candles.get(0).close;

        for (int i = 1; i < candles.size(); i++) {
            ema[i] = ((candles.get(i).close - ema[i - 1]) * multiplier) + ema[i - 1];
        }
        return ema;
    }

    private double[] calculateEMAForSeries(double[] values, int period, int startOffset) {
        double[] ema = new double[values.length];
        if (values.length <= startOffset) return ema;

        double multiplier = 2.0 / (period + 1);
        ema[startOffset] = values[startOffset];

        for (int i = startOffset + 1; i < values.length; i++) {
            ema[i] = ((values[i] - ema[i - 1]) * multiplier) + ema[i - 1];
        }
        return ema;
    }

    @Override
    public int calculateBias(ArrayList<Candle> data) {
        int slowP = (int) getParam("slowPeriod");
        if (data == null || data.size() < slowP + 5) return 50;

        double[] fastEma = calculateEMA(data, (int) getParam("fastPeriod"));
        double[] slowEma = calculateEMA(data, slowP);
        int idx = data.size() - 1;

        if (fastEma[idx] > slowEma[idx]) return 75;
        if (fastEma[idx] < slowEma[idx]) return 25;
        return 50;
    }
}