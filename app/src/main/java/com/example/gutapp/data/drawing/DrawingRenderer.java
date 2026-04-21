package com.example.gutapp.data.drawing;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;

import com.example.gutapp.data.models.Candle;
import com.github.mikephil.charting.charts.CombinedChart;
import com.github.mikephil.charting.utils.Transformer;
import com.github.mikephil.charting.components.YAxis;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * DrawingRenderer — pure rendering logic for ChartDrawing objects.
 *
 * Called from DrawingChart.onDraw() AFTER MPAndroidChart has drawn its own
 * content (price bars, axes, etc.) so drawings appear on top.
 *
 * Coordinate conversion:
 *   - Data space: (candleIndex [float], price [double])  ← what drawings store
 *   - Pixel space: (x [float], y [float])                ← what Canvas expects
 *   - Transformer.pointValuesToPixel() converts between them.
 *
 * The renderer is stateless — it reads from DrawingManager + the candle list
 * on every draw call. No caching (fast enough for typical trading chart sizes).
 */
public class DrawingRenderer {

    // ── Paints ────────────────────────────────────────────────────────
    private final Paint linePaint   = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fillPaint   = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint   = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path  path        = new Path();
    private final float[] pts2      = new float[2]; // reused buffer

    // FIB level colors (tinted from the drawing's base color)
    private static final float[] FIB_ALPHAS = {1f, 0.8f, 0.7f, 0.6f, 0.7f, 0.8f, 1f};

    public DrawingRenderer() {
        linePaint.setStyle(Paint.Style.STROKE);
        fillPaint.setStyle(Paint.Style.FILL);
        textPaint.setTextSize(24f);      // ~9sp — set per draw below
        textPaint.setStyle(Paint.Style.FILL);
    }

    // ── Entry point ───────────────────────────────────────────────────

    /**
     * Paint all drawings in `manager` onto `canvas`.
     *
     * @param canvas      The Canvas provided by DrawingChart.onDraw()
     * @param chart       The parent DrawingChart (for Transformer access)
     * @param manager     All active drawings
     * @param candles     Candle list matching the chart's X indices
     */
    public void draw(Canvas canvas, CombinedChart chart,
                     DrawingManager manager, List<Candle> candles) {
        if (manager.isEmpty() || candles.isEmpty()) return;

        Transformer transformer = chart.getTransformer(YAxis.AxisDependency.LEFT);
        RectF contentRect = chart.getContentRect();
        float textSizePx  = chart.getResources().getDisplayMetrics().density * 9f;
        textPaint.setTextSize(textSizePx);

        for (ChartDrawing drawing : manager.getAll()) {
            if (drawing.style == null) continue;
            switch (drawing.getType()) {
                case HORIZONTAL_LINE:
                    drawHorizontalLine(canvas, (ChartDrawing.HorizontalLine) drawing,
                            transformer, contentRect);
                    break;
                case TREND_LINE:
                    drawTrendLine(canvas, (ChartDrawing.TrendLine) drawing,
                            transformer, contentRect);
                    break;
                case RAY_LINE:
                    drawRayLine(canvas, (ChartDrawing.RayLine) drawing,
                            transformer, contentRect, candles.size());
                    break;
                case VERTICAL_LINE:
                    drawVerticalLine(canvas, (ChartDrawing.VerticalLine) drawing,
                            transformer, contentRect);
                    break;
                case LINEAR_REGRESSION:
                    drawLinearRegression(canvas, (ChartDrawing.LinearRegression) drawing,
                            transformer, contentRect, candles);
                    break;
                case FIB_RETRACEMENT:
                    drawFibRetracement(canvas, (ChartDrawing.FibRetracement) drawing,
                            transformer, contentRect);
                    break;
                case PRICE_RANGE:
                    drawPriceRange(canvas, (ChartDrawing.PriceRange) drawing,
                            transformer, contentRect);
                    break;
            }
        }
    }

    // ── Horizontal line ───────────────────────────────────────────────

    private void drawHorizontalLine(Canvas canvas, ChartDrawing.HorizontalLine d,
                                    Transformer tf, RectF rect) {
        float y = priceToPixelY(d.price, tf);
        if (y < rect.top || y > rect.bottom) return;

        applyLinePaint(d.style);
        canvas.drawLine(rect.left, y, rect.right, y, linePaint);

        if (d.label != null && !d.label.isEmpty()) {
            textPaint.setColor(d.style.color);
            canvas.drawText(d.label, rect.right - textPaint.measureText(d.label) - 4, y - 4, textPaint);
        }
    }

