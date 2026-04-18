package com.example.gutapp.ui;

import android.app.AlertDialog;
import android.graphics.Color;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.FragmentManager;

import com.example.gutapp.R;
import com.example.gutapp.data.UserGlobals;
import com.example.gutapp.data.indicators.Indicator;
import com.example.gutapp.data.indicators.IndicatorRegistry;
import com.example.gutapp.data.indicators.IndicatorSession;
import com.example.gutapp.data.indicators.PresetRepository;
import com.example.gutapp.session.DataType;
import com.example.gutapp.ui.fragments.IndicatorsPanel;

import java.util.List;
import java.util.Locale;

/**
 * ProfileActivity — user profile + indicator preset management.
 *
 * "New Preset" flow:
 *   1. User taps "+ New Preset"
 *   2. AlertDialog asks for a preset name
 *   3. IndicatorsPanel opens with a FRESH IndicatorSession
 *   4. User configures indicators in the panel and taps "Save" in the panel header
 *   5. Preset is saved to PresetRepository and the card list refreshes
 *
 * "Edit Preset" flow:
 *   1. User taps "Edit" on a preset card
 *   2. IndicatorsPanel opens with the preset's IndicatorSession pre-populated
 *   3. User modifies indicators
 *   4. User taps "Save" — preset is overwritten with the new session
 *
 * The IndicatorsPanel's normal "X changes → notifyListener" still works during editing,
 * but we only persist when the user explicitly taps "Save" in the header row we add.
 */
public class ProfileActivity extends SessionActivity implements IndicatorsPanel.IndicatorListener {

    private PresetRepository repo;
    private LinearLayout presetsContainer;

