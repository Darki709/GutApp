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

import com.example.gutapp.R;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

/**
 * IndicatorsPanel — bottom sheet that controls which overlays are active on the chart.
 *
 * Usage from ChartActivity:
 *     IndicatorsPanel panel = IndicatorsPanel.newInstance(currentSettings);
 *     panel.setListener(this);
 *     panel.show(getSupportFragmentManager(), "indicators");
 */
public class IndicatorsPanel extends BottomSheetDialogFragment {

    public interface IndicatorListener {
        void onIndicatorsChanged(IndicatorSettings settings);
    }

    // ── Settings bag passed back to the chart ───────────────────────
    public static class IndicatorSettings {
        public boolean maEnabled    = false;
        public int     maPeriod     = 20;

        public boolean emaEnabled   = false;
        public int     emaPeriod    = 20;

        public boolean bbEnabled    = false;
        public int     bbPeriod     = 20;

        public boolean rsiEnabled   = false;
        public boolean macdEnabled  = false;
        public boolean vwapEnabled  = false;

        // Deep copy for safe passing between threads
        public IndicatorSettings copy() {
            IndicatorSettings s   = new IndicatorSettings();
            s.maEnabled   = maEnabled;   s.maPeriod  = maPeriod;
            s.emaEnabled  = emaEnabled;  s.emaPeriod = emaPeriod;
            s.bbEnabled   = bbEnabled;   s.bbPeriod  = bbPeriod;
            s.rsiEnabled  = rsiEnabled;
            s.macdEnabled = macdEnabled;
            s.vwapEnabled = vwapEnabled;
            return s;
        }
    }

    private static final String ARG_SETTINGS = "settings";

    private IndicatorSettings settings = new IndicatorSettings();
    private IndicatorListener listener;

    public static IndicatorsPanel newInstance(IndicatorSettings current) {
        IndicatorsPanel f = new IndicatorsPanel();
        if (current != null) f.settings = current.copy();
        return f;
    }

    public void setListener(IndicatorListener l) { this.listener = l; }

