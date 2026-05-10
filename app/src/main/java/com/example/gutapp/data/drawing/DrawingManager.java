package com.example.gutapp.data.drawing;

import androidx.annotation.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * DrawingManager — owns all ChartDrawing instances for one chart.
 *
 * Adds over the original:
 *  - Full Undo / Redo stack (up to MAX_HISTORY steps)
 *  - Selection tracking (selectedId)
 *  - Extended DrawingTool enum covering all new drawing types
 *  - Snap-to-price helper
 *  - Active tool color / width state (set from toolbar, used by DrawingChart)
 */
public class DrawingManager {

    private static final int MAX_HISTORY = 50;

    /** All active drawings, keyed by instanceId, insertion-ordered. */
    private final LinkedHashMap<String, ChartDrawing> drawings = new LinkedHashMap<>();

    /** Undo stack: each entry is the list of USER drawing snapshots before the action. */
    private final Deque<List<ChartDrawing>> undoStack = new ArrayDeque<>();
    private final Deque<List<ChartDrawing>> redoStack = new ArrayDeque<>();

    /** Active tool. NULL = pan/zoom mode. */
    private DrawingTool activeTool = null;

    /** ID of the currently selected drawing (null = nothing selected). */
    @Nullable private String selectedId = null;

    /** Active drawing colour / width applied when creating new drawings. */
    private int   activeColor     = 0xFFECEFF1;
    private float activeWidth     = 1.5f;
    private boolean activeDashed  = false;

    public enum DrawingTool {
        // Lines
        HORIZONTAL_LINE,
        TREND_LINE,
        RAY_LINE,
        EXTENDED_LINE,
        VERTICAL_LINE,
        // Regression / statistical
        LINEAR_REGRESSION,
        // Fibonacci
        FIB_RETRACEMENT,
        // Shapes / zones
        PRICE_RANGE,
        RECTANGLE,
        ELLIPSE,
        // Annotation
        TEXT_ANNOTATION,
        ARROW,
        // Channels
        PARALLEL_CHANNEL,
        // Advanced
        PITCHFORK,
        GANN_FAN
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

    /** Build a style from current active settings. */
    public ChartDrawing.DrawingStyle buildActiveStyle() {
        ChartDrawing.DrawingStyle s = new ChartDrawing.DrawingStyle(activeColor, activeWidth, activeDashed);
        s.fillColor = android.graphics.Color.argb(
                50,
                android.graphics.Color.red(activeColor),
                android.graphics.Color.green(activeColor),
                android.graphics.Color.blue(activeColor));
        return s;
    }

    // ── Selection ────────────────────────────────────────────────────

    public void select(String id) {
        if (selectedId != null) {
            ChartDrawing prev = drawings.get(selectedId);
            if (prev != null) prev.selected = false;
        }
        selectedId = id;
        ChartDrawing d = drawings.get(id);
        if (d != null) d.selected = true;
    }

    public void clearSelection() {
        if (selectedId != null) {
            ChartDrawing d = drawings.get(selectedId);
            if (d != null) d.selected = false;
            selectedId = null;
        }
    }

    @Nullable public String getSelectedId() { return selectedId; }

    @Nullable public ChartDrawing getSelected() {
        return selectedId != null ? drawings.get(selectedId) : null;
    }

    // ── CRUD (with undo support) ─────────────────────────────────────

    /** Add a drawing and push an undo checkpoint. */
    public <T extends ChartDrawing> T add(T drawing) {
        if (drawing.source == ChartDrawing.Source.USER) pushUndoCheckpoint();
        drawings.put(drawing.getInstanceId(), drawing);
        return drawing;
    }

    /** Remove by id and push undo checkpoint if it was a user drawing. */
    public void remove(String instanceId) {
        ChartDrawing d = drawings.get(instanceId);
        if (d != null && d.source == ChartDrawing.Source.USER) pushUndoCheckpoint();
        drawings.remove(instanceId);
        if (instanceId.equals(selectedId)) selectedId = null;
    }

