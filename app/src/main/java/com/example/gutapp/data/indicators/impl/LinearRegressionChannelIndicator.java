package com.example.gutapp.data.indicators.impl;

import android.graphics.Color;
import com.example.gutapp.data.drawing.ChartDrawing;
import com.example.gutapp.data.drawing.ChartDrawing.DrawingStyle;
import com.example.gutapp.data.drawing.ChartDrawing.Source;
import com.example.gutapp.data.indicators.Indicator;
import com.example.gutapp.data.models.Candle;
import java.util.ArrayList;

public class LinearRegressionChannelIndicator extends Indicator {

    public LinearRegressionChannelIndicator() {
        // Parameter configuration: Define customizable trailing lookback window
        params.add(new Param("period", "Lookback Period", Param.Type.INTEGER, 5, 300, 50));

        // Default indicator identification color
        setColor(Color.parseColor("#FFEB3B")); // Bright Yellow
    }

    @Override public String  getId()          { return "linear_reg_channel"; }
    @Override public String  getDisplayName() { return "Linear Regression Channel"; }
    @Override public String  getTag()         { return "LRC"; }
    @Override public boolean isSubChart()     { return false; } // Renders directly on main canvas

    @Override
    public Indicator newInstance() {
        return new LinearRegressionChannelIndicator();
    }

    @Override
    public Result compute(ArrayList<Candle> candles) {
        Result r = new Result();
        int period = (int) getParam("period");

        // Ensure historical data context satisfies our window requirements
        if (candles == null || candles.size() < period) {
            return r;
        }

        int totalSize = candles.size();

        // Pull spatial orientation timestamps for anchor points
        // X-axis calculations utilize absolute Unix timestamps in seconds per current API requirements
        long startTs = candles.get(totalSize - period).timestamp;
        long endTs   = candles.get(totalSize - 1).timestamp;

        // Configure style aesthetics matching interface conventions
        DrawingStyle midlineStyle = DrawingStyle.solid(getColor(), 2.0f);

        // Construct the core architectural element
        ChartDrawing.LinearRegression channels = new ChartDrawing.LinearRegression(
                startTs,
                endTs,
                midlineStyle,
                Source.INDICATOR
        );

        // Instruct rendering pipeline to evaluate and overlay variance bands
        channels.drawChannel = true;

        r.drawings.add(channels);
        return r;
    }

    @Override
    public int calculateBias(ArrayList<Candle> data) {
        int period = (int) getParam("period");
        if (data == null || data.size() < period) {
            return 50; // Return neutral state if context criteria is unmet
        }

        int n = data.size();

        // Perform standard Least Squares Linear Regression math to determine final slope vector
        double sumX = 0, sumY = 0, sumXY = 0, sumX2 = 0;
        for (int i = n - period; i < n; i++) {
            // Using localized relative values for precision preservation
            double x = i - (n - period);
            double y = data.get(i).close;
            sumX += x;
            sumY += y;
            sumXY += x * y;
            sumX2 += x * x;
        }

        double denominator = (period * sumX2) - (sumX * sumX);
        if (denominator == 0) return 50;

        double slope = ((period * sumXY) - (sumX * sumY)) / denominator;

        // Quantize bias state based on vector trajectory slope orientation
        if (slope > 0) return 75;  // Bullish trajectory
        if (slope < 0) return 25;  // Bearish trajectory
        return 50;                 // Flat/Neutral
    }
}