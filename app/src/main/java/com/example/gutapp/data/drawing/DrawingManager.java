package com.example.gutapp.data.drawing;

import androidx.annotation.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * DrawingManager — owns all ChartDrawing instances for one chart session.
 *
 * Anchors are timestamps so drawings survive timeframe switches correctly.
 * Layer ordering: drawings are drawn in two passes inside DrawingChart.onDraw:
 *   1. Layer.BEHIND_CANDLES  — rendered before MPAndroidChart paints candles/lines
 *   2. [MPAndroidChart paints candles + indicators]
 *   3. Layer.ABOVE_CANDLES   — rendered after, on top of everything
 * Users can change a drawing's layer at runtime from the toolbar.
 */
public class DrawingManager {

    private static final int MAX_HISTORY = 50;

    private final LinkedHashMap<String, ChartDrawing> drawings = new LinkedHashMap<>();
    private final Deque<List<ChartDrawing>> undoStack = new ArrayDeque<>();
    private final Deque<List<ChartDrawing>> redoStack = new ArrayDeque<>();

    @Nullable private DrawingTool activeTool = null;
    @Nullable private String selectedId = null;

    private int   activeColor   = 0xFFECEFF1;
    private float activeWidth   = 1f;
    private boolean activeDashed = false;

    public enum DrawingTool {
        HORIZONTAL_LINE, TREND_LINE, RAY_LINE, EXTENDED_LINE, VERTICAL_LINE,
        LINEAR_REGRESSION, FIB_RETRACEMENT,
        PRICE_RANGE, RECTANGLE, ELLIPSE,
        TEXT_ANNOTATION, ARROW,
        PARALLEL_CHANNEL, PITCHFORK, GANN_FAN,
        RISK_REWARD
    }

    // ── Tool state ───────────────────────────────────────────────────
    public void setActiveTool(@Nullable DrawingTool tool) { this.activeTool = tool; }
    @Nullable public DrawingTool getActiveTool() { return activeTool; }
    public boolean hasActiveTool() { return activeTool != null; }

    public void setActiveColor(int color) { this.activeColor = color; }
    public int  getActiveColor() { return activeColor; }
    public void setActiveWidth(float width) { this.activeWidth = width; }
    public float getActiveWidth() { return activeWidth; }
    public void setActiveDashed(boolean dashed) { this.activeDashed = dashed; }
    public boolean isActiveDashed() { return activeDashed; }

    public ChartDrawing.DrawingStyle buildActiveStyle() {
        ChartDrawing.DrawingStyle s = new ChartDrawing.DrawingStyle(activeColor, activeWidth, activeDashed);
        s.fillColor = android.graphics.Color.argb(50,
                android.graphics.Color.red(activeColor),
                android.graphics.Color.green(activeColor),
                android.graphics.Color.blue(activeColor));
        return s;
    }

    // ── Selection ────────────────────────────────────────────────────
    public void select(String id) {
        if (selectedId != null) { ChartDrawing p = drawings.get(selectedId); if (p != null) p.selected = false; }
        selectedId = id;
        ChartDrawing d = drawings.get(id);
        if (d != null) d.selected = true;
    }
    public void clearSelection() {
        if (selectedId != null) { ChartDrawing d = drawings.get(selectedId); if (d != null) d.selected = false; selectedId = null; }
    }
    @Nullable public String getSelectedId() { return selectedId; }
    @Nullable public ChartDrawing getSelected() { return selectedId != null ? drawings.get(selectedId) : null; }

    // ── Layer controls ───────────────────────────────────────────────
    /** Move the selected drawing to front (above candles). */
    public void bringSelectedToFront() {
        ChartDrawing d = getSelected();
        if (d != null) d.layer = ChartDrawing.Layer.ABOVE_CANDLES;
    }
    /** Move the selected drawing to back (behind candles). */
    public void sendSelectedToBack() {
        ChartDrawing d = getSelected();
        if (d != null) d.layer = ChartDrawing.Layer.BEHIND_CANDLES;
    }
    /** Toggle layer of selected drawing. Returns new layer. */
    public ChartDrawing.Layer toggleSelectedLayer() {
        ChartDrawing d = getSelected();
        if (d == null) return ChartDrawing.Layer.BEHIND_CANDLES;
        d.layer = d.layer == ChartDrawing.Layer.BEHIND_CANDLES
                ? ChartDrawing.Layer.ABOVE_CANDLES : ChartDrawing.Layer.BEHIND_CANDLES;
        return d.layer;
    }

