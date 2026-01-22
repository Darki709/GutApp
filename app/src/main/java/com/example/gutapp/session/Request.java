package com.example.gutapp.session;


import java.security.SecureRandom;
import java.util.Arrays;
import java.util.stream.Stream;

public abstract class Request {
    protected byte[] reqId = new byte[4];
    public Request(){
        SecureRandom random = new SecureRandom();
        random.nextBytes(reqId);
    }

    public abstract byte[] getBytes(); //returns the ready to go byte buffer of the request
    /*
    * int length = 6 + reqId.length + payload.length;
        ByteBuffer buffer = ByteBuffer.allocate(length);
        buffer.putInt(length);
        buffer.put(Flag.PLAINTEXT.value);
        buffer.put(RequestType.HANDSHAKEHELLO.value);
        buffer.put(reqId);
        buffer.put(payload);
        return buffer.array();
      *
     */

    public abstract void handle(Response response); //only the corresponding request should handle the response

}
