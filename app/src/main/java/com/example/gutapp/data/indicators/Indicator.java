package com.example.gutapp.data.indicators;

import android.graphics.Color;

import com.example.gutapp.data.models.Candle;
import com.github.mikephil.charting.data.LineDataSet;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Base class for all chart indicators.
 *
 * KEY CHANGES vs previous version:
 *  - instanceId: each ADDED indicator gets a unique UUID, allowing multiple
 *    instances of the same type (e.g., MA(20) and MA(50) both active at once).
 *  - color: user-selectable color stored per-instance.
 *  - newInstance(): each Indicator subclass must be able to produce a fresh copy
 *    of itself (used when user adds another instance of the same type).
 *  - The registry now stores TYPES (factories), not live instances.
 *    Live instances are stored in IndicatorSession.
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
    public static class Result {
        public final List<LineDataSet> overlayLines   = new ArrayList<>();
        public final List<LineDataSet> subChartLines  = new ArrayList<>();
        public float subChartMin = Float.NaN;
        public float subChartMax = Float.NaN;
    }

    // ── Instance identity ─────────────────────────────────────────────
    /** Unique per-instance ID (UUID). Two MA(20) indicators have different instanceIds. */
    private String instanceId = UUID.randomUUID().toString();
    /** User-chosen display color for the main line of this indicator */
    private int color = Color.parseColor("#FFC107"); // default amber

    public String getInstanceId() { return instanceId; }
    public void   setInstanceId(String id) { this.instanceId = id; }
    public int    getColor()      { return color; }
    public void   setColor(int c) { this.color = c; }

    // ── Type identity (class-level, not instance-level) ───────────────
    public abstract String getId();          // type id: "ma", "ema", "rsi"
    public abstract String getDisplayName(); // "Moving Average"
    public abstract String getTag();         // "MA"
    public abstract boolean isSubChart();

    /**
     * Produce a fresh instance of this indicator type with default params.
     * Used by IndicatorSession.addInstance(typeId).
     */
    public abstract Indicator newInstance();

    // ── Parameters ────────────────────────────────────────────────────
    protected final List<Param> params = new ArrayList<>();
    public List<Param> getParams() { return params; }

    public float getParam(String key) {
        for (Param p : params) if (p.key.equals(key)) return p.value;
        throw new IllegalArgumentException("Unknown param: " + key);
    }
    public void setParam(String key, float value) {
        for (Param p : params) { if (p.key.equals(key)) { p.value = value; return; } }
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

    // ── Helpers ───────────────────────────────────────────────────────
    protected LineDataSet makeLineSet(List<com.github.mikephil.charting.data.Entry> entries,
                                      String label, int c, float width) {
        LineDataSet set = new LineDataSet(entries, label);
        set.setColor(c); set.setLineWidth(width);
        set.setDrawCircles(false); set.setDrawValues(false);
        set.setAxisDependency(com.github.mikephil.charting.components.YAxis.AxisDependency.LEFT);
        return set;
    }
    protected LineDataSet makeDashedLineSet(List<com.github.mikephil.charting.data.Entry> entries,
                                            String label, int c) {
        LineDataSet set = makeLineSet(entries, label, c, 1f);
        set.enableDashedLine(6f, 3f, 0f);
        return set;
    }

    // ── Serialization snapshot ────────────────────────────────────────
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
            this.color = color;   this.params = params;
        }
    }

    /**
     * Returns a score from 0 to 100.
     * 0-30:   Strong Bearish
     * 30-45:  Bearish
     * 45-55:  Neutral
     * 55-70:  Bullish
     * 70-100: Strong Bullish
     */
    public abstract int calculateBias(ArrayList<Candle> candles);
}