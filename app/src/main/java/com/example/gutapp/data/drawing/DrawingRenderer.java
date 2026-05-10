package com.example.gutapp.data.drawing;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;

import com.example.gutapp.data.models.Candle;
import com.github.mikephil.charting.charts.CombinedChart;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.utils.Transformer;

import java.util.List;
import java.util.Locale;

/**
 * DrawingRenderer — pure rendering for all ChartDrawing types.
 *
 * New vs original:
 *  - Arrow with arrowhead
 *  - ExtendedLine (both directions)
 *  - TextAnnotation with bubble background
 *  - Rectangle + Ellipse shapes with optional fill
 *  - ParallelChannel (two parallel trend lines + shaded fill)
 *  - Andrews Pitchfork (median + two prong lines)
 *  - Gann Fan (9 angle lines from pivot)
 *  - Selection highlight ring on anchor handles
 *  - Price / index label on Y-axis right edge for horizontal lines
 *  - Snap crosshair when drawing tool is active
 */
public class DrawingRenderer {

    private final Paint linePaint  = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fillPaint  = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint  = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint handlePaint= new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bgPaint    = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path  path       = new Path();
    private final float[] pts2     = new float[2];

    private static final float[] FIB_ALPHAS = {1f, 0.8f, 0.7f, 0.6f, 0.7f, 0.8f, 1f};
    // Gann fan slope multipliers (relative to 1×1 unit)
    private static final float[] GANN_SLOPES = {8f, 4f, 3f, 2f, 1f, 0.5f, 0.333f, 0.25f, 0.125f};
    private static final String[] GANN_LABELS = {"1×8","1×4","1×3","1×2","1×1","2×1","3×1","4×1","8×1"};

    public DrawingRenderer() {
        linePaint.setStyle(Paint.Style.STROKE);
        fillPaint.setStyle(Paint.Style.FILL);
        textPaint.setStyle(Paint.Style.FILL);
        handlePaint.setStyle(Paint.Style.FILL);
        bgPaint.setStyle(Paint.Style.FILL);
    }

    public void draw(Canvas canvas, CombinedChart chart,
                     DrawingManager manager, List<Candle> candles) {
        if (manager.isEmpty() || candles.isEmpty()) return;

        Transformer tf      = chart.getTransformer(YAxis.AxisDependency.LEFT);
        RectF       rect    = chart.getContentRect();
        float       textSz  = chart.getResources().getDisplayMetrics().density * 9f;
        textPaint.setTextSize(textSz);

        for (ChartDrawing d : manager.getAll()) {
            if (d.style == null) continue;
            switch (d.getType()) {
                case HORIZONTAL_LINE:
                    drawHorizontalLine(canvas, (ChartDrawing.HorizontalLine) d, tf, rect);
                    break;
                case TREND_LINE:
                    drawTrendLine(canvas, (ChartDrawing.TrendLine) d, tf, rect, candles.size());
                    break;
                case RAY_LINE:
                    drawRayLine(canvas, (ChartDrawing.RayLine) d, tf, rect);
                    break;
                case EXTENDED_LINE:
                    drawExtendedLine(canvas, (ChartDrawing.ExtendedLine) d, tf, rect);
                    break;
                case VERTICAL_LINE:
                    drawVerticalLine(canvas, (ChartDrawing.VerticalLine) d, tf, rect);
                    break;
                case LINEAR_REGRESSION:
                    drawLinearRegression(canvas, (ChartDrawing.LinearRegression) d, tf, rect, candles);
                    break;
                case FIB_RETRACEMENT:
                    drawFibRetracement(canvas, (ChartDrawing.FibRetracement) d, tf, rect);
                    break;
                case PRICE_RANGE:
                    drawPriceRange(canvas, (ChartDrawing.PriceRange) d, tf, rect);
                    break;
                case RECTANGLE:
                    drawRectangle(canvas, (ChartDrawing.Rectangle) d, tf, rect);
                    break;
                case ELLIPSE:
                    drawEllipse(canvas, (ChartDrawing.Ellipse) d, tf, rect);
                    break;
                case TEXT_ANNOTATION:
                    drawTextAnnotation(canvas, (ChartDrawing.TextAnnotation) d, tf, rect, chart);
                    break;
                case ARROW:
                    drawArrow(canvas, (ChartDrawing.Arrow) d, tf, rect);
                    break;
                case PARALLEL_CHANNEL:
                    drawParallelChannel(canvas, (ChartDrawing.ParallelChannel) d, tf, rect);
                    break;
                case PITCHFORK:
                    drawPitchfork(canvas, (ChartDrawing.Pitchfork) d, tf, rect);
                    break;
                case GANN_FAN:
                    drawGannFan(canvas, (ChartDrawing.GannFan) d, tf, rect);
                    break;
            }
        }
    }

