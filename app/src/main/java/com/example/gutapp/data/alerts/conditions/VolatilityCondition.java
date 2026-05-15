package com.example.gutapp.data.alerts.conditions;

import com.example.gutapp.data.alerts.Condition;
import com.example.gutapp.data.alerts.PriceResource;
import com.example.gutapp.data.models.Candle;
import com.example.gutapp.database.StockDataHelper;
import com.google.gson.Gson;

import java.util.List;

public class VolatilityCondition extends Condition {
    private final String symbol;
    private final double percentThreshold; // e.g., 2.0 for 2%
    private final StockDataHelper.Timeframe timeframe;

    public VolatilityCondition(String symbol, double percentThreshold, StockDataHelper.Timeframe timeframe) {
        this.symbol = symbol;
        this.percentThreshold = percentThreshold;
        this.timeframe = timeframe;
    }

    @Override
    public boolean check(PriceResource resource) {
        // Look at the last 2 candles to see the immediate move
        List<Candle> history = resource.getCachedStockData(symbol, timeframe, 2);

        if (history == null || history.size() < 2) return false;

        double oldPrice = history.get(0).close; // Previous candle
        double newPrice = history.get(1).close; // Current candle

        double move = Math.abs((newPrice - oldPrice) / oldPrice) * 100;
        return move >= percentThreshold;
    }

    @Override
    public String getNotification() {
        return "High volatility detected for " + symbol + "! Move exceeded " + percentThreshold + "%.";
    }

    @Override
    public String serialize() {
        return new Gson().toJson(this);
    }
}
