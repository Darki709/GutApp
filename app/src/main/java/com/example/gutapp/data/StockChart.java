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

import com.example.gutapp.data.indicators.Indicator;
import com.example.gutapp.data.indicators.IndicatorSession;
import com.example.gutapp.data.models.Candle;
import com.example.gutapp.data.models.PriceChunk;
import com.example.gutapp.database.StockDataHelper;
import com.example.gutapp.session.DataType;
import com.example.gutapp.session.NetworkClient;
import com.example.gutapp.session.SessionCallback;
import com.github.mikephil.charting.charts.CombinedChart;
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

public class StockChart implements SessionCallback {

    public enum ChartType { CANDLE, BAR, LINE }

    // ── Colors ───────────────────────────────────────────────────────
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

    // ── Core fields ───────────────────────────────────────────────────
    private final CombinedChart chart;
    private final Context activityContext;

    @Setter @Nullable private LinearLayout subChartsContainer = null;
    @Setter @Nullable private NestedScrollView subChartsScroller = null;

    /** Keyed by indicator instanceId */
    private final LinkedHashMap<String, LineChart> subChartViews = new LinkedHashMap<>();

    private final CopyOnWriteArrayList<Candle> allCandles = new CopyOnWriteArrayList<>();
    private final Set<Integer> reqIds = ConcurrentHashMap.<Integer>newKeySet();

    @Setter @Nullable private IndicatorSession indicatorSession = null;

    private StockDataHelper.Timeframe interval;
    private ChartType currentChartType = ChartType.CANDLE;

    private final AtomicBoolean isUpdatePending = new AtomicBoolean(false);
    private volatile boolean done = false;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final List<Candle> streamBuffer = new ArrayList<>();

    @Nullable private SessionCallback chainedListener = null;
    @Nullable private LimitLine currentPriceLine = null;

    // ── Viewport state ────────────────────────────────────────────────
    /**
     * Whether the user is "pinned" to the right edge.
     * true  → live updates auto-scroll to show newest candle.
     * false → user has panned left; we preserve their viewport exactly.
     *
     * Set to true on initial load and on zoomReset().
     * Set to false as soon as we detect the user has scrolled away from the right.
     */
    private boolean isPinnedToRight = true;

    /**
     * The lowest visible X index saved just before we rebuild chart data.
     * Used to restore the viewport position when !isPinnedToRight.
     */
    private float savedLowestX  = -1f;
    private float savedHighestX = -1f;

    /**
     * Whether the initial view (first data load) has been applied yet.
     * Once applied, live updates never re-zoom — they only scroll.
     */
    private boolean initialViewApplied = false;

    // ── Constructor ───────────────────────────────────────────────────
    public StockChart(CombinedChart chart, Context context) {
        this.chart = chart;
        this.activityContext = context;
    }

    // ── Public API ────────────────────────────────────────────────────

    public void setChartType(ChartType type) {
        this.currentChartType = type;
        triggerChartUpdate(true);
    }
    public ChartType getChartType() { return currentChartType; }

    public void applyIndicators() { triggerChartUpdate(true); }

    public void zoomIn() {
        chart.zoom(1.5f, 1f, chart.getWidth() / 2f, chart.getHeight() / 2f);
        chart.invalidate();
        syncSubChartsToMain();
    }

    public void zoomOut() {
        chart.zoom(1f / 1.5f, 1f, chart.getWidth() / 2f, chart.getHeight() / 2f);
        chart.invalidate();
        syncSubChartsToMain();
    }

    public void zoomReset() {
        ArrayList<Candle> snap = new ArrayList<>(allCandles);
        if (snap.isEmpty()) return;
        applyInitialView(snap.size());
        isPinnedToRight   = true;
        initialViewApplied = true;
        chart.postInvalidate();
        syncSubChartsToMain();
    }

