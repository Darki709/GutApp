package com.example.gutapp.session.Responses;

import com.example.gutapp.session.Response;

public class RegisterResponse extends Response {
    public static final int SUCCESS = 0;
    public static final int USERNAME_TAKEN = 1;
    public static final int INSECURE_PASSWORD = 2;

    private int state;
    private String feedback = "";

    public RegisterResponse(byte[] response) {
        super(response[0]);
        this.state = response[1] & 0xFF;
        if (state == INSECURE_PASSWORD && response.length > 2) {
            this.feedback = new String(response, 2, response.length - 2);
        }
    }

    public int getState() {
        return state;
    }

    public String getFeedback() {
        return feedback;
    }
}
