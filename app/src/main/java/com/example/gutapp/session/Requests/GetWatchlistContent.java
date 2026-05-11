package com.example.gutapp.session.Requests;

import com.example.gutapp.session.AsyncRequest;
import com.example.gutapp.session.DataType;
import com.example.gutapp.session.Flag;
import com.example.gutapp.session.RequestType;
import com.example.gutapp.session.Response;
import com.example.gutapp.session.Responses.WatchlistResponses;
import com.example.gutapp.session.SessionCallback;
import java.nio.ByteBuffer;

public class GetWatchlistContent extends AsyncRequest {
    private final String listName;

    public GetWatchlistContent(String listName, SessionCallback caller) {
        super(caller);
        this.listName = listName;
    }

    @Override
    public byte[] getBytes() {
        byte[] nameBytes = listName.getBytes();
        // Length: Flag(1) + Type(1) + ReqId(4) + NameLen(1) + NameData
        int length = 7 + nameBytes.length;

        ByteBuffer buffer = ByteBuffer.allocate(4 + length);
        buffer.putInt(length);
        buffer.put(Flag.ENCRYPTED.value);
        buffer.put(RequestType.GET_WATCHLIST_CONTENT.value);
        buffer.put(reqId);
        buffer.put((byte)nameBytes.length);
        buffer.put(nameBytes);
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