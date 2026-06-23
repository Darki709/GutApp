package com.example.gutapp.ui.dialogue;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;

import com.example.gutapp.data.indicators.Indicator;

import java.util.List;
import java.util.Locale;

/**
 * IndicatorEditPanel — bottom sheet-style editor that slides in when an
 * indicator's overlay line is tapped on the chart (pan/zoom mode).
 *
 * Mirrors {@link DrawingEditPanel}: header (name + delete + close), a color
 * palette, and a row of −/+ steppers — one per indicator parameter.
 *
 * Call show(indicator, onDelete, onDeselect, onChange) to populate + reveal,
 * hide() to collapse. onChange fires on every edit so the caller can re-render
 * + persist exactly like the drawing panel does.
 */
public class IndicatorEditPanel extends LinearLayout {

    // Same palette as DrawingEditPanel for a consistent look.
    private static final int[] COLORS = {
            0xFFECEFF1, 0xFF26A69A, 0xFF2196F3, 0xFF4CAF50,
            0xFFF44336, 0xFFFF9800, 0xFFFFEB3B, 0xFF9C27B0,
            0xFFE91E63, 0xFF00BCD4, 0xFF795548, 0xFF607D8B
    };

    public interface OnChangeListener { void onChange(); }

    @Nullable private Indicator       current;
    @Nullable private Runnable        onDelete;
    @Nullable private Runnable        onDeselect;
    @Nullable private OnChangeListener onChange;

    public IndicatorEditPanel(Context ctx) { super(ctx); init(); }
    public IndicatorEditPanel(Context ctx, AttributeSet a) { super(ctx, a); init(); }

    private void init() {
        setOrientation(VERTICAL);
        setBackgroundColor(Color.parseColor("#F0161414"));
        setVisibility(GONE);
        setElevation(20f);
    }

    /** Populate and reveal the panel for the given indicator instance. */
    public void show(Indicator ind, Runnable onDeleteFn, Runnable onDeselectFn, OnChangeListener onChangeFn) {
        this.current    = ind;
        this.onDelete   = onDeleteFn;
        this.onDeselect = onDeselectFn;
        this.onChange   = onChangeFn;
        removeAllViews();
        buildPanel(ind);
        setVisibility(VISIBLE);
    }

    public void hide() {
        setVisibility(GONE);
        removeAllViews();
        current = null;
    }

    @Nullable public Indicator getCurrent() { return current; }

    // ── Build ─────────────────────────────────────────────────────────
    private void buildPanel(Indicator ind) {
        setPadding(0, dp(6), 0, dp(10));
        addView(buildHeader(ind));
        addView(div());
        addView(buildColorRow(ind));

        List<Indicator.Param> params = ind.getParams();
        if (!params.isEmpty()) {
            addView(div());
            LinearLayout col = new LinearLayout(getContext());
            col.setOrientation(VERTICAL);
            col.setPadding(dp(12), dp(6), dp(12), dp(2));
            col.addView(sectionLabel("PARAMETERS"));
            for (Indicator.Param p : params) col.addView(buildParamRow(ind, p));
            addView(col);
        }
    }

    // ── Header ────────────────────────────────────────────────────────
    private View buildHeader(Indicator ind) {
        LinearLayout row = row(dp(12), dp(8), dp(12), dp(8));
        row.setGravity(Gravity.CENTER_VERTICAL);

        View dot = new View(getContext());
        LayoutParams dotP = new LayoutParams(dp(12), dp(12));
        dotP.setMarginEnd(dp(8));
        dot.setLayoutParams(dotP);
        android.graphics.drawable.GradientDrawable g = new android.graphics.drawable.GradientDrawable();
        g.setShape(android.graphics.drawable.GradientDrawable.OVAL);
        g.setColor(ind.getColor());
        dot.setBackground(g);
        row.addView(dot);

        TextView name = tv(ind.getDisplayName() + "  " + ind.getTag(), "#26A69A", 13f, true);
        name.setLayoutParams(new LayoutParams(0, WRAP, 1f));
        row.addView(name);

        TextView del = chip("🗑 Delete", "#EF5350");
        del.setOnClickListener(v -> { if (onDelete != null) onDelete.run(); });
        row.addView(del); row.addView(sp(8));

        TextView close = chip("✕", "#78909C");
        close.setOnClickListener(v -> { if (onDeselect != null) onDeselect.run(); });
        row.addView(close);
        return row;
    }

    // ── Color row ─────────────────────────────────────────────────────
    private View buildColorRow(Indicator ind) {
        LinearLayout col = new LinearLayout(getContext());
        col.setOrientation(VERTICAL);
        col.setPadding(dp(12), dp(4), dp(12), dp(2));

        TextView lbl = sectionLabel("COLOR");
        col.addView(lbl);

        HorizontalScrollView hsv = new HorizontalScrollView(getContext());
        hsv.setHorizontalScrollBarEnabled(false);
        LinearLayout swRow = new LinearLayout(getContext());
        swRow.setOrientation(HORIZONTAL); swRow.setGravity(Gravity.CENTER_VERTICAL);
        swRow.setPadding(0, dp(6), 0, dp(2));

        for (int color : COLORS) {
            View sw = new View(getContext());
            applySwatch(sw, color, ind.getColor() == color);
            sw.setOnClickListener(v -> {
                ind.setColor(color);
                refreshColorRow(swRow, ind);
                fire();
            });
            swRow.addView(sw);
        }
        hsv.addView(swRow); col.addView(hsv);
        return col;
    }

