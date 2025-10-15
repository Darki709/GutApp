package com.example.gutapp.data.chart;

import android.database.sqlite.SQLiteDatabase;

import com.example.gutapp.database.DB_Helper;
import com.example.gutapp.database.indicatorHelpers.SMA_DBHelper;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineDataSet;

import java.util.ArrayList;
import java.util.List;

public class IndicatorUtil {


    public static LineDataSet movingAverageDataSet(SQLiteDatabase db, List<float[]> prices, int period, String symbol, String id) {
        List<Entry> entries = new ArrayList<>();
        if (prices == null || prices.size() < period) return new LineDataSet(entries, symbol);

        for (int i = period - 1; i < prices.size(); i++) {
            float sum = 0f;
            for (int j = i - period + 1; j <= i; j++) {
                sum += prices.get(j)[1]; // sum y-values
            }
            float avg = sum / period;

            //cache data to the dedicated table
            SMA_DBHelper.insertSMA(db, symbol, prices.get(i)[0], avg, period);
            entries.add(new Entry(prices.get(i)[0], avg)); // x = last index, y = avg
        }

        LineDataSet set = new LineDataSet(entries, id);
        return set;
    }
}
