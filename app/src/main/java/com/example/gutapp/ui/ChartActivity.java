package com.example.gutapp.ui;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.PopupWindow;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.gutapp.R;
import com.example.gutapp.data.chart.Indicator;
import com.example.gutapp.data.chart.IndicatorManager;
import com.example.gutapp.data.chart.Indicators;
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
import java.util.List;
import java.util.Locale;

public class ChartActivity extends AppCompatActivity implements View.OnClickListener {

    public static final String CHART_LOG_TAG = "GutChart";

    private DB_Helper db_helper;
    private StockDataHelper stockDataHelper;
    private CombinedChart chart;
    private String symbol; // Default symbol
    private boolean isInitialLoad = true;

    private TextView textViewTitle;

//indicator management
    private IndicatorManager indicatorManager;
    private AvailableIndicatorsAdapter availableIndicatorsAdapter;
    private ActiveIndicatorsAdapter activeIndicatorsAdapter;
    private PopupWindow indicatorPopupWindow;

    @SuppressLint("SetTextI18n")
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
        //loading chart symbol from the caller
        Intent intent = getIntent();
        symbol = intent.getStringExtra("symbol");
        String name = intent.getStringExtra("name");

        //initialize important database objects
        db_helper = new DB_Helper(this);
        stockDataHelper = (StockDataHelper) db_helper.getHelper(DB_Index.STOCK_TABLE);

        chart = findViewById(R.id.stockChart);


        // Set up button listeners
        findViewById(R.id.button5m).setOnClickListener(this);
        findViewById(R.id.button15m).setOnClickListener(this);
        findViewById(R.id.button1h).setOnClickListener(this);
        findViewById(R.id.button1d).setOnClickListener(this);
        findViewById(R.id.indicatorsButton).setOnClickListener(this);


        textViewTitle = findViewById(R.id.textViewTitle);

        ImageButton buttonHome = findViewById(R.id.buttonHome);
        buttonHome.setOnClickListener(this);

        //initialize indicator manager
        indicatorManager = new IndicatorManager(chart, db_helper, symbol);


        // Set up the chart
        setupChart();
        formatTile("1d");

