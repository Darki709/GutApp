// ═══════════════════════════════════════════════════════════════════
// DrawingToolbarFragment — a ready-to-use drawing tools bottom sheet
// ═══════════════════════════════════════════════════════════════════
package com.example.gutapp.ui.fragments;

import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.gutapp.data.drawing.DrawingManager;
import com.example.gutapp.data.drawing.ChartDrawing;
import com.example.gutapp.ui.chart.DrawingChart;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

/**
 * DrawingToolbarFragment — bottom sheet for selecting a drawing tool.
 */
public class DrawingToolbarFragment extends BottomSheetDialogFragment {

    private DrawingChart drawingChart;

    public static DrawingToolbarFragment newInstance(DrawingChart chart) {
        DrawingToolbarFragment f = new DrawingToolbarFragment();
        f.drawingChart = chart;
        return f;
    }

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return buildView(requireContext());
    }

    private View buildView(android.content.Context ctx) {
        int pad = dp(ctx, 16);
        android.widget.ScrollView root = new android.widget.ScrollView(ctx);
        root.setBackgroundColor(Color.parseColor("#1A1818"));

        LinearLayout main = new LinearLayout(ctx);
        main.setOrientation(LinearLayout.VERTICAL);
        main.setPadding(pad, pad, pad, dp(ctx, 32));
        root.addView(main);

        // Title
        TextView title = makeText(ctx, "Drawing Tools", "#ECEFF1", 16f, true);
        title.setPadding(0, 0, 0, dp(ctx, 12));
        main.addView(title);
        main.addView(makeDivider(ctx));

        // Tool rows
        addToolRow(ctx, main, "—  Horizontal Line",    DrawingManager.DrawingTool.HORIZONTAL_LINE,
                "Mark support / resistance at a fixed price");
        addToolRow(ctx, main, "/  Trend Line",          DrawingManager.DrawingTool.TREND_LINE,
                "Connect two price points with a segment");
        addToolRow(ctx, main, "↗  Ray Line",            DrawingManager.DrawingTool.RAY_LINE,
                "Trend line that extends to the right edge");
        addToolRow(ctx, main, "|  Vertical Line",       DrawingManager.DrawingTool.VERTICAL_LINE,
                "Mark a point in time");
        addToolRow(ctx, main, "∿  Linear Regression",  DrawingManager.DrawingTool.LINEAR_REGRESSION,
                "Drag to select a candle range — draws best-fit line");
        addToolRow(ctx, main, "φ  Fibonacci",           DrawingManager.DrawingTool.FIB_RETRACEMENT,
                "Drag from swing high to swing low");
        addToolRow(ctx, main, "□  Price Range",         DrawingManager.DrawingTool.PRICE_RANGE,
                "Shade a supply / demand zone");

        main.addView(makeSpacing(ctx, 12));
        main.addView(makeDivider(ctx));

        // Clear user drawings
        TextView clearBtn = makeText(ctx, "✕  Clear All Drawings", "#EF5350", 13f, false);
        clearBtn.setPadding(0, dp(ctx, 12), 0, dp(ctx, 4));
        clearBtn.setOnClickListener(v -> {
            drawingChart.getDrawingManager().clearUserDrawings();
            drawingChart.postInvalidate();
            dismiss();
        });
        main.addView(clearBtn);

        // Deactivate / back to pan-zoom
        TextView cancelBtn = makeText(ctx, "✓  Back to Pan / Zoom", "#26A69A", 13f, false);
        cancelBtn.setPadding(0, dp(ctx, 8), 0, dp(ctx, 4));
        cancelBtn.setOnClickListener(v -> {
            drawingChart.getDrawingManager().setActiveTool(null);
            dismiss();
        });
        main.addView(cancelBtn);

        return root;
    }

    private void addToolRow(android.content.Context ctx, LinearLayout parent,
                            String icon, DrawingManager.DrawingTool tool, String description) {
        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(0, dp(ctx, 8), 0, dp(ctx, 8));
        row.setOnClickListener(v -> {
            drawingChart.getDrawingManager().setActiveTool(tool);
            dismiss();
        });

        TextView name = makeText(ctx, icon, "#ECEFF1", 13f, false);
        row.addView(name);

        TextView desc = makeText(ctx, description, "#546E7A", 11f, false);
        LinearLayout.LayoutParams dp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        dp.topMargin = dpI(ctx, 2);
        desc.setLayoutParams(dp);
        row.addView(desc);

        parent.addView(row);
        parent.addView(makeDivider(ctx));
    }

    private TextView makeText(android.content.Context ctx, String text,
                              String color, float size, boolean bold) {
        TextView tv = new TextView(ctx);
        tv.setText(text);
        tv.setTextColor(Color.parseColor(color));
        tv.setTextSize(size);
        if (bold) tv.setTypeface(null, android.graphics.Typeface.BOLD);
        return tv;
    }

    private View makeDivider(android.content.Context ctx) {
        View v = new View(ctx);
        v.setBackgroundColor(Color.parseColor("#2A2828"));
        v.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 1));
        return v;
    }

    private View makeSpacing(android.content.Context ctx, int dpVal) {
        View v = new View(ctx);
        v.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dpI(ctx, dpVal)));
        return v;
    }

    private int dpI(android.content.Context ctx, int val) {
        return Math.round(val * ctx.getResources().getDisplayMetrics().density);
    }

    private int dp(android.content.Context ctx, int val) { return dpI(ctx, val); }
}