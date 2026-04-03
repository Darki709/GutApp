package com.example.gutapp.session.Requests;

import androidx.annotation.Nullable;

import com.example.gutapp.data.models.Order;
import com.example.gutapp.session.AsyncRequest;
import com.example.gutapp.session.DataType;
import com.example.gutapp.session.Flag;
import com.example.gutapp.session.RequestType;
import com.example.gutapp.session.Response;
import com.example.gutapp.session.ResponseType;
import com.example.gutapp.session.Responses.FetchOrdersResponse;
import com.example.gutapp.session.SessionCallback;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

public class FetchOrders extends AsyncRequest {
    private String symbol;
    private OrderView view;
    private int limit;
    private int offset;

    public enum OrderView {
        ALL((byte)3), ACTIVE((byte)0), INACTIVE((byte)1);
        public final byte value;
        OrderView(byte value) { this.value = value; }
    }

    public FetchOrders(@Nullable String symbol, OrderView view, int offset, SessionCallback caller) {
        super(caller); //
        this.symbol = symbol;
        this.view = view;
        this.limit = 100;
        this.offset = offset;
    }

    @Override
    public byte[] getBytes() {
        byte[] symBytes = (symbol != null) ? symbol.getBytes() : new byte[0];

        // Header (6) + symLen (1) + symbol + view (1) + limit (4) + offset (4)
        int symLen = symbol == null ? 0 : symbol.length();
        int length = 16 + symLen;
        ByteBuffer buffer = ByteBuffer.allocate(4 + length);

        buffer.putInt(length);
        buffer.put(Flag.ENCRYPTED.value);
        buffer.put(RequestType.FETCHORDERS.value);
        buffer.put(reqId);

        // Payload matches FetchOrdersTask constructor logic
        buffer.put((byte) symLen);
        if (symLen > 0) {
            buffer.put(symBytes);
        }
        buffer.put(view.value);
        buffer.putInt(limit);
        buffer.putInt(offset);

        return buffer.array();
    }

    @Override
    public void handle(Response response) {
        if (caller == null) return;
        FetchOrdersResponse batch = (FetchOrdersResponse) response;

        // Data is already parsed and ready
        ArrayList<Order> fetchedOrders = batch.getOrders();

        FetchOrdersResponse.debugPrintOrders(fetchedOrders);

        // Send to UI
        caller.onDataReceived(DataType.ORDERS_BATCH, fetchedOrders);

        isDone = true;
    }
}
