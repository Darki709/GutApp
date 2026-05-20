package com.example.gutapp.ui;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;

import com.example.gutapp.data.alerts.Alert;
import com.example.gutapp.data.alerts.AlertManager;
import com.example.gutapp.database.DB_Helper;
import com.example.gutapp.session.DataType;
import com.example.gutapp.session.NetworkClient;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * AlertsActivity — displays all alerts the user has created.
 *
 * Features:
 *  - Groups alerts by symbol with section headers
 *  - Shows condition summary, status badge, repeat mode, priority
 *  - Toggle ACTIVE ↔ INACTIVE without deleting
 *  - Swipe-to-delete (simulated via a "✕" button)
 *  - FAB to create a new alert from scratch (launches AddAlertBottomSheet)
 *  - Tapping an alert row re-opens AddAlertBottomSheet in edit mode
 *
 * This activity is opened from:
 *  a) ChartActivity toolbar "🔔" button (symbol pre-populated)
 *  b) HomeActivity alerts icon
 *  c) Tapping a triggered system notification
 */
public class AlertsActivity extends SessionActivity {

    // ── Colour palette (matches your app dark theme) ──────────────────
    private static final int BG        = Color.parseColor("#121318");
    private static final int SURFACE   = Color.parseColor("#1E2028");
    private static final int BORDER    = Color.parseColor("#2A2D3A");
    private static final int TEXT_PRI  = Color.parseColor("#EAEAEA");
    private static final int TEXT_SEC  = Color.parseColor("#8A8FA8");
    private static final int ACCENT    = Color.parseColor("#4F8EF7");
    private static final int GREEN     = Color.parseColor("#26A69A");
    private static final int RED       = Color.parseColor("#EF5350");
    private static final int YELLOW    = Color.parseColor("#FFA726");

    private LinearLayout alertsContainer;
    private TextView     emptyView;
    private String       filterSymbol;   // set when launched from ChartActivity

    // ── Lifecycle ─────────────────────────────────────────────────────

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        filterSymbol = getIntent().getStringExtra("symbol"); // may be null

