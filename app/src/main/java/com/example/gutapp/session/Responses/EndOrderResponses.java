package com.example.gutapp.session.Responses;

import com.example.gutapp.session.AsyncResponse;

import java.nio.ByteBuffer;
import java.util.Arrays;

import lombok.Getter;

public class EndOrderResponses{
    static public class Success extends AsyncResponse {
        @Getter
        final double end_price;
        @Getter
        final long end_ts;

        public Success(byte[] response) {
            super(response[0], Arrays.copyOfRange(response, 1, 5));
            // 2. Wrap the remaining payload starting from index 5
            // Byte layout: [1B Type | 4B ReqID | 8B EndPrice | 8B EndTS]
            ByteBuffer buffer = ByteBuffer.wrap(response, 5, response.length-5);

            // 3. Extract the 8-byte double and 8-byte long
            this.end_price = buffer.getDouble();
            this.end_ts = buffer.getLong();
        }
    }

    static public class Failure extends AsyncResponse{
        @Getter
        String error;

        public Failure(byte[] response) {
            super(response[0], Arrays.copyOfRange(response, 1, 5));
            switch (response[5]) {
                case 0:
                    error = "No active order matched the requested order";
                case 1:
                    error = "Order not filled to prevent slippage";
            }
        }
    }
}
