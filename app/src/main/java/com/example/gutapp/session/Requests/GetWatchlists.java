package com.example.gutapp.session.Requests;

import com.example.gutapp.session.AsyncRequest;
import com.example.gutapp.session.DataType;
import com.example.gutapp.session.Flag;
import com.example.gutapp.session.RequestType;
import com.example.gutapp.session.Response;
import com.example.gutapp.session.Responses.WatchlistResponses;
import com.example.gutapp.session.SessionCallback;
import java.nio.ByteBuffer;

public class GetWatchlists extends AsyncRequest {

    public GetWatchlists(SessionCallback caller) {
        super(caller);
    }

    @Override
    public byte[] getBytes() {
        int payloadLength = 5; // [1B Flag][1B Type][4B ReqId] - server side logic check
        // Note: Your SendOrder uses 4 + length, where length includes Flag.
        ByteBuffer buffer = ByteBuffer.allocate(4 + 6);
        buffer.putInt(6);
        buffer.put(Flag.ENCRYPTED.value);
        buffer.put(RequestType.FETCH_WATCHLISTS.value);
        buffer.put(reqId);
        return buffer.array();
    }

    @Override
    public void handle(Response response) {
        if (caller == null) return;
        WatchlistResponses.Summary res = (WatchlistResponses.Summary) response;
        if (res.status == 0) {
            caller.onDataReceived(DataType.WATCHLISTS_LOADED, res.lists);
        } else {
            caller.onDataReceived(DataType.WATCHLIST_ERROR, "Failed to load watchlists: " + res.status);
        }
    }
}