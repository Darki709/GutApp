package com.example.gutapp.session.Responses;

import static com.example.gutapp.session.Connection.NETWORK_LOG_TAG;

import android.util.Log;

import com.example.gutapp.data.models.Candle;
import com.example.gutapp.session.AsyncResponse;
import com.github.mikephil.charting.data.CandleEntry;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;

public class SnapshotResponse extends AsyncResponse {
    private final boolean isDone;
    private final ArrayList<Candle> entries;


    public SnapshotResponse(byte[] response) {
        super(response[0], Arrays.copyOfRange(response, 1, 5));
        ByteBuffer buf = ByteBuffer.wrap(Arrays.copyOfRange(response, 5, response.length));
        buf.order(ByteOrder.BIG_ENDIAN);
        this.isDone = buf.get() == 0x01;
        int candleCount = buf.getShort();
        //Log.d(NETWORK_LOG_TAG, "SnapshotResponse: " + candleCount + " candles");
        this.entries = new ArrayList<>(candleCount);
        for (int i = 0; i < candleCount; i++) {
            long timestamp = buf.getLong();  // 8 bytes
            double open = buf.getDouble();   // 8 bytes
            double high = buf.getDouble();   // 8 bytes
            double low = buf.getDouble();    // 8 bytes
            double close = buf.getDouble();  // 8 bytes
            long volume = buf.getLong();     // 8 bytes
            this.entries.add(new Candle(timestamp, open, high, low, close, volume));
            //Log.d(NETWORK_LOG_TAG, "Parsing candle: " + timestamp + " " + open + " " + high + " " + low + " " + close + " " + volume);
        }
    }

    public boolean isFetchError(){
        return isDone && entries.isEmpty();
    }

    public boolean isDone() {
        return isDone;
    }

    public ArrayList<Candle> getEntries() {
        return entries;
    }
}
