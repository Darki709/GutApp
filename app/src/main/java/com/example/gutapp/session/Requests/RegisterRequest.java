package com.example.gutapp.session.Requests;

import com.example.gutapp.session.Flag;
import com.example.gutapp.session.Request;
import com.example.gutapp.session.RequestType;
import com.example.gutapp.session.Response;

import java.nio.ByteBuffer;

public class RegisterRequest extends Request {
    private final byte[] payload;

    public RegisterRequest(String username, String password) {
        ByteBuffer buffer = ByteBuffer.allocate(username.length() + password.length() + 2); //username length password length and two bytes of the username length and the password length
        buffer.put((byte) username.length());
        buffer.put(username.getBytes());
        buffer.put((byte) password.length());
        buffer.put(password.getBytes());
        payload = buffer.array();
    }

    @Override
    public byte[] getBytes() {
        int length = 2 + reqId.length + payload.length;
        ByteBuffer buffer = ByteBuffer.allocate(length + 4);
        buffer.putInt(length);
        buffer.put(Flag.ENCRYPTED.value);
        buffer.put(RequestType.REGISTER.value);
        buffer.put(reqId);
        buffer.put(payload);
        return buffer.array();
    }

    @Override
    public void handle(Response response) {
        //not needed for this request
    }
}
