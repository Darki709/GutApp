package com.example.gutapp.ui.dialogue;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.text.InputType;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;

import com.example.gutapp.data.drawing.ChartDrawing;

import java.util.Locale;

/**
 * DrawingEditPanel — a LinearLayout that slides in at the bottom of the screen
 * whenever a USER drawing is selected in pan/zoom mode.
 *
 * Shows: name, color palette, stroke controls, layer toggle, type-specific
 * parameters (label, extend flags, channel toggle, fib range info, zone delta).
 *
 * Call show(drawing, onChange) to populate + reveal.
 * Call hide() to collapse.
 */
public class DrawingEditPanel extends LinearLayout {

    private static final int[] COLORS = {
            0xFFECEFF1, 0xFF26A69A, 0xFF2196F3, 0xFF4CAF50,
            0xFFF44336, 0xFFFF9800, 0xFFFFEB3B, 0xFF9C27B0,
            0xFFE91E63, 0xFF00BCD4, 0xFF795548, 0xFF607D8B
    };

    public interface OnChangeListener { void onChange(); }

    private ChartDrawing currentDrawing;
    private Runnable     onDelete;
    private Runnable     onDeselect;
    private OnChangeListener onChange;

    public DrawingEditPanel(Context ctx) { super(ctx); init(); }
    public DrawingEditPanel(Context ctx, AttributeSet a) { super(ctx, a); init(); }

    private void init() {
        setOrientation(VERTICAL);
        setBackgroundColor(Color.parseColor("#F0161414"));
        setVisibility(GONE);
        setElevation(20f);
    }

    /** Populate and reveal the panel for the given drawing. */
    public void show(ChartDrawing d, Runnable onDeleteFn, Runnable onDeselectFn, OnChangeListener onChangeFn) {
        this.currentDrawing = d;
        this.onDelete   = onDeleteFn;
        this.onDeselect = onDeselectFn;
        this.onChange   = onChangeFn;
        removeAllViews();
        buildPanel(d);
        setVisibility(VISIBLE);
    }

    public void hide() {
        setVisibility(GONE);
        removeAllViews();
        currentDrawing = null;
    }

    // ── Build ─────────────────────────────────────────────────────────
    private void buildPanel(ChartDrawing d) {
        setPadding(0, dp(6), 0, dp(10));

        addView(buildHeader(d));
        addView(div());
        addView(buildColorRow(d));
        addView(buildStrokeRow(d));
        addView(div());
        addView(buildLayerRow(d));

        View specific = buildSpecific(d);
        if (specific != null) { addView(div()); addView(specific); }
    }

