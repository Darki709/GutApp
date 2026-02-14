package com.example.gutapp.session.Requests;

import static com.example.gutapp.session.Connection.NETWORK_LOG_TAG;

import android.util.Log;

import com.example.gutapp.session.CryptoUtility;
import com.example.gutapp.session.Flag;
import com.example.gutapp.session.Request;
import com.example.gutapp.session.RequestType;
import com.example.gutapp.session.Response;
import com.example.gutapp.session.Responses.HandShakeHelloResponse;
import com.example.gutapp.session.SessionManager;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

import javax.crypto.SecretKey;

public class HandShakeHello extends Request {
    byte[] publicKey;
    CryptoUtility.CryptoContext ctx;

    public HandShakeHello(CryptoUtility.CryptoContext ctx) {
        super();
        publicKey = CryptoUtility.convertToPEM(ctx.keyPair.getPublic().getEncoded()).getBytes(StandardCharsets.UTF_8); //turn the rsa public key to bytes for sending
        this.ctx = ctx;
    }

    @Override
    public byte[] getBytes() {
        int length = 2 + reqId.length + publicKey.length;
        ByteBuffer buffer = ByteBuffer.allocate(length + 4); //add length header bytes
        buffer.putInt(length);
        buffer.put(Flag.PLAINTEXT.value);
        buffer.put(RequestType.HANDSHAKEHELLO.value);
        buffer.put(reqId);
        buffer.put(publicKey);
        return buffer.array();
    }

    @Override //should receive only corresponding response or else throw exception
    public void handle(Response response) {
        HandShakeHelloResponse handShakeHelloResponse;
        try {
            handShakeHelloResponse = (HandShakeHelloResponse) response;
        } catch (Exception e) {
            throw new RuntimeException("Invalid response type");
        }
        try {
            ctx.aesKey = CryptoUtility.bytesToAESKey(
                    CryptoUtility.decryptRSA(handShakeHelloResponse.getPayload(), ctx.keyPair.getPrivate()));
        } catch (Exception e) {
            Log.e(NETWORK_LOG_TAG, "Error decrypting aes key: " + e.getMessage());
            throw new RuntimeException(e);
        }
    }
}
