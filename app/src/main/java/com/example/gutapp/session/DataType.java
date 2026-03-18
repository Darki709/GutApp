package com.example.gutapp.session;

public enum DataType {
    ERROR,
    REGISTER_ERROR,
    LOGIN_ERROR,
    AUTH_SUCCESS,
    MARKET_DATA,

    // From RequestTickerData
    TICKER_STREAM,
    TICKER_SNAPSHOT,
    TICKER_REQUEST_DONE,
    TICKER_ERROR,
    SEARCH_RESULT,
    SEARCH_NO_RESULT;
}
