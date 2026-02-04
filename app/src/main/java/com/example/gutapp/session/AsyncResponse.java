package com.example.gutapp.session;

import java.nio.ByteBuffer;

public abstract class AsyncResponse extends Response{
    protected int reqId;
    public AsyncResponse(byte responseType, byte[] reqId) {
        super(responseType);
        this.reqId = ByteBuffer.wrap(reqId).getInt();
    }
    
    public int getReqId(){
        return this.reqId;
    }
}
