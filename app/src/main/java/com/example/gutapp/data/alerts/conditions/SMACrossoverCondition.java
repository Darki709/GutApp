package com.example.gutapp.data.alerts.conditions;

import com.example.gutapp.data.alerts.Condition;
import com.example.gutapp.data.alerts.PriceResource;
import com.example.gutapp.data.models.Candle;
import com.example.gutapp.database.StockDataHelper;
import com.google.gson.Gson;

import java.util.List;

public class SMACrossoverCondition extends Condition {
    private final String symbol;
    private final StockDataHelper.Timeframe timeframe;
    private final int period;

    public SMACrossoverCondition(String symbol, StockDataHelper.Timeframe timeframe, int period) {
        this.symbol = symbol;
        this.timeframe = timeframe;
        this.period = period;
    }

    @Override
    public boolean check(PriceResource resource) {
        // Request exactly 'period' amount of candles for the average
        List<Candle> history = resource.getCachedStockData(symbol, timeframe, period);

        if (history == null || history.size() < period) return false;

        double sum = 0;
        for (Candle c : history) {
            sum += c.close;
        }
        double sma = sum / period;

        // Trigger if current price is above the moving average
        Candle latest = resource.getLatestPrice(symbol);
        return latest != null && latest.close > sma;
    }

    @Override
    public String getNotification() {
        return symbol + " crossed above the " + period + "-period SMA on the " + timeframe + " chart.";
    }

    @Override
    public String serialize() {
        return new Gson().toJson(this);
    }
}