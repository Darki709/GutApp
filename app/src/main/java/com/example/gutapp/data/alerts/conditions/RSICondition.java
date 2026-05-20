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
 * Fires when the Relative Strength Index (RSI) crosses a level threshold.
 *
 * RSI is computed using Wilder's smoothing method over {@code period} candles.
 * Requires period+1 candles from the DB (period gains/losses + one look-back).
 *
 * Examples
 * ─────────
 *  • RSI(14) drops below 30  → oversold, potential long entry
 *  • RSI(14) rises above 70  → overbought, potential short entry
 *  • RSI(7)  drops below 20  → extreme oversold on scalp timeframe
 *
 * Use case: mean-reversion alerts, overbought/oversold scanners.
 */
public class RSICondition extends Condition {

    public static final String TYPE = "RSI";
    private static final Gson GSON = new Gson();

    public enum CrossDirection { ABOVE, BELOW }

    @SerializedName("symbol")        private final String symbol;
    @SerializedName("timeframe")     private final StockDataHelper.Timeframe timeframe;
    @SerializedName("period")        private final int period;
    @SerializedName("level")         private final double level;
    @SerializedName("crossDirection") private final CrossDirection crossDirection;

    public RSICondition(String symbol, StockDataHelper.Timeframe timeframe,
                        int period, double level, CrossDirection crossDirection) {
        this.symbol        = symbol;
        this.timeframe     = timeframe;
        this.period        = period;
        this.level         = level;
        this.crossDirection = crossDirection;
    }

    @Override
    public boolean check(PriceResource resource) {
        // Need period+1 closes to compute period gains/losses
        List<Candle> history = resource.getCachedStockData(symbol, timeframe, period + 1);
        if (history == null || history.size() < period + 1) return false;

        double rsi = computeRsi(history);

        return crossDirection == CrossDirection.BELOW ? rsi <= level : rsi >= level;
    }

    /** Wilder's smoothed RSI over the supplied candle list (oldest-first). */
    private double computeRsi(List<Candle> candles) {
        double avgGain = 0, avgLoss = 0;

        // Seed with simple averages for the first period
        for (int i = 1; i <= period; i++) {
            double change = candles.get(i).close - candles.get(i - 1).close;
            if (change > 0) avgGain += change;
            else            avgLoss += Math.abs(change);
        }
        avgGain /= period;
        avgLoss /= period;

        if (avgLoss == 0) return 100.0;
        double rs = avgGain / avgLoss;
        return 100.0 - (100.0 / (1.0 + rs));
    }

    @Override public String getTypeName() { return TYPE; }
    @Override public String serialize()   { return GSON.toJson(this); }
    public static Condition fromJson(String json) {
        return GSON.fromJson(json, RSICondition.class);
    }

    @Override
    public String getNotification() {
        return String.format(Locale.US,
                "RSI(%d) on %s %s %.0f (%s chart)",
                period, symbol,
                crossDirection == CrossDirection.BELOW ? "dropped below" : "rose above",
                level, timeframe.value);
    }

    @Override
    public String getSummary() {
        return String.format(Locale.US, "RSI(%d) %s %.0f — %s",
                period,
                crossDirection == CrossDirection.BELOW ? "≤" : "≥",
                level, timeframe.value);
    }
}
