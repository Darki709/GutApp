package com.example.gutapp.data.indicators.impl;

import android.graphics.Color;
import com.example.gutapp.data.drawing.ChartDrawing;
import com.example.gutapp.data.drawing.ChartDrawing.DrawingStyle;
import com.example.gutapp.data.drawing.ChartDrawing.Source;
import com.example.gutapp.data.indicators.Indicator;
import com.example.gutapp.data.models.Candle;
import java.util.ArrayList;

public class PivotPointsIndicator extends Indicator {

    public PivotPointsIndicator() { setColor(Color.parseColor("#ECEFF1")); }

    @Override public String  getId()          { return "pivots"; }
    @Override public String  getDisplayName() { return "Pivot Points"; }
    @Override public String  getTag()         { return "PP"; }
    @Override public boolean isSubChart()     { return false; }
    @Override public Indicator newInstance()  { return new PivotPointsIndicator(); }

    @Override
    public Result compute(ArrayList<Candle> candles) {
        Result r = new Result();
        if (candles.size() < 2) return r;

        Candle prev  = candles.get(candles.size() - 2);
        double pivot = (prev.high + prev.low + prev.close) / 3.0;
        double r1    = 2 * pivot - prev.low;
        double s1    = 2 * pivot - prev.high;
        double r2    = pivot + (prev.high - prev.low);
        double s2    = pivot - (prev.high - prev.low);
        double r3    = prev.high + 2 * (pivot - prev.low);
        double s3    = prev.low  - 2 * (prev.high - pivot);

        // HorizontalLine takes (price, label, style, source) — no timestamps needed
        r.drawings.add(hline(pivot, "PP", DrawingStyle.solid(Color.parseColor("#ECEFF1"), 1.2f)));
        r.drawings.add(hline(r1, "R1", DrawingStyle.dashed(Color.parseColor("#EF9A9A"))));
        r.drawings.add(hline(r2, "R2", DrawingStyle.dashed(Color.parseColor("#E57373"))));
        r.drawings.add(hline(r3, "R3", DrawingStyle.dashed(Color.parseColor("#F44336"))));
        r.drawings.add(hline(s1, "S1", DrawingStyle.dashed(Color.parseColor("#A5D6A7"))));
        r.drawings.add(hline(s2, "S2", DrawingStyle.dashed(Color.parseColor("#66BB6A"))));
        r.drawings.add(hline(s3, "S3", DrawingStyle.dashed(Color.parseColor("#4CAF50"))));
        return r;
    }

    @Override
    public int calculateBias(ArrayList<Candle> data) {
        if (data == null || data.size() < 2) return 50;
        Candle prev  = data.get(data.size() - 2);
        double pivot = (prev.high + prev.low + prev.close) / 3.0;
        double close = data.get(data.size() - 1).close;
        if (close > pivot) return 75;
        if (close < pivot) return 25;
        return 50;
    }

    private ChartDrawing.HorizontalLine hline(double price, String label, DrawingStyle style) {
        return new ChartDrawing.HorizontalLine(price, label, style, Source.INDICATOR);
    }
}