    // ── Horizontal line ───────────────────────────────────────────────

    private void drawHorizontalLine(Canvas canvas, ChartDrawing.HorizontalLine d,
                                    Transformer tf, RectF rect) {
        float y = priceToY(d.price, tf);
        if (y < rect.top - 10 || y > rect.bottom + 10) return;

        applyLine(d.style);
        canvas.drawLine(rect.left, y, rect.right, y, linePaint);

        // Y-axis label bubble on right edge
        String lbl = d.label != null && !d.label.isEmpty() ? d.label
                : String.format(Locale.US, "%.4f", d.price);
        drawYLabel(canvas, lbl, d.style.color, rect.right - 4, y);

        if (d.selected) drawSelectionHandle(canvas, rect.left + 40, y);
    }

    // ── Trend line ────────────────────────────────────────────────────

    private void drawTrendLine(Canvas canvas, ChartDrawing.TrendLine d,
                               Transformer tf, RectF rect, int totalCandles) {
        float x1 = indexToX(d.startIndex, tf), y1 = priceToY(d.startPrice, tf);
        float x2 = indexToX(d.endIndex,   tf), y2 = priceToY(d.endPrice,   tf);

        if (d.extendLeft || d.extendRight) {
            float[] extended = extendLine(x1, y1, x2, y2, rect, d.extendLeft, d.extendRight);
            x1 = extended[0]; y1 = extended[1]; x2 = extended[2]; y2 = extended[3];
        }

        applyLine(d.style);
        canvas.drawLine(x1, y1, x2, y2, linePaint);
        if (!d.extendLeft)  drawHandle(canvas, x1, y1, d.style.color, d.selected);
        if (!d.extendRight) drawHandle(canvas, x2, y2, d.style.color, d.selected);
    }

    // ── Ray line ──────────────────────────────────────────────────────

    private void drawRayLine(Canvas canvas, ChartDrawing.RayLine d,
                             Transformer tf, RectF rect) {
        float x1 = indexToX(d.startIndex, tf),  y1 = priceToY(d.startPrice, tf);
        float x2 = indexToX(d.anchorIndex, tf), y2 = priceToY(d.anchorPrice, tf);
        float dx = x2 - x1, dy = y2 - y1;
        float xEnd = rect.right + 200;
        float yEnd = dx == 0 ? rect.bottom : y1 + (xEnd - x1) * (dy / dx);

        applyLine(d.style);
        canvas.drawLine(x1, y1, xEnd, yEnd, linePaint);
        drawHandle(canvas, x1, y1, d.style.color, d.selected);
    }

    // ── Extended line (both directions) ───────────────────────────────

    private void drawExtendedLine(Canvas canvas, ChartDrawing.ExtendedLine d,
                                  Transformer tf, RectF rect) {
        float x1 = indexToX(d.startIndex, tf), y1 = priceToY(d.startPrice, tf);
        float x2 = indexToX(d.endIndex,   tf), y2 = priceToY(d.endPrice,   tf);
        float[] ext = extendLine(x1, y1, x2, y2, rect, true, true);
        applyLine(d.style);
        canvas.drawLine(ext[0], ext[1], ext[2], ext[3], linePaint);
        drawHandle(canvas, x1, y1, d.style.color, d.selected);
        drawHandle(canvas, x2, y2, d.style.color, d.selected);
    }

    // ── Vertical line ─────────────────────────────────────────────────

