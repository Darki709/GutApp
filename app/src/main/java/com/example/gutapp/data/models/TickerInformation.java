package com.example.gutapp.data.models;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class TickerInformation {
    public final String name;
    public final String exchange;
    public final AssetType type;
    public final String sector;


    public TickerInformation(String name, String exchange, AssetType type, String sector){
        this.name = name;
        this.exchange = exchange.isEmpty() ? "N/A" : exchange;
        this.type = type;
        this.sector = sector.isEmpty() ? "N/A" : sector ;
    }

    @Override
    @NonNull
    public String toString(){
        return String.format("Name: %s | Exchange: %s | Type: %s | Sector: %s", name, exchange, type.type, sector);
    }


    public enum AssetType{

        STOCK("STOCK"),
        CRYPTO("CRYPTO"),
        ETF("ETF"),
        FOREX("FOREX"),
        OTHER("OTHER");

        public final String type;
        AssetType(String type){
            this.type = type;
        }

        public static AssetType fromByte(byte b) {
            AssetType[] values = AssetType.values();
            // Check if the byte is within the valid range of our enum
            if (b >= 0 && b < values.length) {
                return values[b];
            }
            return OTHER; // Fallback for safety
        }
    }
}
