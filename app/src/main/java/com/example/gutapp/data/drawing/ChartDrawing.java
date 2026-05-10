package com.example.gutapp.data.drawing;

import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Paint;

import java.util.UUID;

/**
 * ChartDrawing — base class for every overlay on the chart canvas.
 *
 * Drawing types:
 *   LINES     : HorizontalLine, TrendLine, RayLine, ExtendedLine, VerticalLine
 *   REGRESSION: LinearRegression
 *   FIBONACCI : FibRetracement
 *   SHAPE     : PriceRange, Rectangle, Ellipse
 *   ANNOTATION: TextAnnotation, Arrow
 *   CHANNEL   : ParallelChannel
 *   ADVANCED  : Pitchfork (Andrews), GannFan
 */
public abstract class ChartDrawing {

    // ── Style descriptor ─────────────────────────────────────────────
    public static class DrawingStyle {
        public int     color;
        public float   strokeWidth;
        public boolean dashed;
        public float   dashOn;
        public float   dashOff;
        public boolean filled;
        public int     fillColor;
        public float   opacity = 1f;

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
            this.fillColor   = Color.argb(40,
                    Color.red(color), Color.green(color), Color.blue(color));
        }

        public static DrawingStyle solid(int color) { return new DrawingStyle(color, 1.5f, false); }
        public static DrawingStyle solid(int color, float width) { return new DrawingStyle(color, width, false); }
        public static DrawingStyle dashed(int color) { return new DrawingStyle(color, 1f, true); }

        public DrawingStyle withFill(boolean filled) { this.filled = filled; return this; }

        public void applyTo(Paint paint) {
            paint.setColor(color);
            paint.setStrokeWidth(strokeWidth);
            int a = Math.round(255 * opacity);
            paint.setAlpha(Math.max(0, Math.min(255, a)));
            if (dashed) {
                paint.setPathEffect(new DashPathEffect(new float[]{dashOn, dashOff}, 0));
            } else {
                paint.setPathEffect(null);
            }
        }