    private void drawVerticalLine(Canvas canvas, ChartDrawing.VerticalLine d,
                                  Transformer tf, RectF rect) {
        float x = indexToX(d.candleIndex, tf);
        if (x < rect.left - 10 || x > rect.right + 10) return;

        applyLine(d.style);
        canvas.drawLine(x, rect.top, x, rect.bottom, linePaint);

        if (d.label != null && !d.label.isEmpty()) {
            textPaint.setColor(d.style.color);
            canvas.drawText(d.label, x + 4, rect.top + textPaint.getTextSize() + 4, textPaint);
        }
        if (d.selected) drawSelectionHandle(canvas, x, rect.top + 40);
    }

    // ── Linear regression ─────────────────────────────────────────────

    private void drawLinearRegression(Canvas canvas, ChartDrawing.LinearRegression d,
                                      Transformer tf, RectF rect, List<Candle> candles) {
        int start = Math.max(0, d.startIndex);
        int end   = Math.min(candles.size() - 1, d.endIndex);
        if (end <= start) return;

        int n = end - start + 1;
        double sumX=0, sumY=0, sumXY=0, sumXX=0;
        for (int i = 0; i < n; i++) {
            double x = i, y = candles.get(start + i).close;
            sumX += x; sumY += y; sumXY += x*y; sumXX += x*x;
        }
        double slope     = (n*sumXY - sumX*sumY) / (n*sumXX - sumX*sumX);
        double intercept = (sumY - slope*sumX) / n;

        float x1 = indexToX(start, tf), y1 = priceToY(intercept, tf);
        float x2 = indexToX(end,   tf), y2 = priceToY(intercept + slope*(n-1), tf);

        applyLine(d.style);
        canvas.drawLine(x1, y1, x2, y2, linePaint);

        if (d.drawChannel) {
            double ss = 0;
            for (int i=0; i<n; i++) {
                double res = candles.get(start+i).close - (intercept + slope*i);
                ss += res*res;
            }
            double std = Math.sqrt(ss/n);

            Paint ch = new Paint(linePaint);
            ch.setAlpha(120);
            ch.setPathEffect(new android.graphics.DashPathEffect(new float[]{6,3},0));

            float yu1 = priceToY(intercept+std, tf), yu2 = priceToY(intercept+slope*(n-1)+std, tf);
            float yl1 = priceToY(intercept-std, tf), yl2 = priceToY(intercept+slope*(n-1)-std, tf);

            canvas.drawLine(x1, yu1, x2, yu2, ch);
            canvas.drawLine(x1, yl1, x2, yl2, ch);

            path.reset();
            path.moveTo(x1,yu1); path.lineTo(x2,yu2);
            path.lineTo(x2,yl2); path.lineTo(x1,yl1);
            path.close();
            fillPaint.setColor(d.style.fillColor);
            canvas.drawPath(path, fillPaint);
        }
    }

    // ── Fibonacci retracement ─────────────────────────────────────────

