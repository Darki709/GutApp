package com.example.gutapp.session.Requests;

import androidx.annotation.Nullable;

import com.example.gutapp.data.UserGlobals;
import com.example.gutapp.session.AsyncRequest;
import com.example.gutapp.session.Flag;
import com.example.gutapp.session.RequestType;
import com.example.gutapp.session.Response;
import com.example.gutapp.session.Responses.GetBalanceResponse;
import com.example.gutapp.session.SessionCallback;

import java.nio.ByteBuffer;

public class GetBalance extends AsyncRequest {

    public GetBalance() {
        super(null);
    }

    @Override
    public byte[] getBytes() {
        int length = 6;
        ByteBuffer buffer = ByteBuffer.allocate(4 + length);
        buffer.putInt(length);
        buffer.put(Flag.ENCRYPTED.value);
        buffer.put(RequestType.GETBALANCE.value);
        buffer.put(reqId);
        return buffer.array();
    }

    @Override
    public void handle(Response response) {
        UserGlobals.updateBalance(((GetBalanceResponse) response).balance);
        this.isDone = true;
    }
}
