package com.example.gutapp.data.alerts;

import lombok.Getter;
import lombok.Setter;

public class Alert {

    @Getter
    private final String symbol;

    @Getter
    private final Condition condition;

    @Getter
    @Setter
    private Status status;

    public enum Status {
        ACTIVE, //the is monitored
        INACTIVE,//the is not monitored
        TRIGGERED;//the notification is not monitored (it already triggered)

        static public Status fromInt(int status) {
            Status[] values = Status.values();
            return values[status];
        }
    }

    public Alert(String symbol, Condition condition) {
        this.symbol = symbol;
        this.condition = condition;
        this.status = Status.ACTIVE;
    }

    public String getNotification() { return condition.getNotification(); }

    public String serialize() {
        return String.format("%s|%s|%s", symbol, status.name(), condition.serialize());
    }
}
