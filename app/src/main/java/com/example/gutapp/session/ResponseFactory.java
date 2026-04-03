package com.example.gutapp.session;

import com.example.gutapp.session.Responses.GetBalanceResponse;
import com.example.gutapp.session.Responses.OrderResponses;
import com.example.gutapp.session.Responses.SearchTickerResponse;
import com.example.gutapp.session.Responses.SnapshotResponse;
import com.example.gutapp.session.Responses.StreamResponse;
import com.example.gutapp.session.Responses.TickerInfoResponse;

public class ResponseFactory {
    public static AsyncResponse createResponse(byte[] response) {
        switch (ResponseType.fromByte(response[0])) {
            case STREAM:
                return new StreamResponse(response);
            case SNAPSHOT:
                return new SnapshotResponse(response);
            case SEARCHTICKERRESPONSE:
                return new SearchTickerResponse(response);
            case GETBALANCE:
                return new GetBalanceResponse(response);
            case TICKERINFO:
                return new TickerInfoResponse(response);
            case ORDERCOMMITED:
                return new OrderResponses.Commited(response);
            case INVALIDORDER:
                return new OrderResponses.Invalid(response);
            case ORDERSLIPPED:
                return new OrderResponses.Slip(response);
            default:
                throw new RuntimeException("Unknown response type: " + response[0]);
        }
    }
}
