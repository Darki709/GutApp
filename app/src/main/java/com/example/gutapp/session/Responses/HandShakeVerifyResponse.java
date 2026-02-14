package com.example.gutapp.session.Responses;

import com.example.gutapp.session.Response;
import com.example.gutapp.session.ResponseType;

public class HandShakeVerifyResponse extends Response {
    private boolean isAccepted; //should be 1 if the server accepts the handshake

    public HandShakeVerifyResponse(byte[] response) {
        super(response[0]);
        ResponseType type = super.getType();
        this.isAccepted = (type == ResponseType.HANDSHAKESUCCESS);
    }

    public boolean isAccepted() {
        return isAccepted;
    }
}
