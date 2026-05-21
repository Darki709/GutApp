package com.example.gutapp.ui.fragments;

import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.gutapp.data.drawing.DrawingManager;
import com.example.gutapp.data.drawing.ChartDrawing;
import com.example.gutapp.ui.chart.DrawingChart;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import java.util.Locale;

/**
 * DrawingToolbarFragment — TradingView-style drawing panel.
 *
 * Sections:
 *  1. Mode bar   — Undo · Redo · Snap toggle · Clear selected · Clear all
 *  2. Color row  — 12 preset colors + stroke width controls
 *  3. Tool grid  — All 15 tools in 5 categories
 *  4. Active drawing list (scrollable) — edit style, label, delete each drawing
 */
public class DrawingToolbarFragment extends BottomSheetDialogFragment {

    private DrawingChart drawingChart;
    private LinearLayout drawingsListContainer;
    private TextView snapBtn, undoBtn, redoBtn;

    // Preset colors matching TradingView defaults
    private static final int[] PRESET_COLORS = {
            0xFFECEFF1, // white
            0xFF26A69A, // teal
            0xFF2196F3, // blue
            0xFF4CAF50, // green
            0xFFF44336, // red
            0xFFFF9800, // orange
            0xFFFFEB3B, // yellow
            0xFF9C27B0, // purple
            0xFFE91E63, // pink
            0xFF00BCD4, // cyan
            0xFF795548, // brown
            0xFF607D8B, // steel
    };

    private static final String[] COLOR_NAMES = {
            "White","Teal","Blue","Green","Red","Orange",
            "Yellow","Purple","Pink","Cyan","Brown","Steel"
    };

    private int selectedColorIndex = 0;
    private float selectedWidth = 1f;
    private boolean selectedDashed = false;

    // Tool definitions: {icon, label, tool_enum, description}
    private static final Object[][] TOOLS = {
            // ── Lines ──────────────────────────────────────────────────────
            { "—",  "Horizontal",    DrawingManager.DrawingTool.HORIZONTAL_LINE,   "Fixed price level" },
            { "/",  "Trend Line",    DrawingManager.DrawingTool.TREND_LINE,        "Two-point segment" },
            { "↗",  "Ray",           DrawingManager.DrawingTool.RAY_LINE,          "Extends right to infinity" },
            { "↔",  "Extended",      DrawingManager.DrawingTool.EXTENDED_LINE,     "Extends both directions" },
            { "|",  "Vertical",      DrawingManager.DrawingTool.VERTICAL_LINE,     "Mark a time event" },
            // ── Channels ────────────────────────────────────────────────────
            { "⫴",  "Channel",       DrawingManager.DrawingTool.PARALLEL_CHANNEL,  "Two parallel trend lines" },
            // ── Shapes ──────────────────────────────────────────────────────
            { "▭",  "Rectangle",     DrawingManager.DrawingTool.RECTANGLE,         "Price + time box" },
            { "⬭",  "Ellipse",       DrawingManager.DrawingTool.ELLIPSE,           "Oval zone" },
            { "▤",  "Price Range",   DrawingManager.DrawingTool.PRICE_RANGE,       "Horizontal supply/demand zone" },
            { "📊", "Risk/Reward",   DrawingManager.DrawingTool.RISK_REWARD,       "Long/Short Position tool" },
            // ── Annotations ─────────────────────────────────────────────────
            { "T",  "Text",          DrawingManager.DrawingTool.TEXT_ANNOTATION,   "Tap to place note" },
            { "→",  "Arrow",         DrawingManager.DrawingTool.ARROW,             "Directional arrow" },
            // ── Statistical ─────────────────────────────────────────────────
            { "∿",  "LinReg",        DrawingManager.DrawingTool.LINEAR_REGRESSION, "Best-fit regression line" },
            // ── Fibonacci ───────────────────────────────────────────────────
            { "φ",  "Fibonacci",     DrawingManager.DrawingTool.FIB_RETRACEMENT,   "Fib levels high→low" },
            // ── Advanced ────────────────────────────────────────────────────
            { "⑃",  "Pitchfork",     DrawingManager.DrawingTool.PITCHFORK,         "Andrews Pitchfork (3 points)" },
            { "𝐺",  "Gann Fan",      DrawingManager.DrawingTool.GANN_FAN,          "9 Gann angle lines" },
    };

