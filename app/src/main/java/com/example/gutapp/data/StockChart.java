package com.example.gutapp.data;

import static com.example.gutapp.ui.ChartActivity.CHART_LOG_TAG;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.core.widget.NestedScrollView;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer;

import com.example.gutapp.data.drawing.ChartDrawing;
import com.example.gutapp.data.indicators.Indicator;
import com.example.gutapp.data.indicators.IndicatorSession;
import com.example.gutapp.data.models.Candle;
import com.example.gutapp.data.models.PriceChunk;
import com.example.gutapp.database.StockDataHelper;
import com.example.gutapp.session.DataType;
import com.example.gutapp.session.NetworkClient;
import com.example.gutapp.session.SessionCallback;
import com.example.gutapp.ui.chart.DrawingChart;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.AxisBase;
import com.github.mikephil.charting.components.Description;
import com.github.mikephil.charting.components.LimitLine;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.data.CandleData;
import com.github.mikephil.charting.data.CandleDataSet;
import com.github.mikephil.charting.data.CandleEntry;
import com.github.mikephil.charting.data.CombinedData;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.formatter.ValueFormatter;
import com.github.mikephil.charting.utils.Transformer;
import com.github.mikephil.charting.highlight.Highlight;
import com.github.mikephil.charting.listener.ChartTouchListener;
import com.github.mikephil.charting.listener.OnChartGestureListener;
import com.github.mikephil.charting.listener.OnChartValueSelectedListener;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

import lombok.Getter;
import lombok.Setter;

/**
 * StockChart — data/rendering controller for the main price chart.
 *
 * ── Key change: DrawingChart ────────────────────────────────────────
 * The constructor now accepts a DrawingChart instead of a CombinedChart.
 * DrawingChart extends CombinedChart, so all existing MPAndroidChart APIs
 * work identically. The added drawing layer is transparent to this class.
 *
 * After each updateChartData(), StockChart:
 *  1. Collects all ChartDrawing objects from indicator results
 *  2. Passes them to chart.replaceIndicatorDrawings() so they render on canvas
 *  3. Passes the candle list to chart.setCandles() for coordinate conversion
 */
public class StockChart implements SessionCallback {

    public enum ChartType { CANDLE, BAR, LINE }

    // ── Colors ────────────────────────────────────────────────────────
    private static final int COLOR_UP          = Color.parseColor("#00FF88");
    private static final int COLOR_DOWN        = Color.parseColor("#FF4444");
    private static final int COLOR_LINE        = Color.parseColor("#2196F3");
    private static final int COLOR_LINE_FILL   = Color.argb(38, 33, 150, 243);
    private static final int COLOR_VOL_UP      = Color.argb(77, 38, 166, 154);
    private static final int COLOR_VOL_DOWN    = Color.argb(77, 239, 83, 80);
    private static final int COLOR_VOL_NEUTRAL = Color.argb(60, 120, 144, 156);
    private static final int COLOR_BACKGROUND  = Color.parseColor("#121111");
    private static final int COLOR_AXIS_TEXT   = Color.parseColor("#78909C");
    private static final int COLOR_GRID        = Color.argb(20, 255, 255, 255);
    private static final int COLOR_HIGHLIGHT   = Color.parseColor("#FFFFFF");
    private static final int COLOR_PRICE_LINE  = Color.argb(200, 255, 255, 255);

    // ── Core fields ───────────────────────────────────────────────────
    /** The chart view — DrawingChart (extends CombinedChart) */
    private final DrawingChart chart;
    private final Context activityContext;

    @Setter @Nullable private LinearLayout subChartsContainer = null;
    @Setter @Nullable private NestedScrollView subChartsScroller = null;

    private final LinkedHashMap<String, LineChart> subChartViews = new LinkedHashMap<>();

    private final CopyOnWriteArrayList<Candle> allCandles = new CopyOnWriteArrayList<>();
    private final Set<Integer> reqIds = ConcurrentHashMap.<Integer>newKeySet();

    @Setter @Nullable private IndicatorSession indicatorSession = null;

    // ── Indicator tap-to-select ─────────────────────────────────────────
    /** instanceId → its overlay polyline(s), rebuilt every render; used for hit-testing taps. */
    private final LinkedHashMap<String, List<LineDataSet>> overlayLinesByInstance = new LinkedHashMap<>();
    /** Currently selected overlay indicator (highlighted + bound to the edit panel), or null. */
    @Nullable private String selectedIndicatorId = null;
    private static final float INDICATOR_HIT_PX = 32f;

    private StockDataHelper.Timeframe interval;
    private ChartType currentChartType = ChartType.CANDLE;

    private final AtomicBoolean isUpdatePending = new AtomicBoolean(false);
    private volatile boolean done = false;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final List<Candle> streamBuffer = new ArrayList<>();

    @Nullable private SessionCallback chainedListener = null;
    private MutableLiveData<Double> currentPrice = new MutableLiveData<>(0.0);
    @Nullable private LimitLine currentPriceLine = null;

    /** Fired every time a fresh candle batch is applied to the chart (including timeframe switches). */
    @Nullable private Runnable candlesReadyCallback = null;

