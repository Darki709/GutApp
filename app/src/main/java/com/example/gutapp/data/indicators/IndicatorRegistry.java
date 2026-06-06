package com.example.gutapp.data.indicators;

import com.example.gutapp.data.indicators.impl.AdlIndicator;
import com.example.gutapp.data.indicators.impl.ApoIndicator;
import com.example.gutapp.data.indicators.impl.ArnaudLegouxMovingAverageIndicator;
import com.example.gutapp.data.indicators.impl.AtrIndicator;
import com.example.gutapp.data.indicators.impl.AwesomeOscillatorIndicator;
import com.example.gutapp.data.indicators.impl.BbBreakoutStrategyIndicator;
import com.example.gutapp.data.indicators.impl.BollingerBandsIndicator;
import com.example.gutapp.data.indicators.impl.CciIndicator;
import com.example.gutapp.data.indicators.impl.ChandeKrollStopIndicator;
import com.example.gutapp.data.indicators.impl.ChoppinessIndicator;
import com.example.gutapp.data.indicators.impl.CmfIndicator;
import com.example.gutapp.data.indicators.impl.CmoIndicator;
import com.example.gutapp.data.indicators.impl.CoppockIndicator;
import com.example.gutapp.data.indicators.impl.DemaIndicator;
import com.example.gutapp.data.indicators.impl.DisparityIndexIndicator;
import com.example.gutapp.data.indicators.impl.DpoIndicator;
import com.example.gutapp.data.indicators.impl.EmaIndicator;
import com.example.gutapp.data.indicators.impl.EnvelopesIndicator;
import com.example.gutapp.data.indicators.impl.EomIndicator;
import com.example.gutapp.data.indicators.impl.FisherTransformIndicator;
import com.example.gutapp.data.indicators.impl.ForceIndexIndicator;
import com.example.gutapp.data.indicators.impl.HistoricalVolatilityIndicator;
import com.example.gutapp.data.indicators.impl.HullMovingAverageIndicator;
import com.example.gutapp.data.indicators.impl.KamaIndicator;
import com.example.gutapp.data.indicators.impl.KeltnerChannelsIndicator;
import com.example.gutapp.data.indicators.impl.LinearRegressionChannelIndicator;
import com.example.gutapp.data.indicators.impl.LinearRegressionSlopeIndicator;
import com.example.gutapp.data.indicators.impl.MaCrossoverStrategyIndicator;
import com.example.gutapp.data.indicators.impl.MacdIndicator;
import com.example.gutapp.data.indicators.impl.MaIndicator;
import com.example.gutapp.data.indicators.impl.MacdStrategyIndicator;
import com.example.gutapp.data.indicators.impl.MassIndexIndicator;
import com.example.gutapp.data.indicators.impl.McGinleyDynamicIndicator;
import com.example.gutapp.data.indicators.impl.MomentumIndicator;
import com.example.gutapp.data.indicators.impl.MoneyFlowIndexIndicator;
import com.example.gutapp.data.indicators.impl.ObvIndicator;
import com.example.gutapp.data.indicators.impl.ParabolicSARIndicator;
import com.example.gutapp.data.indicators.impl.PivotPointsIndicator;
import com.example.gutapp.data.indicators.impl.PriceChannelsIndicator;
import com.example.gutapp.data.indicators.impl.RocIndicator;
import com.example.gutapp.data.indicators.impl.RsiIndicator;
import com.example.gutapp.data.indicators.impl.RsiReversalStrategyIndicator;
import com.example.gutapp.data.indicators.impl.SmmaIndicator;
import com.example.gutapp.data.indicators.impl.StandardDeviationIndicator;
import com.example.gutapp.data.indicators.impl.StochasticIndicator;
import com.example.gutapp.data.indicators.impl.SuperTrendStrategyIndicator;
import com.example.gutapp.data.indicators.impl.SupplyDemandZoneIndicator;
import com.example.gutapp.data.indicators.impl.TrixIndicator;
import com.example.gutapp.data.indicators.impl.TrueRangeIndicator;
import com.example.gutapp.data.indicators.impl.TsiIndicator;
import com.example.gutapp.data.indicators.impl.UltimateOscillatorIndicator;
import com.example.gutapp.data.indicators.impl.VolumeOscillatorIndicator;
import com.example.gutapp.data.indicators.impl.VortexIndicator;
import com.example.gutapp.data.indicators.impl.VwapIndicator;
import com.example.gutapp.data.indicators.impl.VwmaIndicator;
import com.example.gutapp.data.indicators.impl.WilliamsRIndicator;
import com.example.gutapp.data.indicators.impl.WmaIndicator;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

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
        register(new KeltnerChannelsIndicator());
        register(new CmoIndicator());
        register(new RocIndicator());
        register(new TsiIndicator());
        register(new PriceChannelsIndicator());
        register(new WilliamsRIndicator());
        register(new LinearRegressionSlopeIndicator());
        register(new HullMovingAverageIndicator());
        register(new ApoIndicator());
        register(new PivotPointsIndicator());
        register(new LinearRegressionChannelIndicator());
        register(new SupplyDemandZoneIndicator());
        register(new MaCrossoverStrategyIndicator());
        register(new AdlIndicator());
        register(new CmfIndicator());
        register(new ChandeKrollStopIndicator());
        register(new ArnaudLegouxMovingAverageIndicator());
        register(new ParabolicSARIndicator());
        register(new AwesomeOscillatorIndicator());
        register(new VwmaIndicator());
        register(new EomIndicator());
        register(new DpoIndicator());
        register(new CoppockIndicator());
        register(new UltimateOscillatorIndicator());
        register(new SuperTrendStrategyIndicator());
        register(new BbBreakoutStrategyIndicator());
        register(new RsiReversalStrategyIndicator());
        register(new MacdStrategyIndicator());
        register(new MomentumIndicator());
        register(new StandardDeviationIndicator());
        register(new EnvelopesIndicator());
        register(new VolumeOscillatorIndicator());
        register(new ChoppinessIndicator());
        register(new TrixIndicator());
        register(new ObvIndicator());
        register(new MoneyFlowIndexIndicator());
        register(new DemaIndicator());
        register(new ForceIndexIndicator());
        register(new TrueRangeIndicator());
        register(new SmmaIndicator());
        register(new KamaIndicator());
        register(new VortexIndicator());
        register(new DisparityIndexIndicator());
        register(new McGinleyDynamicIndicator());
        register(new HistoricalVolatilityIndicator());
        register(new FisherTransformIndicator());
        register(new MassIndexIndicator());
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