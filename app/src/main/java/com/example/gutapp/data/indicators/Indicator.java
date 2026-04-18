package com.example.gutapp.data.indicators;

import com.example.gutapp.data.models.Candle;
import com.github.mikephil.charting.data.LineDataSet;

import java.util.ArrayList;
import java.util.List;

/**
 * Base class for all chart indicators.
 *
 * Each indicator knows:
 *  - its unique ID (used to identify it across settings saves/restores)
 *  - its display name and short tag
 *  - whether it renders ON the price chart (overlay) or BELOW it (sub-chart)
 *  - how to compute its LineDataSet(s) from a candle list
 *
 * To add a new indicator:
 *   1. Create a new class that extends Indicator
 *   2. Override compute() — return one or more LineDataSets
 *   3. Set isSubChart=true if it needs its own pane (RSI, MACD, etc.)
 *   4. Register it in IndicatorRegistry
 *
 * The settings/parameter system is generic: each indicator declares
 * a list of Param objects (period, multiplier, etc.). The IndicatorsPanel
 * reads these and builds the UI rows automatically — no code needed in the panel.
 */
public abstract class Indicator {

    // ── Parameter descriptor ──────────────────────────────────────────
    public static class Param {
        public enum Type { INTEGER, FLOAT }

        public final String key;        // unique within this indicator
        public final String label;      // shown in the UI
        public final Type   type;
        public final float  min;
        public final float  max;
        public       float  value;

        public Param(String key, String label, Type type, float min, float max, float defaultValue) {
            this.key   = key;
            this.label = label;
            this.type  = type;
            this.min   = min;
            this.max   = max;
            this.value = defaultValue;
        }

        public int intValue()   { return Math.round(value); }
        public float floatValue() { return value; }

        public Param copy() {
            return new Param(key, label, type, min, max, value);
        }
    }

    // ── Result container (one indicator can output multiple lines) ────
    public static class Result {
        public final List<LineDataSet> overlayLines = new ArrayList<>();
        public final List<LineDataSet> subChartLines = new ArrayList<>();
        // Sub-chart Y-axis range hints (NaN = auto)
        public float subChartMin = Float.NaN;
        public float subChartMax = Float.NaN;
    }

    // ── Identity ──────────────────────────────────────────────────────
    public abstract String getId();        // e.g. "ma", "ema", "rsi"
    public abstract String getDisplayName(); // e.g. "Moving Average"
    public abstract String getTag();         // e.g. "MA"
    public abstract boolean isSubChart();    // true = needs own pane below

    // ── Parameters ────────────────────────────────────────────────────
    protected final List<Param> params = new ArrayList<>();

    public List<Param> getParams() { return params; }

    public float getParam(String key) {
        for (Param p : params) if (p.key.equals(key)) return p.value;
        throw new IllegalArgumentException("Unknown param: " + key);
    }

    public void setParam(String key, float value) {
        for (Param p : params) {
            if (p.key.equals(key)) {
                p.value = value;
                return;
            }
        }
        throw new IllegalArgumentException("Unknown param: " + key);
    }

    /** Deep-copy all params for thread-safe settings storage */
    public List<Param> copyParams() {
        List<Param> copy = new ArrayList<>();
        for (Param p : params) copy.add(p.copy());
        return copy;
    }

    public void restoreParams(List<Param> saved) {
        for (Param saved_p : saved) {
            for (Param p : params) {
                if (p.key.equals(saved_p.key)) {
                    p.value = saved_p.value;
                    break;
                }
            }
        }
    }

    // ── State ─────────────────────────────────────────────────────────
    private boolean enabled = false;

    public boolean isEnabled()              { return enabled; }
    public void    setEnabled(boolean on)   { this.enabled = on; }

    // ── Computation ───────────────────────────────────────────────────
    /**
     * Compute the indicator's output from the given candles.
     * All heavy math happens here — called from a background-safe context.
     * @return Result containing overlay and/or sub-chart line datasets
     */
    public abstract Result compute(ArrayList<Candle> candles);

    // ── Helpers for subclasses ─────────────────────────────────────────
    protected LineDataSet makeLineSet(List<com.github.mikephil.charting.data.Entry> entries,
                                      String label, int color, float width) {
        LineDataSet set = new LineDataSet(entries, label);
        set.setColor(color);
        set.setLineWidth(width);
        set.setDrawCircles(false);
        set.setDrawValues(false);
        set.setAxisDependency(com.github.mikephil.charting.components.YAxis.AxisDependency.LEFT);
        return set;
    }

    protected LineDataSet makeDashedLineSet(List<com.github.mikephil.charting.data.Entry> entries,
                                            String label, int color) {
        LineDataSet set = makeLineSet(entries, label, color, 1f);
        set.enableDashedLine(6f, 3f, 0f);
        return set;
    }
}