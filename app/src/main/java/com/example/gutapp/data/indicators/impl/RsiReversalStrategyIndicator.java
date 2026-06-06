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

public class RsiReversalStrategyIndicator extends Indicator {

    public RsiReversalStrategyIndicator() {
        params.add(new Param("rsiPeriod", "RSI Lookback", Param.Type.INTEGER, 2, 50, 14));
        params.add(new Param("overbought", "Overbought Level", Param.Type.INTEGER, 50, 95, 70));
        params.add(new Param("oversold", "Oversold Level", Param.Type.INTEGER, 5, 50, 30));

        // Visibility Toggle: 0 = Hide Lines, 1 = Show Lines
        params.add(new Param("showRsiLines", "Show Trackers (0=No, 1=Yes)", Param.Type.INTEGER, 0, 1, 1));

        setColor(Color.parseColor("#FFEB3B"));
    }

    @Override public String  getId()          { return "rsi_reversal_strategy"; }
    @Override public String  getDisplayName() { return "RSI Reversal Strategy"; }
    @Override public String  getTag()         { return "RSI_STRAT"; }
    @Override public boolean isSubChart()     { return false; }

    @Override
    public Indicator newInstance() {
        return new RsiReversalStrategyIndicator();
    }

    @Override
    public Result compute(ArrayList<Candle> candles) {
        Result r = new Result();
        int period = (int) getParam("rsiPeriod");
        int overbought = (int) getParam("overbought");
        int oversold = (int) getParam("oversold");
        int showRsiLines = (int) getParam("showRsiLines");

        if (candles == null || candles.size() < period + 2) {
            return r;
        }

        int len = candles.size();
        double[] rsi = calculateRSI(candles, period);

        // --- OPTIONAL LINE RENDERING (SCALED FOR OVERLAY IF SELECTED) ---
        if (showRsiLines == 1) {
            List<Entry> rsiEntries = new ArrayList<>();
            for (int i = period; i < len; i++) {
                // Map the 0-100 RSI structure safely into the local asset absolute scaling zone for layout visibility
                double assetPrice = candles.get(i).close;
                rsiEntries.add(new Entry(i, (float) assetPrice));
            }
            r.overlayLines.add(makeLineSet(rsiEntries, "RSI Tracking Price", getColor(), 1.3f));
        }

        // --- SIGNAL EXTRACTION LOOP ---
        for (int i = period + 1; i < len; i++) {
            double currentRsi = rsi[i];
            double prevRsi = rsi[i - 1];

            Candle c = candles.get(i);
            long candleTs = c.timestamp;
            double verticalOffset = (c.high - c.low) * 0.4;
            if (verticalOffset == 0) verticalOffset = c.close * 0.003;

            // RSI exit out of extreme oversold terrain -> BUY Signal
            if (prevRsi <= oversold && currentRsi > oversold) {
                double targetYPrice = c.low - verticalOffset;
                DrawingStyle buyStyle = DrawingStyle.solid(Color.parseColor("#4CAF50"), 1.5f);
                r.drawings.add(new ChartDrawing.TextAnnotation(candleTs, targetYPrice, "▲ BUY", buyStyle, Source.INDICATOR));
            }
            // RSI cross down out of overbought peak -> SELL Signal
            else if (prevRsi >= overbought && currentRsi < overbought) {
                double targetYPrice = c.high + verticalOffset;
                DrawingStyle sellStyle = DrawingStyle.solid(Color.parseColor("#E53935"), 1.5f);
                r.drawings.add(new ChartDrawing.TextAnnotation(candleTs, targetYPrice, "▼ SELL", sellStyle, Source.INDICATOR));
            }
        }

        return r;
    }

    private double[] calculateRSI(ArrayList<Candle> candles, int period) {
        double[] rsi = new double[candles.size()];
        double gains = 0, losses = 0;

        for (int i = 1; i <= period; i++) {
            double diff = candles.get(i).close - candles.get(i - 1).close;
            if (diff > 0) gains += diff;
            else losses -= diff;
        }

        double avgGain = gains / period;
        double avgLoss = losses / period;
        rsi[period] = avgLoss == 0 ? 100 : 100 - (100 / (1 + (avgGain / avgLoss)));

        for (int i = period + 1; i < candles.size(); i++) {
            double diff = candles.get(i).close - candles.get(i - 1).close;
            double cg = diff > 0 ? diff : 0;
            double cl = diff < 0 ? -diff : 0;

            avgGain = ((avgGain * (period - 1)) + cg) / period;
            avgLoss = ((avgLoss * (period - 1)) + cl) / period;

            rsi[i] = avgLoss == 0 ? 100 : 100 - (100 / (1 + (avgGain / avgLoss)));
        }
        return rsi;
    }

    @Override
    public int calculateBias(ArrayList<Candle> data) {
        int period = (int) getParam("rsiPeriod");
        if (data == null || data.size() < period + 1) return 50;

        double[] rsi = calculateRSI(data, period);
        double lastRsi = rsi[data.size() - 1];

        if (lastRsi > 55) return 70;
        if (lastRsi < 45) return 30;
        return 50;
    }
}