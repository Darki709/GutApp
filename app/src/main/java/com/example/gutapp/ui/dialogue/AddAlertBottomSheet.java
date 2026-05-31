package com.example.gutapp.ui.dialogue;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.text.InputType;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.example.gutapp.R;
import com.example.gutapp.data.alerts.Alert;
import com.example.gutapp.data.alerts.AlertManager;
import com.example.gutapp.data.alerts.Condition;
import com.example.gutapp.database.StockDataHelper;
import com.example.gutapp.ui.AlertsActivity;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * AddAlertBottomSheet — static helper that drives the panel_add_alert.xml
 * overlay embedded in ChartActivity.
 *
 * When called from AlertsActivity (which has no chart panel), it falls back
 * to a full-screen AlertsActivity-hosted BottomSheetDialog.
 *
 * Usage:
 * AddAlertBottomSheet.show(activity, symbol, existingAlertOrNull, onSaveCallback);
 *
 * The panel is already in ChartActivity's layout. show() just populates it
 * and makes it VISIBLE. close() sets it GONE.
 */
public class AddAlertBottomSheet {

    private static final List<String> COND_NAMES = Arrays.asList(
            "Price Threshold", "Price Change %", "SMA Crossover",
            "Volatility", "RSI", "Volume Spike");

    private static final String[] TF_LABELS = {"1m", "5m", "15m", "1h", "1d"};
    private static final StockDataHelper.Timeframe[] TF_VALUES = {
            StockDataHelper.Timeframe.ONE_MIN,
            StockDataHelper.Timeframe.FIVE_MIN,
            StockDataHelper.Timeframe.FIFTEEN_MIN,
            StockDataHelper.Timeframe.HOURLY,
            StockDataHelper.Timeframe.DAILY
    };

    // ── Entry point ───────────────────────────────────────────────────

    /**
     * Show the alert creation / edit panel.
     *
     * If the activity has panel_add_alert embedded (ChartActivity), drives it
     * directly.  Otherwise, opens AlertsActivity as a fallback.
     *
     * @param activity   Calling activity (ChartActivity preferred)
     * @param symbol     Pre-populated symbol (may be null)
     * @param existing   Alert to edit, or null to create new
     * @param onSave     Called after a successful save so callers can refresh
     */
    public static void show(Activity activity, String symbol, Alert existing, Runnable onSave) {
        View panel = activity.findViewById(R.id.addAlertPanel);
        if (panel == null) {
            // Fallback: open AlertsActivity directly
            Intent i = new Intent(activity, AlertsActivity.class);
            if (symbol != null) i.putExtra("symbol", symbol);
            activity.startActivity(i);
            return;
        }
        bind(activity, panel, symbol, existing, onSave);
        panel.setVisibility(View.VISIBLE);
    }

    public static void hide(Activity activity) {
        View panel = activity.findViewById(R.id.addAlertPanel);
        if (panel != null) panel.setVisibility(View.GONE);
    }

    // ── Binding ───────────────────────────────────────────────────────