    // The session being edited right now (null when no panel is open)
    private IndicatorSession editingSession = null;
    // The name of the preset being edited (null when creating new)
    private String editingPresetName = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_profile);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets sb = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(sb.left, sb.top, sb.right, sb.bottom);
            return insets;
        });

        repo = new PresetRepository(this);
        presetsContainer = findViewById(R.id.presetsContainer);

        View back = findViewById(R.id.btnBack);
        if (back != null) back.setOnClickListener(v -> finish());

        // User info
        TextView tvName = findViewById(R.id.tvProfileName);
        if (tvName != null)
            tvName.setText(UserGlobals.USER_NAME != null ? UserGlobals.USER_NAME : "—");

        TextView tvBalance = findViewById(R.id.tvProfileBalance);
        if (tvBalance != null)
            UserGlobals.getBalance().observe(this,
                    b -> tvBalance.setText(String.format(Locale.US, "$%.4f", b)));

        // "+ New Preset" → ask for name first, then open editor panel
        View newBtn = findViewById(R.id.btnNewPreset);
        if (newBtn != null) newBtn.setOnClickListener(v -> promptNewPresetName());

        renderPresets();
    }

    // ── New preset flow ───────────────────────────────────────────────

    /**
     * Step 1: ask for a name, then open the indicator panel to configure.
     */
    private void promptNewPresetName() {
        EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setHint("e.g. Trend Setup, Scalping…");
        input.setTextColor(Color.parseColor("#ECEFF1"));
        input.setHintTextColor(Color.parseColor("#546E7A"));
        input.setBackgroundColor(Color.parseColor("#252323"));
        input.setPadding(dp(14), dp(10), dp(14), dp(10));

        new AlertDialog.Builder(this)
                .setTitle("New Preset")
                .setMessage("Enter a name for this preset:")
                .setView(input)
                .setPositiveButton("Next: Add Indicators", (d, w) -> {
                    String name = input.getText().toString().trim();
                    if (name.isEmpty()) {
                        Toast.makeText(this, "Name cannot be empty", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    // Check for duplicates
                    for (PresetRepository.Preset p : repo.getAllPresets()) {
                        if (p.name.equalsIgnoreCase(name)) {
                            Toast.makeText(this,
                                    "A preset named \"" + name + "\" already exists",
                                    Toast.LENGTH_SHORT).show();
                            return;
                        }
                    }
                    openEditorPanel(name, new IndicatorSession());
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // ── Edit preset flow ──────────────────────────────────────────────

    /**
     * Opens the indicator panel pre-populated with the preset's indicators.
     */
    private void editPreset(PresetRepository.Preset preset) {
        // Restore preset into a temporary session
        IndicatorSession session = new IndicatorSession();
        session.loadPreset(preset.snapshots);
        openEditorPanel(preset.name, session);
    }

    // ── Shared: open panel ────────────────────────────────────────────

    /**
     * Opens IndicatorsPanel bottom sheet for the given session.
     * A "Save Preset" button is shown inside the panel (via the SaveableIndicatorsPanel subclass).
     * When the user taps Save, the session is persisted.
     */
    private void openEditorPanel(String presetName, IndicatorSession session) {
        editingSession    = session;
        editingPresetName = presetName;

        // We use SaveableIndicatorsPanel — a subclass of IndicatorsPanel that adds
        // a "Save Preset" button in the title row. (Defined below.)
        SaveableIndicatorsPanel panel = SaveableIndicatorsPanel.newSaveable(session, presetName);
        panel.setListener(this);
        panel.setSaveListener(this::onPresetSaveRequested);
        panel.show(getSupportFragmentManager(), "preset_editor");
    }

    /**
     * Called by SaveableIndicatorsPanel when the user taps "Save Preset".
     * @param presetName the name shown in the panel header (immutable once the panel is open)
     */
    private void onPresetSaveRequested(String presetName) {
        if (editingSession == null) return;
        repo.savePreset(new PresetRepository.Preset(presetName, editingSession.savePreset()));
        Toast.makeText(this, "Preset \"" + presetName + "\" saved", Toast.LENGTH_SHORT).show();
        editingSession    = null;
        editingPresetName = null;
        renderPresets();
        // Close the panel if still open
        FragmentManager fm = getSupportFragmentManager();
        androidx.fragment.app.Fragment f = fm.findFragmentByTag("preset_editor");
        if (f instanceof androidx.fragment.app.DialogFragment)
            ((androidx.fragment.app.DialogFragment) f).dismiss();
    }

    // ── IndicatorsPanel.IndicatorListener ────────────────────────────
    // This fires on every toggle/slider change while the panel is open.
    // We don't auto-save here — only the explicit "Save Preset" tap saves.
    @Override
    public void onIndicatorsChanged() {
        // no-op in profile context: save is manual via the Save button
    }

    // ── Preset list rendering ─────────────────────────────────────────

    private void renderPresets() {
        presetsContainer.removeAllViews();
        List<PresetRepository.Preset> presets = repo.getAllPresets();

        if (presets.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("No saved presets yet.\nTap \"+ New Preset\" to create one.");
            empty.setTextColor(Color.parseColor("#546E7A"));
            empty.setTextSize(13f);
            empty.setPadding(dp(4), dp(10), dp(4), dp(8));
            presetsContainer.addView(empty);
            return;
        }

        for (PresetRepository.Preset preset : presets)
            presetsContainer.addView(buildPresetCard(preset));
    }

    private View buildPresetCard(PresetRepository.Preset preset) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundColor(Color.parseColor("#1E1C1C"));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(dp(10), dp(4), dp(10), dp(4));
        card.setLayoutParams(lp);
        card.setPadding(dp(12), dp(10), dp(12), dp(10));

        // ── Name ────────────────────────────────────────────────────
        TextView nameView = new TextView(this);
        nameView.setText(preset.name);
        nameView.setTextColor(Color.parseColor("#ECEFF1"));
        nameView.setTextSize(14f);
        nameView.setTypeface(null, android.graphics.Typeface.BOLD);
        card.addView(nameView);

        // ── Indicator chips row ──────────────────────────────────────
        if (!preset.snapshots.isEmpty()) {
            LinearLayout chips = new LinearLayout(this);
            chips.setOrientation(LinearLayout.HORIZONTAL);
            LinearLayout.LayoutParams chipRowLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            chipRowLp.topMargin = dp(5); chipRowLp.bottomMargin = dp(8);
            chips.setLayoutParams(chipRowLp);

            for (Indicator.IndicatorSnapshot snap : preset.snapshots) {
                Indicator proto = IndicatorRegistry.getInstance().getType(snap.typeId);
                if (proto == null) continue;
                chips.addView(makeIndicatorChip(proto.getTag(), snap));
            }
            card.addView(chips);
        } else {
            TextView none = new TextView(this);
            none.setText("No indicators");
            none.setTextColor(Color.parseColor("#546E7A"));
            none.setTextSize(11f);
            LinearLayout.LayoutParams nlp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            nlp.topMargin = dp(4); nlp.bottomMargin = dp(8);
            none.setLayoutParams(nlp);
            card.addView(none);
        }

        // ── Action buttons ───────────────────────────────────────────
        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(android.view.Gravity.END);
        card.addView(actions);

        // Edit — opens IndicatorsPanel pre-filled with this preset
        TextView editBtn = actionChip("✎ Edit", "#2196F3");
        editBtn.setOnClickListener(v -> editPreset(preset));
        actions.addView(editBtn);

        // Delete
        TextView deleteBtn = actionChip("Delete", "#EF5350");
        deleteBtn.setOnClickListener(v ->
                new AlertDialog.Builder(this)
                        .setMessage("Delete preset \"" + preset.name + "\"?")
                        .setPositiveButton("Delete", (d, w) -> {
                            repo.deletePreset(preset.name);
                            renderPresets();
                        })
                        .setNegativeButton("Cancel", null)
                        .show());
        actions.addView(deleteBtn);

        return card;
    }

    /**
     * Small colored chip showing tag + first param value + color dot.
     * E.g. "●  MA(20)"
     */
    private View makeIndicatorChip(String tag, Indicator.IndicatorSnapshot snap) {
        LinearLayout chip = new LinearLayout(this);
        chip.setOrientation(LinearLayout.HORIZONTAL);
        chip.setGravity(android.view.Gravity.CENTER_VERTICAL);
        chip.setBackgroundColor(Color.parseColor("#252A3D"));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMarginEnd(dp(6));
        chip.setLayoutParams(lp);
        chip.setPadding(dp(6), dp(3), dp(8), dp(3));

        // Color dot
        View dot = new View(this);
        LinearLayout.LayoutParams dotLp = new LinearLayout.LayoutParams(dp(8), dp(8));
        dotLp.setMarginEnd(dp(5));
        dot.setLayoutParams(dotLp);
        dot.setBackgroundColor(snap.color);
        chip.addView(dot);

        // Tag + first param
        TextView tv = new TextView(this);
        String label = tag;
        if (!snap.params.isEmpty())
            label += "(" + Math.round(snap.params.get(0).value) + ")";
        tv.setText(label);
        tv.setTextColor(Color.parseColor("#B0BEC5"));
        tv.setTextSize(11f);
        chip.addView(tv);
        return chip;
    }

    // ── Helpers ───────────────────────────────────────────────────────

    private TextView actionChip(String text, String color) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextColor(Color.parseColor(color));
        tv.setTextSize(12f);
        tv.setPadding(dp(10), dp(4), dp(10), dp(4));
        return tv;
    }

    private int dp(int val) {
        return Math.round(val * getResources().getDisplayMetrics().density);
    }

    @Override protected void refreshNetwork() {}

    @Override
    public void onDataReceived(DataType msgType, Object parsedData) {}

    // ════════════════════════════════════════════════════════════════
    // SaveableIndicatorsPanel — IndicatorsPanel + "Save Preset" button
    // ════════════════════════════════════════════════════════════════
    /**
     * Subclass of IndicatorsPanel that injects a "Save Preset" action button
     * into the title row. Used only from ProfileActivity.
     *
     * We override buildTitleRow (via a callback hook) so everything else in
     * IndicatorsPanel — search, active list, catalog, color picker — works unchanged.
     *
     * The simpler alternative (and the one we use) is to override onCreateView
     * to wrap the parent's view and inject a persistent sticky header above it.
     */
    public static class SaveableIndicatorsPanel extends IndicatorsPanel {

        /** Called when the user taps "Save Preset" */
        public interface SaveListener {
            void onSave(String presetName);
        }

        private SaveListener saveListener;
        private String presetName;

        public static SaveableIndicatorsPanel newSaveable(IndicatorSession session, String name) {
            SaveableIndicatorsPanel p = new SaveableIndicatorsPanel();
            p.session  = session;   // inherited protected field
            p.presetName = name;
            return p;
        }

        public void setSaveListener(SaveListener l) { this.saveListener = l; }

        @Override
        public View onCreateView(@NonNull android.view.LayoutInflater inflater,
                                 @Nullable android.view.ViewGroup container,
                                 @Nullable Bundle savedInstanceState) {
            // Get the normal panel view from the parent
            View panelRoot = super.onCreateView(inflater, container, savedInstanceState);

            // Wrap it in a vertical LinearLayout so we can add a sticky header above it
            android.content.Context ctx = requireContext();
            android.widget.LinearLayout wrapper = new android.widget.LinearLayout(ctx);
            wrapper.setOrientation(android.widget.LinearLayout.VERTICAL);
            wrapper.setBackgroundColor(android.graphics.Color.parseColor("#1A1818"));

            // ── Sticky save bar ────────────────────────────────────
            android.widget.LinearLayout saveBar = new android.widget.LinearLayout(ctx);
            saveBar.setOrientation(android.widget.LinearLayout.HORIZONTAL);
            saveBar.setGravity(android.view.Gravity.CENTER_VERTICAL);
            saveBar.setBackgroundColor(android.graphics.Color.parseColor("#1F1D1D"));
            saveBar.setPadding(dpI(ctx,16), dpI(ctx,10), dpI(ctx,16), dpI(ctx,10));

            // Preset name label
            android.widget.TextView nameLbl = new android.widget.TextView(ctx);
            nameLbl.setText("Editing: " + presetName);
            nameLbl.setTextColor(android.graphics.Color.parseColor("#ECEFF1"));
            nameLbl.setTextSize(13f);
            nameLbl.setTypeface(null, android.graphics.Typeface.BOLD);
            android.widget.LinearLayout.LayoutParams nlp =
                    new android.widget.LinearLayout.LayoutParams(0,
                            android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            nameLbl.setLayoutParams(nlp);
            saveBar.addView(nameLbl);

            // Save button
            android.widget.TextView saveBtn = new android.widget.TextView(ctx);
            saveBtn.setText("💾 Save Preset");
            saveBtn.setTextColor(android.graphics.Color.parseColor("#26A69A"));
            saveBtn.setTextSize(13f);
            saveBtn.setTypeface(null, android.graphics.Typeface.BOLD);
            saveBtn.setPadding(dpI(ctx,12), dpI(ctx,6), dpI(ctx,12), dpI(ctx,6));
            saveBtn.setBackgroundColor(android.graphics.Color.parseColor("#1A3330"));
            saveBtn.setOnClickListener(v -> {
                if (saveListener != null) saveListener.onSave(presetName);
            });
            saveBar.addView(saveBtn);

            wrapper.addView(saveBar);

            // Divider
            android.view.View div = new android.view.View(ctx);
            div.setBackgroundColor(android.graphics.Color.parseColor("#2A2828"));
            android.widget.LinearLayout.LayoutParams divLp =
                    new android.widget.LinearLayout.LayoutParams(
                            android.widget.LinearLayout.LayoutParams.MATCH_PARENT, 1);
            div.setLayoutParams(divLp);
            wrapper.addView(div);

            // Panel body (scrollable indicators list)
            wrapper.addView(panelRoot);
            return wrapper;
        }

        private int dpI(android.content.Context ctx, int val) {
            return Math.round(val * ctx.getResources().getDisplayMetrics().density);
        }

    }
}