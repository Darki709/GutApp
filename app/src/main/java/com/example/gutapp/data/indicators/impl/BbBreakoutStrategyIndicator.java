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
 * Bollinger Bands Breakout Strategy Indicator.
 * Generates trading signals based on price breakouts through volatility-driven standard deviation bands.
 * Supports togglable rendering of underlying band visualization layers.
 */
public class BbBreakoutStrategyIndicator extends Indicator {

    /**
     * Initializes the indicator with lookback, standard deviation, and visibility parameters.
     */
    public BbBreakoutStrategyIndicator() {
        params.add(new Param("period", "BB Lookback Period", Param.Type.INTEGER, 5, 100, 20));
        params.add(new Param("stdDev", "Standard Deviation", Param.Type.FLOAT, 0.5f, 5.0f, 2.0f));
        params.add(new Param("showBands", "Show BB Bands (0=No, 1=Yes)", Param.Type.INTEGER, 0, 1, 1));

        setColor(Color.parseColor("#9C27B0"));
    }

    @Override public String  getId()          { return "bb_breakout_strategy"; }
    @Override public String  getDisplayName() { return "Bollinger Bands Breakout Strategy"; }
    @Override public String  getTag()         { return "BB_STRAT"; }
    @Override public boolean isSubChart()     { return false; }

    @Override
    public Indicator newInstance() {
        return new BbBreakoutStrategyIndicator();
    }

    /**
     * Computes the Bollinger Bands and generates buy/sell text annotations on breakout events.
     * * @param candles The historical series of chart data.
     * @return The computed chart lines and drawing annotations.
     */
    @Override
    public Result compute(ArrayList<Candle> candles) {
        Result r = new Result();
        int period = (int) getParam("period");
        float stdDev = getParam("stdDev");
        int showBands = (int) getParam("showBands");

        if (candles == null || candles.size() < period + 1) {
            return r;
        }

        int len = candles.size();
        double[] upperBands = new double[len];
        double[] lowerBands = new double[len];
        double[] middleBands = new double[len];

        for (int i = period - 1; i < len; i++) {
            double sum = 0;
            for (int j = i - period + 1; j <= i; j++) {
                sum += candles.get(j).close;
            }
            double mean = sum / period;
            middleBands[i] = mean;

            double varianceSum = 0;
            for (int j = i - period + 1; j <= i; j++) {
                varianceSum += Math.pow(candles.get(j).close - mean, 2);
            }
            double sd = Math.sqrt(varianceSum / period);

            upperBands[i] = mean + (stdDev * sd);
            lowerBands[i] = mean - (stdDev * sd);
        }

        if (showBands == 1) {
            List<Entry> upperEntries = new ArrayList<>();
            List<Entry> lowerEntries = new ArrayList<>();
            List<Entry> midEntries = new ArrayList<>();

            for (int i = period - 1; i < len; i++) {
                upperEntries.add(new Entry(i, (float) upperBands[i]));
                lowerEntries.add(new Entry(i, (float) lowerBands[i]));
                midEntries.add(new Entry(i, (float) middleBands[i]));
            }

            r.overlayLines.add(makeLineSet(upperEntries, "BB Upper", getColor(), 1.1f));
            r.overlayLines.add(makeLineSet(lowerEntries, "BB Lower", getColor(), 1.1f));
            r.overlayLines.add(makeDashedLineSet(midEntries, "BB Basis", Color.argb(100, 158, 158, 158)));
        }

        for (int i = period; i < len; i++) {
            Candle curr = candles.get(i);
            Candle prev = candles.get(i - 1);

            long candleTs = curr.timestamp;
            double verticalPadding = (curr.high - curr.low) * 0.35;
            if (verticalPadding == 0) verticalPadding = curr.close * 0.0025;

            if (prev.close <= upperBands[i - 1] && curr.close > upperBands[i]) {
                double targetYPrice = curr.low - verticalPadding;
                DrawingStyle buyStyle = DrawingStyle.solid(Color.parseColor("#00E676"), 1.5f);
                r.drawings.add(new ChartDrawing.TextAnnotation(candleTs, targetYPrice, "▲ BUY", buyStyle, Source.INDICATOR));
            }
            else if (prev.close >= lowerBands[i - 1] && curr.close < lowerBands[i]) {
                double targetYPrice = curr.high + verticalPadding;
                DrawingStyle sellStyle = DrawingStyle.solid(Color.parseColor("#FF1744"), 1.5f);
                r.drawings.add(new ChartDrawing.TextAnnotation(candleTs, targetYPrice, "▼ SELL", sellStyle, Source.INDICATOR));
            }
        }

        return r;
    }

    /**
     * Evaluates current market bias based on closing price position relative to the baseline mean.
     * * @param data The historical series of chart data.
     * @return Bias integer indicator scale from 0 to 100.
     */
    @Override
    public int calculateBias(ArrayList<Candle> data) {
        int period = (int) getParam("period");
        if (data == null || data.size() < period) return 50;

        int lastIdx = data.size() - 1;
        double sum = 0;
        for (int i = lastIdx - period + 1; i <= lastIdx; i++) {
            sum += data.get(i).close;
        }
        double sma = sum / period;
        double close = data.get(lastIdx).close;

        if (close > sma) return 70;
        if (close < sma) return 30;
        return 50;
    }
}