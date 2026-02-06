package com.example.gutapp.session;

import com.example.gutapp.session.Responses.SnapshotResponse;
import com.example.gutapp.session.Responses.StreamResponse;

public class ResponseFactory {
    public static AsyncResponse createResponse(byte[] response) {
        switch (ResponseType.fromByte(response[0])) {
            case STREAM:
                return new StreamResponse(response);
            case SNAPSHOT:
                return new SnapshotResponse(response);
            default:
                throw new RuntimeException("Unknown response type: " + response[0]);
        }
    }
}
