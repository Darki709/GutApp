package com.example.gutapp.session.Responses;

import static com.example.gutapp.ui.ChartActivity.CHART_LOG_TAG;

import android.util.Log;

import com.example.gutapp.data.models.Candle;
import com.example.gutapp.session.AsyncResponse;

import java.nio.ByteBuffer;
import java.util.Arrays;

public class StreamResponse extends AsyncResponse {

    long ts;
    double open;
    double high;
    double low;
    double close;
    long volume;

    public StreamResponse(byte[] response) {
        super(response[0], Arrays.copyOfRange(response, 1, 5));
        ByteBuffer buffer = ByteBuffer.wrap(response, 5, response.length-5);
        ts = buffer.getLong();
        open = buffer.getDouble();
        high = buffer.getDouble();
        low = buffer.getDouble();
        close = buffer.getDouble();
        volume = buffer.getLong();
    }

    public Candle getCandle() {
        return new Candle(ts, open, high, low, close, volume);
    }
}