    private void drawFibRetracement(Canvas canvas, ChartDrawing.FibRetracement d,
                                    Transformer tf, RectF rect) {
        if (d.levels == null || d.levels.length == 0) return;
        float x1 = indexToX(d.startIndex, tf), x2 = indexToX(d.endIndex, tf);
        // Fib spans only between the two anchor X positions, ordered left→right
        float xL = Math.min(x1, x2), xR = Math.max(x1, x2);
        // Skip drawing if the whole range is off-screen
        if (xR < rect.left - 10 || xL > rect.right + 10) return;
        // Clamp draw range to visible canvas (still correct, levels are horizontal)
        float drawL = Math.max(xL, rect.left);
        float drawR = Math.min(xR, rect.right);

        double range = d.highPrice - d.lowPrice;
        int base = d.style.color;

        // Semi-transparent fill between each level (within the horizontal range)
        for (int i = 0; i < d.levels.length - 1; i++) {
            float lv1 = d.levels[i], lv2 = d.levels[i+1];
            double p1 = d.highPrice - lv1*range, p2 = d.highPrice - lv2*range;
            float fy1 = priceToY(p1, tf), fy2 = priceToY(p2, tf);
            fillPaint.setColor(base);
            fillPaint.setAlpha(i % 2 == 0 ? 25 : 12);
            canvas.drawRect(drawL, Math.min(fy1,fy2), drawR, Math.max(fy1,fy2), fillPaint);
        }

        // Vertical border lines at the two anchors (full height of the fib)
        float yTop = priceToY(d.highPrice, tf), yBot = priceToY(d.lowPrice, tf);
        linePaint.setColor(base); linePaint.setAlpha(60);
        linePaint.setStrokeWidth(d.style.strokeWidth * 0.6f);
        linePaint.setPathEffect(null);
        canvas.drawLine(xL, yTop, xL, yBot, linePaint);
        canvas.drawLine(xR, yTop, xR, yBot, linePaint);

        // Horizontal level lines — only within the anchor range
        for (int i = 0; i < d.levels.length; i++) {
            double price = d.highPrice - d.levels[i]*range;
            float y = priceToY(price, tf);
            if (y < rect.top - 20 || y > rect.bottom + 20) continue;

            int alpha = (int)(FIB_ALPHAS[Math.min(i, FIB_ALPHAS.length-1)] * 220);
            linePaint.setColor(base); linePaint.setAlpha(alpha);
            linePaint.setStrokeWidth(d.style.strokeWidth);
            linePaint.setPathEffect(new android.graphics.DashPathEffect(new float[]{6,3},0));
            canvas.drawLine(drawL, y, drawR, y, linePaint);

            // Label at right edge of the range (not the screen edge)
            textPaint.setColor(base); textPaint.setAlpha(alpha);
            String lbl = String.format(Locale.US, "%.3f  %.5f", d.levels[i], price);
            float labelX = Math.min(drawR, rect.right) - textPaint.measureText(lbl) - 4;
            if (labelX > drawL) canvas.drawText(lbl, labelX, y - 3, textPaint);
        }
        linePaint.setPathEffect(null); linePaint.setAlpha(255);

        // Anchor handles
        float yH = priceToY(d.highPrice, tf), yL = priceToY(d.lowPrice, tf);
        drawHandle(canvas, x1, yH, base, d.selected);
        drawHandle(canvas, x2, yL, base, d.selected);
    }

    // ── Price range ───────────────────────────────────────────────────

    private void drawPriceRange(Canvas canvas, ChartDrawing.PriceRange d,
                                Transformer tf, RectF rect) {
        float yH = priceToY(d.priceHigh, tf), yL = priceToY(d.priceLow, tf);
        if (yH > rect.bottom && yL > rect.bottom) return;
        if (yH < rect.top   && yL < rect.top)    return;
        yH = clamp(yH, rect.top, rect.bottom);
        yL = clamp(yL, rect.top, rect.bottom);

        fillPaint.setColor(d.style.fillColor);
        canvas.drawRect(rect.left, yH, rect.right, yL, fillPaint);
        applyLine(d.style);
        canvas.drawLine(rect.left, yH, rect.right, yH, linePaint);
        canvas.drawLine(rect.left, yL, rect.right, yL, linePaint);

        if (d.selected) { drawSelectionHandle(canvas, rect.left+40, yH); drawSelectionHandle(canvas, rect.left+40, yL); }
    }

    // ── Rectangle ─────────────────────────────────────────────────────

    private void drawRectangle(Canvas canvas, ChartDrawing.Rectangle d,
                               Transformer tf, RectF rect) {
        float x1 = indexToX(d.startIndex, tf), y1 = priceToY(d.startPrice, tf);
        float x2 = indexToX(d.endIndex,   tf), y2 = priceToY(d.endPrice,   tf);

        RectF r = new RectF(Math.min(x1,x2), Math.min(y1,y2), Math.max(x1,x2), Math.max(y1,y2));
        if (d.style.filled) {
            fillPaint.setColor(d.style.fillColor);
            canvas.drawRect(r, fillPaint);
        }
        applyLine(d.style);
        canvas.drawRect(r, linePaint);
        if (d.selected) { drawHandle(canvas,x1,y1,d.style.color,true); drawHandle(canvas,x2,y2,d.style.color,true); }
    }

    // ── Ellipse ───────────────────────────────────────────────────────

