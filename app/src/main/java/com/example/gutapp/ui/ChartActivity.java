package com.example.gutapp.ui;

import static android.widget.Toast.LENGTH_LONG;
import static android.widget.Toast.LENGTH_SHORT;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.Nullable;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.gutapp.R;
import com.example.gutapp.data.OrderDialog;
import com.example.gutapp.data.StockChart;
import com.example.gutapp.data.UserGlobals;
import com.example.gutapp.data.api.GeminiHelper;
import com.example.gutapp.data.indicators.IndicatorRegistry;
import com.example.gutapp.data.models.Candle;
import com.example.gutapp.data.models.Order;
import com.example.gutapp.data.models.TickerInfo;
import com.example.gutapp.data.models.TickerInformation;
import com.example.gutapp.database.DB_Helper;
import com.example.gutapp.database.LastFetchCacheHelper;
import com.example.gutapp.database.StockDataHelper;
import com.example.gutapp.session.DataType;
import com.example.gutapp.session.NetworkClient;
import com.example.gutapp.session.Requests.FetchOrders;
import com.example.gutapp.session.Requests.RequestTickerData;
import com.example.gutapp.session.Requests.SendOrder;
import com.example.gutapp.session.Requests.TickerInfoRequest;
import com.example.gutapp.session.SessionCallback;
import com.example.gutapp.ui.fragments.IndicatorsPanel;
import com.example.gutapp.ui.fragments.OrdersList;
import com.github.mikephil.charting.charts.CombinedChart;
import com.github.mikephil.charting.charts.LineChart;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.firebase.FirebaseApp;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Locale;
import java.util.Optional;

