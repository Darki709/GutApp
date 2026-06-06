package com.example.gutapp.data.alerts.conditions;

import com.example.gutapp.data.alerts.Condition;
import com.example.gutapp.data.alerts.PriceResource;
import com.example.gutapp.data.models.Candle;
import com.example.gutapp.database.StockDataHelper;
import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;

import java.util.List;
import java.util.Locale;

/**
 * Fires when the most recent candle's volume exceeds the rolling average
 * volume by a configurable multiplier.
 *
 * Algorithm
 * ─────────
 *  avgVolume = mean(volume[0..lookback-1])   (excludes the current candle)
 *  trigger   = volume[lookback] >= avgVolume * multiplier
 *
 * Examples
 * ─────────
 *  • 3× average volume on 1m BTC → "unusual buying/selling pressure"
 *  • 5× average volume on 1D SPY → "institutional accumulation day"
 *
 * Use case: detect institutional order flow, news-driven volume surges.
 */
public class VolumeSpikeCondition extends Condition {

    public static final String TYPE = "VOLUME_SPIKE";
    private static final Gson GSON = new Gson();

    @SerializedName("symbol")      private final String symbol;
    @SerializedName("timeframe")   private final StockDataHelper.Timeframe timeframe;
    @SerializedName("lookback")    private final int lookback;       // candles for avg baseline
    @SerializedName("multiplier")  private final double multiplier;  // e.g. 3.0 → 3× avg

    public VolumeSpikeCondition(String symbol, StockDataHelper.Timeframe timeframe,
                                int lookback, double multiplier) {
        this.symbol     = symbol;
        this.timeframe  = timeframe;
        this.lookback   = lookback;
        this.multiplier = multiplier;
    }

    @Override
    public boolean check(PriceResource resource) {
        // Need lookback baseline candles + 1 current candle
        List<Candle> history = resource.getCachedStockData(symbol, timeframe, lookback + 1);
        if (history == null || history.size() < lookback + 1) return false;

        // Average of the lookback baseline candles (all but the last)
        double avgVolume = 0;
        for (int i = 0; i < lookback; i++) avgVolume += history.get(i).volume;
        avgVolume /= lookback;

        if (avgVolume == 0) return false;

        double currentVolume = history.get(lookback).volume;
        return currentVolume >= avgVolume * multiplier;
    }

    @Override public String getTypeName() { return TYPE; }
    @Override public String serialize()   { return GSON.toJson(this); }
    public static Condition fromJson(String json) {
        return GSON.fromJson(json, VolumeSpikeCondition.class);
    }

    @Override
    public String getNotification() {
        return String.format(Locale.US,
                "Volume spike on %s! %.1f× the %d-candle average (%s)",
                symbol, multiplier, lookback, timeframe.value);
    }

    @Override
    public String getSummary() {
        return String.format(Locale.US, "Volume ≥ %.1f× avg(%d) — %s",
                multiplier, lookback, timeframe.value);
    }

    // Accessors for the edit UI — read the exact stored values (never re-parse getSummary()).
    public StockDataHelper.Timeframe getTimeframe()  { return timeframe; }
    public int                       getLookback()   { return lookback; }
    public double                    getMultiplier() { return multiplier; }
}