        public DrawingStyle copy() {
            DrawingStyle s = new DrawingStyle(color, strokeWidth, dashed);
            s.dashOn = dashOn; s.dashOff = dashOff;
            s.filled = filled; s.fillColor = fillColor; s.opacity = opacity;
            return s;
        }
    }

    // ── Identity ────────────────────────────────────────────────────
    private final String instanceId;
    public enum Source { USER, INDICATOR }
    public final Source source;
    public DrawingStyle style;
    public boolean locked;
    public boolean selected;

    protected ChartDrawing(Source source, DrawingStyle style) {
        this.instanceId = UUID.randomUUID().toString();
        this.source     = source;
        this.style      = style;
        this.locked     = (source == Source.INDICATOR);
        this.selected   = false;
    }

    public String getInstanceId() { return instanceId; }
    public abstract DrawingType getType();

    public enum DrawingType {
        HORIZONTAL_LINE, TREND_LINE, RAY_LINE, EXTENDED_LINE, VERTICAL_LINE,
        LINEAR_REGRESSION, FIB_RETRACEMENT,
        PRICE_RANGE, RECTANGLE, ELLIPSE,
        TEXT_ANNOTATION, ARROW,
        PARALLEL_CHANNEL, PITCHFORK, GANN_FAN
    }

    // ════════════════════════════════════════════════════════════════
    // Concrete subclasses
    // ════════════════════════════════════════════════════════════════

    public static class HorizontalLine extends ChartDrawing {
        public double price;
        public String label;
        public HorizontalLine(double price, DrawingStyle style, Source source) {
            super(source, style); this.price = price; this.label = "";
        }
        public HorizontalLine(double price, String label, DrawingStyle style, Source source) {
            super(source, style); this.price = price; this.label = label;
        }
        @Override public DrawingType getType() { return DrawingType.HORIZONTAL_LINE; }
    }

    public static class TrendLine extends ChartDrawing {
        public int startIndex; public double startPrice;
        public int endIndex;   public double endPrice;
        public boolean extendLeft  = false;
        public boolean extendRight = false;
        public TrendLine(int si, double sp, int ei, double ep, DrawingStyle style, Source source) {
            super(source, style);
            this.startIndex = si; this.startPrice = sp;
            this.endIndex   = ei; this.endPrice   = ep;
        }
        @Override public DrawingType getType() { return DrawingType.TREND_LINE; }
    }

    public static class RayLine extends ChartDrawing {
        public int startIndex; public double startPrice;
        public int anchorIndex; public double anchorPrice;
        public RayLine(int si, double sp, int ai, double ap, DrawingStyle style, Source source) {
            super(source, style);
            this.startIndex = si; this.startPrice = sp;
            this.anchorIndex = ai; this.anchorPrice = ap;
        }
        @Override public DrawingType getType() { return DrawingType.RAY_LINE; }
    }

    public static class ExtendedLine extends ChartDrawing {
        public int startIndex; public double startPrice;
        public int endIndex;   public double endPrice;
        public ExtendedLine(int si, double sp, int ei, double ep, DrawingStyle style, Source source) {
            super(source, style);
            this.startIndex = si; this.startPrice = sp;
            this.endIndex   = ei; this.endPrice   = ep;
        }
        @Override public DrawingType getType() { return DrawingType.EXTENDED_LINE; }
    }

    public static class VerticalLine extends ChartDrawing {
        public int candleIndex;
        public String label;
        public VerticalLine(int idx, DrawingStyle style, Source source) {
            super(source, style); this.candleIndex = idx; this.label = "";
        }
        public VerticalLine(int idx, String label, DrawingStyle style, Source source) {
            super(source, style); this.candleIndex = idx; this.label = label;
        }
        @Override public DrawingType getType() { return DrawingType.VERTICAL_LINE; }
    }

    public static class LinearRegression extends ChartDrawing {
        public int startIndex; public int endIndex;
        public boolean drawChannel;
        public LinearRegression(int si, int ei, DrawingStyle style, Source source) {
            super(source, style); this.startIndex = si; this.endIndex = ei; this.drawChannel = false;
        }
        @Override public DrawingType getType() { return DrawingType.LINEAR_REGRESSION; }
    }

    public static class FibRetracement extends ChartDrawing {
        public int startIndex; public double highPrice;
        public int endIndex;   public double lowPrice;
        public float[] levels;
        public FibRetracement(int si, double hp, int ei, double lp, DrawingStyle style, Source source) {
            super(source, style);
            this.startIndex = si; this.highPrice = hp;
            this.endIndex   = ei; this.lowPrice  = lp;
            this.levels = new float[]{0f, 0.236f, 0.382f, 0.5f, 0.618f, 0.786f, 1f};
        }
        @Override public DrawingType getType() { return DrawingType.FIB_RETRACEMENT; }
    }

    public static class PriceRange extends ChartDrawing {
        public double priceHigh; public double priceLow;
        public PriceRange(double hi, double lo, DrawingStyle style, Source source) {
            super(source, style); this.priceHigh = hi; this.priceLow = lo; this.style.filled = true;
        }
        @Override public DrawingType getType() { return DrawingType.PRICE_RANGE; }
    }

    public static class Rectangle extends ChartDrawing {
        public int startIndex; public double startPrice;
        public int endIndex;   public double endPrice;
        public Rectangle(int si, double sp, int ei, double ep, DrawingStyle style, Source source) {
            super(source, style);
            this.startIndex = si; this.startPrice = sp;
            this.endIndex   = ei; this.endPrice   = ep;
        }
        @Override public DrawingType getType() { return DrawingType.RECTANGLE; }
    }

    public static class Ellipse extends ChartDrawing {
        public int startIndex; public double startPrice;
        public int endIndex;   public double endPrice;
        public Ellipse(int si, double sp, int ei, double ep, DrawingStyle style, Source source) {
            super(source, style);
            this.startIndex = si; this.startPrice = sp;
            this.endIndex   = ei; this.endPrice   = ep;
        }
        @Override public DrawingType getType() { return DrawingType.ELLIPSE; }
    }

    public static class TextAnnotation extends ChartDrawing {
        public int candleIndex; public double price;
        public String text;
        public float textSizeSp = 12f;
        public TextAnnotation(int idx, double price, String text, DrawingStyle style, Source source) {
            super(source, style); this.candleIndex = idx; this.price = price; this.text = text;
        }
        @Override public DrawingType getType() { return DrawingType.TEXT_ANNOTATION; }
    }

    public static class Arrow extends ChartDrawing {
        public int startIndex; public double startPrice;
        public int endIndex;   public double endPrice;
        public Arrow(int si, double sp, int ei, double ep, DrawingStyle style, Source source) {
            super(source, style);
            this.startIndex = si; this.startPrice = sp;
            this.endIndex   = ei; this.endPrice   = ep;
        }
        @Override public DrawingType getType() { return DrawingType.ARROW; }
    }

    public static class ParallelChannel extends ChartDrawing {
        public int startIndex; public double startPrice;
        public int endIndex;   public double endPrice;
        public double midPrice;
        public ParallelChannel(int si, double sp, int ei, double ep, double mid,
                               DrawingStyle style, Source source) {
            super(source, style);
            this.startIndex = si; this.startPrice = sp;
            this.endIndex   = ei; this.endPrice   = ep;
            this.midPrice   = mid;
        }
        @Override public DrawingType getType() { return DrawingType.PARALLEL_CHANNEL; }
    }

    public static class Pitchfork extends ChartDrawing {
        public int p0Index; public double p0Price;
        public int p1Index; public double p1Price;
        public int p2Index; public double p2Price;
        public Pitchfork(int p0i, double p0p, int p1i, double p1p,
                         int p2i, double p2p, DrawingStyle style, Source source) {
            super(source, style);
            this.p0Index = p0i; this.p0Price = p0p;
            this.p1Index = p1i; this.p1Price = p1p;
            this.p2Index = p2i; this.p2Price = p2p;
        }
        @Override public DrawingType getType() { return DrawingType.PITCHFORK; }
    }

    public static class GannFan extends ChartDrawing {
        public int startIndex; public double startPrice;
        public int endIndex;   public double endPrice;
        public GannFan(int si, double sp, int ei, double ep, DrawingStyle style, Source source) {
            super(source, style);
            this.startIndex = si; this.startPrice = sp;
            this.endIndex   = ei; this.endPrice   = ep;
        }
        @Override public DrawingType getType() { return DrawingType.GANN_FAN; }
    }
}