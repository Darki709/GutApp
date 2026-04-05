package com.example.gutapp.session.Requests;

import static com.example.gutapp.session.Connection.NETWORK_LOG_TAG;
import static com.example.gutapp.ui.ChartActivity.CHART_LOG_TAG;

import android.util.Log;

import androidx.annotation.NonNull;

import com.example.gutapp.data.models.TickerInformation;
import com.example.gutapp.session.AsyncRequest;
import com.example.gutapp.session.DataType;
import com.example.gutapp.session.Flag;
import com.example.gutapp.session.RequestType;
import com.example.gutapp.session.Response;
import com.example.gutapp.session.Responses.TickerInfoResponse;
import com.example.gutapp.session.SessionCallback;

import java.nio.ByteBuffer;

public class TickerInfoRequest extends AsyncRequest {
    String symbol;

    public TickerInfoRequest(String symbol ,@NonNull SessionCallback caller) {
        super(caller);
        this.symbol = symbol;
    }

    @Override
    public byte[] getBytes() {
        int length = 7 + symbol.length();
        ByteBuffer buffer = ByteBuffer.allocate(4 + length);
        buffer.putInt(length);
        buffer.put(Flag.ENCRYPTED.value);
        buffer.put(RequestType.TICKERINFO.value);
        buffer.put(reqId);
        buffer.put((byte)symbol.length());
        buffer.put(symbol.getBytes());
        Log.i(NETWORK_LOG_TAG, "Fetching details for " + symbol);
        return buffer.array();
    }

    @Override
    public void handle(Response response) {
        if(caller == null){
            this.isDone = true;
            return;
        }
        TickerInfoResponse info = (TickerInfoResponse) response;
        if(info.getInformation() == null){
            caller.onDataReceived(DataType.SEARCH_NO_RESULT, "Ticker " + symbol + " doesn't exist");
            return;
        }
        Log.i(CHART_LOG_TAG, info.getInformation().toString());
        caller.onDataReceived(DataType.TICKER_INFORMATION, info.getInformation());
        this.isDone = true;
    }
}
