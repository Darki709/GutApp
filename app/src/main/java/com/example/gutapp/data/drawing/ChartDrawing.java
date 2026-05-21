package com.example.gutapp.data.drawing;

import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Paint;

import com.example.gutapp.data.models.Candle;

import java.util.List;
import java.util.UUID;

/**
 * ChartDrawing — base class for every overlay drawn on the chart canvas.
 *
 * ── Timeframe-safe coordinates ──────────────────────────────────────
 * Anchors are stored as TIMESTAMPS (seconds), not candle indices.
 * At render time, DrawingRenderer calls resolveIndex(timestamp, candles)
 * to find the nearest candle index for the current timeframe.
 * This makes every drawing visible and correctly positioned on all timeframes.
 *
 * The `*Index` fields are TRANSIENT render-time caches — never persisted.
 * DrawingPersistence saves only timestamps and prices.
 */
public abstract class ChartDrawing {

    // ── DrawingStyle ─────────────────────────────────────────────────
    public static class DrawingStyle {
        public int     color;
        public float   strokeWidth;
        public boolean dashed;
        public float   dashOn;
        public float   dashOff;
        public boolean filled;
        public int     fillColor;
        public float   opacity = 1f;

        public DrawingStyle() { this(Color.parseColor("#ECEFF1"), 1.5f, false); }

        public DrawingStyle(int color, float strokeWidth, boolean dashed) {
            this.color = color; this.strokeWidth = strokeWidth; this.dashed = dashed;
            this.dashOn = 8f; this.dashOff = 4f; this.filled = false;
            this.fillColor = Color.argb(40, Color.red(color), Color.green(color), Color.blue(color));
        }

        public static DrawingStyle solid(int color)               { return new DrawingStyle(color, 1.5f, false); }
        public static DrawingStyle solid(int color, float width)  { return new DrawingStyle(color, width, false); }
        public static DrawingStyle dashed(int color)              { return new DrawingStyle(color, 1f, true); }
        public DrawingStyle withFill(boolean f) { this.filled = f; return this; }

        public void applyTo(Paint paint) {
            paint.setColor(color); paint.setStrokeWidth(strokeWidth);
            paint.setAlpha(Math.max(0, Math.min(255, Math.round(255 * opacity))));
            paint.setPathEffect(dashed ? new DashPathEffect(new float[]{dashOn, dashOff}, 0) : null);
        }

        public DrawingStyle copy() {
            DrawingStyle s = new DrawingStyle(color, strokeWidth, dashed);
            s.dashOn = dashOn; s.dashOff = dashOff;
            s.filled = filled; s.fillColor = fillColor; s.opacity = opacity;
            return s;
        }
    }

    // ── Identity ─────────────────────────────────────────────────────
    private final String instanceId;
    public enum Source { USER, INDICATOR }
    public final Source source;
    public DrawingStyle style;
    public boolean locked;
    public boolean selected;

    protected ChartDrawing(Source source, DrawingStyle style) {
        this.instanceId = UUID.randomUUID().toString();
        this.source = source; this.style = style;
        this.locked = (source == Source.INDICATOR);
    }
    public String getInstanceId() { return instanceId; }
    public abstract DrawingType getType();

    // ── Timestamp → index resolution ─────────────────────────────────
    /**
     * Given a timestamp (seconds), find the index of the nearest candle.
     * Returns -500..candles.size()+500 so off-chart drawings still render correctly.
     */
    public static int resolveIndex(long timestamp, List<Candle> candles) {
        if (candles == null || candles.isEmpty()) return 0;
        if (timestamp <= 0) return 0;
        int best = 0;
        long bestDiff = Long.MAX_VALUE;
        for (int i = 0; i < candles.size(); i++) {
            long diff = Math.abs(candles.get(i).timestamp - timestamp);
            if (diff < bestDiff) { bestDiff = diff; best = i; }
            if (diff == 0) break;
        }
        // If the timestamp is before/after all candles, extrapolate index proportionally
        if (candles.size() >= 2) {
            long t0 = candles.get(0).timestamp;
            long t1 = candles.get(candles.size() - 1).timestamp;
            if (timestamp < t0 || timestamp > t1) {
                long intervalMs = (t1 - t0) / Math.max(1, candles.size() - 1);
                if (intervalMs > 0) {
                    int extrapolated = (int) ((timestamp - t0) / intervalMs);
                    // Clamp to generous bounds so the line appears off-screen but draws correctly
                    return Math.max(-500, Math.min(candles.size() + 500, extrapolated));
                }
            }
        }
        return best;
    }

