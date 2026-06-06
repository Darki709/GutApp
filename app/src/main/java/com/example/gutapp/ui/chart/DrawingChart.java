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
 * DrawingChart — CombinedChart + full TradingView-style drawing / editing layer.
 *
 * ── Interaction model ────────────────────────────────────────────────
 *  PAN/ZOOM MODE  (no active tool)
 *    · Single tap            → hit-test select nearest drawing
 *    · Tap on empty space    → deselect
 *    · Long-press selected   → delete
 *    · Drag on selected body → move whole drawing
 *    · Drag on a handle      → move that anchor only (resize/reshape)
 *
 *  DRAW MODE  (active tool set)
 *    · 1-point tools: single tap places immediately
 *    · 2-point tools: first tap sets anchor-1, drag or second tap sets anchor-2
 *    · 3-point (Pitchfork): three sequential taps
 *    · Live drag preview shown while finger is moving
 *    · Snap-to-OHLC crosshair overlay
 *
 * ── Layer ordering ───────────────────────────────────────────────────
 *  BEHIND_CANDLES → drawLayer() → super.onDraw() (candles+indicators) → ABOVE_CANDLES
 *
 * ── Timestamp coordinates ────────────────────────────────────────────
 *  All anchors stored as Unix timestamps so drawings survive timeframe switches.
 */
public class DrawingChart extends CombinedChart {

    // ── State ────────────────────────────────────────────────────────
    private final DrawingManager  drawingManager  = new DrawingManager();
    private final DrawingRenderer drawingRenderer = new DrawingRenderer();
    private List<Candle> candles = new ArrayList<>();

    // ── Drawing placement state ──────────────────────────────────────
    private float  tapAnchorX = -1, tapAnchorY = -1;
    private long   anchor1Ts = -1;  private double anchor1Price = Double.NaN;
    private long   anchor2Ts = -1;  private double anchor2Price = Double.NaN;
    private int    pitchforkState = 0;
    @Nullable private ChartDrawing previewDrawing = null;

    // ── Edit drag state ──────────────────────────────────────────────
    /**
     * Which handle on the selected drawing is being dragged.
     * -1 = dragging the whole body, 0..N = specific handle index.
     * Integer.MIN_VALUE = no drag in progress.
     */
    private int    dragHandleIdx = Integer.MIN_VALUE;
    private float  dragLastX, dragLastY;
    /** Cached selected drawing ref at drag-start so we don't re-lookup every MOVE */
    @Nullable private ChartDrawing dragTarget = null;

    // ── Settings ─────────────────────────────────────────────────────
    private boolean snapEnabled = true;
    private static final float HIT_BODY_PX   = 28f;  // tap-to-select tolerance
    private static final float HIT_HANDLE_PX = 32f;  // handle grab radius

    // ── Visuals ──────────────────────────────────────────────────────
    private final Paint crosshairPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private GestureDetector gestureDetector;

    // ── Callback ─────────────────────────────────────────────────────
    @Nullable private DrawingEventListener drawingEventListener;

    public interface DrawingEventListener {
        void onDrawingCreated(ChartDrawing drawing);
        void onDrawingRemoved(ChartDrawing drawing);
        void onDrawingSelected(@Nullable ChartDrawing drawing);
        void onDrawingsChanged();
        /** Called whenever active tool changes — used to show/hide the HUD. */
        void onToolChanged(@Nullable DrawingManager.DrawingTool tool);
    }

    // ── Constructors ─────────────────────────────────────────────────
    public DrawingChart(Context context) { super(context); initDrawingLayer(); }
    public DrawingChart(Context context, AttributeSet a) { super(context, a); initDrawingLayer(); }
    public DrawingChart(Context context, AttributeSet a, int d) { super(context, a, d); initDrawingLayer(); }

