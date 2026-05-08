package com.example.gutapp.session.Requests;

import static com.example.gutapp.session.Connection.NETWORK_LOG_TAG;
import static com.example.gutapp.ui.ChartActivity.CHART_LOG_TAG;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.example.gutapp.data.StockChart;
import com.example.gutapp.data.models.Candle;
import com.example.gutapp.data.models.PriceChunk;
import com.example.gutapp.database.DB_Helper;
import com.example.gutapp.database.StockDataHelper;
import com.example.gutapp.session.AsyncRequest;
import com.example.gutapp.session.DataType;
import com.example.gutapp.session.Flag;
import com.example.gutapp.session.NetworkClient;
import com.example.gutapp.session.RequestType;
import com.example.gutapp.session.Response;
import com.example.gutapp.session.Responses.SnapshotResponse;
import com.example.gutapp.session.Responses.StreamResponse;
import com.example.gutapp.session.SessionCallback;
import com.example.gutapp.ui.ChartActivity;

import java.nio.charset.StandardCharsets;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;

public class RequestTickerData extends AsyncRequest {
    private final String symbol;
    private final StockDataHelper.Timeframe interval;
    private final long startTs;
    private final long endTs;
    private final byte flags;

    private final boolean isStream;

    public RequestTickerData(String symbol, StockDataHelper.Timeframe interval, long startTs , long endTs , boolean isSnapshot, boolean isStream , SessionCallback caller) {
        super(caller);
        this.symbol = symbol;
        this.interval = interval;
        this.startTs = startTs;
        this.endTs = endTs;
        this.isStream = isStream;

        // Bitwise flags: 0x01 SNAPSHOT, 0x02 STREAM
        byte f = 0;
        if (isSnapshot) f |= 0x01;
        if (isStream) f |= 0x02;
        this.flags = f;
    }

    @Override
    public byte[] getBytes() {
        byte[] symbolBytes = symbol.getBytes(StandardCharsets.US_ASCII);
        int length = 1 + 1 + 4 + 1 + symbolBytes.length + 4 + 8 + 8 + 1;

        ByteBuffer buf = ByteBuffer.allocate(length + 4);
        buf.order(ByteOrder.BIG_ENDIAN);
        buf.putInt(length);                 // length
        buf.put(Flag.ENCRYPTED.value);      // flag
        buf.put(RequestType.REQUESTTICKERDATA.value);//request ticker data type
        buf.put(reqId);                     //request id
        buf.put((byte) symbolBytes.length); // symbolLen (0)
        buf.put(symbolBytes);               // symbol (1)
        buf.putInt(interval.interval);               // interval (?)
        buf.putLong(startTs);               // start_ts (?)
        buf.putLong(endTs);                 // end_ts (?)
        buf.put(flags);                     // flags (?)
        
        Log.i(NETWORK_LOG_TAG, "RequestTickerData: " + symbol + interval + startTs + endTs + flags);
        return buf.array();
    }

    @Override
    public void handle(Response response) {
        if(caller == null){
            this.isDone = true;
            return;
        }
        Log.i(CHART_LOG_TAG, "handling new price response from server");
        Thread cacheThread;
        switch(response.getType()){
            case SNAPSHOT:
                SnapshotResponse snapshotResponse = (SnapshotResponse) response;
                if(snapshotResponse.isFetchError()){
                    this.isDone = true;
                    Log.e(NETWORK_LOG_TAG, "Error fetching price data for " + symbol + " " + interval.value);
                    caller.onDataReceived(DataType.TICKER_ERROR, "No price data for " + symbol + " " + interval.value);
                    return;
                }
                ArrayList<Candle> entries = snapshotResponse.getEntries();
                int reqId = snapshotResponse.getReqId();
                PriceChunk priceChunk = new PriceChunk(reqId, entries, snapshotResponse.isDone());
                caller.onDataReceived(DataType.TICKER_SNAPSHOT, priceChunk);
                //cache the price data for future requests
                cacheThread = new Thread( () -> cachePriceData(entries, this.interval));
                cacheThread.start();
                if(snapshotResponse.isDone() && !isStream){
                    this.isDone = true;
                    caller.onDataReceived(DataType.TICKER_REQUEST_DONE, priceChunk);
                }
                break;
            case STREAM:
                StreamResponse streamResponse = (StreamResponse) response;
                Candle candle = streamResponse.getCandle();
                ArrayList<Candle> streamEntries = new ArrayList<>();
                streamEntries.add(candle);
                PriceChunk streamChunk = new PriceChunk(streamResponse.getReqId(), streamEntries, false);
                caller.onDataReceived(DataType.TICKER_STREAM, streamChunk);
                Log.i(CHART_LOG_TAG, "Received stream data for " + symbol + " : open = " + candle.open + ", high = " + candle.high + ", low = " + candle.low + ", close = " + candle.close + ", volume = " + candle.volume + ", ts = " + candle.timestamp);
                cacheThread = new Thread( () -> cachePriceData(streamEntries, StockDataHelper.Timeframe.ONE_MIN));
                cacheThread.start();
                break;
    }
    }

    @Override
    public void discardRequest() {
        super.discardRequest();
        if(isStream)
            NetworkClient.getInstance(null).getSessionManager().pushRequest(new CancelTickerStream(reqId, symbol));
    }


    private void cachePriceData(ArrayList<Candle> entries, StockDataHelper.Timeframe interval) {
        StockDataHelper stockDataHelper = new StockDataHelper(DB_Helper.getInstance(null));
        //remove the last data point to prevent corrupted half baked data points
        //entries.remove(entries.size() - 1);
        stockDataHelper.saveStockData(symbol, interval, entries);
    }
}
