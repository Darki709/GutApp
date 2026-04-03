package com.example.gutapp.session.Responses;

import static com.example.gutapp.session.Connection.NETWORK_LOG_TAG;

import android.util.Log;

import com.example.gutapp.session.AsyncResponse;
import com.example.gutapp.session.ResponseType;

import java.nio.ByteBuffer;
import java.util.Arrays;

public class OrderResponses {
    public static class Commited extends AsyncResponse {
        public final double paid_price;
        public final long ts;
        public final int order_id;

        public Commited(byte[] response) {
            super(response[0], Arrays.copyOfRange(response, 1, 5));
            ByteBuffer buffer = ByteBuffer.wrap(response, 5, response.length-5);
            paid_price = buffer.getDouble();
            ts = buffer.getLong();
            order_id = buffer.getInt();
            Log.d(NETWORK_LOG_TAG, String.format("Received order confirmation: order_id: %d paid_price: %.10f executed at %d", order_id, paid_price, ts));
        }
    }

    public static class Invalid extends AsyncResponse{

        public final Status status;

        public Invalid(byte[] response) {
            super(response[0], Arrays.copyOfRange(response, 1, 5));
            status = Status.fromByte(response[5]);
        }

        public enum Status{
            INVALIDBALANCE,
            INVALIDSYMBOL;

            public static Status fromByte(byte b){
                int index = b & 0xFF; //Convert byte to unsigned int to avoid negative index errors
                Status[] types = Status.values();
                if (index < 0 || index >= types.length) {
                    throw new IllegalArgumentException("Invalid OrderInvalidStatus byte: " + b);
                }
                return types[index];
            }
        }
    }

    public static class Slip extends AsyncResponse{
        public Slip(byte[] response){
            super(response[0], Arrays.copyOfRange(response, 1, 5));
        }
    }
}
