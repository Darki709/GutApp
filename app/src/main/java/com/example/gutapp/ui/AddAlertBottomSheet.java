package com.example.gutapp.ui;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.example.gutapp.data.alerts.Alert;
import com.example.gutapp.data.alerts.AlertManager;
import com.example.gutapp.data.alerts.Condition;
import com.example.gutapp.data.alerts.conditions.PriceChangePercentCondition;
import com.example.gutapp.data.alerts.conditions.PriceThresholdCondition;
import com.example.gutapp.data.alerts.conditions.RSICondition;
import com.example.gutapp.data.alerts.conditions.SMACrossoverCondition;
import com.example.gutapp.data.alerts.conditions.VolatilityCondition;
import com.example.gutapp.data.alerts.conditions.VolumeSpikeCondition;
import com.example.gutapp.database.StockDataHelper;
import com.google.android.material.bottomsheet.BottomSheetDialog;

import java.util.Arrays;
import java.util.List;

/**
 * AddAlertBottomSheet — the "Create / Edit Alert" UI.
 *
 * Works as a static factory: caller passes in an optional existing Alert for
 * edit mode (null = create mode), and a Runnable that is called on success
 * to let the caller refresh its list.
 *
 * Condition types supported:
 *   PRICE_THRESHOLD       — fixed price level above/below
 *   PRICE_CHANGE_PERCENT  — % move in one candle, directional
 *   SMA_CROSSOVER         — golden/death cross on any period + timeframe
 *   VOLATILITY            — absolute % volatility between candles
 *   RSI                   — RSI overbought/oversold on any period + level
 *   VOLUME_SPIKE          — volume N× rolling average
 *
 * Common settings (all condition types):
 *   • Label (user-defined name)
 *   • Repeat mode: Once / Persistent / Cooldown (+ cooldown seconds)
 *   • Priority: Low / Medium / High
 *   • Expiry: optional date-time picker (simplified to hours-from-now)
 */
public class AddAlertBottomSheet {

    // ── Palette ───────────────────────────────────────────────────────
    private static final int BG       = Color.parseColor("#1E2028");
    private static final int SURFACE  = Color.parseColor("#252830");
    private static final int BORDER   = Color.parseColor("#2A2D3A");
    private static final int TEXT_PRI = Color.parseColor("#EAEAEA");
    private static final int TEXT_SEC = Color.parseColor("#8A8FA8");
    private static final int ACCENT   = Color.parseColor("#4F8EF7");
    private static final int RED      = Color.parseColor("#EF5350");
    private static final int GREEN    = Color.parseColor("#26A69A");

    private static final List<String> CONDITION_NAMES = Arrays.asList(
            "Price Threshold",
            "Price Change %",
            "SMA Crossover",
            "Volatility",
            "RSI",
            "Volume Spike"
    );

    private static final List<String> TF_NAMES = Arrays.asList("1m","5m","15m","1h","1d");
    private static final StockDataHelper.Timeframe[] TF_VALUES = {
            StockDataHelper.Timeframe.ONE_MIN,
            StockDataHelper.Timeframe.FIVE_MIN,
            StockDataHelper.Timeframe.FIFTEEN_MIN,
            StockDataHelper.Timeframe.HOURLY,
            StockDataHelper.Timeframe.DAILY
    };

    // ── Entry point ───────────────────────────────────────────────────

    /**
     * @param context      Activity context
     * @param symbol       Pre-populated symbol (may be null for free-form input)
     * @param existing     Alert to edit, or null to create a new one
     * @param onSuccess    Called on the UI thread after save
     */
    public static void show(Context context,
                             String symbol,
                             Alert existing,
                             Runnable onSuccess) {
        BottomSheetDialog dialog = new BottomSheetDialog(context);
        dialog.setContentView(buildContent(context, dialog, symbol, existing, onSuccess));
        dialog.show();
    }

    // ── Build the form ────────────────────────────────────────────────

    private static View buildContent(Context ctx,
                                     BottomSheetDialog dialog,
                                     String preSymbol,
                                     Alert existing,
                                     Runnable onSuccess) {

        LinearLayout root = new LinearLayout(ctx);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);

