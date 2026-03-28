package com.example.gutapp.session;

//task type in server
public enum RequestType {
    HANDSHAKEHELLO((byte) 0),
    HANDSHAKEVERIFY((byte) 1),
    LOGIN((byte) 2),
    REGISTER((byte) 3),
    REQUESTTICKERDATA((byte) 4),
    CANCELTICKERSTREAM((byte) 5),
    SEARCHTICKER((byte) 6),
    TICKERINFO((byte)7),
    GETBALANCE((byte)8),
    SENDORDER((byte)9)
    ;

    public final byte value;

    RequestType(byte value) {
        this.value = value;
    }
}
