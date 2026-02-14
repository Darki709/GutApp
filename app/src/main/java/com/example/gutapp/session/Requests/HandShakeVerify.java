package com.example.gutapp.session.Requests;

import com.example.gutapp.session.CryptoUtility;
import com.example.gutapp.session.Flag;
import com.example.gutapp.session.Request;
import com.example.gutapp.session.RequestType;
import com.example.gutapp.session.Response;
import com.example.gutapp.session.ResponseType;
import com.example.gutapp.session.Responses.HandShakeVerifyResponse;

import java.nio.ByteBuffer;

public class HandShakeVerify extends Request {
    byte[] payload;

    public HandShakeVerify(CryptoUtility.CryptoContext ctx) {
        super();
        try{
        payload = CryptoUtility.encryptAESECB("encrypted", ctx.aesKey);}
        catch (Exception e){
            throw new RuntimeException(e);
        }
    }

    @Override
    public byte[] getBytes() {
        int length = 2 + reqId.length + payload.length;
        ByteBuffer buffer = ByteBuffer.allocate(length + 4);
        buffer.putInt(length);
        buffer.put(Flag.PLAINTEXT.value);
        buffer.put(RequestType.HANDSHAKEVERIFY.value);
        buffer.put(reqId);
        buffer.put(payload);
        return buffer.array();
    }

    @Override
    public void handle(Response response) {
        HandShakeVerifyResponse handShakeVerifyResponse;
        try {
            handShakeVerifyResponse = (HandShakeVerifyResponse) response;
            if(!handShakeVerifyResponse.isAccepted())
                throw new RuntimeException("Handshake failure");
        } catch (Exception e) {
            throw new RuntimeException("Handshake failure");
        }
    }
}