    private void drawEllipse(Canvas canvas, ChartDrawing.Ellipse d,
                             Transformer tf, RectF rect) {
        float x1 = indexToX(d.startIndex, tf), y1 = priceToY(d.startPrice, tf);
        float x2 = indexToX(d.endIndex,   tf), y2 = priceToY(d.endPrice,   tf);

        RectF r = new RectF(Math.min(x1,x2), Math.min(y1,y2), Math.max(x1,x2), Math.max(y1,y2));
        if (d.style.filled) {
            fillPaint.setColor(d.style.fillColor);
            canvas.drawOval(r, fillPaint);
        }
        applyLine(d.style);
        canvas.drawOval(r, linePaint);
        if (d.selected) { drawHandle(canvas,x1,y1,d.style.color,true); drawHandle(canvas,x2,y2,d.style.color,true); }
    }

    // ── Text annotation ───────────────────────────────────────────────

    private void drawTextAnnotation(Canvas canvas, ChartDrawing.TextAnnotation d,
                                    Transformer tf, RectF rect, CombinedChart chart) {
        float x = indexToX(d.candleIndex, tf);
        float y = priceToY(d.price, tf);
        if (x < rect.left-200 || x > rect.right+200) return;

        float sp = chart.getResources().getDisplayMetrics().scaledDensity;
        textPaint.setTextSize(d.textSizeSp * sp);
        textPaint.setTypeface(Typeface.DEFAULT_BOLD);
        textPaint.setColor(d.style.color);

        String txt = d.text != null ? d.text : "";
        float tw = textPaint.measureText(txt);
        float th = textPaint.getTextSize();
        float pad = 8f;

        // Bubble background
        bgPaint.setColor(Color.argb(200, 20, 20, 25));
        RectF bubble = new RectF(x - pad, y - th - pad, x + tw + pad, y + pad);
        canvas.drawRoundRect(bubble, 6, 6, bgPaint);

        // Border
        linePaint.setColor(d.style.color); linePaint.setStrokeWidth(1f); linePaint.setPathEffect(null);
        canvas.drawRoundRect(bubble, 6, 6, linePaint);

        canvas.drawText(txt, x, y, textPaint);
        textPaint.setTypeface(Typeface.DEFAULT);

        if (d.selected) drawSelectionHandle(canvas, x + tw/2, y - th/2);
    }

    // ── Arrow ─────────────────────────────────────────────────────────

    private void drawArrow(Canvas canvas, ChartDrawing.Arrow d,
                           Transformer tf, RectF rect) {
        float x1 = indexToX(d.startIndex, tf), y1 = priceToY(d.startPrice, tf);
        float x2 = indexToX(d.endIndex,   tf), y2 = priceToY(d.endPrice,   tf);

        applyLine(d.style);
        canvas.drawLine(x1, y1, x2, y2, linePaint);

        // Arrowhead
        float dx = x2-x1, dy = y2-y1;
        float len = (float) Math.sqrt(dx*dx + dy*dy);
        if (len < 1) return;
        float ux = dx/len, uy = dy/len;
        float headLen = 20f + d.style.strokeWidth*4;
        float headW   = 8f  + d.style.strokeWidth*2;

        float ax = x2 - ux*headLen - uy*headW;
        float ay = y2 - uy*headLen + ux*headW;
        float bx = x2 - ux*headLen + uy*headW;
        float by = y2 - uy*headLen - ux*headW;

        path.reset();
        path.moveTo(x2, y2); path.lineTo(ax, ay); path.lineTo(bx, by); path.close();
        fillPaint.setColor(d.style.color);
        canvas.drawPath(path, fillPaint);

        drawHandle(canvas, x1, y1, d.style.color, d.selected);
    }

    // ── Parallel channel ──────────────────────────────────────────────

