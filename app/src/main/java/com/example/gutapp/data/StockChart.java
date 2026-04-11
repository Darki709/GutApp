package com.example.gutapp.data;

import static com.example.gutapp.ui.ChartActivity.CHART_LOG_TAG;

import android.content.Context;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;

import com.example.gutapp.data.models.Candle;
import com.example.gutapp.data.models.PriceChunk;
import com.example.gutapp.database.StockDataHelper;
import com.example.gutapp.session.DataType;
import com.example.gutapp.session.NetworkClient;
import com.example.gutapp.session.SessionCallback;
import com.example.gutapp.ui.fragments.IndicatorsPanel;
import com.github.mikephil.charting.charts.CombinedChart;
import com.github.mikephil.charting.components.AxisBase;
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
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

public class StockChart implements SessionCallback {

    // ─────────────────────────────────────────────
    //  Chart display types
    // ─────────────────────────────────────────────
    public enum ChartType {
        CANDLE,   // Classic Japanese candlestick
        BAR,      // OHLC bar chart (rendered via CandleDataSet with 0-width body)
        LINE      // Close-price line with gradient fill
    }

    // ─────────────────────────────────────────────
    //  Color constants  (dark theme, TradingView-like)
    // ─────────────────────────────────────────────
    // ── Core Brand Colors ────────────────────────────────
    private static final int COLOR_UP          = Color.parseColor("#00FF88"); // Teal-Green (Buy/Up)
    private static final int COLOR_DOWN        = Color.parseColor("#FF4444"); // Red (Sell/Down)
    private static final int COLOR_LINE        = Color.parseColor("#2196F3"); // Blue (Price Line)

    // ── Backgrounds & Fills (Uses Alpha for depth) ───────
// Line fill: 15% opacity of Blue
    private static final int COLOR_LINE_FILL   = Color.argb(38, 33, 150, 243);
    // Vol Up: 30% opacity of Green
    private static final int COLOR_VOL_UP      = Color.argb(77, 38, 166, 154);
    // Vol Down: 30% opacity of Red
    private static final int COLOR_VOL_DOWN    = Color.argb(77, 239, 83, 80);
    // Neutral Vol: Dim Gray-Blue
    private static final int COLOR_VOL_NEUTRAL = Color.argb(60, 120, 144, 156);

    // ── UI Elements ──────────────────────────────────────
    private static final int COLOR_BACKGROUND  = Color.parseColor("#121111"); // Dark Chart Background
    private static final int COLOR_AXIS_TEXT   = Color.parseColor("#78909C"); // Dimmer text for readability
    private static final int COLOR_GRID        = Color.argb(20, 255, 255, 255); // Very subtle grid (8% opacity)
    private static final int COLOR_HIGHLIGHT   = Color.parseColor("#FFFFFF"); // Crosshair color
    private static final int COLOR_PRICE_LINE  = Color.argb(200, 255, 255, 255); // Current price indicator

    // ─────────────────────────────────────────────
    //  Core fields
    // ─────────────────────────────────────────────
    private final CombinedChart chart;
    private final CopyOnWriteArrayList<Candle> allCandles = new CopyOnWriteArrayList<>();
    private final Set<Integer> reqIds = ConcurrentHashMap.<Integer>newKeySet();
    private StockDataHelper.Timeframe interval;

    private ChartType currentChartType = ChartType.CANDLE; // default

    private final AtomicBoolean isUpdatePending = new AtomicBoolean(false);
    private final Context activityContext;
    private volatile boolean done = false;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private final List<Candle> streamBuffer = new ArrayList<>();

    @Nullable
    private SessionCallback chainedListener = null;

    // Current-price limit line (updated on every stream tick)
    @Nullable
    private LimitLine currentPriceLine = null;

    private IndicatorsPanel.IndicatorSettings indicatorSettings =
            new IndicatorsPanel.IndicatorSettings();

    // ─────────────────────────────────────────────
    //  Constructor
    // ─────────────────────────────────────────────
    public StockChart(CombinedChart chart, Context context) {
        this.chart = chart;
        this.activityContext = context;
    }

    // ─────────────────────────────────────────────
    //  Public API: switch chart type at runtime
    // ─────────────────────────────────────────────
    /**
     * Call this from your toolbar buttons (Candle / Bar / Line).
     * Triggers an immediate redraw with the new type applied.
     */
    public void setChartType(ChartType type) {
        this.currentChartType = type;
        triggerChartUpdate(true);
    }

