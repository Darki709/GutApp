package com.example.gutapp.data.models;

import com.example.gutapp.session.Requests.SendOrder;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.OptionalLong;

import lombok.Getter;

public class Order {
    @Getter
    String symbol;
    @Getter
    OrderType type;
    @Getter
    long entry_ts;
    @Getter
    double entry_price;
    @Getter
    int quantity;
    @Getter
    int order_id = 0;
    @Getter
    boolean active;
    @Getter
    Optional<Long> end_ts;
    @Getter
    Optional<Double> end_price;

    // 1. Constructor for a BRAND NEW Order
    // Used when a user clicks 'Buy' or 'Sell' in the app
    public Order(String symbol, OrderType type, double entry_price, int quantity) {
        this.symbol = symbol;
        this.type = type;
        this.entry_price = entry_price;
        this.quantity = quantity;
        this.entry_ts = System.currentTimeMillis(); // Generate current timestamp
        this.order_id = 0; // Server will assign a real ID later
        this.active = true;
        this.end_ts = Optional.empty();
        this.end_price = Optional.empty();
    }

    // 2. Constructor for an OLD ACTIVE Order
    // Used when loading your current positions from the C++ server/DB
    public Order(int order_id, String symbol, OrderType type, long entry_ts, double entry_price, int quantity) {
        this.order_id = order_id;
        this.symbol = symbol;
        this.type = type;
        this.entry_ts = entry_ts;
        this.entry_price = entry_price;
        this.quantity = quantity;
        this.active = true;
        this.end_ts = Optional.empty();
        this.end_price = Optional.empty();
    }

    // 3. Constructor for a CLOSED Order
    // Used for showing Trade History
    public Order(int order_id, String symbol, OrderType type, long entry_ts, double entry_price,
                 int quantity, long end_ts, double end_price) {
        this.order_id = order_id;
        this.symbol = symbol;
        this.type = type;
        this.entry_ts = entry_ts;
        this.entry_price = entry_price;
        this.quantity = quantity;
        this.active = false;
        this.end_ts = Optional.of(end_ts);
        this.end_price = Optional.of(end_price);
    }

    public static Order fromBytes(byte[] bytes) {
        ByteBuffer buffer = ByteBuffer.wrap(bytes);

        // 1. Read Symbol [1 byte len | symbol]
        int symbolLen = buffer.get() & 0xFF; // Treat as unsigned
        byte[] symbolBytes = new byte[symbolLen];
        buffer.get(symbolBytes);
        String symbol = new String(symbolBytes, StandardCharsets.US_ASCII);

        // 2. Read Metadata [4 bytes ID | 1 byte Type]
        int orderId = buffer.getInt();
        int typeOrdinal = buffer.get() & 0xFF;
        OrderType type = OrderType.values()[typeOrdinal];

        // 3. Read Entry Data [8 bytes Price | 8 bytes TS | 4 bytes Qty]
        double entryPrice = buffer.getDouble();
        long entryTs = buffer.getLong();
        int quantity = buffer.getInt();

        // 4. Read Status [1 byte Active]
        boolean active = (buffer.get() == 1);

        if (active) {
            // Return an Active Order using the second constructor
            return new Order(orderId, symbol, type, entryTs, entryPrice, quantity);
        } else {
            // 5. Read Closing Data (if inactive) [8 bytes End Price | 8 bytes End TS]
            double endPrice = buffer.getDouble();
            long endTs = buffer.getLong();

            // Return a Closed Order using the third constructor
            return new Order(orderId, symbol, type, entryTs, entryPrice, quantity, endTs, endPrice);
        }
    }


    public enum OrderType{
        Long((byte)0),
        Short((byte)1);

        public final byte value;

        OrderType(byte value){
            this.value = value;
        }
    }

    public double paidPrice(){
        return quantity * entry_price;
    }

    public double priceDelta(){
        if(end_price.isEmpty()) throw new RuntimeException("don't access price delta on unfinished order");
        double delta = quantity*(end_price.get()-entry_price);
        return type == OrderType.Long ? delta : -delta;
    }
}
