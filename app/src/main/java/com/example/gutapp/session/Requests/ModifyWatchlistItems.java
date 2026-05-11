package com.example.gutapp.session.Requests;

import com.example.gutapp.session.AsyncRequest;
import com.example.gutapp.session.DataType;
import com.example.gutapp.session.Flag;
import com.example.gutapp.session.RequestType;
import com.example.gutapp.session.Response;
import com.example.gutapp.session.Responses.WatchlistResponses;
import com.example.gutapp.session.SessionCallback;
import java.nio.ByteBuffer;

public class ModifyWatchlistItems extends AsyncRequest {
    public enum Action { ADD((byte)0), REMOVE((byte)1); public final byte v; Action(byte v){this.v=v;} }

    private final Action action;
    private final String listName;
    private final String symbol;

    public ModifyWatchlistItems(Action action, String listName, String symbol, SessionCallback caller) {
        super(caller);
        this.action = action;
        this.listName = listName;
        this.symbol = symbol;


    }

    @Override
    public byte[] getBytes() {
        byte[] listBytes = listName.getBytes();
        byte[] symBytes = symbol.getBytes();
        // Length: Flag(1) + Type(1) + ReqId(4) + Action(1) + ListLen(1) + ListData + SymLen(1) + SymData
        int length = 9 + listBytes.length + symBytes.length;

        ByteBuffer buffer = ByteBuffer.allocate(4 + length);
        buffer.putInt(length);
        buffer.put(Flag.ENCRYPTED.value);
        buffer.put(RequestType.MODIFY_WATCHLIST_ITEMS.value);
        buffer.put(reqId);
        buffer.put(action.v);
        buffer.put((byte)listBytes.length);
        buffer.put(listBytes);
        buffer.put((byte)symBytes.length);
        buffer.put(symBytes);
        return buffer.array();
    }

    @Override
    public void handle(Response response) {
        if (caller == null) return;
        WatchlistResponses.ActionStatus res = (WatchlistResponses.ActionStatus) response;
        caller.onDataReceived(DataType.WATCHLIST_OPERATION_RESULT, res.status);
    }
}