package com.example.gutapp.data;

import static com.example.gutapp.ui.ChartActivity.CHART_LOG_TAG;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.core.widget.NestedScrollView;

import com.example.gutapp.data.indicators.Indicator;
import com.example.gutapp.data.indicators.IndicatorRegistry;
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
import com.github.mikephil.charting.listener.OnChartValueSelectedListener;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

import lombok.Setter;

public class StockChart implements SessionCallback {

    // ── Chart display types ──────────────────────────────────────────
    public enum ChartType { CANDLE, BAR, LINE }

    // ── Colors ───────────────────────────────────────────────────────
    private static final int COLOR_UP          = Color.parseColor("#00FF88");
    private static final int COLOR_DOWN        = Color.parseColor("#FF4444");
    private static final int COLOR_LINE        = Color.parseColor("#2196F3");
    private static final int COLOR_VOL_UP      = Color.argb(77,  38, 166, 154);
    private static final int COLOR_VOL_DOWN    = Color.argb(77, 239,  83,  80);
    private static final int COLOR_VOL_NEUTRAL = Color.argb(60, 120, 144, 156);
    private static final int COLOR_BACKGROUND  = Color.parseColor("#121111");
    private static final int COLOR_AXIS_TEXT   = Color.parseColor("#78909C");
    private static final int COLOR_GRID        = Color.argb(20, 255, 255, 255);
    private static final int COLOR_HIGHLIGHT   = Color.parseColor("#FFFFFF");

    // ── Core fields ───────────────────────────────────────────────────
    private final CombinedChart chart;

    /**
     * Optional sub-chart views (one per sub-chart indicator like RSI, MACD).
     * Pass them via attachSubChart() from your Activity after inflating.
     * Each LineChart here is styled and updated alongside the main chart.
     */
    private final List<LineChart> subCharts = new ArrayList<>();
    @Setter
    private Optional<View> subChartsContainer = Optional.empty();

    private final CopyOnWriteArrayList<Candle> allCandles = new CopyOnWriteArrayList<>();
    private final Set<Integer> reqIds = ConcurrentHashMap.<Integer>newKeySet();
    private StockDataHelper.Timeframe interval;

    private ChartType currentChartType = ChartType.CANDLE;

    private final AtomicBoolean isUpdatePending = new AtomicBoolean(false);
    private final Context activityContext;
    private volatile boolean done = false;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final List<Candle> streamBuffer = new ArrayList<>();

    @Nullable
    private SessionCallback chainedListener = null;
    @Nullable
    private LimitLine currentPriceLine = null;

    // ── Stable-scroll state ───────────────────────────────────────────
    // We track whether the user is "pinned to the right edge" to decide
    // whether to auto-scroll on live updates, or leave the view where it is.
    private boolean isPinnedToRight = true;
    private float   savedLowestX    = -1f;
    private float   savedHighestX   = -1f;

    // ── Constructor ───────────────────────────────────────────────────
    public StockChart(CombinedChart chart, Context context) {
        this.chart = chart;
        this.activityContext = context;
    }

    // ── Sub-chart management ──────────────────────────────────────────
    /**
     * Call from Activity after layout inflation.
     * Order matters: subCharts.get(0) = first sub-chart indicator, etc.
     * The Activity can re-call this when indicator panel changes.
     */
    public void attachSubChart(LineChart subChart) {
        setupSubChart(subChart);
        subCharts.add(subChart);
    }

    public void clearSubCharts() {
        subCharts.clear();
    }

