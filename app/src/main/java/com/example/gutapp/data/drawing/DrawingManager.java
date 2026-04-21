package com.example.gutapp.data.drawing;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * DrawingManager — owns all ChartDrawing instances for one chart.
 *
 * Thread-safety note: All mutations happen on the main thread (from
 * DrawingChart or StockChart.updateChartData). No locking needed.
 *
 * Separation of concerns:
 *  - DrawingManager: the data layer (stores, queries, removes drawings)
 *  - DrawingRenderer: the rendering layer (paints onto the Canvas)
 *  - DrawingChart:    the View layer (handles touch, delegates to both)
 */
public class DrawingManager {

    /** All active drawings, keyed by instanceId, insertion-ordered */
    private final LinkedHashMap<String, ChartDrawing> drawings = new LinkedHashMap<>();

    /** Active tool mode. NULL = no drawing tool active (normal pan/zoom). */
    private DrawingTool activeTool = null;

    public enum DrawingTool {
        HORIZONTAL_LINE,
        TREND_LINE,
        RAY_LINE,
        VERTICAL_LINE,
        LINEAR_REGRESSION,
        FIB_RETRACEMENT,
        PRICE_RANGE
    }

    // ── Tool mode ────────────────────────────────────────────────────

    public void setActiveTool(@androidx.annotation.Nullable DrawingTool tool) {
        this.activeTool = tool;
    }

    @androidx.annotation.Nullable
    public DrawingTool getActiveTool() { return activeTool; }

    public boolean hasActiveTool() { return activeTool != null; }

    // ── CRUD ──────────────────────────────────────────────────────────

    /** Add any drawing and return it */
    public <T extends ChartDrawing> T add(T drawing) {
        drawings.put(drawing.getInstanceId(), drawing);
        return drawing;
    }

    /** Remove a drawing by instanceId. Safe to call if id doesn't exist. */
    public void remove(String instanceId) {
        drawings.remove(instanceId);
    }

    /** Remove all user-drawn drawings (keep indicator drawings) */
    public void clearUserDrawings() {
        drawings.entrySet().removeIf(e -> e.getValue().source == ChartDrawing.Source.USER);
    }

    /** Remove all indicator drawings (called before re-computing indicators) */
    public void clearIndicatorDrawings() {
        drawings.entrySet().removeIf(e -> e.getValue().source == ChartDrawing.Source.INDICATOR);
    }

    /** Remove everything */
    public void clearAll() {
        drawings.clear();
    }

    /** All drawings in insertion order */
    public List<ChartDrawing> getAll() {
        return new ArrayList<>(drawings.values());
    }

    /** Drawings of a specific type */
    public List<ChartDrawing> getByType(ChartDrawing.DrawingType type) {
        List<ChartDrawing> result = new ArrayList<>();
        for (ChartDrawing d : drawings.values()) if (d.getType() == type) result.add(d);
        return result;
    }

    @androidx.annotation.Nullable
    public ChartDrawing get(String instanceId) {
        return drawings.get(instanceId);
    }

    public boolean isEmpty() { return drawings.isEmpty(); }
    public int size()        { return drawings.size(); }

    // ── Convenience factory methods (user-source) ─────────────────────

    public ChartDrawing.HorizontalLine addHorizontalLine(double price, int color) {
        return add(new ChartDrawing.HorizontalLine(
                price,
                ChartDrawing.DrawingStyle.solid(color),
                ChartDrawing.Source.USER));
    }

    public ChartDrawing.TrendLine addTrendLine(int startIdx, double startPrice,
                                               int endIdx,   double endPrice,
                                               int color) {
        return add(new ChartDrawing.TrendLine(
                startIdx, startPrice, endIdx, endPrice,
                ChartDrawing.DrawingStyle.solid(color),
                ChartDrawing.Source.USER));
    }

    public ChartDrawing.RayLine addRayLine(int startIdx, double startPrice,
                                           int anchorIdx, double anchorPrice,
                                           int color) {
        return add(new ChartDrawing.RayLine(
                startIdx, startPrice, anchorIdx, anchorPrice,
                ChartDrawing.DrawingStyle.solid(color),
                ChartDrawing.Source.USER));
    }

    public ChartDrawing.VerticalLine addVerticalLine(int candleIndex, int color) {
        return add(new ChartDrawing.VerticalLine(
                candleIndex,
                ChartDrawing.DrawingStyle.dashed(color),
                ChartDrawing.Source.USER));
    }

    public ChartDrawing.LinearRegression addLinearRegression(int start, int end, int color) {
        return add(new ChartDrawing.LinearRegression(
                start, end,
                ChartDrawing.DrawingStyle.solid(color),
                ChartDrawing.Source.USER));
    }

    public ChartDrawing.FibRetracement addFibRetracement(int startIdx, double high,
                                                         int endIdx,   double low,
                                                         int color) {
        return add(new ChartDrawing.FibRetracement(
                startIdx, high, endIdx, low,
                ChartDrawing.DrawingStyle.dashed(color),
                ChartDrawing.Source.USER));
    }

    public ChartDrawing.PriceRange addPriceRange(double high, double low, int color) {
        return add(new ChartDrawing.PriceRange(
                high, low,
                ChartDrawing.DrawingStyle.solid(color).withFill(true),
                ChartDrawing.Source.USER));
    }
}