    public void setCandlesReadyCallback(@Nullable Runnable r) { this.candlesReadyCallback = r; }

    // ── Viewport state ─────────────────────────────────────────────────
    private boolean isPinnedToRight    = true;
    private float   savedLowestX       = -1f;
    private float   savedHighestX      = -1f;
    private boolean initialViewApplied = false;

    // ── Constructor ────────────────────────────────────────────────────
    /**
     * @param chart The DrawingChart view from the inflated layout.
     *              In ChartActivity: DrawingChart chart = findViewById(R.id.stockChart);
     */
    public StockChart(DrawingChart chart, Context context) {
        this.chart          = chart;
        this.activityContext = context;
        currentPrice.observeForever(new Observer<Double>() {
            @Override
            public void onChanged(Double aDouble) {
                mainHandler.postDelayed(() -> {
                    synchronized(allCandles) {
                        if (chainedListener != null && !allCandles.isEmpty())
                            chainedListener.onDataReceived(DataType.MARKET_DATA,allCandles.get(allCandles.size()-1).close);
                    }
                }, 50);
            }
        });

    }

    // ── Public API ─────────────────────────────────────────────────────

    public void setChartType(ChartType type) {
        this.currentChartType = type;
        triggerChartUpdate(true);
    }
    public ChartType getChartType() { return currentChartType; }

    public void applyIndicators() { triggerChartUpdate(false); }

    /** Expose the DrawingChart so ChartActivity can access drawing tools */
    public DrawingChart getDrawingChart() { return chart; }

    // ── Indicator tap-to-select ─────────────────────────────────────────
    /**
     * Hit-test a tap (view pixels) against every overlay indicator's polyline.
     * Returns the nearest instance id within {@link #INDICATOR_HIT_PX}, or null.
     * Uses the RIGHT-axis transformer — the same space overlay lines render in.
     */
    @Nullable
    public String pickIndicatorAt(float px, float py) {
        if (overlayLinesByInstance.isEmpty()) return null;
        Transformer tf = chart.getTransformer(YAxis.AxisDependency.RIGHT);
        String best = null;
        float bestDist = INDICATOR_HIT_PX;
        float[] buf = new float[2];
        for (Map.Entry<String, List<LineDataSet>> e : overlayLinesByInstance.entrySet()) {
            for (LineDataSet set : e.getValue()) {
                int n = set.getEntryCount();
                float prevX = 0, prevY = 0; boolean hasPrev = false;
                for (int i = 0; i < n; i++) {
                    Entry en = set.getEntryForIndex(i);
                    buf[0] = en.getX(); buf[1] = en.getY();
                    tf.pointValuesToPixel(buf);
                    float x = buf[0], y = buf[1];
                    if (hasPrev) {
                        float d = ptSeg(px, py, prevX, prevY, x, y);
                        if (d < bestDist) { bestDist = d; best = e.getKey(); }
                    }
                    prevX = x; prevY = y; hasPrev = true;
                }
            }
        }
        return best;
    }

    /** Mark an overlay indicator as selected (highlights its line) and re-render. */
    public void setSelectedIndicator(@Nullable String instanceId) {
        this.selectedIndicatorId = instanceId;
        applyIndicators();
    }

    @Nullable public String getSelectedIndicator() { return selectedIndicatorId; }

    /** Perpendicular distance from point (px,py) to segment (ax,ay)-(bx,by), in pixels. */
    private static float ptSeg(float px, float py, float ax, float ay, float bx, float by) {
        float dx = bx - ax, dy = by - ay;
        if (dx == 0 && dy == 0) return (float) Math.hypot(px - ax, py - ay);
        float t = ((px - ax) * dx + (py - ay) * dy) / (dx * dx + dy * dy);
        t = Math.max(0f, Math.min(1f, t));
        float cx = ax + t * dx, cy = ay + t * dy;
        return (float) Math.hypot(px - cx, py - cy);
    }

    public void zoomIn() {
        chart.zoom(1.5f, 1f, chart.getWidth() / 2f, chart.getHeight() / 2f);
        syncSubChartsToMain();
        chart.postInvalidate();
        chart.postInvalidate();
    }

    public void zoomOut() {
        chart.zoom(1f / 1.5f, 1f, chart.getWidth() / 2f, chart.getHeight() / 2f);
        syncSubChartsToMain();
        chart.postInvalidate();
        chart.postInvalidate();
    }

    public void zoomReset() {
        if (allCandles.isEmpty()) return;
        int total = allCandles.size();
        // Temporarily pin range to position, then release
        chart.setVisibleXRangeMaximum(Math.min(total, 80));
        chart.moveViewToX(total - 1f);
        mainHandler.post(() -> {
            chart.setVisibleXRangeMaximum(total + 200f);
            syncSubChartsToMain();
            chart.postInvalidate();
        });
        isPinnedToRight = true;
        chart.postInvalidate();
        chart.postInvalidate();
    }

    public ArrayList<Candle> getAllCandles() {
        synchronized (allCandles) { return new ArrayList<>(allCandles); }
    }