    // ── DrawingType enum ─────────────────────────────────────────────
    public enum DrawingType {
        HORIZONTAL_LINE, TREND_LINE, RAY_LINE, EXTENDED_LINE, VERTICAL_LINE,
        LINEAR_REGRESSION, FIB_RETRACEMENT,
        PRICE_RANGE, RECTANGLE, ELLIPSE,
        TEXT_ANNOTATION, ARROW,
        PARALLEL_CHANNEL, PITCHFORK, GANN_FAN
    }

    // ── Layer ordering ───────────────────────────────────────────────
    public enum Layer {
        BEHIND_CANDLES,   // default — renders before MPAndroidChart data
        ABOVE_CANDLES     // renders after MPAndroidChart data (foreground)
    }
    public Layer layer = Layer.BEHIND_CANDLES;

    // ════════════════════════════════════════════════════════════════
    // Concrete subclasses — anchors stored as TIMESTAMPS
    // ════════════════════════════════════════════════════════════════

    public static class HorizontalLine extends ChartDrawing {
        public double price; public String label;
        public HorizontalLine(double price, DrawingStyle style, Source source) {
            super(source, style); this.price = price; this.label = ""; }
        public HorizontalLine(double price, String label, DrawingStyle style, Source source) {
            super(source, style); this.price = price; this.label = label; }
        @Override public DrawingType getType() { return DrawingType.HORIZONTAL_LINE; }
    }

    public static class TrendLine extends ChartDrawing {
        public long startTs; public double startPrice;
        public long endTs;   public double endPrice;
        public boolean extendLeft = false, extendRight = false;
        public TrendLine(long sTs, double sp, long eTs, double ep, DrawingStyle style, Source source) {
            super(source, style);
            this.startTs = sTs; this.startPrice = sp; this.endTs = eTs; this.endPrice = ep; }
        @Override public DrawingType getType() { return DrawingType.TREND_LINE; }
    }

    public static class RayLine extends ChartDrawing {
        public long startTs; public double startPrice;
        public long anchorTs; public double anchorPrice;
        public RayLine(long sTs, double sp, long aTs, double ap, DrawingStyle style, Source source) {
            super(source, style);
            this.startTs = sTs; this.startPrice = sp; this.anchorTs = aTs; this.anchorPrice = ap; }
        @Override public DrawingType getType() { return DrawingType.RAY_LINE; }
    }

    public static class ExtendedLine extends ChartDrawing {
        public long startTs; public double startPrice;
        public long endTs;   public double endPrice;
        public ExtendedLine(long sTs, double sp, long eTs, double ep, DrawingStyle style, Source source) {
            super(source, style);
            this.startTs = sTs; this.startPrice = sp; this.endTs = eTs; this.endPrice = ep; }
        @Override public DrawingType getType() { return DrawingType.EXTENDED_LINE; }
    }

    public static class VerticalLine extends ChartDrawing {
        public long candleTs; public String label;
        public VerticalLine(long ts, DrawingStyle style, Source source) {
            super(source, style); this.candleTs = ts; this.label = ""; }
        public VerticalLine(long ts, String label, DrawingStyle style, Source source) {
            super(source, style); this.candleTs = ts; this.label = label; }
        @Override public DrawingType getType() { return DrawingType.VERTICAL_LINE; }
    }

    public static class LinearRegression extends ChartDrawing {
        public long startTs; public long endTs; public boolean drawChannel; public int channelDeviation = 1;
        public LinearRegression(long sTs, long eTs, DrawingStyle style, Source source) {
            super(source, style); this.startTs = sTs; this.endTs = eTs; this.drawChannel = false; }
        @Override public DrawingType getType() { return DrawingType.LINEAR_REGRESSION; }
    }