    // ── Inflate the layout ──────────────────────────────────────────
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        // We build the layout programmatically so you don't need a new XML file.
        // (You can replace this with a proper XML layout file if preferred.)
        return buildView(inflater.getContext());
    }

    private View buildView(android.content.Context ctx) {
        // Root scroll container
        android.widget.ScrollView root = new android.widget.ScrollView(ctx);
        root.setBackgroundColor(android.graphics.Color.parseColor("#242222"));

        android.widget.LinearLayout container = new android.widget.LinearLayout(ctx);
        container.setOrientation(android.widget.LinearLayout.VERTICAL);
        int pad = dp(ctx, 16);
        container.setPadding(pad, pad, pad, dp(ctx, 32));
        root.addView(container);

        // Title
        TextView title = new TextView(ctx);
        title.setText("Indicators");
        title.setTextColor(android.graphics.Color.parseColor("#ECEFF1"));
        title.setTextSize(16f);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        title.setPadding(0, 0, 0, dp(ctx, 12));
        container.addView(title);

        // Divider
        container.addView(makeDivider(ctx));

        // ── Moving Average ────────────────────────────────────────
        addSectionHeader(ctx, container, "Overlays");

        RowView maRow = addToggleRow(ctx, container, "MA", "Moving Average", settings.maEnabled);
        SeekBar maSeek = addPeriodRow(ctx, container, "Period", settings.maPeriod, 5, 200, maRow.label);
        maRow.check.setOnCheckedChangeListener((b, on) -> {
            settings.maEnabled = on;
            maSeek.setEnabled(on);
            notifyListener();
        });
        maSeek.setOnSeekBarChangeListener(seekListener(v -> {
            settings.maPeriod = v;
            notifyListener();
        }));

        container.addView(makeSpacing(ctx, 8));

        // ── EMA ──────────────────────────────────────────────────
        RowView emaRow = addToggleRow(ctx, container, "EMA", "Exponential MA", settings.emaEnabled);
        SeekBar emaSeek = addPeriodRow(ctx, container, "Period", settings.emaPeriod, 5, 200, emaRow.label);
        emaRow.check.setOnCheckedChangeListener((b, on) -> {
            settings.emaEnabled = on;
            emaSeek.setEnabled(on);
            notifyListener();
        });
        emaSeek.setOnSeekBarChangeListener(seekListener(v -> {
            settings.emaPeriod = v;
            notifyListener();
        }));

        container.addView(makeSpacing(ctx, 8));

        // ── Bollinger Bands ───────────────────────────────────────
        RowView bbRow = addToggleRow(ctx, container, "BB", "Bollinger Bands", settings.bbEnabled);
        SeekBar bbSeek = addPeriodRow(ctx, container, "Period", settings.bbPeriod, 5, 50, bbRow.label);
        bbRow.check.setOnCheckedChangeListener((b, on) -> {
            settings.bbEnabled = on;
            bbSeek.setEnabled(on);
            notifyListener();
        });
        bbSeek.setOnSeekBarChangeListener(seekListener(v -> {
            settings.bbPeriod = v;
            notifyListener();
        }));

        // ── Oscillators section ───────────────────────────────────
        container.addView(makeSpacing(ctx, 12));
        container.addView(makeDivider(ctx));
        addSectionHeader(ctx, container, "Oscillators  (shown below chart)");

        addSimpleToggle(ctx, container, "RSI", "Relative Strength Index", settings.rsiEnabled, on -> {
            settings.rsiEnabled = on;
            notifyListener();
        });
        addSimpleToggle(ctx, container, "MACD", "Moving Avg Convergence Divergence", settings.macdEnabled, on -> {
            settings.macdEnabled = on;
            notifyListener();
        });

        // ── Other ─────────────────────────────────────────────────
        container.addView(makeSpacing(ctx, 12));
        container.addView(makeDivider(ctx));
        addSectionHeader(ctx, container, "Other");

        addSimpleToggle(ctx, container, "VWAP", "Volume Weighted Avg Price", settings.vwapEnabled, on -> {
            settings.vwapEnabled = on;
            notifyListener();
        });

        // ── Clear all button ─────────────────────────────────────
        container.addView(makeSpacing(ctx, 16));
        android.widget.Button clearBtn = new android.widget.Button(ctx);
        clearBtn.setText("Clear All Indicators");
        clearBtn.setTextColor(android.graphics.Color.parseColor("#EF5350"));
        clearBtn.setBackgroundColor(android.graphics.Color.parseColor("#2E2C2C"));
        clearBtn.setOnClickListener(v -> {
            settings = new IndicatorSettings();
            notifyListener();
            dismiss();
        });
        container.addView(clearBtn);

        return root;
    }

    // ── UI builder helpers ───────────────────────────────────────────

    private static class RowView {
        CheckBox check;
        TextView label;  // shows current period value
    }

    private RowView addToggleRow(android.content.Context ctx,
                                 android.widget.LinearLayout parent,
                                 String tag, String name, boolean checked) {
        android.widget.LinearLayout row = new android.widget.LinearLayout(ctx);
        row.setOrientation(android.widget.LinearLayout.HORIZONTAL);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(ctx, 4), 0, dp(ctx, 2));

        CheckBox cb = new CheckBox(ctx);
        cb.setChecked(checked);

        // Colored tag badge
        TextView tagView = new TextView(ctx);
        tagView.setText("  " + tag + "  ");
        tagView.setTextColor(android.graphics.Color.parseColor("#ECEFF1"));
        tagView.setTextSize(11f);
        tagView.setBackgroundColor(android.graphics.Color.parseColor("#2E3347"));
        android.widget.LinearLayout.LayoutParams tagParams =
                new android.widget.LinearLayout.LayoutParams(
                        android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                        android.widget.LinearLayout.LayoutParams.WRAP_CONTENT);
        tagParams.setMarginStart(dp(ctx, 8));
        tagParams.setMarginEnd(dp(ctx, 8));
        tagView.setLayoutParams(tagParams);

        TextView nameView = new TextView(ctx);
        nameView.setText(name);
        nameView.setTextColor(android.graphics.Color.parseColor("#B0BEC5"));
        nameView.setTextSize(13f);
        android.widget.LinearLayout.LayoutParams nameParams =
                new android.widget.LinearLayout.LayoutParams(0,
                        android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        nameView.setLayoutParams(nameParams);

        row.addView(cb);
        row.addView(tagView);
        row.addView(nameView);

        parent.addView(row);

        RowView rv = new RowView();
        rv.check = cb;
        rv.label = nameView;
        return rv;
    }

    private SeekBar addPeriodRow(android.content.Context ctx,
                                 android.widget.LinearLayout parent,
                                 String label, int currentVal,
                                 int min, int max, TextView boundLabel) {
        android.widget.LinearLayout row = new android.widget.LinearLayout(ctx);
        row.setOrientation(android.widget.LinearLayout.HORIZONTAL);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        row.setPadding(dp(ctx, 32), 0, 0, dp(ctx, 6));

        TextView lbl = new TextView(ctx);
        lbl.setText(label + ": ");
        lbl.setTextColor(android.graphics.Color.parseColor("#546E7A"));
        lbl.setTextSize(12f);
        lbl.setMinWidth(dp(ctx, 60));
        row.addView(lbl);

        SeekBar seek = new SeekBar(ctx);
        seek.setMax(max - min);
        seek.setProgress(currentVal - min);
        android.widget.LinearLayout.LayoutParams seekParams =
                new android.widget.LinearLayout.LayoutParams(0,
                        android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        seek.setLayoutParams(seekParams);
        row.addView(seek);

        TextView valView = new TextView(ctx);
        valView.setText("  " + currentVal);
        valView.setTextColor(android.graphics.Color.parseColor("#ECEFF1"));
        valView.setTextSize(12f);
        valView.setMinWidth(dp(ctx, 36));
        row.addView(valView);

        // Update val label on seek
        seek.setOnSeekBarChangeListener(seekListener(v -> {
            valView.setText("  " + (v + min));
        }));

        parent.addView(row);
        return seek;
    }

    private void addSimpleToggle(android.content.Context ctx,
                                 android.widget.LinearLayout parent,
                                 String tag, String name, boolean checked,
                                 java.util.function.Consumer<Boolean> onChange) {
        RowView rv = addToggleRow(ctx, parent, tag, name, checked);
        rv.check.setOnCheckedChangeListener((b, on) -> onChange.accept(on));
        parent.addView(makeSpacing(ctx, 4));
    }

    private void addSectionHeader(android.content.Context ctx,
                                  android.widget.LinearLayout parent, String text) {
        TextView tv = new TextView(ctx);
        tv.setText(text.toUpperCase());
        tv.setTextColor(android.graphics.Color.parseColor("#546E7A"));
        tv.setTextSize(10f);
        tv.setLetterSpacing(0.12f);
        tv.setPadding(0, dp(ctx, 8), 0, dp(ctx, 4));
        parent.addView(tv);
    }

    private View makeDivider(android.content.Context ctx) {
        View v = new View(ctx);
        v.setBackgroundColor(android.graphics.Color.parseColor("#2E2C2C"));
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

    // Simple seek listener adapter
    private SeekBar.OnSeekBarChangeListener seekListener(java.util.function.IntConsumer onProgress) {
        return new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int v, boolean fromUser) {
                if (fromUser) onProgress.accept(v);
            }
            @Override public void onStartTrackingTouch(SeekBar s) {}
            @Override public void onStopTrackingTouch(SeekBar s) {}
        };
    }

    private void notifyListener() {
        if (listener != null) listener.onIndicatorsChanged(settings.copy());
    }
}