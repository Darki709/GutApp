package com.example.gutapp.data.alerts.conditions;

import com.example.gutapp.data.alerts.Condition;
import com.example.gutapp.data.alerts.PriceResource;
import com.example.gutapp.data.models.Candle;
import com.google.gson.Gson;

public class PriceThresholdCondition extends Condition {
    private final String symbol;
    private final double targetPrice;
    private final boolean lookForAbove; // true for "Greater than", false for "Less than"

    public PriceThresholdCondition(String symbol, double targetPrice, boolean lookForAbove) {
        this.symbol = symbol;
        this.targetPrice = targetPrice;
        this.lookForAbove = lookForAbove;
    }

    @Override
    public boolean check(PriceResource resource) {
        Candle latest = resource.getLatestPrice(symbol);
        if (latest == null) return false;

        return lookForAbove ? latest.close >= targetPrice : latest.close <= targetPrice;
    }

    @Override
    public String getNotification() {
        return symbol + (lookForAbove ? " rose above " : " dropped below ") + targetPrice;
    }

    @Override
    public String serialize() {
        return new Gson().toJson(this);
    }
}