    private void initDrawingLayer() {
        crosshairPaint.setColor(Color.parseColor("#26A69A"));
        crosshairPaint.setStrokeWidth(1f); crosshairPaint.setAlpha(150);
        crosshairPaint.setStyle(Paint.Style.STROKE);
        crosshairPaint.setPathEffect(new android.graphics.DashPathEffect(new float[]{4,4},0));

        gestureDetector = new GestureDetector(getContext(),
                new GestureDetector.SimpleOnGestureListener() {
                    @Override public void onLongPress(MotionEvent e) {
                        if (drawingManager.hasActiveTool()) return;
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

    public void setActiveTool(@Nullable DrawingManager.DrawingTool tool) {
        drawingManager.setActiveTool(tool);
        cancelCurrentDrawing();
        if (drawingEventListener != null) drawingEventListener.onToolChanged(tool);
    }

    public void replaceIndicatorDrawings(List<ChartDrawing> list) {
        drawingManager.clearIndicatorDrawings();
        for (ChartDrawing d : list) drawingManager.add(d);
        postInvalidate();
    }

    public void cancelCurrentDrawing() {
        previewDrawing = null;
        anchor1Ts = -1; anchor1Price = Double.NaN;
        anchor2Ts = -1; anchor2Price = Double.NaN;
        pitchforkState = 0; postInvalidate();
    }

    public boolean undo() {
        boolean ok = drawingManager.undo();
        if (ok) { postInvalidate(); notifyChanged(); } return ok;
    }
    public boolean redo() {
        boolean ok = drawingManager.redo();
        if (ok) { postInvalidate(); notifyChanged(); } return ok;
    }

    public void clearAllUserDrawings() {
        drawingManager.clearUserDrawings();
        postInvalidate(); notifyChanged();
    }

    /** Remove a single drawing by id and immediately persist the change. */
    public void removeDrawing(String instanceId) {
        ChartDrawing d = drawingManager.get(instanceId);
        drawingManager.remove(instanceId);
        postInvalidate();
        if (drawingEventListener != null && d != null)
            drawingEventListener.onDrawingRemoved(d);
        notifyChanged();
    }

    private void notifyChanged() {
        if (drawingEventListener != null) drawingEventListener.onDrawingsChanged();
    }

    /** Public entry point for the toolbar to trigger a persistence save after inline edits. */
    public void notifyDrawingsChanged() {
        postInvalidate();
        notifyChanged();
    }

    // ── Two-pass onDraw ───────────────────────────────────────────────
    @Override
    protected void onDraw(Canvas canvas) {
        prepareCanvas(canvas);                            //update the canvas with new price data
        drawLayerPass(canvas, ChartDrawing.Layer.BEHIND_CANDLES); //paint under price layer
        super.onDraw(canvas); //paint price
        drawLayerPass(canvas, ChartDrawing.Layer.ABOVE_CANDLES);// paint above price layer

        // Snap crosshair while placing a drawing
        if (drawingManager.hasActiveTool() && tapAnchorX > 0) {
            android.graphics.RectF r = getContentRect();
            canvas.drawLine(tapAnchorX, r.top, tapAnchorX, r.bottom, crosshairPaint);
            canvas.drawLine(r.left, tapAnchorY, r.right, tapAnchorY, crosshairPaint);
        }
    }

    private void prepareCanvas(Canvas canvas) {
        if (mData == null)
            return;

        // execute all drawing commands
        drawGridBackground(canvas);

        if (mAutoScaleMinMaxEnabled) {
            autoScale();
        }

        if (mAxisLeft.isEnabled())
            mAxisRendererLeft.computeAxis(mAxisLeft.mAxisMinimum, mAxisLeft.mAxisMaximum, mAxisLeft.isInverted());

        if (mAxisRight.isEnabled())
            mAxisRendererRight.computeAxis(mAxisRight.mAxisMinimum, mAxisRight.mAxisMaximum, mAxisRight.isInverted());

        if (mXAxis.isEnabled())
            mXAxisRenderer.computeAxis(mXAxis.mAxisMinimum, mXAxis.mAxisMaximum, false);

        mXAxisRenderer.renderAxisLine(canvas);
        mAxisRendererLeft.renderAxisLine(canvas);
        mAxisRendererRight.renderAxisLine(canvas);

        if (mXAxis.isDrawGridLinesBehindDataEnabled())
            mXAxisRenderer.renderGridLines(canvas);

        if (mAxisLeft.isDrawGridLinesBehindDataEnabled())
            mAxisRendererLeft.renderGridLines(canvas);

        if (mAxisRight.isDrawGridLinesBehindDataEnabled())
            mAxisRendererRight.renderGridLines(canvas);

        if (mXAxis.isEnabled() && mXAxis.isDrawLimitLinesBehindDataEnabled())
            mXAxisRenderer.renderLimitLines(canvas);

        if (mAxisLeft.isEnabled() && mAxisLeft.isDrawLimitLinesBehindDataEnabled())
            mAxisRendererLeft.renderLimitLines(canvas);

        if (mAxisRight.isEnabled() && mAxisRight.isDrawLimitLinesBehindDataEnabled())
            mAxisRendererRight.renderLimitLines(canvas);
    }

    private void drawLayerPass(Canvas canvas, ChartDrawing.Layer layer) {
        // Include the live preview drawing in the render
        if (previewDrawing != null && previewDrawing.layer == layer) {
            DrawingManager tmp = new DrawingManager();
            for (ChartDrawing d : drawingManager.getAll()) tmp.add(d);
            tmp.add(previewDrawing);
            drawingRenderer.drawLayer(canvas, this, tmp, candles, layer);
        } else {
            drawingRenderer.drawLayer(canvas, this, drawingManager, candles, layer);
        }
    }

    // ═════════════════════════════════════════════════════════════════
    // TOUCH HANDLING
    // ═════════════════════════════════════════════════════════════════
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        gestureDetector.onTouchEvent(event);

        if (drawingManager.hasActiveTool()) {
            return handlePlacementTouch(event);
        } else {
            return handleEditTouch(event);
        }
    }

    // ── EDIT / SELECT mode ───────────────────────────────────────────
    private boolean handleEditTouch(MotionEvent event) {
        float px = event.getX(), py = event.getY();

        switch (event.getActionMasked()) {

            case MotionEvent.ACTION_DOWN: {
                tapAnchorX = px; tapAnchorY = py;
                dragHandleIdx = Integer.MIN_VALUE; dragTarget = null;

                // 1. Try to grab a handle on the selected drawing
                String selId = drawingManager.getSelectedId();
                if (selId != null) {
                    ChartDrawing sel = drawingManager.get(selId);
                    if (sel != null) {
                        int h = nearestHandle(sel, px, py);
                        if (h != Integer.MIN_VALUE) {
                            dragHandleIdx = h; dragTarget = sel;
                            dragLastX = px; dragLastY = py;
                            return true;
                        }
                        if (hitBody(sel, px, py)) {
                            dragHandleIdx = -1; dragTarget = sel;
                            dragLastX = px; dragLastY = py;
                            return true;
                        }
                    }
                }
                // 3. Nothing hit — pass to super for pan/zoom
                super.onTouchEvent(event);
                return true;
            }

            case MotionEvent.ACTION_MOVE: {
                if (dragTarget != null) {
                    float dxPx = px - dragLastX, dyPx = py - dragLastY;
                    if (dragHandleIdx == -1) moveDrawing(dragTarget, dxPx, dyPx);
                    else moveHandle(dragTarget, dragHandleIdx, px, py);
                    dragLastX = px; dragLastY = py;
                    postInvalidate();
                    return true;
                }
                return super.onTouchEvent(event);
            }

            case MotionEvent.ACTION_UP: {
                if (dragTarget != null) {
                    notifyChanged();
                    dragTarget = null; dragHandleIdx = Integer.MIN_VALUE;
                    postInvalidate();
                    return true;
                }
                // Tap with no drag: try to select
                float dx = Math.abs(px - tapAnchorX), dy = Math.abs(py - tapAnchorY);
                if (dx < 10 && dy < 10) trySelect(px, py);
                super.onTouchEvent(event);
                return true;
            }

            case MotionEvent.ACTION_CANCEL: {
                if (dragTarget != null) {
                    notifyChanged();
                    dragTarget = null; dragHandleIdx = Integer.MIN_VALUE;
                }
                return super.onTouchEvent(event);
            }
        }
        return super.onTouchEvent(event);
    }

    // ── PLACEMENT mode ───────────────────────────────────────────────
    private boolean handlePlacementTouch(MotionEvent event) {
        float px = event.getX(), py = event.getY();
        DrawingManager.DrawingTool tool = drawingManager.getActiveTool();

        long   snapTs    = pixelToTimestamp(px);
        double snapPrice = snapEnabled ? snapToOHLC(py, snapTs) : pixelToPrice(py);
        float  snapPx    = (float) timestampToPixelX(snapTs);
        float  snapPy    = priceToPixelY(snapPrice);

        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                tapAnchorX = px; tapAnchorY = py;
                drawingManager.clearSelection();
                if (drawingEventListener != null) drawingEventListener.onDrawingSelected(null);
                return true;

            case MotionEvent.ACTION_MOVE:
                if (DrawingManager.isTwoPointTool(tool) && anchor1Ts >= 0) {
                    previewDrawing = buildDrawing(tool, anchor1Ts, anchor1Price, snapTs, snapPrice);
                    if (previewDrawing != null) previewDrawing.layer = ChartDrawing.Layer.ABOVE_CANDLES;
                }
                tapAnchorX = snapPx; tapAnchorY = snapPy;
                postInvalidate(); return true;

            case MotionEvent.ACTION_UP: {
                float dx = Math.abs(px - tapAnchorX), dy = Math.abs(py - tapAnchorY);
                boolean wasDrag = (dx > 8 || dy > 8);
                previewDrawing = null; tapAnchorX = -1; tapAnchorY = -1;
                handlePlacementTap(tool, snapTs, snapPrice, wasDrag,
                        pixelToTimestamp(px), pixelToPrice(py));
                postInvalidate(); return true;
            }
        }
        return true;
    }

    private void handlePlacementTap(DrawingManager.DrawingTool tool,
                                    long snapTs, double snapPrice, boolean wasDrag,
                                    long rawTs, double rawPrice) {
        // Three-point
        if (DrawingManager.isThreePointTool(tool)) {
            if      (pitchforkState == 0) { anchor1Ts=snapTs; anchor1Price=snapPrice; pitchforkState=1; }
            else if (pitchforkState == 1) { anchor2Ts=snapTs; anchor2Price=snapPrice; pitchforkState=2; }
            else {
                finalizeDrawing(drawingManager.addPitchfork(
                        anchor1Ts,anchor1Price, anchor2Ts,anchor2Price, snapTs,snapPrice));
                pitchforkState=0; anchor1Ts=-1; anchor1Price=Double.NaN;
                anchor2Ts=-1; anchor2Price=Double.NaN;
            }
            return;
        }
        // One-point
        if (!DrawingManager.isTwoPointTool(tool)) {
            ChartDrawing d = placeOnePoint(tool, snapTs, snapPrice);
            if (d != null) finalizeDrawing(d);
            return;
        }
        // Two-point — drag workflow
        if (wasDrag) {
            if (anchor1Ts >= 0) {
                ChartDrawing d = buildDrawing(tool, anchor1Ts, anchor1Price, rawTs, rawPrice);
                if (d != null) { drawingManager.add(d); finalizeDrawing(d); }
                anchor1Ts=-1; anchor1Price=Double.NaN;
            }
            return;
        }
        // Two-point — tap-tap workflow
        if (anchor1Ts < 0) { anchor1Ts=snapTs; anchor1Price=snapPrice; }
        else {
            ChartDrawing d = buildDrawing(tool, anchor1Ts, anchor1Price, snapTs, snapPrice);
            if (d != null) { drawingManager.add(d); finalizeDrawing(d); }
            anchor1Ts=-1; anchor1Price=Double.NaN;
        }
    }

    // ═════════════════════════════════════════════════════════════════
    // EDIT HELPERS — move / resize each drawing type
    // ═════════════════════════════════════════════════════════════════

    /**
     * Returns handle index (0-based) if px,py is within HIT_HANDLE_PX of any
     * anchor handle, or Integer.MIN_VALUE if none.
     */
    private int nearestHandle(ChartDrawing d, float px, float py) {
        float[][] handles = getHandles(d);
        if (handles == null) return Integer.MIN_VALUE;
        for (int i = 0; i < handles.length; i++) {
            float dist = (float) Math.hypot(px - handles[i][0], py - handles[i][1]);
            if (dist <= HIT_HANDLE_PX) return i;
        }
        return Integer.MIN_VALUE;
    }

    /** Returns pixel positions [x,y] for every draggable handle of a drawing. */
    @Nullable
    private float[][] getHandles(ChartDrawing d) {
        switch (d.getType()) {
            case HORIZONTAL_LINE:
                return new float[][]{{getContentRect().left + 40,
                        priceToPixelY(((ChartDrawing.HorizontalLine)d).price)}};
            case VERTICAL_LINE:
                return new float[][]{{(float)timestampToPixelX(((ChartDrawing.VerticalLine)d).candleTs),
                        getContentRect().top + 40}};
            case TREND_LINE: { ChartDrawing.TrendLine t=(ChartDrawing.TrendLine)d;
                return new float[][]{
                        {(float)timestampToPixelX(t.startTs), priceToPixelY(t.startPrice)},
                        {(float)timestampToPixelX(t.endTs),   priceToPixelY(t.endPrice)}}; }
            case RAY_LINE: { ChartDrawing.RayLine r=(ChartDrawing.RayLine)d;
                return new float[][]{
                        {(float)timestampToPixelX(r.startTs),  priceToPixelY(r.startPrice)},
                        {(float)timestampToPixelX(r.anchorTs), priceToPixelY(r.anchorPrice)}}; }
            case EXTENDED_LINE: { ChartDrawing.ExtendedLine el=(ChartDrawing.ExtendedLine)d;
                return new float[][]{
                        {(float)timestampToPixelX(el.startTs), priceToPixelY(el.startPrice)},
                        {(float)timestampToPixelX(el.endTs),   priceToPixelY(el.endPrice)}}; }
            case ARROW: { ChartDrawing.Arrow ar=(ChartDrawing.Arrow)d;
                return new float[][]{
                        {(float)timestampToPixelX(ar.startTs), priceToPixelY(ar.startPrice)},
                        {(float)timestampToPixelX(ar.endTs),   priceToPixelY(ar.endPrice)}}; }
            case RECTANGLE: { ChartDrawing.Rectangle r=(ChartDrawing.Rectangle)d;
                return new float[][]{
                        {(float)timestampToPixelX(r.startTs), priceToPixelY(r.startPrice)},
                        {(float)timestampToPixelX(r.endTs),   priceToPixelY(r.endPrice)}}; }
            case ELLIPSE: { ChartDrawing.Ellipse el=(ChartDrawing.Ellipse)d;
                return new float[][]{
                        {(float)timestampToPixelX(el.startTs), priceToPixelY(el.startPrice)},
                        {(float)timestampToPixelX(el.endTs),   priceToPixelY(el.endPrice)}}; }
            case FIB_RETRACEMENT: { ChartDrawing.FibRetracement f=(ChartDrawing.FibRetracement)d;
                return new float[][]{
                        {(float)timestampToPixelX(f.startTs), priceToPixelY(f.highPrice)},
                        {(float)timestampToPixelX(f.endTs),   priceToPixelY(f.lowPrice)}}; }
            case PARALLEL_CHANNEL: { ChartDrawing.ParallelChannel pc=(ChartDrawing.ParallelChannel)d;
                return new float[][]{
                        {(float)timestampToPixelX(pc.startTs), priceToPixelY(pc.startPrice)},
                        {(float)timestampToPixelX(pc.endTs),   priceToPixelY(pc.endPrice)},
                        {(float)timestampToPixelX(pc.startTs), priceToPixelY(pc.midPrice)}}; }
            case PITCHFORK: { ChartDrawing.Pitchfork pf=(ChartDrawing.Pitchfork)d;
                return new float[][]{
                        {(float)timestampToPixelX(pf.p0Ts), priceToPixelY(pf.p0Price)},
                        {(float)timestampToPixelX(pf.p1Ts), priceToPixelY(pf.p1Price)},
                        {(float)timestampToPixelX(pf.p2Ts), priceToPixelY(pf.p2Price)}}; }
            case GANN_FAN: { ChartDrawing.GannFan gf=(ChartDrawing.GannFan)d;
                return new float[][]{
                        {(float)timestampToPixelX(gf.startTs), priceToPixelY(gf.startPrice)},
                        {(float)timestampToPixelX(gf.endTs),   priceToPixelY(gf.endPrice)}}; }
            case TEXT_ANNOTATION: { ChartDrawing.TextAnnotation ta=(ChartDrawing.TextAnnotation)d;
                return new float[][]{{(float)timestampToPixelX(ta.candleTs), priceToPixelY(ta.price)}}; }
            case LINEAR_REGRESSION: { ChartDrawing.LinearRegression lr=(ChartDrawing.LinearRegression)d;
                return new float[][]{
                        {(float)timestampToPixelX(lr.startTs), getContentRect().centerY()},
                        {(float)timestampToPixelX(lr.endTs),   getContentRect().centerY()}}; }
            case RISK_REWARD: {
                ChartDrawing.RiskReward rr = (ChartDrawing.RiskReward) d;
                float xStart = (float) timestampToPixelX(rr.startTs);
                float xEnd = (float) timestampToPixelX(rr.endTs);
                float yEntry = priceToPixelY(rr.entryPrice);
                float yTarget = priceToPixelY(rr.targetPrice);
                float yStop = priceToPixelY(rr.stopPrice);

                return new float[][]{
                        {xStart, yEntry},          // Handle 0: Entry Anchor Point (Left boundary line)
                        {(xStart + xEnd) / 2f, yTarget}, // Handle 1: Profit Target Ceiling/Floor Adjustment (Center)
                        {(xStart + xEnd) / 2f, yStop},   // Handle 2: Stop Loss Ceiling/Floor Adjustment (Center)
                        {xEnd, yEntry}             // Handle 3: Time Extension Limit (Right boundary line)
                };
            }
            default: return null;
        }
    }

    // ── Helper: fractional-index ↔ timestamp (no rounding loss) ────────

    /** Pixel X → fractional candle index (accurate past last candle). */
    private float pixelToFractionalIndex(float px) {
        Transformer tf = getTransformer(YAxis.AxisDependency.RIGHT);
        float[] p = {px, 0}; tf.pixelsToValue(p); return p[0];
    }

    /** Fractional candle index → timestamp (extrapolates beyond data range). */
    private long fractionalIndexToTimestamp(float fi) {
        if (candles.isEmpty()) return System.currentTimeMillis() / 1000L;
        if (candles.size() == 1) return candles.get(0).timestamp;
        long t0 = candles.get(0).timestamp;
        long tN = candles.get(candles.size() - 1).timestamp;
        long avg = (tN - t0) / Math.max(1, candles.size() - 1);
        if (fi <= 0)               return t0 + Math.round(fi * avg);
        if (fi >= candles.size()-1) return tN + Math.round((fi-(candles.size()-1)) * avg);
        int lo = (int) fi, hi = Math.min(lo+1, candles.size()-1);
        return candles.get(lo).timestamp
                + Math.round((fi-lo)*(candles.get(hi).timestamp - candles.get(lo).timestamp));
    }

    /** Timestamp → fractional candle index (inverse of above). */
    private float timestampToFractionalIndex(long ts) {
        if (candles.isEmpty()) return 0;
        if (candles.size() == 1) return 0;
        long t0 = candles.get(0).timestamp;
        long tN = candles.get(candles.size()-1).timestamp;
        long avg = (tN - t0) / Math.max(1, candles.size()-1);
        if (ts <= t0) return avg > 0 ? (float)(ts-t0)/avg : 0;
        if (ts >= tN) return (candles.size()-1) + (avg > 0 ? (float)(ts-tN)/avg : 0);
        int lo = 0, hi = candles.size()-1;
        while (lo+1 < hi) { int m=(lo+hi)/2; if (candles.get(m).timestamp<=ts) lo=m; else hi=m; }
        long tLo=candles.get(lo).timestamp, tHi=candles.get(hi).timestamp;
        return tHi==tLo ? lo : lo + (float)(ts-tLo)/(tHi-tLo);
    }

    /** Move a specific handle to a new pixel position — lossless past last candle. */
    private void moveHandle(ChartDrawing d, int handleIdx, float px, float py) {
        long   newTs    = fractionalIndexToTimestamp(pixelToFractionalIndex(px));
        double newPrice = snapEnabled ? snapToOHLC(py, newTs) : pixelToPrice(py);
        switch (d.getType()) {
            case HORIZONTAL_LINE:  ((ChartDrawing.HorizontalLine)d).price = newPrice; break;
            case VERTICAL_LINE:    ((ChartDrawing.VerticalLine)d).candleTs = newTs; break;
            case TREND_LINE: { ChartDrawing.TrendLine t=(ChartDrawing.TrendLine)d;
                if (handleIdx==0){t.startTs=newTs;t.startPrice=newPrice;}
                else             {t.endTs  =newTs;t.endPrice  =newPrice;} break; }
            case RAY_LINE: { ChartDrawing.RayLine r=(ChartDrawing.RayLine)d;
                if (handleIdx==0){r.startTs=newTs;r.startPrice=newPrice;}
                else             {r.anchorTs=newTs;r.anchorPrice=newPrice;} break; }
            case EXTENDED_LINE: { ChartDrawing.ExtendedLine el=(ChartDrawing.ExtendedLine)d;
                if (handleIdx==0){el.startTs=newTs;el.startPrice=newPrice;}
                else             {el.endTs  =newTs;el.endPrice  =newPrice;} break; }
            case ARROW: { ChartDrawing.Arrow ar=(ChartDrawing.Arrow)d;
                if (handleIdx==0){ar.startTs=newTs;ar.startPrice=newPrice;}
                else             {ar.endTs  =newTs;ar.endPrice  =newPrice;} break; }
            case RECTANGLE: { ChartDrawing.Rectangle r=(ChartDrawing.Rectangle)d;
                if (handleIdx==0){r.startTs=newTs;r.startPrice=newPrice;}
                else             {r.endTs  =newTs;r.endPrice  =newPrice;} break; }
            case ELLIPSE: { ChartDrawing.Ellipse el=(ChartDrawing.Ellipse)d;
                if (handleIdx==0){el.startTs=newTs;el.startPrice=newPrice;}
                else             {el.endTs  =newTs;el.endPrice  =newPrice;} break; }
            case FIB_RETRACEMENT: { ChartDrawing.FibRetracement f=(ChartDrawing.FibRetracement)d;
                if (handleIdx==0){f.startTs=newTs;f.highPrice=newPrice;}
                else             {f.endTs  =newTs;f.lowPrice =newPrice;} break; }
            case PARALLEL_CHANNEL: { ChartDrawing.ParallelChannel pc=(ChartDrawing.ParallelChannel)d;
                if (handleIdx==0)     {pc.startTs=newTs;pc.startPrice=newPrice;}
                else if(handleIdx==1) {pc.endTs=newTs;pc.endPrice=newPrice;}
                else                  {pc.midPrice=newPrice;} break; }
            case PITCHFORK: { ChartDrawing.Pitchfork pf=(ChartDrawing.Pitchfork)d;
                if (handleIdx==0)     {pf.p0Ts=newTs;pf.p0Price=newPrice;}
                else if(handleIdx==1) {pf.p1Ts=newTs;pf.p1Price=newPrice;}
                else                  {pf.p2Ts=newTs;pf.p2Price=newPrice;} break; }
            case GANN_FAN: { ChartDrawing.GannFan gf=(ChartDrawing.GannFan)d;
                if (handleIdx==0){gf.startTs=newTs;gf.startPrice=newPrice;}
                else             {gf.endTs  =newTs;gf.endPrice  =newPrice;} break; }
            case TEXT_ANNOTATION: { ChartDrawing.TextAnnotation ta=(ChartDrawing.TextAnnotation)d;
                ta.candleTs=newTs; ta.price=newPrice; break; }
            case LINEAR_REGRESSION: { ChartDrawing.LinearRegression lr=(ChartDrawing.LinearRegression)d;
                if (handleIdx==0) lr.startTs=newTs; else lr.endTs=newTs; break; }
            case RISK_REWARD:{
                ChartDrawing.RiskReward rr = (ChartDrawing.RiskReward) d;
                long currentTs = pixelToTimestamp(px);
                double currentPrice = pixelToPrice(py); // or your specific pixel to data transformation utility

                switch (handleIdx) {
                    case 0: // Left anchor moved
                        rr.startTs = currentTs;
                        rr.entryPrice = currentPrice;
                        break;
                    case 1: // Take Profit vertical slider
                        rr.targetPrice = currentPrice;
                        break;
                    case 2: // Stop Loss vertical slider
                        rr.stopPrice = currentPrice;
                        break;
                    case 3: // Right side extension boundary moved
                        rr.endTs = currentTs;
                        break;
                }
                break;}
        }
    }

    private void moveDrawing(ChartDrawing d, float dxPx, float dyPx) {
        // Convert pixel delta to fractional-index delta using the transformer directly.
        // We must transform two ABSOLUTE pixel positions and subtract — NOT transform
        // a relative delta from 0, because pixelsToValue({0,0}) is the LEFT EDGE of
        // the current viewport, not index 0, and changes as the chart scrolls.
        Transformer tf = getTransformer(YAxis.AxisDependency.RIGHT);
        float[] pRef   = {dragLastX,        0f}; tf.pixelsToValue(pRef);
        float[] pMoved = {dragLastX + dxPx, 0f}; tf.pixelsToValue(pMoved);
        float dfi = pMoved[0] - pRef[0];

        double dprice = pixelToPrice(dragLastY + dyPx) - pixelToPrice(dragLastY);

        java.util.function.LongUnaryOperator shift =
                ts -> fractionalIndexToTimestamp(timestampToFractionalIndex(ts) + dfi);

        switch (d.getType()) {
            case HORIZONTAL_LINE:  ((ChartDrawing.HorizontalLine)d).price += dprice; break;
            case VERTICAL_LINE:    { ChartDrawing.VerticalLine v=(ChartDrawing.VerticalLine)d;
                v.candleTs=shift.applyAsLong(v.candleTs); break; }
            case PRICE_RANGE: { ChartDrawing.PriceRange pr=(ChartDrawing.PriceRange)d;
                pr.priceHigh+=dprice; pr.priceLow+=dprice; break; }
            case TREND_LINE: { ChartDrawing.TrendLine t=(ChartDrawing.TrendLine)d;
                t.startTs=shift.applyAsLong(t.startTs); t.startPrice+=dprice;
                t.endTs  =shift.applyAsLong(t.endTs);   t.endPrice  +=dprice; break; }
            case RAY_LINE: { ChartDrawing.RayLine r=(ChartDrawing.RayLine)d;
                r.startTs =shift.applyAsLong(r.startTs);  r.startPrice +=dprice;
                r.anchorTs=shift.applyAsLong(r.anchorTs); r.anchorPrice+=dprice; break; }
            case EXTENDED_LINE: { ChartDrawing.ExtendedLine el=(ChartDrawing.ExtendedLine)d;
                el.startTs=shift.applyAsLong(el.startTs); el.startPrice+=dprice;
                el.endTs  =shift.applyAsLong(el.endTs);   el.endPrice  +=dprice; break; }
            case ARROW: { ChartDrawing.Arrow ar=(ChartDrawing.Arrow)d;
                ar.startTs=shift.applyAsLong(ar.startTs); ar.startPrice+=dprice;
                ar.endTs  =shift.applyAsLong(ar.endTs);   ar.endPrice  +=dprice; break; }
            case RECTANGLE: { ChartDrawing.Rectangle r=(ChartDrawing.Rectangle)d;
                r.startTs=shift.applyAsLong(r.startTs); r.startPrice+=dprice;
                r.endTs  =shift.applyAsLong(r.endTs);   r.endPrice  +=dprice; break; }
            case ELLIPSE: { ChartDrawing.Ellipse el=(ChartDrawing.Ellipse)d;
                el.startTs=shift.applyAsLong(el.startTs); el.startPrice+=dprice;
                el.endTs  =shift.applyAsLong(el.endTs);   el.endPrice  +=dprice; break; }
            case FIB_RETRACEMENT: { ChartDrawing.FibRetracement f=(ChartDrawing.FibRetracement)d;
                f.startTs=shift.applyAsLong(f.startTs); f.highPrice+=dprice;
                f.endTs  =shift.applyAsLong(f.endTs);   f.lowPrice +=dprice; break; }
            case PARALLEL_CHANNEL: { ChartDrawing.ParallelChannel pc=(ChartDrawing.ParallelChannel)d;
                pc.startTs=shift.applyAsLong(pc.startTs); pc.startPrice+=dprice;
                pc.endTs  =shift.applyAsLong(pc.endTs);   pc.endPrice  +=dprice;
                pc.midPrice+=dprice; break; }
            case PITCHFORK: { ChartDrawing.Pitchfork pf=(ChartDrawing.Pitchfork)d;
                pf.p0Ts=shift.applyAsLong(pf.p0Ts); pf.p0Price+=dprice;
                pf.p1Ts=shift.applyAsLong(pf.p1Ts); pf.p1Price+=dprice;
                pf.p2Ts=shift.applyAsLong(pf.p2Ts); pf.p2Price+=dprice; break; }
            case GANN_FAN: { ChartDrawing.GannFan gf=(ChartDrawing.GannFan)d;
                gf.startTs=shift.applyAsLong(gf.startTs); gf.startPrice+=dprice;
                gf.endTs  =shift.applyAsLong(gf.endTs);   gf.endPrice  +=dprice; break; }
            case TEXT_ANNOTATION: { ChartDrawing.TextAnnotation ta=(ChartDrawing.TextAnnotation)d;
                ta.candleTs=shift.applyAsLong(ta.candleTs); ta.price+=dprice; break; }
            case LINEAR_REGRESSION: { ChartDrawing.LinearRegression lr=(ChartDrawing.LinearRegression)d;
                lr.startTs=shift.applyAsLong(lr.startTs);
                lr.endTs  =shift.applyAsLong(lr.endTs); break; }
            case RISK_REWARD:{
                ChartDrawing.RiskReward rr = (ChartDrawing.RiskReward) d;

                // Convert the pixel offset differentials using your existing delta math pipelines
                long deltaTs = pixelToTimestamp(dragLastX + dxPx) - pixelToTimestamp(dragLastX);

                rr.startTs += deltaTs;
                rr.endTs += deltaTs;
                rr.entryPrice += dprice;
                rr.targetPrice += dprice;
                rr.stopPrice += dprice;
        }
    }
}

/** Returns true if px,py is within the bounding box / body hit area of d. */
private boolean hitBody(ChartDrawing d, float px, float py) {
    return hitDistance(d, px, py) < HIT_BODY_PX;
}

// ═════════════════════════════════════════════════════════════════
// HIT-TEST (tap-to-select)
// ═════════════════════════════════════════════════════════════════
private void trySelect(float px, float py) {
    ChartDrawing best = null; float bestDist = HIT_BODY_PX;
    for (ChartDrawing d : drawingManager.getAll()) {
        if (d.source != ChartDrawing.Source.USER || d.locked) continue;
        float dist = hitDistance(d, px, py);
        if (dist < bestDist) { bestDist=dist; best=d; }
    }
    if (best != null) drawingManager.select(best.getInstanceId());
    else drawingManager.clearSelection();
    postInvalidate();
    if (drawingEventListener != null) drawingEventListener.onDrawingSelected(best);
}

private float hitDistance(ChartDrawing d, float px, float py) {
    switch (d.getType()) {
        case HORIZONTAL_LINE:
            return Math.abs(py - priceToPixelY(((ChartDrawing.HorizontalLine)d).price));

        case VERTICAL_LINE:
            return Math.abs(px - (float)timestampToPixelX(((ChartDrawing.VerticalLine)d).candleTs));

        case PRICE_RANGE: {
            ChartDrawing.PriceRange pr = (ChartDrawing.PriceRange) d;
            float yH = priceToPixelY(pr.priceHigh), yL = priceToPixelY(pr.priceLow);
            android.graphics.RectF rc = getContentRect();
            return Math.min(ptSeg(px,py,rc.left,yH,rc.right,yH),
                    ptSeg(px,py,rc.left,yL,rc.right,yL));
        }

        case TREND_LINE: { ChartDrawing.TrendLine t = (ChartDrawing.TrendLine) d;
            return ptSeg(px,py,
                    (float)timestampToPixelX(t.startTs), priceToPixelY(t.startPrice),
                    (float)timestampToPixelX(t.endTs),   priceToPixelY(t.endPrice)); }

        case RAY_LINE: { ChartDrawing.RayLine r = (ChartDrawing.RayLine) d;
            return ptSeg(px,py,
                    (float)timestampToPixelX(r.startTs),  priceToPixelY(r.startPrice),
                    (float)timestampToPixelX(r.anchorTs), priceToPixelY(r.anchorPrice)); }

        case EXTENDED_LINE: { ChartDrawing.ExtendedLine el = (ChartDrawing.ExtendedLine) d;
            return ptSeg(px,py,
                    (float)timestampToPixelX(el.startTs), priceToPixelY(el.startPrice),
                    (float)timestampToPixelX(el.endTs),   priceToPixelY(el.endPrice)); }

        case ARROW: { ChartDrawing.Arrow ar = (ChartDrawing.Arrow) d;
            return ptSeg(px,py,
                    (float)timestampToPixelX(ar.startTs), priceToPixelY(ar.startPrice),
                    (float)timestampToPixelX(ar.endTs),   priceToPixelY(ar.endPrice)); }

        case RECTANGLE: {
            ChartDrawing.Rectangle r = (ChartDrawing.Rectangle) d;
            float x1 = (float)timestampToPixelX(r.startTs), y1 = priceToPixelY(r.startPrice);
            float x2 = (float)timestampToPixelX(r.endTs),   y2 = priceToPixelY(r.endPrice);
            float xL = Math.min(x1,x2), xR = Math.max(x1,x2);
            float yT = Math.min(y1,y2), yB = Math.max(y1,y2);
            float d1 = ptSeg(px,py,xL,yT,xR,yT);
            float d2 = ptSeg(px,py,xL,yB,xR,yB);
            float d3 = ptSeg(px,py,xL,yT,xL,yB);
            float d4 = ptSeg(px,py,xR,yT,xR,yB);
            return Math.min(Math.min(d1,d2), Math.min(d3,d4));
        }

        case ELLIPSE: {
            ChartDrawing.Ellipse el = (ChartDrawing.Ellipse) d;
            float x1 = (float)timestampToPixelX(el.startTs), y1 = priceToPixelY(el.startPrice);
            float x2 = (float)timestampToPixelX(el.endTs),   y2 = priceToPixelY(el.endPrice);
            float cx = (x1+x2)/2, cy = (y1+y2)/2;
            float rx = Math.abs(x2-x1)/2, ry = Math.abs(y2-y1)/2;
            if (rx < 1 || ry < 1) return Float.MAX_VALUE;
            double nx = (px-cx)/rx, ny = (py-cy)/ry;
            return (float)(Math.abs(Math.sqrt(nx*nx+ny*ny) - 1.0) * Math.min(rx, ry));
        }

        case FIB_RETRACEMENT: {
            ChartDrawing.FibRetracement f = (ChartDrawing.FibRetracement) d;
            float x1 = (float)timestampToPixelX(f.startTs);
            float x2 = (float)timestampToPixelX(f.endTs);
            float xL = Math.min(x1,x2), xR = Math.max(x1,x2);
            // Hit any level line within the horizontal range
            if (f.levels != null && px >= xL && px <= xR) {
                float minDist = Float.MAX_VALUE;
                double range = f.highPrice - f.lowPrice;
                for (float lv : f.levels) {
                    float y = priceToPixelY(f.highPrice - lv * range);
                    minDist = Math.min(minDist, Math.abs(py - y));
                }
                return minDist;
            }
            // Outside horizontal range — hit the anchor handles
            return Math.min(
                    (float)Math.hypot(px - x1, py - priceToPixelY(f.highPrice)),
                    (float)Math.hypot(px - x2, py - priceToPixelY(f.lowPrice)));
        }

        case TEXT_ANNOTATION: {
            ChartDrawing.TextAnnotation ta = (ChartDrawing.TextAnnotation) d;
            return (float)Math.hypot(px - timestampToPixelX(ta.candleTs),
                    py - priceToPixelY(ta.price));
        }

        case PARALLEL_CHANNEL: {
            ChartDrawing.ParallelChannel pc = (ChartDrawing.ParallelChannel) d;
            float x1 = (float)timestampToPixelX(pc.startTs), y1 = priceToPixelY(pc.startPrice);
            float x2 = (float)timestampToPixelX(pc.endTs),   y2 = priceToPixelY(pc.endPrice);
            float ym  = priceToPixelY(pc.midPrice);
            float offset = ym - y1;
            float d1 = ptSeg(px,py, x1,y1,        x2,y2);
            float d2 = ptSeg(px,py, x1,y1+offset, x2,y2+offset);
            return Math.min(d1, d2);
        }

        case LINEAR_REGRESSION: {
            ChartDrawing.LinearRegression lr = (ChartDrawing.LinearRegression) d;
            // Hit on the start/end boundary lines (vertical handles)
            float x1 = (float)timestampToPixelX(lr.startTs);
            float x2 = (float)timestampToPixelX(lr.endTs);
            android.graphics.RectF rc = getContentRect();
            return Math.min(Math.abs(px - x1), Math.abs(px - x2));
        }

        case PITCHFORK: {
            ChartDrawing.Pitchfork pf = (ChartDrawing.Pitchfork) d;
            float x0 = (float)timestampToPixelX(pf.p0Ts), y0 = priceToPixelY(pf.p0Price);
            float x1 = (float)timestampToPixelX(pf.p1Ts), y1 = priceToPixelY(pf.p1Price);
            float x2 = (float)timestampToPixelX(pf.p2Ts), y2 = priceToPixelY(pf.p2Price);
            float mx = (x1+x2)/2, my = (y1+y2)/2;
            float d0 = ptSeg(px,py, x0,y0, mx,my);  // median line
            float d1 = (float)Math.hypot(px-x1, py-y1); // p1 handle
            float d2 = (float)Math.hypot(px-x2, py-y2); // p2 handle
            return Math.min(d0, Math.min(d1, d2));
        }

        case GANN_FAN: {
            ChartDrawing.GannFan gf = (ChartDrawing.GannFan) d;
            // Hit on the pivot handle
            return (float)Math.hypot(px - timestampToPixelX(gf.startTs),
                    py - priceToPixelY(gf.startPrice));
        }

        case RISK_REWARD: {
            ChartDrawing.RiskReward rr = (ChartDrawing.RiskReward) d;

            // 1. Resolve structural anchor coordinates into screen-space pixel space
            float xStart = (float) timestampToPixelX(rr.startTs);
            float xEnd   = (float) timestampToPixelX(rr.endTs);

            float yEntry  = priceToPixelY(rr.entryPrice);
            float yTarget = priceToPixelY(rr.targetPrice);
            float yStop   = priceToPixelY(rr.stopPrice);

            // 2. Measure proximity to all 4 horizontal lines (Target, Entry, Stop Loss)
            float dTarget = ptSeg(px, py, xStart, yTarget, xEnd, yTarget);
            float dEntry  = ptSeg(px, py, xStart, yEntry,  xEnd, yEntry);
            float dStop   = ptSeg(px, py, xStart, yStop,   xEnd, yStop);

            // 3. Measure proximity to the vertical border paths (Left boundary & Right boundary)
            float yTopLine = Math.min(yTarget, yStop); // Canvas min Y is visually higher up
            float yBotLine = Math.max(yTarget, yStop); // Canvas max Y is visually lower down

            float dLeftBorder  = ptSeg(px, py, xStart, yTopLine, xStart, yBotLine);
            float dRightBorder = ptSeg(px, py, xEnd,   yTopLine, xEnd,   yBotLine);

            // 4. Return the shortest distance value to any of its structural borders
            float minH = Math.min(dTarget, Math.min(dEntry, dStop));
            float minV = Math.min(dLeftBorder, dRightBorder);

            return Math.min(minH, minV);
        }

        default: return Float.MAX_VALUE;
    }
}

private float ptSeg(float px,float py,float x1,float y1,float x2,float y2) {
    float dx=x2-x1,dy=y2-y1,lenSq=dx*dx+dy*dy;
    if (lenSq<1) return (float)Math.hypot(px-x1,py-y1);
    float t=Math.max(0,Math.min(1,((px-x1)*dx+(py-y1)*dy)/lenSq));
    return (float)Math.hypot(px-(x1+t*dx),py-(y1+t*dy));
}

// ═════════════════════════════════════════════════════════════════
// SNAP + COORD HELPERS
// ═════════════════════════════════════════════════════════════════
private double snapToOHLC(float py, long ts) {
    double raw = pixelToPrice(py);
    if (candles.isEmpty()) return raw;
    int idx = ChartDrawing.resolveIndex(ts, candles);
    if (idx<0||idx>=candles.size()) return raw;
    Candle c = candles.get(idx);
    double best=raw, bestDist=Double.MAX_VALUE;
    for (double v : new double[]{c.open,c.high,c.low,c.close}) {
        double dist=Math.abs(v-raw); if(dist<bestDist){bestDist=dist;best=v;}
    }
    double range=Math.abs(pixelToPrice(getContentRect().top)-pixelToPrice(getContentRect().bottom));
    return bestDist<range*0.03?best:raw;
}

private long pixelToTimestamp(float px) {
    return fractionalIndexToTimestamp(pixelToFractionalIndex(px));
}

private double timestampToPixelX(long ts) {
    if (candles.isEmpty()) return 0;
    Transformer tf = getTransformer(YAxis.AxisDependency.RIGHT);

    long t0 = candles.get(0).timestamp;
    long tN = candles.get(candles.size() - 1).timestamp;

    float fi; // fractional candle index to pass to the transformer

    long avgInterval = candles.size() < 2 ? 60L : (tN - t0) / Math.max(1, candles.size() - 1);

    if (ts <= t0) {
        // Extrapolate left so drawings anchored before the first visible candle render
        // off-screen rather than all collapsing to index 0 (which caused visual jumps).
        fi = avgInterval > 0 ? (float)(ts - t0) / avgInterval : 0f;
    } else if (ts >= tN) {
        // Extrapolate right past the last candle
        fi = (candles.size() - 1) + (float)(ts - tN) / Math.max(1, avgInterval);
    } else {
        // Binary-search for the surrounding candles, then interpolate
        int lo = 0, hi = candles.size() - 1;
        while (lo + 1 < hi) {
            int mid = (lo + hi) / 2;
            if (candles.get(mid).timestamp <= ts) lo = mid; else hi = mid;
        }
        long tLo = candles.get(lo).timestamp, tHi = candles.get(hi).timestamp;
        float frac = tHi == tLo ? 0f : (float)(ts - tLo) / (float)(tHi - tLo);
        fi = lo + frac;
    }

    float[] p = {fi, 0};
    tf.pointValuesToPixel(p);
    return p[0];
}

private double pixelToPrice(float py) {
    Transformer tf=getTransformer(YAxis.AxisDependency.RIGHT);
    float[] p={0,py}; tf.pixelsToValue(p); return p[1];
}

private float priceToPixelY(double price) {
    Transformer tf=getTransformer(YAxis.AxisDependency.RIGHT);
    float[] p={0,(float)price}; tf.pointValuesToPixel(p); return p[1];
}

// ═════════════════════════════════════════════════════════════════
// DRAWING FACTORY
// ═════════════════════════════════════════════════════════════════
@Nullable
private ChartDrawing placeOnePoint(DrawingManager.DrawingTool tool, long ts, double price) {
    switch (tool) {
        case HORIZONTAL_LINE:  return drawingManager.addHorizontalLine(price);
        case VERTICAL_LINE:    return drawingManager.addVerticalLine(ts);
        case TEXT_ANNOTATION:  return drawingManager.addTextAnnotation(ts,price,"Note");
        default:               return null;
    }
}

@Nullable
private ChartDrawing buildDrawing(DrawingManager.DrawingTool tool,
                                  long sTs,double sp,long eTs,double ep) {
    ChartDrawing.DrawingStyle s=drawingManager.buildActiveStyle();
    ChartDrawing.Source src=ChartDrawing.Source.USER;
    double hi=Math.max(sp,ep),lo=Math.min(sp,ep);
    switch (tool) {
        case TREND_LINE:        return new ChartDrawing.TrendLine(sTs,sp,eTs,ep,s,src);
        case RAY_LINE:          return new ChartDrawing.RayLine(sTs,sp,eTs,ep,s,src);
        case EXTENDED_LINE:     return new ChartDrawing.ExtendedLine(sTs,sp,eTs,ep,s,src);
        case LINEAR_REGRESSION: return new ChartDrawing.LinearRegression(sTs,eTs,s,src);
        case FIB_RETRACEMENT: { ChartDrawing.DrawingStyle ds=s.copy();ds.dashed=true;
            return new ChartDrawing.FibRetracement(sTs,hi,eTs,lo,ds,src); }
        case PRICE_RANGE: { ChartDrawing.DrawingStyle ds=s.copy();ds.filled=true;
            return new ChartDrawing.PriceRange(hi,lo,ds,src); }
        case RECTANGLE: { ChartDrawing.DrawingStyle ds=s.copy();ds.filled=true;
            return new ChartDrawing.Rectangle(sTs,sp,eTs,ep,ds,src); }
        case ELLIPSE: { ChartDrawing.DrawingStyle ds=s.copy();ds.filled=true;
            return new ChartDrawing.Ellipse(sTs,sp,eTs,ep,ds,src); }
        case ARROW:             return new ChartDrawing.Arrow(sTs,sp,eTs,ep,s,src);
        case PARALLEL_CHANNEL:  return new ChartDrawing.ParallelChannel(sTs,sp,eTs,ep,(sp+ep)/2,s,src);
        case GANN_FAN:          return new ChartDrawing.GannFan(sTs,sp,eTs,ep,s,src);
        case RISK_REWARD: {
            ChartDrawing.DrawingStyle ds = s.copy();
            ds.filled = true; // Risk/Reward zones use fill tracking
            boolean isLong = ep >= sp;
            double stopPrice = sp - (ep - sp); // Symmetrical 1:1 default risk profile layout
            return new ChartDrawing.RiskReward(sTs, eTs, sp, ep, stopPrice, isLong, ds, src);
        }
        default:                return null;
    }
}

private void finalizeDrawing(ChartDrawing d) {
    postInvalidate();
    if (drawingEventListener!=null) {
        drawingEventListener.onDrawingCreated(d);
        drawingEventListener.onDrawingsChanged();
    }
}
}