        // Initial chart load with daily data
        updateChartData(StockDataHelper.Timeframe.DAILY);
    }

    private void updateChartData(StockDataHelper.Timeframe timeframe) {
        ArrayList<CandleEntry> stockData;
        try {
            stockData = stockDataHelper.getCachedStockData(symbol, timeframe);
        } catch (Exception e) {
            Log.e(db_helper.DB_LOG_TAG, "Error getting stock data: " + e.getMessage());
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
        indicatorManager.setCurrentTimeframe(timeframe);

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
            formatTile("5m");;
        } else if (id == R.id.button15m) {
            updateChartData(StockDataHelper.Timeframe.FIFTEEN_MIN);
            formatTile("15m");
        } else if (id == R.id.button1h) {
            updateChartData(StockDataHelper.Timeframe.HOURLY);
            formatTile("1h");
        } else if (id == R.id.button1d) {
            updateChartData(StockDataHelper.Timeframe.DAILY);
            formatTile("1d");
        }
        else if (id == R.id.buttonHome) {
            Intent intent = new Intent(this, HomeActivity.class);
            startActivity(intent);
        }
        else if (id == R.id.indicatorsButton) {
            showIndicatorsPopup();
            Log.i(CHART_LOG_TAG, "Indicators button clicked");
        }
    }

    public void formatTile(String timeFrame){
        textViewTitle.setText(symbol + " (" + timeFrame + ")");
    }

    //indicator management section

    // --- NEW: INDICATOR POPUP METHOD ---
    private void showIndicatorsPopup() {
        LayoutInflater inflater = (LayoutInflater) getSystemService(LAYOUT_INFLATER_SERVICE);
        View popupView = inflater.inflate(R.layout.popup_indicators, null);

        indicatorPopupWindow = new PopupWindow(popupView,
                ViewGroup.LayoutParams.MATCH_PARENT, 800 , true);

        RecyclerView availableRv = popupView.findViewById(R.id.availableIndicatorsRecyclerView);
        availableRv.setLayoutManager(new LinearLayoutManager(this));
        availableIndicatorsAdapter = new AvailableIndicatorsAdapter();
        availableRv.setAdapter(availableIndicatorsAdapter);

        RecyclerView activeRv = popupView.findViewById(R.id.activeIndicatorsRecyclerView);
        activeRv.setLayoutManager(new LinearLayoutManager(this));
        activeIndicatorsAdapter = new ActiveIndicatorsAdapter();
        activeRv.setAdapter(activeIndicatorsAdapter);

        popupView.findViewById(R.id.closePopupButton).setOnClickListener(v -> indicatorPopupWindow.dismiss());

        indicatorPopupWindow.showAtLocation(findViewById(R.id.main), Gravity.CENTER, 0, 0);

        // Make sure active indicators list is up-to-date when opening
        activeIndicatorsAdapter.updateData(new ArrayList<>(indicatorManager.getAllIndicators().values()));
    }

    // --- NEW: RECYCLERVIEW ADAPTERS AND VIEW HOLDERS ---

    // Adapter for the list of available indicators
    private class AvailableIndicatorsAdapter extends RecyclerView.Adapter<IndicatorViewHolder> {
        private final Indicators[] available = Indicators.values();

        @NonNull
        @Override
        public IndicatorViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_indicator, parent, false);
            return new IndicatorViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull IndicatorViewHolder holder, int position) {
            Indicators indicatorType = available[position];
            holder.indicatorName.setText(indicatorType.name());
            holder.buttonAction1.setText("Add");
            holder.buttonAction2.setText("Settings");

            holder.buttonAction1.setOnClickListener(v -> {
                // Add with default settings, e.g., SMA(20)
                float[] defaultParams = {Color.YELLOW, 20, 1f}; // color, period, width
                indicatorManager.createIndicator(indicatorType, defaultParams);
                activeIndicatorsAdapter.updateData(new ArrayList<>(indicatorManager.getAllIndicators().values()));
                Toast.makeText(ChartActivity.this, indicatorType.name() + " added.", Toast.LENGTH_SHORT).show();
            });
            holder.buttonAction2.setOnClickListener(v -> showSettingsDialog(indicatorType));
        }

        @Override
        public int getItemCount() {
            return available.length;
        }
    }

    // Adapter for the list of currently active indicators on the chart
    private class ActiveIndicatorsAdapter extends RecyclerView.Adapter<IndicatorViewHolder> {
        private List<Indicator> activeList = new ArrayList<>();

        void updateData(List<Indicator> newData) {
            this.activeList = newData;
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public IndicatorViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_indicator, parent, false);
            return new IndicatorViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull IndicatorViewHolder holder, int position) {
            Indicator indicator = activeList.get(position);
            holder.indicatorName.setText(indicator.getType().name() + " (" + indicator.getID() + ")");
            holder.buttonAction1.setText("Settings");
            holder.buttonAction2.setText("Remove");

            holder.buttonAction1.setOnClickListener(v -> showSettingsDialog(indicator));
            holder.buttonAction2.setOnClickListener(v -> {
                indicatorManager.deleteIndicator(indicator.getID());
                updateData(new ArrayList<>(indicatorManager.getAllIndicators().values())); // Refresh list
            });
        }

        @Override
        public int getItemCount() {
            return activeList.size();
        }
    }

    // ViewHolder used by both adapters
    private static class IndicatorViewHolder extends RecyclerView.ViewHolder {
        TextView indicatorName;
        Button buttonAction1, buttonAction2;
        IndicatorViewHolder(@NonNull View itemView) {
            super(itemView);
            indicatorName = itemView.findViewById(R.id.indicatorName);
            buttonAction1 = itemView.findViewById(R.id.buttonAction1);
            buttonAction2 = itemView.findViewById(R.id.buttonAction2);
        }
    }

    // --- NEW: SETTINGS DIALOGS (PLACEHOLDERS) ---

    // For a new indicator
    private void showSettingsDialog(Indicators type) {
        // Here you would inflate a custom layout with EditTexts for period, color pickers, etc.
        // For now, we'll just use a simple AlertDialog.
        new AlertDialog.Builder(this)
                .setTitle("Settings for " + type.name())
                .setMessage("Settings dialog not yet implemented. Adding with default values.")
                .setPositiveButton("Add", (dialog, which) -> {
                    float[] defaultParams = {Color.CYAN, 50, 3f}; // Different defaults for settings
                    indicatorManager.createIndicator(type, defaultParams);
                    activeIndicatorsAdapter.updateData(new ArrayList<>(indicatorManager.getAllIndicators().values()));
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // For an existing, active indicator
    private void showSettingsDialog(Indicator indicator) {
        // Again, this should be a custom dialog.
        new AlertDialog.Builder(this)
                .setTitle("Change Settings for " + indicator.getType().name() + " (" + indicator.getID() + ")")
                .setMessage("Settings dialog not yet implemented. No changes made.")
                .setPositiveButton("Apply", (dialog, which) -> {
                    // Example of changing settings
                    // float[] newParams = {Color.MAGENTA, 100, 4f};
                    // indicatorManager.changeSettings(indicator.getID(), newParams);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }
}