    // ── Trend line ────────────────────────────────────────────────────

    private void drawTrendLine(Canvas canvas, ChartDrawing.TrendLine d,
                               Transformer tf, RectF rect) {
        float x1 = indexToPixelX(d.startIndex, tf);
        float y1 = priceToPixelY(d.startPrice, tf);
        float x2 = indexToPixelX(d.endIndex,   tf);
        float y2 = priceToPixelY(d.endPrice,   tf);

        // Clip to content rect
        if (x1 > rect.right  && x2 > rect.right)  return;
        if (x1 < rect.left   && x2 < rect.left)   return;

        applyLinePaint(d.style);
        canvas.drawLine(x1, y1, x2, y2, linePaint);

        // Anchor dots
        canvas.drawCircle(x1, y1, linePaint.getStrokeWidth() * 2.5f, linePaint);
        canvas.drawCircle(x2, y2, linePaint.getStrokeWidth() * 2.5f, linePaint);
    }

    // ── Ray line ──────────────────────────────────────────────────────

    private void drawRayLine(Canvas canvas, ChartDrawing.RayLine d,
                             Transformer tf, RectF rect, int totalCandles) {
        float x1 = indexToPixelX(d.startIndex,  tf);
        float y1 = priceToPixelY(d.startPrice,  tf);
        float x2 = indexToPixelX(d.anchorIndex, tf);
        float y2 = priceToPixelY(d.anchorPrice, tf);

        // Extend to right edge (last candle)
        float dx = x2 - x1;
        float dy = y2 - y1;
        if (dx == 0) {
            // Vertical ray — draw to bottom
            applyLinePaint(d.style);
            canvas.drawLine(x1, y1, x1, rect.bottom, linePaint);
            return;
        }
        float xEnd = rect.right + 100; // past the edge to ensure it reaches
        float yEnd = y1 + (xEnd - x1) * (dy / dx);

        applyLinePaint(d.style);
        canvas.drawLine(x1, y1, xEnd, yEnd, linePaint);
        canvas.drawCircle(x1, y1, linePaint.getStrokeWidth() * 2.5f, linePaint);
    }

    // ── Vertical line ─────────────────────────────────────────────────

    private void drawVerticalLine(Canvas canvas, ChartDrawing.VerticalLine d,
                                  Transformer tf, RectF rect) {
        float x = indexToPixelX(d.candleIndex, tf);
        if (x < rect.left || x > rect.right) return;

        applyLinePaint(d.style);
        canvas.drawLine(x, rect.top, x, rect.bottom, linePaint);

        if (d.label != null && !d.label.isEmpty()) {
            textPaint.setColor(d.style.color);
            canvas.drawText(d.label, x + 4, rect.top + textPaint.getTextSize() + 4, textPaint);
        }
    }

    // ── Linear regression ─────────────────────────────────────────────

    private void drawLinearRegression(Canvas canvas, ChartDrawing.LinearRegression d,
                                      Transformer tf, RectF rect, List<Candle> candles) {
        int start = Math.max(0, d.startIndex);
        int end   = Math.min(candles.size() - 1, d.endIndex);
        if (end <= start) return;

        int n = end - start + 1;
        double sumX = 0, sumY = 0, sumXY = 0, sumXX = 0;
        for (int i = 0; i < n; i++) {
            double x = i;
            double y = candles.get(start + i).close;
            sumX  += x;
            sumY  += y;
            sumXY += x * y;
            sumXX += x * x;
        }
        double slope     = (n * sumXY - sumX * sumY) / (n * sumXX - sumX * sumX);
        double intercept = (sumY - slope * sumX) / n;

        float x1 = indexToPixelX(start, tf);
        float y1 = priceToPixelY(intercept, tf);
        float x2 = indexToPixelX(end, tf);
        float y2 = priceToPixelY(intercept + slope * (n - 1), tf);

        applyLinePaint(d.style);
        canvas.drawLine(x1, y1, x2, y2, linePaint);

        if (d.drawChannel) {
            // Compute standard deviation of residuals
            double ss = 0;
            for (int i = 0; i < n; i++) {
                double predicted = intercept + slope * i;
                double residual  = candles.get(start + i).close - predicted;
                ss += residual * residual;
            }
            double stdDev = Math.sqrt(ss / n);

            // Upper channel
            float yu1 = priceToPixelY(intercept + stdDev, tf);
            float yu2 = priceToPixelY(intercept + slope * (n-1) + stdDev, tf);
            // Lower channel
            float yl1 = priceToPixelY(intercept - stdDev, tf);
            float yl2 = priceToPixelY(intercept + slope * (n-1) - stdDev, tf);

            Paint channelPaint = new Paint(linePaint);
            channelPaint.setAlpha(120);
            channelPaint.setPathEffect(new android.graphics.DashPathEffect(new float[]{6,3},0));
            canvas.drawLine(x1, yu1, x2, yu2, channelPaint);
            canvas.drawLine(x1, yl1, x2, yl2, channelPaint);

            // Fill
            if (d.style.filled) {
                path.reset();
                path.moveTo(x1, yu1); path.lineTo(x2, yu2);
                path.lineTo(x2, yl2); path.lineTo(x1, yl1);
                path.close();
                fillPaint.setColor(d.style.fillColor);
                canvas.drawPath(path, fillPaint);
            }
        }
    }

