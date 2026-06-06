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
 * SuperTrend Strategy Indicator that produces actionable execution signals
 * based on dynamic ATR volatility channel breaks.
 */
public class SuperTrendStrategyIndicator extends Indicator {

    public SuperTrendStrategyIndicator() {
        // Lookback constraints
        params.add(new Param("atrPeriod", "ATR Period", Param.Type.INTEGER, 3, 50, 10));
        params.add(new Param("multiplier", "ATR Multiplier", Param.Type.FLOAT, 1.0f, 10.0f, 3.0f));

        // Visibility Toggle: 0 = Hide Lines, 1 = Show Lines
        params.add(new Param("showLines", "Show Trend Lines (0=No, 1=Yes)", Param.Type.INTEGER, 0, 1, 1));

        setColor(Color.parseColor("#00E5FF"));
    }

    @Override public String  getId()          { return "supertrend_strategy"; }
    @Override public String  getDisplayName() { return "SuperTrend Strategy"; }
    @Override public String  getTag()         { return "ST_STRAT"; }
    @Override public boolean isSubChart()     { return false; }

    @Override
    public Indicator newInstance() {
        return new SuperTrendStrategyIndicator();
    }

    @Override
    public Result compute(ArrayList<Candle> candles) {
        Result r = new Result();
        int atrPeriod = (int) getParam("atrPeriod");
        float multiplier = getParam("multiplier");
        int showLines = (int) getParam("showLines");

        if (candles == null || candles.size() < atrPeriod + 1) {
            return r;
        }

        int len = candles.size();
        double[] atr = calculateATR(candles, atrPeriod);

        double[] basicUpperBand = new double[len];
        double[] basicLowerBand = new double[len];
        double[] finalUpperBand = new double[len];
        double[] finalLowerBand = new double[len];
        double[] superTrend = new double[len];
        boolean[] isUpTrend = new boolean[len]; // Tracks trend state changes per index

        // Pre-populate core volatility envelopes
        for (int i = 0; i < len; i++) {
            Candle c = candles.get(i);
            double midpoint = (c.high + c.low) / 2.0;
            basicUpperBand[i] = midpoint + (multiplier * atr[i]);
            basicLowerBand[i] = midpoint - (multiplier * atr[i]);
        }

        // Initialize historical seed elements
        finalUpperBand[atrPeriod] = basicUpperBand[atrPeriod];
        finalLowerBand[atrPeriod] = basicLowerBand[atrPeriod];
        superTrend[atrPeriod] = basicUpperBand[atrPeriod];
        isUpTrend[atrPeriod] = false;

        // --- CALCULATION MATRIX LOOP ---
        for (int i = atrPeriod + 1; i < len; i++) {
            Candle curr = candles.get(i);
            Candle prev = candles.get(i - 1);

            // Calculate final upper band boundary constraint logic
            if (basicUpperBand[i] < finalUpperBand[i - 1] || prev.close > finalUpperBand[i - 1]) {
                finalUpperBand[i] = basicUpperBand[i];
            } else {
                finalUpperBand[i] = finalUpperBand[i - 1];
            }

            // Calculate final lower band boundary constraint logic
            if (basicLowerBand[i] > finalLowerBand[i - 1] || prev.close < finalLowerBand[i - 1]) {
                finalLowerBand[i] = basicLowerBand[i];
            } else {
                finalLowerBand[i] = finalLowerBand[i - 1];
            }

            // Determine directional tracking line state switches
            if (superTrend[i - 1] == finalUpperBand[i - 1]) {
                isUpTrend[i] = curr.close > finalUpperBand[i];
            } else {
                isUpTrend[i] = !(curr.close < finalLowerBand[i]);
            }

            superTrend[i] = isUpTrend[i] ? finalLowerBand[i] : finalUpperBand[i];
        }

        // --- OPTIONAL TREND LINE RENDERING ---
        if (showLines == 1) {
            List<Entry> upperEntries = new ArrayList<>();
            List<Entry> lowerEntries = new ArrayList<>();

            for (int i = atrPeriod + 1; i < len; i++) {
                if (isUpTrend[i]) {
                    lowerEntries.add(new Entry(i, (float) superTrend[i]));
                } else {
                    upperEntries.add(new Entry(i, (float) superTrend[i]));
                }
            }

            // Generate fragmented lines to visually distinguish bullish vs bearish states
            if (!lowerEntries.isEmpty()) {
                r.overlayLines.add(makeLineSet(lowerEntries, "ST Bullish", Color.parseColor("#00E676"), 1.6f));
            }
            if (!upperEntries.isEmpty()) {
                r.overlayLines.add(makeLineSet(upperEntries, "ST Bearish", Color.parseColor("#FF1744"), 1.6f));
            }
        }

        // --- SIGNAL GENERATION LOOP ---
        for (int i = atrPeriod + 2; i < len; i++) {
            boolean currentTrendUp = isUpTrend[i];
            boolean previousTrendUp = isUpTrend[i - 1];

            long candleTs = candles.get(i).timestamp;
            double verticalOffsetPadding = (candles.get(i).high - candles.get(i).low) * 0.4;
            if (verticalOffsetPadding == 0) verticalOffsetPadding = candles.get(i).close * 0.003;

            // Trend flips from bearish to bullish -> BUY Signal
            if (!previousTrendUp && currentTrendUp) {
                double targetYPrice = candles.get(i).low - verticalOffsetPadding;
                DrawingStyle buyStyle = DrawingStyle.solid(Color.parseColor("#4CAF50"), 1.5f);
                r.drawings.add(new ChartDrawing.TextAnnotation(candleTs, targetYPrice, "▲ BUY", buyStyle, Source.INDICATOR));
            }
            // Trend flips from bullish to bearish -> SELL Signal
            else if (previousTrendUp && !currentTrendUp) {
                double targetYPrice = candles.get(i).high + verticalOffsetPadding;
                DrawingStyle sellStyle = DrawingStyle.solid(Color.parseColor("#E53935"), 1.5f);
                r.drawings.add(new ChartDrawing.TextAnnotation(candleTs, targetYPrice, "▼ SELL", sellStyle, Source.INDICATOR));
            }
        }

        return r;
    }