    // ── Chart setup ────────────────────────────────────────────────────
    public void setupChart(TextView candleDataTextView) {
        chart.setBackgroundColor(COLOR_BACKGROUND);
        chart.setAutoScaleMinMaxEnabled(true);
        chart.setDragEnabled(true);
        chart.setScaleXEnabled(true);
        chart.setScaleYEnabled(false);   // Y scale only via auto-fit, not pinch
        chart.setDrawGridBackground(false);
        chart.setPinchZoom(false);       // false = X zoom only, feels like TradingView
        chart.setDoubleTapToZoomEnabled(true);
        chart.setKeepPositionOnRotation(true);

        chart.setDrawOrder(new com.github.mikephil.charting.charts.CombinedChart.DrawOrder[]{
                com.github.mikephil.charting.charts.CombinedChart.DrawOrder.BAR,
                com.github.mikephil.charting.charts.CombinedChart.DrawOrder.BUBBLE,
                com.github.mikephil.charting.charts.CombinedChart.DrawOrder.LINE,
                com.github.mikephil.charting.charts.CombinedChart.DrawOrder.SCATTER,
                com.github.mikephil.charting.charts.CombinedChart.DrawOrder.CANDLE
        });

        YAxis left = chart.getAxisLeft();
        left.setEnabled(true);
        left.setDrawGridLines(true);  left.setGridColor(COLOR_GRID);
        left.setGridLineWidth(0.5f);
        left.setDrawLabels(false);
        left.setDrawAxisLine(false);
        left.setAxisMinimum(0f);

        YAxis right = chart.getAxisRight();
        right.setEnabled(true);       right.setDrawGridLines(false);
        right.setDrawLabels(true);    right.setDrawAxisLine(false);
        right.setLabelCount(6, false);
        right.setTextColor(COLOR_AXIS_TEXT); right.setTextSize(10f);
        right.setSpaceTop(40f);       right.setSpaceBottom(40f);
        right.setPosition(YAxis.YAxisLabelPosition.OUTSIDE_CHART);

        XAxis x = chart.getXAxis();
        x.setDrawGridLines(false);    x.setPosition(XAxis.XAxisPosition.BOTTOM);
        x.setSpaceMin(0.5f);          x.setSpaceMax(80f);   // small right margin only
        x.setTextColor(COLOR_AXIS_TEXT); x.setTextSize(10f);
        x.setDrawAxisLine(false);     x.setAvoidFirstLastClipping(true);

        chart.getLegend().setEnabled(false);
        chart.getDescription().setEnabled(false);
        chart.setHighlightPerDragEnabled(true);
        chart.setHighlightPerTapEnabled(true);
        chart.setMaxHighlightDistance(20);

        chart.setOnChartValueSelectedListener(new OnChartValueSelectedListener() {
            @Override
            public void onValueSelected(Entry e, Highlight h) {
                int index = Math.round(e.getX());
                if (index >= 0 && index < allCandles.size()) {
                    Candle c = allCandles.get(index);
                    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());
                    boolean isUp = c.close >= c.open;
                    String info = String.format(Locale.US,
                            "%s  %s\nO: %.5f   H: %.5f\nL: %.5f   C: %.5f\nVol: %s",
                            isUp ? "▲" : "▼",
                            sdf.format(new Date(c.timestamp * 1000L)),
                            c.open, c.high, c.low, c.close, formatVolume(c.volume));
                    candleDataTextView.setText(info);
                    candleDataTextView.setVisibility(View.VISIBLE);
                }
            }
            @Override
            public void onNothingSelected() {
                candleDataTextView.setVisibility(View.INVISIBLE);
            }
        });

