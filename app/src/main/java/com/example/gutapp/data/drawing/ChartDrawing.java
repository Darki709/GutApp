package com.example.gutapp.data.drawing;

import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Paint;

import java.util.UUID;

/**
 * ChartDrawing — base class for everything that can be drawn on the chart canvas.
 *
 * Design goals:
 *  - Every drawing is a value object: immutable coords + mutable style.
 *  - Drawings can come from two sources:
 *      1. User interaction (tap, drag on the chart) → managed by DrawingChart
 *      2. Indicator.Result.drawings list → added automatically by StockChart
 *         when an indicator computes its output.
 *  - All drawings have a unique instanceId so they can be removed individually.
 *  - Style (color, strokeWidth, dashed, filled) is bundled in DrawingStyle.
 *
 * Subclass hierarchy:
 *   ChartDrawing
 *     ├── HorizontalLine     — fixed price level (resistance / support / custom)
 *     ├── TrendLine          — two price+index anchor points
 *     ├── RayLine            — like TrendLine but extends right to infinity
 *     ├── LinearRegression   — computed best-fit line over a candle range
 *     ├── FibRetracement     — fib levels between two price points
 *     ├── PriceRange         — shaded rectangle between two price levels
 *     └── VerticalLine       — marks a candle index (time event)
 */
public abstract class ChartDrawing {

    // ── Style descriptor ─────────────────────────────────────────────
    public static class DrawingStyle {
        public int   color;
        public float strokeWidth;
        public boolean dashed;
        public float  dashOn;
        public float  dashOff;
        public boolean filled;        // for area-type drawings
        public int   fillColor;

        /** Default: thin solid white */
        public DrawingStyle() {
            this(Color.parseColor("#ECEFF1"), 1.5f, false);
        }

        public DrawingStyle(int color, float strokeWidth, boolean dashed) {
            this.color       = color;
            this.strokeWidth = strokeWidth;
            this.dashed      = dashed;
            this.dashOn      = 8f;
            this.dashOff     = 4f;
            this.filled      = false;
            this.fillColor   = Color.argb(40, Color.red(color),
                    Color.green(color), Color.blue(color));
        }

        /** Solid colored line */
        public static DrawingStyle solid(int color) {
            return new DrawingStyle(color, 1.5f, false);
        }
        /** Solid with custom width */
        public static DrawingStyle solid(int color, float width) {
            return new DrawingStyle(color, width, false);
        }
        /** Dashed line */
        public static DrawingStyle dashed(int color) {
            return new DrawingStyle(color, 1f, true);
        }

        public DrawingStyle withFill(boolean filled) {
            this.filled = filled;
            return this;
        }

        /** Apply to a Paint object */
        public void applyTo(Paint paint) {
            paint.setColor(color);
            paint.setStrokeWidth(strokeWidth);
            if (dashed) {
                paint.setPathEffect(new DashPathEffect(new float[]{dashOn, dashOff}, 0));
            } else {
                paint.setPathEffect(null);
            }
        }
    }

    // ── Instance identity ────────────────────────────────────────────
    private final String instanceId;
    /** Source: USER = manually drawn, INDICATOR = produced by an indicator */
    public enum Source { USER, INDICATOR }
    public final Source source;

    public DrawingStyle style;
    /** If true, this drawing cannot be moved or deleted by user touch */
    public boolean locked;

    protected ChartDrawing(Source source, DrawingStyle style) {
        this.instanceId = UUID.randomUUID().toString();
        this.source     = source;
        this.style      = style;
        this.locked     = (source == Source.INDICATOR);
    }

    public String getInstanceId() { return instanceId; }

    // ── Type tag for the renderer ────────────────────────────────────
    public abstract DrawingType getType();

    public enum DrawingType {
        HORIZONTAL_LINE,
        TREND_LINE,
        RAY_LINE,
        VERTICAL_LINE,
        LINEAR_REGRESSION,
        FIB_RETRACEMENT,
        PRICE_RANGE
    }

    // ════════════════════════════════════════════════════════════════
    // Concrete subclasses
    // ════════════════════════════════════════════════════════════════

    // ── Horizontal price level ────────────────────────────────────────
    /**
     * A horizontal line at a fixed price.
     * Typical uses: support/resistance levels, indicator signal levels (e.g. RSI 70/30).
     */
    public static class HorizontalLine extends ChartDrawing {
        /** Y value in price space */
        public double price;
        /** Optional label shown on the Y axis */
        public String label;

