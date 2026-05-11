package com.example.gutapp.session.Requests;

import com.example.gutapp.session.AsyncRequest;
import com.example.gutapp.session.DataType;
import com.example.gutapp.session.Flag;
import com.example.gutapp.session.RequestType;
import com.example.gutapp.session.Response;
import com.example.gutapp.session.Responses.WatchlistResponses;
import com.example.gutapp.session.SessionCallback;
import java.nio.ByteBuffer;

public class ManageWatchlist extends AsyncRequest {
    public enum Action { CREATE((byte)0), RENAME((byte)1), DELETE((byte)2); public final byte v; Action(byte v){this.v=v;} }

    private final Action action;
    private final String name;
    private final String newName;

    public ManageWatchlist(Action action, String name, String newName, SessionCallback caller) {
        super(caller);
        this.action = action;
        this.name = name;
        this.newName = newName;
    }

    @Override
    public byte[] getBytes() {
        byte[] nameBytes = name.getBytes();
        // Base length: Flag(1) + Type(1) + ReqId(4) + Action(1) + NameLen(1) + NameData
        int length = 8 + nameBytes.length;
        if (action == Action.RENAME) {
            length += 1 + newName.length();
        }

        ByteBuffer buffer = ByteBuffer.allocate(4 + length);
        buffer.putInt(length);
        buffer.put(Flag.ENCRYPTED.value);
        buffer.put(RequestType.MANAGE_WATCHLIST.value);
        buffer.put(reqId); // Using the byte[] from parent Request class
        buffer.put(action.v);
        buffer.put((byte)nameBytes.length);
        buffer.put(nameBytes);

        if (action == Action.RENAME) {
            buffer.put((byte)newName.length());
            buffer.put(newName.getBytes());
        }
        return buffer.array();
    }

    @Override
    public void handle(Response response) {
        if (caller == null) return;
        WatchlistResponses.ActionStatus res = (WatchlistResponses.ActionStatus) response;
        caller.onDataReceived(DataType.WATCHLIST_OPERATION_RESULT, res.status);
    }
}