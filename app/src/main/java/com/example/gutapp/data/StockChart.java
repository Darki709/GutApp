package com.example.gutapp.data;


import static com.example.gutapp.ui.ChartActivity.CHART_LOG_TAG;

import android.content.Context;
import android.graphics.Color;
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
import com.github.mikephil.charting.charts.CombinedChart;
import com.github.mikephil.charting.components.AxisBase;
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
    private final CombinedChart chart;
    private final CopyOnWriteArrayList<Candle> allCandles = new CopyOnWriteArrayList<>();
    private final Set<Integer> reqIds = ConcurrentHashMap.<Integer>newKeySet(); //stores all requests that the chart sent
    private StockDataHelper.Timeframe interval;

    private final AtomicBoolean isUpdatePending = new AtomicBoolean(false);

    private final Context activityContext;
    private volatile boolean done = false;

    Handler mainHandler = new Handler(Looper.getMainLooper()); //used to send error messages to the UI thread


    //store stream responses if they arrived before the snapshot has finished loading
    private final List<Candle> streamBuffer = new ArrayList<>();

    @Nullable
    private SessionCallback chainedListener = null;


    public StockChart(CombinedChart chart, Context context) {
        this.chart = chart;
        this.activityContext = context;
    }

    //set up chart with default settings
    public void setupChart(TextView candleDataTextView) {
        chart.setAutoScaleMinMaxEnabled(true);
        chart.setDragEnabled(true);
        chart.setScaleEnabled(true);
        chart.setDrawGridBackground(false);
        chart.setPinchZoom(true);
        chart.setScaleXEnabled(true);
        chart.setScaleYEnabled(true);

        chart.setDrawOrder(new CombinedChart.DrawOrder[]{
                CombinedChart.DrawOrder.BAR,
                CombinedChart.DrawOrder.BUBBLE,
                CombinedChart.DrawOrder.LINE,
                CombinedChart.DrawOrder.SCATTER,
                CombinedChart.DrawOrder.CANDLE // Draw CandleData last (on top)
        });

        chart.setDrawGridBackground(false);

        // Configure Left Axis (Price)
        YAxis leftAxis = chart.getAxisLeft();
        leftAxis.setDrawGridLines(true);
        leftAxis.setLabelCount(5, false);
        leftAxis.setTextColor(Color.WHITE);
        leftAxis.setSpaceTop(20f);
        leftAxis.setSpaceBottom(20f);

        // Configure Right Axis (Volume)
        YAxis rightAxis = chart.getAxisRight();
        rightAxis.setEnabled(true); // Enable it now
        rightAxis.setDrawGridLines(false); // Keep it clean
        rightAxis.setDrawLabels(false);   // Usually we don't show volume numbers on the side
        rightAxis.setAxisMinimum(1000f);

        XAxis xAxis = chart.getXAxis();
        xAxis.setDrawGridLines(false);
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setSpaceMin(15f);
        xAxis.setSpaceMax(15f);
        xAxis.setTextColor(Color.WHITE);
//        xAxis.setGranularity(1f); // Only allow whole numbers (0, 1, 2...)
//        xAxis.setGranularityEnabled(true);
//        xAxis.setCenterAxisLabels(false);
//        xAxis.setAvoidFirstLastClipping(true);

        chart.getAxisRight().setEnabled(true);
        rightAxis.setDrawLabels(false);
        rightAxis.setDrawGridLines(false);
        chart.getLegend().setEnabled(false);
        chart.getDescription().setEnabled(false);
        chart.setVisibleXRangeMinimum(10f);

        //Enable the crosshair/highlighting interaction
        chart.setHighlightPerDragEnabled(true);
        chart.setHighlightPerTapEnabled(true);
        chart.setMaxHighlightDistance(20);

        chart.setOnChartValueSelectedListener(new OnChartValueSelectedListener() {
            @Override
            public void onValueSelected(Entry e, Highlight h) {
                int index = Math.round(e.getX());
                if (index >= 0 && index < allCandles.size()) {
                    Candle c = allCandles.get(index);

                    // Format time
                    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());
                    String dateStr = sdf.format(new Date(c.timestamp * 1000L));

                    // Fix the Long/Float crash by casting volume to double
                    String info = String.format(Locale.US,
                            "Time: %s\nO: %.5f H: %.5f\nL: %.5f C: %.5f\nVol: %d",
                            dateStr, c.open, c.high, c.low, c.close, c.volume);

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

    public void addChunk(List<Candle> chunk) {
        if (chunk == null || chunk.isEmpty()) return;

        synchronized (allCandles) {
            for (Candle newCandle : chunk) {
                if (!allCandles.isEmpty()) {
                    Candle last = allCandles.get(allCandles.size() - 1);
                    // If this is an update to the current last candle
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

    private void triggerChartUpdate(boolean initialized) {
        // Check if the activity/view is still valid before posting
        if (chart == null) return;

        if (isUpdatePending.compareAndSet(false, true)) {
            mainHandler.postDelayed(() -> {
                // CRITICAL: Clear highlight before changing the underlying data
                chart.highlightValue(null);
                updateChartData(initialized);
                isUpdatePending.set(false);
            }, 150);
        }
    }

    private void updateChartData(boolean initialized) {

        Log.i(CHART_LOG_TAG, "Updating chart display");
        
        // Take a snapshot of the current candles to avoid race conditions with renderer
        ArrayList<Candle> safeCopy = new ArrayList<>(allCandles);
        if (safeCopy.isEmpty()) return;

        // Sort the local copy, not the thread-safe list
        Collections.sort(safeCopy, (c1, c2) -> Long.compare(c1.timestamp, c2.timestamp));

        CombinedData data = new CombinedData();
        data.setData(generateCandleData(safeCopy));
        data.setData(generateVolumeData(safeCopy));
        chart.setData(data);

        chart.getXAxis().setValueFormatter(new ValueFormatter() {
            private final SimpleDateFormat dailyFormat = new SimpleDateFormat("MMM dd", Locale.getDefault());
            private final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());

            @Override
            public String getAxisLabel(float value, AxisBase axis) {
                int index = Math.round(value);
                if (index < 0 || index >= safeCopy.size()) return "";

                long timestamp = safeCopy.get(index).timestamp;
                Date date = new Date(timestamp * 1000L);

                if (interval == StockDataHelper.Timeframe.DAILY) {
                    return dailyFormat.format(date);
                } else {
                    return timeFormat.format(date);
                }
            }
        });

        float desiredVisibleRange = 60f;
        int totalCount = safeCopy.size();

        if (totalCount > desiredVisibleRange) {
            chart.setVisibleXRangeMinimum(10f);
            if(!initialized){
            chart.zoom(totalCount / desiredVisibleRange, 1f, 0f, 0f);
            chart.moveViewToX(totalCount - 1);}
            chart.setAutoScaleMinMaxEnabled(true);
        } else {
            chart.fitScreen();
        }
        float maxVolume = 0;
        for (Candle c : safeCopy) {
            if (c.volume > maxVolume) maxVolume = (float) c.volume;
        }
        chart.getAxisRight().setAxisMaximum(maxVolume * 10f); // Volume scale
        chart.notifyDataSetChanged();
        chart.invalidate();
    }

    private CandleData generateCandleData(ArrayList<Candle> candles) {
        List<CandleEntry> entries = new ArrayList<>();
        for (int i = 0; i < candles.size(); i++) {
            Candle c = candles.get(i);
            entries.add(new CandleEntry(i, (float) c.high, (float) c.low, (float) c.open, (float) c.close));
        }

        CandleDataSet set = new CandleDataSet(entries, "Prices");
        set.setDecreasingColor(Color.parseColor("#FF5252"));
        set.setDecreasingPaintStyle(Paint.Style.FILL);
        set.setIncreasingColor(Color.parseColor("#2ECC71"));
        set.setIncreasingPaintStyle(Paint.Style.FILL);
        set.setShadowColorSameAsCandle(true);
        set.setShadowWidth(1.2f);
        set.setDrawValues(false);
        set.setHighlightEnabled(true);
        set.setHighLightColor(Color.WHITE);
        set.setAxisDependency(YAxis.AxisDependency.LEFT);

        return new CandleData(set);
    }

    private BarData generateVolumeData(ArrayList<Candle> candles) {
        List<BarEntry> entries = new ArrayList<>();
        for (int i = 0; i < candles.size(); i++) {
            Candle c = candles.get(i);
            entries.add(new BarEntry(i, (float) c.volume));
        }

        BarDataSet set = new BarDataSet(entries, "Volume");
        set.setAxisDependency(YAxis.AxisDependency.RIGHT);
        set.setColor(Color.argb(40, 0, 0, 255));
        set.setDrawValues(false);
        return new BarData(set);
    }

    public void clearChart() {
        allCandles.clear();
    }

    public void addToCurrentRequest(int reqId) {
        this.reqIds.add(reqId);
    }

    public void flushRequests() {
        NetworkClient.getInstance(null).getSessionManager().discardRequests(reqIds.stream().mapToInt(Integer::intValue).toArray());
        this.reqIds.clear();
    }

    private void streamUpdate(List<Candle> chunk) {
        if (chunk == null || chunk.isEmpty()) return;
        Candle liveData = chunk.get(0);

        synchronized (allCandles) {
            if (allCandles.isEmpty() && !done) {
                streamBuffer.addAll(chunk);
                Log.d("CHART_DEBUG", "Buffering stream response until snapshot arrives...");
                return;
            }
            if(allCandles.isEmpty()) return;
            Candle lastCandle = allCandles.get(allCandles.size() - 1);
            if (lastCandle.timestamp + interval.interval > liveData.timestamp) {
                // Update the current candle (live movement)
                Candle updated = new Candle(
                        lastCandle.timestamp,
                        lastCandle.open,
                        Math.max(liveData.high, lastCandle.high),
                        Math.min(liveData.low, lastCandle.low),
                        liveData.close,
                        lastCandle.volume + liveData.volume
                );
                allCandles.set(allCandles.size()-1, updated);
            } else {
                // New candle interval started
                Candle newCandle = new Candle(
                        lastCandle.timestamp + interval.interval,
                        liveData.open, liveData.high, liveData.low, liveData.close, liveData.volume
                );
                allCandles.add(newCandle);
            }
        }
        triggerChartUpdate(true);
    }

    @Override
    public void onDataReceived(DataType msgType, Object parsedData) {
        if (msgType == DataType.TICKER_ERROR) {
            mainHandler.post(() -> Toast.makeText(activityContext, (String) parsedData, Toast.LENGTH_SHORT).show());
            flushRequests();
            return;
        }

        if (!(parsedData instanceof PriceChunk)) return;
        PriceChunk chunk = (PriceChunk) parsedData;
        if (!reqIds.contains(chunk.reqId)) return;

        switch (msgType) {
            case TICKER_STREAM:
                streamUpdate(chunk.chunk);
                if(chainedListener != null){
                    synchronized (allCandles) {
                        if(!allCandles.isEmpty()){
                        chainedListener.onDataReceived(DataType.MARKET_DATA, allCandles.get(allCandles.size() - 1).close);}
                    }}
                break;
            case TICKER_SNAPSHOT:
                addChunk(chunk.chunk);
                break;
            case TICKER_REQUEST_DONE:
                reqIds.remove(chunk.reqId);
                if(chainedListener != null){
                done = true;
                    for(Candle buffed : streamBuffer){
                        streamUpdate(List.of(buffed));
                    }
                synchronized (allCandles) {
                    if(!allCandles.isEmpty()) chainedListener.onDataReceived(DataType.MARKET_DATA, allCandles.get(allCandles.size() - 1).close);
                }}
                break;
        }
    }

    public void bindListener(SessionCallback chainedListener){
        this.chainedListener = chainedListener;
    }

    public boolean isDone(){
        return done;
    }

    public void setInterval(StockDataHelper.Timeframe interval) {
        this.interval = interval;
    }

    @Override
    public void onActionRequired(int actionType, @Nullable Object data) {}
}
