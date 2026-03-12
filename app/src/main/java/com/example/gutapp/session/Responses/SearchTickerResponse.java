package com.example.gutapp.session.Responses;

import com.example.gutapp.data.models.TickerInfo;
import com.example.gutapp.session.AsyncResponse;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;

public class SearchTickerResponse extends AsyncResponse {
    private final ArrayList<TickerInfo> tickers = new ArrayList<>();

    public SearchTickerResponse(byte[] response) {
        super(response[0], Arrays.copyOfRange(response, 1, 5));
        ByteBuffer buffer = ByteBuffer.wrap(response, 5, response.length);
        int count = buffer.getInt();
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

    public ArrayList<TickerInfo> getTickers() {
        return tickers;
    }
}
