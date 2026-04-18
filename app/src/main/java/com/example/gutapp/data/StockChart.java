package com.example.gutapp.data;

import static com.example.gutapp.ui.ChartActivity.CHART_LOG_TAG;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
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

import lombok.Setter;

public class StockChart implements SessionCallback {

    public enum ChartType { CANDLE, BAR, LINE }

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

    private final CombinedChart chart;
    private final Context activityContext;

    /**
     * Dynamic sub-chart container.
     * Must be a LinearLayout (vertical). StockChart dynamically inflates/removes
     * LineChart children — one per active sub-chart indicator.
     * Pass this via setSubChartsContainer() from ChartActivity after layout inflation.
     * -- SETTER --
     * Pass the LinearLayout that will hold dynamic sub-chart LineChart views

     */
    @Setter
    @Nullable
    private LinearLayout subChartsContainer = null;
    @Setter
    @Nullable
    private NestedScrollView subChartsScroller = null;
    /** Dynamically created sub-chart views, keyed by instanceId */
    private final java.util.LinkedHashMap<String, LineChart> subChartViews = new java.util.LinkedHashMap<>();

    private final CopyOnWriteArrayList<Candle> allCandles = new CopyOnWriteArrayList<>();
    private final Set<Integer> reqIds = ConcurrentHashMap.<Integer>newKeySet();
    private StockDataHelper.Timeframe interval;
    private ChartType currentChartType = ChartType.CANDLE;
    private final AtomicBoolean isUpdatePending = new AtomicBoolean(false);
    private volatile boolean done = false;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final List<Candle> streamBuffer = new ArrayList<>();

    @Nullable private SessionCallback chainedListener = null;
    @Nullable private LimitLine currentPriceLine = null;

    /** The indicator session to render from. Set from ChartActivity. */
    @Setter
    @Nullable private IndicatorSession indicatorSession = null;

    private boolean isPinnedToRight = true;
    private float savedLowestX = -1f;

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

    public void applyIndicators() {
        triggerChartUpdate(true);
    }

    public void zoomIn() {
        chart.zoom(1.5f, 1f, chart.getWidth() / 2f, chart.getHeight() / 2f);
        chart.invalidate();
    }

    public void zoomOut() {
        chart.zoom(1f / 1.5f, 1f, chart.getWidth() / 2f, chart.getHeight() / 2f);
        chart.invalidate();
    }