    private void setupSubChart(LineChart sub) {
        sub.setBackgroundColor(COLOR_BACKGROUND);
        sub.setDrawGridBackground(false);
        sub.setAutoScaleMinMaxEnabled(false);
        sub.setTouchEnabled(false); // driven by main chart sync
        sub.getLegend().setEnabled(false);
        sub.getDescription().setEnabled(false);

        XAxis xAxis = sub.getXAxis();
        xAxis.setDrawGridLines(false);
        xAxis.setDrawLabels(false);
        xAxis.setDrawAxisLine(false);

        YAxis left = sub.getAxisLeft();
        left.setEnabled(false);

        YAxis right = sub.getAxisRight();
        right.setDrawGridLines(true);
        right.setGridColor(COLOR_GRID);
        right.setGridLineWidth(0.5f);
        right.setTextColor(COLOR_AXIS_TEXT);
        right.setTextSize(9f);
        right.setLabelCount(4, false);
        right.setDrawAxisLine(false);
    }

    // ── Public type/indicator API ─────────────────────────────────────
    public void setChartType(ChartType type) {
        this.currentChartType = type;
        triggerChartUpdate(true);
    }

    public ChartType getChartType() { return currentChartType; }

    /** Called from Activity when indicator settings change */
    public void applyIndicators() {
        triggerChartUpdate(true);
    }

    // ── Zoom helpers (called from Activity zoom buttons) ─────────────
    public void zoomIn() {
        chart.zoom(1.5f, 1f, chart.getWidth() / 2f, chart.getHeight() / 2f);
        chart.invalidate(); // Refreshes the view
    }

    public void zoomOut() {
        chart.zoom(1f / 1.5f, 1f, chart.getWidth() / 2f, chart.getHeight() / 2f);
        chart.invalidate();
    }