        chart.setOnChartGestureListener(new OnChartGestureListener() {
            @Override public void onChartGestureStart(MotionEvent me, ChartTouchListener.ChartGesture lg) {}
            @Override public void onChartGestureEnd(MotionEvent me, ChartTouchListener.ChartGesture lg) {
                updatePinState(); syncSubChartsToMain();
            }
            @Override public void onChartLongPressed(MotionEvent me) {}
            @Override public void onChartDoubleTapped(MotionEvent me) { updatePinState(); syncSubChartsToMain(); }
            @Override public void onChartSingleTapped(MotionEvent me) {}
            @Override public void onChartFling(MotionEvent me1, MotionEvent me2, float vX, float vY) {
                updatePinState();
                mainHandler.postDelayed(() -> { updatePinState(); syncSubChartsToMain(); }, 400);
            }
            @Override public void onChartScale(MotionEvent me, float sX, float sY) { updatePinState(); syncSubChartsToMain(); }
            @Override public void onChartTranslate(MotionEvent me, float dX, float dY) { updatePinState(); syncSubChartsToMain(); }
        });
    }

    private void updatePinState() {
        if (chart.getData() == null) return;
        float highest = chart.getHighestVisibleX();
        float total   = allCandles.size() - 1f;
        isPinnedToRight = (total - highest <= 2f);
    }

    // ── Data ingestion ─────────────────────────────────────────────────
    public void addChunk(List<Candle> chunk) {
        if (chunk == null || chunk.isEmpty()) return;
        synchronized (allCandles) {
            for (Candle nc : chunk) {
                if (!allCandles.isEmpty()) {
                    Candle last = allCandles.get(allCandles.size() - 1);
                    if (last.timestamp == nc.timestamp) {
                        allCandles.set(allCandles.size() - 1, nc);
                        continue;
                    }
                }
                allCandles.add(nc);
            }
        }
        triggerChartUpdate(false);
    }

    // ── Throttled update ───────────────────────────────────────────────
    private void triggerChartUpdate(boolean isLiveUpdate) {
        if (chart == null) return;
        if (isUpdatePending.compareAndSet(false, true)) {
            mainHandler.postDelayed(() -> {
                if (chart.getData() != null) {
                    savedLowestX  = chart.getLowestVisibleX();
                    savedHighestX = chart.getHighestVisibleX();
                }
                updateChartData(isLiveUpdate);
                isUpdatePending.set(false);
            }, 150);
        }
    }

    // ── Core render ────────────────────────────────────────────────────
    private void updateChartData(boolean isLiveUpdate) {
        ArrayList<Candle> snap = new ArrayList<>(allCandles);
        if (snap.isEmpty()) return;
        Collections.sort(snap, (a, b) -> Long.compare(a.timestamp, b.timestamp));
        // Remove duplicates produced by overlapping snapshot chunks or DB-cache + server seams.
        // Keep the LAST occurrence of each timestamp (most up-to-date data wins).
        ArrayList<Candle> deduped = new ArrayList<>(snap.size());
        for (int i = 0; i < snap.size(); i++) {
            if (i == snap.size() - 1 || snap.get(i).timestamp != snap.get(i + 1).timestamp) {
                deduped.add(snap.get(i));
            }
        }
        snap = deduped;

        // ── Build price/volume data ────────────────────────────────
        CombinedData data = new CombinedData();
        data.setData(generateVolumeData(snap));
        LineData lineData = new LineData();

        // ── Collect indicator results ──────────────────────────────
        List<ChartDrawing> indicatorDrawings = new ArrayList<>();
        overlayLinesByInstance.clear();

        if (indicatorSession != null) {
            for (Indicator ind : indicatorSession.getOverlays()) {
                Indicator.Result res = ind.compute(snap);
                boolean isSelected = ind.getInstanceId().equals(selectedIndicatorId);
                for (LineDataSet s : res.overlayLines) {
                    // Emphasize the selected indicator's line so the tap-selection is visible.
                    if (isSelected) s.setLineWidth(s.getLineWidth() + 1.6f);
                    lineData.addDataSet(s);
                }
                if (!res.overlayLines.isEmpty())
                    overlayLinesByInstance.put(ind.getInstanceId(), new ArrayList<>(res.overlayLines));
                indicatorDrawings.addAll(res.drawings);  // ← collect drawings
            }
        }

        switch (currentChartType) {
            case CANDLE: data.setData(generateCandleData(snap));  break;
            case BAR:    data.setData(generateOhlcBarData(snap)); break;
            case LINE:   lineData.addDataSet(generateLineData(snap)); break;
        }
        if (lineData.getDataSetCount() > 0) data.setData(lineData);

        // ── X formatter (shared with sub-charts) ──────────────────
        ValueFormatter xFormatter = buildXFormatter(snap);
        chart.getXAxis().setValueFormatter(xFormatter);
        chart.setData(data);

        // ── Pass candle list + indicator drawings to DrawingChart ──
        chart.setCandles(snap);
        chart.replaceIndicatorDrawings(indicatorDrawings);

        int total = snap.size();

        if (!initialViewApplied) {
            // Post so chart has completed its first layout before we set the viewport
            int finalTotal = total;
            mainHandler.post(() -> applyInitialView(finalTotal));

            initialViewApplied = true;
            isPinnedToRight    = true;
            if (candlesReadyCallback != null) candlesReadyCallback.run();

        } else if (isLiveUpdate) {
            // Live tick — viewport unchanged; only expand right-side space for newest candle.
            chart.getXAxis().setSpaceMax(80f);
            // postInvalidate is handled below (only fires for isLiveUpdate).

        } else {
            // Timeframe / indicator reload — restore to approximately same X position
            int finalTotal = total;
            mainHandler.post(() -> {
                if (savedLowestX >= 0 && savedHighestX > savedLowestX) {
                    float range = savedHighestX - savedLowestX;
                    chart.setVisibleXRangeMaximum(range);
                    chart.moveViewToX(savedLowestX);
                    mainHandler.post(() -> {
                        chart.setVisibleXRangeMaximum(finalTotal + 200f);
                        chart.postInvalidate();
                    });
                } else {
                    applyInitialView(finalTotal);
                }
            });
        }

        chart.notifyDataSetChanged();
        chart.calculateOffsets();

        updateCurrentPriceLine(snap.get(snap.size() - 1));
        // Only invalidate immediately for live ticks (viewport doesn't change).
        // For indicator/timeframe reload the viewport-restore callbacks call postInvalidate()
        // themselves — triggering a redraw here first would render drawings at stale positions
        // for one frame, causing the visible "jump" before they snap back on pan.
        if (isLiveUpdate) chart.postInvalidate();
        float maxVol = 0;
        for (Candle c : snap) if (c.volume > maxVol) maxVol = c.volume;
        chart.getAxisLeft().setAxisMaximum(maxVol > 0 ? maxVol * 8f : 1f);

        updateSubCharts(snap, xFormatter);
    }


    private void applyInitialView(int total) {
        int visible = Math.min(total, 80);
        chart.setVisibleXRangeMaximum(visible);
        chart.moveViewToX(total - 1f);
        // Release the range ceiling on the next frame so the user can zoom freely
        mainHandler.post(() -> chart.setVisibleXRangeMaximum(total + 200f));
    }

    // ── Sub-chart management ───────────────────────────────────────────
    private void updateSubCharts(ArrayList<Candle> snap, ValueFormatter xFormatter) {
        if (subChartsContainer == null || subChartsScroller == null) return;

        List<Indicator> subInds = indicatorSession != null
                ? indicatorSession.getSubCharts() : new ArrayList<>();

        Set<String> needed = new java.util.LinkedHashSet<>();
        for (Indicator i : subInds) needed.add(i.getInstanceId());

        java.util.Iterator<Map.Entry<String, LineChart>> it = subChartViews.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, LineChart> e = it.next();
            if (!needed.contains(e.getKey())) {
                subChartsContainer.removeView(e.getValue());
                View div = subChartsContainer.findViewWithTag("div_" + e.getKey());
                if (div != null) subChartsContainer.removeView(div);
                it.remove();
            }
        }

        if (subInds.isEmpty()) {
            subChartsScroller.setVisibility(View.GONE);
            setScrollerWeight(0f);
            return;
        }

        subChartsScroller.setVisibility(View.VISIBLE);
        setScrollerWeight(1f);

        for (Indicator ind : subInds) {
            LineChart sub = subChartViews.get(ind.getInstanceId());
            boolean isNew = (sub == null);
            if (isNew) {
                View div = new View(activityContext);
                LinearLayout.LayoutParams divLp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, 1);
                div.setLayoutParams(divLp);
                div.setBackgroundColor(Color.parseColor("#252323"));
                div.setTag("div_" + ind.getInstanceId());
                subChartsContainer.addView(div);

                sub = createSubChartView(ind, xFormatter);
                subChartViews.put(ind.getInstanceId(), sub);
                subChartsContainer.addView(sub);
            } else {
                configureSubChartXAxis(sub, xFormatter);
            }
            populateSubChart(sub, ind, snap, isNew);
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private LineChart createSubChartView(Indicator ind, ValueFormatter xFormatter) {
        LineChart sub = new LineChart(activityContext);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dpToPx(160));
        sub.setLayoutParams(lp);
        sub.setBackgroundColor(COLOR_BACKGROUND);
        sub.setDrawGridBackground(false);
        sub.setAutoScaleMinMaxEnabled(true);
        sub.setTouchEnabled(true);
        sub.setDragEnabled(true);
        sub.setScaleXEnabled(true);
        sub.setScaleYEnabled(false);
        sub.setPinchZoom(false);
        sub.setDoubleTapToZoomEnabled(false);
        sub.getLegend().setEnabled(false);

        Description desc = new Description();
        desc.setText(ind.getTag());
        desc.setTextColor(COLOR_AXIS_TEXT);
        desc.setTextSize(9f);
        sub.setDescription(desc);

        XAxis x = sub.getXAxis();
        x.setDrawGridLines(false);
        x.setPosition(XAxis.XAxisPosition.BOTTOM);
        x.setTextColor(COLOR_AXIS_TEXT);
        x.setTextSize(9f);
        x.setDrawAxisLine(false);
        x.setAvoidFirstLastClipping(true);
        x.setLabelCount(4, false);
        x.setValueFormatter(xFormatter);
        x.setSpaceMin(15f);
        x.setSpaceMax(80f);

        sub.getAxisLeft().setEnabled(false);
        YAxis r = sub.getAxisRight();
        r.setDrawGridLines(true);  r.setGridColor(COLOR_GRID);
        r.setGridLineWidth(0.5f);  r.setTextColor(COLOR_AXIS_TEXT);
        r.setTextSize(9f);         r.setLabelCount(4, false);
        r.setDrawAxisLine(false);  r.setSpaceTop(15f); r.setSpaceBottom(15f);

        sub.setOnChartGestureListener(new OnChartGestureListener() {
            @Override public void onChartGestureStart(MotionEvent me, ChartTouchListener.ChartGesture lg) {}
            @Override public void onChartGestureEnd(MotionEvent me, ChartTouchListener.ChartGesture lg) {
                sub.getParent().requestDisallowInterceptTouchEvent(false);
                syncMainChartFromSub(sub); }
            @Override public void onChartLongPressed(MotionEvent me) {
                // LOCK: Take control away from the NestedScrollView
                // This enables horizontal dragging inside the chart
                sub.getParent().requestDisallowInterceptTouchEvent(true);

                // Visual/Physical feedback (Optional)
                sub.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS);
            }
            @Override public void onChartDoubleTapped(MotionEvent me) {}
            @Override public void onChartSingleTapped(MotionEvent me) {}
            @Override public void onChartFling(MotionEvent me1, MotionEvent me2, float vX, float vY) {syncMainChartFromSub(sub);}
            @Override public void onChartScale(MotionEvent me, float sX, float sY) { syncMainChartFromSub(sub); }
            @Override public void onChartTranslate(MotionEvent me, float dX, float dY) { syncMainChartFromSub(sub); }
        });

        // --- TOUCH LISTENER (The "Passive" State) ---
        sub.setOnTouchListener((v, event) -> {
            // We do NOT call requestDisallowIntercept here.
            // We let the event pass to the GestureListener above.
            v.onTouchEvent(event);

            // When the touch is finished, ensure we release the parent scroll lock
            if (event.getAction() == MotionEvent.ACTION_UP || event.getAction() == MotionEvent.ACTION_CANCEL) {
                v.getParent().requestDisallowInterceptTouchEvent(false);
            }
            return true;
        });

        return sub;
    }

    private void configureSubChartXAxis(LineChart sub, ValueFormatter xFormatter) {
        sub.getXAxis().setValueFormatter(xFormatter);
    }

    private void populateSubChart(LineChart sub, Indicator ind,
                                  ArrayList<Candle> snap, boolean isNew) {
        Indicator.Result res = ind.compute(snap);
        if (res.subChartLines.isEmpty()) { sub.setVisibility(View.GONE); return; }
        sub.setVisibility(View.VISIBLE);

        LineData d = new LineData();
        for (LineDataSet s : res.subChartLines) {
            s.setDrawCircles(false); s.setDrawValues(false); d.addDataSet(s);
        }
        sub.setData(d);

        if (!Float.isNaN(res.subChartMin) && !Float.isNaN(res.subChartMax)) {
            sub.getAxisRight().setAxisMinimum(res.subChartMin);
            sub.getAxisRight().setAxisMaximum(res.subChartMax);
            sub.setAutoScaleMinMaxEnabled(false);
        } else {
            sub.getAxisRight().resetAxisMinimum();
            sub.getAxisRight().resetAxisMaximum();
            sub.setAutoScaleMinMaxEnabled(true);
        }

        sub.notifyDataSetChanged();

        if (isNew) {
            int total = snap.size();
            sub.fitScreen();
            if (total > 60) {
                sub.setVisibleXRangeMaximum(60f);
                sub.setVisibleXRangeMinimum(10f);
                sub.moveViewToX(total - 1);
                mainHandler.postDelayed(() -> { sub.setVisibleXRangeMaximum(total); sub.postInvalidate(); }, 120);
            } else {
                sub.moveViewToX(total - 1);
            }
        } else {
            syncSubChartToMain(sub);
        }

        sub.calculateOffsets();
        sub.postInvalidate();
    }

    // ── Viewport sync ──────────────────────────────────────────────────
    private void syncSubChartsToMain() {
        for (LineChart sub : subChartViews.values()) syncSubChartToMain(sub);
    }

    private void syncSubChartToMain(LineChart sub) {
        if (sub == null || sub.getData() == null || chart.getData() == null) return;
        float lo = chart.getLowestVisibleX(), range = chart.getVisibleXRange();
        if (range <= 0) return;
        sub.setVisibleXRangeMaximum(range);
        sub.setVisibleXRangeMinimum(range);
        sub.moveViewToX(lo);
        mainHandler.postDelayed(() -> {
            int total = allCandles.size();
            sub.setVisibleXRangeMinimum(10f);
            sub.setVisibleXRangeMaximum(total);
            sub.postInvalidate();
        }, 30);
    }

    private void syncMainChartFromSub(LineChart sub) {
        if (sub.getData() == null || chart.getData() == null) return;
        float lo = sub.getLowestVisibleX(), range = sub.getVisibleXRange();
        if (range <= 0) return;
        int total = allCandles.size();
        chart.setVisibleXRangeMaximum(range); chart.setVisibleXRangeMinimum(range);
        chart.moveViewToX(lo);
        mainHandler.postDelayed(() -> {
            chart.setVisibleXRangeMinimum(10f); chart.setVisibleXRangeMaximum(total);
            chart.postInvalidate(); updatePinState();
        }, 30);
        for (Map.Entry<String, LineChart> e : subChartViews.entrySet()) {
            LineChart other = e.getValue();
            if (other == sub || other.getData() == null) continue;
            other.setVisibleXRangeMaximum(range); other.setVisibleXRangeMinimum(range);
            other.moveViewToX(lo);
            mainHandler.postDelayed(() -> { other.setVisibleXRangeMinimum(10f); other.setVisibleXRangeMaximum(total); other.postInvalidate(); }, 30);
        }
    }

    private void setScrollerWeight(float weight) {
        if (subChartsScroller == null) return;
        ViewGroup.LayoutParams rawLp = subChartsScroller.getLayoutParams();
        if (rawLp instanceof LinearLayout.LayoutParams) {
            LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) rawLp;
            lp.weight = weight; lp.height = 0;
            subChartsScroller.setLayoutParams(lp);
        }
    }

    // ── Price line ─────────────────────────────────────────────────────
    private void updateCurrentPriceLine(Candle latest) {
        YAxis right = chart.getAxisRight();
        if (currentPriceLine != null) right.removeLimitLine(currentPriceLine);
        boolean isUp = latest.close >= latest.open;
        int c = isUp ? COLOR_UP : COLOR_DOWN;
        currentPriceLine = new LimitLine((float)latest.close,
                String.format(Locale.US, "%.5f", latest.close));
        currentPriceLine.setLineColor(c);   currentPriceLine.setLineWidth(1f);
        currentPriceLine.enableDashedLine(8f, 4f, 0f);
        currentPriceLine.setTextColor(c);   currentPriceLine.setTextSize(9f);
        currentPriceLine.setLabelPosition(LimitLine.LimitLabelPosition.RIGHT_TOP);
        right.addLimitLine(currentPriceLine);
        right.setDrawLimitLinesBehindData(false);
    }

    // ── X formatter ────────────────────────────────────────────────────
    private ValueFormatter buildXFormatter(ArrayList<Candle> snap) {
        return new ValueFormatter() {
            final SimpleDateFormat dailyFmt = new SimpleDateFormat("MMM dd", Locale.getDefault());
            final SimpleDateFormat timeFmt  = new SimpleDateFormat("HH:mm",  Locale.getDefault());
            @Override
            public String getAxisLabel(float value, AxisBase axis) {
                int i = Math.round(value);
                if (i < 0 || i >= snap.size()) return "";
                Date d = new Date(snap.get(i).timestamp * 1000L);
                return (interval == StockDataHelper.Timeframe.DAILY)
                        ? dailyFmt.format(d) : timeFmt.format(d);
            }
        };
    }

    // ── Data generators ────────────────────────────────────────────────
    private CandleData generateCandleData(ArrayList<Candle> c) {
        List<CandleEntry> e = new ArrayList<>();
        for (int i=0;i<c.size();i++) { Candle cv=c.get(i); e.add(new CandleEntry(i,(float)cv.high,(float)cv.low,(float)cv.open,(float)cv.close)); }
        CandleDataSet s = new CandleDataSet(e,"Prices");
        s.setDecreasingColor(COLOR_DOWN); s.setDecreasingPaintStyle(Paint.Style.FILL);
        s.setIncreasingColor(COLOR_UP);   s.setIncreasingPaintStyle(Paint.Style.FILL);
        s.setNeutralColor(Color.GRAY); s.setShadowColorSameAsCandle(true); s.setShadowWidth(1.5f);
        s.setBarSpace(0.1f); s.setDrawValues(false); s.setHighlightEnabled(true);
        s.setHighLightColor(COLOR_HIGHLIGHT); s.setHighlightLineWidth(1f);
        s.enableDashedHighlightLine(8f,4f,0f); s.setAxisDependency(YAxis.AxisDependency.RIGHT);
        return new CandleData(s);
    }

    private CandleData generateOhlcBarData(ArrayList<Candle> c) {
        List<CandleEntry> e = new ArrayList<>();
        for (int i=0;i<c.size();i++) { Candle cv=c.get(i); e.add(new CandleEntry(i,(float)cv.high,(float)cv.low,(float)cv.open,(float)cv.close)); }
        CandleDataSet s = new CandleDataSet(e,"Prices");
        s.setDecreasingColor(COLOR_DOWN); s.setDecreasingPaintStyle(Paint.Style.STROKE);
        s.setIncreasingColor(COLOR_UP);   s.setIncreasingPaintStyle(Paint.Style.STROKE);
        s.setNeutralColor(Color.GRAY); s.setShadowColorSameAsCandle(true); s.setShadowWidth(1.5f);
        s.setBarSpace(0.3f); s.setDrawValues(false); s.setHighlightEnabled(true);
        s.setHighLightColor(COLOR_HIGHLIGHT); s.setHighlightLineWidth(1f);
        s.enableDashedHighlightLine(8f,4f,0f); s.setAxisDependency(YAxis.AxisDependency.RIGHT);
        return new CandleData(s);
    }

    private LineDataSet generateLineData(ArrayList<Candle> c) {
        List<Entry> e = new ArrayList<>();
        for (int i=0;i<c.size();i++) e.add(new Entry(i,(float)c.get(i).close));
        LineDataSet s = new LineDataSet(e,"Close");
        s.setColor(COLOR_LINE); s.setLineWidth(1.8f); s.setDrawCircles(false); s.setDrawValues(false);
        s.setMode(LineDataSet.Mode.LINEAR); s.setDrawFilled(true); s.setFillColor(COLOR_LINE); s.setFillAlpha(25);
        s.setHighlightEnabled(true); s.setHighLightColor(COLOR_HIGHLIGHT); s.setHighlightLineWidth(1f);
        s.enableDashedHighlightLine(8f,4f,0f); s.setAxisDependency(YAxis.AxisDependency.RIGHT);
        return s;
    }

    private BarData generateVolumeData(ArrayList<Candle> c) {
        List<BarEntry> e = new ArrayList<>();
        int[] colors = new int[c.size()];
        for (int i=0;i<c.size();i++) {
            Candle cv=c.get(i); e.add(new BarEntry(i,(float) Math.max(0L, Math.min(cv.volume, Integer.MAX_VALUE))));
            if      (cv.close>cv.open) colors[i]=COLOR_VOL_UP;
            else if (cv.close<cv.open) colors[i]=COLOR_VOL_DOWN;
            else                        colors[i]=COLOR_VOL_NEUTRAL;
        }
        BarDataSet s = new BarDataSet(e,"Volume");
        s.setColors(colors); s.setAxisDependency(YAxis.AxisDependency.LEFT);
        s.setDrawValues(false); s.setHighlightEnabled(false);
        BarData bd = new BarData(s); bd.setBarWidth(0.8f);
        return bd;
    }

    // ── Utility ────────────────────────────────────────────────────────
    private int dpToPx(int dp) {
        return Math.round(dp * activityContext.getResources().getDisplayMetrics().density);
    }
    private String formatVolume(long v) {
        if (v>=1_000_000) return String.format(Locale.US,"%dM",v/1_000_000);
        if (v>=1_000)     return String.format(Locale.US,"%dK",v/1_000);
        return String.format(Locale.US,"%d",v);
    }

    // ── Stream update ──────────────────────────────────────────────────
    private void streamUpdate(List<Candle> chunk) {
        if (chunk==null||chunk.isEmpty()) return;
        Candle live = chunk.get(0);
        synchronized (allCandles) {
            // Buffer ticks until the snapshot is fully delivered so we never corrupt
            // a partially-received candle.  done is set true by TICKER_REQUEST_DONE.
            if (!done) { streamBuffer.addAll(chunk); return; }
            if (allCandles.isEmpty()) return;
            Candle last = allCandles.get(allCandles.size()-1);
            if (live.timestamp==last.timestamp) return;
            if (last.timestamp+interval.interval>live.timestamp) {
                allCandles.set(allCandles.size()-1, new Candle(last.timestamp,last.open,
                        Math.max(live.high,last.high),Math.min(live.low,last.low),live.close,
                        Math.max(0L,last.volume)+Math.max(0L,live.volume)));
            } else {
                // Floor-align the new candle's timestamp to the interval boundary so
                // 5-min candles never land on e.g. :01, :02, :03 seconds.
                long newTs = (live.timestamp / (long)interval.interval) * (long)interval.interval;
                allCandles.add(new Candle(newTs,live.open,live.high,live.low,live.close,live.volume));
            }
        }
        triggerChartUpdate(true);
    }

    // ── SessionCallback ────────────────────────────────────────────────
    @Override
    public void onDataReceived(DataType msgType, Object parsedData) {
        if (msgType==DataType.TICKER_ERROR) {
            mainHandler.post(()->Toast.makeText(activityContext,(String)parsedData,Toast.LENGTH_SHORT).show());
            flushRequests(); return;
        }
        if (!(parsedData instanceof PriceChunk)) return;
        PriceChunk chunk=(PriceChunk)parsedData;
        if (!reqIds.contains(chunk.reqId)) return;
        switch (msgType) {
            case TICKER_STREAM:
                streamUpdate(chunk.chunk);
                break;
            case TICKER_SNAPSHOT:
                addChunk(chunk.chunk);
                break;
            case TICKER_REQUEST_DONE:
                reqIds.remove(chunk.reqId);
                done = true;
                // Flush any ticks that arrived while the snapshot was still in flight.
                List<Candle> toFlush = new ArrayList<>(streamBuffer);
                streamBuffer.clear();
                for (Candle b : toFlush) streamUpdate(List.of(b));
                break;
        }
        synchronized (allCandles){
            if(!allCandles.isEmpty()) mainHandler.post(() -> {currentPrice.setValue(allCandles.get(allCandles.size()-1).close);});
        }
    }
    @Override public void onActionRequired(int a, @Nullable Object d) {}

    // ── Lifecycle ──────────────────────────────────────────────────────
    public void clearChart() {
        allCandles.clear(); done=false; streamBuffer.clear();
        isPinnedToRight=true; initialViewApplied=false;
        savedLowestX=-1f; savedHighestX=-1f;
        if (currentPriceLine!=null) { chart.getAxisRight().removeLimitLine(currentPriceLine); currentPriceLine=null; }
    }
    public void addToCurrentRequest(int reqId) { this.reqIds.add(reqId); }
    public void flushRequests() {
        NetworkClient.getInstance(null).getSessionManager()
                .discardRequests(reqIds.stream().mapToInt(Integer::intValue).toArray());
        this.reqIds.clear();
    }
    public void bindListener(SessionCallback l) { this.chainedListener=l; }
    public boolean isDone() { return done; }
    public void setInterval(StockDataHelper.Timeframe tf) {
        this.interval=tf;
        done = false;
    }
}