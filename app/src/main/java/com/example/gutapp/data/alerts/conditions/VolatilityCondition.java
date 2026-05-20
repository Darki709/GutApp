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
 * Fires when the absolute price move from the previous close to the current
 * close exceeds {@code percentThreshold} percent.
 *
 * Examples
 * ─────────
 *  • 2% move between consecutive 1m candles → scalp volatility alert
 *  • 5% move between consecutive 1D candles → macro volatility alert
 *
 * Use case: catch sudden price explosions regardless of direction.
 */
public class VolatilityCondition extends Condition {

    public static final String TYPE = "VOLATILITY";
    private static final Gson GSON = new Gson();

    @SerializedName("symbol")           private final String symbol;
    @SerializedName("percentThreshold") private final double percentThreshold;
    @SerializedName("timeframe")        private final StockDataHelper.Timeframe timeframe;

    public VolatilityCondition(String symbol, double percentThreshold,
                               StockDataHelper.Timeframe timeframe) {
        this.symbol           = symbol;
        this.percentThreshold = percentThreshold;
        this.timeframe        = timeframe;
    }

    @Override
    public boolean check(PriceResource resource) {
        List<Candle> history = resource.getCachedStockData(symbol, timeframe, 2);
        if (history == null || history.size() < 2) return false;

        double oldPrice = history.get(0).close;
        double newPrice = history.get(1).close;
        if (oldPrice == 0) return false;

        double move = Math.abs((newPrice - oldPrice) / oldPrice) * 100.0;
        return move >= percentThreshold;
    }

    @Override public String getTypeName() { return TYPE; }
    @Override public String serialize()   { return GSON.toJson(this); }
    public static Condition fromJson(String json) {
        return GSON.fromJson(json, VolatilityCondition.class);
    }

    @Override
    public String getNotification() {
        return String.format(Locale.US,
                "High volatility on %s! Move exceeded %.1f%% (%s)",
                symbol, percentThreshold, timeframe.value);
    }

    @Override
    public String getSummary() {
        return String.format(Locale.US, "|Δ| ≥ %.1f%% on %s", percentThreshold, timeframe.value);
    }
}
