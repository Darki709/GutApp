package com.example.gutapp.ui.chart;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;

import androidx.annotation.Nullable;

import com.example.gutapp.data.drawing.ChartDrawing;
import com.example.gutapp.data.drawing.DrawingManager;
import com.example.gutapp.data.drawing.DrawingRenderer;
import com.example.gutapp.data.models.Candle;
import com.github.mikephil.charting.charts.CombinedChart;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.utils.Transformer;

import java.util.ArrayList;
import java.util.List;

/**
 * DrawingChart — extends CombinedChart with a professional TradingView-style drawing layer.
 *
 * Features:
 *  - All 15 drawing types with full touch handling
 *  - 3-point tool support (Pitchfork)
 *  - Live preview while dragging
 *  - Hit-test + tap-to-select any USER drawing
 *  - Long-press on selected drawing → delete it
 *  - Snap-to-candle-OHLC (optional, toggle with setSnapEnabled)
 *  - Snap crosshair overlay while drawing
 *  - Selection highlighted with teal handle rings
 *  - Undo / Redo delegated to DrawingManager
 *  - DrawingEventListener for auto-save callbacks
 */
public class DrawingChart extends CombinedChart {

    private final DrawingManager  drawingManager  = new DrawingManager();
    private final DrawingRenderer drawingRenderer = new DrawingRenderer();
    private List<Candle> candles = new ArrayList<>();

    // ── Touch state ──────────────────────────────────────────────────
    // First anchor (all tools)
    private float tapAnchorX = -1, tapAnchorY = -1;
    private int    anchor1Index = -1;
    private double anchor1Price = Double.NaN;

    // Second anchor (three-point tools: Pitchfork)
    private int    anchor2Index = -1;
    private double anchor2Price = Double.NaN;
    private int    pitchforkState = 0; // 0=none, 1=p0 set, 2=p0+p1 set

    // Live preview drawing
    @Nullable private ChartDrawing previewDrawing = null;

    // Snap
    private boolean snapEnabled = true;

    // Hit-test tolerance in pixels
    private static final float HIT_RADIUS_PX = 28f;

    // Crosshair paint for snap overlay
    private final Paint crosshairPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    // Gesture detector for long-press
    private GestureDetector gestureDetector;

    // ── Event listener ───────────────────────────────────────────────
    @Nullable private DrawingEventListener drawingEventListener;

    public interface DrawingEventListener {
        void onDrawingCreated(ChartDrawing drawing);
        void onDrawingRemoved(ChartDrawing drawing);
        void onDrawingSelected(@Nullable ChartDrawing drawing);
        /** Called whenever drawings change so the host can trigger auto-save. */
        void onDrawingsChanged();
    }

    // ── Constructors ─────────────────────────────────────────────────
    public DrawingChart(Context context) { super(context); initDrawingLayer(); }
    public DrawingChart(Context context, AttributeSet a) { super(context, a); initDrawingLayer(); }
    public DrawingChart(Context context, AttributeSet a, int d) { super(context, a, d); initDrawingLayer(); }

    private void initDrawingLayer() {
        crosshairPaint.setColor(Color.parseColor("#26A69A"));
        crosshairPaint.setStrokeWidth(1f);
        crosshairPaint.setAlpha(150);
        crosshairPaint.setStyle(Paint.Style.STROKE);
        crosshairPaint.setPathEffect(new android.graphics.DashPathEffect(new float[]{4,4},0));

        gestureDetector = new GestureDetector(getContext(),
                new GestureDetector.SimpleOnGestureListener() {
                    @Override
                    public void onLongPress(MotionEvent e) {
                        if (drawingManager.hasActiveTool()) return;
                        // Long-press with nothing active → delete selected
                        String sel = drawingManager.getSelectedId();
                        if (sel != null) {
                            ChartDrawing d = drawingManager.get(sel);
                            drawingManager.remove(sel);
                            postInvalidate();
                            if (drawingEventListener != null && d != null) {
                                drawingEventListener.onDrawingRemoved(d);
                                drawingEventListener.onDrawingsChanged();
                            }
                        }
                    }
                });
    }

    // ── Public API ───────────────────────────────────────────────────

    public DrawingManager getDrawingManager() { return drawingManager; }