    // ── Fibonacci retracement ─────────────────────────────────────────

    private void drawFibRetracement(Canvas canvas, ChartDrawing.FibRetracement d,
                                    Transformer tf, RectF rect) {
        if (d.levels == null || d.levels.length == 0) return;
        float x1 = indexToPixelX(d.startIndex, tf);
        float x2 = indexToPixelX(d.endIndex,   tf);
        float xLeft  = Math.min(x1, rect.left);
        float xRight = Math.max(x2, rect.right);

        double priceRange = d.highPrice - d.lowPrice;
        int baseColor = d.style.color;

        for (int i = 0; i < d.levels.length; i++) {
            float level = d.levels[i];
            double price = d.highPrice - level * priceRange;
            float y = priceToPixelY(price, tf);

            if (y < rect.top - 20 || y > rect.bottom + 20) continue;

            // Color: slightly tinted per level
            int alpha = (int)(FIB_ALPHAS[Math.min(i, FIB_ALPHAS.length-1)] * 200);
            linePaint.setColor(baseColor);
            linePaint.setAlpha(alpha);
            linePaint.setStrokeWidth(d.style.strokeWidth);
            linePaint.setPathEffect(new android.graphics.DashPathEffect(new float[]{6,3},0));
            canvas.drawLine(xLeft, y, xRight, y, linePaint);

            // Label: "0.618"
            textPaint.setColor(baseColor);
            textPaint.setAlpha(alpha);
            String lbl = String.format(Locale.US, "%.3f", level);
            canvas.drawText(lbl, rect.right - textPaint.measureText(lbl) - 4, y - 4, textPaint);
        }
        linePaint.setPathEffect(null);
        linePaint.setAlpha(255);
    }

    // ── Price range (shaded band) ─────────────────────────────────────

    private void drawPriceRange(Canvas canvas, ChartDrawing.PriceRange d,
                                Transformer tf, RectF rect) {
        float yHigh = priceToPixelY(d.priceHigh, tf);
        float yLow  = priceToPixelY(d.priceLow,  tf);

        if (yHigh > rect.bottom && yLow > rect.bottom) return;
        if (yHigh < rect.top   && yLow < rect.top)    return;

        yHigh = Math.max(yHigh, rect.top);
        yLow  = Math.min(yLow,  rect.bottom);

        // Fill
        fillPaint.setColor(d.style.fillColor);
        canvas.drawRect(rect.left, yHigh, rect.right, yLow, fillPaint);

        // Border lines
        applyLinePaint(d.style);
        canvas.drawLine(rect.left, yHigh, rect.right, yHigh, linePaint);
        canvas.drawLine(rect.left, yLow,  rect.right, yLow,  linePaint);
    }

    // ── Coordinate helpers ────────────────────────────────────────────

    /** Convert a candle array index to canvas X pixel. */
    private float indexToPixelX(int index, Transformer tf) {
        pts2[0] = index;
        pts2[1] = 0;
        tf.pointValuesToPixel(pts2);
        return pts2[0];
    }

    /** Convert a price value to canvas Y pixel. */
    private float priceToPixelY(double price, Transformer tf) {
        pts2[0] = 0;
        pts2[1] = (float) price;
        tf.pointValuesToPixel(pts2);
        return pts2[1];
    }

    private void applyLinePaint(ChartDrawing.DrawingStyle style) {
        linePaint.setColor(style.color);
        linePaint.setStrokeWidth(style.strokeWidth);
        if (style.dashed) {
            linePaint.setPathEffect(
                    new android.graphics.DashPathEffect(
                            new float[]{style.dashOn, style.dashOff}, 0));
        } else {
            linePaint.setPathEffect(null);
        }
        linePaint.setAlpha(255);
    }
}