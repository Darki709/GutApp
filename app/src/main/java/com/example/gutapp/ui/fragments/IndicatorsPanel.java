package com.example.gutapp.ui.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.gutapp.data.indicators.Indicator;
import com.example.gutapp.data.indicators.IndicatorRegistry;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import java.util.List;

/**
 * IndicatorsPanel — fully driven by IndicatorRegistry.
 *
 * The panel never hardcodes indicator names. It reads every registered
 * indicator's metadata (id, name, tag, params) and builds the UI rows
 * automatically. To add a new indicator to the app you only need to:
 *   1. Create the indicator class
 *   2. Register it in IndicatorRegistry
 * This panel needs no changes at all.
 *
 * Listener is called after every toggle/slider change so the chart
 * updates in real time while the sheet is still open.
 */
public class IndicatorsPanel extends BottomSheetDialogFragment {

    public interface IndicatorListener {
        /** Called whenever any indicator's enabled state or params change */
        void onIndicatorsChanged();
    }

    private IndicatorListener listener;

    public static IndicatorsPanel newInstance() {
        return new IndicatorsPanel();
    }

    public void setListener(IndicatorListener l) { this.listener = l; }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return buildView(requireContext());
    }

    // ── Layout builder — fully driven by IndicatorRegistry ────────────
    private View buildView(android.content.Context ctx) {
        android.widget.ScrollView root = new android.widget.ScrollView(ctx);
        root.setBackgroundColor(android.graphics.Color.parseColor("#1A1818"));

        android.widget.LinearLayout container = new android.widget.LinearLayout(ctx);
        container.setOrientation(android.widget.LinearLayout.VERTICAL);
        int pad16 = dp(ctx, 16);
        container.setPadding(pad16, pad16, pad16, dp(ctx, 40));
        root.addView(container);

        // ── Title row ─────────────────────────────────────────────
        android.widget.LinearLayout titleRow = new android.widget.LinearLayout(ctx);
        titleRow.setOrientation(android.widget.LinearLayout.HORIZONTAL);
        titleRow.setGravity(android.view.Gravity.CENTER_VERTICAL);
        titleRow.setPadding(0, 0, 0, dp(ctx, 14));

        TextView title = new TextView(ctx);
        title.setText("Indicators");
        title.setTextColor(android.graphics.Color.parseColor("#ECEFF1"));
        title.setTextSize(17f);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        android.widget.LinearLayout.LayoutParams titleParams =
                new android.widget.LinearLayout.LayoutParams(0,
                        android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        title.setLayoutParams(titleParams);
        titleRow.addView(title);

        TextView clearAll = new TextView(ctx);
        clearAll.setText("Clear All");
        clearAll.setTextColor(android.graphics.Color.parseColor("#EF5350"));
        clearAll.setTextSize(13f);
        clearAll.setPadding(dp(ctx,8), dp(ctx,4), dp(ctx,8), dp(ctx,4));
        clearAll.setOnClickListener(v -> {
            IndicatorRegistry.getInstance().clearAll();
            notifyListener();
            dismiss();
        });
        titleRow.addView(clearAll);
        container.addView(titleRow);
        container.addView(makeDivider(ctx));

        // ── Overlay indicators ─────────────────────────────────────
        boolean hasOverlay   = false;
        boolean hasSubChart  = false;

        for (Indicator ind : IndicatorRegistry.getInstance().getAll()) {
            if (!ind.isSubChart() && !hasOverlay) {
                addSectionHeader(ctx, container, "Overlays");
                hasOverlay = true;
            }
            if (ind.isSubChart() && !hasSubChart) {
                container.addView(makeSpacing(ctx, 12));
                container.addView(makeDivider(ctx));
                addSectionHeader(ctx, container, "Oscillators  (separate pane below chart)");
                hasSubChart = true;
            }
            buildIndicatorRow(ctx, container, ind);
        }

        return root;
    }

    /**
     * Builds one indicator's row: toggle checkbox + tag badge + name,
     * then one SeekBar row per param.
     */
    private void buildIndicatorRow(android.content.Context ctx,
                                   android.widget.LinearLayout parent,
                                   Indicator ind) {
        // ── Toggle row ─────────────────────────────────────────────
        android.widget.LinearLayout row = new android.widget.LinearLayout(ctx);
        row.setOrientation(android.widget.LinearLayout.HORIZONTAL);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(ctx, 6), 0, dp(ctx, 2));

        CheckBox cb = new CheckBox(ctx);
        cb.setChecked(ind.isEnabled());

        // Tag badge
        TextView tag = new TextView(ctx);
        tag.setText("  " + ind.getTag() + "  ");
        tag.setTextColor(android.graphics.Color.parseColor("#ECEFF1"));
        tag.setTextSize(11f);
        tag.setBackgroundColor(android.graphics.Color.parseColor("#252A3D"));
        android.widget.LinearLayout.LayoutParams tagP =
                new android.widget.LinearLayout.LayoutParams(
                        android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                        android.widget.LinearLayout.LayoutParams.WRAP_CONTENT);
        tagP.setMarginStart(dp(ctx, 8));
        tagP.setMarginEnd(dp(ctx, 8));
        tag.setLayoutParams(tagP);

        // Indicator display name
        TextView name = new TextView(ctx);
        name.setText(ind.getDisplayName());
        name.setTextColor(android.graphics.Color.parseColor("#B0BEC5"));
        name.setTextSize(13f);
        android.widget.LinearLayout.LayoutParams nameP =
                new android.widget.LinearLayout.LayoutParams(0,
                        android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        name.setLayoutParams(nameP);

        row.addView(cb);
        row.addView(tag);
        row.addView(name);
        parent.addView(row);

        // ── Param rows (seekbars) ──────────────────────────────────
        List<Indicator.Param> params = ind.getParams();
        List<View> paramRows = new java.util.ArrayList<>();

        for (Indicator.Param param : params) {
            View paramRow = buildParamRow(ctx, parent, param);
            paramRows.add(paramRow);
            // Start hidden if indicator is disabled
            paramRow.setVisibility(ind.isEnabled() ? View.VISIBLE : View.GONE);
        }

        parent.addView(makeSpacing(ctx, 2));

        // Checkbox toggles the indicator and shows/hides its param rows
        cb.setOnCheckedChangeListener((btn, on) -> {
            ind.setEnabled(on);
            for (View pRow : paramRows) pRow.setVisibility(on ? View.VISIBLE : View.GONE);
            notifyListener();
        });
    }

    private View buildParamRow(android.content.Context ctx,
                               android.widget.LinearLayout parent,
                               Indicator.Param param) {
        android.widget.LinearLayout row = new android.widget.LinearLayout(ctx);
        row.setOrientation(android.widget.LinearLayout.HORIZONTAL);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        row.setPadding(dp(ctx, 36), dp(ctx, 2), 0, dp(ctx, 4));

        // Label
        TextView lbl = new TextView(ctx);
        lbl.setText(param.label + ": ");
        lbl.setTextColor(android.graphics.Color.parseColor("#546E7A"));
        lbl.setTextSize(12f);
        lbl.setMinWidth(dp(ctx, 72));
        row.addView(lbl);

        // SeekBar
        SeekBar seek = new SeekBar(ctx);
        int range = (int)(param.max - param.min);
        seek.setMax(range);
        seek.setProgress((int)(param.value - param.min));
        android.widget.LinearLayout.LayoutParams seekP =
                new android.widget.LinearLayout.LayoutParams(0,
                        android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        seek.setLayoutParams(seekP);
        row.addView(seek);

        // Value label
        TextView val = new TextView(ctx);
        val.setMinWidth(dp(ctx, 40));
        val.setTextColor(android.graphics.Color.parseColor("#ECEFF1"));
        val.setTextSize(12f);
        val.setPadding(dp(ctx, 6), 0, 0, 0);
        updateValLabel(val, param);
        row.addView(val);

        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar s, int progress, boolean fromUser) {
                if (!fromUser) return;
                float newVal = param.min + progress;
                param.value = newVal;
                updateValLabel(val, param);
                notifyListener();
            }
            @Override public void onStartTrackingTouch(SeekBar s) {}
            @Override public void onStopTrackingTouch(SeekBar s) {}
        });

        parent.addView(row);
        return row;
    }

    private void updateValLabel(TextView tv, Indicator.Param param) {
        if (param.type == Indicator.Param.Type.INTEGER) {
            tv.setText(String.valueOf(param.intValue()));
        } else {
            tv.setText(String.format(Locale.US, "%.1f", param.floatValue()));
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────
    private void addSectionHeader(android.content.Context ctx,
                                  android.widget.LinearLayout parent, String text) {
        TextView tv = new TextView(ctx);
        tv.setText(text.toUpperCase());
        tv.setTextColor(android.graphics.Color.parseColor("#546E7A"));
        tv.setTextSize(10f);
        tv.setLetterSpacing(0.12f);
        tv.setPadding(0, dp(ctx, 10), 0, dp(ctx, 4));
        parent.addView(tv);
    }

    private View makeDivider(android.content.Context ctx) {
        View v = new View(ctx);
        v.setBackgroundColor(android.graphics.Color.parseColor("#2A2828"));
        android.widget.LinearLayout.LayoutParams p =
                new android.widget.LinearLayout.LayoutParams(
                        android.widget.LinearLayout.LayoutParams.MATCH_PARENT, 1);
        v.setLayoutParams(p);
        return v;
    }

    private View makeSpacing(android.content.Context ctx, int dpVal) {
        View v = new View(ctx);
        android.widget.LinearLayout.LayoutParams p =
                new android.widget.LinearLayout.LayoutParams(
                        android.widget.LinearLayout.LayoutParams.MATCH_PARENT, dp(ctx, dpVal));
        v.setLayoutParams(p);
        return v;
    }

    private int dp(android.content.Context ctx, int dp) {
        return Math.round(dp * ctx.getResources().getDisplayMetrics().density);
    }

    private void notifyListener() {
        if (listener != null) listener.onIndicatorsChanged();
    }

    // Keep for import
    private final java.util.Locale Locale = java.util.Locale.US;
}