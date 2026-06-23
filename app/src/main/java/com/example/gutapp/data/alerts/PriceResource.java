package com.example.gutapp.data.alerts;

import com.example.gutapp.data.models.Candle;
import com.example.gutapp.database.StockDataHelper;

import java.util.List;

/**
 * PriceResource — the read-only market-data contract that Condition
 * implementations use to fetch prices without knowing where data comes from.
 *
 * This separation keeps Condition classes testable in isolation: unit tests
 * can supply a mock PriceResource without touching the DB or network.
 *
 * The AlertManager implements this interface by combining:
 *  - an in-memory cache (latestPrices)   for the hot path (tick data)
 *  - a SQLite read-through               for historical candle windows
 */
public interface PriceResource {

    /**
     * Returns the most-recently-received tick candle for {@code symbol},
     * or null if no data has arrived yet.
     *
     * Implementations should never block; return null if uncertain.
     */
    Candle getLatestPrice(String symbol);

    /**
     * Returns the {@code count} most-recent candles for {@code symbol}
     * on {@code timeframe}, sorted oldest-first.
     *
     * Returns an empty list (never null) if fewer candles are available
     * than requested — callers should guard with size checks.
     */
    List<Candle> getCachedStockData(String symbol,
                                    StockDataHelper.Timeframe timeframe,
                                    int count);
}
