package com.example.gutapp.session.Responses;

import com.example.gutapp.session.AsyncResponse;

import java.util.Arrays;

public class StreamResponse extends AsyncResponse {
    public StreamResponse(byte[] response) {
        super(response[0], Arrays.copyOfRange(response, 1, 5));
    }
}