    // ── Header ────────────────────────────────────────────────────────
    private View buildHeader(ChartDrawing d) {
        LinearLayout row = row(dp(12), dp(8), dp(12), dp(8));
        row.setGravity(Gravity.CENTER_VERTICAL);

        TextView name = tv("✏  " + typeName(d), "#26A69A", 13f, true);
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
    private View buildColorRow(ChartDrawing d) {
        LinearLayout col = new LinearLayout(getContext());
        col.setOrientation(VERTICAL);
        col.setPadding(dp(12), dp(4), dp(12), dp(2));

        TextView lbl = tv("COLOR", "#546E7A", 9f, true);
        lbl.setLetterSpacing(0.1f); col.addView(lbl);

        HorizontalScrollView hsv = new HorizontalScrollView(getContext());
        hsv.setHorizontalScrollBarEnabled(false);
        LinearLayout swRow = new LinearLayout(getContext());
        swRow.setOrientation(HORIZONTAL); swRow.setGravity(Gravity.CENTER_VERTICAL);
        swRow.setPadding(0, dp(6), 0, dp(2));

        for (int color : COLORS) {
            View sw = new View(getContext());
            boolean sel = d.style != null && d.style.color == color;
            LayoutParams lp = new LayoutParams(sel ? dp(26) : dp(20), sel ? dp(26) : dp(20));
            lp.setMarginEnd(dp(8)); sw.setLayoutParams(lp);
            if (sel) {
                android.graphics.drawable.GradientDrawable ring =
                        new android.graphics.drawable.GradientDrawable();
                ring.setShape(android.graphics.drawable.GradientDrawable.OVAL);
                ring.setColor(color); ring.setStroke(dp(2), Color.WHITE);
                sw.setBackground(ring);
            } else { sw.setBackgroundColor(color); }
            sw.setOnClickListener(v -> {
                if (d.style != null) {
                    d.style.color = color;
                    d.style.fillColor = Color.argb(50,
                            Color.red(color), Color.green(color), Color.blue(color));
                    refreshColorRow(swRow, d);
                }
                fire();
            });
            swRow.addView(sw);
        }
        hsv.addView(swRow); col.addView(hsv);
        return col;
    }

    private void refreshColorRow(LinearLayout swatchRow, ChartDrawing d) {
        for (int i = 0; i < swatchRow.getChildCount(); i++) {
            View swatch = swatchRow.getChildAt(i);
            boolean sel = d.style != null && d.style.color == COLORS[i];
            LinearLayout.LayoutParams lp = new LayoutParams(sel ? dp(26) : dp(20), sel ? dp(26) : dp(20));
            lp.setMarginEnd(dp(8));
            swatch.setLayoutParams(lp);
            if (sel) {
                android.graphics.drawable.GradientDrawable ring =
                        new android.graphics.drawable.GradientDrawable();
                ring.setShape(android.graphics.drawable.GradientDrawable.OVAL);
                ring.setColor(COLORS[i]); ring.setStroke(dp(2), Color.WHITE);
                swatch.setBackground(ring);
            }
            else swatch.setBackgroundColor(COLORS[i]);
        }
    }

    // ── Stroke row ────────────────────────────────────────────────────
    private View buildStrokeRow(ChartDrawing d) {
        LinearLayout row = row(dp(12), dp(4), dp(12), dp(4));
        row.setGravity(Gravity.CENTER_VERTICAL);

        row.addView(tv("W:", "#546E7A", 10f, false)); row.addView(sp(4));

        TextView wm = chip("−", "#78909C");
        TextView wDisplay = tv(d.style != null ?
                        String.format(Locale.US, "%.1f", d.style.strokeWidth) : "1.5",
                "#ECEFF1", 11f, true);
        wDisplay.setMinWidth(dp(28)); wDisplay.setGravity(Gravity.CENTER);
        TextView wp = chip("+", "#78909C");

        wm.setOnClickListener(v -> {
            if (d.style != null) d.style.strokeWidth = Math.max(0.5f, d.style.strokeWidth - 0.5f);
            wDisplay.setText(d.style != null ?
                    String.format(Locale.US, "%.1f", d.style.strokeWidth) : "1.5");
            fire();
        });
        wp.setOnClickListener(v -> {
            if (d.style != null) d.style.strokeWidth = Math.min(8f, d.style.strokeWidth + 0.5f);
            wDisplay.setText(d.style != null ?
                    String.format(Locale.US, "%.1f", d.style.strokeWidth) : "1.5");
            fire();
        });

        row.addView(wm); row.addView(wDisplay); row.addView(wp); row.addView(sp(12));

        // Dash toggle
        boolean isDash = d.style != null && d.style.dashed;
        TextView dash = chip(isDash ? "─ ─" : "───", isDash ? "#26A69A" : "#78909C");
        dash.setOnClickListener(v -> {
            if (d.style != null) d.style.dashed = !d.style.dashed;
            boolean nd = d.style != null && d.style.dashed;
            dash.setText(nd ? "─ ─" : "───");
            dash.setTextColor(Color.parseColor(nd ? "#26A69A" : "#78909C"));
            fire();
        });
        row.addView(dash); row.addView(sp(8));

        // Fill toggle (shapes only)
        if (d instanceof ChartDrawing.Rectangle || d instanceof ChartDrawing.Ellipse
                || d instanceof ChartDrawing.PriceRange
                || d instanceof ChartDrawing.ParallelChannel
                || d instanceof ChartDrawing.FibRetracement) {
            boolean filled = d.style != null && d.style.filled;
            TextView fill = chip(filled ? "▪Fill" : "□Fill", filled ? "#26A69A" : "#78909C");
            fill.setOnClickListener(v -> {
                if (d.style != null) d.style.filled = !d.style.filled;
                boolean nf = d.style != null && d.style.filled;
                fill.setText(nf ? "▪Fill" : "□Fill");
                fill.setTextColor(Color.parseColor(nf ? "#26A69A" : "#78909C"));
                fire();
            });
            row.addView(fill);
        }

        return row;
    }

    // ── Layer row ─────────────────────────────────────────────────────
    private View buildLayerRow(ChartDrawing d) {
        LinearLayout row = row(dp(12), dp(4), dp(12), dp(4));
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.addView(tv("Depth:", "#546E7A", 10f, false)); row.addView(sp(8));

        boolean above = d.layer == ChartDrawing.Layer.ABOVE_CANDLES;
        TextView btn = chip(above ? "▲ Above candles" : "▼ Below candles",
                above ? "#26A69A" : "#78909C");
        btn.setOnClickListener(v -> {
            d.layer = d.layer == ChartDrawing.Layer.BEHIND_CANDLES
                    ? ChartDrawing.Layer.ABOVE_CANDLES : ChartDrawing.Layer.BEHIND_CANDLES;
            boolean na = d.layer == ChartDrawing.Layer.ABOVE_CANDLES;
            btn.setText(na ? "▲ Above candles" : "▼ Below candles");
            btn.setTextColor(Color.parseColor(na ? "#26A69A" : "#78909C"));
            fire();
        });
        row.addView(btn);
        return row;
    }

    // ── Type-specific params ──────────────────────────────────────────
    @Nullable
    private View buildSpecific(ChartDrawing d) {
        LinearLayout col = new LinearLayout(getContext());
        col.setOrientation(VERTICAL);
        col.setPadding(dp(12), dp(6), dp(12), dp(4));
        boolean has = false;

        // Label editor
        if (d instanceof ChartDrawing.HorizontalLine
                || d instanceof ChartDrawing.VerticalLine
                || d instanceof ChartDrawing.TextAnnotation) {
            col.addView(sectionLabel("LABEL"));
            String cur = "";
            if      (d instanceof ChartDrawing.HorizontalLine) cur = ((ChartDrawing.HorizontalLine)d).label;
            else if (d instanceof ChartDrawing.VerticalLine)   cur = ((ChartDrawing.VerticalLine)d).label;
            else                                                cur = ((ChartDrawing.TextAnnotation)d).text;
            EditText et = new EditText(getContext());
            et.setInputType(InputType.TYPE_CLASS_TEXT);
            et.setText(cur); et.setSelection(et.getText().length());
            et.setTextColor(Color.parseColor("#ECEFF1"));
            et.setHintTextColor(Color.parseColor("#546E7A"));
            et.setHint("Label…");
            et.setBackgroundColor(Color.parseColor("#252323"));
            et.setPadding(dp(10), dp(6), dp(10), dp(6)); et.setTextSize(12f);
            LayoutParams lp = new LayoutParams(MATCH, WRAP); lp.topMargin = dp(4);
            et.setLayoutParams(lp);
            et.addTextChangedListener(new android.text.TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s,int a,int b,int c){}
                @Override public void onTextChanged(CharSequence s,int a,int b,int c){}
                @Override public void afterTextChanged(android.text.Editable s) {
                    String txt = s.toString().trim();
                    if      (d instanceof ChartDrawing.HorizontalLine) ((ChartDrawing.HorizontalLine)d).label = txt;
                    else if (d instanceof ChartDrawing.VerticalLine)   ((ChartDrawing.VerticalLine)d).label   = txt;
                    else                                                ((ChartDrawing.TextAnnotation)d).text   = txt;
                    fire();
                }
            });
            col.addView(et); has = true;
        }

