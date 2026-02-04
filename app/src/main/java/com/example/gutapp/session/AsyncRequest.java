package com.example.gutapp.session;

public abstract class AsyncRequest extends Request{
    protected SessionCallback caller;
    protected boolean isDone = false;

    public AsyncRequest(SessionCallback caller){
        this.caller = caller;
    }

    public SessionCallback getCaller(){
        return this.caller;
    }

    //used by the recv loop to check if a pending request has been completed
    public boolean isDone(){
        return this.isDone;
    }
}
