package com.example.gutapp.data.models;

import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.data.CandleEntry;

public class Candle {
    public final long timestamp;
    public final double open;
    public final double high;
    public final double low;
    public final double close;
    public final long volume;

    public Candle(long timestamp, double open, double high, double low, double close, long volume) {
        this.timestamp = timestamp;
        this.open = open;
        this.high = high;
        this.low = low;
        this.close = close;
        this.volume = volume;
    }

    @Override
    public String toString(){
        return String.format("%d, %.4f, %.4f, %.4f, %.4f, %d", timestamp, open, high, low, close, volume);
    }
}
