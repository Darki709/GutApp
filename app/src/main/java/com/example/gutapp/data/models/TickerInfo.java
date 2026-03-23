package com.example.gutapp.data.models;

import android.os.Parcel;
import android.os.Parcelable;

public class TickerInfo implements Parcelable {
    public final String symbol;

    public final String name;
    public final int tickerId;

    public TickerInfo(String name, String symbol, int tickerId){
        this.symbol = symbol;
        this.name = name;
        this.tickerId = tickerId;
    }

    public TickerInfo(String name, String symbol){
        this.symbol = symbol;
        this.name = name;
        this.tickerId = -1; //when you dont need the id
    }

    // Android Studio can generate the rest of this (Alt+Insert -> Parcelable)
    protected TickerInfo(Parcel in) {
        symbol = in.readString();
        name = in.readString();
        tickerId = in.readInt();
    }

    public static final Creator<TickerInfo> CREATOR = new Creator<TickerInfo>() {
        @Override
        public TickerInfo createFromParcel(Parcel in) { return new TickerInfo(in); }
        @Override
        public TickerInfo[] newArray(int size) { return new TickerInfo[size]; }
    };

    @Override
    public int describeContents() { return 0; }
    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(symbol);
        dest.writeString(name);
        dest.writeInt(tickerId);
    }
}
