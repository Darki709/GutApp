package com.example.gutapp.session.Requests;

import com.example.gutapp.session.AsyncRequest;
import com.example.gutapp.session.Flag;
import com.example.gutapp.session.RequestType;
import com.example.gutapp.session.Response;
import com.example.gutapp.session.SessionCallback;

import java.nio.ByteBuffer;

public class SearchTicker extends AsyncRequest {
    private final String searchQuery;
    private final boolean isFast;
    private final char limit;
    private final int lastTickerID;
    private final SessionCallback caller;

    public SearchTicker(String searchQuery, char limit, int lastTickerID ,SessionCallback caller) {
        super(caller);
        if(searchQuery.length() > 255) throw new IllegalArgumentException("Search query too long");
        this.searchQuery = searchQuery;
        this.isFast = false;
        this.limit = limit;
        this.lastTickerID = lastTickerID;
        this.caller = caller;
    }

    public SearchTicker(String searchQuery,SessionCallback caller) {
        super(caller);
        if(searchQuery.length() > 255) throw new IllegalArgumentException("Search query too long");
        this.searchQuery = searchQuery;
        this.isFast = true;
        this.limit = 0;
        this.lastTickerID = 0;
        this.caller = caller;
    }

    @Override
    public byte[] getBytes() {
        int length = 2 + reqId.length + 1 + searchQuery.length() + 1 + (isFast ? 0 : 5);
        ByteBuffer buffer = ByteBuffer.allocate(length + 4); //add length header bytes
        buffer.putInt(length);
        buffer.put(Flag.PLAINTEXT.value);
        buffer.put(RequestType.SEARCHTICKER.value);
        buffer.put(reqId);
        buffer.put((byte)searchQuery.length());
        buffer.put(searchQuery.getBytes());
        buffer.put((byte)(isFast ? 1 : 0));
        if (!isFast){
            buffer.put((byte)limit);
            buffer.putInt(lastTickerID);
        }
        return new byte[0];
    }

    @Override
    public void handle(Response response) {
        if(this.caller == null) {
            this.isDone = true;
            return;
        }

    }
}