    private void drawParallelChannel(Canvas canvas, ChartDrawing.ParallelChannel d,
                                     Transformer tf, RectF rect) {
        float x1 = indexToX(d.startIndex, tf), y1 = priceToY(d.startPrice, tf);
        float x2 = indexToX(d.endIndex,   tf), y2 = priceToY(d.endPrice,   tf);
        float ym  = priceToY(d.midPrice,   tf);

        // Offset = perpendicular distance encoded as simple Y shift
        float offset = ym - y1;

        applyLine(d.style);
        canvas.drawLine(x1, y1, x2, y2, linePaint);
        canvas.drawLine(x1, y1+offset, x2, y2+offset, linePaint);

        // Mid (dashed)
        Paint mid = new Paint(linePaint);
        mid.setAlpha(100);
        mid.setPathEffect(new android.graphics.DashPathEffect(new float[]{5,5},0));
        canvas.drawLine(x1, y1+offset/2, x2, y2+offset/2, mid);

        // Fill
        path.reset();
        path.moveTo(x1,y1); path.lineTo(x2,y2);
        path.lineTo(x2,y2+offset); path.lineTo(x1,y1+offset);
        path.close();
        fillPaint.setColor(d.style.fillColor);
        canvas.drawPath(path, fillPaint);

        drawHandle(canvas, x1, y1, d.style.color, d.selected);
        drawHandle(canvas, x2, y2, d.style.color, d.selected);
        drawHandle(canvas, x1, y1+offset, d.style.color, d.selected);
    }

    // ── Andrews Pitchfork ─────────────────────────────────────────────

    private void drawPitchfork(Canvas canvas, ChartDrawing.Pitchfork d,
                               Transformer tf, RectF rect) {
        float x0 = indexToX(d.p0Index, tf), y0 = priceToY(d.p0Price, tf);
        float x1 = indexToX(d.p1Index, tf), y1 = priceToY(d.p1Price, tf);
        float x2 = indexToX(d.p2Index, tf), y2 = priceToY(d.p2Price, tf);

        // Midpoint of p1–p2
        float mx = (x1+x2)/2, my = (y1+y2)/2;

        applyLine(d.style);
        // Handle → midpoint (median line)
        float[] medExt = extendLineRight(x0, y0, mx, my, rect);
        canvas.drawLine(x0, y0, medExt[0], medExt[1], linePaint);

        // Upper prong through p1
        Paint prong = new Paint(linePaint);
        prong.setAlpha(180);
        float[] u = extendLineRight(x1, y1, x1+(mx-x0), y1+(my-y0), rect);
        canvas.drawLine(x1, y1, u[0], u[1], prong);

        // Lower prong through p2
        float[] l = extendLineRight(x2, y2, x2+(mx-x0), y2+(my-y0), rect);
        canvas.drawLine(x2, y2, l[0], l[1], prong);

        // Handle line connecting p1–p2
        Paint handle = new Paint(linePaint);
        handle.setAlpha(120);
        handle.setPathEffect(new android.graphics.DashPathEffect(new float[]{4,4},0));
        canvas.drawLine(x1, y1, x2, y2, handle);

        drawHandle(canvas, x0, y0, d.style.color, d.selected);
        drawHandle(canvas, x1, y1, d.style.color, d.selected);
        drawHandle(canvas, x2, y2, d.style.color, d.selected);
    }

    // ── Gann Fan ──────────────────────────────────────────────────────

    private void drawGannFan(Canvas canvas, ChartDrawing.GannFan d,
                             Transformer tf, RectF rect) {
        float x0 = indexToX(d.startIndex, tf), y0 = priceToY(d.startPrice, tf);
        float x1 = indexToX(d.endIndex,   tf), y1 = priceToY(d.endPrice,   tf);

        float dx = x1 - x0;
        float dy = y1 - y0;
        if (Math.abs(dx) < 1) return;

        // 1×1 slope (pixels per pixel-x)
        float unit = dy / dx;

        for (int i = 0; i < GANN_SLOPES.length; i++) {
            float slope = unit * GANN_SLOPES[i];
            float xEnd  = rect.right + 100;
            float yEnd  = y0 + (xEnd - x0) * slope;

            int alpha = i == 4 ? 255 : 140;  // 1x1 is brightest
            linePaint.setColor(d.style.color);
            linePaint.setAlpha(alpha);
            linePaint.setStrokeWidth(i == 4 ? d.style.strokeWidth*1.5f : d.style.strokeWidth);
            linePaint.setPathEffect(null);
            canvas.drawLine(x0, y0, xEnd, yEnd, linePaint);

            // Label
            float labelX = Math.min(x0 + 60, rect.right - 40);
            float labelY = y0 + (labelX - x0) * slope - 4;
            if (labelY > rect.top && labelY < rect.bottom) {
                textPaint.setColor(d.style.color);
                textPaint.setAlpha(alpha);
                canvas.drawText(GANN_LABELS[i], labelX, labelY, textPaint);
            }
        }
        linePaint.setAlpha(255);
        drawHandle(canvas, x0, y0, d.style.color, d.selected);
    }