    /** Remove currently selected drawing. */
    public void removeSelected() {
        if (selectedId != null) remove(selectedId);
    }

    public void clearUserDrawings() {
        pushUndoCheckpoint();
        drawings.entrySet().removeIf(e -> e.getValue().source == ChartDrawing.Source.USER);
        selectedId = null;
    }

    /** Clear user drawings without pushing an undo checkpoint (used during reloads). */
    public void clearUserDrawingsSilent() {
        drawings.entrySet().removeIf(e -> e.getValue().source == ChartDrawing.Source.USER);
        selectedId = null;
        undoStack.clear();
        redoStack.clear();
    }

    public void clearIndicatorDrawings() {
        drawings.entrySet().removeIf(e -> e.getValue().source == ChartDrawing.Source.INDICATOR);
    }

    public void clearAll() {
        pushUndoCheckpoint();
        drawings.clear();
        selectedId = null;
    }

    public List<ChartDrawing> getAll() { return new ArrayList<>(drawings.values()); }

    public List<ChartDrawing> getUserDrawings() {
        List<ChartDrawing> list = new ArrayList<>();
        for (ChartDrawing d : drawings.values())
            if (d.source == ChartDrawing.Source.USER) list.add(d);
        return list;
    }

    public List<ChartDrawing> getByType(ChartDrawing.DrawingType type) {
        List<ChartDrawing> result = new ArrayList<>();
        for (ChartDrawing d : drawings.values()) if (d.getType() == type) result.add(d);
        return result;
    }

    @Nullable public ChartDrawing get(String instanceId) { return drawings.get(instanceId); }
    public boolean isEmpty() { return drawings.isEmpty(); }
    public int size() { return drawings.size(); }

    // ── Undo / Redo ──────────────────────────────────────────────────

    private void pushUndoCheckpoint() {
        List<ChartDrawing> snap = getUserDrawings();
        undoStack.push(snap);
        if (undoStack.size() > MAX_HISTORY) {
            // trim oldest
            List<List<ChartDrawing>> trimmed = new ArrayList<>(undoStack);
            trimmed.remove(trimmed.size() - 1);
            undoStack.clear();
            for (int i = trimmed.size() - 1; i >= 0; i--) undoStack.push(trimmed.get(i));
        }
        redoStack.clear();
    }

    /** Undo last user drawing action. Returns true if state changed. */
    public boolean undo() {
        if (undoStack.isEmpty()) return false;
        // Save current state to redo
        redoStack.push(getUserDrawings());
        // Restore previous state
        List<ChartDrawing> prev = undoStack.pop();
        clearIndicatorless();
        for (ChartDrawing d : prev) drawings.put(d.getInstanceId(), d);
        clearSelection();
        return true;
    }

    /** Redo previously undone action. Returns true if state changed. */
    public boolean redo() {
        if (redoStack.isEmpty()) return false;
        undoStack.push(getUserDrawings());
        List<ChartDrawing> next = redoStack.pop();
        clearIndicatorless();
        for (ChartDrawing d : next) drawings.put(d.getInstanceId(), d);
        clearSelection();
        return true;
    }

    public boolean canUndo() { return !undoStack.isEmpty(); }
    public boolean canRedo() { return !redoStack.isEmpty(); }

    private void clearIndicatorless() {
        drawings.entrySet().removeIf(e -> e.getValue().source == ChartDrawing.Source.USER);
    }

    // ── Convenience factory methods ──────────────────────────────────

    public ChartDrawing.HorizontalLine addHorizontalLine(double price) {
        return add(new ChartDrawing.HorizontalLine(price, buildActiveStyle(), ChartDrawing.Source.USER));
    }

    public ChartDrawing.TrendLine addTrendLine(int si, double sp, int ei, double ep) {
        return add(new ChartDrawing.TrendLine(si, sp, ei, ep, buildActiveStyle(), ChartDrawing.Source.USER));
    }

    public ChartDrawing.RayLine addRayLine(int si, double sp, int ai, double ap) {
        return add(new ChartDrawing.RayLine(si, sp, ai, ap, buildActiveStyle(), ChartDrawing.Source.USER));
    }