    public ChartType getChartType() {
        return currentChartType;
    }

    // ─────────────────────────────────────────────
    //  Chart setup
    // ─────────────────────────────────────────────
    public void setupChart(TextView candleDataTextView) {
        chart.setBackgroundColor(COLOR_BACKGROUND);
        chart.setAutoScaleMinMaxEnabled(true);
        chart.setDragEnabled(true);
        chart.setScaleEnabled(true);
        chart.setDrawGridBackground(false);
        chart.setPinchZoom(true);
        chart.setDoubleTapToZoomEnabled(true);
        chart.setKeepPositionOnRotation(true);

        // Draw order: volume bars behind price data, candle/line on top
        chart.setDrawOrder(new CombinedChart.DrawOrder[]{
                CombinedChart.DrawOrder.BAR,
                CombinedChart.DrawOrder.BUBBLE,
                CombinedChart.DrawOrder.LINE,
                CombinedChart.DrawOrder.SCATTER,
                CombinedChart.DrawOrder.CANDLE
        });

        // ── Left axis (price) ──────────────────────────────
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

        // ── Right axis (volume — hidden labels, used only for scaling) ──
        YAxis rightAxis = chart.getAxisRight();
        rightAxis.setEnabled(true);
        rightAxis.setDrawGridLines(false);
        rightAxis.setDrawLabels(false);
        rightAxis.setDrawAxisLine(false);
        rightAxis.setAxisMinimum(0f);

        // ── X axis ────────────────────────────────────────
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

        // ── Crosshair / selection ──────────────────────────
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
                            arrow, dateStr,
                            c.open, c.high,
                            c.low, c.close,
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

    // ─────────────────────────────────────────────
    //  Data ingestion (unchanged from your original)
    // ─────────────────────────────────────────────
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

    // ─────────────────────────────────────────────
    //  Update scheduling
    // ─────────────────────────────────────────────
    private void triggerChartUpdate(boolean initialized) {
        if (chart == null) return;
        if (isUpdatePending.compareAndSet(false, true)) {
            mainHandler.postDelayed(() -> {
                chart.highlightValue(null);
                updateChartData(initialized);
                isUpdatePending.set(false);
            }, 150);
        }
    }

    // ─────────────────────────────────────────────
    //  Core render — switches on currentChartType
    // ─────────────────────────────────────────────
    private void updateChartData(boolean initialized) {
        Log.i(CHART_LOG_TAG, "Updating chart display — type=" + currentChartType);

        ArrayList<Candle> safeCopy = new ArrayList<>(allCandles);
        if (safeCopy.isEmpty()) return;

        Collections.sort(safeCopy, (c1, c2) -> Long.compare(c1.timestamp, c2.timestamp));

        CombinedData data = new CombinedData();

        // Always add volume bars on right axis
        data.setData(generateVolumeData(safeCopy));
        LineData lineData = new LineData();

        if (indicatorSettings.maEnabled) {
            lineData.addDataSet(generateMALine(safeCopy, indicatorSettings.maPeriod,
                    Color.parseColor("#FFC107"), "MA"));
        }
        if (indicatorSettings.emaEnabled) {
            lineData.addDataSet(generateEMALine(safeCopy, indicatorSettings.emaPeriod,
                    Color.parseColor("#E91E63"), "EMA"));
        }
        if (indicatorSettings.bbEnabled) {
            // BB requires upper+lower bands — add two LineDataSets
            addBollingerBands(lineData, safeCopy, indicatorSettings.bbPeriod);
        }
        if (indicatorSettings.vwapEnabled) {
            lineData.addDataSet(generateVWAP(safeCopy, Color.parseColor("#AB47BC"), "VWAP"));
        }

        // Swap price layer based on type
        switch (currentChartType) {
            case CANDLE:
                data.setData(generateCandleData(safeCopy));
                break;
            case BAR:
                // MPAndroidChart has no native OHLC bar; we reuse CandleDataSet
                // but strip the body fill so only the high-low wick and open/close
                // tick marks are visible — exactly an OHLC bar appearance.
                data.setData(generateOhlcBarData(safeCopy));
                break;
            case LINE:
                lineData.addDataSet(generateLineData(safeCopy));
                break;
        }

        data.setData(lineData);

        chart.getXAxis().setValueFormatter(new ValueFormatter() {
            private final SimpleDateFormat dailyFmt = new SimpleDateFormat("MMM dd", Locale.getDefault());
            private final SimpleDateFormat timeFmt  = new SimpleDateFormat("HH:mm",  Locale.getDefault());

            @Override
            public String getAxisLabel(float value, AxisBase axis) {
                int index = Math.round(value);
                if (index < 0 || index >= safeCopy.size()) return "";
                long ts = safeCopy.get(index).timestamp;
                Date d = new Date(ts * 1000L);
                return (interval == StockDataHelper.Timeframe.DAILY)
                        ? dailyFmt.format(d) : timeFmt.format(d);
            }
        });

        //apply all the data into the chart
        chart.setData(data);

        // Current-price dashed line on left axis
        updateCurrentPriceLine(safeCopy.get(safeCopy.size() - 1));

        chart.getData().notifyDataChanged();
        chart.notifyDataSetChanged();

        int totalCount = safeCopy.size();
        if (!initialized) {
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
        } else {
            float lastVisible = chart.getLowestVisibleX();
            chart.moveViewToX(lastVisible);
            chart.getTransformer(YAxis.AxisDependency.LEFT).prepareMatrixValuePx(
                    chart.getXAxis().mAxisMinimum,
                    chart.getXAxis().mAxisRange,
                    chart.getAxisLeft().mAxisRange,
                    chart.getAxisLeft().mAxisMinimum
            );
            chart.calculateOffsets();
        }
        chart.postInvalidate();
    }

    // ─────────────────────────────────────────────
    //  Current-price horizontal dashed line
    // ─────────────────────────────────────────────
    private void updateCurrentPriceLine(Candle latest) {
        YAxis leftAxis = chart.getAxisLeft();

        // Remove old line
        if (currentPriceLine != null) {
            leftAxis.removeLimitLine(currentPriceLine);
        }

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

    // ─────────────────────────────────────────────
    //  Data generators
    // ─────────────────────────────────────────────

    /** Classic filled candlestick */
    private CandleData generateCandleData(ArrayList<Candle> candles) {
        List<CandleEntry> entries = new ArrayList<>();
        for (int i = 0; i < candles.size(); i++) {
            Candle c = candles.get(i);
            entries.add(new CandleEntry(i, (float) c.high, (float) c.low,
                    (float) c.open, (float) c.close));
        }

        CandleDataSet set = new CandleDataSet(entries, "Prices");
        set.setDecreasingColor(COLOR_DOWN);
        set.setDecreasingPaintStyle(Paint.Style.FILL);
        set.setIncreasingColor(COLOR_UP);
        set.setIncreasingPaintStyle(Paint.Style.FILL);
        set.setNeutralColor(Color.GRAY);
        set.setShadowColorSameAsCandle(true);
        set.setShadowWidth(1.5f);
        set.setBarSpace(0.1f);           // tight spacing like TradingView
        set.setDrawValues(false);
        set.setHighlightEnabled(true);
        set.setHighLightColor(COLOR_HIGHLIGHT);
        set.setHighlightLineWidth(1f);
        set.enableDashedHighlightLine(8f, 4f, 0f);
        set.setAxisDependency(YAxis.AxisDependency.LEFT);

        return new CandleData(set);
    }

    /**
     * OHLC bar chart — uses CandleDataSet with STROKE-only style.
     * The body border is drawn but not filled, so only the wick lines
     * and open/close horizontal ticks are visible (classic OHLC look).
     */
    private CandleData generateOhlcBarData(ArrayList<Candle> candles) {
        List<CandleEntry> entries = new ArrayList<>();
        for (int i = 0; i < candles.size(); i++) {
            Candle c = candles.get(i);
            entries.add(new CandleEntry(i, (float) c.high, (float) c.low,
                    (float) c.open, (float) c.close));
        }

        CandleDataSet set = new CandleDataSet(entries, "Prices");
        set.setDecreasingColor(COLOR_DOWN);
        set.setDecreasingPaintStyle(Paint.Style.STROKE); // stroke only = OHLC bar
        set.setIncreasingColor(COLOR_UP);
        set.setIncreasingPaintStyle(Paint.Style.STROKE); // stroke only = OHLC bar
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

    /** Close-price line with filled area below */
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

        // Gradient fill below the line
        set.setDrawFilled(true);
        set.setFillColor(COLOR_LINE);
        set.setFillAlpha(25);           // subtle area fill

        set.setHighlightEnabled(true);
        set.setHighLightColor(COLOR_HIGHLIGHT);
        set.setHighlightLineWidth(1f);
        set.enableDashedHighlightLine(8f, 4f, 0f);
        set.setAxisDependency(YAxis.AxisDependency.LEFT);

        return set;
    }

    /** Volume bars — color-coded by candle direction, scaled to right axis */
    private BarData generateVolumeData(ArrayList<Candle> candles) {
        List<BarEntry> entries = new ArrayList<>();
        int[] colors = new int[candles.size()];

        for (int i = 0; i < candles.size(); i++) {
            Candle c = candles.get(i);
            entries.add(new BarEntry(i, c.volume));

            if (c.close > c.open)       colors[i] = COLOR_VOL_UP;
            else if (c.close < c.open)  colors[i] = COLOR_VOL_DOWN;
            else                        colors[i] = COLOR_VOL_NEUTRAL;
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

    // ─────────────────────────────────────────────
    //  Stream / live update (unchanged logic)
    // ─────────────────────────────────────────────
    private void streamUpdate(List<Candle> chunk) {
        if (chunk == null || chunk.isEmpty()) return;
        Candle liveData = chunk.get(0);

        synchronized (allCandles) {
            if (allCandles.isEmpty() && !done) {
                streamBuffer.addAll(chunk);
                Log.d("CHART_DEBUG", "Buffering stream response until snapshot arrives...");
                return;
            }
            if (allCandles.isEmpty()) return;

            Candle lastCandle = allCandles.get(allCandles.size() - 1);
            if (lastCandle.timestamp + interval.interval > liveData.timestamp) {
                Candle updated = new Candle(
                        lastCandle.timestamp,
                        lastCandle.open,
                        Math.max(liveData.high, lastCandle.high),
                        Math.min(liveData.low, lastCandle.low),
                        liveData.close,
                        lastCandle.volume + liveData.volume
                );
                allCandles.set(allCandles.size() - 1, updated);
            } else {
                Candle newCandle = new Candle(
                        lastCandle.timestamp + interval.interval,
                        liveData.open, liveData.high, liveData.low,
                        liveData.close, liveData.volume
                );
                allCandles.add(newCandle);
            }
        }
        triggerChartUpdate(true);
    }

    // ─────────────────────────────────────────────
    //  SessionCallback
    // ─────────────────────────────────────────────
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
                        if (!allCandles.isEmpty()) {
                            chainedListener.onDataReceived(DataType.MARKET_DATA,
                                    allCandles.get(allCandles.size() - 1).close);
                        }
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
                    for (Candle buffed : streamBuffer) {
                        streamUpdate(List.of(buffed));
                    }
                    synchronized (allCandles) {
                        if (!allCandles.isEmpty()) {
                            chainedListener.onDataReceived(DataType.MARKET_DATA,
                                    allCandles.get(allCandles.size() - 1).close);
                        }
                    }
                }
                break;
        }
    }

    @Override
    public void onActionRequired(int actionType, @Nullable Object data) {}

    // ─────────────────────────────────────────────
    //  Helpers
    // ─────────────────────────────────────────────
    private String formatVolume(float vol) {
        if (vol >= 1_000_000) return String.format(Locale.US, "%.2fM", vol / 1_000_000);
        if (vol >= 1_000)     return String.format(Locale.US, "%.1fK", vol / 1_000);
        return String.format(Locale.US, "%.0f", vol);
    }

    // ─────────────────────────────────────────────
    //  Existing public API — unchanged
    // ─────────────────────────────────────────────
    public void clearChart() {
        allCandles.clear();
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

    public void bindListener(SessionCallback chainedListener) {
        this.chainedListener = chainedListener;
    }

    public boolean isDone() { return done; }

    public void setInterval(StockDataHelper.Timeframe interval) {
        this.interval = interval;
    }

    /**
     * Applies indicator settings to the chart overlays.
     * Called on the calling thread; posts the actual redraw to the main thread.
     */
    public void applyIndicators(IndicatorsPanel.IndicatorSettings settings) {
        this.indicatorSettings = settings;
        triggerChartUpdate(true);   // reuse your existing update pipeline
    }

    // ── MA line generator ─────────────────────────────────────────────
    private LineDataSet generateMALine(ArrayList<Candle> candles, int period, int color, String label) {
        List<Entry> entries = new ArrayList<>();
        for (int i = period - 1; i < candles.size(); i++) {
            double sum = 0;
            for (int j = i - period + 1; j <= i; j++) sum += candles.get(j).close;
            entries.add(new Entry(i, (float)(sum / period)));
        }
        LineDataSet set = new LineDataSet(entries, label);
        set.setColor(color);
        set.setLineWidth(1.4f);
        set.setDrawCircles(false);
        set.setDrawValues(false);
        set.setAxisDependency(YAxis.AxisDependency.LEFT);
        return set;
    }

    // ── EMA line generator ────────────────────────────────────────────
    private LineDataSet generateEMALine(ArrayList<Candle> candles, int period, int color, String label) {
        List<Entry> entries = new ArrayList<>();
        double multiplier = 2.0 / (period + 1);
        double ema = candles.get(0).close;
        for (int i = 1; i < candles.size(); i++) {
            ema = (candles.get(i).close - ema) * multiplier + ema;
            if (i >= period - 1) entries.add(new Entry(i, (float) ema));
        }
        LineDataSet set = new LineDataSet(entries, label);
        set.setColor(color);
        set.setLineWidth(1.4f);
        set.setDrawCircles(false);
        set.setDrawValues(false);
        set.setAxisDependency(YAxis.AxisDependency.LEFT);
        return set;
    }

    // ── Bollinger Bands ───────────────────────────────────────────────
    private void addBollingerBands(LineData data, ArrayList<Candle> candles, int period) {
        List<Entry> upper = new ArrayList<>(), lower = new ArrayList<>(), mid = new ArrayList<>();
        for (int i = period - 1; i < candles.size(); i++) {
            double sum = 0;
            for (int j = i - period + 1; j <= i; j++) sum += candles.get(j).close;
            double ma = sum / period;
            double variance = 0;
            for (int j = i - period + 1; j <= i; j++) {
                double diff = candles.get(j).close - ma;
                variance += diff * diff;
            }
            double stdDev = Math.sqrt(variance / period);
            upper.add(new Entry(i, (float)(ma + 2 * stdDev)));
            lower.add(new Entry(i, (float)(ma - 2 * stdDev)));
            mid.add(new Entry(i, (float) ma));
        }
        int bbColor = Color.parseColor("#4DD0E1");
        data.addDataSet(makeBBLine(upper, bbColor, "BB Upper"));
        data.addDataSet(makeBBLine(lower, bbColor, "BB Lower"));
        data.addDataSet(makeBBLine(mid,   Color.argb(120, 77, 208, 225), "BB Mid"));
    }

    private LineDataSet makeBBLine(List<Entry> entries, int color, String label) {
        LineDataSet set = new LineDataSet(entries, label);
        set.setColor(color);
        set.setLineWidth(1f);
        set.enableDashedLine(6f, 3f, 0f);
        set.setDrawCircles(false);
        set.setDrawValues(false);
        set.setAxisDependency(YAxis.AxisDependency.LEFT);
        return set;
    }

    // ── VWAP ─────────────────────────────────────────────────────────
    private LineDataSet generateVWAP(ArrayList<Candle> candles, int color, String label) {
        List<Entry> entries = new ArrayList<>();
        double cumTPV = 0, cumVol = 0;
        for (int i = 0; i < candles.size(); i++) {
            Candle c = candles.get(i);
            double typicalPrice = (c.high + c.low + c.close) / 3.0;
            cumTPV += typicalPrice * c.volume;
            cumVol += c.volume;
            if (cumVol > 0) entries.add(new Entry(i, (float)(cumTPV / cumVol)));
        }
        LineDataSet set = new LineDataSet(entries, label);
        set.setColor(color);
        set.setLineWidth(1.6f);
        set.setDrawCircles(false);
        set.setDrawValues(false);
        set.setAxisDependency(YAxis.AxisDependency.LEFT);
        return set;
    }
}