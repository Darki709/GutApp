package com.example.gutapp.data.alerts;

public abstract class Condition {

    /**
     * check if the alert is triggered, it gets the last data point but it can also load more data if it needs to
     * */
    abstract public boolean check(PriceResource resource);
    abstract public String getNotification();
    abstract public String serialize();
}
