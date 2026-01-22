package com.example.gutapp.session;

public abstract class Response {
    ResponseType type;

    public Response(byte responseType){ //gets the unencrypted bytes of the response, without length bytes and flag byte
        this.type = ResponseType.fromByte(responseType);
    }

    public ResponseType getType(){
        return this.type;
    }
}
