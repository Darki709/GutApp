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
 * Fires when the price moves by {@code percentThreshold}% or more in a
 * specified direction over the last completed candle on a given timeframe.
 *
 * Examples
 * ─────────
 *  • BTC moved UP   ≥ 2% on the 1H chart → "bullish momentum spike"
 *  • SPY moved DOWN ≥ 1% on the 5m chart → "flash crash warning"
 *  • Either direction ≥ 3% on 1D         → "big daily move"
 *
 * Use case: news-event alerts, momentum signals.
 */
public class PriceChangePercentCondition extends Condition {

    public static final String TYPE = "PRICE_CHANGE_PERCENT";
    private static final Gson GSON = new Gson();

    public enum Direction { UP, DOWN, EITHER }

    @SerializedName("symbol")           private final String symbol;
    @SerializedName("percentThreshold") private final double percentThreshold;
    @SerializedName("direction")        private final Direction direction;
    @SerializedName("timeframe")        private final StockDataHelper.Timeframe timeframe;

    public PriceChangePercentCondition(String symbol, double percentThreshold,
                                       Direction direction,
                                       StockDataHelper.Timeframe timeframe) {
        this.symbol           = symbol;
        this.percentThreshold = percentThreshold;
        this.direction        = direction;
        this.timeframe        = timeframe;
    }

    @Override
    public boolean check(PriceResource resource) {
        List<Candle> history = resource.getCachedStockData(symbol, timeframe, 2);
        if (history == null || history.size() < 2) return false;

        double prev = history.get(0).close;
        double curr = history.get(1).close;
        if (prev == 0) return false;

        double changePct = ((curr - prev) / prev) * 100.0;

        switch (direction) {
            case UP:     return changePct >=  percentThreshold;
            case DOWN:   return changePct <= -percentThreshold;
            case EITHER: return Math.abs(changePct) >= percentThreshold;
            default:     return false;
        }
    }

    @Override public String getTypeName() { return TYPE; }
    @Override public String serialize()   { return GSON.toJson(this); }
    public static Condition fromJson(String json) {
        return GSON.fromJson(json, PriceChangePercentCondition.class);
    }

    @Override
    public String getNotification() {
        String dir = direction == Direction.UP   ? "jumped up"
                   : direction == Direction.DOWN ? "dropped"
                   : "moved";
        return String.format(Locale.US, "%s %s ≥%.1f%% on %s",
                symbol, dir, percentThreshold, timeframe.value);
    }

    @Override
    public String getSummary() {
        return String.format(Locale.US, "%s move ≥ %.1f%% (%s)",
                direction.name().toLowerCase(), percentThreshold, timeframe.value);
    }

    // Accessors for the edit UI — read the exact stored values (never re-parse getSummary()).
    public StockDataHelper.Timeframe getTimeframe()        { return timeframe; }
    public double                    getPercentThreshold() { return percentThreshold; }
    public Direction                 getDirection()        { return direction; }
}
