package com.example.gutapp.session.Requests;

import com.example.gutapp.session.AsyncRequest;
import com.example.gutapp.session.DataType;
import com.example.gutapp.session.Flag;
import com.example.gutapp.session.RequestType;
import com.example.gutapp.session.Response;
import com.example.gutapp.session.Responses.WatchlistResponses;
import com.example.gutapp.session.SessionCallback;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

public class GetWatchlistContent extends AsyncRequest {
    private final String listName;
    private final int offset;
    private final int limit;

    public GetWatchlistContent(String listName, int offset, int limit, SessionCallback caller) {
        super(caller);
        this.listName = listName;
        this.offset = offset;
        this.limit = limit;
    }

    @Override
    public byte[] getBytes() {
        byte[] nameBytes = listName.getBytes(StandardCharsets.US_ASCII);
        // Flag(1) + Type(1) + ReqId(4) + NameLen(1) + NameData + Offset(4) + Limit(4)
        int length = 7 + nameBytes.length + 8;

        ByteBuffer buffer = ByteBuffer.allocate(4 + length);
        buffer.putInt(length);
        buffer.put(Flag.ENCRYPTED.value);
        buffer.put(RequestType.GET_WATCHLIST_CONTENT.value);
        buffer.put(reqId);
        buffer.put((byte) nameBytes.length);
        buffer.put(nameBytes);
        buffer.putInt(offset); // New field
        buffer.putInt(limit);  // New field
        return buffer.array();
    }

    @Override
    public void handle(Response response) {
        if (caller == null) return;
        WatchlistResponses.Content res = (WatchlistResponses.Content) response;
        if (res.status == 0) {
            caller.onDataReceived(DataType.WATCHLIST_CONTENT_LOADED, res.tickers);
        } else {
            caller.onDataReceived(DataType.WATCHLIST_ERROR, "Failed to load content: " + res.status);
        }
    }
}