    private static void bind(Activity activity, View panel,
                             String symbol, Alert existing, Runnable onSave) {
        Context ctx = activity;

        // Title
        TextView title = panel.findViewById(R.id.alertPanelTitle);
        title.setText(existing != null ? "🔔  Edit Alert" : "🔔  New Alert");

        // Close
        panel.findViewById(R.id.alertPanelClose).setOnClickListener(v ->
                panel.setVisibility(View.GONE));

        // View all → open AlertsActivity
        panel.findViewById(R.id.alertPanelViewAll).setOnClickListener(v -> {
            panel.setVisibility(View.GONE);
            Intent i = new Intent(ctx, AlertsActivity.class);
            if (symbol != null) i.putExtra("symbol", symbol);
            activity.startActivity(i);
        });

        if(symbol == null){
        }

        // Label
        EditText etLabel = panel.findViewById(R.id.alertLabelInput);
        etLabel.setText(existing != null ? existing.getLabel() : "");

        // Condition spinner
        Spinner condSpinner = panel.findViewById(R.id.alertConditionSpinner);
        ArrayAdapter<String> condAdapter = new ArrayAdapter<>(ctx,
                android.R.layout.simple_spinner_item, COND_NAMES);
        condAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        condSpinner.setAdapter(condAdapter);

        LinearLayout condParams = panel.findViewById(R.id.alertConditionParams);

        int initialIdx = 0;
        if (existing != null && existing.getCondition() != null) {
            switch (existing.getCondition().getTypeName()) {
                case "PRICE_THRESHOLD":       initialIdx = 0; break;
                case "PRICE_CHANGE_PERCENT":  initialIdx = 1; break;
                case "SMA_CROSSOVER":         initialIdx = 2; break;
                case "VOLATILITY":            initialIdx = 3; break;
                case "RSI":                   initialIdx = 4; break;
                case "VOLUME_SPIKE":          initialIdx = 5; break;
            }
        }

        renderCondParams(ctx, condParams, initialIdx, existing);
        condSpinner.setSelection(initialIdx);

        condSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
                boolean matchesModel = false;
                if (existing != null && existing.getCondition() != null) {
                    String type = existing.getCondition().getTypeName();
                    if ((pos == 0 && "PRICE_THRESHOLD".equals(type)) ||
                            (pos == 1 && "PRICE_CHANGE_PERCENT".equals(type)) ||
                            (pos == 2 && "SMA_CROSSOVER".equals(type)) ||
                            (pos == 3 && "VOLATILITY".equals(type)) ||
                            (pos == 4 && "RSI".equals(type)) ||
                            (pos == 5 && "VOLUME_SPIKE".equals(type))) {
                        matchesModel = true;
                    }
                }
                renderCondParams(ctx, condParams, pos, matchesModel ? existing : null);
            }
            @Override public void onNothingSelected(AdapterView<?> p) {}
        });

        // Repeat
        RadioGroup repeatGroup = panel.findViewById(R.id.alertRepeatGroup);
        EditText etCooldown   = panel.findViewById(R.id.alertCooldownInput);
        if (existing != null) {
            switch (existing.getRepeatMode()) {
                case PERSISTENT:
                    ((RadioButton) panel.findViewById(R.id.rbPersist)).setChecked(true); break;
                case COOLDOWN:
                    ((RadioButton) panel.findViewById(R.id.rbCooldown)).setChecked(true);
                    etCooldown.setVisibility(View.VISIBLE);
                    etCooldown.setText(String.valueOf(existing.getCooldownSeconds())); break;
                default:
                    ((RadioButton) panel.findViewById(R.id.rbOnce)).setChecked(true); break;
            }
        } else {
            ((RadioButton) panel.findViewById(R.id.rbOnce)).setChecked(true);
            etCooldown.setVisibility(View.GONE);
        }
        repeatGroup.setOnCheckedChangeListener((g, id) ->
                etCooldown.setVisibility(id == R.id.rbCooldown ? View.VISIBLE : View.GONE));

        // Priority
        if (existing != null) {
            switch (existing.getPriority()) {
                case HIGH: ((RadioButton) panel.findViewById(R.id.rbPriHigh)).setChecked(true); break;
                case LOW:  ((RadioButton) panel.findViewById(R.id.rbPriLow)).setChecked(true);  break;
                default:   ((RadioButton) panel.findViewById(R.id.rbPriMed)).setChecked(true);  break;
            }
        } else {
            ((RadioButton) panel.findViewById(R.id.rbPriMed)).setChecked(true);
        }

        // Expiry
        EditText etExpiry = panel.findViewById(R.id.alertExpiryInput);
        if (existing != null && existing.getExpiresAt() > 0) {
            long hours = (existing.getExpiresAt() - System.currentTimeMillis() / 1000L) / 3600;
            etExpiry.setText(hours > 0 ? String.valueOf(hours) : "0");
        } else {
            etExpiry.setText("0");
        }

        // Save
        TextView saveBtn = panel.findViewById(R.id.alertPanelSaveBtn);
        saveBtn.setText(existing != null ? "Update Alert" : "Create Alert");
        saveBtn.setOnClickListener(v -> {
            String lbl = etLabel.getText().toString().trim();
            String sym = (symbol != null ? symbol : (existing != null ? existing.getSymbol() : "")).toUpperCase().trim();

            if (sym.isEmpty()) {
                Toast.makeText(ctx, "No symbol available", Toast.LENGTH_SHORT).show();
                return;
            }
            if (lbl.isEmpty()) {
                Toast.makeText(ctx, "Please enter a label", Toast.LENGTH_SHORT).show();
                return;
            }

            Condition condition = buildCondition(ctx, condSpinner.getSelectedItemPosition(), sym, condParams);
            if (condition == null) return;

            // Repeat
            Alert.RepeatMode mode;
            int cooldownSecs = 0;
            int repeatId = repeatGroup.getCheckedRadioButtonId();
            if (repeatId == R.id.rbPersist) {
                mode = Alert.RepeatMode.PERSISTENT;
            } else if (repeatId == R.id.rbCooldown) {
                mode = Alert.RepeatMode.COOLDOWN;
                try { cooldownSecs = Integer.parseInt(etCooldown.getText().toString()); }
                catch (NumberFormatException e) { cooldownSecs = 300; }
            } else {
                mode = Alert.RepeatMode.ONCE;
            }

            // Priority
            Alert.Priority priority;
            int priId = ((RadioGroup) panel.findViewById(R.id.alertPriorityGroup)).getCheckedRadioButtonId();
            if      (priId == R.id.rbPriHigh) priority = Alert.Priority.HIGH;
            else if (priId == R.id.rbPriLow)  priority = Alert.Priority.LOW;
            else                              priority = Alert.Priority.MEDIUM;

            // Expiry
            long expiresAt = 0;
            try {
                int hours = Integer.parseInt(etExpiry.getText().toString().trim());
                if (hours > 0) expiresAt = System.currentTimeMillis() / 1000L + hours * 3600L;
            } catch (NumberFormatException ignored) {}

            if (existing != null) AlertManager.getInstance().removeAlert(existing.getId());

            Alert alert = new Alert(sym, lbl, condition, mode, cooldownSecs, expiresAt, priority);
            AlertManager.getInstance().addAlert(alert);

            panel.setVisibility(View.GONE);
            Toast.makeText(ctx, "Alert saved!", Toast.LENGTH_SHORT).show();
            if (onSave != null) onSave.run();
        });
    }

    // ── Dynamic condition parameter widgets ───────────────────────────

    private static void renderCondParams(Context ctx, LinearLayout container,
                                         int idx, Alert existing) {
        container.removeAllViews();
        switch (idx) {
            case 0: buildPriceThreshold(ctx, container, existing);  break;
            case 1: buildPriceChangePct(ctx, container, existing);  break;
            case 2: buildSma(ctx, container, existing);             break;
            case 3: buildVolatility(ctx, container, existing);      break;
            case 4: buildRsi(ctx, container, existing);             break;
            case 5: buildVolumeSpike(ctx, container, existing);     break;
        }
    }

    private static void buildPriceThreshold(Context ctx, LinearLayout c, Alert ex) {
        c.addView(sectionLabel(ctx, "TARGET PRICE"));
        EditText et = numField(ctx, "e.g. 1.0850"); et.setTag("price"); c.addView(et);

        c.addView(sectionLabel(ctx, "DIRECTION"));
        RadioGroup rg = new RadioGroup(ctx); rg.setOrientation(RadioGroup.HORIZONTAL); rg.setTag("dir");
        RadioButton above = radio(ctx, "Above ↑"); above.setTag("above");
        RadioButton below = radio(ctx, "Below ↓"); below.setTag("below");
        rg.addView(above); rg.addView(below); c.addView(rg);

        above.setChecked(true);

        if (ex != null && ex.getCondition() != null) {
            String summary = ex.getCondition().getSummary();
            try {
                String clean = summary.replaceAll("[^0-9.]", "").trim();
                if (!clean.isEmpty()) et.setText(clean);
                if (summary.contains("<") || summary.contains("≤") || summary.toLowerCase().contains("below")) {
                    below.setChecked(true);
                } else {
                    above.setChecked(true);
                }
            } catch (Exception ignored) {}
        }
    }

    private static void buildPriceChangePct(Context ctx, LinearLayout c, Alert ex) {
        c.addView(sectionLabel(ctx, "% THRESHOLD"));
        EditText et = numField(ctx, "e.g. 2.0"); et.setTag("pct"); c.addView(et);

        c.addView(sectionLabel(ctx, "DIRECTION"));
        Spinner sp = spinner(ctx, Arrays.asList("Either", "Up ↑", "Down ↓")); sp.setTag("dir"); c.addView(sp);

        c.addView(sectionLabel(ctx, "TIMEFRAME"));
        Spinner tf = spinner(ctx, Arrays.asList(TF_LABELS)); tf.setTag("tf"); c.addView(tf);

        if (ex != null && ex.getCondition() != null) {
            String summary = ex.getCondition().getSummary();
            try {
                Pattern pattern = Pattern.compile("([0-9]+\\.?[0-9]*)\\s*%");
                Matcher matcher = pattern.matcher(summary);
                if (matcher.find()) {
                    String clean = matcher.group(1);
                    if (clean != null && !clean.isEmpty()) et.setText(clean);
                } else {
                    String clean = summary.replaceAll("[^0-9.]", "").trim();
                    if (!clean.isEmpty()) et.setText(clean);
                }

                if (summary.toLowerCase().contains("up")) sp.setSelection(1);
                else if (summary.toLowerCase().contains("down")) sp.setSelection(2);

                for (int i = 0; i < TF_LABELS.length; i++) {
                    if (summary.contains(TF_LABELS[i])) {
                        tf.setSelection(i);
                        break;
                    }
                }
            } catch (Exception ignored) {}
        }
    }

    private static void buildSma(Context ctx, LinearLayout c, Alert ex) {
        c.addView(sectionLabel(ctx, "PERIOD"));
        EditText et = numField(ctx, "e.g. 20"); et.setTag("period"); c.addView(et);

        c.addView(sectionLabel(ctx, "CROSS"));
        RadioGroup rg = new RadioGroup(ctx); rg.setOrientation(RadioGroup.HORIZONTAL); rg.setTag("cross");
        RadioButton ab = radio(ctx, "Cross Above ↑"); ab.setTag("above");
        RadioButton bl = radio(ctx, "Cross Below ↓"); bl.setTag("below");
        rg.addView(ab); rg.addView(bl); c.addView(rg);

        ab.setChecked(true);

        c.addView(sectionLabel(ctx, "TIMEFRAME"));
        Spinner tf = spinner(ctx, Arrays.asList(TF_LABELS)); tf.setTag("tf"); c.addView(tf);

        if (ex != null && ex.getCondition() != null) {
            String summary = ex.getCondition().getSummary();
            try {
                String clean = summary.replaceAll("[^0-9.]", "").trim();
                if (!clean.isEmpty()) et.setText(clean);
                if (summary.toLowerCase().contains("below")) {
                    bl.setChecked(true);
                } else {
                    ab.setChecked(true);
                }

                for (int i = 0; i < TF_LABELS.length; i++) {
                    if (summary.contains(TF_LABELS[i])) {
                        tf.setSelection(i);
                        break;
                    }
                }
            } catch (Exception ignored) {}
        }
    }

    private static void buildVolatility(Context ctx, LinearLayout c, Alert ex) {
        c.addView(sectionLabel(ctx, "% MOVE THRESHOLD"));
        EditText et = numField(ctx, "e.g. 2.0"); et.setTag("pct"); c.addView(et);

        c.addView(sectionLabel(ctx, "TIMEFRAME"));
        Spinner tf = spinner(ctx, Arrays.asList(TF_LABELS)); tf.setTag("tf"); c.addView(tf);

        if (ex != null && ex.getCondition() != null) {
            String summary = ex.getCondition().getSummary();
            try {
                String clean = summary.replaceAll("[^0-9.]", "").trim();
                if (!clean.isEmpty()) et.setText(clean);

                for (int i = 0; i < TF_LABELS.length; i++) {
                    if (summary.contains(TF_LABELS[i])) {
                        tf.setSelection(i);
                        break;
                    }
                }
            } catch (Exception ignored) {}
        }
    }

    private static void buildRsi(Context ctx, LinearLayout c, Alert ex) {
        c.addView(sectionLabel(ctx, "PERIOD"));
        EditText etP = numField(ctx, "e.g. 14"); etP.setTag("period"); c.addView(etP);

        c.addView(sectionLabel(ctx, "LEVEL"));
        EditText etL = numField(ctx, "e.g. 30 (oversold) or 70"); etL.setTag("level"); c.addView(etL);

        c.addView(sectionLabel(ctx, "TRIGGER WHEN RSI"));
        RadioGroup rg = new RadioGroup(ctx); rg.setOrientation(RadioGroup.HORIZONTAL); rg.setTag("cross");
        RadioButton bl = radio(ctx, "≤ Level (oversold)"); bl.setTag("below");
        RadioButton ab = radio(ctx, "≥ Level (overbought)"); ab.setTag("above");
        rg.addView(bl); rg.addView(ab); c.addView(rg);

        bl.setChecked(true);

        c.addView(sectionLabel(ctx, "TIMEFRAME"));
        Spinner tf = spinner(ctx, Arrays.asList(TF_LABELS)); tf.setTag("tf"); c.addView(tf);

        if (ex != null && ex.getCondition() != null) {
            String summary = ex.getCondition().getSummary();
            try {
                String[] parts = summary.replaceAll("[^0-9. ]", "").trim().split("\\s+");
                if (parts.length > 0 && !parts[0].isEmpty()) etP.setText(parts[0]);
                if (parts.length > 1 && !parts[1].isEmpty()) etL.setText(parts[1]);
                if (summary.contains(">") || summary.contains("≥") || summary.toLowerCase().contains("above")) {
                    ab.setChecked(true);
                } else {
                    bl.setChecked(true);
                }

                for (int i = 0; i < TF_LABELS.length; i++) {
                    if (summary.contains(TF_LABELS[i])) {
                        tf.setSelection(i);
                        break;
                    }
                }
            } catch (Exception ignored) {}
        }
    }

    private static void buildVolumeSpike(Context ctx, LinearLayout c, Alert ex) {
        c.addView(sectionLabel(ctx, "LOOKBACK CANDLES"));
        EditText etL = numField(ctx, "e.g. 20"); etL.setTag("lookback"); c.addView(etL);

        c.addView(sectionLabel(ctx, "SPIKE MULTIPLIER"));
        EditText etM = numField(ctx, "e.g. 3.0"); etM.setTag("mult"); c.addView(etM);

        c.addView(sectionLabel(ctx, "TIMEFRAME"));
        Spinner tf = spinner(ctx, Arrays.asList(TF_LABELS)); tf.setTag("tf"); c.addView(tf);

        if (ex != null && ex.getCondition() != null) {
            String summary = ex.getCondition().getSummary();
            try {
                String[] parts = summary.replaceAll("[^0-9. ]", "").trim().split("\\s+");
                if (parts.length > 1 && !parts[1].isEmpty()) etL.setText(parts[1]);
                if (parts.length > 0 && !parts[0].isEmpty()) etM.setText(parts[0]);

                for (int i = 0; i < TF_LABELS.length; i++) {
                    if (summary.contains(TF_LABELS[i])) {
                        tf.setSelection(i);
                        break;
                    }
                }
            } catch (Exception ignored) {}
        }
    }

    // ── Condition factory ─────────────────────────────────────────────

    private static Condition buildCondition(Context ctx, int idx, String symbol, LinearLayout params) {
        try {
            switch (idx) {
                case 0: {
                    double price = Double.parseDouble(((EditText) params.findViewWithTag("price")).getText().toString().trim());
                    RadioGroup rg = params.findViewWithTag("dir");
                    RadioButton above = rg.findViewWithTag("above");
                    RadioButton below = rg.findViewWithTag("below");
                    boolean checked = above.isChecked();
                    if (!checked && !below.isChecked()) {
                        checked = true;
                    }
                    return new com.example.gutapp.data.alerts.conditions.PriceThresholdCondition(symbol, price, checked);
                }
                case 1: {
                    double pct = Double.parseDouble(((EditText) params.findViewWithTag("pct")).getText().toString().trim());
                    int dirIdx = ((Spinner) params.findViewWithTag("dir")).getSelectedItemPosition();
                    com.example.gutapp.data.alerts.conditions.PriceChangePercentCondition.Direction dir = dirIdx == 1
                            ? com.example.gutapp.data.alerts.conditions.PriceChangePercentCondition.Direction.UP
                            : dirIdx == 2 ? com.example.gutapp.data.alerts.conditions.PriceChangePercentCondition.Direction.DOWN
                            : com.example.gutapp.data.alerts.conditions.PriceChangePercentCondition.Direction.EITHER;
                    int tfIdx = ((Spinner) params.findViewWithTag("tf")).getSelectedItemPosition();
                    return new com.example.gutapp.data.alerts.conditions.PriceChangePercentCondition(symbol, pct, dir, TF_VALUES[tfIdx]);
                }
                case 2: {
                    int period = Integer.parseInt(((EditText) params.findViewWithTag("period")).getText().toString().trim());
                    RadioGroup rg = params.findViewWithTag("cross");
                    RadioButton above = rg.findViewWithTag("above");
                    RadioButton below = rg.findViewWithTag("below");
                    boolean checked = above.isChecked();
                    if (!checked && !below.isChecked()) {
                        checked = true;
                    }
                    int tfIdx = ((Spinner) params.findViewWithTag("tf")).getSelectedItemPosition();
                    return new com.example.gutapp.data.alerts.conditions.SMACrossoverCondition(symbol, TF_VALUES[tfIdx], period,
                            checked ? com.example.gutapp.data.alerts.conditions.SMACrossoverCondition.CrossDirection.ABOVE : com.example.gutapp.data.alerts.conditions.SMACrossoverCondition.CrossDirection.BELOW);
                }
                case 3: {
                    double pct = Double.parseDouble(((EditText) params.findViewWithTag("pct")).getText().toString().trim());
                    int tfIdx = ((Spinner) params.findViewWithTag("tf")).getSelectedItemPosition();
                    return new com.example.gutapp.data.alerts.conditions.VolatilityCondition(symbol, pct, TF_VALUES[tfIdx]);
                }
                case 4: {
                    int period = Integer.parseInt(((EditText) params.findViewWithTag("period")).getText().toString().trim());
                    double level = Double.parseDouble(((EditText) params.findViewWithTag("level")).getText().toString().trim());
                    RadioGroup rg = params.findViewWithTag("cross");
                    RadioButton above = rg.findViewWithTag("above");
                    RadioButton below = rg.findViewWithTag("below");
                    boolean checked = above.isChecked();
                    if (!checked && !below.isChecked()) {
                        checked = false;
                    }
                    int tfIdx = ((Spinner) params.findViewWithTag("tf")).getSelectedItemPosition();
                    return new com.example.gutapp.data.alerts.conditions.RSICondition(symbol, TF_VALUES[tfIdx], period, level,
                            !checked ? com.example.gutapp.data.alerts.conditions.RSICondition.CrossDirection.BELOW : com.example.gutapp.data.alerts.conditions.RSICondition.CrossDirection.ABOVE);
                }
                case 5: {
                    int lookback = Integer.parseInt(((EditText) params.findViewWithTag("lookback")).getText().toString().trim());
                    double mult  = Double.parseDouble(((EditText) params.findViewWithTag("mult")).getText().toString().trim());
                    int tfIdx    = ((Spinner) params.findViewWithTag("tf")).getSelectedItemPosition();
                    return new com.example.gutapp.data.alerts.conditions.VolumeSpikeCondition(symbol, TF_VALUES[tfIdx], lookback, mult);
                }
            }
        } catch (Exception e) {
            Toast.makeText(ctx, "Please fill in all fields correctly", Toast.LENGTH_SHORT).show();
        }
        return null;
    }

    // ── Widget helpers ────────────────────────────────────────────────

    private static TextView sectionLabel(Context ctx, String text) {
        TextView tv = new TextView(ctx);
        tv.setText(text);
        tv.setTextColor(0xFF546E7A);
        tv.setTextSize(10f);
        tv.setLetterSpacing(0.12f);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(ctx, 10);
        lp.bottomMargin = dp(ctx, 3);
        tv.setLayoutParams(lp);
        return tv;
    }

    private static EditText numField(Context ctx, String hint) {
        EditText et = new EditText(ctx);
        et.setHint(hint);
        et.setHintTextColor(0xFF546E7A);
        et.setTextColor(0xFFECEFF1);
        et.setTextSize(13f);
        et.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        et.setBackground(ctx.getDrawable(R.drawable.chart_btn_inactive));
        et.setPadding(dp(ctx, 10), dp(ctx, 8), dp(ctx, 10), dp(ctx, 8));
        et.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return et;
    }

    private static RadioButton radio(Context ctx, String text) {
        RadioButton rb = new RadioButton(ctx);
        rb.setId(View.generateViewId());
        rb.setText(text);
        rb.setTextColor(0xFFECEFF1);
        rb.setTextSize(12f);
        RadioGroup.LayoutParams lp = new RadioGroup.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, dp(ctx, 12), 0);
        rb.setLayoutParams(lp);
        return rb;
    }

    private static Spinner spinner(Context ctx, List<String> items) {
        Spinner sp = new Spinner(ctx);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(ctx,
                android.R.layout.simple_spinner_item, items);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        sp.setAdapter(adapter);
        sp.setBackground(ctx.getDrawable(R.drawable.chart_btn_inactive));
        sp.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return sp;
    }

    private static int dp(Context ctx, int v) {
        return Math.round(ctx.getResources().getDisplayMetrics().density * v);
    }
}