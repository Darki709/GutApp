package com.example.gutapp.session.Responses;

import com.example.gutapp.session.Response;

public class LoginResponse extends Response {
    LoginState state;

    public LoginResponse(byte[] response) {
        super(response[0]);
        int state = response[1] & 0xFF;
        switch (state) {
            case 0: this.state = LoginState.SUCCESS; break;
            case 1: this.state = LoginState.WRONGPASSWORD; break;
            case 2: this.state = LoginState.INVALIDUSER; break;
            default: throw new IllegalArgumentException("Invalid login state: " + state);
        }
    }

    public LoginState getState() {
        return state;
    }

    public enum LoginState {
        SUCCESS,
        WRONGPASSWORD,
        INVALIDUSER;
    }
}