        // ── Root layout ───────────────────────────────────────────────
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);
        setContentView(root);

        // ── Toolbar ───────────────────────────────────────────────────
        root.addView(buildToolbar());

        // ── Scrollable list ───────────────────────────────────────────
        ScrollView scroll = new ScrollView(this);
        scroll.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        scroll.setFillViewport(true);

        alertsContainer = new LinearLayout(this);
        alertsContainer.setOrientation(LinearLayout.VERTICAL);
        alertsContainer.setPadding(dp(16), dp(8), dp(16), dp(80));
        scroll.addView(alertsContainer);
        root.addView(scroll);

        // ── FAB ───────────────────────────────────────────────────────
        root.addView(buildFab());

        // ── Empty state ───────────────────────────────────────────────
        emptyView = new TextView(this);
        emptyView.setText("No alerts yet.\nTap + to create one.");
        emptyView.setTextColor(TEXT_SEC);
        emptyView.setTextSize(15f);
        emptyView.setGravity(Gravity.CENTER);
        emptyView.setPadding(0, dp(60), 0, 0);
        emptyView.setVisibility(View.GONE);
        alertsContainer.addView(emptyView);

        refreshList();
    }

    @Override protected void onResume() { super.onResume(); refreshList(); }

    // ── SessionActivity stubs ─────────────────────────────────────────
    @Override protected void refreshNetwork() { /* no network data needed in this screen */ }
    @Override public void onDataReceived(DataType t, Object d) {}
    @Override public void onActionRequired(int a, @Nullable Object d) {}

    // ── Build UI ──────────────────────────────────────────────────────

    private View buildToolbar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setBackgroundColor(SURFACE);
        bar.setPadding(dp(16), dp(12), dp(16), dp(12));
        bar.setGravity(Gravity.CENTER_VERTICAL);

        ImageButton back = new ImageButton(this);
        back.setImageResource(android.R.drawable.ic_menu_revert);
        back.setBackgroundColor(Color.TRANSPARENT);
        back.setColorFilter(TEXT_PRI);
        back.setOnClickListener(v -> finish());
        bar.addView(back, new LinearLayout.LayoutParams(dp(36), dp(36)));

        TextView title = new TextView(this);
        title.setText(filterSymbol != null
                ? "🔔  Alerts — " + filterSymbol
                : "🔔  All Alerts");
        title.setTextColor(TEXT_PRI);
        title.setTextSize(18f);
        title.setTypeface(null, Typeface.BOLD);
        title.setPadding(dp(12), 0, 0, 0);
        bar.addView(title, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        return bar;
    }

    private View buildFab() {
        TextView fab = new TextView(this);
        fab.setText("＋  New Alert");
        fab.setTextColor(Color.WHITE);
        fab.setTextSize(15f);
        fab.setTypeface(null, Typeface.BOLD);
        fab.setBackgroundColor(ACCENT);
        fab.setGravity(Gravity.CENTER);
        fab.setPadding(dp(24), dp(14), dp(24), dp(14));

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(dp(16), dp(8), dp(16), dp(16));
        fab.setLayoutParams(lp);

        fab.setOnClickListener(v ->
                AddAlertBottomSheet.show(this, filterSymbol, null, this::refreshList));
        return fab;
    }

    // ── Data ──────────────────────────────────────────────────────────

    private void refreshList() {
        AlertManager am = AlertManager.getInstance();
        List<Alert> alerts = filterSymbol != null
                ? am.getAlertsForSymbol(filterSymbol)
                : am.getAllAlerts();

        alertsContainer.removeAllViews();
        alertsContainer.addView(emptyView);

        if (alerts.isEmpty()) {
            emptyView.setVisibility(View.VISIBLE);
            return;
        }
        emptyView.setVisibility(View.GONE);

        // Group by symbol
        String lastSymbol = null;
        for (Alert a : alerts) {
            if (!a.getSymbol().equals(lastSymbol)) {
                lastSymbol = a.getSymbol();
                if (filterSymbol == null) {  // only show headers in global view
                    alertsContainer.addView(buildSectionHeader(a.getSymbol()));
                }
            }
            alertsContainer.addView(buildAlertRow(a));
        }
    }

    private View buildSectionHeader(String symbol) {
        TextView tv = new TextView(this);
        tv.setText(symbol);
        tv.setTextColor(ACCENT);
        tv.setTextSize(13f);
        tv.setTypeface(null, Typeface.BOLD);
        tv.setAllCaps(true);
        tv.setLetterSpacing(0.1f);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, dp(16), 0, dp(4));
        tv.setLayoutParams(lp);
        return tv;
    }

    private View buildAlertRow(Alert alert) {
        // Card container
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundColor(SURFACE);
        card.setPadding(dp(14), dp(12), dp(14), dp(12));
        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cardLp.setMargins(0, 0, 0, dp(8));
        card.setLayoutParams(cardLp);
        card.setOnClickListener(v ->
                AddAlertBottomSheet.show(this, alert.getSymbol(), alert, this::refreshList));

        // ── Row 1: label + status badge + delete ──
        LinearLayout row1 = row(Gravity.CENTER_VERTICAL);

        TextView label = new TextView(this);
        label.setText(alert.getLabel());
        label.setTextColor(TEXT_PRI);
        label.setTextSize(15f);
        label.setTypeface(null, Typeface.BOLD);
        label.setMaxLines(1);
        label.setEllipsize(TextUtils.TruncateAt.END);
        row1.addView(label, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        row1.addView(buildStatusBadge(alert));

        TextView del = new TextView(this);
        del.setText("✕");
        del.setTextColor(RED);
        del.setTextSize(16f);
        del.setPadding(dp(12), dp(4), dp(4), dp(4));
        del.setOnClickListener(v -> {
            AlertManager.getInstance().removeAlert(alert.getId());
            refreshList();
            Toast.makeText(this, "Alert deleted", Toast.LENGTH_SHORT).show();
        });
        row1.addView(del);
        card.addView(row1);

        // ── Row 2: condition summary ──────────────────────────────────
        TextView summary = new TextView(this);
        summary.setText(alert.getCondition().getSummary());
        summary.setTextColor(TEXT_SEC);
        summary.setTextSize(13f);
        summary.setPadding(0, dp(4), 0, dp(6));
        card.addView(summary);

        // ── Row 3: metadata chips + toggle ────────────────────────────
        LinearLayout row3 = row(Gravity.CENTER_VERTICAL);

        row3.addView(chip(repeatLabel(alert), BORDER, TEXT_SEC));
        row3.addView(chip(priorityLabel(alert.getPriority()), BORDER, priorityColor(alert.getPriority())));

        if (alert.getExpiresAt() > 0) {
            row3.addView(chip("⏳ " + formatDate(alert.getExpiresAt()), BORDER, TEXT_SEC));
        }

        if (alert.getLastTriggeredAt() > 0) {
            row3.addView(chip("Last: " + formatDate(alert.getLastTriggeredAt()), BORDER, TEXT_SEC));
        }

        // Spacer
        View spacer = new View(this);
        row3.addView(spacer, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        // Toggle switch
        Switch toggle = new Switch(this);
        toggle.setChecked(alert.getStatus() == Alert.Status.ACTIVE);
        toggle.setOnCheckedChangeListener((btn, isChecked) -> {
            Alert.Status target = isChecked ? Alert.Status.ACTIVE : Alert.Status.INACTIVE;
            AlertManager.getInstance().setAlertStatus(alert.getId(), target);
        });
        row3.addView(toggle);
        card.addView(row3);

        return card;
    }

    private View buildStatusBadge(Alert alert) {
        TextView tv = new TextView(this);
        int bg, fg;
        String text;
        switch (alert.getStatus()) {
            case ACTIVE:
                bg = Color.argb(40, 38, 166, 154); fg = GREEN; text = "● ACTIVE"; break;
            case TRIGGERED:
                bg = Color.argb(40, 255, 167, 38); fg = YELLOW; text = "⚡ TRIGGERED"; break;
            default:
                bg = BORDER; fg = TEXT_SEC; text = "◌ PAUSED"; break;
        }
        tv.setText(text);
        tv.setTextColor(fg);
        tv.setTextSize(11f);
        tv.setTypeface(null, Typeface.BOLD);
        tv.setBackgroundColor(bg);
        tv.setPadding(dp(8), dp(3), dp(8), dp(3));
        return tv;
    }

    // ── Utility ───────────────────────────────────────────────────────

    private LinearLayout row(int gravity) {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(gravity);
        r.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return r;
    }

    private View chip(String text, int bg, int textColor) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextColor(textColor);
        tv.setTextSize(11f);
        tv.setBackgroundColor(bg);
        tv.setPadding(dp(8), dp(3), dp(8), dp(3));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, dp(6), 0);
        tv.setLayoutParams(lp);
        return tv;
    }

    private String repeatLabel(Alert a) {
        switch (a.getRepeatMode()) {
            case ONCE:      return "Once";
            case PERSISTENT: return "Persistent";
            case COOLDOWN:  return "Every " + a.getCooldownSeconds() + "s";
            default:        return a.getRepeatMode().name();
        }
    }

    private String priorityLabel(Alert.Priority p) {
        switch (p) {
            case HIGH:   return "🔴 High";
            case MEDIUM: return "🟡 Medium";
            default:     return "⚪ Low";
        }
    }

    private int priorityColor(Alert.Priority p) {
        switch (p) {
            case HIGH:   return RED;
            case MEDIUM: return YELLOW;
            default:     return TEXT_SEC;
        }
    }

    private String formatDate(long epochSeconds) {
        return new SimpleDateFormat("MMM d HH:mm", Locale.US)
                .format(new Date(epochSeconds * 1000L));
    }

    private int dp(int dp) {
        float density = getResources().getDisplayMetrics().density;
        return Math.round(dp * density);
    }
}
