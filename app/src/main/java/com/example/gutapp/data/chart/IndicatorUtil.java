package com.example.gutapp.data.chart;

import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

import com.example.gutapp.database.DB_Helper;
import com.example.gutapp.database.StockDataHelper;
import com.example.gutapp.database.IndicatorDBHelper;
import com.example.gutapp.database.indicatorHelpers.BollingerBands_DBHelper;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineDataSet;

import java.util.ArrayList;
import java.util.List;

public class IndicatorUtil {

    public static LineDataSet movingAverageDataSet(SQLiteDatabase db, List<float[]> prices, int period, String symbol, String id, StockDataHelper.Timeframe timeframe, String indicatorName) {
        List<Entry> entries = new ArrayList<>();
        if (prices == null || prices.size() < period) return new LineDataSet(entries, symbol);

        // --- PERFORMANCE FIX: WRAP LOOP IN A TRANSACTION ---
        db.beginTransaction(); // <-- 1. Begin the transaction
        try {
            for (int i = period - 1; i < prices.size(); i++) {
                float sum = 0f;
                for (int j = i - period + 1; j <= i; j++) {
                    sum += prices.get(j)[1]; // sum y-values
                }
                float avg = sum / period;

                // Pass timeframe when caching data (this is now extremely fast)
                IndicatorDBHelper.insertIndicatorData(db, symbol, i, avg, period, timeframe, indicatorName);
                entries.add(new Entry(prices.get(i)[0], avg));
            }
            db.setTransactionSuccessful(); // <-- 2. Mark transaction as successful
        } finally {
            db.endTransaction(); // <-- 3. End the transaction (commits if successful, rolls back otherwise)
        }
        // ----------------------------------------------------

        LineDataSet set = new LineDataSet(entries, id);
        return set;
    }

    public static LineDataSet exponentialMovingAverageDataSet(SQLiteDatabase db, List<float[]> prices, int period, String symbol, String id, StockDataHelper.Timeframe timeframe, String indicatorName) {
        List<Entry> entries = new ArrayList<>();
        if (prices == null || prices.isEmpty()) return new LineDataSet(entries, symbol);

        float multiplier = 2.0f / (period + 1);
        float ema = prices.get(0)[1]; // Start with the first price

        db.beginTransaction();
        try {
            // Insert the first EMA value
            IndicatorDBHelper.insertIndicatorData(db, symbol, 0, ema, period, timeframe, indicatorName);
            entries.add(new Entry(prices.get(0)[0], ema));

            for (int i = period - 1; i < prices.size(); i++) {
                ema = (prices.get(i)[1] - ema) * multiplier + ema;
                IndicatorDBHelper.insertIndicatorData(db, symbol, i, ema, period, timeframe, indicatorName);
                entries.add(new Entry(prices.get(i)[0], ema));
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }

        LineDataSet set = new LineDataSet(entries, id);
        return set;
    }

    public static List<LineDataSet> bollingerBandsDataSet(SQLiteDatabase db, List<float[]> prices, int period, float stdDevMultiplier, String symbol, String id, StockDataHelper.Timeframe timeframe) {
        List<Entry> middleBandEntries = new ArrayList<>();
        List<Entry> upperBandEntries = new ArrayList<>();
        List<Entry> lowerBandEntries = new ArrayList<>();
        List<LineDataSet> allBandsDataSets = new ArrayList<>();

        if (prices == null || prices.size() < period) {
            allBandsDataSets.add(new LineDataSet(middleBandEntries, id + "_middle"));
            allBandsDataSets.add(new LineDataSet(upperBandEntries, id + "_upper"));
            allBandsDataSets.add(new LineDataSet(lowerBandEntries, id + "_lower"));
            return allBandsDataSets;
        }

        db.beginTransaction();
        try {
            for (int i = period - 1; i < prices.size(); i++) {
                // Calculate SMA (Middle Band)
                float sum = 0f;
                for (int j = i - period + 1; j <= i; j++) {
                    sum += prices.get(j)[1];
                }
                float middleBand = sum / period;

                // Calculate Standard Deviation
                float varianceSum = 0f;
                for (int j = i - period + 1; j <= i; j++) {
                    varianceSum += Math.pow(prices.get(j)[1] - middleBand, 2);
                }
                float standardDeviation = (float) Math.sqrt(varianceSum / period);

                // Calculate Upper and Lower Bands
                float upperBand = middleBand + (standardDeviation * stdDevMultiplier);
                float lowerBand = middleBand - (standardDeviation * stdDevMultiplier);

                // Cache the result (using sequential index 'i' as x-value)
                BollingerBands_DBHelper.insertBollingerBands(
                        db, symbol, i, middleBand, upperBand, lowerBand,
                        period, stdDevMultiplier, timeframe
                );

                // Add to entries list (using sequential index 'i' as x-value)
                middleBandEntries.add(new Entry(i, middleBand));
                upperBandEntries.add(new Entry(i, upperBand));
                lowerBandEntries.add(new Entry(i, lowerBand));
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }

        allBandsDataSets.add(new LineDataSet(middleBandEntries, id + "_middle"));
        allBandsDataSets.add(new LineDataSet(upperBandEntries, id + "_upper"));
        allBandsDataSets.add(new LineDataSet(lowerBandEntries, id + "_lower"));
        return allBandsDataSets;
    }
}
