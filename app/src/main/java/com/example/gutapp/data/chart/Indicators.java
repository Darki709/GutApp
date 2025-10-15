package com.example.gutapp.data.chart;

public enum Indicators {
    SMA;

    public static Indicators fromInt(int i) {
        Indicators[] values = Indicators.values();
        if (i >= 0 && i < values.length) {
            return values[i];
        }
        throw new RuntimeException("Invalid indicator index:");
    }
}