        // Horizontal line price display
        if (d instanceof ChartDrawing.HorizontalLine) {
            col.addView(sp(4));
            col.addView(tv(String.format(Locale.US,"Price: %.5f",
                    ((ChartDrawing.HorizontalLine)d).price), "#78909C", 10f, false));
            has = true;
        }

        // TrendLine extend toggles
        if (d instanceof ChartDrawing.TrendLine) {
            ChartDrawing.TrendLine tl = (ChartDrawing.TrendLine) d;
            col.addView(sectionLabel("EXTEND"));
            LinearLayout r = row(0,dp(4),0,0); r.setGravity(Gravity.CENTER_VERTICAL);
            TextView extL = chip(tl.extendLeft  ? "← Left ON":"← Left",  tl.extendLeft  ?"#26A69A":"#78909C");
            TextView extR = chip(tl.extendRight ? "Right ON →":"Right →", tl.extendRight ?"#26A69A":"#78909C");
            extL.setOnClickListener(v -> { tl.extendLeft = !tl.extendLeft;
                extL.setText(tl.extendLeft?"← Left ON":"← Left");
                extL.setTextColor(Color.parseColor(tl.extendLeft?"#26A69A":"#78909C")); fire(); });
            extR.setOnClickListener(v -> { tl.extendRight = !tl.extendRight;
                extR.setText(tl.extendRight?"Right ON →":"Right →");
                extR.setTextColor(Color.parseColor(tl.extendRight?"#26A69A":"#78909C")); fire(); });
            r.addView(extL); r.addView(sp(10)); r.addView(extR); col.addView(r); has = true;
        }

