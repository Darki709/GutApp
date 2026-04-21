package com.example.gutapp.ui.chart;

import android.content.Context;
import android.graphics.Canvas;
import android.util.AttributeSet;
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
 * DrawingChart — extends CombinedChart with a full drawing layer.
 *
 * ── What it adds ───────────────────────────────────────────────────
 *  1. DrawingManager: stores all ChartDrawing instances
 *  2. DrawingRenderer: paints them onto the canvas after MPAndroidChart draws
 *  3. Touch-to-draw: when a DrawingTool is active, taps/drags create new drawings
 *  4. Hit-test + delete: long-press on a USER drawing selects/removes it
 *
 * ── Indicator drawings ──────────────────────────────────────────────
 *  Indicators produce drawings via Indicator.Result.drawings.
 *  StockChart calls chart.replaceIndicatorDrawings(List<ChartDrawing>) after
 *  each compute cycle. These drawings are locked (cannot be moved/deleted by touch).
 */
public class DrawingChart extends CombinedChart {

    // ── Drawing layer ─────────────────────────────────────────────────
    private final DrawingManager  drawingManager  = new DrawingManager();
    private final DrawingRenderer drawingRenderer = new DrawingRenderer();

    /** The current candle list — kept in sync by StockChart */
    private List<Candle> candles = new ArrayList<>();

    // ── Touch-to-draw state ───────────────────────────────────────────
    /** First tap anchor for two-point drawings (TrendLine, Fib, etc.) */
    private float  tapAnchorX    = -1;
    private float  tapAnchorY    = -1;
    private int    anchorIndex   = -1;
    private double anchorPrice   = Double.NaN;

    /** Currently-being-drawn in-progress drawing (shown as a preview) */
    @Nullable private ChartDrawing previewDrawing = null;

    /** Hit-test tolerance in pixels */
    private static final float HIT_RADIUS_PX = 24f;

    /** Listener for when the user creates or removes a drawing */
    @Nullable private DrawingEventListener drawingEventListener;

    public interface DrawingEventListener {
        void onDrawingCreated(ChartDrawing drawing);
        void onDrawingRemoved(ChartDrawing drawing);
        void onDrawingSelected(ChartDrawing drawing);
    }

    // ── Default drawing color (can be changed by tool UI) ────────────
    private int activeDrawingColor = android.graphics.Color.parseColor("#ECEFF1");

    // ── Constructors (all required for XML inflation) ─────────────────
    public DrawingChart(Context context) {
        super(context);
    }
    public DrawingChart(Context context, AttributeSet attrs) {
        super(context, attrs);
    }
    public DrawingChart(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    // ── Public API ────────────────────────────────────────────────────

    public DrawingManager getDrawingManager() { return drawingManager; }

    /** Called by StockChart every time it rebuilds candle data */
    public void setCandles(List<Candle> candles) {
        this.candles = new ArrayList<>(candles);
    }

    public void setActiveDrawingColor(int color) {
        this.activeDrawingColor = color;
    }

    public void setDrawingEventListener(DrawingEventListener l) {
        this.drawingEventListener = l;
    }

    /**
     * Replace all INDICATOR-source drawings with a fresh list.
     * Called by StockChart after indicator.compute() returns.
     */
    public void replaceIndicatorDrawings(List<ChartDrawing> newDrawings) {
        drawingManager.clearIndicatorDrawings();
        for (ChartDrawing d : newDrawings) drawingManager.add(d);
        postInvalidate();
    }

    // ── onDraw override ───────────────────────────────────────────────

    @Override
    protected void onDraw(Canvas canvas) {
        // 1. Let MPAndroidChart draw everything normally first
        super.onDraw(canvas);

        // 2. Draw our overlay on top
        if (!drawingManager.isEmpty() || previewDrawing != null) {
            // Temporarily add preview so renderer draws it too
            DrawingManager renderManager = drawingManager;
            if (previewDrawing != null) {
                renderManager = new DrawingManager();
                for (ChartDrawing d : drawingManager.getAll()) renderManager.add(d);
                renderManager.add(previewDrawing);
            }
            drawingRenderer.draw(canvas, this, renderManager, candles);
        }
    }

    // ── Touch handling ────────────────────────────────────────────────

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!drawingManager.hasActiveTool()) {
            // No tool active — let MPAndroidChart handle pan/zoom normally
            return super.onTouchEvent(event);
        }

