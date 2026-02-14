package com.example.gutapp.data.models;

import java.util.ArrayList;

public class PriceChunk {
    public final int reqId;
    public final ArrayList<Candle> chunk;
    public final boolean isLast;

    public PriceChunk(int reqId, ArrayList<Candle> chunk, boolean isLast) {
        this.reqId = reqId;
        this.chunk = chunk;
        this.isLast = isLast;
    }
}
