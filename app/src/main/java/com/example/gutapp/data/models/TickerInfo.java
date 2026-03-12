package com.example.gutapp.data.models;

public class TickerInfo {
    public final String symbol;
    public final String name;
    public final int tickerId;

    public TickerInfo(String name, String symbol, int tickerId){
        this.symbol = symbol;
        this.name = name;
        this.tickerId = tickerId;
    }
}