    public void zoomReset() {
        ArrayList<Candle> snap = new ArrayList<>(allCandles);
        if (snap.isEmpty()) return;
        int total = snap.size();
        float desired = 60f;
        if (total > desired) {
            float scaleX = total / desired;
            chart.zoom(scaleX, 1f, 0f, 0f);
            chart.moveViewToX(total - 1);
        } else {
            chart.fitScreen();
        }
        isPinnedToRight = true;
        chart.postInvalidate();
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
                CombinedChart.DrawOrder.BAR,
                CombinedChart.DrawOrder.BUBBLE,
                CombinedChart.DrawOrder.LINE,
                CombinedChart.DrawOrder.SCATTER,
                CombinedChart.DrawOrder.CANDLE
        });

        YAxis leftAxis = chart.getAxisLeft();
        leftAxis.setDrawGridLines(true);
        leftAxis.setGridColor(COLOR_GRID);
        leftAxis.setGridLineWidth(0.5f);
        leftAxis.setLabelCount(6, false);
        leftAxis.setTextColor(COLOR_AXIS_TEXT);
        leftAxis.setTextSize(10f);
        leftAxis.setSpaceTop(15f);
        leftAxis.setSpaceBottom(15f);
        leftAxis.setDrawAxisLine(false);
        leftAxis.setPosition(YAxis.YAxisLabelPosition.OUTSIDE_CHART);

        YAxis rightAxis = chart.getAxisRight();
        rightAxis.setEnabled(true);
        rightAxis.setDrawGridLines(false);
        rightAxis.setDrawLabels(false);
        rightAxis.setDrawAxisLine(false);
        rightAxis.setAxisMinimum(0f);

        XAxis xAxis = chart.getXAxis();
        xAxis.setDrawGridLines(false);
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setSpaceMin(15f);
        xAxis.setSpaceMax(15f);
        xAxis.setTextColor(COLOR_AXIS_TEXT);
        xAxis.setTextSize(10f);
        xAxis.setDrawAxisLine(false);
        xAxis.setAvoidFirstLastClipping(true);

        chart.getLegend().setEnabled(false);
        chart.getDescription().setEnabled(false);
        chart.setVisibleXRangeMinimum(10f);
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
                    String dateStr = sdf.format(new Date(c.timestamp * 1000L));
                    boolean isUp = c.close >= c.open;
                    String arrow = isUp ? "▲" : "▼";
                    String info = String.format(Locale.US,
                            "%s  %s\nO: %.5f   H: %.5f\nL: %.5f   C: %.5f\nVol: %s",
                            arrow, dateStr, c.open, c.high, c.low, c.close,
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
    }

    // ── Data ingestion ────────────────────────────────────────────────
    public void addChunk(List<Candle> chunk) {
        if (chunk == null || chunk.isEmpty()) return;
        synchronized (allCandles) {
            for (Candle newCandle : chunk) {
                if (!allCandles.isEmpty()) {
                    Candle last = allCandles.get(allCandles.size() - 1);
                    if (last.timestamp == newCandle.timestamp) {
                        allCandles.set(allCandles.size() - 1, newCandle);
                        continue;
                    }
                }
                allCandles.add(newCandle);
            }
        }
        triggerChartUpdate(false);
    }

    // ── Throttled update trigger ──────────────────────────────────────
    private void triggerChartUpdate(boolean isLiveUpdate) {
        if (chart == null) return;
        if (isUpdatePending.compareAndSet(false, true)) {
            mainHandler.postDelayed(() -> {
                // Save viewport position BEFORE touching data
                if (isLiveUpdate && chart.getData() != null) {
                    savedLowestX  = chart.getLowestVisibleX();
                    savedHighestX = chart.getHighestVisibleX();
                    // Pinned = user is at the rightmost candle
                    float totalCount = allCandles.size();
                    isPinnedToRight = (savedHighestX >= totalCount - 3);
                }
                updateChartData(isLiveUpdate);
                isUpdatePending.set(false);
            }, 150);
        }
    }

    // ── Core render ───────────────────────────────────────────────────
    private void updateChartData(boolean isLiveUpdate) {
        Log.i(CHART_LOG_TAG, "Updating chart — type=" + currentChartType + " live=" + isLiveUpdate);

        ArrayList<Candle> safeCopy = new ArrayList<>(allCandles);
        if (safeCopy.isEmpty()) return;
        Collections.sort(safeCopy, (c1, c2) -> Long.compare(c1.timestamp, c2.timestamp));

        // ── Build price layer ─────────────────────────────────────
        CombinedData data = new CombinedData();
        data.setData(generateVolumeData(safeCopy));

        LineData lineData = new LineData();

        // Overlay indicators
        for (Indicator ind : IndicatorRegistry.getInstance().getEnabledOverlays()) {
            Indicator.Result result = ind.compute(safeCopy);
            for (LineDataSet set : result.overlayLines) lineData.addDataSet(set);
        }

        switch (currentChartType) {
            case CANDLE: data.setData(generateCandleData(safeCopy)); break;
            case BAR:    data.setData(generateOhlcBarData(safeCopy)); break;
            case LINE:   lineData.addDataSet(generateLineData(safeCopy)); break;
        }
        data.setData(lineData);

        // ── X-axis formatter ──────────────────────────────────────
        chart.getXAxis().setValueFormatter(new ValueFormatter() {
            private final SimpleDateFormat dailyFmt = new SimpleDateFormat("MMM dd", Locale.getDefault());
            private final SimpleDateFormat timeFmt  = new SimpleDateFormat("HH:mm",  Locale.getDefault());
            @Override
            public String getAxisLabel(float value, AxisBase axis) {
                int index = Math.round(value);
                if (index < 0 || index >= safeCopy.size()) return "";
                Date d = new Date(safeCopy.get(index).timestamp * 1000L);
                return (interval == StockDataHelper.Timeframe.DAILY)
                        ? dailyFmt.format(d) : timeFmt.format(d);
            }
        });

        chart.setData(data);
        updateCurrentPriceLine(safeCopy.get(safeCopy.size() - 1));
        chart.getData().notifyDataChanged();
        chart.notifyDataSetChanged();

        // ── Viewport preservation (NO JUMP on live updates) ───────
        if (!isLiveUpdate) {
            // Initial load — animate to right edge
            int totalCount = safeCopy.size();
            float desiredVisible = 60f;
            if (totalCount > desiredVisible) {
                chart.setVisibleXRangeMinimum(10f);
                float scaleX = totalCount / desiredVisible;
                chart.zoom(scaleX, 1f, 0f, 0f);
                chart.moveViewToX(totalCount - 1);
            } else {
                chart.fitScreen();
            }
            float maxVol = 0;
            for (Candle c : safeCopy) if (c.volume > maxVol) maxVol = c.volume;
            chart.getAxisRight().setAxisMaximum(maxVol * 10f);
            isPinnedToRight = true;
        } else {
            // Live update — only move if pinned to right
            if (isPinnedToRight) {
                // Scroll to show the newest candle without zooming
                chart.moveViewToX(safeCopy.size() - 1);
            } else {
                // User is scrolled back — restore their exact viewport
                if (savedLowestX >= 0) {
                    chart.moveViewToX(savedLowestX);
                }
            }
            // Recalculate Y without resetting X
            chart.getTransformer(YAxis.AxisDependency.LEFT).prepareMatrixValuePx(
                    chart.getXAxis().mAxisMinimum,
                    chart.getXAxis().mAxisRange,
                    chart.getAxisLeft().mAxisRange,
                    chart.getAxisLeft().mAxisMinimum
            );
            chart.calculateOffsets();
        }

        chart.postInvalidate();

        // ── Update sub-charts ─────────────────────────────────────
        updateSubCharts(safeCopy);
    }

    // ── Sub-chart rendering ───────────────────────────────────────────
    private void updateSubCharts(ArrayList<Candle> safeCopy) {
        if(subChartsContainer.isEmpty()) return;
        List<Indicator> subIndicators = IndicatorRegistry.getInstance().getEnabledSubCharts();

        for (int i = 0; i < subCharts.size(); i++) {
            LineChart subChart = subCharts.get(i);

            if (i >= subIndicators.size()) {
                subChart.setVisibility(View.GONE);
                subChart.clear();
                continue;
            }

            Indicator ind = subIndicators.get(i);
            Indicator.Result result = ind.compute(safeCopy);

            if (result.subChartLines.isEmpty()) {
                subChart.setVisibility(View.GONE);
                continue;
            }

            subChart.setVisibility(View.VISIBLE);

            // Sync X-axis formatter with main chart
            subChart.getXAxis().setValueFormatter(chart.getXAxis().getValueFormatter());

            LineData subData = new LineData();
            for (LineDataSet set : result.subChartLines) subData.addDataSet(set);
            subChart.setData(subData);

            // Apply Y range hints
            if (!Float.isNaN(result.subChartMin) && !Float.isNaN(result.subChartMax)) {
                subChart.getAxisRight().setAxisMinimum(result.subChartMin);
                subChart.getAxisRight().setAxisMaximum(result.subChartMax);
            } else {
                subChart.getAxisRight().resetAxisMinimum();
                subChart.getAxisRight().resetAxisMaximum();
            }

            //set up indicator description
            Description desc = new Description();
            desc.setText(ind.getDisplayName());
            desc.setTextColor(Color.parseColor("#78909C")); // Use a dim grey for a clean look
            desc.setTextSize(12f);
            desc.setPosition(140f, 40f);
            subChart.setDescription(desc);

            subChart.getData().notifyDataChanged();
            subChart.notifyDataSetChanged();

            // Mirror main chart's X viewport
            try {
                float lo = chart.getLowestVisibleX();
                float hi = chart.getHighestVisibleX();
                subChart.setVisibleXRange(hi - lo, hi - lo);
                subChart.moveViewToX(lo);
            } catch (Exception ignored) {}

            subChart.postInvalidate();
        }
        if(subIndicators.isEmpty()){
            subChartsContainer.get().setVisibility(View.GONE);
            setScrollViewWeight(0f, subChartsContainer.get());
        }
        else{
            subChartsContainer.get().setVisibility(View.VISIBLE);
            setScrollViewWeight(1f, subChartsContainer.get());
        }
    }

    private static void setScrollViewWeight(float weight, View scrollView) {
        // Get the current layout params
        LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) scrollView.getLayoutParams();

        // Update the weight
        params.weight = weight;

        // If weight is 0, height should be 0. If weight > 0, height should be 0dp to let weight work.
        params.height = 0;

        // Apply the changes
        scrollView.setLayoutParams(params);
    }

    // ── Current-price dashed line ─────────────────────────────────────
    private void updateCurrentPriceLine(Candle latest) {
        YAxis leftAxis = chart.getAxisLeft();
        if (currentPriceLine != null) leftAxis.removeLimitLine(currentPriceLine);

        boolean isUp = latest.close >= latest.open;
        int lineColor = isUp ? COLOR_UP : COLOR_DOWN;

        currentPriceLine = new LimitLine((float) latest.close,
                String.format(Locale.US, "%.5f", latest.close));
        currentPriceLine.setLineColor(lineColor);
        currentPriceLine.setLineWidth(1f);
        currentPriceLine.enableDashedLine(8f, 4f, 0f);
        currentPriceLine.setTextColor(lineColor);
        currentPriceLine.setTextSize(9f);
        currentPriceLine.setLabelPosition(LimitLine.LimitLabelPosition.RIGHT_TOP);

        leftAxis.addLimitLine(currentPriceLine);
        leftAxis.setDrawLimitLinesBehindData(false);
    }

    // ── Price data generators ─────────────────────────────────────────
    private CandleData generateCandleData(ArrayList<Candle> candles) {
        List<CandleEntry> entries = new ArrayList<>();
        for (int i = 0; i < candles.size(); i++) {
            Candle c = candles.get(i);
            entries.add(new CandleEntry(i, (float) c.high, (float) c.low, (float) c.open, (float) c.close));
        }
        CandleDataSet set = new CandleDataSet(entries, "Prices");
        set.setDecreasingColor(COLOR_DOWN);
        set.setDecreasingPaintStyle(Paint.Style.FILL);
        set.setIncreasingColor(COLOR_UP);
        set.setIncreasingPaintStyle(Paint.Style.FILL);
        set.setNeutralColor(Color.GRAY);
        set.setShadowColorSameAsCandle(true);
        set.setShadowWidth(1.5f);
        set.setBarSpace(0.1f);
        set.setDrawValues(false);
        set.setHighlightEnabled(true);
        set.setHighLightColor(COLOR_HIGHLIGHT);
        set.setHighlightLineWidth(1f);
        set.enableDashedHighlightLine(8f, 4f, 0f);
        set.setAxisDependency(YAxis.AxisDependency.LEFT);
        return new CandleData(set);
    }

    private CandleData generateOhlcBarData(ArrayList<Candle> candles) {
        List<CandleEntry> entries = new ArrayList<>();
        for (int i = 0; i < candles.size(); i++) {
            Candle c = candles.get(i);
            entries.add(new CandleEntry(i, (float) c.high, (float) c.low, (float) c.open, (float) c.close));
        }
        CandleDataSet set = new CandleDataSet(entries, "Prices");
        set.setDecreasingColor(COLOR_DOWN);
        set.setDecreasingPaintStyle(Paint.Style.STROKE);
        set.setIncreasingColor(COLOR_UP);
        set.setIncreasingPaintStyle(Paint.Style.STROKE);
        set.setNeutralColor(Color.GRAY);
        set.setShadowColorSameAsCandle(true);
        set.setShadowWidth(1.5f);
        set.setBarSpace(0.3f);
        set.setDrawValues(false);
        set.setHighlightEnabled(true);
        set.setHighLightColor(COLOR_HIGHLIGHT);
        set.setHighlightLineWidth(1f);
        set.enableDashedHighlightLine(8f, 4f, 0f);
        set.setAxisDependency(YAxis.AxisDependency.LEFT);
        return new CandleData(set);
    }

    private LineDataSet generateLineData(ArrayList<Candle> candles) {
        List<Entry> entries = new ArrayList<>();
        for (int i = 0; i < candles.size(); i++) {
            entries.add(new Entry(i, (float) candles.get(i).close));
        }
        LineDataSet set = new LineDataSet(entries, "Close");
        set.setColor(COLOR_LINE);
        set.setLineWidth(1.8f);
        set.setDrawCircles(false);
        set.setDrawValues(false);
        set.setMode(LineDataSet.Mode.LINEAR);
        set.setDrawFilled(true);
        set.setFillColor(COLOR_LINE);
        set.setFillAlpha(25);
        set.setHighlightEnabled(true);
        set.setHighLightColor(COLOR_HIGHLIGHT);
        set.setHighlightLineWidth(1f);
        set.enableDashedHighlightLine(8f, 4f, 0f);
        set.setAxisDependency(YAxis.AxisDependency.LEFT);
        return set;
    }

    private BarData generateVolumeData(ArrayList<Candle> candles) {
        List<BarEntry> entries = new ArrayList<>();
        int[] colors = new int[candles.size()];
        for (int i = 0; i < candles.size(); i++) {
            Candle c = candles.get(i);
            entries.add(new BarEntry(i, c.volume));
            if (c.close > c.open)      colors[i] = COLOR_VOL_UP;
            else if (c.close < c.open) colors[i] = COLOR_VOL_DOWN;
            else                       colors[i] = COLOR_VOL_NEUTRAL;
        }
        BarDataSet set = new BarDataSet(entries, "Volume");
        set.setColors(colors);
        set.setAxisDependency(YAxis.AxisDependency.RIGHT);
        set.setDrawValues(false);
        set.setHighlightEnabled(false);
        BarData barData = new BarData(set);
        barData.setBarWidth(0.8f);
        return barData;
    }

    // ── Stream / live update ──────────────────────────────────────────
    private void streamUpdate(List<Candle> chunk) {
        if (chunk == null || chunk.isEmpty()) return;
        Candle liveData = chunk.get(0);

        synchronized (allCandles) {
            if (allCandles.isEmpty() && !done) {
                streamBuffer.addAll(chunk);
                return;
            }
            if (allCandles.isEmpty()) return;

            Candle lastCandle = allCandles.get(allCandles.size() - 1);
            if (lastCandle.timestamp + interval.interval > liveData.timestamp) {
                allCandles.set(allCandles.size() - 1, new Candle(
                        lastCandle.timestamp,
                        lastCandle.open,
                        Math.max(liveData.high, lastCandle.high),
                        Math.min(liveData.low,  lastCandle.low),
                        liveData.close,
                        lastCandle.volume + liveData.volume
                ));
            } else {
                allCandles.add(new Candle(
                        lastCandle.timestamp + interval.interval,
                        liveData.open, liveData.high, liveData.low,
                        liveData.close, liveData.volume
                ));
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

    @Override
    public void onActionRequired(int actionType, @Nullable Object data) {}

    // ── Helpers ───────────────────────────────────────────────────────
    private String formatVolume(float vol) {
        if (vol >= 1_000_000) return String.format(Locale.US, "%.2fM", vol / 1_000_000);
        if (vol >= 1_000)     return String.format(Locale.US, "%.1fK", vol / 1_000);
        return String.format(Locale.US, "%.0f", vol);
    }

    public void clearChart() {
        allCandles.clear();
        done = false;
        streamBuffer.clear();
        isPinnedToRight = true;
        savedLowestX = -1f;
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
    public boolean isDone()                     { return done; }
    public void setInterval(StockDataHelper.Timeframe tf) { this.interval = tf; }
}