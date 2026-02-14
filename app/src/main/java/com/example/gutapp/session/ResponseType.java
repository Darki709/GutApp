package com.example.gutapp.session;

//message type in server
public enum ResponseType{
    HANDSHAKEVERIFY((byte) 0),
    HANDSHAKESUCCESS((byte) 1),
    SNAPSHOT((byte) 2),
    STREAM((byte) 3),
    REGISTER((byte) 4),
    LOGIN((byte) 5);

    public final byte value;

    ResponseType(byte value) {
        this.value = value;
    }

    public static ResponseType fromByte(byte b) {
        int index = b & 0xFF; //Convert byte to unsigned int to avoid negative index errors
        ResponseType[] types = ResponseType.values();
        if (index < 0 || index >= types.length) {
            throw new IllegalArgumentException("Invalid RequestType byte: " + b);
        }

        return types[index];
    }
}
