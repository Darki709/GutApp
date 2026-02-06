package com.example.gutapp.session.Requests;

import static com.example.gutapp.session.Connection.NETWORK_LOG_TAG;

import android.util.Log;

import com.example.gutapp.session.AsyncRequest;
import com.example.gutapp.session.Flag;
import com.example.gutapp.session.RequestType;
import com.example.gutapp.session.Response;
import com.example.gutapp.session.SessionCallback;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

public class CancelTickerStream extends AsyncRequest {
    private final String symbol;
    private final byte[] previousReqId;

    //reqId and symbol are used to identify the request in the server side


    public CancelTickerStream(byte[] reqId, String symbol) {
        super(null);
        this.isDone = true;
        this.previousReqId = reqId;
        this.symbol = symbol;
        Log.i(NETWORK_LOG_TAG, "CancelTickerStream: " + symbol + " " + ByteBuffer.wrap(reqId).getInt());
    }

    @Override
    public byte[] getBytes() {
        byte[] symbolBytes = symbol.getBytes(StandardCharsets.US_ASCII);
        int length = 1 + 1 + 4 + 4 + 1 + symbolBytes.length;
        ByteBuffer buffer = ByteBuffer.allocate(4 + length);
        buffer.order(ByteOrder.BIG_ENDIAN);
        buffer.putInt(length);
        buffer.put(Flag.ENCRYPTED.value);
        buffer.put(RequestType.CANCELTICKERSTREAM.value);
        buffer.put(reqId);
        buffer.put(previousReqId);
        buffer.put((byte) symbolBytes.length);
        buffer.put(symbolBytes);
        return buffer.array();
    }

    @Override
    public void handle(Response response) {
        //not needed here
    }
}
