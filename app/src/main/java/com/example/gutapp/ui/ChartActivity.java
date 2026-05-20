package com.example.gutapp.ui;

import static android.widget.Toast.LENGTH_LONG;
import static android.widget.Toast.LENGTH_SHORT;

import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.InputType;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewParent;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.Nullable;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.widget.NestedScrollView;

import com.example.gutapp.R;
import com.example.gutapp.data.OrderDialog;
import com.example.gutapp.data.StockChart;
import com.example.gutapp.data.UserGlobals;
import com.example.gutapp.data.api.GeminiHelper;
import com.example.gutapp.data.indicators.CurrentSessionHolder;
import com.example.gutapp.data.indicators.Indicator;
import com.example.gutapp.data.indicators.IndicatorRegistry;
import com.example.gutapp.data.indicators.IndicatorSession;
import com.example.gutapp.data.indicators.PresetRepository;
import com.example.gutapp.data.models.Candle;
import com.example.gutapp.data.models.Order;
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
import com.example.gutapp.ui.fragments.DrawingToolbarFragment;
import com.example.gutapp.ui.fragments.IndicatorsPanel;
import com.example.gutapp.ui.fragments.OrdersList;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.snackbar.Snackbar;
import com.example.gutapp.ui.chart.DrawingChart;
import com.example.gutapp.ui.chart.DrawingEditPanel;
import com.example.gutapp.data.drawing.DrawingManager;
import com.example.gutapp.data.drawing.DrawingPersistence;
import com.example.gutapp.data.drawing.ChartDrawing;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class ChartActivity extends SessionActivity implements
        View.OnClickListener,
        OrderDialog.OrderDialogListener,
        OrdersList.Listener,
        IndicatorsPanel.IndicatorListener,
        GeminiHelper.AnalysisCallback {

    public static final String CHART_LOG_TAG = "GutChart";

    private DB_Helper db_helper;
    private StockChart chartContainer;
    private String symbol, name;
    private TextView textViewTitle, textViewName, textViewPrice;
    private StockDataHelper.Timeframe interval;
    private OrderDialog activeDialog = null;
    private volatile double current_price;

    private OrdersList ordersFragment;
    private ArrayList<Order> allOrders = new ArrayList<>();

    // Chart type chips
    private TextView btnChartCandle, btnChartBar, btnChartLine, activeCTypeBtn;
    // Timeframe chips
    private TextView btn5m, btn15m, btn1h, btn1d, activeTfBtn;

    // Indicator session + persistence
    private IndicatorSession indicatorSession = new IndicatorSession();
    private PresetRepository presetRepo;
    private boolean isManagerReturned = false;
    private LinearLayout presetListContainer = null;

    // Drawing persistence (auto-save / auto-load per ticker)
    private DrawingPersistence drawingPersistence;

    // Drawing mode HUD
    private android.widget.LinearLayout drawingModeHud;
    private android.widget.TextView     hudToolName;

    // Drawing edit panel (slides up when a drawing is selected)
    private DrawingEditPanel drawingEditPanel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_chart);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets sb = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(sb.left, sb.top, sb.right, sb.bottom);
            return insets;
        });

        symbol = getIntent().getStringExtra("symbol");
        name   = getIntent().getStringExtra("name");
        db_helper  = DB_Helper.getInstance(this);
        presetRepo = new PresetRepository(this);

        // Auto-load saved indicator state for this ticker
        presetRepo.autoLoad(symbol, indicatorSession);

        // Expose session for ProfileActivity's "Save Current" shortcut
        CurrentSessionHolder.set(indicatorSession);

        // ── Chart setup ────────────────────────────────────────────
        DrawingChart chart = findViewById(R.id.stockChart);
        chartContainer = new StockChart(chart, this);
        chartContainer.setupChart(findViewById(R.id.candleDataTextView));
        chartContainer.bindListener(this);
        chartContainer.setIndicatorSession(indicatorSession);
        chartContainer.setSubChartsContainer(
                (LinearLayout) findViewById(R.id.subChartsContainer));

        // ── Drawing persistence (auto-save / auto-load) ────────────
        drawingPersistence = new DrawingPersistence(this);
        chart.setDrawingEventListener(new DrawingChart.DrawingEventListener() {
            @Override public void onDrawingCreated(ChartDrawing drawing) {}
            @Override public void onDrawingRemoved(ChartDrawing drawing) {}
            @Override public void onDrawingSelected(@androidx.annotation.Nullable ChartDrawing drawing) {
                if (drawing != null && drawingEditPanel != null) {
                    drawingEditPanel.show(
                            drawing,
                            () -> { chart.removeDrawing(drawing.getInstanceId()); drawingEditPanel.hide(); },
                            () -> { chart.getDrawingManager().clearSelection(); chart.postInvalidate(); drawingEditPanel.hide(); },
                            () -> { chart.postInvalidate(); drawingPersistence.save(symbol, chart.getDrawingManager()); }
                    );
                } else if (drawing == null && drawingEditPanel != null) {
                    drawingEditPanel.hide();
                }
            }
            @Override public void onDrawingsChanged() {
                drawingPersistence.save(symbol, chart.getDrawingManager());
            }
            @Override public void onToolChanged(@androidx.annotation.Nullable DrawingManager.DrawingTool tool) {
                updateDrawingHud(tool);
                // Hide the edit panel whenever we enter drawing mode
                if (tool != null && drawingEditPanel != null) drawingEditPanel.hide();
            }
        });

        // ── Drawing Mode HUD ───────────────────────────────────────
        drawingModeHud = findViewById(R.id.drawingModeHud);
        hudToolName    = findViewById(R.id.hudToolName);
        findViewById(R.id.hudExitBtn).setOnClickListener(v -> {
            chart.setActiveTool(null);
            chart.cancelCurrentDrawing();
        });

        // ── Drawing Edit Panel ─────────────────────────────────────
        // Add programmatically so it sits at the bottom of the root layout
        drawingEditPanel = new DrawingEditPanel(this);
        android.widget.FrameLayout root = findViewById(android.R.id.content);
        // Find the root ConstraintLayout
        android.view.ViewGroup rootView = (android.view.ViewGroup) getWindow().getDecorView()
                .findViewById(android.R.id.content);
        if (rootView instanceof android.widget.FrameLayout && rootView.getChildCount() > 0) {
            android.view.View rootChild = rootView.getChildAt(0);
            if (rootChild instanceof android.view.ViewGroup) {
                android.view.ViewGroup vg = (android.view.ViewGroup) rootChild;
                androidx.constraintlayout.widget.ConstraintLayout.LayoutParams clp =
                        new androidx.constraintlayout.widget.ConstraintLayout.LayoutParams(
                                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                                android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
                clp.bottomToBottom = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.PARENT_ID;
                clp.startToStart  = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.PARENT_ID;
                clp.endToEnd      = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.PARENT_ID;
                drawingEditPanel.setLayoutParams(clp);
                drawingEditPanel.setTranslationZ(20f);
                vg.addView(drawingEditPanel);
            }
        }

        // Reload saved drawings every time a fresh candle batch arrives (covers timeframe switches).
        // We clear user drawings first so switching 1D→5m doesn't duplicate them.
        chartContainer.setCandlesReadyCallback(() -> {
            chart.getDrawingManager().clearUserDrawingsSilent();
            drawingPersistence.load(symbol, chart.getDrawingManager());
            chart.postInvalidate();
        });
        chartContainer.setSubChartsScroller((NestedScrollView) findViewById(R.id.subChartsScrollView));

        // ── Text views ─────────────────────────────────────────────
        textViewTitle = findViewById(R.id.textViewTitle);
        textViewPrice = findViewById(R.id.textViewPrice);
        textViewName  = findViewById(R.id.textViewName);
        textViewName.setText(name);

        // ── Buttons ────────────────────────────────────────────────
        findViewById(R.id.buttonHome).setOnClickListener(this);
        findViewById(R.id.buttonBuy).setOnClickListener(this);
        findViewById(R.id.buttonSell).setOnClickListener(this);
        findViewById(R.id.indicatorsButton).setOnClickListener(this);
        findViewById(R.id.btnPresets).setOnClickListener(this);       // ← preset picker
        findViewById(R.id.btnAiAnalyze).setOnClickListener(this);
        findViewById(R.id.btnZoomIn).setOnClickListener(this);
        findViewById(R.id.btnZoomOut).setOnClickListener(this);
        findViewById(R.id.btnZoomReset).setOnClickListener(this);
        findViewById(R.id.btnCalculateBias).setOnClickListener(this);
        findViewById(R.id.drawingPanel).setOnClickListener(this);
        findViewById(R.id.btnAlerts).setOnClickListener(this);



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
        btn5m.setOnClickListener(this);  btn15m.setOnClickListener(this);
        btn1h.setOnClickListener(this);  btn1d.setOnClickListener(this);

        interval = StockDataHelper.Timeframe.DAILY;
        activeTfBtn = btn1d;
        setChipActive(btn1d, true);
        formatTitle(interval.value);

        NetworkClient.getInstance(null).getSessionManager()
                .pushRequest(new TickerInfoRequest(symbol, this));

        findViewById(R.id.ordersFragmentContainer).setVisibility(View.GONE);
        findViewById(R.id.emptyOrdersView).setVisibility(View.GONE);

        NetworkClient.getInstance(null).getSessionManager()
                .pushRequest(new FetchOrders(symbol, FetchOrders.OrderView.ACTIVE, 0, this));

        DrawingToolbarFragment sheet = DrawingToolbarFragment.newInstance(chartContainer.getDrawingChart());
        refreshIndicatorChip();
    }

    private String getSentimentString(int score) {
        if (score >= 70) return "Strong Bullish 🚀";
        if (score >= 55) return "Bullish 📈";
        if (score > 45) return "Neutral ⚖";
        if (score > 30) return "Bearish 📉";
        return "Strong Bearish 🩸";
    }

    // ── Lifecycle ──────────────────────────────────────────────────────
    @Override protected void onResume() {
        super.onResume();
        updateChartData();
        if(isManagerReturned)
        {
            refreshPresetListUI();
            isManagerReturned = false;
        }
    }
    @Override protected void refreshNetwork() { onResume(); }

    @Override
    protected void onPause() {
        super.onPause();
        presetRepo.autoSave(symbol, indicatorSession);
        // Persist all user drawings for this ticker before going to background
        if (drawingPersistence != null) {
            DrawingChart drawingChart = chartContainer.getDrawingChart();
            if (drawingChart != null)
                drawingPersistence.save(symbol, drawingChart.getDrawingManager());
        }
        chartContainer.flushRequests();
        chartContainer.clearChart();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        CurrentSessionHolder.set(null);
        if (ordersFragment != null) ordersFragment.onPause();
    }

    // ── Click handling ─────────────────────────────────────────────────
    @Override
    public void onClick(View v) {
        int id = v.getId();
        if      (id == R.id.button5m)       switchTimeframe(StockDataHelper.Timeframe.FIVE_MIN,    btn5m);
        else if (id == R.id.button15m)      switchTimeframe(StockDataHelper.Timeframe.FIFTEEN_MIN, btn15m);
        else if (id == R.id.button1h)       switchTimeframe(StockDataHelper.Timeframe.HOURLY,      btn1h);
        else if (id == R.id.button1d)       switchTimeframe(StockDataHelper.Timeframe.DAILY,       btn1d);
        else if (id == R.id.btnChartCandle) switchChartType(StockChart.ChartType.CANDLE, btnChartCandle);
        else if (id == R.id.btnChartBar)    switchChartType(StockChart.ChartType.BAR,   btnChartBar);
        else if (id == R.id.btnChartLine)   switchChartType(StockChart.ChartType.LINE,  btnChartLine);
        else if (id == R.id.indicatorsButton) openIndicatorsPanel();
        else if (id == R.id.btnPresets)     openPresetPicker();
        else if (id == R.id.btnZoomIn)      chartContainer.zoomIn();
        else if (id == R.id.btnZoomOut)     chartContainer.zoomOut();
        else if (id == R.id.btnZoomReset)   chartContainer.zoomReset();
        else if (id == R.id.buttonHome)     startActivity(new Intent(this, HomeActivity.class));
        else if (id == R.id.buttonBuy)  {
            activeDialog = new OrderDialog(this, symbol, current_price, Order.OrderType.Long, this);
            activeDialog.show();
        } else if (id == R.id.buttonSell) {
            activeDialog = new OrderDialog(this, symbol, current_price, Order.OrderType.Short, this);
            activeDialog.show();
        } else if (id == R.id.btnAiAnalyze) {
            findViewById(R.id.btnAiAnalyze).setEnabled(false);
            ((ProgressBar) findViewById(R.id.pbAiLoading)).setVisibility(View.VISIBLE);
            performAiAnalysis();
        }
        else if(id == R.id.btnCalculateBias){
            List<Indicator> active = indicatorSession.getAll();
            if (active.isEmpty()) {
                Toast.makeText(this, "Add indicators to calculate bias", Toast.LENGTH_SHORT).show();
                return;
            }

            int totalScore = 0;
            for (Indicator ind : active) {
                totalScore += ind.calculateBias(chartContainer.getAllCandles());
            }

            int finalScore = totalScore / active.size();
            String sentiment = getSentimentString(finalScore);

            // Show a custom SnackBar or Alert
            Snackbar.make(v, "Market Bias: " + finalScore + "/100 (" + sentiment + ")",
                    Snackbar.LENGTH_LONG).show();
        }
        else if(id == R.id.drawingPanel) openDrawingPanel();
        else if(id == R.id.btnAlerts) {
            android.content.Intent alertIntent = new android.content.Intent(this, com.example.gutapp.ui.AlertsActivity.class);
            alertIntent.putExtra("symbol", symbol);
            startActivity(alertIntent);
        }
    }

    // ── Chart type / timeframe switchers ───────────────────────────────
    private void switchChartType(StockChart.ChartType type, TextView chip) {
        if (chip == activeCTypeBtn) return;
        setChipActive(activeCTypeBtn, false);
        activeCTypeBtn = chip;
        setChipActive(chip, true);
        chartContainer.setChartType(type);
    }

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
        IndicatorsPanel panel = IndicatorsPanel.newInstance(indicatorSession);
        panel.setListener(this);
        panel.show(getSupportFragmentManager(), "indicators");
    }

    @Override
    public void onIndicatorsChanged() {
        presetRepo.autoSave(symbol, indicatorSession);
        refreshIndicatorChip();
        chartContainer.applyIndicators();
    }

    private void refreshIndicatorChip() {
        TextView indBtn = findViewById(R.id.indicatorsButton);
        if (indBtn == null) return;
        int n = indicatorSession.size();
        if (n > 0) {
            indBtn.setText("⊕ Indicators (" + n + ")");
            indBtn.setTextColor(Color.parseColor("#2196F3"));
        } else {
            indBtn.setText("⊕ Indicators");
            indBtn.setTextColor(Color.parseColor("#78909C"));
        }
    }

    private void openDrawingPanel(){
        DrawingToolbarFragment sheet = DrawingToolbarFragment.newInstance(chartContainer.getDrawingChart());
        sheet.show(getSupportFragmentManager(), "drawing_tools");
    }

    // ── Preset picker ─────────────────────────────────────────────────
    /**
     * Opens a bottom-sheet style dialog that lists all named presets.
     * The user can:
     *  - Tap a preset → apply it to the current session and redraw
     *  - Tap "Save as Preset…" → name + save the current session
     *  - Tap "Clear" → clear the current session
     */
    private void openPresetPicker() {
        List<PresetRepository.Preset> presets = presetRepo.getAllPresets();
        View root = buildPresetPickerView(presets);
        BottomSheetDialog dialog = new BottomSheetDialog(this);
        dialog.setContentView(root);
        dialog.show();
    }

    private View buildPresetPickerView(List<PresetRepository.Preset> presets) {
        // Inflate the XML layout
        View v = LayoutInflater.from(this).inflate(R.layout.view_preset_picker, null);

        // Bind the Manage button
        v.findViewById(R.id.btnManagePresets).setOnClickListener(view -> {
            isManagerReturned = true;
            startActivity(new Intent(this, ProfileActivity.class));
        });

        // Get the containers
        LinearLayout actionRowContainer = v.findViewById(R.id.actionRowContainer);
        presetListContainer = v.findViewById(R.id.presetListContainer);

        // Add Action Rows (using your existing helpers)
        actionRowContainer.addView(makeActionRow(this, "💾", "Save Current as Preset…", "#26A69A",
                view -> promptSaveCurrentPreset()));

        actionRowContainer.addView(makeActionRow(this, "✕", "Clear All Indicators", "#EF5350",
                view -> {
                    indicatorSession.clearAll();
                    presetRepo.autoSave(symbol, indicatorSession);
                    refreshIndicatorChip();
                    chartContainer.applyIndicators();

                    // Dismiss BottomSheet Logic
                    ViewParent p = v.getParent();
                    while (p != null) {
                        if (p instanceof BottomSheetDialog) { ((BottomSheetDialog) p).dismiss(); break; }
                        p = p.getParent();
                    }
                }));

        // Initial fill of the preset list
        refreshPresetListUI();

        return v;
    }

    private void refreshPresetListUI() {
        if (presetListContainer == null) return;
        presetListContainer.removeAllViews();

        List<PresetRepository.Preset> presets = presetRepo.getAllPresets();

        if (presets.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("No saved presets yet.");
            // ... apply your styling ...
            presetListContainer.addView(empty);
        } else {
            presetListContainer.addView(makeSectionHeader(this, "Saved Presets"));
            for (PresetRepository.Preset preset : presets) {
                presetListContainer.addView(buildPickerPresetRow(this, preset));
            }
        }
    }

    private View buildPickerPresetRow(android.content.Context ctx, PresetRepository.Preset preset) {
        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        row.setPadding(dpI(4), dpI(8), dpI(4), dpI(8));

        // Indicator chips summary
        LinearLayout left = new LinearLayout(ctx);
        left.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams leftLp = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        left.setLayoutParams(leftLp);

        TextView nameView = new TextView(ctx);
        nameView.setText(preset.name);
        nameView.setTextColor(Color.parseColor("#ECEFF1"));
        nameView.setTextSize(13f);
        nameView.setTypeface(null, Typeface.BOLD);
        left.addView(nameView);

        if (!preset.snapshots.isEmpty()) {
            // Build a one-line summary: "MA(20) · EMA(9) · RSI(14)"
            StringBuilder sb = new StringBuilder();
            for (Indicator.IndicatorSnapshot snap : preset.snapshots) {
                Indicator proto = IndicatorRegistry.getInstance().getType(snap.typeId);
                if (proto == null) continue;
                if (sb.length() > 0) sb.append(" · ");
                sb.append(proto.getTag());
                if (!snap.params.isEmpty())
                    sb.append("(").append(Math.round(snap.params.get(0).value)).append(")");
            }
            TextView summary = new TextView(ctx);
            summary.setText(sb.toString());
            summary.setTextColor(Color.parseColor("#78909C"));
            summary.setTextSize(11f);
            LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            slp.topMargin = dpI(2);
            summary.setLayoutParams(slp);
            left.addView(summary);
        }
        row.addView(left);

        // "Apply" button
        TextView applyBtn = new TextView(ctx);
        applyBtn.setText("Apply");
        applyBtn.setTextColor(Color.parseColor("#26A69A"));
        applyBtn.setTextSize(12f);
        applyBtn.setTypeface(null, Typeface.BOLD);
        applyBtn.setPadding(dpI(10), dpI(6), dpI(6), dpI(6));
        applyBtn.setOnClickListener(v -> applyPreset(preset));
        row.addView(applyBtn);

        return row;
    }

    /**
     * Applies a preset to the current indicator session and redraws the chart.
     */
    private void applyPreset(PresetRepository.Preset preset) {
        indicatorSession.loadPreset(preset.snapshots);
        presetRepo.autoSave(symbol, indicatorSession);
        refreshIndicatorChip();
        chartContainer.applyIndicators();
        Toast.makeText(this, "Applied preset: " + preset.name, LENGTH_SHORT).show();
    }

    /**
     * Prompts for a name and saves the current session as a new named preset.
     */
    private void promptSaveCurrentPreset() {
        if (indicatorSession.isEmpty()) {
            Toast.makeText(this, "No active indicators to save", LENGTH_SHORT).show();
            return;
        }
        EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setHint("Preset name…");
        input.setTextColor(Color.parseColor("#ECEFF1"));
        input.setHintTextColor(Color.parseColor("#546E7A"));
        input.setBackgroundColor(Color.parseColor("#252323"));
        input.setPadding(dpI(14), dpI(10), dpI(14), dpI(10));

        new AlertDialog.Builder(this)
                .setTitle("Save Preset")
                .setView(input)
                .setPositiveButton("Save", (d, w) -> {
                    String n = input.getText().toString().trim();
                    if (n.isEmpty()) {
                        Toast.makeText(this, "Name cannot be empty", LENGTH_SHORT).show();
                        return;
                    }
                    presetRepo.savePreset(new PresetRepository.Preset(n, indicatorSession.savePreset()));
                    Toast.makeText(this, "Preset \"" + n + "\" saved", LENGTH_SHORT).show();
                    refreshPresetListUI();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // ── View helpers for preset picker ────────────────────────────────

    private View makeActionRow(android.content.Context ctx, String icon, String label,
                               String color, View.OnClickListener listener) {
        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        row.setPadding(dpI(4), dpI(10), dpI(4), dpI(10));
        row.setOnClickListener(listener);

        TextView iconView = new TextView(ctx);
        iconView.setText(icon);
        iconView.setTextSize(14f);
        iconView.setMinWidth(dpI(28));
        iconView.setGravity(android.view.Gravity.CENTER);
        row.addView(iconView);

        TextView lbl = new TextView(ctx);
        lbl.setText(label);
        lbl.setTextColor(Color.parseColor(color));
        lbl.setTextSize(13f);
        lbl.setPadding(dpI(8), 0, 0, 0);
        row.addView(lbl);
        return row;
    }

    private View makeSectionHeader(android.content.Context ctx, String text) {
        TextView tv = new TextView(ctx);
        tv.setText(text.toUpperCase(Locale.US));
        tv.setTextColor(Color.parseColor("#546E7A"));
        tv.setTextSize(10f);
        tv.setLetterSpacing(0.12f);
        tv.setPadding(dpI(4), dpI(12), 0, dpI(4));
        return tv;
    }

    private View makeDivider(android.content.Context ctx) {
        View v = new View(ctx);
        v.setBackgroundColor(Color.parseColor("#2A2828"));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 1);
        v.setLayoutParams(p);
        return v;
    }

    private View makeSpacing(android.content.Context ctx, int dp) {
        View v = new View(ctx);
        v.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dpI(dp)));
        return v;
    }

    private int dpI(int val) {
        return Math.round(val * getResources().getDisplayMetrics().density);
    }

    // ── Title ──────────────────────────────────────────────────────────
    public void formatTitle(String tf) {
        textViewTitle.setText(symbol + " (" + tf + ")");
    }

    // ── Chart data loading ─────────────────────────────────────────────
    private void updateChartData() {
        chartContainer.setInterval(interval);
        chartContainer.flushRequests();
        chartContainer.clearChart();

        StockDataHelper sdh = new StockDataHelper(db_helper);
        LastFetchCacheHelper ch = new LastFetchCacheHelper(db_helper);
        long last = ch.getLastFetchTime(symbol, interval);

        NetworkClient.getInstance(this).getSessionManager()
                .pushRequest(getRequest(symbol, interval, last, 0, true, false, chartContainer));
        NetworkClient.getInstance(this).getSessionManager()
                .pushRequest(getRequest(symbol, interval, 0, 0, false, true, chartContainer));

        try {
            ArrayList<Candle> data = sdh.getCachedStockData(symbol, interval);
            if (data != null && !data.isEmpty()) {
                chartContainer.addChunk(data);
                double price = data.get(data.size() - 1).close;
                textViewPrice.setText(String.format(Locale.US, "%.6f", price));
                current_price = price;
            } else throw new Exception("empty");
        } catch (Exception e) {
            Log.e(DB_Helper.DB_LOG_TAG, "Cache miss: " + e.getMessage());
            if (last != 0)
                NetworkClient.getInstance(this).getSessionManager()
                        .pushRequest(getRequest(symbol, interval, 0, last, true, false, chartContainer));
        }
    }

    private RequestTickerData getRequest(String sym, StockDataHelper.Timeframe tf,
                                         long s, long e, boolean snap, boolean stream,
                                         SessionCallback cb) {
        RequestTickerData r = new RequestTickerData(sym, tf, s, e, snap, stream, cb);
        chartContainer.addToCurrentRequest(r.getReqId());
        return r;
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
                if (chartContainer.isDone()) runOnUiThread(() -> {
                    updatePriceDisplay((Double) parsedData);
                    if (activeDialog != null && activeDialog.isShowing())
                        activeDialog.updateLivePrice((Double) parsedData);
                });
                break;
            case ORDER_INVALID:
            case ORDER_SLIP:
                runOnUiThread(() -> Toast.makeText(this, (String) parsedData, LENGTH_LONG).show());
                break;
            case ORDER_RECEIVED:
                Order o = (Order) parsedData;
                runOnUiThread(() -> {
                    Toast.makeText(this, String.format(
                            "Order completed for %s, paid $%.16f per unit",
                            symbol, o.getEntry_price()), LENGTH_LONG).show();
                    synchronized (allOrders) { allOrders.add(o); }
                    updateOrdersUI();
                });
                break;
            case ORDERS_BATCH:
                runOnUiThread(() -> {
                    synchronized (allOrders) {
                        allOrders.addAll((ArrayList<Order>) parsedData);
                    }
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
        View c = findViewById(R.id.ordersFragmentContainer);
        View e = findViewById(R.id.emptyOrdersView);
        if (allOrders.isEmpty()) {
            c.setVisibility(View.GONE);
            e.setVisibility(View.VISIBLE);
        } else {
            e.setVisibility(View.GONE);
            c.setVisibility(View.VISIBLE);
            ordersFragment = OrdersList.newInstance(allOrders);
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.ordersFragmentContainer, ordersFragment)
                    .commit();
            ordersFragment.setListener(this);
        }
    }

    @Override
    public void onConfirmOrder(int qty, double price, Order.OrderType type) {
        NetworkClient.getInstance(null).getSessionManager()
                .pushRequest(new SendOrder(symbol, qty, price, type, this, this));
        Toast.makeText(this, String.format(
                "Sent order for %s, price: $%.16f", symbol, qty * price), LENGTH_SHORT).show();
    }

    @Override public void PLUpdate(double pl) {}
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

    // ── AI Analysis ───────────────────────────────────────────────────
    private String cachedAiResponse = null;

    private void performAiAnalysis() {
        if (cachedAiResponse != null) { showAiPopup(cachedAiResponse); return; }
        com.google.firebase.FirebaseApp.initializeApp(this);
        new GeminiHelper().getAiAnalysis(name, this);
    }

    private void showAiPopup(String rawJson) {
        runOnUiThread(() -> {
            View v = getLayoutInflater().inflate(R.layout.dialog_ai_analysis, null);
            BottomSheetDialog d = new BottomSheetDialog(this);
            TextView tvR   = v.findViewById(R.id.tvRating);
            TextView tvS   = v.findViewById(R.id.tvScore);
            TextView tvSen = v.findViewById(R.id.tvSentiment);
            TextView tvH   = v.findViewById(R.id.tvHistory);
            ImageButton bc = v.findViewById(R.id.btnClosePopup);
            bc.setOnClickListener(x -> d.dismiss());
            try {
                findViewById(R.id.btnAiAnalyze).setEnabled(true);
                ((ProgressBar) findViewById(R.id.pbAiLoading)).setVisibility(View.GONE);
                JSONObject j = new JSONObject(rawJson);
                String rating = j.optString("rating_word", "Neutral");
                tvR.setText(rating);
                tvS.setText(j.optInt("score_out_of_hundred", 0) + "/100");
                tvSen.setText(j.optString("sentiment_analysis", ""));
                tvH.setText(j.optString("company_history", ""));
                if      (rating.equalsIgnoreCase("Bullish")) tvR.setTextColor(Color.parseColor("#4CAF50"));
                else if (rating.equalsIgnoreCase("Bearish")) tvR.setTextColor(Color.parseColor("#F44336"));
                else                                          tvR.setTextColor(Color.WHITE);
            } catch (JSONException ex) {
                Log.e(CHART_LOG_TAG, "AI parse", ex);
                tvSen.setText("Error parsing analysis.");
            }
            d.setContentView(v);
            d.show();
        });
    }

    @Override public void onSuccess(String r) { cachedAiResponse = r; showAiPopup(r); }
    @Override public void onError(String e) {
        runOnUiThread(() -> {
            findViewById(R.id.btnAiAnalyze).setEnabled(true);
            ((ProgressBar) findViewById(R.id.pbAiLoading)).setVisibility(View.GONE);
            Toast.makeText(this, "AI analysis failed", LENGTH_SHORT).show();
        });
    }

    /**
     * Activate a drawing tool. Call with null to return to normal pan/zoom.
     * Example:
     *   setDrawingTool(DrawingManager.DrawingTool.HORIZONTAL_LINE);
     *   setDrawingTool(null); // deactivate
     */
    private void setDrawingTool(@Nullable DrawingManager.DrawingTool tool) {
        chartContainer.getDrawingChart().getDrawingManager().setActiveTool(tool);
        // Visual feedback: update active tool button state in your toolbar
    }

    private void clearAllUserDrawings() {
        DrawingChart dc = chartContainer.getDrawingChart();
        if (dc != null) dc.clearAllUserDrawings();
    }

    /** Show/hide the drawing mode HUD and update the tool name label. */
    private void updateDrawingHud(@androidx.annotation.Nullable DrawingManager.DrawingTool tool) {
        if (drawingModeHud == null || hudToolName == null) return;
        if (tool == null) {
            drawingModeHud.setVisibility(android.view.View.GONE);
        } else {
            hudToolName.setText(toolDisplayName(tool));
            drawingModeHud.setVisibility(android.view.View.VISIBLE);
        }
    }

    private String toolDisplayName(DrawingManager.DrawingTool tool) {
        switch (tool) {
            case HORIZONTAL_LINE:    return "Horizontal Line";
            case TREND_LINE:         return "Trend Line";
            case RAY_LINE:           return "Ray Line";
            case EXTENDED_LINE:      return "Extended Line";
            case VERTICAL_LINE:      return "Vertical Line";
            case LINEAR_REGRESSION:  return "Lin Regression";
            case FIB_RETRACEMENT:    return "Fibonacci";
            case PRICE_RANGE:        return "Price Range";
            case RECTANGLE:          return "Rectangle";
            case ELLIPSE:            return "Ellipse";
            case TEXT_ANNOTATION:    return "Text";
            case ARROW:              return "Arrow";
            case PARALLEL_CHANNEL:   return "Channel";
            case PITCHFORK:          return "Pitchfork";
            case GANN_FAN:           return "Gann Fan";
            default:                 return tool.name();
        }
    }
}