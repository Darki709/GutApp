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
 * Fires when the current close crosses a Simple Moving Average (SMA).
 *
 * Crossing is detected by comparing:
 *  - previous close vs. SMA(period-1 + previous)  → was below?
 *  - current  close vs. SMA(period candles)        → is now above?
 *
 * A true "crossover" only fires on the candle where the cross happens,
 * not on every subsequent candle while price stays above.  This prevents
 * the PERSISTENT repeat mode from spamming after an initial crossover.
 *
 * Examples
 * ─────────
 *  • 20-SMA golden cross on 1H BTCUSD
 *  • 200-SMA support test on 1D EURUSD
 *
 * Use case: trend-following entry/exit signals.
 */
public class SMACrossoverCondition extends Condition {

    public static final String TYPE = "SMA_CROSSOVER";
    private static final Gson GSON = new Gson();

    public enum CrossDirection { ABOVE, BELOW }

    @SerializedName("symbol")        private final String symbol;
    @SerializedName("timeframe")     private final StockDataHelper.Timeframe timeframe;
    @SerializedName("period")        private final int period;
    @SerializedName("crossDirection") private final CrossDirection crossDirection;

    public SMACrossoverCondition(String symbol, StockDataHelper.Timeframe timeframe,
                                 int period, CrossDirection crossDirection) {
        this.symbol        = symbol;
        this.timeframe     = timeframe;
        this.period        = period;
        this.crossDirection = crossDirection;
    }

    @Override
    public boolean check(PriceResource resource) {
        // Need period+1 candles to detect a cross (need the previous candle too)
        List<Candle> history = resource.getCachedStockData(symbol, timeframe, period + 1);
        if (history == null || history.size() < period + 1) return false;

        // SMA over the last 'period' candles (indices [1..period])
        double sma = 0;
        for (int i = 1; i <= period; i++) sma += history.get(i).close;
        sma /= period;

        // Previous SMA (indices [0..period-1])
        double prevSma = 0;
        for (int i = 0; i < period; i++) prevSma += history.get(i).close;
        prevSma /= period;

        double currClose = history.get(period).close;
        double prevClose = history.get(period - 1).close;

        if (crossDirection == CrossDirection.ABOVE) {
            // Crossed above: previous close was below prevSma, current close is above sma
            return prevClose < prevSma && currClose >= sma;
        } else {
            // Crossed below
            return prevClose > prevSma && currClose <= sma;
        }
    }

    @Override public String getTypeName() { return TYPE; }
    @Override public String serialize()   { return GSON.toJson(this); }
    public static Condition fromJson(String json) {
        return GSON.fromJson(json, SMACrossoverCondition.class);
    }

    @Override
    public String getNotification() {
        return String.format(Locale.US, "%s crossed %s the %d-period SMA (%s)",
                symbol,
                crossDirection == CrossDirection.ABOVE ? "above" : "below",
                period, timeframe.value);
    }

    @Override
    public String getSummary() {
        return String.format(Locale.US, "SMA(%d) cross %s — %s",
                period,
                crossDirection == CrossDirection.ABOVE ? "↑" : "↓",
                timeframe.value);
    }
}
