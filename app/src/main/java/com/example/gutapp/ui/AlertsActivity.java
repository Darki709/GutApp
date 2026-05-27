package com.example.gutapp.ui;

import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
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
import com.example.gutapp.session.DataType;
import com.example.gutapp.ui.dialogue.AddAlertBottomSheet;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class AlertsActivity extends SessionActivity {

    private LinearLayout listContainer;
    private View emptyState;
    private TextView countBar;
    private String filterSymbol;

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
        findViewById(R.id.btnNewAlert).setOnClickListener(v ->
                AddAlertBottomSheet.show(this, filterSymbol, null, this::refreshList));

        refreshList();
    }

    @Override protected void onResume() { super.onResume(); refreshList(); }
    @Override protected void networkReconnect() {}

    @Override
    protected void networkDisconnect() {}

    @Override public void onDataReceived(DataType t, Object d) {}

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
        toggle.setChecked(alert.getStatus() == Alert.Status.ACTIVE);
        toggle.setOnCheckedChangeListener((btn, on) ->
                AlertManager.getInstance().setAlertStatus(alert.getId(),
                        on ? Alert.Status.ACTIVE : Alert.Status.INACTIVE));

        // Delete
        row.findViewById(R.id.alertDeleteBtn).setOnClickListener(v -> {
            AlertManager.getInstance().removeAlert(alert.getId());
            refreshList();
            Toast.makeText(this, "Alert deleted", Toast.LENGTH_SHORT).show();
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
