package com.example.gutapp.ui;

import android.graphics.Color;
import android.graphics.Paint;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.gutapp.R;
import com.example.gutapp.database.DB_Helper;
import com.example.gutapp.database.DB_Index;
import com.example.gutapp.database.StockDataHelper;

import com.github.mikephil.charting.charts.CombinedChart;
import com.github.mikephil.charting.components.AxisBase;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.CandleData;
import com.github.mikephil.charting.data.CandleDataSet;
import com.github.mikephil.charting.data.CandleEntry;
import com.github.mikephil.charting.data.CombinedData;
import com.github.mikephil.charting.formatter.ValueFormatter;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

public class ChartActivity extends AppCompatActivity implements View.OnClickListener {

    private DB_Helper db_helper;
    private StockDataHelper stockDataHelper;
    private CombinedChart chart;
    private String symbol = "IBM"; // Default symbol
    private boolean isInitialLoad = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_chart);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        db_helper = new DB_Helper(this);
        stockDataHelper = (StockDataHelper) db_helper.getHelper(DB_Index.STOCK_TABLE);

        chart = findViewById(R.id.stockChart);

        // Set up button listeners
        findViewById(R.id.button5m).setOnClickListener(this);
        findViewById(R.id.button15m).setOnClickListener(this);
        findViewById(R.id.button1h).setOnClickListener(this);
        findViewById(R.id.button1d).setOnClickListener(this);

        // Initial chart load with daily data
        updateChartData(StockDataHelper.Timeframe.DAILY);
    }

    private void updateChartData(StockDataHelper.Timeframe timeframe) {
        ArrayList<CandleEntry> stockData;
        try {
            stockData = stockDataHelper.getCachedStockData(symbol, timeframe);
        } catch (Exception e) {
            Log.e(db_helper.DB_LOG_TAG, "Error getting stock data: " + e.getMessage());
            e.printStackTrace();
            return;
        }

        if (stockData == null || stockData.isEmpty()) {
            Log.e(db_helper.DB_LOG_TAG, "Stock data is empty or null for timeframe: " + timeframe.name());
            chart.clear(); // Clear the chart if there is no data
            chart.invalidate();
            return;
        }

        CandleDataSet dataSet = new CandleDataSet(stockData, "Stock Price");
        dataSet.setIncreasingColor(Color.GREEN);
        dataSet.setDecreasingColor(Color.RED);
        dataSet.setIncreasingPaintStyle(Paint.Style.FILL);
        dataSet.setDecreasingPaintStyle(Paint.Style.FILL);
        dataSet.setShadowColorSameAsCandle(true);
        dataSet.setDrawValues(false);

        CandleData candleData = new CandleData(dataSet);
        CombinedData combinedData = new CombinedData();
        combinedData.setData(candleData);

        chart.setData(combinedData);
        setupChart();

        // Dynamic date formatting for the X-axis using the stored timestamp
        chart.getXAxis().setValueFormatter(new ValueFormatter() {
            private final SimpleDateFormat dailyFormat = new SimpleDateFormat("MMM dd", Locale.US);
            private final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm", Locale.US);

            @Override
            public String getAxisLabel(float value, AxisBase axis) {
                int index = (int) value;
                if (index >= 0 && index < stockData.size()) {
                    CandleEntry entry = stockData.get(index);
                    long timestamp = (long) entry.getData();
                    if (timeframe == StockDataHelper.Timeframe.DAILY) {
                        return dailyFormat.format(new Date(timestamp));
                    } else {
                        return timeFormat.format(new Date(timestamp));
                    }
                }
                return "";
            }
        });

        if (isInitialLoad) {
            if (!stockData.isEmpty()) {
                int dataSize = stockData.size();
                float desiredVisibleRange = 60f;
                if (dataSize > desiredVisibleRange) {
                    float scaleX = (float) dataSize / desiredVisibleRange;
                    float xCenter = dataSize - (desiredVisibleRange / 2f);
                    // We need a Y value for centering, let's find the corresponding entry
                    int centerIndex = (int)xCenter;
                    if (centerIndex >= 0 && centerIndex < dataSize) {
                        CandleEntry centerEntry = stockData.get(centerIndex);
                        float yCenter = centerEntry.getClose();
                        chart.zoom(scaleX, 1f, xCenter, yCenter, YAxis.AxisDependency.LEFT);
                    }
                } else {
                    chart.fitScreen(); // If less than 60 entries, just show them all
                }
            }
            isInitialLoad = false;
        }


        chart.invalidate(); // Refresh the chart
        Log.i(db_helper.DB_LOG_TAG, "Chart updated for timeframe: " + timeframe.name());
    }

    private void setupChart() {
        chart.setAutoScaleMinMaxEnabled(false);
        chart.setDragEnabled(true);
        chart.setScaleEnabled(true);
        chart.setDrawGridBackground(false);
        chart.setPinchZoom(true);

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


    @Override
    public void onClick(View v) {
        isInitialLoad = false; // Disable initial zoom on subsequent clicks
        int id = v.getId();
        if (id == R.id.button5m) {
            updateChartData(StockDataHelper.Timeframe.FIVE_MIN);
        } else if (id == R.id.button15m) {
            updateChartData(StockDataHelper.Timeframe.FIFTEEN_MIN);
        } else if (id == R.id.button1h) {
            updateChartData(StockDataHelper.Timeframe.HOURLY);
        } else if (id == R.id.button1d) {
            isInitialLoad = true;
            updateChartData(StockDataHelper.Timeframe.DAILY);
        }
    }
}
