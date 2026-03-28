package com.example.gutapp.session.Requests;

import com.example.gutapp.session.AsyncRequest;
import com.example.gutapp.session.Response;
import com.example.gutapp.session.SessionCallback;

public class SendOrder extends AsyncRequest {
    public SendOrder(SessionCallback caller) {
        super(caller);
    }

    @Override
    public byte[] getBytes() {
        return new byte[0];
    }

    @Override
    public void handle(Response response) {

    }
}
