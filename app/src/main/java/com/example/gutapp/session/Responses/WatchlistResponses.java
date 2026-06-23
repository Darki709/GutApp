package com.example.gutapp.session.Responses;

import com.example.gutapp.session.AsyncResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class WatchlistResponses {

    public static class Summary extends AsyncResponse {
        public final byte status;
        public final List<WatchlistInfo> lists = new ArrayList<>();

        public Summary(byte[] response) {
            super(response[0], Arrays.copyOfRange(response, 1, 5));
            ByteBuffer buffer = ByteBuffer.wrap(response, 5, response.length - 5);
            this.status = buffer.get();
            if (this.status == 0) {
                int count = buffer.get() & 0xFF;
                for (int i = 0; i < count; i++) {
                    int id = buffer.getInt();
                    int len = buffer.get() & 0xFF;
                    byte[] nameBytes = new byte[len];
                    buffer.get(nameBytes);
                    lists.add(new WatchlistInfo(id, new String(nameBytes, StandardCharsets.US_ASCII)));
                }
            }
        }
    }

    public static class ActionStatus extends AsyncResponse {
        public final byte status;
        public ActionStatus(byte[] response) {
            super(response[0], Arrays.copyOfRange(response, 1, 5));
            this.status = response[5];
        }
    }

    public static class Content extends AsyncResponse {
        public final byte status;
        public final List<String> tickers = new ArrayList<>();

        public Content(byte[] response) {
            super(response[0], Arrays.copyOfRange(response, 1, 5));
            ByteBuffer buffer = ByteBuffer.wrap(response, 5, response.length - 5);
            this.status = buffer.get();
            if (this.status == 0) {
                int count = buffer.get() & 0xFF;
                for (int i = 0; i < count; i++) {
                    int len = buffer.get() & 0xFF;
                    byte[] symBytes = new byte[len];
                    buffer.get(symBytes);
                    tickers.add(new String(symBytes, StandardCharsets.US_ASCII));
                }
            }
        }
    }

    public static class WatchlistInfo {
        public final int id;
        public final String name;
        public WatchlistInfo(int id, String name) { this.id = id; this.name = name; }
    }

    public static String translate(byte status) {
        switch (status) {
            case 0:
                return "Operation completed successfully.";
            case 1:
                return "The selected watchlist or ticker could not be found.";
            case 2:
                return "This item already exists (Duplicate name or ticker).";
            case 3:
                return "Invalid ticker symbol. Please check the spelling.";
            case 4:
                return "Database error. Please try again later.";
            case 5:
                return "Session expired. Please log in again.";
            default:
                return "An unexpected error occurred (Unknown Code: " + status + ").";
        }
    }
}