        // LinReg channel toggle
        if (d instanceof ChartDrawing.LinearRegression) {
            ChartDrawing.LinearRegression lr = (ChartDrawing.LinearRegression) d;

            col.addView(sectionLabel("OPTIONS"));

            TextView ch = chip(
                    lr.drawChannel ? "± " + lr.channelDeviation + "σ Channel ON"
                            : "± " + lr.channelDeviation + "σ Channel",
                    lr.drawChannel ? "#26A69A" : "#78909C"
            );

            col.addView(ch);

            // container for +/- controls
            LinearLayout devControls = new LinearLayout(col.getContext());
            devControls.setOrientation(LinearLayout.HORIZONTAL);

            TextView minus = chip("−1", "#78909C");
            TextView plus  = chip("+1", "#78909C");

            devControls.addView(minus);
            devControls.addView(plus);

            col.addView(devControls);

            updateDevButtons(lr, minus, plus, ch);

            // initial visibility
            devControls.setVisibility(lr.drawChannel ? View.VISIBLE : View.GONE);

            ch.setOnClickListener(v -> {
                lr.drawChannel = !lr.drawChannel;

                ch.setText(lr.drawChannel
                        ? "± " + lr.channelDeviation + "σ Channel ON"
                        : "± " + lr.channelDeviation + "σ Channel");

                ch.setTextColor(Color.parseColor(lr.drawChannel ? "#26A69A" : "#78909C"));

                devControls.setVisibility(lr.drawChannel ? View.VISIBLE : View.GONE);

                fire();
            });

            minus.setOnClickListener(v -> {
                if (lr.channelDeviation <= 1) return;

                lr.channelDeviation = Math.max(1, lr.channelDeviation - 1);
                ch.setText("± " + lr.channelDeviation + "σ Channel ON");

                updateDevButtons(lr, minus, plus, ch);
                fire();
            });

            plus.setOnClickListener(v -> {
                lr.channelDeviation = lr.channelDeviation + 1;
                ch.setText("± " + lr.channelDeviation + "σ Channel ON");
                updateDevButtons(lr, minus, plus, ch);
                fire();
            });

            has = true;
        }

        // Fib info
        if (d instanceof ChartDrawing.FibRetracement) {
            ChartDrawing.FibRetracement f = (ChartDrawing.FibRetracement) d;
            col.addView(sectionLabel("FIB RANGE"));
            col.addView(tv(String.format(Locale.US,"High %.5f  →  Low %.5f",f.highPrice,f.lowPrice),
                    "#ECEFF1", 10f, false));
            col.addView(tv("Levels: " + levelsStr(f.levels), "#78909C", 10f, false)); has = true;
        }

        // Price range info
        if (d instanceof ChartDrawing.PriceRange) {
            ChartDrawing.PriceRange pr = (ChartDrawing.PriceRange) d;
            double delta = Math.abs(pr.priceHigh-pr.priceLow);
            double pct   = pr.priceLow>0 ? delta/pr.priceLow*100 : 0;
            col.addView(sectionLabel("ZONE"));
            col.addView(tv(String.format(Locale.US,"%.5f – %.5f   Δ%.5f (%.2f%%)",
                    pr.priceLow, pr.priceHigh, delta, pct), "#ECEFF1", 10f, false)); has = true;
        }

        return has ? col : null;
    }

    private void updateDevButtons(ChartDrawing.LinearRegression lr,
                                  TextView minus,
                                  TextView plus,
                                  TextView ch) {

        boolean canDecrement = lr.channelDeviation > 1;

        minus.setEnabled(canDecrement);
        minus.setAlpha(canDecrement ? 1f : 0.3f);
    }

    private void fire() {
        if (onChange != null) onChange.onChange();
    }

    // ── View helpers ─────────────────────────────────────────────────
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
        row.setOrientation(HORIZONTAL); row.setPadding(l,t,r,b); return row;
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
    private String levelsStr(float[] lvl) {
        if (lvl==null) return ""; StringBuilder sb=new StringBuilder();
        for (int i=0;i<lvl.length;i++){if(i>0)sb.append(" ");sb.append(String.format(Locale.US,"%.3f",lvl[i]));}
        return sb.toString();
    }
    private String typeName(ChartDrawing d) {
        switch (d.getType()) {
            case HORIZONTAL_LINE:   return "Horizontal Line";
            case TREND_LINE:        return "Trend Line";
            case RAY_LINE:          return "Ray";
            case EXTENDED_LINE:     return "Extended Line";
            case VERTICAL_LINE:     return "Vertical Line";
            case LINEAR_REGRESSION: return "Lin Regression";
            case FIB_RETRACEMENT:   return "Fibonacci";
            case PRICE_RANGE:       return "Price Range";
            case RECTANGLE:         return "Rectangle";
            case ELLIPSE:           return "Ellipse";
            case TEXT_ANNOTATION:   return "Text";
            case ARROW:             return "Arrow";
            case PARALLEL_CHANNEL:  return "Channel";
            case PITCHFORK:         return "Pitchfork";
            case GANN_FAN:          return "Gann Fan";
            default:                return d.getType().name();
        }
    }
    private int dp(int val) {
        return Math.round(val * getContext().getResources().getDisplayMetrics().density);
    }
    private static final int MATCH = ViewGroup.LayoutParams.MATCH_PARENT;
    private static final int WRAP  = ViewGroup.LayoutParams.WRAP_CONTENT;
}