    public void setCandles(List<Candle> c) { this.candles = new ArrayList<>(c); }

    public void setDrawingEventListener(DrawingEventListener l) { this.drawingEventListener = l; }

    public void setSnapEnabled(boolean snap) { this.snapEnabled = snap; }

    public boolean isSnapEnabled() { return snapEnabled; }

    public void replaceIndicatorDrawings(List<ChartDrawing> list) {
        drawingManager.clearIndicatorDrawings();
        for (ChartDrawing d : list) drawingManager.add(d);
        postInvalidate();
    }

    /** Programmatically cancel current in-progress drawing. */
    public void cancelCurrentDrawing() {
        previewDrawing = null;
        anchor1Index = -1; anchor1Price = Double.NaN;
        anchor2Index = -1; anchor2Price = Double.NaN;
        pitchforkState = 0;
        postInvalidate();
    }

    public boolean undo() {
        boolean changed = drawingManager.undo();
        if (changed) { postInvalidate(); if (drawingEventListener!=null) drawingEventListener.onDrawingsChanged(); }
        return changed;
    }

    public boolean redo() {
        boolean changed = drawingManager.redo();
        if (changed) { postInvalidate(); if (drawingEventListener!=null) drawingEventListener.onDrawingsChanged(); }
        return changed;
    }

    // ── onDraw ───────────────────────────────────────────────────────

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        // Build temporary manager including preview
        DrawingManager renderMgr = drawingManager;
        if (previewDrawing != null) {
            renderMgr = new DrawingManager();
            for (ChartDrawing d : drawingManager.getAll()) renderMgr.add(d);
            renderMgr.add(previewDrawing);
        }

        if (!renderMgr.isEmpty()) {
            drawingRenderer.draw(canvas, this, renderMgr, candles);
        }

