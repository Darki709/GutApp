package com.example.gutapp.session.Requests;

import com.example.gutapp.data.UserGlobals;
import com.example.gutapp.session.Flag;
import com.example.gutapp.session.Request;
import com.example.gutapp.session.RequestType;
import com.example.gutapp.session.Response;
import com.example.gutapp.session.Responses.LoginResponse;

import java.nio.ByteBuffer;

public class LoginRequest extends Request {
    private final byte[] payload;
    private String username;


    public LoginRequest(String username, String password) {
        super();
        ByteBuffer buffer = ByteBuffer.allocate(username.length() + password.length() + 2); //username length password length and two bytes of the username length and the password length
        buffer.put((byte) username.length());
        buffer.put(username.getBytes());
        buffer.put((byte) password.length());
        buffer.put(password.getBytes());
        payload = buffer.array();
        this.username = username;
    }


    @Override
    public byte[] getBytes() {
        int length = 2 + reqId.length + payload.length;
        ByteBuffer buffer = ByteBuffer.allocate(length + 4);
        buffer.putInt(length);
        buffer.put(Flag.ENCRYPTED.value);
        buffer.put(RequestType.LOGIN.value);
        buffer.put(reqId);
        buffer.put(payload);
        return buffer.array();
    }

    @Override
    public void handle(Response response) {
        try{
            LoginResponse loginResponse = (LoginResponse) response;
            if(loginResponse.getState() != LoginResponse.LoginState.SUCCESS){
                throw new RuntimeException("login failed");
            }
            UserGlobals.USER_NAME = username;
        }catch (Exception e){
            throw new IllegalArgumentException("invalid response type");
        }
    }
}