    public ChartDrawing.ExtendedLine addExtendedLine(int si, double sp, int ei, double ep) {
        return add(new ChartDrawing.ExtendedLine(si, sp, ei, ep, buildActiveStyle(), ChartDrawing.Source.USER));
    }

    public ChartDrawing.VerticalLine addVerticalLine(int idx) {
        ChartDrawing.DrawingStyle s = buildActiveStyle(); s.dashed = true;
        return add(new ChartDrawing.VerticalLine(idx, s, ChartDrawing.Source.USER));
    }

    public ChartDrawing.LinearRegression addLinearRegression(int si, int ei) {
        return add(new ChartDrawing.LinearRegression(si, ei, buildActiveStyle(), ChartDrawing.Source.USER));
    }

    public ChartDrawing.FibRetracement addFibRetracement(int si, double hp, int ei, double lp) {
        ChartDrawing.DrawingStyle s = buildActiveStyle(); s.dashed = true;
        return add(new ChartDrawing.FibRetracement(si, hp, ei, lp, s, ChartDrawing.Source.USER));
    }

    public ChartDrawing.PriceRange addPriceRange(double hi, double lo) {
        ChartDrawing.DrawingStyle s = buildActiveStyle(); s.filled = true;
        return add(new ChartDrawing.PriceRange(hi, lo, s, ChartDrawing.Source.USER));
    }

    public ChartDrawing.Rectangle addRectangle(int si, double sp, int ei, double ep) {
        ChartDrawing.DrawingStyle s = buildActiveStyle(); s.filled = true;
        return add(new ChartDrawing.Rectangle(si, sp, ei, ep, s, ChartDrawing.Source.USER));
    }

    public ChartDrawing.Ellipse addEllipse(int si, double sp, int ei, double ep) {
        ChartDrawing.DrawingStyle s = buildActiveStyle(); s.filled = true;
        return add(new ChartDrawing.Ellipse(si, sp, ei, ep, s, ChartDrawing.Source.USER));
    }

    public ChartDrawing.TextAnnotation addTextAnnotation(int idx, double price, String text) {
        return add(new ChartDrawing.TextAnnotation(idx, price, text, buildActiveStyle(), ChartDrawing.Source.USER));
    }

    public ChartDrawing.Arrow addArrow(int si, double sp, int ei, double ep) {
        return add(new ChartDrawing.Arrow(si, sp, ei, ep, buildActiveStyle(), ChartDrawing.Source.USER));
    }

    public ChartDrawing.ParallelChannel addParallelChannel(int si, double sp, int ei, double ep, double mid) {
        return add(new ChartDrawing.ParallelChannel(si, sp, ei, ep, mid, buildActiveStyle(), ChartDrawing.Source.USER));
    }

    public ChartDrawing.Pitchfork addPitchfork(int p0i, double p0p, int p1i, double p1p,
                                               int p2i, double p2p) {
        return add(new ChartDrawing.Pitchfork(p0i, p0p, p1i, p1p, p2i, p2p,
                buildActiveStyle(), ChartDrawing.Source.USER));
    }

    public ChartDrawing.GannFan addGannFan(int si, double sp, int ei, double ep) {
        return add(new ChartDrawing.GannFan(si, sp, ei, ep, buildActiveStyle(), ChartDrawing.Source.USER));
    }

    // ── Tool category helpers ────────────────────────────────────────

    public static boolean isTwoPointTool(DrawingTool tool) {
        switch (tool) {
            case TREND_LINE: case RAY_LINE: case EXTENDED_LINE:
            case LINEAR_REGRESSION: case FIB_RETRACEMENT:
            case PRICE_RANGE: case RECTANGLE: case ELLIPSE:
            case ARROW: case PARALLEL_CHANNEL: case GANN_FAN:
                return true;
            default:
                return false;
        }
    }

    public static boolean isThreePointTool(DrawingTool tool) {
        return tool == DrawingTool.PITCHFORK;
    }
}