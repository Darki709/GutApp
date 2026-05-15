package com.example.gutapp.data.indicators;

import android.graphics.Color;

import com.example.gutapp.data.drawing.ChartDrawing;
import com.example.gutapp.data.models.Candle;
import com.github.mikephil.charting.data.LineDataSet;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Indicator — base class for all chart indicators.
 *
 * Key additions in this version:
 *  - Result.drawings: indicators can now produce ChartDrawing objects in addition to
 *    line datasets. These appear as locked overlays on the DrawingChart canvas.
 *    Example: a support/resistance indicator can emit HorizontalLine drawings.
 *    Example: a pivot point indicator can emit multiple HorizontalLine drawings.
 */
public abstract class Indicator {

    // ── Parameter descriptor ──────────────────────────────────────────
    public static class Param {
        public enum Type { INTEGER, FLOAT }
        public final String key;
        public final String label;
        public final Type   type;
        public final float  min;
        public final float  max;
        public       float  value;

        public Param(String key, String label, Type type, float min, float max, float defaultValue) {
            this.key   = key;  this.label = label;
            this.type  = type; this.min   = min;
            this.max   = max;  this.value = defaultValue;
        }
        public int   intValue()   { return Math.round(value); }
        public float floatValue() { return value; }
        public Param copy() { return new Param(key, label, type, min, max, value); }
    }

    // ── Result container ──────────────────────────────────────────────
    /**
     * Everything an indicator can produce in one compute() call:
     *
     *  overlayLines   → LineDataSet objects drawn ON the main price chart
     *  subChartLines  → LineDataSet objects drawn in a separate pane below
     *  drawings       → ChartDrawing objects (locked, INDICATOR source) drawn
     *                   on the DrawingChart canvas — supports all drawing types:
     *                   horizontal lines, trend lines, regression channels, etc.
     *
     * Indicators should only populate the lists they need. An indicator can
     * use ANY combination (e.g. VWAP = one overlayLine; a Pivot Point indicator
     * might produce zero lines but several HorizontalLine drawings).
     */
    public static class Result {
        public final List<LineDataSet>    overlayLines  = new ArrayList<>();
        public final List<LineDataSet>    subChartLines = new ArrayList<>();
        public final List<ChartDrawing>   drawings      = new ArrayList<>();

        // Sub-chart Y-axis range hints (Float.NaN = auto)
        public float subChartMin = Float.NaN;
        public float subChartMax = Float.NaN;

        // ── Helper factories for common drawing types ─────────────────

        /** Add a locked horizontal line at a fixed price (e.g. RSI 70/30 level) */
        public void addHorizontalLine(double price, String label,
                                      ChartDrawing.DrawingStyle style) {
            drawings.add(new ChartDrawing.HorizontalLine(
                    price, label, style, ChartDrawing.Source.INDICATOR));
        }

        /** Add a locked trend line */
        public void addTrendLine(int startIdx, double startPrice,
                                 int endIdx, double endPrice,
                                 ChartDrawing.DrawingStyle style) {
            drawings.add(new ChartDrawing.TrendLine(
                    startIdx, startPrice, endIdx, endPrice,
                    style, ChartDrawing.Source.INDICATOR));
        }

        /** Add a locked linear regression channel */
        public void addRegressionChannel(int startIdx, int endIdx,
                                         ChartDrawing.DrawingStyle style) {
            ChartDrawing.LinearRegression r = new ChartDrawing.LinearRegression(
                    startIdx, endIdx, style, ChartDrawing.Source.INDICATOR);
            r.drawChannel = true;
            drawings.add(r);
        }

        /** Add a locked price range / zone */
        public void addPriceRange(double high, double low,
                                  ChartDrawing.DrawingStyle style) {
            ChartDrawing.DrawingStyle s = new ChartDrawing.DrawingStyle(
                    style.color, style.strokeWidth, style.dashed);
            s.filled = true;
            drawings.add(new ChartDrawing.PriceRange(
                    high, low, s, ChartDrawing.Source.INDICATOR));
        }
    }

    // ── Instance identity ─────────────────────────────────────────────
    private String instanceId = UUID.randomUUID().toString();
    private int    color      = Color.parseColor("#FFC107");

    public String getInstanceId()       { return instanceId; }
    public void   setInstanceId(String id) { this.instanceId = id; }
    public int    getColor()            { return color; }
    public void   setColor(int c)       { this.color = c; }

    // ── Type identity ─────────────────────────────────────────────────
    public abstract String  getId();
    public abstract String  getDisplayName();
    public abstract String  getTag();
    public abstract boolean isSubChart();
    public abstract Indicator newInstance();

    // ── Parameters ────────────────────────────────────────────────────
    protected final List<Param> params = new ArrayList<>();
    public List<Param> getParams() { return params; }

    public float getParam(String key) {
        for (Param p : params) if (p.key.equals(key)) return p.value;
        throw new IllegalArgumentException("Unknown param: " + key);
    }
    public void setParam(String key, float value) {
        for (Param p : params) {
            if (p.key.equals(key)) { p.value = value; return; }
        }
        throw new IllegalArgumentException("Unknown param: " + key);
    }
    public List<Param> copyParams() {
        List<Param> copy = new ArrayList<>();
        for (Param p : params) copy.add(p.copy());
        return copy;
    }
    public void restoreParams(List<Param> saved) {
        for (Param s : saved) {
            for (Param p : params) { if (p.key.equals(s.key)) { p.value = s.value; break; } }
        }
    }

    // ── Computation ───────────────────────────────────────────────────
    public abstract Result compute(ArrayList<Candle> candles);

    // ── Line helpers ──────────────────────────────────────────────────
    protected LineDataSet makeLineSet(List<com.github.mikephil.charting.data.Entry> entries,
                                      String label, int c, float width) {
        LineDataSet set = new LineDataSet(entries, label);
        set.setColor(c); set.setLineWidth(width);
        set.setDrawCircles(false); set.setDrawValues(false);
        set.setAxisDependency(com.github.mikephil.charting.components.YAxis.AxisDependency.RIGHT);
        return set;
    }
    protected LineDataSet makeDashedLineSet(List<com.github.mikephil.charting.data.Entry> entries,
                                            String label, int c) {
        LineDataSet set = makeLineSet(entries, label, c, 1f);
        set.enableDashedLine(6f, 3f, 0f);
        return set;
    }

    // ── Serialization ─────────────────────────────────────────────────
    public IndicatorSnapshot snapshot() {
        return new IndicatorSnapshot(getId(), instanceId, color, copyParams());
    }

    public static class IndicatorSnapshot {
        public final String typeId;
        public final String instanceId;
        public final int    color;
        public final List<Param> params;
        public IndicatorSnapshot(String typeId, String instanceId, int color, List<Param> params) {
            this.typeId = typeId; this.instanceId = instanceId;
            this.color = color; this.params = params;
        }
    }

    /**
     * Calculates the bias of and indicator based on current price
     * Returns a score from 0-100 (100 is bullish 0 is bearish) so the user can infer the current direction the chart might move
    **/
    public abstract int calculateBias(ArrayList<Candle> data);
}