        // Snap crosshair while a tool is active and user is touching
        if (drawingManager.hasActiveTool() && tapAnchorX > 0) {
            drawSnapCrosshair(canvas, tapAnchorX, tapAnchorY);
        }
    }

    private void drawSnapCrosshair(Canvas canvas, float x, float y) {
        android.graphics.RectF rect = getContentRect();
        canvas.drawLine(x, rect.top, x, rect.bottom, crosshairPaint);
        canvas.drawLine(rect.left, y, rect.right, y, crosshairPaint);
    }

    // ── Touch handling ───────────────────────────────────────────────

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        gestureDetector.onTouchEvent(event);

        if (!drawingManager.hasActiveTool()) {
            // Pan/zoom mode — check for tap-to-select
            if (event.getActionMasked() == MotionEvent.ACTION_UP) {
                float dx = Math.abs(event.getX() - tapAnchorX);
                float dy = Math.abs(event.getY() - tapAnchorY);
                if (dx < 8 && dy < 8) trySelect(event.getX(), event.getY());
            }
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                tapAnchorX = event.getX(); tapAnchorY = event.getY();
            }
            return super.onTouchEvent(event);
        }

        return handleDrawingTouch(event);
    }

    private boolean handleDrawingTouch(MotionEvent event) {
        float px = event.getX(), py = event.getY();
        DrawingManager.DrawingTool tool = drawingManager.getActiveTool();

        // Apply snap
        int snapIndex  = pixelToIndex(px);
        double snapPrice = snapEnabled ? snapPrice(py, snapIndex) : pixelToPrice(py);
        float snapPx = (float) (snapEnabled ? indexToPixel(snapIndex) : px);
        float snapPy = (float) (snapEnabled ? priceToPixel(snapPrice) : py);

        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN: {
                tapAnchorX = px; tapAnchorY = py;
                // Clear selection when starting a new draw
                drawingManager.clearSelection();
                if (drawingEventListener!=null) drawingEventListener.onDrawingSelected(null);
                return true;
            }

            case MotionEvent.ACTION_MOVE: {
                if (DrawingManager.isTwoPointTool(tool) && anchor1Index >= 0) {
                    previewDrawing = buildDrawing(tool,
                            anchor1Index, anchor1Price, snapIndex, snapPrice);
                    postInvalidate();
                }
                // Show snap crosshair position
                tapAnchorX = snapPx; tapAnchorY = snapPy;
                postInvalidate();
                return true;
            }

            case MotionEvent.ACTION_UP: {
                float dx = Math.abs(px - tapAnchorX);
                float dy = Math.abs(py - tapAnchorY);
                boolean isTap = (dx < 8 && dy < 8);
                previewDrawing = null;
                tapAnchorX = -1; tapAnchorY = -1;

                handleToolTap(tool, snapIndex, snapPrice, !isTap,
                        pixelToIndex(px), pixelToPrice(py));
                postInvalidate();
                return true;
            }
        }
        return true;
    }

    private void handleToolTap(DrawingManager.DrawingTool tool,
                               int tapIndex, double tapPrice,
                               boolean wasDrag, int rawIndex, double rawPrice) {

        // ── Three-point tools (Pitchfork) ────────────────────────────
        if (DrawingManager.isThreePointTool(tool)) {
            if (pitchforkState == 0) {
                anchor1Index = tapIndex; anchor1Price = tapPrice;
                pitchforkState = 1;
            } else if (pitchforkState == 1) {
                anchor2Index = tapIndex; anchor2Price = tapPrice;
                pitchforkState = 2;
            } else {
                // Third point → finalize
                ChartDrawing d = drawingManager.addPitchfork(
                        anchor1Index, anchor1Price,
                        anchor2Index, anchor2Price,
                        tapIndex, tapPrice);
                finalize(d);
                pitchforkState = 0;
                anchor1Index = -1; anchor1Price = Double.NaN;
                anchor2Index = -1; anchor2Price = Double.NaN;
            }
            return;
        }

        // ── One-point tools ──────────────────────────────────────────
        if (!DrawingManager.isTwoPointTool(tool)) {
            ChartDrawing d = placeOnePointDrawing(tool, tapIndex, tapPrice);
            if (d != null) finalize(d);
            return;
        }

        // ── Two-point tools ──────────────────────────────────────────
        if (wasDrag) {
            // Drag completed — use raw end position
            if (anchor1Index >= 0) {
                ChartDrawing d = buildDrawing(tool, anchor1Index, anchor1Price, rawIndex, rawPrice);
                if (d != null) { drawingManager.add(d); finalize(d); }
                anchor1Index = -1; anchor1Price = Double.NaN;
            }
            return;
        }

        // Tap-tap workflow
        if (anchor1Index < 0) {
            anchor1Index = tapIndex; anchor1Price = tapPrice;
        } else {
            ChartDrawing d = buildDrawing(tool, anchor1Index, anchor1Price, tapIndex, tapPrice);
            if (d != null) { drawingManager.add(d); finalize(d); }
            anchor1Index = -1; anchor1Price = Double.NaN;
        }
    }

    @Nullable
    private ChartDrawing placeOnePointDrawing(DrawingManager.DrawingTool tool,
                                              int idx, double price) {
        switch (tool) {
            case HORIZONTAL_LINE:  return drawingManager.addHorizontalLine(price);
            case VERTICAL_LINE:    return drawingManager.addVerticalLine(idx);
            case TEXT_ANNOTATION:  return drawingManager.addTextAnnotation(idx, price, "Note");
            default:               return null;
        }
    }

    @Nullable
    private ChartDrawing buildDrawing(DrawingManager.DrawingTool tool,
                                      int si, double sp, int ei, double ep) {
        ChartDrawing.DrawingStyle style = drawingManager.buildActiveStyle();
        ChartDrawing.Source src = ChartDrawing.Source.USER;
        double hi = Math.max(sp, ep), lo = Math.min(sp, ep);

        switch (tool) {
            case TREND_LINE:         return new ChartDrawing.TrendLine(si, sp, ei, ep, style, src);
            case RAY_LINE:           return new ChartDrawing.RayLine(si, sp, ei, ep, style, src);
            case EXTENDED_LINE:      return new ChartDrawing.ExtendedLine(si, sp, ei, ep, style, src);
            case LINEAR_REGRESSION:  return new ChartDrawing.LinearRegression(si, ei, style, src);
            case FIB_RETRACEMENT: {
                ChartDrawing.DrawingStyle ds = style.copy(); ds.dashed = true;
                return new ChartDrawing.FibRetracement(si, hi, ei, lo, ds, src);
            }
            case PRICE_RANGE: {
                ChartDrawing.DrawingStyle ds = style.copy(); ds.filled = true;
                return new ChartDrawing.PriceRange(hi, lo, ds, src);
            }
            case RECTANGLE: {
                ChartDrawing.DrawingStyle ds = style.copy(); ds.filled = true;
                return new ChartDrawing.Rectangle(si, sp, ei, ep, ds, src);
            }
            case ELLIPSE: {
                ChartDrawing.DrawingStyle ds = style.copy(); ds.filled = true;
                return new ChartDrawing.Ellipse(si, sp, ei, ep, ds, src);
            }
            case ARROW:              return new ChartDrawing.Arrow(si, sp, ei, ep, style, src);
            case PARALLEL_CHANNEL: {
                double mid = (sp + ep) / 2;
                return new ChartDrawing.ParallelChannel(si, sp, ei, ep, mid, style, src);
            }
            case GANN_FAN:           return new ChartDrawing.GannFan(si, sp, ei, ep, style, src);
            default:                 return null;
        }
    }

    private void finalize(ChartDrawing d) {
        postInvalidate();
        if (drawingEventListener != null) {
            drawingEventListener.onDrawingCreated(d);
            drawingEventListener.onDrawingsChanged();
        }
    }

    // ── Hit-test (tap-to-select) ─────────────────────────────────────

    private void trySelect(float px, float py) {
        ChartDrawing best = null;
        float bestDist = HIT_RADIUS_PX;

        Transformer tf = getTransformer(YAxis.AxisDependency.LEFT);

        for (ChartDrawing d : drawingManager.getAll()) {
            if (d.source != ChartDrawing.Source.USER || d.locked) continue;
            float dist = hitDistance(d, px, py, tf);
            if (dist < bestDist) { bestDist = dist; best = d; }
        }

        if (best != null) {
            drawingManager.select(best.getInstanceId());
        } else {
            drawingManager.clearSelection();
            best = null;
        }
        postInvalidate();
        if (drawingEventListener != null) drawingEventListener.onDrawingSelected(best);
    }

    private float hitDistance(ChartDrawing d, float px, float py, Transformer tf) {
        switch (d.getType()) {
            case HORIZONTAL_LINE: {
                float y = priceToPixel(((ChartDrawing.HorizontalLine) d).price);
                return Math.abs(py - y);
            }
            case VERTICAL_LINE: {
                float x = (float) indexToPixel(((ChartDrawing.VerticalLine) d).candleIndex);
                return Math.abs(px - x);
            }
            case TREND_LINE: case RAY_LINE: case EXTENDED_LINE: case ARROW: {
                float x1, y1, x2, y2;
                if (d instanceof ChartDrawing.TrendLine) {
                    ChartDrawing.TrendLine t = (ChartDrawing.TrendLine) d;
                    x1 = (float)indexToPixel(t.startIndex); y1 = priceToPixel(t.startPrice);
                    x2 = (float)indexToPixel(t.endIndex);   y2 = priceToPixel(t.endPrice);
                } else if (d instanceof ChartDrawing.RayLine) {
                    ChartDrawing.RayLine r = (ChartDrawing.RayLine) d;
                    x1 = (float)indexToPixel(r.startIndex);  y1 = priceToPixel(r.startPrice);
                    x2 = (float)indexToPixel(r.anchorIndex); y2 = priceToPixel(r.anchorPrice);
                } else if (d instanceof ChartDrawing.Arrow) {
                    ChartDrawing.Arrow ar = (ChartDrawing.Arrow) d;
                    x1 = (float)indexToPixel(ar.startIndex); y1 = priceToPixel(ar.startPrice);
                    x2 = (float)indexToPixel(ar.endIndex);   y2 = priceToPixel(ar.endPrice);
                } else {
                    ChartDrawing.ExtendedLine el = (ChartDrawing.ExtendedLine) d;
                    x1 = (float)indexToPixel(el.startIndex); y1 = priceToPixel(el.startPrice);
                    x2 = (float)indexToPixel(el.endIndex);   y2 = priceToPixel(el.endPrice);
                }
                return pointToSegmentDist(px, py, x1, y1, x2, y2);
            }
            case RECTANGLE: case PRICE_RANGE: {
                float yH, yL;
                float xL = getContentRect().left, xR = getContentRect().right;
                float x1, x2;
                if (d instanceof ChartDrawing.Rectangle) {
                    ChartDrawing.Rectangle r = (ChartDrawing.Rectangle) d;
                    x1 = (float)indexToPixel(r.startIndex); yH = priceToPixel(Math.max(r.startPrice,r.endPrice));
                    x2 = (float)indexToPixel(r.endIndex);   yL = priceToPixel(Math.min(r.startPrice,r.endPrice));
                    xL = Math.min(x1,x2); xR = Math.max(x1,x2);
                } else {
                    ChartDrawing.PriceRange pr = (ChartDrawing.PriceRange) d;
                    yH = priceToPixel(pr.priceHigh); yL = priceToPixel(pr.priceLow);
                }
                // Hit border edges
                float d1 = pointToSegmentDist(px,py, xL,yH, xR,yH);
                float d2 = pointToSegmentDist(px,py, xL,yL, xR,yL);
                return Math.min(d1,d2);
            }
            case TEXT_ANNOTATION: {
                ChartDrawing.TextAnnotation ta = (ChartDrawing.TextAnnotation) d;
                float x = (float)indexToPixel(ta.candleIndex), y = priceToPixel(ta.price);
                return (float) Math.hypot(px-x, py-y);
            }
            default:
                return Float.MAX_VALUE;
        }
    }

    private float pointToSegmentDist(float px, float py,
                                     float x1, float y1, float x2, float y2) {
        float dx = x2-x1, dy = y2-y1;
        float lenSq = dx*dx + dy*dy;
        if (lenSq < 1) return (float) Math.hypot(px-x1, py-y1);
        float t = Math.max(0, Math.min(1, ((px-x1)*dx + (py-y1)*dy) / lenSq));
        float cx = x1 + t*dx, cy = y1 + t*dy;
        return (float) Math.hypot(px-cx, py-cy);
    }

    // ── Snap helpers ─────────────────────────────────────────────────

    /** Snap Y pixel to nearest OHLC value of the nearest candle (if within range). */
    private double snapPrice(float py, int candleIndex) {
        double raw = pixelToPrice(py);
        if (candles.isEmpty() || candleIndex < 0 || candleIndex >= candles.size()) {
            // Out of candle range — no snap, just return raw price
            return raw;
        }
        Candle c = candles.get(candleIndex);
        double[] ohlc = {c.open, c.high, c.low, c.close};
        double best = raw, bestDist = Double.MAX_VALUE;
        for (double v : ohlc) {
            double dist = Math.abs(v - raw);
            if (dist < bestDist) { bestDist = dist; best = v; }
        }
        double range = Math.abs(pixelToPrice(getContentRect().top) - pixelToPrice(getContentRect().bottom));
        return bestDist < range * 0.03 ? best : raw;
    }

    // ── Coordinate helpers ───────────────────────────────────────────

    private int pixelToIndex(float px) {
        Transformer tf = getTransformer(YAxis.AxisDependency.LEFT);
        float[] pts = {px, 0};
        tf.pixelsToValue(pts);
        // Allow negative indices (left of first candle) and indices beyond last candle
        // so users can draw trendlines and rectangles into future/past empty space.
        // Only enforce a generous outer bound to prevent Integer overflow.
        int idx = Math.round(pts[0]);
        int maxFuture = candles.isEmpty() ? 500 : candles.size() + 500;
        return Math.max(-500, Math.min(maxFuture, idx));
    }

    private double pixelToPrice(float py) {
        Transformer tf = getTransformer(YAxis.AxisDependency.LEFT);
        float[] pts = {0, py};
        tf.pixelsToValue(pts);
        return pts[1];
    }

    private double indexToPixel(int index) {
        Transformer tf = getTransformer(YAxis.AxisDependency.LEFT);
        float[] pts = {index, 0};
        tf.pointValuesToPixel(pts);
        return pts[0];
    }

    private float priceToPixel(double price) {
        Transformer tf = getTransformer(YAxis.AxisDependency.LEFT);
        float[] pts = {0, (float) price};
        tf.pointValuesToPixel(pts);
        return pts[1];
    }
}