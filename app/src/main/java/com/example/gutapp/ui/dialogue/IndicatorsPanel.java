package com.example.gutapp.ui.dialogue;

import android.graphics.Color;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.gutapp.R;
import com.example.gutapp.data.indicators.Indicator;
import com.example.gutapp.data.indicators.IndicatorRegistry;
import com.example.gutapp.data.indicators.IndicatorSession;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import java.util.List;
import java.util.Locale;

/**
 * IndicatorsPanel — fully rewritten for multi-instance support.
 *
 * Layout (programmatic, no extra XML needed):
 *  ┌──────────────────────────────────────┐
 *  │  Indicators            [Clear All]   │
 *  │  ── ACTIVE ────────────────────────  │
 *  │  [MA(20) ●amber   ✎ params  🗑]      │  ← each active instance
 *  │  [RSI(14) ●purple ✎ params  🗑]      │
 *  │  ── ADD INDICATOR ─────────────────  │
 *  │  🔍 [search bar]                     │
 *  │  MA  Moving Average          [+]     │  ← type catalog rows
 *  │  EMA Exponential MA          [+]     │
 *  │  ...                                 │
 *  └──────────────────────────────────────┘
 *
 * When user taps ✎ on an active instance, an inline param editor expands.
 * When user taps [+] on a type, a new instance is added to the session.
 * Color picker: tapping the color dot shows a simple preset-color chooser.
 */
public class IndicatorsPanel extends BottomSheetDialogFragment {

    public interface IndicatorListener {
        void onIndicatorsChanged();
    }

    // Preset colors for the color picker (12 options)
    private static final int[] PRESET_COLORS = {
            Color.parseColor("#FFC107"), Color.parseColor("#E91E63"),
            Color.parseColor("#2196F3"), Color.parseColor("#4CAF50"),
            Color.parseColor("#FF5722"), Color.parseColor("#9C27B0"),
            Color.parseColor("#00BCD4"), Color.parseColor("#FF9800"),
            Color.parseColor("#CDDC39"), Color.parseColor("#F44336"),
            Color.parseColor("#03A9F4"), Color.parseColor("#8BC34A"),
    };

    protected IndicatorSession session;
    private IndicatorListener listener;
    private android.widget.LinearLayout activeContainer;
    private android.widget.LinearLayout catalogContainer;
    private String searchQuery = "";

    public static IndicatorsPanel newInstance(IndicatorSession session) {
        IndicatorsPanel f = new IndicatorsPanel();
        f.session = session;
        return f;
    }