        public HorizontalLine(double price, DrawingStyle style, Source source) {
            super(source, style);
            this.price = price;
            this.label = "";
        }

        public HorizontalLine(double price, String label, DrawingStyle style, Source source) {
            super(source, style);
            this.price = price;
            this.label = label;
        }

        @Override public DrawingType getType() { return DrawingType.HORIZONTAL_LINE; }
    }

    // ── Trend line (segment between two candle/price anchors) ─────────
    /**
     * A straight line segment connecting two (index, price) anchor points.
     * startIndex/endIndex are the candle array indices (X axis).
     */
    public static class TrendLine extends ChartDrawing {
        public int    startIndex;
        public double startPrice;
        public int    endIndex;
        public double endPrice;

        public TrendLine(int startIndex, double startPrice,
                         int endIndex,   double endPrice,
                         DrawingStyle style, Source source) {
            super(source, style);
            this.startIndex = startIndex; this.startPrice = startPrice;
            this.endIndex   = endIndex;   this.endPrice   = endPrice;
        }

        @Override public DrawingType getType() { return DrawingType.TREND_LINE; }
    }

    // ── Ray line (trend line that extends infinitely right) ────────────
    public static class RayLine extends ChartDrawing {
        public int    startIndex;
        public double startPrice;
        public int    anchorIndex;   // second anchor to define slope
        public double anchorPrice;

        public RayLine(int startIndex, double startPrice,
                       int anchorIndex, double anchorPrice,
                       DrawingStyle style, Source source) {
            super(source, style);
            this.startIndex  = startIndex;  this.startPrice  = startPrice;
            this.anchorIndex = anchorIndex; this.anchorPrice = anchorPrice;
        }

        @Override public DrawingType getType() { return DrawingType.RAY_LINE; }
    }

    // ── Vertical line at a candle index ───────────────────────────────
    public static class VerticalLine extends ChartDrawing {
        public int candleIndex;
        public String label;

        public VerticalLine(int candleIndex, DrawingStyle style, Source source) {
            super(source, style);
            this.candleIndex = candleIndex;
            this.label = "";
        }

        @Override public DrawingType getType() { return DrawingType.VERTICAL_LINE; }
    }

    // ── Linear regression line ────────────────────────────────────────
    /**
     * Least-squares linear regression computed over candles[startIndex..endIndex].
     * The DrawingChart renderer computes the fit lazily and caches it.
     */
    public static class LinearRegression extends ChartDrawing {
        public int startIndex;
        public int endIndex;
        /** Whether to also draw ±1 stddev channel */
        public boolean drawChannel;

        public LinearRegression(int startIndex, int endIndex,
                                DrawingStyle style, Source source) {
            super(source, style);
            this.startIndex   = startIndex;
            this.endIndex     = endIndex;
            this.drawChannel  = false;
        }

        @Override public DrawingType getType() { return DrawingType.LINEAR_REGRESSION; }
    }

    // ── Fibonacci retracement ─────────────────────────────────────────
    /**
     * Fibonacci levels between a swing high and swing low.
     * Standard levels: 0, 0.236, 0.382, 0.5, 0.618, 0.786, 1.0
     */
    public static class FibRetracement extends ChartDrawing {
        public int    startIndex;
        public double highPrice;
        public int    endIndex;
        public double lowPrice;
        public float[] levels;   // e.g. {0f, 0.236f, 0.382f, 0.5f, 0.618f, 0.786f, 1f}

        public FibRetracement(int startIndex, double highPrice,
                              int endIndex,   double lowPrice,
                              DrawingStyle style, Source source) {
            super(source, style);
            this.startIndex = startIndex; this.highPrice = highPrice;
            this.endIndex   = endIndex;   this.lowPrice  = lowPrice;
            this.levels = new float[]{0f, 0.236f, 0.382f, 0.5f, 0.618f, 0.786f, 1f};
        }

        @Override public DrawingType getType() { return DrawingType.FIB_RETRACEMENT; }
    }

    // ── Price range (shaded rectangle) ───────────────────────────────
    /**
     * A shaded price band between two levels, spanning the full X axis.
     * Useful for indicating supply/demand zones, value areas, etc.
     */
    public static class PriceRange extends ChartDrawing {
        public double priceHigh;
        public double priceLow;

        public PriceRange(double priceHigh, double priceLow,
                          DrawingStyle style, Source source) {
            super(source, style);
            this.priceHigh = priceHigh;
            this.priceLow  = priceLow;
            this.style.filled = true;
        }

        @Override public DrawingType getType() { return DrawingType.PRICE_RANGE; }
    }
}