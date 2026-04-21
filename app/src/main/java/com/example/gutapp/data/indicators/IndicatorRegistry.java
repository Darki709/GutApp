package com.example.gutapp.data.indicators;

import com.example.gutapp.data.indicators.impl.AtrIndicator;
import com.example.gutapp.data.indicators.impl.BollingerBandsIndicator;
import com.example.gutapp.data.indicators.impl.CciIndicator;
import com.example.gutapp.data.indicators.impl.EmaIndicator;
import com.example.gutapp.data.indicators.impl.MacdIndicator;
import com.example.gutapp.data.indicators.impl.MaIndicator;
import com.example.gutapp.data.indicators.impl.RsiIndicator;
import com.example.gutapp.data.indicators.impl.StochasticIndicator;
import com.example.gutapp.data.indicators.impl.VwapIndicator;
import com.example.gutapp.data.indicators.impl.WmaIndicator;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * IndicatorRegistry — immutable catalog of available indicator TYPES.
 *
 * This no longer tracks which indicators are "enabled".
 * Live instances are managed by IndicatorSession (one per chart/preset).
 *
 * To add a new indicator type:
 *   1. Create MyIndicator.java in impl/, implement newInstance()
 *   2. register(new MyIndicator()) here
 *   Done.
 */
public class IndicatorRegistry {

    private final LinkedHashMap<String, Indicator> types = new LinkedHashMap<>();
    private static IndicatorRegistry instance;

    public static IndicatorRegistry getInstance() {
        if (instance == null) instance = new IndicatorRegistry();
        return instance;
    }

    private IndicatorRegistry() {
        register(new MaIndicator());
        register(new EmaIndicator());
        register(new BollingerBandsIndicator());
        register(new VwapIndicator());
        register(new RsiIndicator());
        register(new MacdIndicator());
        register(new CciIndicator());
        register(new StochasticIndicator());
        register(new WmaIndicator());
        register(new AtrIndicator());
    }

    private void register(Indicator prototype) {
        types.put(prototype.getId(), prototype);
    }

    /** All registered type prototypes in display order */
    public List<Indicator> getAllTypes() {
        return new ArrayList<>(types.values());
    }

    /** Get a type prototype by typeId */
    public Indicator getType(String typeId) {
        return types.get(typeId);
    }

    /** Check if a type exists */
    public boolean hasType(String typeId) {
        return types.containsKey(typeId);
    }
}