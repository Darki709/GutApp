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

public class MaCrossoverStrategyIndicator extends Indicator {

    public MaCrossoverStrategyIndicator() {
        // Core tracking metrics
        params.add(new Param("fastPeriod", "Fast MA Period", Param.Type.INTEGER, 2, 50, 9));
        params.add(new Param("slowPeriod", "Slow MA Period", Param.Type.INTEGER, 10, 200, 21));

        // Visibility toggle: 0 = Off (Hide Lines), 1 = On (Show Lines)
        params.add(new Param("showLines", "Show MA Lines (0=No, 1=Yes)", Param.Type.INTEGER, 0, 1, 1));

        setColor(Color.parseColor("#2196F3"));
    }

    @Override public String  getId()          { return "ma_crossover_strategy"; }
    @Override public String  getDisplayName() { return "MA Crossover Strategy"; }
    @Override public String  getTag()         { return "MA_CROSS_STRAT"; }
    @Override public boolean isSubChart()     { return false; }

    @Override
    public Indicator newInstance() {
        return new MaCrossoverStrategyIndicator();
    }

    @Override
    public Result compute(ArrayList<Candle> candles) {
        Result r = new Result();
        int fastP = (int) getParam("fastPeriod");
        int slowP = (int) getParam("slowPeriod");
        int showLines = (int) getParam("showLines");

        if (candles == null || candles.size() < slowP + 1) {
            return r;
        }

        double[] fastMAs = calculateSMA(candles, fastP);
        double[] slowMAs = calculateSMA(candles, slowP);

        // --- OPTIONAL MA LINE RENDERING ---
        // If the user sets the toggle to 1 (True), populate overlayLines
        if (showLines == 1) {
            List<Entry> fastEntries = new ArrayList<>();
            List<Entry> slowEntries = new ArrayList<>();

            // Core chart lines use sequential array indices (i) for their X-coordinates
            for (int i = slowP; i < candles.size(); i++) {
                fastEntries.add(new Entry(i, (float) fastMAs[i]));
                slowEntries.add(new Entry(i, (float) slowMAs[i]));
            }

            r.overlayLines.add(makeLineSet(fastEntries, "Fast MA", getColor(), 1.2f));
            r.overlayLines.add(makeDashedLineSet(slowEntries, "Slow MA", Color.parseColor("#9E9E9E")));
        }

        // --- CORE SIGNAL PROCESSING LOOP ---
        for (int i = slowP; i < candles.size(); i++) {
            double currentFast = fastMAs[i];
            double currentSlow = slowMAs[i];
            double prevFast    = fastMAs[i - 1];
            double prevSlow    = slowMAs[i - 1];

            long candleTs = candles.get(i).timestamp; // TextAnnotations use Unix timestamps for X-coordinates

            double padding = (candles.get(i).high - candles.get(i).low) * 0.3;
            if (padding == 0) padding = candles.get(i).close * 0.002;

            // Golden Cross -> Buy Signal
            if (prevFast <= prevSlow && currentFast > currentSlow) {
                double targetYPrice = candles.get(i).low - padding;

                DrawingStyle buyStyle = DrawingStyle.solid(Color.parseColor("#4CAF50"), 1.5f);

                r.drawings.add(new ChartDrawing.TextAnnotation(candleTs, targetYPrice, "▲ BUY", buyStyle, Source.INDICATOR));
            }
            // Death Cross -> Sell Signal
            else if (prevFast >= prevSlow && currentFast < currentSlow) {
                double targetYPrice = candles.get(i).high + padding;

                DrawingStyle sellStyle = DrawingStyle.solid(Color.parseColor("#E53935"), 1.5f);

                r.drawings.add(new ChartDrawing.TextAnnotation(candleTs, targetYPrice, "▼ SELL", sellStyle, Source.INDICATOR));
            }
        }

        return r;
    }

    private double[] calculateSMA(ArrayList<Candle> candles, int period) {
        double[] output = new double[candles.size()];
        double runningSum = 0;

        for (int i = 0; i < candles.size(); i++) {
            runningSum += candles.get(i).close;
            if (i >= period) {
                runningSum -= candles.get(i - period).close;
                output[i] = runningSum / period;
            } else if (i == period - 1) {
                output[i] = runningSum / period;
            } else {
                output[i] = 0;
            }
        }
        return output;
    }

    @Override
    public int calculateBias(ArrayList<Candle> data) {
        int fastP = (int) getParam("fastPeriod");
        int slowP = (int) getParam("slowPeriod");
        if (data == null || data.size() < slowP) return 50;

        double[] fastMAs = calculateSMA(data, fastP);
        double[] slowMAs = calculateSMA(data, slowP);

        int lastIdx = data.size() - 1;

        if (fastMAs[lastIdx] > slowMAs[lastIdx]) return 75;
        if (fastMAs[lastIdx] < slowMAs[lastIdx]) return 25;
        return 50;
    }
}