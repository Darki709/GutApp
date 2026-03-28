package com.example.gutapp.session.Responses;

import static com.example.gutapp.session.Connection.NETWORK_LOG_TAG;

import android.util.Log;

import com.example.gutapp.data.models.TickerInfo;
import com.example.gutapp.session.AsyncResponse;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;

public class SearchTickerResponse extends AsyncResponse {
    private final ArrayList<TickerInfo> tickers = new ArrayList<>();
    private final boolean found;

    public SearchTickerResponse(byte[] response) {
        super(response[0], Arrays.copyOfRange(response, 1, 5));
        ByteBuffer buffer = ByteBuffer.wrap(response, 5, response.length-5);
        int count = buffer.get() & 0xFF;
        found = count != 0;
        Log.i(NETWORK_LOG_TAG, String.format("Search ticker response count: %d" ,count));
        for (int i = 0; i < count; i++) {
            int namelen = buffer.get() & 0xFF;
            byte[] nameBytes = new byte[namelen];
            buffer.get(nameBytes);
            String name = new String(nameBytes, StandardCharsets.US_ASCII);
            int symbolLen = buffer.get() & 0xFF;
            byte[] symbolBytes = new byte[symbolLen];
            buffer.get(symbolBytes);
            String symbol = new String(symbolBytes, StandardCharsets.US_ASCII);
            tickers.add(new TickerInfo(name, symbol, buffer.getInt()));
            }
    }
    public boolean isFound() {return found;} //if there are no results this will return false

    public ArrayList<TickerInfo> getTickers() {
        return tickers;
    }
}
