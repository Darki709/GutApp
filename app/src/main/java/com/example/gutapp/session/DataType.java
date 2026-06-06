package com.example.gutapp.session;

import com.example.gutapp.data.models.TickerInformation;

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
    SEARCH_NO_RESULT,
    TICKER_INFORMATION,
    ORDER_RECEIVED,
    ORDER_INVALID,
    ORDER_SLIP,
    ORDERS_BATCH,
    ORDER_CLOSED_SUCCESS,
    ORDER_CLOSED_FAILURE,
    WATCHLISTS_LOADED,
    WATCHLIST_OPERATION_RESULT,
    WATCHLIST_CONTENT_LOADED,
    WATCHLIST_ERROR,

    // Chart-state sync (drawings + indicators + presets)
    CHART_SYNC_PULLED,
    CHART_SYNC_PUSHED,
    CHART_SYNC_ERROR;
}