    private void refreshColorRow(LinearLayout swRow, Indicator ind) {
        for (int i = 0; i < swRow.getChildCount(); i++) {
            applySwatch(swRow.getChildAt(i), COLORS[i], ind.getColor() == COLORS[i]);
        }
    }

    private void applySwatch(View sw, int color, boolean selected) {
        LayoutParams lp = new LayoutParams(selected ? dp(26) : dp(20), selected ? dp(26) : dp(20));
        lp.setMarginEnd(dp(8));
        sw.setLayoutParams(lp);
        if (selected) {
            android.graphics.drawable.GradientDrawable ring =
                    new android.graphics.drawable.GradientDrawable();
            ring.setShape(android.graphics.drawable.GradientDrawable.OVAL);
            ring.setColor(color); ring.setStroke(dp(2), Color.WHITE);
            sw.setBackground(ring);
        } else {
            sw.setBackgroundColor(color);
        }
    }

    // ── Param stepper row ─────────────────────────────────────────────
    private View buildParamRow(Indicator ind, Indicator.Param p) {
        LinearLayout row = row(dp(0), dp(4), dp(0), dp(4));
        row.setGravity(Gravity.CENTER_VERTICAL);

        TextView lbl = tv(p.label, "#90A4AE", 11f, false);
        lbl.setLayoutParams(new LayoutParams(0, WRAP, 1f));
        row.addView(lbl);

        final float step = (p.type == Indicator.Param.Type.INTEGER) ? 1f
                : ((p.max - p.min) <= 10f ? 0.1f : 1f);

        TextView minus = chip("−", "#78909C");
        TextView valTv = tv(fmt(p), "#ECEFF1", 12f, true);
        valTv.setMinWidth(dp(44)); valTv.setGravity(Gravity.CENTER);
        TextView plus = chip("+", "#78909C");

        minus.setOnClickListener(v -> {
            p.value = clamp(round(p.value - step, p), p);
            valTv.setText(fmt(p)); fire();
        });
        plus.setOnClickListener(v -> {
            p.value = clamp(round(p.value + step, p), p);
            valTv.setText(fmt(p)); fire();
        });

        row.addView(minus); row.addView(sp(6)); row.addView(valTv);
        row.addView(sp(6)); row.addView(plus);
        return row;
    }

    private float clamp(float v, Indicator.Param p) { return Math.max(p.min, Math.min(p.max, v)); }

    /** Snap INTEGER params to whole numbers; round FLOAT to 1 decimal to avoid drift. */
    private float round(float v, Indicator.Param p) {
        if (p.type == Indicator.Param.Type.INTEGER) return Math.round(v);
        return Math.round(v * 10f) / 10f;
    }

    private String fmt(Indicator.Param p) {
        if (p.type == Indicator.Param.Type.INTEGER) return String.valueOf(p.intValue());
        return String.format(Locale.US, "%.1f", p.floatValue());
    }

    private void fire() { if (onChange != null) onChange.onChange(); }

    // ── View helpers (kept identical to DrawingEditPanel) ─────────────
    private TextView tv(String t, String hex, float sp, boolean bold) {
        TextView v = new TextView(getContext());
        v.setText(t); v.setTextColor(Color.parseColor(hex)); v.setTextSize(sp);
        if (bold) v.setTypeface(null, Typeface.BOLD);
        return v;
    }
    private TextView chip(String t, String hex) {
        TextView v = new TextView(getContext());
        v.setText(t); v.setTextColor(Color.parseColor(hex));
        v.setTextSize(10f); v.setGravity(Gravity.CENTER);
        v.setPadding(dp(8), dp(5), dp(8), dp(5));
        v.setBackgroundColor(Color.parseColor("#1E1C1C"));
        return v;
    }
    private TextView sectionLabel(String t) {
        TextView v = tv(t, "#546E7A", 9f, true);
        v.setLetterSpacing(0.1f);
        LayoutParams lp = new LayoutParams(MATCH, WRAP); lp.bottomMargin = dp(3); v.setLayoutParams(lp);
        return v;
    }
    private LinearLayout row(int l, int t, int r, int b) {
        LinearLayout row = new LinearLayout(getContext());
        row.setOrientation(HORIZONTAL); row.setPadding(l, t, r, b); return row;
    }
    private View div() {
        View v = new View(getContext());
        v.setBackgroundColor(Color.parseColor("#252323"));
        v.setLayoutParams(new LayoutParams(MATCH, 1)); return v;
    }
    private View sp(int d) {
        View v = new View(getContext());
        v.setLayoutParams(new LayoutParams(dp(d), 1)); return v;
    }
    private int dp(int val) {
        return Math.round(val * getContext().getResources().getDisplayMetrics().density);
    }
    private static final int MATCH = ViewGroup.LayoutParams.MATCH_PARENT;
    private static final int WRAP  = ViewGroup.LayoutParams.WRAP_CONTENT;
}
