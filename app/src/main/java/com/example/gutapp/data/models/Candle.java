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

    public long getTimestamp() {
        return timestamp;
    }

    public CandleEntry getEntry(){
        return new CandleEntry(timestamp, (float)open, (float)high, (float)low, (float)close);
    }

    public BarEntry getVolumeEntry(){
        return new BarEntry(timestamp, (float)volume);
    }
}