public class ChartActivity extends SessionActivity implements
        View.OnClickListener,
        OrderDialog.OrderDialogListener,
        OrdersList.Listener,
        IndicatorsPanel.IndicatorListener,
        GeminiHelper.AnalysisCallback {

    public static final String CHART_LOG_TAG = "GutChart";

    // ── Fields ────────────────────────────────────────────────────────
    private DB_Helper db_helper;
    private StockChart chartContainer;
    private String symbol;
    private String name;
    private TextView textViewTitle;
    private TextView textViewName;
    private TextView textViewPrice;
    private StockDataHelper.Timeframe interval;
    private OrderDialog activeDialog = null;
    private volatile double current_price;

    private OrdersList ordersFragment;
    private ArrayList<Order> allOrders = new ArrayList<>();

    // Chart type chips
    private TextView btnChartCandle, btnChartBar, btnChartLine;
    private TextView activeCTypeBtn;

    // Timeframe chips
    private TextView btn5m, btn15m, btn1h, btn1d;
    private TextView activeTfBtn;

    // Sub-chart LineChart views (wired to StockChart after inflation)
    @Nullable private View subChartContainer;
    @Nullable private LineChart subChart1;
    @Nullable private LineChart subChart2;

    // ── onCreate ───────────────────────────────────────────────────────
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

        Intent intent = getIntent();
        symbol = intent.getStringExtra("symbol");
        name   = intent.getStringExtra("name");

        db_helper = DB_Helper.getInstance(this);

        // ── Chart setup ────────────────────────────────────────────
        CombinedChart chart = findViewById(R.id.stockChart);
        chartContainer = new StockChart(chart, this);
        chartContainer.setupChart(findViewById(R.id.candleDataTextView));
        chartContainer.bindListener(this);

        // ── Sub-chart views ────────────────────────────────────────
        // Register each LineChart with StockChart so it can drive
        // their data and visibility automatically.
        subChartContainer = findViewById(R.id.subChartsScroll);
        subChart1 = findViewById(R.id.subChart1);
        subChart2 = findViewById(R.id.subChart2);
        if (subChartContainer != null) chartContainer.setSubChartsContainer(Optional.of(subChartContainer));
        if (subChart1 != null) chartContainer.attachSubChart(subChart1);
        if (subChart2 != null) chartContainer.attachSubChart(subChart2);

        // ── Text views ─────────────────────────────────────────────
        textViewTitle = findViewById(R.id.textViewTitle);
        textViewPrice = findViewById(R.id.textViewPrice);
        textViewName  = findViewById(R.id.textViewName);
        textViewName.setText(name);

        // ── Top bar buttons ────────────────────────────────────────
        ImageButton buttonHome = findViewById(R.id.buttonHome);
        buttonHome.setOnClickListener(this);

        // ── Buy / Sell ─────────────────────────────────────────────
        findViewById(R.id.buttonBuy).setOnClickListener(this);
        findViewById(R.id.buttonSell).setOnClickListener(this);

        // ── Chart type chips ───────────────────────────────────────
        btnChartCandle = findViewById(R.id.btnChartCandle);
        btnChartBar    = findViewById(R.id.btnChartBar);
        btnChartLine   = findViewById(R.id.btnChartLine);
        activeCTypeBtn = btnChartCandle;

        btnChartCandle.setOnClickListener(this);
        btnChartBar.setOnClickListener(this);
        btnChartLine.setOnClickListener(this);

        // ── Timeframe chips ────────────────────────────────────────
        btn5m  = findViewById(R.id.button5m);
        btn15m = findViewById(R.id.button15m);
        btn1h  = findViewById(R.id.button1h);
        btn1d  = findViewById(R.id.button1d);

        btn5m.setOnClickListener(this);
        btn15m.setOnClickListener(this);
        btn1h.setOnClickListener(this);
        btn1d.setOnClickListener(this);

        // ── Indicators chip ────────────────────────────────────────
        findViewById(R.id.indicatorsButton).setOnClickListener(this);

        // ── Zoom buttons ───────────────────────────────────────────
        findViewById(R.id.btnZoomIn).setOnClickListener(this);
        findViewById(R.id.btnZoomOut).setOnClickListener(this);
        findViewById(R.id.btnZoomReset).setOnClickListener(this);

        // ── AI button ──────────────────────────────────────────────
        findViewById(R.id.btnAiAnalyze).setOnClickListener(this);

        // ── Initial state ──────────────────────────────────────────
        this.interval = StockDataHelper.Timeframe.DAILY;
        activeTfBtn = btn1d;
        setChipActive(btn1d, true);
        formatTitle(this.interval.value);

        TickerInfoRequest req = new TickerInfoRequest(symbol, this);
        NetworkClient.getInstance(null).getSessionManager().pushRequest(req);

        findViewById(R.id.ordersFragmentContainer).setVisibility(View.GONE);
        findViewById(R.id.emptyOrdersView).setVisibility(View.GONE);

        FetchOrders fetchOrders = new FetchOrders(symbol, FetchOrders.OrderView.ACTIVE, 0, this);
        NetworkClient.getInstance(null).getSessionManager().pushRequest(fetchOrders);
    }

    // ── Lifecycle ──────────────────────────────────────────────────────
    @Override
    protected void onResume() {
        super.onResume();
        updateChartData();
    }

    @Override
    protected void refreshNetwork() {
        onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
        chartContainer.flushRequests();
        chartContainer.clearChart();
    }

    // ── Click handling ─────────────────────────────────────────────────
    @Override
    public void onClick(View v) {
        int id = v.getId();

        // Timeframe
        if      (id == R.id.button5m)  switchTimeframe(StockDataHelper.Timeframe.FIVE_MIN,    btn5m);
        else if (id == R.id.button15m) switchTimeframe(StockDataHelper.Timeframe.FIFTEEN_MIN, btn15m);
        else if (id == R.id.button1h)  switchTimeframe(StockDataHelper.Timeframe.HOURLY,      btn1h);
        else if (id == R.id.button1d)  switchTimeframe(StockDataHelper.Timeframe.DAILY,       btn1d);

            // Chart types
        else if (id == R.id.btnChartCandle) switchChartType(StockChart.ChartType.CANDLE, btnChartCandle);
        else if (id == R.id.btnChartBar)    switchChartType(StockChart.ChartType.BAR,    btnChartBar);
        else if (id == R.id.btnChartLine)   switchChartType(StockChart.ChartType.LINE,   btnChartLine);

            // Indicators
        else if (id == R.id.indicatorsButton) openIndicatorsPanel();

            // Zoom
        else if (id == R.id.btnZoomIn)    chartContainer.zoomIn();
        else if (id == R.id.btnZoomOut)   chartContainer.zoomOut();
        else if (id == R.id.btnZoomReset) chartContainer.zoomReset();

            // Navigation
        else if (id == R.id.buttonHome) startActivity(new Intent(this, HomeActivity.class));

            // Orders
        else if (id == R.id.buttonBuy) {
            activeDialog = new OrderDialog(this, symbol, current_price, Order.OrderType.Long, this);
            activeDialog.show();
        } else if (id == R.id.buttonSell) {
            activeDialog = new OrderDialog(this, symbol, current_price, Order.OrderType.Short, this);
            activeDialog.show();
        }

        // AI — from user's file: disable button + show spinner while loading
        else if (id == R.id.btnAiAnalyze) {
            findViewById(R.id.btnAiAnalyze).setEnabled(false);
            findViewById(R.id.pbAiLoading).setVisibility(View.VISIBLE);
            performAiAnalysis();
        }
    }

    // ── Chart type switcher ────────────────────────────────────────────
    private void switchChartType(StockChart.ChartType type, TextView chip) {
        if (chip == activeCTypeBtn) return;
        setChipActive(activeCTypeBtn, false);
        activeCTypeBtn = chip;
        setChipActive(chip, true);
        chartContainer.setChartType(type);
    }

    // ── Timeframe switcher ─────────────────────────────────────────────
    private void switchTimeframe(StockDataHelper.Timeframe tf, TextView chip) {
        if (chip == activeTfBtn) return;
        setChipActive(activeTfBtn, false);
        activeTfBtn = chip;
        setChipActive(chip, true);
        interval = tf;
        updateChartData();
        formatTitle(interval.value);
    }

    private void setChipActive(TextView chip, boolean active) {
        if (chip == null) return;
        if (active) {
            chip.setBackgroundResource(R.drawable.chart_btn_active);
            chip.setTextColor(Color.parseColor("#ECEFF1"));
            chip.setTypeface(null, Typeface.BOLD);
        } else {
            chip.setBackgroundResource(R.drawable.chart_btn_inactive);
            chip.setTextColor(Color.parseColor("#78909C"));
            chip.setTypeface(null, Typeface.NORMAL);
        }
    }

    // ── Indicators panel ──────────────────────────────────────────────
    private void openIndicatorsPanel() {
        // New API: no settings argument — panel reads/writes IndicatorRegistry directly
        IndicatorsPanel panel = IndicatorsPanel.newInstance();
        panel.setListener(this);
        panel.show(getSupportFragmentManager(), "indicators");
    }

    /**
     * Called by IndicatorsPanel after every toggle/slider change.
     * New signature: no argument. IndicatorRegistry already holds the latest state.
     */
    @Override
    public void onIndicatorsChanged() {
        // Sync sub-chart divider visibility with active sub-chart count
        refreshSubChartDividers();
        // Tell StockChart to recompute and redraw all active indicators
        chartContainer.applyIndicators();
    }

    /**
     * Shows/hides the 1dp divider lines that sit above each sub-chart pane.
     * Also updates the Indicators chip text with active count badge.
     */
    private void refreshSubChartDividers() {
        int subCount = IndicatorRegistry.getInstance().getEnabledSubCharts().size();

//        View div1 = findViewById(R.id.subChartDivider1);
//        View div2 = findViewById(R.id.subChartDivider2);
//        if (div1 != null) div1.setVisibility(subCount >= 1 ? View.VISIBLE : View.GONE);
//        if (div2 != null) div2.setVisibility(subCount >= 2 ? View.VISIBLE : View.GONE);

        // Update chip badge: "⊕ Indicators (3)" when any are active
        TextView indBtn = findViewById(R.id.indicatorsButton);
        if (indBtn != null) {
            int totalActive = IndicatorRegistry.getInstance().getEnabled().size();
            if (totalActive > 0) {
                indBtn.setText("⊕ Indicators (" + totalActive + ")");
                indBtn.setTextColor(Color.parseColor("#2196F3")); // blue accent when active
            } else {
                indBtn.setText("⊕ Indicators");
                indBtn.setTextColor(Color.parseColor("#78909C"));
            }
        }
    }

    // ── Title helper ──────────────────────────────────────────────────
    public void formatTitle(String timeFrame) {
        textViewTitle.setText(symbol + " (" + timeFrame + ")");
    }

    // ── Chart data loading ─────────────────────────────────────────────
    private void updateChartData() {
        chartContainer.setInterval(interval);
        chartContainer.flushRequests();
        chartContainer.clearChart();

        StockDataHelper stockDataHelper = new StockDataHelper(db_helper);
        LastFetchCacheHelper cacheHelper = new LastFetchCacheHelper(db_helper);
        long lastFetchTime = cacheHelper.getLastFetchTime(symbol, interval);

        RequestTickerData request = getRequest(symbol, interval, lastFetchTime, 0, true, false, chartContainer);
        NetworkClient.getInstance(this).getSessionManager().pushRequest(request);
        RequestTickerData requestStream = getRequest(symbol, interval, 0, 0, false, true, chartContainer);
        NetworkClient.getInstance(this).getSessionManager().pushRequest(requestStream);

        try {
            ArrayList<Candle> stockData = stockDataHelper.getCachedStockData(symbol, interval);
            if (stockData != null && !stockData.isEmpty()) {
                chartContainer.addChunk(stockData);
                double price = stockData.get(stockData.size() - 1).close;
                textViewPrice.setText(String.format(Locale.US, "%.6f", price));
                current_price = price;
            } else {
                throw new Exception("Cache is empty");
            }
        } catch (Exception e) {
            Log.e(DB_Helper.DB_LOG_TAG, "Error fetching cached stock data: " + e.getMessage());
            if (lastFetchTime != 0) {
                RequestTickerData request2 = getRequest(symbol, interval, 0, lastFetchTime, true, false, chartContainer);
                NetworkClient.getInstance(this).getSessionManager().pushRequest(request2);
            }
        }
    }

    private RequestTickerData getRequest(String symbol, StockDataHelper.Timeframe timeframe,
                                         long start_ts, long end_ts,
                                         boolean isSnapshot, boolean isStream,
                                         SessionCallback caller) {
        RequestTickerData request = new RequestTickerData(symbol, timeframe,
                start_ts, end_ts, isSnapshot, isStream, caller);
        chartContainer.addToCurrentRequest(request.getReqId());
        return request;
    }

    // ── SessionCallback ───────────────────────────────────────────────
    @Override
    public void onDataReceived(DataType msgType, Object parsedData) {
        switch (msgType) {
            case SEARCH_NO_RESULT:
                runOnUiThread(() -> Toast.makeText(this, (String) parsedData, LENGTH_SHORT).show());
                break;

            case MARKET_DATA:
                current_price = (Double) parsedData;
                if (chartContainer.isDone()) {
                    runOnUiThread(() -> {
                        updatePriceDisplay((Double) parsedData);
                        if (activeDialog != null && activeDialog.isShowing()) {
                            activeDialog.updateLivePrice((Double) parsedData);
                        }
                    });
                }
                break;

            case ORDER_INVALID:
            case ORDER_SLIP:
                runOnUiThread(() -> Toast.makeText(this, (String) parsedData, LENGTH_LONG).show());
                break;

            case ORDER_RECEIVED:
                Order order = (Order) parsedData;
                runOnUiThread(() -> {
                    Toast.makeText(this, String.format(
                            "Order completed for %s, paid $%.16f per unit",
                            symbol, order.getEntry_price()), LENGTH_LONG).show();
                    synchronized (allOrders) { allOrders.add(order); }
                    updateOrdersUI();
                });
                break;

            case ORDERS_BATCH:
                runOnUiThread(() -> {
                    synchronized (allOrders) { allOrders.addAll((ArrayList<Order>) parsedData); }
                    updateOrdersUI();
                });
                break;

            case TICKER_INFORMATION:
                TickerInformation info = (TickerInformation) parsedData;
                name = info.name;
                runOnUiThread(() -> {
                    textViewName.setText(name);
                    ((TextView) findViewById(R.id.tvExchange)).setText(info.exchange);
                    ((TextView) findViewById(R.id.tvSector)).setText(info.sector);
                    ((TextView) findViewById(R.id.tvType)).setText(info.type.type);
                });
                break;
        }
    }

    private double lastDisplayedPrice = 0;

    private void updatePriceDisplay(double price) {
        textViewPrice.setText(String.format(Locale.US, "%.6f", price));
        if (lastDisplayedPrice > 0) {
            if      (price > lastDisplayedPrice) textViewPrice.setTextColor(Color.parseColor("#00FF88"));
            else if (price < lastDisplayedPrice) textViewPrice.setTextColor(Color.parseColor("#FF4444"));
        }
        lastDisplayedPrice = price;
    }

    // ── Orders UI ─────────────────────────────────────────────────────
    private void updateOrdersUI() {
        View container = findViewById(R.id.ordersFragmentContainer);
        View emptyView  = findViewById(R.id.emptyOrdersView);

        if (allOrders.isEmpty()) {
            container.setVisibility(View.GONE);
            emptyView.setVisibility(View.VISIBLE);
        } else {
            emptyView.setVisibility(View.GONE);
            container.setVisibility(View.VISIBLE);
            this.ordersFragment = OrdersList.newInstance(allOrders);
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.ordersFragmentContainer, ordersFragment)
                    .commit();
            ordersFragment.setListener(this);
        }
    }

    @Override
    public void onConfirmOrder(int quantity, double price, Order.OrderType type) {
        SendOrder request = new SendOrder(symbol, quantity, price, type, this, this);
        NetworkClient.getInstance(null).getSessionManager().pushRequest(request);
        Toast.makeText(this, String.format(
                "Sent order for symbol: %s, order price: $%.16f",
                symbol, quantity * price), LENGTH_SHORT).show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (ordersFragment != null) ordersFragment.onPause();
    }

    @Override public void PLUpdate(double totalPL) {}

    @Override
    public void notifyOrderRemoved(Order order) {
        synchronized (allOrders) { allOrders.remove(order); }
        runOnUiThread(() -> {
            if (ordersFragment != null && ordersFragment.isEmpty()) {
                findViewById(R.id.ordersFragmentContainer).setVisibility(View.GONE);
                findViewById(R.id.emptyOrdersView).setVisibility(View.VISIBLE);
            }
        });
    }

    // ── AI Analysis — unchanged from user's file ──────────────────────
    private String cachedAiResponse = null;

    private void performAiAnalysis() {
        if (cachedAiResponse != null) {
            showAiPopup(cachedAiResponse);
            return;
        }
        GeminiHelper helper = new GeminiHelper();
        helper.getAiAnalysis(name, this);
    }

    private void showAiPopup(String rawJson) {
        runOnUiThread(() -> {
            View popupView = getLayoutInflater().inflate(R.layout.dialog_ai_analysis, null);
            BottomSheetDialog dialog = new BottomSheetDialog(this);

            TextView tvRating   = popupView.findViewById(R.id.tvRating);
            TextView tvScore    = popupView.findViewById(R.id.tvScore);
            TextView tvSentiment = popupView.findViewById(R.id.tvSentiment);
            TextView tvHistory  = popupView.findViewById(R.id.tvHistory);
            ImageButton btnClose = popupView.findViewById(R.id.btnClosePopup);
            btnClose.setOnClickListener(v -> dialog.dismiss());

            try {
                // Re-enable AI button and hide spinner
                findViewById(R.id.btnAiAnalyze).setEnabled(true);
                findViewById(R.id.pbAiLoading).setVisibility(View.GONE);

                JSONObject json = new JSONObject(rawJson);
                String rating   = json.optString("rating_word", "Neutral");
                int    score    = json.optInt("score_out_of_hundred", 0);
                String sentiment = json.optString("sentiment_analysis", "No sentiment data available.");
                String history  = json.optString("company_history", "No history available.");

                tvRating.setText(rating);
                tvScore.setText(score + "/100");
                tvSentiment.setText(sentiment);
                tvHistory.setText(history);

                if      (rating.equalsIgnoreCase("Bullish")) tvRating.setTextColor(Color.parseColor("#4CAF50"));
                else if (rating.equalsIgnoreCase("Bearish")) tvRating.setTextColor(Color.parseColor("#F44336"));
                else                                          tvRating.setTextColor(Color.WHITE);

            } catch (JSONException e) {
                Log.e(CHART_LOG_TAG, "Failed to parse AI JSON", e);
                tvSentiment.setText("Error parsing analysis. Please try again.");
            }

            dialog.setContentView(popupView);
            dialog.show();
        });
    }

    @Override
    public void onSuccess(String result) {
        cachedAiResponse = result;
        showAiPopup(result);
    }

    @Override
    public void onError(String error) {
        runOnUiThread(() -> {
            // Re-enable AI button and hide spinner on error too
            findViewById(R.id.btnAiAnalyze).setEnabled(true);
            findViewById(R.id.pbAiLoading).setVisibility(View.GONE);
            Toast.makeText(this, "AI analysis failed", LENGTH_SHORT).show();
        });
    }
}