    /**
     * Calculates True Range tracking variables and applies Wilder's smoothing.
     */
    private double[] calculateATR(ArrayList<Candle> candles, int period) {
        int total = candles.size();
        double[] atr = new double[total];
        double[] trueRange = new double[total];

        trueRange[0] = candles.get(0).high - candles.get(0).low;
        for (int i = 1; i < total; i++) {
            double highLow = candles.get(i).high - candles.get(i).low;
            double highPastClose = Math.abs(candles.get(i).high - candles.get(i - 1).close);
            double lowPastClose = Math.abs(candles.get(i).low - candles.get(i - 1).close);
            trueRange[i] = Math.max(highLow, Math.max(highPastClose, lowPastClose));
        }

        double currentSum = 0;
        for (int i = 0; i < period; i++) {
            currentSum += trueRange[i];
        }
        atr[period] = currentSum / period;

        for (int i = period + 1; i < total; i++) {
            atr[i] = ((atr[i - 1] * (period - 1)) + trueRange[i]) / period;
        }

        return atr;
    }

    @Override
    public int calculateBias(ArrayList<Candle> data) {
        int atrPeriod = (int) getParam("atrPeriod");
        if (data == null || data.size() < atrPeriod + 2) return 50;

        // Perform lookback evaluation matching the current active orientation array
        Result partialResult = compute(data);

        // Return 50 if calculation limits or boundaries failed
        if (partialResult.overlayLines.isEmpty()) return 50;

        int finalIndex = data.size() - 1;
        double currentClose = data.get(finalIndex).close;
        double currentOpen = data.get(finalIndex).open;

        if (currentClose > currentOpen) return 70;
        if (currentClose < currentOpen) return 30;
        return 50;
    }
}