package com.example.gutapp.session.Responses;

import static com.example.gutapp.session.Connection.NETWORK_LOG_TAG;

import android.util.Log;

import com.example.gutapp.session.Response;
import com.example.gutapp.session.SessionManager;

public class HandShakeHelloResponse extends Response {
    private byte[] payload; //the aes key encrypted with rsa public key


    public HandShakeHelloResponse(byte[] response) {
        super(response[0]);
        payload = new byte[response.length - 1];
        System.arraycopy(response, 1, payload, 0, payload.length);
        Log.i(NETWORK_LOG_TAG, "Received handshake hello response");
    }

    public byte[] getPayload(){
        return this.payload;
    }
}
