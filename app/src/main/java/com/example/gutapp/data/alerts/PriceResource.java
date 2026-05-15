package com.example.gutapp.data.alerts;

import com.example.gutapp.data.models.Candle;
import com.example.gutapp.database.StockDataHelper;

import java.util.List;

public interface PriceResource {
    Candle getLatestPrice(String symbol);

    List<Candle> getCachedStockData(String symbol, StockDataHelper.Timeframe timeframe, int count);
}