        // A drawing tool is active — intercept touch events
        DrawingManager.DrawingTool tool = drawingManager.getActiveTool();
        return handleDrawingTouch(event, tool);
    }

    private boolean handleDrawingTouch(MotionEvent event, DrawingManager.DrawingTool tool) {
        float px = event.getX();
        float py = event.getY();

        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN: {
                tapAnchorX   = px;
                tapAnchorY   = py;
                anchorIndex  = pixelToIndex(px);
                anchorPrice  = pixelToPrice(py);
                previewDrawing = null;
                return true;
            }

            case MotionEvent.ACTION_MOVE: {
                if (!isTwoPointTool(tool)) break;
                // Update preview drawing while dragging
                int curIndex  = pixelToIndex(px);
                double curPrice = pixelToPrice(py);
                previewDrawing = buildDrawing(tool, anchorIndex, anchorPrice,
                        curIndex, curPrice, true);
                postInvalidate();
                return true;
            }

            case MotionEvent.ACTION_UP: {
                previewDrawing = null;
                float dx = Math.abs(px - tapAnchorX);
                float dy = Math.abs(py - tapAnchorY);

                if (dx < 8 && dy < 8) {
                    // Short tap — single-point or first point of two-point
                    handleTap(px, py, tool);
                } else if (isTwoPointTool(tool)) {
                    // Drag completed — finalize two-point drawing
                    int endIndex  = pixelToIndex(px);
                    double endPrice = pixelToPrice(py);
                    ChartDrawing created = buildDrawing(tool, anchorIndex, anchorPrice,
                            endIndex, endPrice, false);
                    if (created != null) {
                        drawingManager.add(created);
                        postInvalidate();
                        if (drawingEventListener != null)
                            drawingEventListener.onDrawingCreated(created);
                    }
                    // Reset anchor
                    anchorIndex = -1;
                    anchorPrice = Double.NaN;
                }
                return true;
            }
        }
        return true;
    }

    /**
     * Handle a single tap:
     *  - HORIZONTAL_LINE / VERTICAL_LINE → create immediately on first tap
     *  - Two-point tools → first tap sets anchor, second tap finalizes
     */
    private void handleTap(float px, float py, DrawingManager.DrawingTool tool) {
        int    tapIndex = pixelToIndex(px);
        double tapPrice = pixelToPrice(py);

        if (!isTwoPointTool(tool)) {
            // One-tap drawing
            ChartDrawing d = buildDrawing(tool, tapIndex, tapPrice, tapIndex, tapPrice, false);
            if (d != null) {
                drawingManager.add(d);
                postInvalidate();
                if (drawingEventListener != null) drawingEventListener.onDrawingCreated(d);
            }
            return;
        }

        // Two-point drawing: check if we already have a first anchor
        if (anchorIndex < 0 || Double.isNaN(anchorPrice)) {
            // First tap — store anchor, show dot
            anchorIndex = tapIndex;
            anchorPrice = tapPrice;
            postInvalidate();
        } else {
            // Second tap — finalize
            ChartDrawing d = buildDrawing(tool, anchorIndex, anchorPrice,
                    tapIndex, tapPrice, false);
            if (d != null) {
                drawingManager.add(d);
                postInvalidate();
                if (drawingEventListener != null) drawingEventListener.onDrawingCreated(d);
            }
            anchorIndex = -1;
            anchorPrice = Double.NaN;
        }
    }

    /**
     * Build a ChartDrawing from two anchor points.
     * @param preview if true, drawing will NOT be locked (still in progress)
     */
    @Nullable
    private ChartDrawing buildDrawing(DrawingManager.DrawingTool tool,
                                      int startIdx, double startPrice,
                                      int endIdx,   double endPrice,
                                      boolean preview) {
        ChartDrawing.DrawingStyle style = ChartDrawing.DrawingStyle.solid(activeDrawingColor);
        ChartDrawing.Source src = ChartDrawing.Source.USER;

        ChartDrawing d;
        switch (tool) {
            case HORIZONTAL_LINE:
                d = new ChartDrawing.HorizontalLine(startPrice, style, src);
                break;
            case VERTICAL_LINE:
                d = new ChartDrawing.VerticalLine(startIdx, style, src);
                break;
            case TREND_LINE:
                d = new ChartDrawing.TrendLine(startIdx, startPrice, endIdx, endPrice, style, src);
                break;
            case RAY_LINE:
                d = new ChartDrawing.RayLine(startIdx, startPrice, endIdx, endPrice, style, src);
                break;
            case LINEAR_REGRESSION:
                d = new ChartDrawing.LinearRegression(startIdx, endIdx, style, src);
                break;
            case FIB_RETRACEMENT:
                d = new ChartDrawing.FibRetracement(startIdx, startPrice, endIdx, endPrice, style, src);
                break;
            case PRICE_RANGE:
                double hi = Math.max(startPrice, endPrice);
                double lo = Math.min(startPrice, endPrice);
                ChartDrawing.DrawingStyle rangeStyle = ChartDrawing.DrawingStyle.solid(activeDrawingColor).withFill(true);
                d = new ChartDrawing.PriceRange(hi, lo, rangeStyle, src);
                break;
            default:
                return null;
        }
        return d;
    }

    // ── Coordinate helpers ────────────────────────────────────────────

    /** Convert canvas pixel X to the nearest candle array index */
    private int pixelToIndex(float px) {
        if (candles.isEmpty()) return 0;
        Transformer tf = getTransformer(YAxis.AxisDependency.LEFT);
        float[] pts = {px, 0};
        tf.pixelsToValue(pts);
        int index = Math.round(pts[0]);
        return Math.max(0, Math.min(candles.size() - 1, index));
    }

    /** Convert canvas pixel Y to a price value */
    private double pixelToPrice(float py) {
        Transformer tf = getTransformer(YAxis.AxisDependency.LEFT);
        float[] pts = {0, py};
        tf.pixelsToValue(pts);
        return pts[1];
    }

    private boolean isTwoPointTool(DrawingManager.DrawingTool tool) {
        return tool == DrawingManager.DrawingTool.TREND_LINE
                || tool == DrawingManager.DrawingTool.RAY_LINE
                || tool == DrawingManager.DrawingTool.LINEAR_REGRESSION
                || tool == DrawingManager.DrawingTool.FIB_RETRACEMENT
                || tool == DrawingManager.DrawingTool.PRICE_RANGE;
    }
}