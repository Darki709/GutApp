package com.example.gutapp.data;


import static com.example.gutapp.ui.ChartActivity.CHART_LOG_TAG;
import static java.util.concurrent.ConcurrentHashMap.newKeySet;

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
import com.example.gutapp.session.Connection;
import com.example.gutapp.session.NetworkClient;
import com.example.gutapp.session.Requests.RequestTickerData;
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
import java.util.concurrent.atomic.AtomicBoolean;

public class StockChart implements SessionCallback {
    private final CombinedChart chart;
    private final List<Candle> allCandles = new ArrayList<>();
    private final Set<Integer> reqIds = ConcurrentHashMap.<Integer>newKeySet(); ; //stores all requests that the chart sent
    private StockDataHelper.Timeframe interval;

    private final AtomicBoolean isUpdatePending = new AtomicBoolean(false);

    private final Context activityContext;

    Handler mainHandler = new Handler(Looper.getMainLooper()); //used to send error messages to the UI thread

    public StockChart(CombinedChart chart, Context context) {
        this.chart = chart;
        this.activityContext = context;
    }

    //set up chart with default settings
    public void setupChart(TextView candleDataTextView) {
        chart.setAutoScaleMinMaxEnabled(false);
        chart.setDragEnabled(true);
        chart.setScaleEnabled(true);
        chart.setDrawGridBackground(false);
        chart.setPinchZoom(true);

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

        chart.getAxisRight().setEnabled(false);
        chart.getLegend().setEnabled(false);
        chart.getDescription().setEnabled(false);

        //Enable the crosshair/highlighting interaction
        chart.setHighlightPerDragEnabled(true);
        chart.setHighlightPerTapEnabled(true);
        chart.setMaxHighlightDistance(20);

        chart.setOnChartValueSelectedListener(new OnChartValueSelectedListener() {
            @Override
            public void onValueSelected(Entry e, Highlight h) {
                int index = (int) e.getX();
                synchronized (allCandles){
                if (index >= 0 && index < allCandles.size()) {
                    Candle c = allCandles.get(index);

                    // Format time
                    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());
                    String dateStr = sdf.format(new Date(c.timestamp * 1000L));

                    // Fix the Long/Float crash by casting volume to double
                    String info = String.format(Locale.US,
                            "Time: %s\nO: %.5f H: %.5f\nL: %.5f C: %.5f\nVol: %.0f",
                            dateStr, c.open, c.high, c.low, c.close, (double)c.volume);

                    candleDataTextView.setText(info);
                    candleDataTextView.setVisibility(View.VISIBLE);
                }}
            }

            @Override
            public void onNothingSelected() {
                candleDataTextView.setVisibility(View.INVISIBLE);
            }
        });
    }

    public synchronized void addChunk(List<Candle> chunk) {
        if(chunk == null || chunk.isEmpty()) return;
        synchronized (allCandles){
            if(!allCandles.isEmpty()){
                //check that last candle isn't written twice
                Candle lastCandle = allCandles.remove(allCandles.size() - 1);
                if(lastCandle.timestamp != chunk.get(0).timestamp){
                    allCandles.add(lastCandle);
                    allCandles.addAll(chunk);
                }
            else allCandles.addAll(chunk);
            }
            else allCandles.addAll(chunk);
        }


        if (isUpdatePending.compareAndSet(false, true)) {
            chart.postDelayed(() -> {
                synchronized(this) {
                    // Sort once before the UI update, not for every tiny chunk
                    synchronized (allCandles){
                        Collections.sort(allCandles, (c1, c2) -> Long.compare(c1.timestamp, c2.timestamp));
                    }
                    updateChartData();
                    isUpdatePending.set(false);
                }
            }, 150); // Delay refresh by 150ms to allow more chunks to arrive
        }
    }

    private synchronized void updateChartData() {
        Log.i(CHART_LOG_TAG, "adding prices to chart");
        CombinedData data = new CombinedData();

        ArrayList<Candle> safeCopy;
        synchronized (this.allCandles){
            if (this.allCandles.isEmpty()) return;
            safeCopy = new ArrayList<>(allCandles);
        }

        data.setData(generateCandleData(safeCopy));
        data.setData(generateVolumeData(safeCopy));

        chart.setData(data);

        chart.getXAxis().setValueFormatter(new ValueFormatter() {
            private final SimpleDateFormat dailyFormat = new SimpleDateFormat("MMM dd", Locale.getDefault());
            private final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());

            @Override
            public String getAxisLabel(float value, AxisBase axis) {
                // Since we passed 'timestamp' as the X-value in addChunk,
                // the 'value' parameter IS the timestamp.
                int index = (int) value;

                // Safety check: index must be within the list bounds
                if (index < 0 || index >= safeCopy.size()) {
                    return "";
                }

                long timestamp = safeCopy.get(index).timestamp;
                Date date = new Date(timestamp * 1000L);

                // Your date formatting logic
                if (interval == StockDataHelper.Timeframe.DAILY) {
                    return dailyFormat.format(date); // Note: swapped these for logic
                } else {
                    return timeFormat.format(date);
                }
            }
        });

        if (!safeCopy.isEmpty()) {
            float desiredVisibleRange = 60f; // Shows last 60 candles
            int totalCount = safeCopy.size();

            if (totalCount > desiredVisibleRange) {
                // 2. Fix the horizontal scale to show exactly 'desiredVisibleRange' candles
                chart.setVisibleXRangeMaximum(desiredVisibleRange);
                chart.setVisibleXRangeMinimum(desiredVisibleRange);

                // 3. Move the view to the very end of the data (the most recent candle)
                // We subtract 1 because indices are 0-based
                chart.moveViewToX(totalCount - 1);

                // 4. Aesthetic: Auto-scale the Y-axis to fit the visible prices
                chart.setAutoScaleMinMaxEnabled(true);
            } else {
                chart.fitScreen();
            }
        }

        chart.getAxisLeft().setStartAtZero(false); // Prices aren't zero-based
        chart.calculateOffsets(); // Refresh the drawing margins
        chart.getAxisLeft().calculate(data.getYMin(), data.getYMax());

        //autoscale volume to the price
        float maxVolume = 0;
        for (Candle c : safeCopy) {
            if (c.volume > maxVolume) maxVolume = (float) c.volume;
        }
        YAxis rightAxis = chart.getAxisRight();
        rightAxis.setAxisMaximum(maxVolume * 0.1f);

        YAxis leftAxis = chart.getAxisLeft();
        leftAxis.setDrawGridLines(true);
        leftAxis.setEnabled(true); // Explicitly ensure it is on
        leftAxis.setTextColor(android.graphics.Color.WHITE); // If using a dark theme
        leftAxis.setSpaceBottom(25f);


        // Tell the chart the data has changed and refresh
        chart.notifyDataSetChanged();
        chart.invalidate();
    }

    private CandleData generateCandleData(ArrayList<Candle> allCandles) {
        List<CandleEntry> entries = new ArrayList<>();
        for (int i = 0; i < allCandles.size(); i++) {
            Candle c = allCandles.get(i);
            // Use 'i' as the X value (the index), not the timestamp
            entries.add(new CandleEntry(i, (float)c.high, (float)c.low, (float)c.open, (float)c.close));
        }

        CandleDataSet set = new CandleDataSet(entries, "Prices");

        //Colors
        set.setDecreasingColor(Color.parseColor("#FF5252")); // Modern Red
        set.setDecreasingPaintStyle(Paint.Style.FILL);
        set.setIncreasingColor(Color.parseColor("#2ECC71")); // Modern Green
        set.setIncreasingPaintStyle(Paint.Style.FILL);
        set.setNeutralColor(Color.LTGRAY);
        //Shadows & Wicks
        set.setShadowColorSameAsCandle(true);
        set.setShadowWidth(1.2f); // Thicker wicks for better visibility
        //Spacing
        set.setBarSpace(0.15f); // Adds a tiny gap between candles for clarity
        //Disable clutter
        set.setDrawValues(false); // Hide numbers on top of candles

        //Interactive visuals with the cursor
        set.setHighlightEnabled(true);
        set.setHighLightColor(Color.WHITE); // Color of the crosshair lines
        set.setDrawHorizontalHighlightIndicator(true);
        set.setDrawVerticalHighlightIndicator(true);


        return new CandleData(set);
    }

    private BarData generateVolumeData(ArrayList<Candle> allCandles) {
        List<BarEntry> entries = new ArrayList<>();
        for (int i = 0; i < allCandles.size(); i++) {
            Candle c = allCandles.get(i);
            // Again, use 'i' for the X value
            entries.add(new BarEntry(i, (float)c.volume));
        }

        BarDataSet set = new BarDataSet(entries, "Volume");

        //allows to scale the volume bars differently than the candles
        set.setAxisDependency(YAxis.AxisDependency.RIGHT);

        int transparentBlue = android.graphics.Color.argb(40, 0, 0, 255);
        set.setColor(transparentBlue);
        set.setDrawValues(false);
        return new BarData(set);
    }

    public void clearChart(){
        allCandles.clear();
    }

    public void addToCurrentRequest(int reqId){
        this.reqIds.add(reqId);
    }

    public void flushRequests(){
        NetworkClient.getInstance(null).getSessionManager().discardRequests(reqIds.stream().mapToInt(Integer::intValue).toArray());
        this.reqIds.clear();
    }

    private synchronized void streamUpdate(List<Candle> chunk){//when data type is stream list candle length is expected to be 1 if it is longer data will be ignored (shouldn't happen)
        Candle liveData = chunk.get(0);
        long currentTs;
        boolean isEmpty;
        synchronized (allCandles){
            isEmpty = allCandles.isEmpty();
        }
        if(isEmpty){
            addChunk(chunk);
        }
        synchronized (allCandles){
            currentTs = allCandles.get(allCandles.size() - 1).timestamp;
        }
        if(currentTs + interval.interval > liveData.timestamp){
            Candle previousLiveData;
            synchronized (allCandles){
                previousLiveData = allCandles.remove(allCandles.size() - 1);
            }
            //live data updates more frequently than the graphs timeframe so we must check whether the data is actually worth updating and use the ts based on the timeframe (jumps between candles should be interval seconds)
            Candle newLiveData = new Candle(previousLiveData.timestamp, previousLiveData.open,Math.max(liveData.high, previousLiveData.high),Math.min(liveData.low, previousLiveData.low), liveData.close, liveData.volume);
            allCandles.add(newLiveData);
            updateChartData();
        }
        else{
            //we need the time stamp to be in interval seconds gaps
            Candle candle = new Candle(currentTs + interval.interval, chunk.get(0).open, chunk.get(0).high, chunk.get(0).low, chunk.get(0).close, chunk.get(0).volume);
            chunk.remove(0);
            chunk.add(candle);
            addChunk(chunk);
        }

    }



    //parsed data should be a PriceChunk object
    @Override
    public void onDataReceived(int msgType, Object parsedData) {
        RequestTickerData.Actions action = RequestTickerData.Actions.fromValue(msgType);
        if(action == null){
            throw new RuntimeException("Wrong action type");
        }
        if(action == RequestTickerData.Actions.ERROR){
            mainHandler.post(() -> Toast.makeText(activityContext, (String) parsedData, Toast.LENGTH_SHORT).show());
            flushRequests();
            return;
        }
        PriceChunk chunk;
        try{
            chunk = (PriceChunk) parsedData;
        }catch (Exception e){
            throw new RuntimeException("Wrong data type");
        }
        if(!reqIds.contains(chunk.reqId)){
            return;
        }
        switch(action){
            case STREAM:
                streamUpdate(chunk.chunk);
                break;
            case SNAPSHOT:
                addChunk(chunk.chunk);
                break;
            case REQUESTDONE:
                Log.i(CHART_LOG_TAG, "request done: " + (chunk.reqId & 0xffffffffL));
                reqIds.remove(chunk.reqId);
                break;
            default:
                break;
        }
    }

    public void setInterval(StockDataHelper.Timeframe interval){ this.interval = interval;}

    @Override
    public void onActionRequired(int actionType,@Nullable Object data) {
        //not needed here
    }
}