    // ── CRUD ─────────────────────────────────────────────────────────
    public <T extends ChartDrawing> T add(T drawing) {
        if (drawing.source == ChartDrawing.Source.USER) pushUndoCheckpoint();
        drawings.put(drawing.getInstanceId(), drawing);
        return drawing;
    }
    public void remove(String instanceId) {
        ChartDrawing d = drawings.get(instanceId);
        if (d != null && d.source == ChartDrawing.Source.USER) pushUndoCheckpoint();
        drawings.remove(instanceId);
        if (instanceId.equals(selectedId)) selectedId = null;
    }
    public void removeSelected() { if (selectedId != null) remove(selectedId); }

    public void clearUserDrawings() {
        pushUndoCheckpoint();
        drawings.entrySet().removeIf(e -> e.getValue().source == ChartDrawing.Source.USER);
        selectedId = null;
    }
    /** Clear without pushing to undo stack — used during timeframe-switch reloads. */
    public void clearUserDrawingsSilent() {
        drawings.entrySet().removeIf(e -> e.getValue().source == ChartDrawing.Source.USER);
        selectedId = null;
        undoStack.clear(); redoStack.clear();
    }
    public void clearIndicatorDrawings() {
        drawings.entrySet().removeIf(e -> e.getValue().source == ChartDrawing.Source.INDICATOR);
    }
    public void clearAll() {
        pushUndoCheckpoint(); drawings.clear(); selectedId = null;
    }

    public List<ChartDrawing> getAll() { return new ArrayList<>(drawings.values()); }
    public List<ChartDrawing> getUserDrawings() {
        List<ChartDrawing> l = new ArrayList<>();
        for (ChartDrawing d : drawings.values()) if (d.source == ChartDrawing.Source.USER) l.add(d);
        return l;
    }
    /** All drawings in a specific layer, ordered by insertion. */
    public List<ChartDrawing> getByLayer(ChartDrawing.Layer layer) {
        List<ChartDrawing> l = new ArrayList<>();
        for (ChartDrawing d : drawings.values()) if (d.layer == layer) l.add(d);
        return l;
    }
    @Nullable public ChartDrawing get(String id) { return drawings.get(id); }
    public boolean isEmpty() { return drawings.isEmpty(); }
    public int size() { return drawings.size(); }

    // ── Undo / Redo ──────────────────────────────────────────────────
    private void pushUndoCheckpoint() {
        List<ChartDrawing> snap = getUserDrawings();
        undoStack.push(snap);
        if (undoStack.size() > MAX_HISTORY) {
            List<List<ChartDrawing>> t = new ArrayList<>(undoStack);
            t.remove(t.size() - 1);
            undoStack.clear();
            for (int i = t.size() - 1; i >= 0; i--) undoStack.push(t.get(i));
        }
        redoStack.clear();
    }
    public boolean undo() {
        if (undoStack.isEmpty()) return false;
        redoStack.push(getUserDrawings());
        List<ChartDrawing> prev = undoStack.pop();
        clearIndicatorless();
        for (ChartDrawing d : prev) drawings.put(d.getInstanceId(), d);
        clearSelection(); return true;
    }
    public boolean redo() {
        if (redoStack.isEmpty()) return false;
        undoStack.push(getUserDrawings());
        List<ChartDrawing> next = redoStack.pop();
        clearIndicatorless();
        for (ChartDrawing d : next) drawings.put(d.getInstanceId(), d);
        clearSelection(); return true;
    }
    public boolean canUndo() { return !undoStack.isEmpty(); }
    public boolean canRedo() { return !redoStack.isEmpty(); }
    private void clearIndicatorless() {
        drawings.entrySet().removeIf(e -> e.getValue().source == ChartDrawing.Source.USER);
    }