    public static class FibRetracement extends ChartDrawing {
        public long startTs; public double highPrice;
        public long endTs;   public double lowPrice;
        public float[] levels;
        public FibRetracement(long sTs, double hp, long eTs, double lp, DrawingStyle style, Source source) {
            super(source, style);
            this.startTs = sTs; this.highPrice = hp; this.endTs = eTs; this.lowPrice = lp;
            this.levels = new float[]{0f, 0.236f, 0.382f, 0.5f, 0.618f, 0.786f, 1f}; }
        @Override public DrawingType getType() { return DrawingType.FIB_RETRACEMENT; }
    }

    public static class PriceRange extends ChartDrawing {
        public double priceHigh; public double priceLow;
        public PriceRange(double hi, double lo, DrawingStyle style, Source source) {
            super(source, style); this.priceHigh = hi; this.priceLow = lo; this.style.filled = true; }
        @Override public DrawingType getType() { return DrawingType.PRICE_RANGE; }
    }

    public static class Rectangle extends ChartDrawing {
        public long startTs; public double startPrice;
        public long endTs;   public double endPrice;
        public Rectangle(long sTs, double sp, long eTs, double ep, DrawingStyle style, Source source) {
            super(source, style);
            this.startTs = sTs; this.startPrice = sp; this.endTs = eTs; this.endPrice = ep; }
        @Override public DrawingType getType() { return DrawingType.RECTANGLE; }
    }

    public static class Ellipse extends ChartDrawing {
        public long startTs; public double startPrice;
        public long endTs;   public double endPrice;
        public Ellipse(long sTs, double sp, long eTs, double ep, DrawingStyle style, Source source) {
            super(source, style);
            this.startTs = sTs; this.startPrice = sp; this.endTs = eTs; this.endPrice = ep; }
        @Override public DrawingType getType() { return DrawingType.ELLIPSE; }
    }

    public static class TextAnnotation extends ChartDrawing {
        public long candleTs; public double price; public String text; public float textSizeSp = 12f;
        public TextAnnotation(long ts, double price, String text, DrawingStyle style, Source source) {
            super(source, style); this.candleTs = ts; this.price = price; this.text = text; layer = Layer.ABOVE_CANDLES;}
        @Override public DrawingType getType() { return DrawingType.TEXT_ANNOTATION; }
    }

    public static class Arrow extends ChartDrawing {
        public long startTs; public double startPrice;
        public long endTs;   public double endPrice;
        public Arrow(long sTs, double sp, long eTs, double ep, DrawingStyle style, Source source) {
            super(source, style);
            this.startTs = sTs; this.startPrice = sp; this.endTs = eTs; this.endPrice = ep; }
        @Override public DrawingType getType() { return DrawingType.ARROW; }
    }

    public static class ParallelChannel extends ChartDrawing {
        public long startTs; public double startPrice;
        public long endTs;   public double endPrice; public double midPrice;
        public ParallelChannel(long sTs, double sp, long eTs, double ep, double mid,
                               DrawingStyle style, Source source) {
            super(source, style);
            this.startTs = sTs; this.startPrice = sp; this.endTs = eTs; this.endPrice = ep; this.midPrice = mid; }
        @Override public DrawingType getType() { return DrawingType.PARALLEL_CHANNEL; }
    }

    public static class Pitchfork extends ChartDrawing {
        public long p0Ts; public double p0Price;
        public long p1Ts; public double p1Price;
        public long p2Ts; public double p2Price;
        public Pitchfork(long p0t, double p0p, long p1t, double p1p,
                         long p2t, double p2p, DrawingStyle style, Source source) {
            super(source, style);
            this.p0Ts = p0t; this.p0Price = p0p;
            this.p1Ts = p1t; this.p1Price = p1p;
            this.p2Ts = p2t; this.p2Price = p2p; }
        @Override public DrawingType getType() { return DrawingType.PITCHFORK; }
    }

    public static class GannFan extends ChartDrawing {
        public long startTs; public double startPrice;
        public long endTs;   public double endPrice;
        public GannFan(long sTs, double sp, long eTs, double ep, DrawingStyle style, Source source) {
            super(source, style);
            this.startTs = sTs; this.startPrice = sp; this.endTs = eTs; this.endPrice = ep; }
        @Override public DrawingType getType() { return DrawingType.GANN_FAN; }
    }
}