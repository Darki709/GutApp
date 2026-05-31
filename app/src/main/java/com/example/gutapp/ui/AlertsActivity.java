package com.example.gutapp.ui;

import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.Nullable;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.gutapp.R;
import com.example.gutapp.data.alerts.Alert;
import com.example.gutapp.data.alerts.AlertManager;
import com.example.gutapp.data.models.TickerInfo;
import com.example.gutapp.session.DataType;
import com.example.gutapp.session.NetworkClient;
import com.example.gutapp.session.Requests.SearchTicker;
import com.example.gutapp.ui.dialogue.AddAlertBottomSheet;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class AlertsActivity extends SessionActivity {

    private LinearLayout listContainer;
    private View emptyState;
    private TextView countBar;
    private String filterSymbol;

    private AlertDialog tickerPickDialog;
    private LinearLayout searchResultsContainer;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_alerts);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            // 1. Extract the system bar pixel dimensions (status bar + nav bar)
            Insets systemBarsInsets = insets.getInsets(WindowInsetsCompat.Type.systemBars());

            // 2. Apply the left, right, and bottom paddings, and force top padding to push the layout down
            v.setPadding(
                    systemBarsInsets.left,
                    systemBarsInsets.top,   // 🚀 This shifts your "Alerts" title below the clock/wifi icons
                    systemBarsInsets.right,
                    systemBarsInsets.bottom
            );

            return insets;
        });

        filterSymbol  = getIntent().getStringExtra("symbol");
        listContainer = findViewById(R.id.alertsListContainer);
        emptyState    = findViewById(R.id.alertsEmptyState);
        countBar      = findViewById(R.id.alertsCountBar);

        TextView title = findViewById(R.id.alertsTitle);
        if (filterSymbol != null) title.setText("🔔  Alerts — " + filterSymbol);

        findViewById(R.id.btnAlertsBack).setOnClickListener(v -> finish());
        findViewById(R.id.btnNewAlert).setOnClickListener(v -> {
            if (filterSymbol != null)
                AddAlertBottomSheet.show(this, filterSymbol, null, this::refreshList);
            else
                showTickerSearchDialog();
        });

        refreshList();
    }

    @Override protected void onResume() { super.onResume(); refreshList(); }
    @Override protected void networkReconnect() {}

    @Override
    protected void networkDisconnect() {}

    @Override
    public void onDataReceived(DataType t, Object d) {
        if (searchResultsContainer == null) return;
        runOnUiThread(() -> {
            searchResultsContainer.removeAllViews();
            if (t == DataType.SEARCH_RESULT && d != null) {
                for (TickerInfo ticker : (ArrayList<TickerInfo>) d)
                    searchResultsContainer.addView(buildSearchResultRow(ticker));
            } else if (t == DataType.SEARCH_NO_RESULT) {
                TextView empty = new TextView(this);
                empty.setText("No results found");
                empty.setTextColor(Color.GRAY);
                empty.setPadding(dp(8), dp(16), dp(8), dp(8));
                searchResultsContainer.addView(empty);
            }
        });
    }

    // ── Ticker search dialog ──────────────────────────────────────────

    private void showTickerSearchDialog() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(12), dp(16), dp(8));

        EditText searchInput = new EditText(this);
        searchInput.setHint("Search ticker or name…");
        searchInput.setHintTextColor(Color.parseColor("#546E7A"));
        searchInput.setTextColor(Color.WHITE);
        searchInput.setSingleLine(true);
        root.addView(searchInput);

        searchResultsContainer = new LinearLayout(this);
        searchResultsContainer.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams svLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(320));
        ScrollView sv = new ScrollView(this);
        sv.setLayoutParams(svLp);
        sv.addView(searchResultsContainer);
        root.addView(sv);

        tickerPickDialog = new AlertDialog.Builder(this, R.style.GutDialog)
                .setTitle("Select Ticker")
                .setView(root)
                .setNegativeButton("Cancel", (d, w) -> searchResultsContainer = null)
                .setOnDismissListener(d -> searchResultsContainer = null)
                .create();
        tickerPickDialog.show();

        Handler handler = new Handler(Looper.getMainLooper());
        Runnable[] pending = {null};
        searchInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            @Override public void afterTextChanged(Editable s) {}
            @Override public void onTextChanged(CharSequence s, int st, int before, int count) {
                if (pending[0] != null) handler.removeCallbacks(pending[0]);
                pending[0] = () -> {
                    String q = s.toString().trim();
                    if (q.length() >= 2) {
                        if (searchResultsContainer != null) searchResultsContainer.removeAllViews();
                        NetworkClient.getInstance(AlertsActivity.this).getSessionManager()
                                .pushRequest(new SearchTicker(q, AlertsActivity.this));
                    }
                };
                handler.postDelayed(pending[0], 400);
            }
        });
    }

    private View buildSearchResultRow(TickerInfo ticker) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(dp(8), dp(12), dp(8), dp(12));
        row.setClickable(true);
        TypedValue ripple = new TypedValue();
        getTheme().resolveAttribute(android.R.attr.selectableItemBackground, ripple, true);
        row.setBackgroundResource(ripple.resourceId);

        TextView sym = new TextView(this);
        sym.setText(ticker.symbol);
        sym.setTextColor(Color.WHITE);
        sym.setTextSize(15f);
        sym.setTypeface(null, Typeface.BOLD);

        TextView name = new TextView(this);
        name.setText(ticker.name);
        name.setTextColor(Color.GRAY);
        name.setTextSize(12f);

        row.addView(sym);
        row.addView(name);

        row.setOnClickListener(v -> {
            if (tickerPickDialog != null) { tickerPickDialog.dismiss(); tickerPickDialog = null; }
            searchResultsContainer = null;
            AddAlertBottomSheet.show(this, ticker.symbol, null, this::refreshList);
        });

        return row;
    }

    // ── List ──────────────────────────────────────────────────────────

    private void refreshList() {
        AlertManager am = AlertManager.getInstance();
        List<Alert> alerts = filterSymbol != null
                ? am.getAlertsForSymbol(filterSymbol)
                : am.getAllAlerts();

        listContainer.removeAllViews();

        if (alerts.isEmpty()) {
            emptyState.setVisibility(View.VISIBLE);
            countBar.setText("");
            return;
        }
        emptyState.setVisibility(View.GONE);

        long active = 0;
        for (Alert a : alerts) if (a.getStatus() == Alert.Status.ACTIVE) active++;
        countBar.setText(active + " active  ·  " + alerts.size() + " total");

        String lastSym = null;
        for (Alert a : alerts) {
            if (filterSymbol == null && !a.getSymbol().equals(lastSym)) {
                lastSym = a.getSymbol();
                listContainer.addView(buildSectionHeader(lastSym));
            }
            listContainer.addView(buildAlertRow(a));
        }
    }

    private View buildSectionHeader(String symbol) {
        TextView tv = new TextView(this);
        tv.setText(symbol);
        tv.setTextColor(Color.parseColor("#2196F3"));
        tv.setTextSize(11f);
        tv.setTypeface(null, Typeface.BOLD);
        tv.setAllCaps(true);
        tv.setLetterSpacing(0.1f);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, dp(14), 0, dp(4));
        tv.setLayoutParams(lp);
        return tv;
    }

    private View buildAlertRow(Alert alert) {
        View row = LayoutInflater.from(this).inflate(R.layout.item_alert, listContainer, false);

        ((TextView) row.findViewById(R.id.alertLabel)).setText(alert.getLabel());
        ((TextView) row.findViewById(R.id.alertSummary)).setText(alert.getCondition().getSummary());

        // Status badge
        TextView badge = row.findViewById(R.id.alertStatusBadge);
        switch (alert.getStatus()) {
            case ACTIVE:
                badge.setText("● ACTIVE");
                badge.setTextColor(Color.parseColor("#00FF88"));
                badge.setBackgroundColor(Color.argb(40, 0, 255, 136));
                break;
            case TRIGGERED:
                badge.setText("⚡ FIRED");
                badge.setTextColor(Color.parseColor("#FFA726"));
                badge.setBackgroundColor(Color.argb(40, 255, 167, 38));
                break;
            default:
                badge.setText("◌ PAUSED");
                badge.setTextColor(Color.parseColor("#9E9E9E"));
                badge.setBackgroundColor(Color.parseColor("#262525"));
                break;
        }

        // Repeat chip
        TextView repeatChip = row.findViewById(R.id.alertRepeatChip);
        switch (alert.getRepeatMode()) {
            case ONCE:        repeatChip.setText("Once");     break;
            case PERSISTENT:  repeatChip.setText("Repeat");   break;
            case COOLDOWN:    repeatChip.setText(alert.getCooldownSeconds() + "s cd"); break;
        }

        // Priority chip
        TextView priChip = row.findViewById(R.id.alertPriorityChip);
        switch (alert.getPriority()) {
            case HIGH:   priChip.setText("🔴 High");  priChip.setTextColor(Color.parseColor("#FF4444")); break;
            case LOW:    priChip.setText("⚪ Low");   priChip.setTextColor(Color.parseColor("#9E9E9E")); break;
            default:     priChip.setText("🟡 Med");   priChip.setTextColor(Color.parseColor("#FFC107")); break;
        }

        // Last triggered chip
        if (alert.getLastTriggeredAt() > 0) {
            TextView lv = row.findViewById(R.id.alertLastTriggered);
            lv.setVisibility(View.VISIBLE);
            lv.setText("↯ " + fmt(alert.getLastTriggeredAt()));
        }

        // Toggle
        Switch toggle = row.findViewById(R.id.alertToggle);
        toggle.setOnCheckedChangeListener(null);
        toggle.setChecked(alert.getStatus() == Alert.Status.ACTIVE);
        toggle.setOnCheckedChangeListener((btn, on) -> {
            Alert.Status targetStatus = on ? Alert.Status.ACTIVE : Alert.Status.INACTIVE;

            if (alert.getStatus() != targetStatus) {
                AlertManager.getInstance().setAlertStatus(alert.getId(), targetStatus);
                refreshList();
            }

        });

        // Delete
        row.findViewById(R.id.alertDeleteBtn).setOnClickListener(v -> {
            AlertManager.getInstance().removeAlert(alert.getId());
            refreshList();
            //Toast.makeText(this, "Alert deleted", Toast.LENGTH_SHORT).show();
        });

        // Row tap = edit
        row.setOnClickListener(v ->
                AddAlertBottomSheet.show(this, alert.getSymbol(), alert, this::refreshList));

        return row;
    }

    private String fmt(long epochSec) {
        return new SimpleDateFormat("MMM d HH:mm", Locale.US).format(new Date(epochSec * 1000L));
    }

    private int dp(int v) { return Math.round(getResources().getDisplayMetrics().density * v); }
}
