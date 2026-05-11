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
    SENDORDER((byte)9),
    FETCHORDERS((byte)10),
    ENDORDER((byte)11),
    FETCH_WATCHLISTS((byte) 12),
    MANAGE_WATCHLIST((byte) 13),
    MODIFY_WATCHLIST_ITEMS((byte) 14),
    GET_WATCHLIST_CONTENT((byte) 15);

    public final byte value;

    RequestType(byte value) {
        this.value = value;
    }
}