    public static DrawingToolbarFragment newInstance(DrawingChart chart) {
        DrawingToolbarFragment f = new DrawingToolbarFragment();
        f.drawingChart = chart;
        // Sync initial state from manager
        f.selectedColorIndex = 0;
        f.selectedWidth = chart.getDrawingManager().getActiveWidth();
        f.selectedDashed = chart.getDrawingManager().isActiveDashed();
        return f;
    }

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return buildView();
    }

    private View buildView() {
        ScrollView root = new ScrollView(requireContext());
        root.setBackgroundColor(Color.parseColor("#161414"));

        LinearLayout main = new LinearLayout(requireContext());
        main.setOrientation(LinearLayout.VERTICAL);
        main.setPadding(0, dp(8), 0, dp(32));
        root.addView(main);

        // Drag handle
        View handle = new View(requireContext());
        handle.setBackgroundColor(Color.parseColor("#3A3838"));
        LinearLayout.LayoutParams hlp = new LinearLayout.LayoutParams(dp(40), dp(4));
        hlp.gravity = android.view.Gravity.CENTER_HORIZONTAL;
        hlp.topMargin = dp(6); hlp.bottomMargin = dp(10);
        handle.setLayoutParams(hlp);
        main.addView(handle);

        // ── Title + mode buttons ────────────────────────────────────
        main.addView(buildModeBar());
        main.addView(divider());

        // ── Color + stroke controls ─────────────────────────────────
        main.addView(buildColorRow());
        main.addView(divider());

        // ── Tool grid ───────────────────────────────────────────────
        main.addView(sectionLabel("DRAWING TOOLS"));
        main.addView(buildToolGrid());
        main.addView(divider());

        // ── Active drawings list ────────────────────────────────────
        main.addView(sectionLabel("ON CHART"));
        drawingsListContainer = new LinearLayout(requireContext());
        drawingsListContainer.setOrientation(LinearLayout.VERTICAL);
        main.addView(drawingsListContainer);
        refreshDrawingsList();

        return root;
    }

    // ── Mode bar (Undo / Redo / Snap / Clear) ──────────────────────

    private View buildModeBar() {
        LinearLayout row = new LinearLayout(requireContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        row.setPadding(dp(12), dp(8), dp(12), dp(8));

        // Title
        TextView title = tv("✏ Drawing Studio", "#ECEFF1", 15f, true);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(0, wc(), 1f);
        title.setLayoutParams(tlp);
        row.addView(title);

        // Undo
        undoBtn = chipBtn("↩ Undo", "#78909C");
        undoBtn.setOnClickListener(v -> {
            if (drawingChart.undo()) refreshAll();
            else Toast.makeText(requireContext(),"Nothing to undo",Toast.LENGTH_SHORT).show();
        });
        row.addView(undoBtn);
        row.addView(spacer(6));

        // Redo
        redoBtn = chipBtn("↪ Redo", "#78909C");
        redoBtn.setOnClickListener(v -> {
            if (drawingChart.redo()) refreshAll();
            else Toast.makeText(requireContext(),"Nothing to redo",Toast.LENGTH_SHORT).show();
        });
        row.addView(redoBtn);
        row.addView(spacer(6));

        // Snap toggle
        boolean snapping = drawingChart.isSnapEnabled();
        snapBtn = chipBtn(snapping ? "⊙ Snap ON" : "○ Snap", snapping ? "#26A69A" : "#546E7A");
        snapBtn.setOnClickListener(v -> {
            boolean now = !drawingChart.isSnapEnabled();
            drawingChart.setSnapEnabled(now);
            snapBtn.setText(now ? "⊙ Snap ON" : "○ Snap");
            snapBtn.setTextColor(Color.parseColor(now ? "#26A69A" : "#546E7A"));
        });
        row.addView(snapBtn);

        return row;
    }

    // ── Color row ────────────────────────────────────────────────────

    private View buildColorRow() {
        LinearLayout col = new LinearLayout(requireContext());
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(12), dp(8), dp(12), dp(8));

        // Label
        col.addView(sectionLabel("COLOR & STYLE"));

        // Color swatches (horizontal scroll)
        HorizontalScrollView hsv = new HorizontalScrollView(requireContext());
        hsv.setHorizontalScrollBarEnabled(false);
        LinearLayout swatchRow = new LinearLayout(requireContext());
        swatchRow.setOrientation(LinearLayout.HORIZONTAL);
        swatchRow.setGravity(android.view.Gravity.CENTER_VERTICAL);
        swatchRow.setPadding(0, dp(6), 0, dp(6));

        for (int i = 0; i < PRESET_COLORS.length; i++) {
            final int idx = i;
            View swatch = new View(requireContext());
            int size = i == selectedColorIndex ? dp(28) : dp(22);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(size, size);
            lp.setMarginEnd(dp(8));
            swatch.setLayoutParams(lp);
            swatch.setBackgroundColor(PRESET_COLORS[i]);
            if (i == selectedColorIndex) {
                // Selected ring
                android.graphics.drawable.GradientDrawable ring =
                        new android.graphics.drawable.GradientDrawable();
                ring.setShape(android.graphics.drawable.GradientDrawable.OVAL);
                ring.setColor(PRESET_COLORS[i]);
                ring.setStroke(dp(2), Color.WHITE);
                swatch.setBackground(ring);
            }
            swatch.setOnClickListener(v -> {
                selectedColorIndex = idx;
                applyActiveStyle();
                refreshColorRow(swatchRow);
            });
            swatchRow.addView(swatch);
        }
        hsv.addView(swatchRow);
        col.addView(hsv);

        // Stroke width + dashed controls
        LinearLayout styleRow = new LinearLayout(requireContext());
        styleRow.setOrientation(LinearLayout.HORIZONTAL);
        styleRow.setGravity(android.view.Gravity.CENTER_VERTICAL);
        styleRow.setPadding(0, dp(4), 0, 0);

        styleRow.addView(tv("Width:", "#546E7A", 11f, false));
        styleRow.addView(spacer(8));

        for (float w : new float[]{1f, 2f, 3f, 4f}) {
            TextView wBtn = chipBtn(w == Math.floor(w) ? (int)w+"px" : w+"px", "#78909C");
            if (w == selectedWidth) { wBtn.setBackgroundResource(com.example.gutapp.R.drawable.chart_btn_active); wBtn.setTextColor(Color.WHITE); }
            wBtn.setOnClickListener(v -> { selectedWidth = w; applyActiveStyle(); refreshAll(); refreshWidthRow(styleRow); });
            styleRow.addView(wBtn); styleRow.addView(spacer(4));
        }

        styleRow.addView(spacer(12));
        TextView dashToggle = chipBtn(selectedDashed ? "─ ─ Dashed" : "─── Solid", selectedDashed ? "#26A69A" : "#78909C");
        dashToggle.setOnClickListener(v -> {
            selectedDashed = !selectedDashed;
            dashToggle.setText(selectedDashed ? "─ ─ Dashed" : "─── Solid");
            dashToggle.setTextColor(Color.parseColor(selectedDashed ? "#26A69A" : "#78909C"));
            applyActiveStyle();
        });
        styleRow.addView(dashToggle);

        col.addView(styleRow);
        return col;
    }

    private void refreshWidthRow(LinearLayout styleRow){
        float[] widths = {1f, 2f, 3f, 4f};
        for(int i = 2; i <= 8; i +=2){
            TextView wBtn = (TextView) styleRow.getChildAt(i);
            if (widths[(i-2)/2] == selectedWidth) { wBtn.setBackgroundResource(com.example.gutapp.R.drawable.chart_btn_active); wBtn.setTextColor(Color.WHITE); }
            else { wBtn.setBackgroundResource(0); wBtn.setTextColor(Color.parseColor("#78909C")); }
        }
    }

    private void refreshColorRow(LinearLayout swatchRow) {
        for (int i = 0; i < swatchRow.getChildCount(); i++) {
            View swatch = swatchRow.getChildAt(i);
            int size = i == selectedColorIndex ? dp(28) : dp(22);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(size, size);
            lp.setMarginEnd(dp(8));
            swatch.setLayoutParams(lp);
            if (i == selectedColorIndex) {
                android.graphics.drawable.GradientDrawable ring =
                        new android.graphics.drawable.GradientDrawable();
                ring.setShape(android.graphics.drawable.GradientDrawable.OVAL);
                ring.setColor(PRESET_COLORS[i]);
                ring.setStroke(dp(2), Color.WHITE);
                swatch.setBackground(ring);
            } else {
                swatch.setBackgroundColor(PRESET_COLORS[i]);
            }
        }
    }

    private void applyActiveStyle() {
        drawingChart.getDrawingManager().setActiveColor(PRESET_COLORS[selectedColorIndex]);
        drawingChart.getDrawingManager().setActiveWidth(selectedWidth);
        drawingChart.getDrawingManager().setActiveDashed(selectedDashed);
    }

    // ── Tool grid ────────────────────────────────────────────────────

    private View buildToolGrid() {
        LinearLayout grid = new LinearLayout(requireContext());
        grid.setOrientation(LinearLayout.VERTICAL);
        grid.setPadding(dp(12), dp(4), dp(12), dp(4));

        int cols = 3;
        LinearLayout row = null;
        for (int i = 0; i < TOOLS.length; i++) {
            if (i % cols == 0) {
                row = new LinearLayout(requireContext());
                row.setOrientation(LinearLayout.HORIZONTAL);
                LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(mp(), wc());
                rlp.bottomMargin = dp(6);
                row.setLayoutParams(rlp);
                grid.addView(row);
            }

            String icon  = (String)  TOOLS[i][0];
            String label = (String)  TOOLS[i][1];
            DrawingManager.DrawingTool tool = (DrawingManager.DrawingTool) TOOLS[i][2];
            String desc  = (String)  TOOLS[i][3];

            boolean isActive = drawingChart.getDrawingManager().getActiveTool() == tool;

            LinearLayout card = new LinearLayout(requireContext());
            card.setOrientation(LinearLayout.VERTICAL);
            card.setGravity(android.view.Gravity.CENTER);
            card.setPadding(dp(6), dp(10), dp(6), dp(10));
            card.setBackgroundColor(isActive
                    ? Color.parseColor("#1B3A2E")
                    : Color.parseColor("#1E1C1C"));
            LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(0, wc(), 1f);
            clp.setMarginEnd(i % cols < cols-1 ? dp(6) : 0);
            card.setLayoutParams(clp);

            TextView iconTv = tv(icon, isActive ? "#26A69A" : "#ECEFF1", 20f, true);
            iconTv.setGravity(android.view.Gravity.CENTER);
            card.addView(iconTv);

            TextView labelTv = tv(label, isActive ? "#26A69A" : "#78909C", 10f, false);
            labelTv.setGravity(android.view.Gravity.CENTER);
            LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(mp(), wc());
            llp.topMargin = dp(3);
            labelTv.setLayoutParams(llp);
            card.addView(labelTv);

            card.setOnClickListener(v -> {
                DrawingManager.DrawingTool cur = drawingChart.getDrawingManager().getActiveTool();
                if (cur == tool) {
                    drawingChart.setActiveTool(null);
                    Toast.makeText(requireContext(), "Pan/Zoom mode", Toast.LENGTH_SHORT).show();
                } else {
                    drawingChart.setActiveTool(tool);
                    Toast.makeText(requireContext(), label + ": " + desc, Toast.LENGTH_SHORT).show();
                }
                dismiss();
            });

            card.setOnLongClickListener(v -> {
                new AlertDialog.Builder(requireContext())
                        .setTitle(label)
                        .setMessage(desc)
                        .setPositiveButton("Select", (d,w) -> { card.callOnClick(); })
                        .setNegativeButton("Cancel", null)
                        .show();
                return true;
            });

            if (row != null) row.addView(card);
        }

        // Pad last row if needed
        if (TOOLS.length % cols != 0 && row != null) {
            int empty = cols - (TOOLS.length % cols);
            for (int i = 0; i < empty; i++) {
                View ph = new View(requireContext());
                ph.setLayoutParams(new LinearLayout.LayoutParams(0, dp(1), 1f));
                row.addView(ph);
            }
        }

        // Done / Clear row
        LinearLayout actionRow = new LinearLayout(requireContext());
        actionRow.setOrientation(LinearLayout.HORIZONTAL);
        actionRow.setPadding(0, dp(8), 0, 0);

        TextView doneBtn = chipBtn("✓ Pan/Zoom Mode", "#26A69A");
        doneBtn.setLayoutParams(new LinearLayout.LayoutParams(0, wc(), 1f));
        doneBtn.setOnClickListener(v -> {
            drawingChart.setActiveTool(null);
            dismiss();
        });
        actionRow.addView(doneBtn);
        actionRow.addView(spacer(8));

        TextView clearSelBtn = chipBtn("✕ Del Selected", "#EF5350");
        clearSelBtn.setOnClickListener(v -> {
            String sel = drawingChart.getDrawingManager().getSelectedId();
            if (sel != null) {
                drawingChart.removeDrawing(sel);
                refreshDrawingsList();
                Toast.makeText(requireContext(), "Drawing deleted", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(requireContext(), "Nothing selected", Toast.LENGTH_SHORT).show();
            }
        });
        actionRow.addView(clearSelBtn);
        actionRow.addView(spacer(8));

        TextView clearAllBtn = chipBtn("⊘ Clear All", "#B71C1C");
        clearAllBtn.setOnClickListener(v -> new AlertDialog.Builder(requireContext())
                .setTitle("Clear All Drawings")
                .setMessage("Remove all user drawings from this chart?")
                .setPositiveButton("Clear", (d, w) -> {
                    // clearAllUserDrawings() fires onDrawingsChanged → auto-saves empty state
                    drawingChart.clearAllUserDrawings();
                    refreshDrawingsList();
                })
                .setNegativeButton("Cancel", null).show());
        actionRow.addView(clearAllBtn);

        grid.addView(actionRow);
        return grid;
    }

    // ── Active drawings list ──────────────────────────────────────────

    private void refreshDrawingsList() {
        if (drawingsListContainer == null) return;
        drawingsListContainer.removeAllViews();

        java.util.List<ChartDrawing> userDrawings =
                drawingChart.getDrawingManager().getUserDrawings();

        if (userDrawings.isEmpty()) {
            TextView empty = tv("No drawings yet. Select a tool above, then tap the chart.",
                    "#546E7A", 12f, false);
            empty.setPadding(dp(16), dp(12), dp(16), dp(12));
            empty.setGravity(android.view.Gravity.CENTER);
            drawingsListContainer.addView(empty);
            return;
        }

        for (ChartDrawing d : userDrawings) {
            drawingsListContainer.addView(buildDrawingRow(d));
        }
    }

    private View buildDrawingRow(ChartDrawing d) {
        LinearLayout row = new LinearLayout(requireContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        row.setPadding(dp(16), dp(10), dp(12), dp(10));
        row.setBackgroundColor(d.selected
                ? Color.parseColor("#1B3A2E") : Color.parseColor("#1A1818"));

        // Color swatch
        View swatch = new View(requireContext());
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(dp(14), dp(14));
        slp.setMarginEnd(dp(10));
        swatch.setLayoutParams(slp);
        swatch.setBackgroundColor(d.style != null ? d.style.color : Color.WHITE);
        swatch.setOnClickListener(v -> openColorPicker(d));
        row.addView(swatch);

        // Name
        TextView name = tv(describeDrawing(d), "#ECEFF1", 12f, false);
        name.setLayoutParams(new LinearLayout.LayoutParams(0, wc(), 1f));
        name.setOnClickListener(v -> {
            drawingChart.getDrawingManager().select(d.getInstanceId());
            drawingChart.postInvalidate();
            refreshDrawingsList();
        });
        row.addView(name);

        // Layer toggle (above/behind candles)
        boolean isAbove = d.layer == ChartDrawing.Layer.ABOVE_CANDLES;
        TextView layerBtn = chipBtn(isAbove ? "▲ Front" : "▼ Back", isAbove ? "#26A69A" : "#546E7A");
        layerBtn.setOnClickListener(v -> {
            d.layer = d.layer == ChartDrawing.Layer.BEHIND_CANDLES
                    ? ChartDrawing.Layer.ABOVE_CANDLES : ChartDrawing.Layer.BEHIND_CANDLES;
            boolean above = d.layer == ChartDrawing.Layer.ABOVE_CANDLES;
            layerBtn.setText(above ? "▲ Front" : "▼ Back");
            layerBtn.setTextColor(android.graphics.Color.parseColor(above ? "#26A69A" : "#546E7A"));
            drawingChart.postInvalidate();
            notifyChanged();
        });
        row.addView(layerBtn);
        row.addView(spacer(4));
        TextView dash = chipBtn(d.style.dashed ? "- -" : "───", d.style.dashed ? "#26A69A" : "#546E7A");
        dash.setOnClickListener(v -> {
            d.style.dashed = !d.style.dashed;
            dash.setText(d.style.dashed ? "- -" : "───");
            dash.setTextColor(android.graphics.Color.parseColor(d.style.dashed ? "#26A69A" : "#546E7A"));
            drawingChart.postInvalidate();
            notifyChanged();
        });
        row.addView(dash);
        row.addView(spacer(4));

        // Width +/-
        TextView wp = chipBtn("+", "#78909C");
        wp.setOnClickListener(v -> {
            d.style.strokeWidth = Math.min(8f, d.style.strokeWidth + 0.5f);
            drawingChart.postInvalidate();
            notifyChanged();
        });
        row.addView(wp);
        row.addView(spacer(2));

        TextView wm = chipBtn("−", "#78909C");
        wm.setOnClickListener(v -> {
            d.style.strokeWidth = Math.max(0.5f, d.style.strokeWidth - 0.5f);
            drawingChart.postInvalidate();
            notifyChanged();
        });
        row.addView(wm);
        row.addView(spacer(8));

    // Label edit (for HLine, VLine, Text)
        if (d instanceof ChartDrawing.HorizontalLine ||
    d instanceof ChartDrawing.VerticalLine   ||
    d instanceof ChartDrawing.TextAnnotation ||
        d instanceof ChartDrawing.RiskReward) {
        TextView editBtn = chipBtn("✎", "#78909C");
        editBtn.setOnClickListener(v -> openLabelEditor(d));
        row.addView(editBtn);
        row.addView(spacer(4));
    }

    // Delete
    TextView del = tv("✕", "#EF5350", 14f, true);
        del.setPadding(dp(8), dp(4), dp(4), dp(4));
        del.setOnClickListener(v -> {
        drawingChart.removeDrawing(d.getInstanceId());
        refreshDrawingsList();
    });
        row.addView(del);

    // Bottom divider
    LinearLayout wrapper = new LinearLayout(requireContext());
        wrapper.setOrientation(LinearLayout.VERTICAL);
        wrapper.addView(row);
        wrapper.addView(divider());
        return wrapper;
}

private String describeDrawing(ChartDrawing d) {
    switch (d.getType()) {
        case HORIZONTAL_LINE: {
            ChartDrawing.HorizontalLine h = (ChartDrawing.HorizontalLine) d;
            String lbl = h.label != null && !h.label.isEmpty() ? " \""+h.label+"\"" : "";
            return String.format(Locale.US, "H Line  %.5f%s", h.price, lbl);
        }
        case TREND_LINE:      return "Trend Line";
        case RAY_LINE:        return "Ray Line ↗";
        case EXTENDED_LINE:   return "Extended Line ↔";
        case VERTICAL_LINE: {
            ChartDrawing.VerticalLine v = (ChartDrawing.VerticalLine) d;
            return "V Line  idx " + v.candleTs;
        }
        case LINEAR_REGRESSION: return "Lin Regression";
        case FIB_RETRACEMENT: {
            ChartDrawing.FibRetracement f = (ChartDrawing.FibRetracement) d;
            return String.format(Locale.US, "Fibonacci  %.4f–%.4f", f.highPrice, f.lowPrice);
        }
        case PRICE_RANGE: {
            ChartDrawing.PriceRange pr = (ChartDrawing.PriceRange) d;
            return String.format(Locale.US, "Zone  %.4f–%.4f", pr.priceLow, pr.priceHigh);
        }
        case RECTANGLE:       return "Rectangle";
        case ELLIPSE:         return "Ellipse";
        case TEXT_ANNOTATION: return "\"" + ((ChartDrawing.TextAnnotation)d).text + "\"";
        case ARROW:           return "Arrow →";
        case PARALLEL_CHANNEL:return "Channel";
        case PITCHFORK:       return "Pitchfork";
        case GANN_FAN:        return "Gann Fan";
        case RISK_REWARD: {
            ChartDrawing.RiskReward rr = (ChartDrawing.RiskReward) d;
            double reward = Math.abs(rr.targetPrice - rr.entryPrice);
            double risk = Math.abs(rr.entryPrice - rr.stopPrice);
            double ratio = risk == 0 ? 0 : reward / risk;
            return String.format(Locale.US, "R:R Tool (Ratio: %.2f)", ratio);
        }
        default:              return d.getType().name();
    }
}

private void openColorPicker(ChartDrawing d) {
    LinearLayout grid = new LinearLayout(requireContext());
    grid.setOrientation(LinearLayout.HORIZONTAL);
    grid.setPadding(dp(16),dp(16),dp(16),dp(8));

    AlertDialog[] holder = {null};
    for (int i = 0; i < PRESET_COLORS.length; i++) {
        final int color = PRESET_COLORS[i];
        View sw = new View(requireContext());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(30), dp(30));
        lp.setMarginEnd(dp(8));
        sw.setLayoutParams(lp);
        sw.setBackgroundColor(color);
        sw.setOnClickListener(v -> {
            if (d.style != null) {
                d.style.color = color;
                d.style.fillColor = Color.argb(50,
                        Color.red(color), Color.green(color), Color.blue(color));
            }
            drawingChart.postInvalidate();
            refreshDrawingsList();
            notifyChanged();
            if (holder[0] != null) holder[0].dismiss();
        });
        grid.addView(sw);
    }

    holder[0] = new AlertDialog.Builder(requireContext())
            .setTitle("Pick Color")
            .setView(grid)
            .setNegativeButton("Cancel", null)
            .show();
}

private void openLabelEditor(ChartDrawing d) {

    if (d instanceof ChartDrawing.RiskReward) {
        ChartDrawing.RiskReward rr = (ChartDrawing.RiskReward) d;

        // Root container for fields
        LinearLayout form = new LinearLayout(requireContext());
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(16), dp(10), dp(16), dp(10));
        form.setBackgroundColor(Color.parseColor("#161414"));

        // Helper to build numeric entry fields
        EditText entryInput  = buildNumericField("Entry Price", String.valueOf(rr.entryPrice), form);
        EditText targetInput = buildNumericField("Target Price", String.valueOf(rr.targetPrice), form);
        EditText stopInput   = buildNumericField("Stop Loss Price", String.valueOf(rr.stopPrice), form);

        new AlertDialog.Builder(requireContext())
                .setTitle("Adjust Coordinates")
                .setView(form)
                .setPositiveButton("Save", (dial, w) -> {
                    try {
                        rr.entryPrice  = Double.parseDouble(entryInput.getText().toString().trim());
                        rr.targetPrice = Double.parseDouble(targetInput.getText().toString().trim());
                        rr.stopPrice   = Double.parseDouble(stopInput.getText().toString().trim());

                        drawingChart.postInvalidate();
                        refreshDrawingsList();
                        notifyChanged();
                    } catch (NumberFormatException e) {
                        Toast.makeText(requireContext(), "Invalid number entry", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
        return;
    }

    EditText input = new EditText(requireContext());
    input.setInputType(InputType.TYPE_CLASS_TEXT);
    input.setTextColor(Color.parseColor("#ECEFF1"));
    input.setHintTextColor(Color.parseColor("#546E7A"));
    input.setBackgroundColor(Color.parseColor("#252323"));
    input.setPadding(dp(14),dp(10),dp(14),dp(10));

    String cur = "";
    if (d instanceof ChartDrawing.HorizontalLine) cur = ((ChartDrawing.HorizontalLine)d).label;
    else if (d instanceof ChartDrawing.VerticalLine) cur = ((ChartDrawing.VerticalLine)d).label;
    else if (d instanceof ChartDrawing.TextAnnotation) cur = ((ChartDrawing.TextAnnotation)d).text;
    input.setText(cur);
    input.setSelection(input.getText().length());

    new AlertDialog.Builder(requireContext())
            .setTitle("Edit Label")
            .setView(input)
            .setPositiveButton("OK", (dial, w) -> {
                String txt = input.getText().toString().trim();
                if (d instanceof ChartDrawing.HorizontalLine)
                    ((ChartDrawing.HorizontalLine)d).label = txt;
                else if (d instanceof ChartDrawing.VerticalLine)
                    ((ChartDrawing.VerticalLine)d).label = txt;
                else if (d instanceof ChartDrawing.TextAnnotation)
                    ((ChartDrawing.TextAnnotation)d).text = txt;
                drawingChart.postInvalidate();
                refreshDrawingsList();
                notifyChanged();
            })
            .setNegativeButton("Cancel", null)
            .show();
}

    // Small helper method to keep layout rendering clean
    private EditText buildNumericField(String hint, String initialValue, LinearLayout parent) {
        TextView label = new TextView(requireContext());
        label.setText(hint);
        label.setTextColor(Color.parseColor("#546E7A"));
        label.setTextSize(11f);
        label.setPadding(0, dp(6), 0, dp(2));
        parent.addView(label);

        EditText input = new EditText(requireContext());
        input.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        input.setTextColor(Color.parseColor("#ECEFF1"));
        input.setBackgroundColor(Color.parseColor("#252323"));
        input.setPadding(dp(10), dp(8), dp(10), dp(8));
        input.setText(initialValue);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(mp(), wc());
        lp.bottomMargin = dp(8);
        input.setLayoutParams(lp);

        parent.addView(input);
        return input;
    }

private void notifyChanged() {
    // Trigger auto-save via the chart's DrawingEventListener
    if (drawingChart != null) drawingChart.postInvalidate();
}

private void refreshAll() {
    drawingChart.postInvalidate();
    refreshDrawingsList();
}

// ── View helpers ─────────────────────────────────────────────────

private TextView tv(String text, String color, float size, boolean bold) {
    TextView t = new TextView(requireContext());
    t.setText(text); t.setTextColor(Color.parseColor(color)); t.setTextSize(size);
    if (bold) t.setTypeface(null, Typeface.BOLD);
    return t;
}

private TextView chipBtn(String text, String color) {
    TextView t = new TextView(requireContext());
    t.setText(text); t.setTextColor(Color.parseColor(color));
    t.setTextSize(10f); t.setGravity(android.view.Gravity.CENTER);
    t.setPadding(dp(8), dp(5), dp(8), dp(5));
    try { t.setBackgroundResource(com.example.gutapp.R.drawable.chart_btn_inactive); }
    catch (Exception ignored) {}
    return t;
}

private View divider() {
    View v = new View(requireContext());
    v.setBackgroundColor(Color.parseColor("#252323"));
    v.setLayoutParams(new LinearLayout.LayoutParams(mp(), 1));
    return v;
}

private View spacer(int dp) {
    View v = new View(requireContext());
    v.setLayoutParams(new LinearLayout.LayoutParams(dp(dp), 1));
    return v;
}

private TextView sectionLabel(String text) {
    TextView t = tv(text, "#546E7A", 10f, true);
    t.setLetterSpacing(0.12f);
    t.setPadding(dp(16), dp(10), dp(16), dp(4));
    return t;
}

private int dp(int val) {
    return Math.round(val * requireContext().getResources().getDisplayMetrics().density);
}
private int mp() { return ViewGroup.LayoutParams.MATCH_PARENT; }
private int wc() { return ViewGroup.LayoutParams.WRAP_CONTENT; }
}