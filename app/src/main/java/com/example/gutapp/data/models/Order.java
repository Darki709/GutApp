package com.example.gutapp.data.models;

import java.io.Serializable;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import lombok.Getter;

public class Order implements Serializable {
    // Standard version ID for serialization consistency
    private static final long serialVersionUID = 1L;

    @Getter String symbol;
    @Getter OrderType type;
    @Getter long entry_ts;
    @Getter double entry_price;
    @Getter int quantity;
    @Getter int order_id = 0;
    @Getter boolean active;

    // Standard Optional is NOT serializable.
    // We store these as nullable fields for the Bundle and wrap them in getters.
    private Long end_ts_internal = null;
    private Double end_price_internal = null;

    public enum OrderType implements Serializable {
        Long((byte)0),
        Short((byte)1);

        public final byte value;
        OrderType(byte value){ this.value = value; }
    }

    // 1. Constructor for a BRAND NEW Order
    public Order(String symbol, OrderType type, double entry_price, int quantity) {
        this.symbol = symbol;
        this.type = type;
        this.entry_price = entry_price;
        this.quantity = quantity;
        this.entry_ts = System.currentTimeMillis();
        this.order_id = 0;
        this.active = true;
    }

    // 2. Constructor for an OLD ACTIVE Order
    public Order(int order_id, String symbol, OrderType type, long entry_ts, double entry_price, int quantity) {
        this.order_id = order_id;
        this.symbol = symbol;
        this.type = type;
        this.entry_ts = entry_ts * 1000L;
        this.entry_price = entry_price;
        this.quantity = quantity;
        this.active = true;
    }

    // 3. Constructor for a CLOSED Order
    public Order(int order_id, String symbol, OrderType type, long entry_ts, double entry_price,
                 int quantity, long end_ts, double end_price) {
        this.order_id = order_id;
        this.symbol = symbol;
        this.type = type;
        this.entry_ts = entry_ts * 1000L;
        this.entry_price = entry_price;
        this.quantity = quantity;
        this.active = false;
        this.end_ts_internal = end_ts * 1000L;
        this.end_price_internal = end_price;
    }

    // Wrap the internal nullable fields back into Optionals for your UI logic
    public Optional<Long> getEnd_ts() {
        return Optional.ofNullable(end_ts_internal);
    }

    public Optional<Double> getEnd_price() {
        return Optional.ofNullable(end_price_internal);
    }

    public double paidPrice(){
        return quantity * entry_price;
    }

    public double endValue(){
        if(end_ts_internal == null) throw new IllegalStateException("can't calculate end value for active order");
       return quantity * end_price_internal;
    }

    public double priceDelta(){
        if(end_price_internal == null) throw new RuntimeException("don't access price delta on unfinished order");
        double delta = quantity * (end_price_internal - entry_price);
        return type == OrderType.Long ? delta : -delta;
    }

    public void setInactive(double end_price, long end_ts){
        this.end_price_internal = end_price;
        this.end_ts_internal = end_ts;
        this.active = false;
    }

    public static Order fromBuffer(ByteBuffer buffer) {
        int symbolLen = buffer.get() & 0xFF;
        byte[] symbolBytes = new byte[symbolLen];
        buffer.get(symbolBytes);
        String symbol = new String(symbolBytes, StandardCharsets.US_ASCII);

        int orderId = buffer.getInt();
        int typeOrdinal = buffer.get() & 0xFF;
        OrderType type = (typeOrdinal < OrderType.values().length) ?
                OrderType.values()[typeOrdinal] : OrderType.Long;

        double entryPrice = buffer.getDouble();
        long entryTs = buffer.getLong();
        int quantity = buffer.getInt();
        boolean active = (buffer.get() == 1);

        if (active) {
            return new Order(orderId, symbol, type, entryTs, entryPrice, quantity);
        } else {
            double endPrice = buffer.getDouble();
            long endTs = buffer.getLong();
            return new Order(orderId, symbol, type, entryTs, entryPrice, quantity, endTs, endPrice);
        }
    }
}