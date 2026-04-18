package com.example.gutapp.data.indicators;

import com.example.gutapp.data.indicators.impl.BollingerBandsIndicator;
import com.example.gutapp.data.indicators.impl.EmaIndicator;
import com.example.gutapp.data.indicators.impl.MacdIndicator;
import com.example.gutapp.data.indicators.impl.MaIndicator;
import com.example.gutapp.data.indicators.impl.RsiIndicator;
import com.example.gutapp.data.indicators.impl.VwapIndicator;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * IndicatorRegistry — single source of truth for all available indicators.
 *
 * To add a new indicator to the app:
 *   1. Create MyIndicator.java extending Indicator in the impl/ package
 *   2. Add one line here: register(new MyIndicator())
 *   Done. The panel and chart will automatically pick it up.
 */
public class IndicatorRegistry {

    // Ordered map so panel always shows indicators in a consistent order
    private final Map<String, Indicator> indicators = new LinkedHashMap<>();

    // Singleton
    private static IndicatorRegistry instance;

    public static IndicatorRegistry getInstance() {
        if (instance == null) instance = new IndicatorRegistry();
        return instance;
    }

    private IndicatorRegistry() {
        // ── Register all indicators here ──────────────────────────
        register(new MaIndicator());
        register(new EmaIndicator());
        register(new BollingerBandsIndicator());
        register(new VwapIndicator());
        register(new RsiIndicator());
        register(new MacdIndicator());
        // To add a new one: register(new StochasticIndicator());
    }

    private void register(Indicator ind) {
        indicators.put(ind.getId(), ind);
    }

    /** All registered indicators in display order */
    public List<Indicator> getAll() {
        return new ArrayList<>(indicators.values());
    }

    /** All currently enabled indicators */
    public List<Indicator> getEnabled() {
        List<Indicator> enabled = new ArrayList<>();
        for (Indicator ind : indicators.values()) {
            if (ind.isEnabled()) enabled.add(ind);
        }
        return enabled;
    }

    /** Overlay indicators only (rendered on the price chart) */
    public List<Indicator> getEnabledOverlays() {
        List<Indicator> result = new ArrayList<>();
        for (Indicator ind : indicators.values()) {
            if (ind.isEnabled() && !ind.isSubChart()) result.add(ind);
        }
        return result;
    }

    /** Sub-chart indicators (rendered in separate pane below) */
    public List<Indicator> getEnabledSubCharts() {
        List<Indicator> result = new ArrayList<>();
        for (Indicator ind : indicators.values()) {
            if (ind.isEnabled() && ind.isSubChart()) result.add(ind);
        }
        return result;
    }

    public Indicator get(String id) {
        return indicators.get(id);
    }

    /** Reset all indicators to disabled state */
    public void clearAll() {
        for (Indicator ind : indicators.values()) ind.setEnabled(false);
    }

    /**
     * Save a snapshot of all indicator states (enabled + param values).
     * Returned as a list of IndicatorState for thread-safe passing.
     */
    public List<IndicatorState> saveState() {
        List<IndicatorState> states = new ArrayList<>();
        for (Indicator ind : indicators.values()) {
            states.add(new IndicatorState(ind.getId(), ind.isEnabled(), ind.copyParams()));
        }
        return states;
    }

    /** Restore a previously saved state snapshot */
    public void restoreState(List<IndicatorState> states) {
        for (IndicatorState state : states) {
            Indicator ind = indicators.get(state.id);
            if (ind != null) {
                ind.setEnabled(state.enabled);
                ind.restoreParams(state.params);
            }
        }
    }

    /** Immutable snapshot of a single indicator's state */
    public static class IndicatorState {
        public final String id;
        public final boolean enabled;
        public final List<Indicator.Param> params;

        IndicatorState(String id, boolean enabled, List<Indicator.Param> params) {
            this.id      = id;
            this.enabled = enabled;
            this.params  = params;
        }
    }
}