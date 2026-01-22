package com.example.gutapp.session;

public enum Flag {
    PLAINTEXT((byte)0),
    ENCRYPTED((byte)1);

    public final byte value;

    Flag(byte value) {
        this.value = value;
    }
}