    // ── Chart setup ───────────────────────────────────────────────────
    public void setupChart(TextView candleDataTextView) {
        chart.setBackgroundColor(COLOR_BACKGROUND);
        chart.setAutoScaleMinMaxEnabled(true);
        chart.setDragEnabled(true);
        chart.setScaleEnabled(true);
        chart.setDrawGridBackground(false);
        chart.setPinchZoom(true);
        chart.setDoubleTapToZoomEnabled(true);
        chart.setKeepPositionOnRotation(true);

        chart.setDrawOrder(new CombinedChart.DrawOrder[]{
                CombinedChart.DrawOrder.BAR, CombinedChart.DrawOrder.BUBBLE,
                CombinedChart.DrawOrder.LINE, CombinedChart.DrawOrder.SCATTER,
                CombinedChart.DrawOrder.CANDLE
        });

        YAxis left = chart.getAxisLeft();
        left.setDrawGridLines(true);   left.setGridColor(COLOR_GRID);
        left.setGridLineWidth(0.5f);   left.setLabelCount(6, false);
        left.setTextColor(COLOR_AXIS_TEXT); left.setTextSize(10f);
        left.setSpaceTop(15f);         left.setSpaceBottom(15f);
        left.setDrawAxisLine(false);   left.setPosition(YAxis.YAxisLabelPosition.OUTSIDE_CHART);

        YAxis right = chart.getAxisRight();
        right.setEnabled(true);        right.setDrawGridLines(false);
        right.setDrawLabels(false);    right.setDrawAxisLine(false);
        right.setAxisMinimum(0f);

        XAxis x = chart.getXAxis();
        x.setDrawGridLines(false);     x.setPosition(XAxis.XAxisPosition.BOTTOM);
        x.setSpaceMin(15f);            x.setSpaceMax(15f);
        x.setTextColor(COLOR_AXIS_TEXT); x.setTextSize(10f);
        x.setDrawAxisLine(false);      x.setAvoidFirstLastClipping(true);

        chart.getLegend().setEnabled(false);
        chart.getDescription().setEnabled(false);
        chart.setVisibleXRangeMinimum(10f);
        chart.setHighlightPerDragEnabled(true);
        chart.setHighlightPerTapEnabled(true);
        chart.setMaxHighlightDistance(20);

        // Candle selection → OHLCV overlay
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
                            c.open, c.high, c.low, c.close,
                            formatVolume(c.volume));
                    candleDataTextView.setText(info);
                    candleDataTextView.setVisibility(View.VISIBLE);
                }
            }
            @Override
            public void onNothingSelected() {
                candleDataTextView.setVisibility(View.INVISIBLE);
            }
        });

        // Gesture listener: detects pan/zoom so we know the user left the right edge
        chart.setOnChartGestureListener(new OnChartGestureListener() {
            @Override
            public void onChartGestureStart(MotionEvent me, ChartTouchListener.ChartGesture lg) {}
            @Override
            public void onChartGestureEnd(MotionEvent me, ChartTouchListener.ChartGesture lg) {
                // After any gesture, re-evaluate pin state and sync sub-charts
                updatePinState();
                syncSubChartsToMain();
            }
            @Override public void onChartLongPressed(MotionEvent me) {}
            @Override public void onChartDoubleTapped(MotionEvent me) { updatePinState(); syncSubChartsToMain(); }
            @Override public void onChartSingleTapped(MotionEvent me) {}
            @Override public void onChartFling(MotionEvent me1, MotionEvent me2, float vX, float vY) { updatePinState(); syncSubChartsToMain(); }
            @Override
            public void onChartScale(MotionEvent me, float scaleX, float scaleY) {
                updatePinState();
                syncSubChartsToMain();
            }
            @Override
            public void onChartTranslate(MotionEvent me, float dX, float dY) {
                updatePinState();
                syncSubChartsToMain();
            }
        });
    }

    /**
     * Re-evaluate whether the user is at the right edge.
     * Called after every user gesture. Only updates the flag; never moves the view.
     */
    private void updatePinState() {
        if (chart.getData() == null) return;
        float highest = chart.getHighestVisibleX();
        float total   = allCandles.size() - 1f;
        // Pinned if within 2 candles of the last data point
        isPinnedToRight = (total - highest <= 2f);
    }

    // ── Data ingestion ────────────────────────────────────────────────
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

    // ── Throttled update scheduler ────────────────────────────────────
    /**
     * @param isLiveUpdate true = streaming tick, false = snapshot/initial load or
     *                     indicator change (anything that warrants re-evaluating viewport)
     */
    private void triggerChartUpdate(boolean isLiveUpdate) {
        if (chart == null) return;
        if (isUpdatePending.compareAndSet(false, true)) {
            mainHandler.postDelayed(() -> {
                // Save viewport BEFORE touching the data so we can restore it
                if (chart.getData() != null) {
                    savedLowestX  = chart.getLowestVisibleX();
                    savedHighestX = chart.getHighestVisibleX();
                }
                updateChartData(isLiveUpdate);
                isUpdatePending.set(false);
            }, 150);
        }
    }

    // ── Core render ───────────────────────────────────────────────────
    private void updateChartData(boolean isLiveUpdate) {
        ArrayList<Candle> snap = new ArrayList<>(allCandles);
        if (snap.isEmpty()) return;
        Collections.sort(snap, (a, b) -> Long.compare(a.timestamp, b.timestamp));

        // ── Build data ────────────────────────────────────────────
        CombinedData data = new CombinedData();
        data.setData(generateVolumeData(snap));
        LineData lineData = new LineData();

        if (indicatorSession != null) {
            for (Indicator ind : indicatorSession.getOverlays()) {
                Indicator.Result res = ind.compute(snap);
                for (LineDataSet s : res.overlayLines) lineData.addDataSet(s);
            }
        }

        switch (currentChartType) {
            case CANDLE: data.setData(generateCandleData(snap));   break;
            case BAR:    data.setData(generateOhlcBarData(snap));  break;
            case LINE:   lineData.addDataSet(generateLineData(snap)); break;
        }
        if (lineData.getDataSetCount() > 0) data.setData(lineData);

        // ── Shared X formatter ─────────────────────────────────────
        ValueFormatter xFormatter = buildXFormatter(snap);
        chart.getXAxis().setValueFormatter(xFormatter);
        chart.setData(data);

        updateCurrentPriceLine(snap.get(snap.size() - 1));
        chart.getData().notifyDataChanged();
        chart.notifyDataSetChanged();

        // ── Viewport management ───────────────────────────────────
        int total = snap.size();

        if (!initialViewApplied) {
            // ── FIRST LOAD: set initial zoom to show ~60 candles ──
            float maxVol = 0;
            for (Candle c : snap) if (c.volume > maxVol) maxVol = c.volume;
            chart.getAxisRight().setAxisMaximum(maxVol * 8f);

            applyInitialView(total);
            initialViewApplied = true;
            isPinnedToRight    = true;

        } else if (!isLiveUpdate) {
            // ── INDICATOR CHANGE / TYPE CHANGE / TF CHANGE ─────────
            // Preserve the user's current viewport exactly — never re-zoom
            if (savedLowestX >= 0 && savedHighestX > savedLowestX) {
                float range = savedHighestX - savedLowestX;
                chart.setVisibleXRangeMaximum(range);
                chart.setVisibleXRangeMinimum(range);
                chart.moveViewToX(savedLowestX);
                // Release the lock immediately so user can still zoom freely
                mainHandler.postDelayed(() -> {
                    chart.setVisibleXRangeMinimum(10f);
                    chart.setVisibleXRangeMaximum(total);
                }, 50);
            }

        } else {
            // ── LIVE STREAMING TICK ────────────────────────────────
            if (isPinnedToRight) {
                // Smoothly keep newest candle visible without touching zoom level
                chart.moveViewToX(total - 1);
            } else {
                // User has scrolled back — lock their exact viewport
                if (savedLowestX >= 0 && savedHighestX > savedLowestX) {
                    float range = savedHighestX - savedLowestX;
                    chart.setVisibleXRangeMaximum(range);
                    chart.setVisibleXRangeMinimum(range);
                    chart.moveViewToX(savedLowestX);
                    mainHandler.postDelayed(() -> {
                        chart.setVisibleXRangeMinimum(10f);
                        chart.setVisibleXRangeMaximum(total);
                    }, 50);
                }
            }
        }

        chart.notifyDataSetChanged();
        chart.calculateOffsets();
        chart.postInvalidate();

        // ── Update sub-charts ────────────────────────────────────
        updateSubCharts(snap, xFormatter);
    }

    /**
     * Apply the standard initial zoom: show the last 60 candles (or all if fewer).
     * Does NOT change isPinnedToRight — caller must set that.
     */
    private void applyInitialView(int total) {
        chart.fitScreen();
        if (total > 60) {
            // Show exactly 60 candles from the right
            chart.setVisibleXRangeMaximum(60f);
            chart.setVisibleXRangeMinimum(10f);
            chart.moveViewToX(total - 1);
            // Release the max restriction after a short delay so user can zoom out
            mainHandler.postDelayed(() -> {
                chart.setVisibleXRangeMaximum(total);
            }, 100);
        } else {
            chart.moveViewToX(total - 1);
        }
    }

    // ── Sub-chart management ──────────────────────────────────────────

    private void updateSubCharts(ArrayList<Candle> snap, ValueFormatter xFormatter) {
        if (subChartsContainer == null || subChartsScroller == null) return;

        List<Indicator> subInds = indicatorSession != null
                ? indicatorSession.getSubCharts() : new ArrayList<>();

        // Remove views for indicators that were removed from the session
        Set<String> needed = new java.util.LinkedHashSet<>();
        for (Indicator i : subInds) needed.add(i.getInstanceId());

        java.util.Iterator<Map.Entry<String, LineChart>> it = subChartViews.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, LineChart> e = it.next();
            if (!needed.contains(e.getKey())) {
                // Remove the chart view and its divider
                subChartsContainer.removeView(e.getValue());
                View divider = subChartsContainer.findViewWithTag("div_" + e.getKey());
                if (divider != null) subChartsContainer.removeView(divider);
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
                // Create divider first, then the chart
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
                // Refresh the X formatter in case interval changed
                configureSubChartXAxis(sub, xFormatter);
            }

            populateSubChart(sub, ind, snap, isNew);
        }
    }

    /**
     * Create and fully configure a new LineChart for a sub-chart indicator.
     * Touch is enabled so user can pan/zoom the sub-chart independently.
     * The NestedScrollView handles vertical scrolling between multiple sub-charts.
     */
    @SuppressLint("ClickableViewAccessibility")
    private LineChart createSubChartView(Indicator ind, ValueFormatter xFormatter) {
        LineChart sub = new LineChart(activityContext);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dpToPx(160));
        sub.setLayoutParams(lp);
        sub.setBackgroundColor(COLOR_BACKGROUND);
        sub.setDrawGridBackground(false);
        sub.setAutoScaleMinMaxEnabled(true);

        // Touch: allow horizontal drag/zoom on the sub-chart
        sub.setTouchEnabled(true);
        sub.setDragEnabled(true);
        sub.setScaleXEnabled(true);
        sub.setScaleYEnabled(false);   // only horizontal zoom makes sense for oscillators
        sub.setPinchZoom(false);
        sub.setDoubleTapToZoomEnabled(false);

        sub.getLegend().setEnabled(false);

        // Indicator tag as description label (top-right corner)
        Description desc = new Description();
        desc.setText(ind.getTag());
        desc.setTextColor(COLOR_AXIS_TEXT);
        desc.setTextSize(9f);
        sub.setDescription(desc);

        // X axis — labels ON, matching the main chart format
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
        x.setSpaceMax(15f);


        // Y axis
        sub.getAxisLeft().setEnabled(false);
        YAxis r = sub.getAxisRight();
        r.setDrawGridLines(true);     r.setGridColor(COLOR_GRID);
        r.setGridLineWidth(0.5f);     r.setTextColor(COLOR_AXIS_TEXT);
        r.setTextSize(9f);            r.setLabelCount(4, false);
        r.setDrawAxisLine(false);
        r.setSpaceTop(15f);         r.setSpaceBottom(15f);

        // When the user drags the sub-chart, sync main chart position
        sub.setOnChartGestureListener(new OnChartGestureListener() {
            @Override public void onChartGestureStart(MotionEvent me, ChartTouchListener.ChartGesture lg) {}
            @Override
            public void onChartGestureEnd(MotionEvent me, ChartTouchListener.ChartGesture lg) {
                syncMainChartFromSub(sub);
            }
            @Override public void onChartLongPressed(MotionEvent me) {}
            @Override public void onChartDoubleTapped(MotionEvent me) {}
            @Override public void onChartSingleTapped(MotionEvent me) {}
            @Override public void onChartFling(MotionEvent me1, MotionEvent me2, float vX, float vY) {}
            @Override
            public void onChartScale(MotionEvent me, float scaleX, float scaleY) {
                syncMainChartFromSub(sub);
            }
            @Override
            public void onChartTranslate(MotionEvent me, float dX, float dY) {
                syncMainChartFromSub(sub);
            }
        });

        // Intercept vertical scroll so the NestedScrollView can still scroll vertically
        // while the sub-chart handles horizontal drag
        sub.setOnTouchListener(new View.OnTouchListener() {
            private static final int SCROLL_THRESHOLD = 20; // pixels
            private static final long LONG_PRESS_TIMEOUT = 200; // milliseconds

            private float startX, startY;
            private long startTime;
            private boolean isChartLocked = false;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        startX = event.getX();
                        startY = event.getY();
                        startTime = System.currentTimeMillis();
                        isChartLocked = false;
                        // Initially, let the parent be able to intercept
                        v.getParent().requestDisallowInterceptTouchEvent(false);
                        break;

                    case MotionEvent.ACTION_MOVE:
                        float deltaX = Math.abs(event.getX() - startX);
                        float deltaY = Math.abs(event.getY() - startY);
                        long duration = System.currentTimeMillis() - startTime;

                        // 1. If user holds for longer than 200ms, lock to the chart
                        if (duration > LONG_PRESS_TIMEOUT && !isChartLocked) {
                            isChartLocked = true;
                            v.getParent().requestDisallowInterceptTouchEvent(true);
                        }

                        // 2. If user moves horizontally (panning chart) more than vertically
                        if (deltaX > SCROLL_THRESHOLD && deltaX > deltaY && !isChartLocked) {
                            isChartLocked = true;
                            v.getParent().requestDisallowInterceptTouchEvent(true);
                        }

                        // 3. If it's a clear vertical swipe and we aren't locked, let parent scroll
                        if (deltaY > SCROLL_THRESHOLD && deltaY > deltaX && !isChartLocked) {
                            v.getParent().requestDisallowInterceptTouchEvent(false);
                            return false; // Exit and let ScrollView take over
                        }
                        break;

                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        v.getParent().requestDisallowInterceptTouchEvent(false);
                        isChartLocked = false;
                        break;
                }

                // Forward events to chart for zoom/panning if we are locked or touching
                v.onTouchEvent(event);
                return true;
            }
        });

        return sub;
    }

    private void configureSubChartXAxis(LineChart sub, ValueFormatter xFormatter) {
        sub.getXAxis().setValueFormatter(xFormatter);
    }

    /**
     * Fill a sub-chart with indicator data and sync its viewport to the main chart.
     * @param isNew true on first population — applies the same initial 60-candle view.
     */
    private void populateSubChart(LineChart sub, Indicator ind,
                                  ArrayList<Candle> snap, boolean isNew) {
        Indicator.Result res = ind.compute(snap);
        if (res.subChartLines.isEmpty()) {
            sub.setVisibility(View.GONE);
            return;
        }
        sub.setVisibility(View.VISIBLE);

        LineData d = new LineData();
        for (LineDataSet s : res.subChartLines) {
            s.setDrawCircles(false);
            s.setDrawValues(false);
            d.addDataSet(s);
        }
        sub.setData(d);

        // Apply fixed Y range if the indicator supplies one (e.g. RSI 0–100)
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
            // First paint: mirror the main chart's initial 60-candle view
            int total = snap.size();
            sub.fitScreen();
            if (total > 60) {
                sub.setVisibleXRangeMaximum(60f);
                sub.setVisibleXRangeMinimum(10f);
                sub.moveViewToX(total - 1);
                mainHandler.postDelayed(() -> {
                    sub.setVisibleXRangeMaximum(total);
                    sub.postInvalidate();
                }, 120);
            } else {
                sub.moveViewToX(total - 1);
            }
        } else {
            // Subsequent updates: keep viewport exactly in sync with main chart
            syncSubChartToMain(sub);
        }

        sub.calculateOffsets();
        sub.postInvalidate();
    }

    // ── Viewport sync helpers ─────────────────────────────────────────

    /**
     * Push the main chart's current viewport to ALL sub-charts.
     * Called after main chart gestures and after zoom buttons.
     */
    private void syncSubChartsToMain() {
        for (LineChart sub : subChartViews.values()) {
            syncSubChartToMain(sub);
        }
    }

    /** Sync one sub-chart to match the main chart's X viewport. */
    private void syncSubChartToMain(LineChart sub) {
        if (sub == null || sub.getData() == null) return;
        if (chart.getData() == null) return;

        float lo    = chart.getLowestVisibleX();
        float range = chart.getVisibleXRange();

        if (range <= 0) return;

        sub.setVisibleXRangeMaximum(range);
        sub.setVisibleXRangeMinimum(range);
        sub.moveViewToX(lo);
        // Immediately unlock range so user can zoom the sub-chart
        mainHandler.postDelayed(() -> {
            int total = allCandles.size();
            sub.setVisibleXRangeMinimum(10f);
            sub.setVisibleXRangeMaximum(total);
            sub.postInvalidate();
        }, 30);
    }

    /**
     * When user pans/zooms a sub-chart, push that viewport back to the main chart
     * and all other sub-charts, and update the pin state.
     */
    private void syncMainChartFromSub(LineChart sub) {
        if (sub.getData() == null || chart.getData() == null) return;

        float lo    = sub.getLowestVisibleX();
        float range = sub.getVisibleXRange();
        if (range <= 0) return;

        int total = allCandles.size();

        // Push to main chart
        chart.setVisibleXRangeMaximum(range);
        chart.setVisibleXRangeMinimum(range);
        chart.moveViewToX(lo);
        mainHandler.postDelayed(() -> {
            chart.setVisibleXRangeMinimum(10f);
            chart.setVisibleXRangeMaximum(total);
            chart.postInvalidate();
            updatePinState();
        }, 30);

        // Push to all OTHER sub-charts
        for (Map.Entry<String, LineChart> entry : subChartViews.entrySet()) {
            LineChart other = entry.getValue();
            if (other == sub || other.getData() == null) continue;
            other.setVisibleXRangeMaximum(range);
            other.setVisibleXRangeMinimum(range);
            other.moveViewToX(lo);
            mainHandler.postDelayed(() -> {
                other.setVisibleXRangeMinimum(10f);
                other.setVisibleXRangeMaximum(total);
                other.postInvalidate();
            }, 30);
        }
    }

    /** Apply weight-based height to the NestedScrollView */
    private void setScrollerWeight(float weight) {
        if (subChartsScroller == null) return;
        ViewGroup.LayoutParams rawLp = subChartsScroller.getLayoutParams();
        LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) rawLp;
        lp.weight  = weight;
        lp.height  = 0;
        subChartsScroller.setLayoutParams(lp);
    }

    // ── Price limit line ─────────────────────────────────────────────
    private void updateCurrentPriceLine(Candle latest) {
        YAxis left = chart.getAxisLeft();
        if (currentPriceLine != null) left.removeLimitLine(currentPriceLine);
        boolean isUp = latest.close >= latest.open;
        int c = isUp ? COLOR_UP : COLOR_DOWN;
        currentPriceLine = new LimitLine((float) latest.close,
                String.format(Locale.US, "%.5f", latest.close));
        currentPriceLine.setLineColor(c);
        currentPriceLine.setLineWidth(1f);
        currentPriceLine.enableDashedLine(8f, 4f, 0f);
        currentPriceLine.setTextColor(c);
        currentPriceLine.setTextSize(9f);
        currentPriceLine.setLabelPosition(LimitLine.LimitLabelPosition.RIGHT_TOP);
        left.addLimitLine(currentPriceLine);
        left.setDrawLimitLinesBehindData(false);
    }

    // ── X formatter factory ───────────────────────────────────────────
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

    // ── Data generators ───────────────────────────────────────────────
    private CandleData generateCandleData(ArrayList<Candle> c) {
        List<CandleEntry> e = new ArrayList<>();
        for (int i = 0; i < c.size(); i++) {
            Candle cv = c.get(i);
            e.add(new CandleEntry(i, (float)cv.high, (float)cv.low, (float)cv.open, (float)cv.close));
        }
        CandleDataSet s = new CandleDataSet(e, "Prices");
        s.setDecreasingColor(COLOR_DOWN);  s.setDecreasingPaintStyle(Paint.Style.FILL);
        s.setIncreasingColor(COLOR_UP);    s.setIncreasingPaintStyle(Paint.Style.FILL);
        s.setNeutralColor(Color.GRAY);     s.setShadowColorSameAsCandle(true);
        s.setShadowWidth(1.5f);            s.setBarSpace(0.1f);
        s.setDrawValues(false);            s.setHighlightEnabled(true);
        s.setHighLightColor(COLOR_HIGHLIGHT); s.setHighlightLineWidth(1f);
        s.enableDashedHighlightLine(8f, 4f, 0f);
        s.setAxisDependency(YAxis.AxisDependency.LEFT);
        return new CandleData(s);
    }

    private CandleData generateOhlcBarData(ArrayList<Candle> c) {
        List<CandleEntry> e = new ArrayList<>();
        for (int i = 0; i < c.size(); i++) {
            Candle cv = c.get(i);
            e.add(new CandleEntry(i, (float)cv.high, (float)cv.low, (float)cv.open, (float)cv.close));
        }
        CandleDataSet s = new CandleDataSet(e, "Prices");
        s.setDecreasingColor(COLOR_DOWN);  s.setDecreasingPaintStyle(Paint.Style.STROKE);
        s.setIncreasingColor(COLOR_UP);    s.setIncreasingPaintStyle(Paint.Style.STROKE);
        s.setNeutralColor(Color.GRAY);     s.setShadowColorSameAsCandle(true);
        s.setShadowWidth(1.5f);            s.setBarSpace(0.3f);
        s.setDrawValues(false);            s.setHighlightEnabled(true);
        s.setHighLightColor(COLOR_HIGHLIGHT); s.setHighlightLineWidth(1f);
        s.enableDashedHighlightLine(8f, 4f, 0f);
        s.setAxisDependency(YAxis.AxisDependency.LEFT);
        return new CandleData(s);
    }

    private LineDataSet generateLineData(ArrayList<Candle> c) {
        List<Entry> e = new ArrayList<>();
        for (int i = 0; i < c.size(); i++) e.add(new Entry(i, (float)c.get(i).close));
        LineDataSet s = new LineDataSet(e, "Close");
        s.setColor(COLOR_LINE);    s.setLineWidth(1.8f);
        s.setDrawCircles(false);   s.setDrawValues(false);
        s.setMode(LineDataSet.Mode.LINEAR);
        s.setDrawFilled(true);     s.setFillColor(COLOR_LINE); s.setFillAlpha(25);
        s.setHighlightEnabled(true); s.setHighLightColor(COLOR_HIGHLIGHT);
        s.setHighlightLineWidth(1f); s.enableDashedHighlightLine(8f, 4f, 0f);
        s.setAxisDependency(YAxis.AxisDependency.LEFT);
        return s;
    }

    private BarData generateVolumeData(ArrayList<Candle> c) {
        List<BarEntry> e = new ArrayList<>();
        int[] colors = new int[c.size()];
        for (int i = 0; i < c.size(); i++) {
            Candle cv = c.get(i);
            e.add(new BarEntry(i, cv.volume));
            if      (cv.close > cv.open) colors[i] = COLOR_VOL_UP;
            else if (cv.close < cv.open) colors[i] = COLOR_VOL_DOWN;
            else                         colors[i] = COLOR_VOL_NEUTRAL;
        }
        BarDataSet s = new BarDataSet(e, "Volume");
        s.setColors(colors);
        s.setAxisDependency(YAxis.AxisDependency.RIGHT);
        s.setDrawValues(false);
        s.setHighlightEnabled(false);
        BarData bd = new BarData(s);
        bd.setBarWidth(0.8f);
        return bd;
    }

    // ── Utility ───────────────────────────────────────────────────────
    private int dpToPx(int dp) {
        return Math.round(dp * activityContext.getResources().getDisplayMetrics().density);
    }

    private String formatVolume(long v) {
        if (v >= 1_000_000) return String.format(Locale.US, "%dM", v / 1_000_000);
        if (v >= 1_000)     return String.format(Locale.US, "%dK", v / 1_000);
        return String.format(Locale.US, "%d", v);
    }

    // ── Stream update ─────────────────────────────────────────────────
    private void streamUpdate(List<Candle> chunk) {
        if (chunk == null || chunk.isEmpty()) return;
        Candle live = chunk.get(0);
        synchronized (allCandles) {
            if (allCandles.isEmpty() && !done) { streamBuffer.addAll(chunk); return; }
            if (allCandles.isEmpty()) return;
            Candle last = allCandles.get(allCandles.size() - 1);
            if (last.timestamp + interval.interval > live.timestamp) {
                allCandles.set(allCandles.size() - 1, new Candle(
                        last.timestamp, last.open,
                        Math.max(live.high, last.high),
                        Math.min(live.low, last.low),
                        live.close, last.volume + live.volume));
            } else {
                allCandles.add(new Candle(
                        last.timestamp + interval.interval,
                        live.open, live.high, live.low, live.close, live.volume));
            }
        }
        triggerChartUpdate(true);
    }

    // ── SessionCallback ───────────────────────────────────────────────
    @Override
    public void onDataReceived(DataType msgType, Object parsedData) {
        if (msgType == DataType.TICKER_ERROR) {
            mainHandler.post(() -> Toast.makeText(activityContext,
                    (String) parsedData, Toast.LENGTH_SHORT).show());
            flushRequests();
            return;
        }
        if (!(parsedData instanceof PriceChunk)) return;
        PriceChunk chunk = (PriceChunk) parsedData;
        if (!reqIds.contains(chunk.reqId)) return;

        switch (msgType) {
            case TICKER_STREAM:
                streamUpdate(chunk.chunk);
                if (chainedListener != null) {
                    synchronized (allCandles) {
                        if (!allCandles.isEmpty())
                            chainedListener.onDataReceived(DataType.MARKET_DATA,
                                    allCandles.get(allCandles.size() - 1).close);
                    }
                }
                break;
            case TICKER_SNAPSHOT:
                addChunk(chunk.chunk);
                break;
            case TICKER_REQUEST_DONE:
                reqIds.remove(chunk.reqId);
                if (chainedListener != null) {
                    done = true;
                    for (Candle buffed : streamBuffer) streamUpdate(List.of(buffed));
                    synchronized (allCandles) {
                        if (!allCandles.isEmpty())
                            chainedListener.onDataReceived(DataType.MARKET_DATA,
                                    allCandles.get(allCandles.size() - 1).close);
                    }
                }
                break;
        }
    }

    @Override public void onActionRequired(int a, @Nullable Object d) {}

    // ── Public lifecycle API ──────────────────────────────────────────
    public void clearChart() {
        allCandles.clear();
        done               = false;
        streamBuffer.clear();
        isPinnedToRight    = true;
        initialViewApplied = false;
        savedLowestX       = -1f;
        savedHighestX      = -1f;
        if (currentPriceLine != null) {
            chart.getAxisLeft().removeLimitLine(currentPriceLine);
            currentPriceLine = null;
        }
    }

    public void addToCurrentRequest(int reqId) { this.reqIds.add(reqId); }

    public void flushRequests() {
        NetworkClient.getInstance(null).getSessionManager()
                .discardRequests(reqIds.stream().mapToInt(Integer::intValue).toArray());
        this.reqIds.clear();
    }

    public void bindListener(SessionCallback l) { this.chainedListener = l; }
    public boolean isDone() { return done; }
    public void setInterval(StockDataHelper.Timeframe tf) { this.interval = tf; }

    public ArrayList<Candle> getAllCandles(){
        synchronized (allCandles){
        return new ArrayList<>(allCandles);
        }
    }
}