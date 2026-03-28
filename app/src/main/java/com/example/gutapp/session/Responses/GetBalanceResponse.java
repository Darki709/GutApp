package com.example.gutapp.session.Responses;

import static com.example.gutapp.session.Connection.NETWORK_LOG_TAG;

import android.util.Log;

import com.example.gutapp.session.AsyncResponse;

import java.nio.ByteBuffer;
import java.util.Arrays;

public class GetBalanceResponse extends AsyncResponse {

    public final double balance;

    public GetBalanceResponse(byte[] response) {
        super(response[0], Arrays.copyOfRange(response, 1, 5));
        ByteBuffer buffer = ByteBuffer.wrap(response, 5, response.length - 5);
        balance = buffer.getDouble();
        Log.i(NETWORK_LOG_TAG, "Recieved balance update, new balance: " + balance);
    }
}
