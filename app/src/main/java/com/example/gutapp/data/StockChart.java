package com.example.gutapp.data;


import static com.example.gutapp.ui.ChartActivity.CHART_LOG_TAG;
import static java.util.concurrent.ConcurrentHashMap.newKeySet;

import android.util.Log;

import com.example.gutapp.data.models.Candle;
import com.example.gutapp.database.StockDataHelper;
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
import com.github.mikephil.charting.formatter.ValueFormatter;

import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class StockChart implements SessionCallback {
    private final CombinedChart chart;
    private final List<Candle> allCandles = new ArrayList<>();
    private final Set<Integer> reqIds = ConcurrentHashMap.<Integer>newKeySet(); ; //stores all requests that the chart sent
    private StockDataHelper.Timeframe interval;

    public StockChart(CombinedChart chart) {
        this.chart = chart;
    }

    //set up chart with default settings
    public void setupChart() {
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

        YAxis leftAxis = chart.getAxisLeft();
        leftAxis.setDrawGridLines(true);

        XAxis xAxis = chart.getXAxis();
        xAxis.setDrawGridLines(true);
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setSpaceMin(15f);
        xAxis.setSpaceMax(15f);

        chart.getAxisRight().setEnabled(false);
        chart.getLegend().setEnabled(false);
        chart.getDescription().setEnabled(false);
    }

    public synchronized void addChunk(List<Candle> chunk, StockDataHelper.Timeframe interval) {
        if(chunk == null || chunk.isEmpty()) return;
        this.interval = interval;
        allCandles.addAll(chunk);

        // Essential: Keep the internal list sorted by timestamp
        Collections.sort(allCandles, (c1, c2) -> Long.compare(c1.timestamp, c2.timestamp));

        updateChartData();
    }

    private void updateChartData() {
        Log.i(CHART_LOG_TAG, "adding prices to chart");
        CombinedData data = new CombinedData();

        data.setData(generateCandleData());
        data.setData(generateVolumeData());

        chart.setData(data);

        chart.getXAxis().setValueFormatter(new ValueFormatter() {
            private final SimpleDateFormat dailyFormat = new SimpleDateFormat("MMM dd", Locale.US);
            private final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm", Locale.US);

            @Override
            public String getAxisLabel(float value, AxisBase axis) {
                // Since we passed 'timestamp' as the X-value in addChunk,
                // the 'value' parameter IS the timestamp.
                long timestamp = (long) value;

                // Your protocol sends timestamps in SECONDS,
                // but Java Date needs MILLISECONDS.
                Date date = new Date(timestamp * 1000L);

                // Logic to decide which format to use
                // You can pass the current interval into the Manager to decide this
                if (interval == StockDataHelper.Timeframe.DAILY) {
                    return timeFormat.format(date);
                } else {
                    return dailyFormat.format(date);
                }
            }
        });

        // Tell the chart the data has changed and refresh
        chart.notifyDataSetChanged();
        chart.invalidate();
    }

    private CandleData generateCandleData() {
        List<CandleEntry> entries = new ArrayList<>();
        for (Candle c : allCandles) {
            // X-axis: Timestamp, Y-values: High, Low, Open, Close
            entries.add(c.getEntry());
        }

        CandleDataSet set = new CandleDataSet(entries, "Price");
        set.setShadowColor(android.graphics.Color.DKGRAY);
        set.setShadowWidth(0.7f);
        set.setDecreasingColor(android.graphics.Color.RED);
        set.setDecreasingPaintStyle(android.graphics.Paint.Style.FILL);
        set.setIncreasingColor(android.graphics.Color.GREEN);
        set.setIncreasingPaintStyle(android.graphics.Paint.Style.STROKE);

        return new CandleData(set);
    }

    private BarData generateVolumeData() {
        List<BarEntry> entries = new ArrayList<>();
        for (Candle c : allCandles) {
            entries.add(c.getVolumeEntry());
        }

        BarDataSet set = new BarDataSet(entries, "Volume");
        int transparentBlue = android.graphics.Color.argb(120, 0, 0, 255);
        set.setColor(transparentBlue);
        set.setDrawValues(false);
        return new BarData(set);
    }



    public enum Actions{
        STREAM(0),
        SNAPSHOT(1),
        REQUESTDONE(2);

        public final int value;

        Actions(int value){
            this.value = value;
        }

        static Actions fromValue(int value){
            for(Actions action : Actions.values()){
                if(action.value == value) {
                    return action;
                }
            }
            return null;
        }
    }

    public void addToCurrentRequest(int reqId){
        this.reqIds.add(reqId);
    }

    public void flushRequests(){
        this.reqIds.clear();
    }

    private void streamUpdate(List<Candle> chunk){

    }

    public static class PriceChunk {
        public final int reqId;
        public final ArrayList<Candle> chunk;

        public PriceChunk(int reqId, ArrayList<Candle> chunk) {
            this.reqId = reqId;
            this.chunk = chunk;
        }
    }



    //parsed data should be a PriceChunk object
    @Override
    public void onDataReceived(int msgType, Object parsedData) {
        List<Candle> prices;
        Actions action = Actions.fromValue(msgType);
        PriceChunk chunk;
        if(action == null){
            throw new RuntimeException("Wrong action type");
        }
        try{
            chunk = (PriceChunk) parsedData;
        }catch (Exception e){
            throw new RuntimeException("Wrong data type");
        }
        if(!reqIds.contains(chunk.reqId)){
            return;
        }
        prices = chunk.chunk;
        switch(action){
            case STREAM:
                streamUpdate(prices);
                break;
            case SNAPSHOT:
                addChunk(prices, this.interval);
                break;
            default:
                break;
        }
    }

    @Override
    public void onActionRequired(int actionType) {
        //not needed here
    }
}