    public void setListener(IndicatorListener l) { this.listener = l; }

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_indicators_panel, container, false);

        activeContainer = v.findViewById(R.id.activeContainer);
        catalogContainer = v.findViewById(R.id.catalogContainer);
        EditText searchBar = v.findViewById(R.id.searchBar);

        v.findViewById(R.id.btnClearAll).setOnClickListener(view -> {
            session.clearAll();
            rebuildActiveList(requireContext());
            notifyListener();
        });

        searchBar.addTextChangedListener(new TextWatcher() {
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {
                searchQuery = s.toString().trim().toLowerCase();
                rebuildCatalog(requireContext());
            }
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void afterTextChanged(Editable s) {}
        });

        rebuildActiveList(requireContext());
        rebuildCatalog(requireContext());
        return v;
    }

    // ── Root layout ───────────────────────────────────────────────────
    private View buildView(android.content.Context ctx) {
        android.widget.ScrollView root = new android.widget.ScrollView(ctx);
        root.setBackgroundColor(Color.parseColor("#1A1818"));
        root.setFillViewport(true);

        android.widget.LinearLayout main = new android.widget.LinearLayout(ctx);
        main.setOrientation(android.widget.LinearLayout.VERTICAL);
        main.setPadding(dp(ctx,16), dp(ctx,16), dp(ctx,16), dp(ctx,40));
        root.addView(main);

        // Title + Clear All
        main.addView(buildTitleRow(ctx));
        main.addView(makeDivider(ctx));

        // ── ACTIVE INSTANCES section ───────────────────────────────
        addSectionHeader(ctx, main, "Active Indicators");
        activeContainer = new android.widget.LinearLayout(ctx);
        activeContainer.setOrientation(android.widget.LinearLayout.VERTICAL);
        main.addView(activeContainer);
        rebuildActiveList(ctx);

        main.addView(makeSpacing(ctx, 12));
        main.addView(makeDivider(ctx));

        // ── ADD INDICATOR section ──────────────────────────────────
        addSectionHeader(ctx, main, "Add Indicator");

        // Search bar
        EditText searchBar = new EditText(ctx);
        searchBar.setHint("Search indicators…");
        searchBar.setHintTextColor(Color.parseColor("#546E7A"));
        searchBar.setTextColor(Color.parseColor("#ECEFF1"));
        searchBar.setTextSize(13f);
        searchBar.setBackgroundColor(Color.parseColor("#252323"));
        searchBar.setPadding(dp(ctx,10), dp(ctx,8), dp(ctx,10), dp(ctx,8));
        android.widget.LinearLayout.LayoutParams sbP =
                new android.widget.LinearLayout.LayoutParams(
                        android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                        android.widget.LinearLayout.LayoutParams.WRAP_CONTENT);
        sbP.setMargins(0, dp(ctx,6), 0, dp(ctx,8));
        searchBar.setLayoutParams(sbP);
        searchBar.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s,int a,int b,int c){}
            @Override public void onTextChanged(CharSequence s,int a,int b,int c){
                searchQuery = s.toString().trim().toLowerCase();
                rebuildCatalog(ctx);
            }
            @Override public void afterTextChanged(Editable e){}
        });
        main.addView(searchBar);

        catalogContainer = new android.widget.LinearLayout(ctx);
        catalogContainer.setOrientation(android.widget.LinearLayout.VERTICAL);
        main.addView(catalogContainer);
        rebuildCatalog(ctx);

        return root;
    }

    @Override
    public void onStart() {
        super.onStart();

        // Get the dialog and find the internal container frame
        android.app.Dialog dialog = getDialog();
        if (dialog != null) {
            com.google.android.material.bottomsheet.BottomSheetDialog bsDialog =
                    (com.google.android.material.bottomsheet.BottomSheetDialog) dialog;

            // Find the standard container frame holding the view
            android.view.View bottomSheet = bsDialog.findViewById(
                    com.google.android.material.R.id.design_bottom_sheet
            );

            if (bottomSheet != null) {
                com.google.android.material.bottomsheet.BottomSheetBehavior<android.view.View> behavior =
                        com.google.android.material.bottomsheet.BottomSheetBehavior.from(bottomSheet);

                // Force the sheet to open completely
                behavior.setState(com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED);

                // Optional: prevent half-expanded or collapsed intermediate breaks
                behavior.setSkipCollapsed(true);
            }
        }
    }

    // ── Title row ─────────────────────────────────────────────────────
    private View buildTitleRow(android.content.Context ctx) {
        android.widget.LinearLayout row = new android.widget.LinearLayout(ctx);
        row.setOrientation(android.widget.LinearLayout.HORIZONTAL);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        row.setPadding(0, 0, 0, dp(ctx, 14));

        TextView title = new TextView(ctx);
        title.setText("Indicators");
        title.setTextColor(Color.parseColor("#ECEFF1"));
        title.setTextSize(17f);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        android.widget.LinearLayout.LayoutParams tp =
                new android.widget.LinearLayout.LayoutParams(0,
                        android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        title.setLayoutParams(tp);
        row.addView(title);

        TextView clearAll = new TextView(ctx);
        clearAll.setText("Clear All");
        clearAll.setTextColor(Color.parseColor("#EF5350"));
        clearAll.setTextSize(13f);
        clearAll.setPadding(dp(ctx,8), dp(ctx,4), dp(ctx,8), dp(ctx,4));
        clearAll.setOnClickListener(v -> {
            session.clearAll();
            rebuildActiveList(ctx);
            notifyListener();
        });
        row.addView(clearAll);
        return row;
    }

    // ── Active instances list ─────────────────────────────────────────
    private void rebuildActiveList(android.content.Context ctx) {
        activeContainer.removeAllViews();
        List<Indicator> all = session.getAll();
        if (all.isEmpty()) {
            TextView empty = new TextView(ctx);
            empty.setText("No indicators active");
            empty.setTextColor(Color.parseColor("#546E7A"));
            empty.setPadding(0, dp(ctx, 8), 0, 0);
            activeContainer.addView(empty);
            return;
        }
        for (Indicator ind : all) {
            View row = LayoutInflater.from(ctx).inflate(R.layout.item_indicator_active, activeContainer, false);

            // Bind UI
            View colorDot = row.findViewById(R.id.colorDot);
            TextView tag = row.findViewById(R.id.indicatorTag);
            TextView name = row.findViewById(R.id.indicatorNameSummary);
            TextView editBtn = row.findViewById(R.id.btnEdit);
            LinearLayout editor = row.findViewById(R.id.paramEditor);

            colorDot.setBackgroundColor(ind.getColor());
            tag.setText(ind.getTag());
            name.setText(ind.getDisplayName() + " " + buildParamSummary(ind));

            // Inject Params
            for (Indicator.Param p : ind.getParams()) {
                editor.addView(buildParamRow(ctx, p, ind, name, (LinearLayout)row));
            }

            // Listeners
            editBtn.setOnClickListener(v -> {
                boolean isVisible = editor.getVisibility() == View.VISIBLE;
                editor.setVisibility(isVisible ? View.GONE : View.VISIBLE);
                editBtn.setTextColor(isVisible ? Color.parseColor("#78909C") : Color.parseColor("#2196F3"));
            });

            row.findViewById(R.id.btnRemove).setOnClickListener(v -> {
                session.removeInstance(ind.getInstanceId());
                rebuildActiveList(ctx);
                notifyListener();
            });

            colorDot.setOnClickListener(v -> showColorPicker(ctx, ind, colorDot));

            activeContainer.addView(row);
        }
    }

    private View buildActiveRow(android.content.Context ctx, Indicator ind) {
        android.widget.LinearLayout wrapper = new android.widget.LinearLayout(ctx);
        wrapper.setOrientation(android.widget.LinearLayout.VERTICAL);
        wrapper.setBackgroundColor(Color.parseColor("#1E1C1C"));
        android.widget.LinearLayout.LayoutParams wp =
                new android.widget.LinearLayout.LayoutParams(
                        android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                        android.widget.LinearLayout.LayoutParams.WRAP_CONTENT);
        wp.setMargins(0, dp(ctx,3), 0, dp(ctx,3));
        wrapper.setLayoutParams(wp);
        wrapper.setPadding(dp(ctx,8), dp(ctx,6), dp(ctx,8), dp(ctx,6));

        // Header row: color dot | tag | name | ✎ | 🗑
        android.widget.LinearLayout header = new android.widget.LinearLayout(ctx);
        header.setOrientation(android.widget.LinearLayout.HORIZONTAL);
        header.setGravity(android.view.Gravity.CENTER_VERTICAL);

        // Color dot (tappable color picker)
        View colorDot = new View(ctx);
        int dotSizePx = dp(ctx, 14);
        android.widget.LinearLayout.LayoutParams dotP =
                new android.widget.LinearLayout.LayoutParams(dotSizePx, dotSizePx);
        dotP.setMarginEnd(dp(ctx, 8));
        colorDot.setLayoutParams(dotP);
        colorDot.setBackgroundColor(ind.getColor());
        colorDot.setOnClickListener(v -> showColorPicker(ctx, ind, colorDot));
        header.addView(colorDot);

        // Tag badge
        TextView tag = new TextView(ctx);
        tag.setText(ind.getTag());
        tag.setTextColor(Color.parseColor("#ECEFF1"));
        tag.setTextSize(11f);
        tag.setBackgroundColor(Color.parseColor("#252A3D"));
        android.widget.LinearLayout.LayoutParams tagP =
                new android.widget.LinearLayout.LayoutParams(
                        android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                        android.widget.LinearLayout.LayoutParams.WRAP_CONTENT);
        tagP.setMarginEnd(dp(ctx,8));
        tag.setLayoutParams(tagP);
        tag.setPadding(dp(ctx,5), dp(ctx,1), dp(ctx,5), dp(ctx,1));
        header.addView(tag);

        // Name + brief param summary
        TextView nameView = new TextView(ctx);
        String paramSummary = buildParamSummary(ind);
        nameView.setText(ind.getDisplayName() + (paramSummary.isEmpty() ? "" : "  " + paramSummary));
        nameView.setTextColor(Color.parseColor("#B0BEC5"));
        nameView.setTextSize(12f);
        android.widget.LinearLayout.LayoutParams nameP =
                new android.widget.LinearLayout.LayoutParams(0,
                        android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        nameView.setLayoutParams(nameP);
        header.addView(nameView);

        // Edit button
        TextView editBtn = new TextView(ctx);
        editBtn.setText("✎");
        editBtn.setTextSize(16f);
        editBtn.setTextColor(Color.parseColor("#78909C"));
        editBtn.setPadding(dp(ctx,8), dp(ctx,2), dp(ctx,4), dp(ctx,2));
        header.addView(editBtn);

        // Remove button
        TextView removeBtn = new TextView(ctx);
        removeBtn.setText("✕");
        removeBtn.setTextSize(14f);
        removeBtn.setTextColor(Color.parseColor("#EF5350"));
        removeBtn.setPadding(dp(ctx,4), dp(ctx,2), dp(ctx,4), dp(ctx,2));
        header.addView(removeBtn);
        wrapper.addView(header);

        // Collapsible param editor
        android.widget.LinearLayout paramEditor = new android.widget.LinearLayout(ctx);
        paramEditor.setOrientation(android.widget.LinearLayout.VERTICAL);
        paramEditor.setVisibility(View.GONE);
        paramEditor.setPadding(dp(ctx,4), dp(ctx,4), 0, dp(ctx,2));
        for (Indicator.Param param : ind.getParams()) {
            paramEditor.addView(buildParamRow(ctx, param, ind, nameView, wrapper));
        }
        wrapper.addView(paramEditor);

        // Wire edit toggle
        editBtn.setOnClickListener(v -> {
            if (paramEditor.getVisibility() == View.VISIBLE) {
                paramEditor.setVisibility(View.GONE);
                editBtn.setTextColor(Color.parseColor("#78909C"));
            } else {
                paramEditor.setVisibility(View.VISIBLE);
                editBtn.setTextColor(Color.parseColor("#2196F3"));
            }
        });

        // Wire remove
        removeBtn.setOnClickListener(v -> {
            session.removeInstance(ind.getInstanceId());
            rebuildActiveList(ctx);
            notifyListener();
        });

        return wrapper;
    }

    private String buildParamSummary(Indicator ind) {
        List<Indicator.Param> params = ind.getParams();
        if (params.isEmpty()) return "";
        StringBuilder sb = new StringBuilder("(");
        for (int i = 0; i < Math.min(params.size(), 2); i++) {
            if (i > 0) sb.append(",");
            Indicator.Param p = params.get(i);
            if (p.type == Indicator.Param.Type.INTEGER) sb.append(p.intValue());
            else sb.append(String.format(Locale.US, "%.1f", p.floatValue()));
        }
        sb.append(")");
        return sb.toString();
    }

    // ── Catalog (add indicator) ───────────────────────────────────────
    private void rebuildCatalog(android.content.Context ctx) {
        catalogContainer.removeAllViews();
        for (Indicator type : IndicatorRegistry.getInstance().getAllTypes()) {
            String n = type.getDisplayName().toLowerCase();
            String t = type.getTag().toLowerCase();
            if (!searchQuery.isEmpty() && !n.contains(searchQuery) && !t.contains(searchQuery))
                continue;
            catalogContainer.addView(buildCatalogRow(ctx, type));
        }
    }

    private View buildCatalogRow(android.content.Context ctx, Indicator type) {
        android.widget.LinearLayout row = new android.widget.LinearLayout(ctx);
        row.setOrientation(android.widget.LinearLayout.HORIZONTAL);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(ctx,5), 0, dp(ctx,5));

        // Tag badge
        TextView tag = new TextView(ctx);
        tag.setText("  " + type.getTag() + "  ");
        tag.setTextColor(Color.parseColor("#ECEFF1"));
        tag.setTextSize(11f);
        tag.setBackgroundColor(Color.parseColor("#252A3D"));
        android.widget.LinearLayout.LayoutParams tagP =
                new android.widget.LinearLayout.LayoutParams(
                        android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                        android.widget.LinearLayout.LayoutParams.WRAP_CONTENT);
        tagP.setMarginEnd(dp(ctx,10));
        tag.setLayoutParams(tagP);
        row.addView(tag);

        // Sub-chart indicator label
        if (type.isSubChart()) {
            TextView sub = new TextView(ctx);
            sub.setText("oscillator");
            sub.setTextColor(Color.parseColor("#78909C"));
            sub.setTextSize(9f);
            android.widget.LinearLayout.LayoutParams subP =
                    new android.widget.LinearLayout.LayoutParams(
                            android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                            android.widget.LinearLayout.LayoutParams.WRAP_CONTENT);
            subP.setMarginEnd(dp(ctx,6));
            sub.setLayoutParams(subP);
            row.addView(sub);
        }

        // Name
        TextView name = new TextView(ctx);
        name.setText(type.getDisplayName());
        name.setTextColor(Color.parseColor("#B0BEC5"));
        name.setTextSize(13f);
        android.widget.LinearLayout.LayoutParams nameP =
                new android.widget.LinearLayout.LayoutParams(0,
                        android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        name.setLayoutParams(nameP);
        row.addView(name);

        // + button
        TextView addBtn = new TextView(ctx);
        addBtn.setText("+");
        addBtn.setTextSize(18f);
        addBtn.setTextColor(Color.parseColor("#26A69A"));
        addBtn.setPadding(dp(ctx,10), dp(ctx,2), dp(ctx,10), dp(ctx,2));
        addBtn.setOnClickListener(v -> {
            session.addInstance(type.getId());
            rebuildActiveList(ctx);
            notifyListener();
        });
        row.addView(addBtn);
        return row;
    }

    // ── Param row ────────────────────────────────────────────────────
    private View buildParamRow(android.content.Context ctx, Indicator.Param param,
                               Indicator ind, TextView nameSummary,
                               android.widget.LinearLayout wrapper) {
        android.widget.LinearLayout row = new android.widget.LinearLayout(ctx);
        row.setOrientation(android.widget.LinearLayout.HORIZONTAL);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        row.setPadding(dp(ctx,4), dp(ctx,2), 0, dp(ctx,3));

        TextView lbl = new TextView(ctx);
        lbl.setText(param.label + ": ");
        lbl.setTextColor(Color.parseColor("#546E7A"));
        lbl.setTextSize(12f);
        lbl.setMinWidth(dp(ctx, 72));
        row.addView(lbl);

        SeekBar seek = new SeekBar(ctx);
        int range = (int)(param.max - param.min);
        seek.setMax(range);
        seek.setProgress((int)(param.value - param.min));
        android.widget.LinearLayout.LayoutParams seekP =
                new android.widget.LinearLayout.LayoutParams(0,
                        android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        seek.setLayoutParams(seekP);
        row.addView(seek);

        TextView val = new TextView(ctx);
        val.setMinWidth(dp(ctx, 40));
        val.setTextColor(Color.parseColor("#ECEFF1"));
        val.setTextSize(12f);
        val.setPadding(dp(ctx,6), 0, 0, 0);
        updateValLabel(val, param);
        row.addView(val);

        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s,int p,boolean fromUser) {
                if (!fromUser) return;
                param.value = param.min + p;
                updateValLabel(val, param);
                // Update name summary inline
                String ps = buildParamSummary(ind);
                nameSummary.setText(ind.getDisplayName() + (ps.isEmpty() ? "" : "  " + ps));
                notifyListener();
            }
            @Override public void onStartTrackingTouch(SeekBar s){}
            @Override public void onStopTrackingTouch(SeekBar s){}
        });
        return row;
    }

    // ── Color picker ──────────────────────────────────────────────────
    private void showColorPicker(android.content.Context ctx, Indicator ind, View colorDot) {
        // Build a simple grid dialog with preset colors
        android.widget.GridLayout grid = new android.widget.GridLayout(ctx);
        grid.setColumnCount(6);
        int swatchSize = dp(ctx, 36);
        int swatchMargin = dp(ctx, 4);
        for (int c : PRESET_COLORS) {
            View swatch = new View(ctx);
            android.widget.GridLayout.LayoutParams gp = new android.widget.GridLayout.LayoutParams();
            gp.width = swatchSize; gp.height = swatchSize;
            gp.setMargins(swatchMargin, swatchMargin, swatchMargin, swatchMargin);
            swatch.setLayoutParams(gp);
            swatch.setBackgroundColor(c);
            final int chosenColor = c;
            swatch.setOnClickListener(v -> {
                ind.setColor(chosenColor);
                colorDot.setBackgroundColor(chosenColor);
                notifyListener();
                if (getDialog() != null) {
                    // rebuild active list to reflect new color
                    rebuildActiveList(ctx);
                }
                // dismiss the popup via its window token
                if (v.getRootView() != null) {
                    // Close the color-picker dialog — it was set as content of an AlertDialog
                    // dismiss by finding the root AlertDialog is complex; use a simpler approach:
                    // We tag the dialog on the view
                    Object tag = grid.getTag();
                    if (tag instanceof android.app.AlertDialog) ((android.app.AlertDialog)tag).dismiss();
                }
            });
            grid.addView(swatch);
        }
        android.app.AlertDialog dialog = new android.app.AlertDialog.Builder(ctx, R.style.GutDialog)
                .setTitle("Pick Color")
                .setView(grid)
                .setNegativeButton("Cancel", null)
                .create();
        grid.setTag(dialog); // store for dismissal above
        dialog.show();
    }

    // ── Helpers ───────────────────────────────────────────────────────
    private void updateValLabel(TextView tv, Indicator.Param param) {
        if (param.type == Indicator.Param.Type.INTEGER)
            tv.setText(String.valueOf(param.intValue()));
        else tv.setText(String.format(Locale.US, "%.1f", param.floatValue()));
    }

    private void addSectionHeader(android.content.Context ctx,
                                  android.widget.LinearLayout parent, String text) {
        TextView tv = new TextView(ctx);
        tv.setText(text.toUpperCase(Locale.US));
        tv.setTextColor(Color.parseColor("#546E7A"));
        tv.setTextSize(10f);
        tv.setLetterSpacing(0.12f);
        tv.setPadding(0, dp(ctx,10), 0, dp(ctx,4));
        parent.addView(tv);
    }

    private View makeDivider(android.content.Context ctx) {
        View v = new View(ctx);
        v.setBackgroundColor(Color.parseColor("#2A2828"));
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
}