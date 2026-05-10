package com.example.gutapp.data.models;

import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.data.CandleEntry;

import lombok.Setter;

public class Candle {
    public final long timestamp;
    public final double open;
    public final double high;
    public final double low;
    public final double close;
    public final long volume;
    public enum Direction {
        UP, DOWN
    }
    @Setter
    private Direction direction = null;


    public Candle(long timestamp, double open, double high, double low, double close, long volume) {
        this.timestamp = timestamp;
        this.open = open;
        this.high = high;
        this.low = low;
        this.close = close;
        this.volume = volume;
    }

    public Direction getDirection() throws IllegalStateException{
        if(direction == null) throw new IllegalStateException("Direction not set");
        return direction;
    }

    @Override
    public String toString(){
        return String.format("%d, %.4f, %.4f, %.4f, %.4f, %d", timestamp, open, high, low, close, volume);
    }
}