        // Drag handle
        View handle = new View(ctx);
        handle.setBackgroundColor(BORDER);
        LinearLayout.LayoutParams handleLp = new LinearLayout.LayoutParams(dp(ctx, 40), dp(ctx, 4));
        handleLp.gravity = Gravity.CENTER_HORIZONTAL;
        handleLp.topMargin = dp(ctx, 10);
        handleLp.bottomMargin = dp(ctx, 14);
        handle.setLayoutParams(handleLp);
        root.addView(handle);

        // Title
        TextView title = new TextView(ctx);
        title.setText(existing != null ? "Edit Alert" : "New Alert");
        title.setTextColor(TEXT_PRI);
        title.setTextSize(18f);
        title.setTypeface(null, Typeface.BOLD);
        title.setPadding(dp(ctx,20), 0, dp(ctx,20), dp(ctx,16));
        root.addView(title);

        // Scrollable form
        ScrollView scroll = new ScrollView(ctx);
        LinearLayout form = new LinearLayout(ctx);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(ctx,20), 0, dp(ctx,20), dp(ctx,16));
        scroll.addView(form);
        root.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // ── Symbol ────────────────────────────────────────────────────
        form.addView(sectionLabel(ctx, "SYMBOL"));
        EditText etSymbol = inputField(ctx, "e.g. EURUSD", InputType.TYPE_CLASS_TEXT);
        etSymbol.setText(preSymbol != null ? preSymbol.toUpperCase() : "");
        if (existing != null) {
            etSymbol.setText(existing.getSymbol());
            etSymbol.setEnabled(false);  // can't change symbol on existing alert
        }
        form.addView(etSymbol);

        // ── Label ─────────────────────────────────────────────────────
        form.addView(sectionLabel(ctx, "LABEL"));
        EditText etLabel = inputField(ctx, "My alert name", InputType.TYPE_CLASS_TEXT);
        if (existing != null) etLabel.setText(existing.getLabel());
        form.addView(etLabel);

        // ── Condition type picker ─────────────────────────────────────
        form.addView(sectionLabel(ctx, "CONDITION TYPE"));
        Spinner condSpinner = styledSpinner(ctx, CONDITION_NAMES);
        form.addView(condSpinner);

        // ── Dynamic condition params (replaced when type changes) ──────
        LinearLayout condParams = new LinearLayout(ctx);
        condParams.setOrientation(LinearLayout.VERTICAL);
        form.addView(condParams);

        // ── Common settings ───────────────────────────────────────────
        form.addView(sectionLabel(ctx, "REPEAT MODE"));
        RadioGroup repeatGroup = new RadioGroup(ctx);
        repeatGroup.setOrientation(RadioGroup.HORIZONTAL);
        RadioButton rbOnce = radio(ctx, "Once");
        RadioButton rbPersist = radio(ctx, "Persistent");
        RadioButton rbCooldown = radio(ctx, "Cooldown");
        repeatGroup.addView(rbOnce); repeatGroup.addView(rbPersist); repeatGroup.addView(rbCooldown);
        rbOnce.setChecked(true);
        form.addView(repeatGroup);

        EditText etCooldown = inputField(ctx, "Cooldown seconds (e.g. 300)",
                InputType.TYPE_CLASS_NUMBER);
        etCooldown.setVisibility(View.GONE);
        form.addView(etCooldown);
        repeatGroup.setOnCheckedChangeListener((g, id) ->
                etCooldown.setVisibility(id == rbCooldown.getId() ? View.VISIBLE : View.GONE));

        form.addView(sectionLabel(ctx, "PRIORITY"));
        Spinner prioritySpinner = styledSpinner(ctx, Arrays.asList("Medium", "High", "Low"));
        form.addView(prioritySpinner);

        form.addView(sectionLabel(ctx, "EXPIRES IN (hours, 0 = never)"));
        EditText etExpiry = inputField(ctx, "0", InputType.TYPE_CLASS_NUMBER);
        form.addView(etExpiry);

        // ── Pre-populate condition type if editing ────────────────────
        int initialCondIdx = 0;
        if (existing != null) {
            String t = existing.getCondition().getTypeName();
            switch (t) {
                case PriceThresholdCondition.TYPE:      initialCondIdx = 0; break;
                case PriceChangePercentCondition.TYPE:  initialCondIdx = 1; break;
                case SMACrossoverCondition.TYPE:        initialCondIdx = 2; break;
                case VolatilityCondition.TYPE:          initialCondIdx = 3; break;
                case RSICondition.TYPE:                 initialCondIdx = 4; break;
                case VolumeSpikeCondition.TYPE:         initialCondIdx = 5; break;
            }
            condSpinner.setSelection(initialCondIdx);

            // Repeat mode
            switch (existing.getRepeatMode()) {
                case PERSISTENT: rbPersist.setChecked(true); break;
                case COOLDOWN:
                    rbCooldown.setChecked(true);
                    etCooldown.setVisibility(View.VISIBLE);
                    etCooldown.setText(String.valueOf(existing.getCooldownSeconds()));
                    break;
                default: rbOnce.setChecked(true); break;
            }

            // Priority
            int pIdx = existing.getPriority() == Alert.Priority.HIGH ? 1
                     : existing.getPriority() == Alert.Priority.LOW  ? 2 : 0;
            prioritySpinner.setSelection(pIdx);

            // Expiry
            long nowSec = System.currentTimeMillis() / 1000L;
            if (existing.getExpiresAt() > nowSec) {
                long hoursLeft = (existing.getExpiresAt() - nowSec) / 3600;
                etExpiry.setText(String.valueOf(hoursLeft));
            }
        }

        // ── Render condition params immediately ───────────────────────
        renderCondParams(ctx, condParams, condSpinner.getSelectedItemPosition(), existing);
        condSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
                renderCondParams(ctx, condParams, pos, null);
            }
            @Override public void onNothingSelected(AdapterView<?> p) {}
        });

        // ── Save button ───────────────────────────────────────────────
        TextView saveBtn = new TextView(ctx);
        saveBtn.setText(existing != null ? "Update Alert" : "Create Alert");
        saveBtn.setTextColor(Color.WHITE);
        saveBtn.setTextSize(15f);
        saveBtn.setTypeface(null, Typeface.BOLD);
        saveBtn.setGravity(Gravity.CENTER);
        saveBtn.setBackgroundColor(ACCENT);
        LinearLayout.LayoutParams saveLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(ctx, 50));
        saveLp.topMargin = dp(ctx, 20);
        saveLp.bottomMargin = dp(ctx, 8);
        saveBtn.setLayoutParams(saveLp);

        saveBtn.setOnClickListener(v -> {
            String sym   = etSymbol.getText().toString().trim().toUpperCase();
            String lbl   = etLabel.getText().toString().trim();
            if (sym.isEmpty() || lbl.isEmpty()) {
                Toast.makeText(ctx, "Symbol and label are required", Toast.LENGTH_SHORT).show();
                return;
            }

            // Build condition
            Condition condition = buildCondition(ctx, condSpinner.getSelectedItemPosition(), sym, condParams);
            if (condition == null) return; // validation failed, toast shown inside

            // Repeat
            Alert.RepeatMode repeatMode;
            int cooldownSecs = 0;
            if (rbPersist.isChecked()) {
                repeatMode = Alert.RepeatMode.PERSISTENT;
            } else if (rbCooldown.isChecked()) {
                repeatMode = Alert.RepeatMode.COOLDOWN;
                try { cooldownSecs = Integer.parseInt(etCooldown.getText().toString()); }
                catch (NumberFormatException e) { cooldownSecs = 300; }
            } else {
                repeatMode = Alert.RepeatMode.ONCE;
            }

            // Priority
            Alert.Priority priority;
            switch (prioritySpinner.getSelectedItemPosition()) {
                case 1:  priority = Alert.Priority.HIGH;   break;
                case 2:  priority = Alert.Priority.LOW;    break;
                default: priority = Alert.Priority.MEDIUM; break;
            }

            // Expiry
            long expiresAt = 0;
            String expiryStr = etExpiry.getText().toString().trim();
            if (!expiryStr.isEmpty()) {
                try {
                    int hours = Integer.parseInt(expiryStr);
                    if (hours > 0)
                        expiresAt = (System.currentTimeMillis() / 1000L) + hours * 3600L;
                } catch (NumberFormatException ignored) {}
            }

            Alert alert = new Alert(sym, lbl, condition, repeatMode, cooldownSecs, expiresAt, priority);

            if (existing != null) {
                // Edit: remove old, insert new (simplest approach given immutable condition field)
                AlertManager.getInstance().removeAlert(existing.getId());
            }
            AlertManager.getInstance().addAlert(alert);

            dialog.dismiss();
            if (onSuccess != null) onSuccess.run();
        });
        form.addView(saveBtn);

        return root;
    }

    // ── Dynamic condition parameters ──────────────────────────────────

    private static void renderCondParams(Context ctx, LinearLayout container,
                                         int condIdx, Alert existing) {
        container.removeAllViews();

        switch (condIdx) {
            case 0: buildPriceThresholdParams(ctx, container, existing);     break;
            case 1: buildPriceChangePctParams(ctx, container, existing);     break;
            case 2: buildSmaParams(ctx, container, existing);                break;
            case 3: buildVolatilityParams(ctx, container, existing);         break;
            case 4: buildRsiParams(ctx, container, existing);                break;
            case 5: buildVolumeSpikeParams(ctx, container, existing);        break;
        }
    }

    private static void buildPriceThresholdParams(Context ctx, LinearLayout c, Alert ex) {
        c.addView(sectionLabel(ctx, "TARGET PRICE"));
        EditText etPrice = inputField(ctx, "e.g. 200.00", InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        etPrice.setTag("price");
        if (ex != null && ex.getCondition() instanceof PriceThresholdCondition)
            etPrice.setText(String.valueOf(((PriceThresholdCondition)ex.getCondition()).getTargetPrice()));
        c.addView(etPrice);

        c.addView(sectionLabel(ctx, "DIRECTION"));
        RadioGroup rg = new RadioGroup(ctx);
        rg.setOrientation(RadioGroup.HORIZONTAL);
        rg.setTag("direction");
        RadioButton rbAbove = radio(ctx, "Above ↑");  rbAbove.setTag("above");
        RadioButton rbBelow = radio(ctx, "Below ↓");  rbBelow.setTag("below");
        rg.addView(rbAbove); rg.addView(rbBelow);
        rbAbove.setChecked(true);
        if (ex != null && ex.getCondition() instanceof PriceThresholdCondition)
            (((PriceThresholdCondition)ex.getCondition()).isLookForAbove() ? rbAbove : rbBelow).setChecked(true);
        c.addView(rg);
    }

    private static void buildPriceChangePctParams(Context ctx, LinearLayout c, Alert ex) {
        c.addView(sectionLabel(ctx, "PERCENT THRESHOLD"));
        EditText etPct = inputField(ctx, "e.g. 2.0", InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        etPct.setTag("pct");
        c.addView(etPct);

        c.addView(sectionLabel(ctx, "DIRECTION"));
        Spinner sp = styledSpinner(ctx, Arrays.asList("Either direction", "Up only", "Down only"));
        sp.setTag("dir");
        c.addView(sp);

        c.addView(sectionLabel(ctx, "TIMEFRAME"));
        Spinner tfSp = styledSpinner(ctx, TF_NAMES);
        tfSp.setTag("tf");
        c.addView(tfSp);
    }

    private static void buildSmaParams(Context ctx, LinearLayout c, Alert ex) {
        c.addView(sectionLabel(ctx, "PERIOD"));
        EditText etPeriod = inputField(ctx, "e.g. 20", InputType.TYPE_CLASS_NUMBER);
        etPeriod.setTag("period");
        c.addView(etPeriod);

        c.addView(sectionLabel(ctx, "CROSS DIRECTION"));
        RadioGroup rg = new RadioGroup(ctx);
        rg.setOrientation(RadioGroup.HORIZONTAL);
        rg.setTag("cross");
        RadioButton rbAbove = radio(ctx, "Cross Above ↑");
        RadioButton rbBelow = radio(ctx, "Cross Below ↓");
        rg.addView(rbAbove); rg.addView(rbBelow);
        rbAbove.setChecked(true);
        c.addView(rg);

        c.addView(sectionLabel(ctx, "TIMEFRAME"));
        Spinner tfSp = styledSpinner(ctx, TF_NAMES);
        tfSp.setTag("tf");
        c.addView(tfSp);
    }

    private static void buildVolatilityParams(Context ctx, LinearLayout c, Alert ex) {
        c.addView(sectionLabel(ctx, "PERCENT MOVE THRESHOLD"));
        EditText etPct = inputField(ctx, "e.g. 2.0", InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        etPct.setTag("pct");
        c.addView(etPct);

        c.addView(sectionLabel(ctx, "TIMEFRAME"));
        Spinner tfSp = styledSpinner(ctx, TF_NAMES);
        tfSp.setTag("tf");
        c.addView(tfSp);
    }

    private static void buildRsiParams(Context ctx, LinearLayout c, Alert ex) {
        c.addView(sectionLabel(ctx, "RSI PERIOD"));
        EditText etPeriod = inputField(ctx, "e.g. 14", InputType.TYPE_CLASS_NUMBER);
        etPeriod.setTag("period");
        c.addView(etPeriod);

        c.addView(sectionLabel(ctx, "LEVEL"));
        EditText etLevel = inputField(ctx, "e.g. 30 (oversold) or 70 (overbought)",
                InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        etLevel.setTag("level");
        c.addView(etLevel);

        c.addView(sectionLabel(ctx, "CROSS DIRECTION"));
        RadioGroup rg = new RadioGroup(ctx);
        rg.setOrientation(RadioGroup.HORIZONTAL);
        rg.setTag("cross");
        RadioButton rbAbove = radio(ctx, "RSI ≥ Level (overbought)");
        RadioButton rbBelow = radio(ctx, "RSI ≤ Level (oversold)");
        rg.addView(rbAbove); rg.addView(rbBelow);
        rbBelow.setChecked(true);
        c.addView(rg);

        c.addView(sectionLabel(ctx, "TIMEFRAME"));
        Spinner tfSp = styledSpinner(ctx, TF_NAMES);
        tfSp.setTag("tf");
        c.addView(tfSp);
    }

    private static void buildVolumeSpikeParams(Context ctx, LinearLayout c, Alert ex) {
        c.addView(sectionLabel(ctx, "LOOKBACK CANDLES FOR BASELINE"));
        EditText etLookback = inputField(ctx, "e.g. 20", InputType.TYPE_CLASS_NUMBER);
        etLookback.setTag("lookback");
        c.addView(etLookback);

        c.addView(sectionLabel(ctx, "SPIKE MULTIPLIER (e.g. 3.0 = 3× avg)"));
        EditText etMult = inputField(ctx, "e.g. 3.0",
                InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        etMult.setTag("mult");
        c.addView(etMult);

        c.addView(sectionLabel(ctx, "TIMEFRAME"));
        Spinner tfSp = styledSpinner(ctx, TF_NAMES);
        tfSp.setTag("tf");
        c.addView(tfSp);
    }

    // ── Condition factory ─────────────────────────────────────────────

    private static Condition buildCondition(Context ctx, int condIdx,
                                             String symbol, LinearLayout params) {
        try {
            switch (condIdx) {
                case 0: { // Price Threshold
                    EditText etPrice = params.findViewWithTag("price");
                    RadioGroup rg    = params.findViewWithTag("direction");
                    RadioButton sel  = params.findViewById(rg.getCheckedRadioButtonId());
                    double price = Double.parseDouble(etPrice.getText().toString().trim());
                    boolean above = "above".equals(sel.getTag());
                    return new PriceThresholdCondition(symbol, price, above);
                }
                case 1: { // Price Change %
                    EditText etPct = params.findViewWithTag("pct");
                    Spinner  spDir = params.findViewWithTag("dir");
                    Spinner  spTf  = params.findViewWithTag("tf");
                    double pct = Double.parseDouble(etPct.getText().toString().trim());
                    PriceChangePercentCondition.Direction dir;
                    switch (spDir.getSelectedItemPosition()) {
                        case 1:  dir = PriceChangePercentCondition.Direction.UP;   break;
                        case 2:  dir = PriceChangePercentCondition.Direction.DOWN; break;
                        default: dir = PriceChangePercentCondition.Direction.EITHER; break;
                    }
                    return new PriceChangePercentCondition(symbol, pct, dir,
                            TF_VALUES[spTf.getSelectedItemPosition()]);
                }
                case 2: { // SMA Crossover
                    EditText etPer  = params.findViewWithTag("period");
                    RadioGroup rg   = params.findViewWithTag("cross");
                    Spinner  spTf   = params.findViewWithTag("tf");
                    int period = Integer.parseInt(etPer.getText().toString().trim());
                    boolean crossAbove = rg.getCheckedRadioButtonId() ==
                            ((RadioButton)rg.getChildAt(0)).getId();
                    return new SMACrossoverCondition(symbol,
                            TF_VALUES[spTf.getSelectedItemPosition()], period,
                            crossAbove ? SMACrossoverCondition.CrossDirection.ABOVE
                                       : SMACrossoverCondition.CrossDirection.BELOW);
                }
                case 3: { // Volatility
                    EditText etPct = params.findViewWithTag("pct");
                    Spinner  spTf  = params.findViewWithTag("tf");
                    double pct = Double.parseDouble(etPct.getText().toString().trim());
                    return new VolatilityCondition(symbol, pct,
                            TF_VALUES[spTf.getSelectedItemPosition()]);
                }
                case 4: { // RSI
                    EditText etPer   = params.findViewWithTag("period");
                    EditText etLevel = params.findViewWithTag("level");
                    RadioGroup rg    = params.findViewWithTag("cross");
                    Spinner  spTf    = params.findViewWithTag("tf");
                    int period = Integer.parseInt(etPer.getText().toString().trim());
                    double level = Double.parseDouble(etLevel.getText().toString().trim());
                    boolean crossAbove = rg.getCheckedRadioButtonId() ==
                            ((RadioButton)rg.getChildAt(0)).getId();
                    return new RSICondition(symbol,
                            TF_VALUES[spTf.getSelectedItemPosition()], period, level,
                            crossAbove ? RSICondition.CrossDirection.ABOVE
                                       : RSICondition.CrossDirection.BELOW);
                }
                case 5: { // Volume Spike
                    EditText etLookback = params.findViewWithTag("lookback");
                    EditText etMult     = params.findViewWithTag("mult");
                    Spinner  spTf       = params.findViewWithTag("tf");
                    int lookback = Integer.parseInt(etLookback.getText().toString().trim());
                    double mult = Double.parseDouble(etMult.getText().toString().trim());
                    return new VolumeSpikeCondition(symbol,
                            TF_VALUES[spTf.getSelectedItemPosition()], lookback, mult);
                }
                default:
                    return null;
            }
        } catch (NumberFormatException | NullPointerException e) {
            Toast.makeText(ctx, "Please fill in all required fields", Toast.LENGTH_SHORT).show();
            return null;
        }
    }

    // ── Widget helpers ────────────────────────────────────────────────

    private static TextView sectionLabel(Context ctx, String text) {
        TextView tv = new TextView(ctx);
        tv.setText(text);
        tv.setTextColor(TEXT_SEC);
        tv.setTextSize(11f);
        tv.setTypeface(null, Typeface.BOLD);
        tv.setLetterSpacing(0.1f);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(ctx, 14);
        lp.bottomMargin = dp(ctx, 4);
        tv.setLayoutParams(lp);
        return tv;
    }

    private static EditText inputField(Context ctx, String hint, int inputType) {
        EditText et = new EditText(ctx);
        et.setHint(hint);
        et.setHintTextColor(TEXT_SEC);
        et.setTextColor(TEXT_PRI);
        et.setTextSize(14f);
        et.setInputType(inputType);
        et.setBackgroundColor(SURFACE);
        et.setPadding(dp(ctx,12), dp(ctx,10), dp(ctx,12), dp(ctx,10));
        et.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return et;
    }

    private static RadioButton radio(Context ctx, String text) {
        RadioButton rb = new RadioButton(ctx);
        rb.setText(text);
        rb.setTextColor(TEXT_PRI);
        rb.setTextSize(13f);
        LinearLayout.LayoutParams lp = new RadioGroup.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, dp(ctx, 12), 0);
        rb.setLayoutParams(lp);
        return rb;
    }

    private static Spinner styledSpinner(Context ctx, List<String> items) {
        Spinner sp = new Spinner(ctx);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                ctx, android.R.layout.simple_spinner_item, items);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        sp.setAdapter(adapter);
        sp.setBackgroundColor(SURFACE);
        sp.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return sp;
    }

    private static int dp(Context ctx, int dp) {
        return Math.round(ctx.getResources().getDisplayMetrics().density * dp);
    }
}
