package com.example.gutapp.data.alerts.conditions;

import com.example.gutapp.data.alerts.Condition;
import com.example.gutapp.data.alerts.PriceResource;
import com.example.gutapp.data.models.Candle;
import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;

import java.util.Locale;

/**
 * Fires when the latest close price crosses a fixed threshold.
 *
 * Examples
 * ─────────
 *  • AAPL >= 200.00   (lookForAbove = true)
 *  • BTC  <= 50000.00 (lookForAbove = false)
 *
 * Use case: "alert me when EURUSD drops below 1.0700."
 */
public class PriceThresholdCondition extends Condition {

    public static final String TYPE = "PRICE_THRESHOLD";
    private static final Gson GSON = new Gson();

    @SerializedName("symbol")       private final String symbol;
    @SerializedName("targetPrice")  private final double targetPrice;
    @SerializedName("lookForAbove") private final boolean lookForAbove;

    public PriceThresholdCondition(String symbol, double targetPrice, boolean lookForAbove) {
        this.symbol       = symbol;
        this.targetPrice  = targetPrice;
        this.lookForAbove = lookForAbove;
    }

    @Override
    public boolean check(PriceResource resource) {
        Candle latest = resource.getLatestPrice(symbol);
        if (latest == null) return false;
        return lookForAbove ? latest.close >= targetPrice
                            : latest.close <= targetPrice;
    }

    @Override public String getTypeName()    { return TYPE; }
    @Override public String serialize()      { return GSON.toJson(this); }
    public static Condition fromJson(String json) { return GSON.fromJson(json, PriceThresholdCondition.class); }

    @Override
    public String getNotification() {
        return String.format(Locale.US, "%s %s %.5f",
                symbol, lookForAbove ? "rose above" : "dropped below", targetPrice);
    }

    @Override
    public String getSummary() {
        return String.format(Locale.US, "Price %s %.5f",
                lookForAbove ? "≥" : "≤", targetPrice);
    }

    // Accessors for UI
    public String  getSymbol()       { return symbol; }
    public double  getTargetPrice()  { return targetPrice; }
    public boolean isLookForAbove()  { return lookForAbove; }
}