    // ── Handle / selection rendering ──────────────────────────────────

    private void drawHandle(Canvas canvas, float x, float y, int color, boolean selected) {
        if (selected) drawSelectionHandle(canvas, x, y);
        handlePaint.setColor(Color.parseColor("#1A1818"));
        canvas.drawCircle(x, y, 6f, handlePaint);
        handlePaint.setColor(color);
        handlePaint.setAlpha(220);
        canvas.drawCircle(x, y, 4f, handlePaint);
        handlePaint.setAlpha(255);
    }

    private void drawSelectionHandle(Canvas canvas, float x, float y) {
        handlePaint.setColor(Color.argb(60, 38, 166, 154));
        canvas.drawCircle(x, y, 16f, handlePaint);
        handlePaint.setColor(Color.argb(180, 38, 166, 154));
        handlePaint.setStyle(Paint.Style.STROKE);
        handlePaint.setStrokeWidth(1.5f);
        canvas.drawCircle(x, y, 16f, handlePaint);
        handlePaint.setStyle(Paint.Style.FILL);
    }

    private void drawYLabel(Canvas canvas, String label, int color, float x, float y) {
        textPaint.setColor(color);
        textPaint.setAlpha(220);
        float tw = textPaint.measureText(label);
        float th = textPaint.getTextSize();
        float pad = 4f;
        bgPaint.setColor(Color.argb(200, 20, 20, 25));
        canvas.drawRoundRect(new RectF(x-tw-pad*2, y-th, x, y+pad), 3, 3, bgPaint);
        canvas.drawText(label, x-tw-pad, y, textPaint);
        textPaint.setAlpha(255);
    }

    // ── Coordinate helpers ────────────────────────────────────────────

    private float indexToX(int index, Transformer tf) {
        pts2[0] = index; pts2[1] = 0;
        tf.pointValuesToPixel(pts2);
        return pts2[0];
    }

    private float priceToY(double price, Transformer tf) {
        pts2[0] = 0; pts2[1] = (float) price;
        tf.pointValuesToPixel(pts2);
        return pts2[1];
    }

    private void applyLine(ChartDrawing.DrawingStyle style) {
        linePaint.setColor(style.color);
        linePaint.setStrokeWidth(style.strokeWidth);
        int a = Math.round(255 * style.opacity);
        linePaint.setAlpha(Math.max(0, Math.min(255, a)));
        if (style.dashed) {
            linePaint.setPathEffect(new android.graphics.DashPathEffect(
                    new float[]{style.dashOn, style.dashOff}, 0));
        } else {
            linePaint.setPathEffect(null);
        }
    }

    private float clamp(float v, float lo, float hi) { return Math.max(lo, Math.min(hi, v)); }

    /** Extend a line segment to the boundaries of rect in both or one direction. */
    private float[] extendLine(float x1, float y1, float x2, float y2,
                               RectF rect, boolean extLeft, boolean extRight) {
        float dx = x2 - x1, dy = y2 - y1;
        if (Math.abs(dx) < 0.01f) {
            return new float[]{x1, rect.top, x2, rect.bottom};
        }
        float slope = dy / dx;
        float rx1 = x1, ry1 = y1, rx2 = x2, ry2 = y2;
        if (extLeft) {
            rx1 = rect.left - 200;
            ry1 = y1 + (rx1 - x1) * slope;
        }
        if (extRight) {
            rx2 = rect.right + 200;
            ry2 = y1 + (rx2 - x1) * slope;
        }
        return new float[]{rx1, ry1, rx2, ry2};
    }

    /** Extend a line to the right boundary of rect. */
    private float[] extendLineRight(float x1, float y1, float x2, float y2, RectF rect) {
        float dx = x2-x1, dy = y2-y1;
        if (Math.abs(dx) < 0.01f) return new float[]{x2, rect.bottom};
        float xEnd = rect.right+200;
        float yEnd = y1 + (xEnd-x1)*(dy/dx);
        return new float[]{xEnd, yEnd};
    }
}