    public void zoomReset() {
        ArrayList<Candle> snap = new ArrayList<>(allCandles);
        if (snap.isEmpty()) return;
        int total = snap.size();
        if (total > 60) {
            chart.zoom(total / 60f, 1f, 0f, 0f);
            chart.moveViewToX(total - 1);
        } else { chart.fitScreen(); }
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
                CombinedChart.DrawOrder.BAR, CombinedChart.DrawOrder.BUBBLE,
                CombinedChart.DrawOrder.LINE, CombinedChart.DrawOrder.SCATTER,
                CombinedChart.DrawOrder.CANDLE
        });
        YAxis left = chart.getAxisLeft();
        left.setDrawGridLines(true); left.setGridColor(COLOR_GRID); left.setGridLineWidth(0.5f);
        left.setLabelCount(6, false); left.setTextColor(COLOR_AXIS_TEXT); left.setTextSize(10f);
        left.setSpaceTop(15f); left.setSpaceBottom(15f); left.setDrawAxisLine(false);
        left.setPosition(YAxis.YAxisLabelPosition.OUTSIDE_CHART);
        YAxis right = chart.getAxisRight();
        right.setEnabled(true); right.setDrawGridLines(false); right.setDrawLabels(false);
        right.setDrawAxisLine(false); right.setAxisMinimum(0f);
        XAxis x = chart.getXAxis();
        x.setDrawGridLines(false); x.setPosition(XAxis.XAxisPosition.BOTTOM);
        x.setSpaceMin(15f); x.setSpaceMax(15f); x.setTextColor(COLOR_AXIS_TEXT);
        x.setTextSize(10f); x.setDrawAxisLine(false); x.setAvoidFirstLastClipping(true);
        chart.getLegend().setEnabled(false); chart.getDescription().setEnabled(false);
        chart.setVisibleXRangeMinimum(10f);
        chart.setHighlightPerDragEnabled(true); chart.setHighlightPerTapEnabled(true);
        chart.setMaxHighlightDistance(20);

        chart.setOnChartValueSelectedListener(new OnChartValueSelectedListener() {
            @Override
            public void onValueSelected(Entry e, Highlight h) {
                int index = Math.round(e.getX());
                if (index >= 0 && index < allCandles.size()) {
                    Candle c = allCandles.get(index);
                    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());
                    String info = String.format(Locale.US, "%s  %s\nO: %.5f   H: %.5f\nL: %.5f   C: %.5f\nVol: %s",
                            c.close >= c.open ? "▲" : "▼",
                            sdf.format(new Date(c.timestamp * 1000L)),
                            c.open, c.high, c.low, c.close, formatVolume(c.volume));
                    candleDataTextView.setText(info);
                    candleDataTextView.setVisibility(View.VISIBLE);
                }
            }
            @Override public void onNothingSelected() { candleDataTextView.setVisibility(View.INVISIBLE); }
        });
    }

    // ── Data ingestion ────────────────────────────────────────────────
    public void addChunk(List<Candle> chunk) {
        if (chunk == null || chunk.isEmpty()) return;
        synchronized (allCandles) {
            for (Candle nc : chunk) {
                if (!allCandles.isEmpty()) {
                    Candle last = allCandles.get(allCandles.size()-1);
                    if (last.timestamp == nc.timestamp) { allCandles.set(allCandles.size()-1, nc); continue; }
                }
                allCandles.add(nc);
            }
        }
        triggerChartUpdate(false);
    }

    // ── Throttled update ──────────────────────────────────────────────
    private void triggerChartUpdate(boolean isLive) {
        if (chart == null) return;
        if (isUpdatePending.compareAndSet(false, true)) {
            mainHandler.postDelayed(() -> {
                if (isLive && chart.getData() != null) {
                    savedLowestX = chart.getLowestVisibleX();
                    isPinnedToRight = (chart.getHighestVisibleX() >= allCandles.size() - 3);
                }
                updateChartData(isLive);
                isUpdatePending.set(false);
            }, 150);
        }
    }

    // ── Core render ───────────────────────────────────────────────────
    private void updateChartData(boolean isLive) {
        ArrayList<Candle> snap = new ArrayList<>(allCandles);
        if (snap.isEmpty()) return;
        Collections.sort(snap, (a, b) -> Long.compare(a.timestamp, b.timestamp));

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
            case CANDLE: data.setData(generateCandleData(snap)); break;
            case BAR:    data.setData(generateOhlcBarData(snap)); break;
            case LINE:   lineData.addDataSet(generateLineData(snap)); break;
        }
        if (lineData.getDataSetCount() > 0) data.setData(lineData);

        chart.getXAxis().setValueFormatter(new ValueFormatter() {
            final SimpleDateFormat df = new SimpleDateFormat("MMM dd", Locale.getDefault());
            final SimpleDateFormat tf = new SimpleDateFormat("HH:mm",  Locale.getDefault());
            @Override public String getAxisLabel(float value, AxisBase axis) {
                int i = Math.round(value);
                if (i<0||i>=snap.size()) return "";
                Date d = new Date(snap.get(i).timestamp*1000L);
                return interval == StockDataHelper.Timeframe.DAILY ? df.format(d) : tf.format(d);
            }
        });

        chart.setData(data);
        updateCurrentPriceLine(snap.get(snap.size()-1));
        chart.getData().notifyDataChanged();
        chart.notifyDataSetChanged();

        if (!isLive) {
            int total = snap.size();
            if (total > 60) { chart.zoom(total/60f,1f,0f,0f); chart.moveViewToX(total-1); }
            else { chart.fitScreen(); }
            float maxVol=0; for(Candle c:snap) if(c.volume>maxVol) maxVol=c.volume;
            chart.getAxisRight().setAxisMaximum(maxVol*10f);
            isPinnedToRight = true;
        } else {
            if (isPinnedToRight) chart.moveViewToX(snap.size()-1);
            else if (savedLowestX>=0) chart.moveViewToX(savedLowestX);
            try {
                chart.getTransformer(YAxis.AxisDependency.LEFT).prepareMatrixValuePx(
                        chart.getXAxis().mAxisMinimum, chart.getXAxis().mAxisRange,
                        chart.getAxisLeft().mAxisRange, chart.getAxisLeft().mAxisMinimum);
            } catch (Exception ignored) {}
            chart.calculateOffsets();
        }
        chart.postInvalidate();
        updateSubCharts(snap);
    }

    // ── Dynamic sub-chart management ──────────────────────────────────
    private void updateSubCharts(ArrayList<Candle> snap) {
        if (subChartsContainer == null || subChartsScroller == null) return;

        List<Indicator> subInds = indicatorSession != null
                ? indicatorSession.getSubCharts() : new ArrayList<>();

        // Build set of needed instanceIds
        java.util.Set<String> needed = new java.util.LinkedHashSet<>();
        for (Indicator i : subInds) needed.add(i.getInstanceId());

        // Remove views for indicators no longer active
        java.util.Iterator<java.util.Map.Entry<String,LineChart>> it =
                subChartViews.entrySet().iterator();
        while (it.hasNext()) {
            java.util.Map.Entry<String,LineChart> e = it.next();
            if (!needed.contains(e.getKey())) {
                subChartsContainer.removeView(e.getValue());
                it.remove();
            }
        }

        if (subInds.isEmpty()) {
            subChartsContainer.setVisibility(View.GONE);
            subChartsScroller.setVisibility(View.GONE);
            setScrollViewWeight(0, subChartsScroller);
            return;
        }
        subChartsContainer.setVisibility(View.VISIBLE);
        subChartsScroller.setVisibility(View.VISIBLE);
        setScrollViewWeight(1, subChartsScroller);

        // Add/update views
        for (Indicator ind : subInds) {
            LineChart sub = subChartViews.get(ind.getInstanceId());
            if (sub == null) {
                sub = createSubChartView(ind);
                subChartViews.put(ind.getInstanceId(), sub);
                subChartsContainer.addView(sub);
                // Add divider above
                View div = new View(activityContext);
                LinearLayout.LayoutParams dp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, 1);
                div.setLayoutParams(dp);
                div.setBackgroundColor(Color.parseColor("#252323"));
                div.setTag("div_" + ind.getInstanceId());
                subChartsContainer.addView(div, subChartsContainer.indexOfChild(sub));
            }
            populateSubChart(sub, ind, snap);
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

    private LineChart createSubChartView(Indicator ind) {
        LineChart sub = new LineChart(activityContext);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dpToPx(160));
        sub.setLayoutParams(lp);
        sub.setBackgroundColor(COLOR_BACKGROUND);
        sub.setDrawGridBackground(false);
        sub.setAutoScaleMinMaxEnabled(true);
        sub.setTouchEnabled(false);
        sub.getLegend().setEnabled(false);

        Description desc = new Description();
        desc.setText(ind.getTag());
        desc.setTextColor(COLOR_AXIS_TEXT);
        desc.setTextSize(9f);
        sub.setDescription(desc);

        XAxis x = sub.getXAxis();
        x.setDrawGridLines(false); x.setDrawLabels(false); x.setDrawAxisLine(false);
        sub.getAxisLeft().setEnabled(false);
        YAxis r = sub.getAxisRight();
        r.setDrawGridLines(true); r.setGridColor(COLOR_GRID); r.setGridLineWidth(0.5f);
        r.setTextColor(COLOR_AXIS_TEXT); r.setTextSize(9f); r.setLabelCount(4,false);
        r.setDrawAxisLine(false);
        return sub;
    }

    private void populateSubChart(LineChart sub, Indicator ind, ArrayList<Candle> snap) {
        Indicator.Result res = ind.compute(snap);
        if (res.subChartLines.isEmpty()) { sub.setVisibility(View.GONE); return; }
        sub.setVisibility(View.VISIBLE);
        sub.getXAxis().setValueFormatter(chart.getXAxis().getValueFormatter());
        LineData d = new LineData();
        for (LineDataSet s : res.subChartLines) d.addDataSet(s);
        sub.setData(d);
        if (!Float.isNaN(res.subChartMin)) sub.getAxisRight().setAxisMinimum(res.subChartMin);
        else sub.getAxisRight().resetAxisMinimum();
        if (!Float.isNaN(res.subChartMax)) sub.getAxisRight().setAxisMaximum(res.subChartMax);
        else sub.getAxisRight().resetAxisMaximum();
        sub.getData().notifyDataChanged();
        sub.notifyDataSetChanged();
        try {
            float lo = chart.getLowestVisibleX(), hi = chart.getHighestVisibleX();
            sub.setVisibleXRange(hi - lo, hi - lo);
            sub.moveViewToX(lo);
        } catch (Exception ignored) {}
        sub.moveViewToX(res.subChartLines.get(0).getXMax());
        sub.postInvalidate();
    }

    private int dpToPx(int dp) {
        return Math.round(dp * activityContext.getResources().getDisplayMetrics().density);
    }

    // ── Price line ────────────────────────────────────────────────────
    private void updateCurrentPriceLine(Candle latest) {
        YAxis left = chart.getAxisLeft();
        if (currentPriceLine != null) left.removeLimitLine(currentPriceLine);
        boolean isUp = latest.close >= latest.open;
        int c = isUp ? COLOR_UP : COLOR_DOWN;
        currentPriceLine = new LimitLine((float)latest.close,
                String.format(Locale.US, "%.5f", latest.close));
        currentPriceLine.setLineColor(c); currentPriceLine.setLineWidth(1f);
        currentPriceLine.enableDashedLine(8f,4f,0f);
        currentPriceLine.setTextColor(c); currentPriceLine.setTextSize(9f);
        currentPriceLine.setLabelPosition(LimitLine.LimitLabelPosition.RIGHT_TOP);
        left.addLimitLine(currentPriceLine);
        left.setDrawLimitLinesBehindData(false);
    }

    // ── Data generators ───────────────────────────────────────────────
    private CandleData generateCandleData(ArrayList<Candle> c) {
        List<CandleEntry> e = new ArrayList<>();
        for (int i=0;i<c.size();i++) {
            Candle cv=c.get(i);
            e.add(new CandleEntry(i,(float)cv.high,(float)cv.low,(float)cv.open,(float)cv.close));
        }
        CandleDataSet s = new CandleDataSet(e,"Prices");
        s.setDecreasingColor(COLOR_DOWN); s.setDecreasingPaintStyle(Paint.Style.FILL);
        s.setIncreasingColor(COLOR_UP);   s.setIncreasingPaintStyle(Paint.Style.FILL);
        s.setNeutralColor(Color.GRAY); s.setShadowColorSameAsCandle(true); s.setShadowWidth(1.5f);
        s.setBarSpace(0.1f); s.setDrawValues(false); s.setHighlightEnabled(true);
        s.setHighLightColor(COLOR_HIGHLIGHT); s.setHighlightLineWidth(1f);
        s.enableDashedHighlightLine(8f,4f,0f); s.setAxisDependency(YAxis.AxisDependency.LEFT);
        return new CandleData(s);
    }

    private CandleData generateOhlcBarData(ArrayList<Candle> c) {
        List<CandleEntry> e = new ArrayList<>();
        for (int i=0;i<c.size();i++) {
            Candle cv=c.get(i);
            e.add(new CandleEntry(i,(float)cv.high,(float)cv.low,(float)cv.open,(float)cv.close));
        }
        CandleDataSet s = new CandleDataSet(e,"Prices");
        s.setDecreasingColor(COLOR_DOWN); s.setDecreasingPaintStyle(Paint.Style.STROKE);
        s.setIncreasingColor(COLOR_UP);   s.setIncreasingPaintStyle(Paint.Style.STROKE);
        s.setNeutralColor(Color.GRAY); s.setShadowColorSameAsCandle(true); s.setShadowWidth(1.5f);
        s.setBarSpace(0.3f); s.setDrawValues(false); s.setHighlightEnabled(true);
        s.setHighLightColor(COLOR_HIGHLIGHT); s.setHighlightLineWidth(1f);
        s.enableDashedHighlightLine(8f,4f,0f); s.setAxisDependency(YAxis.AxisDependency.LEFT);
        return new CandleData(s);
    }

    private LineDataSet generateLineData(ArrayList<Candle> c) {
        List<Entry> e = new ArrayList<>();
        for (int i=0;i<c.size();i++) e.add(new Entry(i,(float)c.get(i).close));
        LineDataSet s = new LineDataSet(e,"Close");
        s.setColor(COLOR_LINE); s.setLineWidth(1.8f); s.setDrawCircles(false); s.setDrawValues(false);
        s.setMode(LineDataSet.Mode.LINEAR); s.setDrawFilled(true); s.setFillColor(COLOR_LINE);
        s.setFillAlpha(25); s.setHighlightEnabled(true); s.setHighLightColor(COLOR_HIGHLIGHT);
        s.setHighlightLineWidth(1f); s.enableDashedHighlightLine(8f,4f,0f);
        s.setAxisDependency(YAxis.AxisDependency.LEFT);
        return s;
    }

    private BarData generateVolumeData(ArrayList<Candle> c) {
        List<BarEntry> e = new ArrayList<>();
        int[] colors = new int[c.size()];
        for (int i=0;i<c.size();i++) {
            Candle cv=c.get(i); e.add(new BarEntry(i,cv.volume));
            if (cv.close>cv.open) colors[i]=COLOR_VOL_UP;
            else if (cv.close<cv.open) colors[i]=COLOR_VOL_DOWN;
            else colors[i]=COLOR_VOL_NEUTRAL;
        }
        BarDataSet s = new BarDataSet(e,"Volume");
        s.setColors(colors); s.setAxisDependency(YAxis.AxisDependency.RIGHT);
        s.setDrawValues(false); s.setHighlightEnabled(false);
        BarData bd = new BarData(s); bd.setBarWidth(0.8f);
        return bd;
    }

    // ── Stream update ─────────────────────────────────────────────────
    private void streamUpdate(List<Candle> chunk) {
        if (chunk==null||chunk.isEmpty()) return;
        Candle live = chunk.get(0);
        synchronized (allCandles) {
            if (allCandles.isEmpty() && !done) { streamBuffer.addAll(chunk); return; }
            if (allCandles.isEmpty()) return;
            Candle last = allCandles.get(allCandles.size()-1);
            if (last.timestamp + interval.interval > live.timestamp) {
                allCandles.set(allCandles.size()-1, new Candle(
                        last.timestamp, last.open, Math.max(live.high,last.high),
                        Math.min(live.low,last.low), live.close, last.volume+live.volume));
            } else {
                allCandles.add(new Candle(last.timestamp+interval.interval,
                        live.open, live.high, live.low, live.close, live.volume));
            }
        }
        triggerChartUpdate(true);
    }

    // ── SessionCallback ───────────────────────────────────────────────
    @Override
    public void onDataReceived(DataType msgType, Object parsedData) {
        if (msgType == DataType.TICKER_ERROR) {
            mainHandler.post(() -> Toast.makeText(activityContext,(String)parsedData,Toast.LENGTH_SHORT).show());
            flushRequests(); return;
        }
        if (!(parsedData instanceof PriceChunk)) return;
        PriceChunk chunk = (PriceChunk) parsedData;
        if (!reqIds.contains(chunk.reqId)) return;
        switch (msgType) {
            case TICKER_STREAM:
                streamUpdate(chunk.chunk);
                if (chainedListener!=null) synchronized(allCandles) {
                    if (!allCandles.isEmpty())
                        chainedListener.onDataReceived(DataType.MARKET_DATA, allCandles.get(allCandles.size()-1).close);
                }
                break;
            case TICKER_SNAPSHOT:
                addChunk(chunk.chunk); break;
            case TICKER_REQUEST_DONE:
                reqIds.remove(chunk.reqId);
                if (chainedListener!=null) {
                    done=true;
                    for (Candle b:streamBuffer) streamUpdate(List.of(b));
                    synchronized(allCandles) {
                        if (!allCandles.isEmpty())
                            chainedListener.onDataReceived(DataType.MARKET_DATA, allCandles.get(allCandles.size()-1).close);
                    }
                }
                break;
        }
    }
    @Override public void onActionRequired(int a, @Nullable Object d) {}

    // ── Helpers ───────────────────────────────────────────────────────
    private String formatVolume(float v) {
        if (v>=1_000_000) return String.format(Locale.US,"%.2fM",v/1_000_000);
        if (v>=1_000)     return String.format(Locale.US,"%.1fK",v/1_000);
        return String.format(Locale.US,"%.0f",v);
    }

    public void clearChart() {
        allCandles.clear(); done=false; streamBuffer.clear(); isPinnedToRight=true; savedLowestX=-1f;
        if (currentPriceLine!=null) { chart.getAxisLeft().removeLimitLine(currentPriceLine); currentPriceLine=null; }
    }
    public void addToCurrentRequest(int reqId) { this.reqIds.add(reqId); }
    public void flushRequests() {
        NetworkClient.getInstance(null).getSessionManager()
                .discardRequests(reqIds.stream().mapToInt(Integer::intValue).toArray());
        reqIds.clear();
    }
    public void bindListener(SessionCallback l) { this.chainedListener = l; }
    public boolean isDone() { return done; }
    public void setInterval(StockDataHelper.Timeframe tf) { this.interval = tf; }
}