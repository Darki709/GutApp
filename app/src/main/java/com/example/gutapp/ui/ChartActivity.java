package com.example.gutapp.ui;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
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
    private String timeframe = "1d";

//indicator management
    private IndicatorManager indicatorManager;
    private AvailableIndicatorsAdapter availableIndicatorsAdapter;
    private ActiveIndicatorsAdapter activeIndicatorsAdapter;
    private PopupWindow indicatorPopupWindow;
    private PopupWindow settingsPopupWindow; // Declare settings PopupWindow

    @SuppressLint("SetTextI11n")
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

    @Override
    protected void onStop(){
        super.onStop();
        indicatorManager.storePresets();
        Toast.makeText(this, "All presets saved", Toast.LENGTH_SHORT).show();
        Log.i(CHART_LOG_TAG, "All presets saved");
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


    @Override
    public void onClick(View v) {
        isInitialLoad = false; // Disable initial zoom on subsequent clicks
        int id = v.getId();
        if (id == R.id.button5m) {
            updateChartData(StockDataHelper.Timeframe.FIVE_MIN);
            timeframe = "5m";
            formatTile("5m");;
        } else if (id == R.id.button15m) {
            updateChartData(StockDataHelper.Timeframe.FIFTEEN_MIN);
            timeframe = "15m";
            formatTile("15m");
        }
        else if (id == R.id.button1h) {
            updateChartData(StockDataHelper.Timeframe.HOURLY);
            timeframe = "1h";
            formatTile("1h");
        }
        else if (id == R.id.button1d) {
            updateChartData(StockDataHelper.Timeframe.DAILY);
            timeframe = "1d";
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
        indicatorPopupWindow.setElevation(10f); // Added elevation

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
                float[] defaultParams;
                if (indicatorType == Indicators.BOLLINGER_BANDS) {
                    defaultParams = new float[]{Color.YELLOW, 20, 2.0f, 1f}; // color, period, stdDevMultiplier, width
                } else {
                    defaultParams = new float[]{Color.YELLOW, 20, 1f}; // color, period, width (for SMA, EMA)
                }
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

    // For a new indicator
    private void showSettingsDialog(Indicators type) {
        LayoutInflater inflater = (LayoutInflater) getSystemService(LAYOUT_INFLATER_SERVICE);
        View popupView = inflater.inflate(R.layout.popup_indicator_settings, null);

        // Set up the PopupWindow
        settingsPopupWindow = new PopupWindow(popupView,
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, true);
        settingsPopupWindow.setFocusable(true);
        settingsPopupWindow.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT)); // Important for custom background
        settingsPopupWindow.setElevation(10f); // Added elevation
        settingsPopupWindow.showAtLocation(findViewById(R.id.main), Gravity.CENTER, 0, 0);

        // Get references to views in the popup
        TextView dialogTitle = popupView.findViewById(R.id.dialog_title); // Assuming you add a TextView with this ID in XML
        if (dialogTitle != null) {
            dialogTitle.setText("Settings for " + type.name());
        }

        final EditText editTextPeriod = popupView.findViewById(R.id.edit_text_period);
        final EditText editTextWidth = popupView.findViewById(R.id.edit_text_width);
        final Button buttonColorPicker = popupView.findViewById(R.id.button_color_picker);
        final Button buttonCancel = popupView.findViewById(R.id.button_cancel);
        final Button buttonApply = popupView.findViewById(R.id.button_apply);

        // Default settings
        final int defaultColor = Color.YELLOW;
        final int defaultPeriod = 20;
        final float defaultWidth = 2f;
        final float defaultStdDevMultiplier = 2.0f; // Default for Bollinger Bands

        final int[] selectedColor = { defaultColor };

        editTextPeriod.setText(String.valueOf(defaultPeriod));
        editTextWidth.setText(String.valueOf(defaultWidth));
        buttonColorPicker.setBackgroundColor(selectedColor[0]);

        // Conditionally show/hide and set text for stdDevMultiplier
        final EditText editTextStdDevMultiplier = popupView.findViewById(R.id.edit_text_std_dev_multiplier);
        if (type == Indicators.BOLLINGER_BANDS) {
            editTextStdDevMultiplier.setVisibility(View.VISIBLE);
            editTextStdDevMultiplier.setText(String.valueOf(defaultStdDevMultiplier));
        } else {
            editTextStdDevMultiplier.setVisibility(View.GONE);
        }

        buttonColorPicker.setOnClickListener(v -> {
            final String[] colorNames = {"Red", "Green", "Blue", "Yellow", "Cyan", "Magenta"};
            final int[] colors = {Color.RED, Color.GREEN, Color.BLUE, Color.YELLOW, Color.CYAN, Color.MAGENTA};

            new AlertDialog.Builder(ChartActivity.this)
                .setTitle("Choose a color")
                .setItems(colorNames, (d, which) -> {
                    selectedColor[0] = colors[which];
                    buttonColorPicker.setBackgroundColor(selectedColor[0]);
                })
                .show();
        });

        buttonApply.setOnClickListener(v -> {
            try {
                String periodText = editTextPeriod.getText().toString();
                String widthText = editTextWidth.getText().toString();

                if (periodText.isEmpty() || widthText.isEmpty()) {
                    Toast.makeText(ChartActivity.this, "Fields cannot be empty.", Toast.LENGTH_SHORT).show();
                    return;
                }

                int newPeriod = Integer.parseInt(periodText);
                float newWidth = Float.parseFloat(widthText);

                float[] newParams;
                if (type == Indicators.BOLLINGER_BANDS) {
                    String stdDevText = editTextStdDevMultiplier.getText().toString();
                    if (stdDevText.isEmpty()) {
                        Toast.makeText(ChartActivity.this, "Standard Deviation Multiplier cannot be empty.", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    float newStdDevMultiplier = Float.parseFloat(stdDevText);
                    newParams = new float[]{(float) selectedColor[0], (float) newPeriod, newStdDevMultiplier, newWidth};
                } else {
                    newParams = new float[]{(float) selectedColor[0], (float) newPeriod, newWidth};
                }

                indicatorManager.createIndicator(type, newParams);
                
                activeIndicatorsAdapter.updateData(new ArrayList<>(indicatorManager.getAllIndicators().values()));
                Toast.makeText(ChartActivity.this, type.name() + " added.", Toast.LENGTH_SHORT).show();
                settingsPopupWindow.dismiss(); // Dismiss the popup
            } catch (NumberFormatException e) {
                Toast.makeText(ChartActivity.this, "Invalid number format.", Toast.LENGTH_SHORT).show();
            }
        });

        buttonCancel.setOnClickListener(v -> settingsPopupWindow.dismiss()); // Dismiss on cancel
    }

    // For an existing, active indicator
    private void showSettingsDialog(Indicator indicator) {
        LayoutInflater inflater = (LayoutInflater) getSystemService(LAYOUT_INFLATER_SERVICE);
        View popupView = inflater.inflate(R.layout.popup_indicator_settings, null);

        // Set up the PopupWindow
        settingsPopupWindow = new PopupWindow(popupView,
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, true);
        settingsPopupWindow.setFocusable(true);
        settingsPopupWindow.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT)); // Important for custom background
        settingsPopupWindow.setElevation(10f); // Added elevation
        settingsPopupWindow.showAtLocation(findViewById(R.id.main), Gravity.CENTER, 0, 0);

        // Get references to views in the popup
        TextView dialogTitle = popupView.findViewById(R.id.dialog_title); // Assuming you add a TextView with this ID in XML
        if (dialogTitle != null) {
            dialogTitle.setText("Change Settings for " + indicator.getType().name());
        }

        final EditText editTextPeriod = popupView.findViewById(R.id.edit_text_period);
        final EditText editTextWidth = popupView.findViewById(R.id.edit_text_width);
        final Button buttonColorPicker = popupView.findViewById(R.id.button_color_picker);
        final Button buttonCancel = popupView.findViewById(R.id.button_cancel);
        final Button buttonApply = popupView.findViewById(R.id.button_apply);

        // Parse current settings
        String[] params = indicator.getParams().split(":");
        final int currentColor = Integer.parseInt(params[0]);
        int currentPeriod = Integer.parseInt(params[1]);
        float currentWidth;
        float currentStdDevMultiplier = 0f; // Initialize for Bollinger Bands

        // Conditionally parse based on indicator type
        if (indicator.getType() == Indicators.BOLLINGER_BANDS) {
            currentStdDevMultiplier = Float.parseFloat(params[2]);
            currentWidth = Float.parseFloat(params[3]);
        } else {
            currentWidth = Float.parseFloat(params[2]);
        }

        final int[] selectedColor = { currentColor }; // Use an array to be final and mutable

        editTextPeriod.setText(String.valueOf(currentPeriod));
        editTextWidth.setText(String.valueOf(currentWidth));
        buttonColorPicker.setBackgroundColor(selectedColor[0]);

        final EditText editTextStdDevMultiplier = popupView.findViewById(R.id.edit_text_std_dev_multiplier);
        final TextView textStdDevMultiplier = popupView.findViewById(R.id.text_std_dev_multiplier);
        if (indicator.getType() == Indicators.BOLLINGER_BANDS) {
            editTextStdDevMultiplier.setVisibility(View.VISIBLE);
            textStdDevMultiplier.setVisibility(View.VISIBLE);
            editTextStdDevMultiplier.setText(String.valueOf(currentStdDevMultiplier));
        } else {
            editTextStdDevMultiplier.setVisibility(View.GONE);
            textStdDevMultiplier.setVisibility(View.GONE);
        }

        buttonColorPicker.setOnClickListener(v -> {
            final String[] colorNames = {"Red", "Green", "Blue", "Yellow", "Cyan", "Magenta"};
            final int[] colors = {Color.RED, Color.GREEN, Color.BLUE, Color.YELLOW, Color.CYAN, Color.MAGENTA};

            new AlertDialog.Builder(ChartActivity.this)
                .setTitle("Choose a color")
                .setItems(colorNames, (d, which) -> {
                    selectedColor[0] = colors[which];
                    buttonColorPicker.setBackgroundColor(selectedColor[0]);
                })
                .show();
        });

        buttonApply.setOnClickListener(v -> {
            try {
                String periodText = editTextPeriod.getText().toString();
                String widthText = editTextWidth.getText().toString();

                if (periodText.isEmpty() || widthText.isEmpty()) {
                    Toast.makeText(ChartActivity.this, "Fields cannot be empty.", Toast.LENGTH_SHORT).show();
                    return;
                }

                int newPeriod = Integer.parseInt(periodText);
                float newWidth = Float.parseFloat(widthText);

                float[] newParams;
                if (indicator.getType() == Indicators.BOLLINGER_BANDS) {
                    String stdDevText = editTextStdDevMultiplier.getText().toString();
                    if (stdDevText.isEmpty()) {
                        Toast.makeText(ChartActivity.this, "Standard Deviation Multiplier cannot be empty.", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    float newStdDevMultiplier = Float.parseFloat(stdDevText);
                    newParams = new float[]{(float) selectedColor[0], (float) newPeriod, newStdDevMultiplier, newWidth};
                } else {
                    newParams = new float[]{(float) selectedColor[0], (float) newPeriod, newWidth};
                }

                indicator.changeSettings(newParams, chart);
                Toast.makeText(ChartActivity.this, "Indicator updated.", Toast.LENGTH_SHORT).show();
                settingsPopupWindow.dismiss(); // Dismiss the popup
            } catch (NumberFormatException e) {
                Toast.makeText(ChartActivity.this, "Invalid number format.", Toast.LENGTH_SHORT).show();
            }
        });

        buttonCancel.setOnClickListener(v -> settingsPopupWindow.dismiss()); // Dismiss on cancel
    }
}