    // ── Factory methods (all take timestamps) ────────────────────────
    public ChartDrawing.HorizontalLine addHorizontalLine(double price) {
        return add(new ChartDrawing.HorizontalLine(price, buildActiveStyle(), ChartDrawing.Source.USER));
    }
    public ChartDrawing.TrendLine addTrendLine(long sTs, double sp, long eTs, double ep) {
        return add(new ChartDrawing.TrendLine(sTs, sp, eTs, ep, buildActiveStyle(), ChartDrawing.Source.USER));
    }
    public ChartDrawing.RayLine addRayLine(long sTs, double sp, long aTs, double ap) {
        return add(new ChartDrawing.RayLine(sTs, sp, aTs, ap, buildActiveStyle(), ChartDrawing.Source.USER));
    }
    public ChartDrawing.ExtendedLine addExtendedLine(long sTs, double sp, long eTs, double ep) {
        return add(new ChartDrawing.ExtendedLine(sTs, sp, eTs, ep, buildActiveStyle(), ChartDrawing.Source.USER));
    }
    public ChartDrawing.VerticalLine addVerticalLine(long ts) {
        ChartDrawing.DrawingStyle s = buildActiveStyle(); s.dashed = true;
        return add(new ChartDrawing.VerticalLine(ts, s, ChartDrawing.Source.USER));
    }
    public ChartDrawing.LinearRegression addLinearRegression(long sTs, long eTs) {
        return add(new ChartDrawing.LinearRegression(sTs, eTs, buildActiveStyle(), ChartDrawing.Source.USER));
    }
    public ChartDrawing.FibRetracement addFibRetracement(long sTs, double hp, long eTs, double lp) {
        ChartDrawing.DrawingStyle s = buildActiveStyle(); s.dashed = true;
        return add(new ChartDrawing.FibRetracement(sTs, hp, eTs, lp, s, ChartDrawing.Source.USER));
    }
    public ChartDrawing.PriceRange addPriceRange(double hi, double lo) {
        ChartDrawing.DrawingStyle s = buildActiveStyle(); s.filled = true;
        return add(new ChartDrawing.PriceRange(hi, lo, s, ChartDrawing.Source.USER));
    }
    public ChartDrawing.Rectangle addRectangle(long sTs, double sp, long eTs, double ep) {
        ChartDrawing.DrawingStyle s = buildActiveStyle(); s.filled = true;
        return add(new ChartDrawing.Rectangle(sTs, sp, eTs, ep, s, ChartDrawing.Source.USER));
    }
    public ChartDrawing.Ellipse addEllipse(long sTs, double sp, long eTs, double ep) {
        ChartDrawing.DrawingStyle s = buildActiveStyle(); s.filled = true;
        return add(new ChartDrawing.Ellipse(sTs, sp, eTs, ep, s, ChartDrawing.Source.USER));
    }
    public ChartDrawing.TextAnnotation addTextAnnotation(long ts, double price, String text) {
        return add(new ChartDrawing.TextAnnotation(ts, price, text, buildActiveStyle(), ChartDrawing.Source.USER));
    }
    public ChartDrawing.Arrow addArrow(long sTs, double sp, long eTs, double ep) {
        return add(new ChartDrawing.Arrow(sTs, sp, eTs, ep, buildActiveStyle(), ChartDrawing.Source.USER));
    }
    public ChartDrawing.ParallelChannel addParallelChannel(long sTs, double sp, long eTs, double ep, double mid) {
        return add(new ChartDrawing.ParallelChannel(sTs, sp, eTs, ep, mid, buildActiveStyle(), ChartDrawing.Source.USER));
    }
    public ChartDrawing.Pitchfork addPitchfork(long p0t, double p0p, long p1t, double p1p, long p2t, double p2p) {
        return add(new ChartDrawing.Pitchfork(p0t, p0p, p1t, p1p, p2t, p2p, buildActiveStyle(), ChartDrawing.Source.USER));
    }
    public ChartDrawing.GannFan addGannFan(long sTs, double sp, long eTs, double ep) {
        return add(new ChartDrawing.GannFan(sTs, sp, eTs, ep, buildActiveStyle(), ChartDrawing.Source.USER));
    }
    public ChartDrawing.RiskReward addRiskReward(long sTs, long eTs, double entry, double target, double stop, boolean isLong) {
        return add(new ChartDrawing.RiskReward(sTs, eTs, entry, target, stop, isLong, buildActiveStyle(), ChartDrawing.Source.USER));
    }

    // ── Tool helpers ─────────────────────────────────────────────────
    public static boolean isTwoPointTool(DrawingTool tool) {
        switch (tool) {
            case TREND_LINE: case RAY_LINE: case EXTENDED_LINE:
            case LINEAR_REGRESSION: case FIB_RETRACEMENT:
            case PRICE_RANGE: case RECTANGLE: case ELLIPSE:
            case ARROW: case PARALLEL_CHANNEL: case GANN_FAN:
            case RISK_REWARD:
                return true;
            default: return false;
        }
    }
    public static boolean isThreePointTool(DrawingTool tool) { return tool == DrawingTool.PITCHFORK; }
}