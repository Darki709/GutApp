package com.example.gutapp.session.Requests;

import static com.example.gutapp.session.Connection.NETWORK_LOG_TAG;
import static com.example.gutapp.session.CryptoUtility.KEY_PASS;
import static com.example.gutapp.session.CryptoUtility.getVault;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.example.gutapp.data.UserGlobals;
import com.example.gutapp.data.models.Order;
import com.example.gutapp.session.AsyncRequest;
import com.example.gutapp.session.DataType;
import com.example.gutapp.session.Flag;
import com.example.gutapp.session.RequestType;
import com.example.gutapp.session.Response;
import com.example.gutapp.session.Responses.EndOrderResponses;
import com.example.gutapp.session.SessionCallback;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

public class EndOrder extends AsyncRequest {
    private final Order order;
    private final double expectedPrice;
    private final String password;
    private final Context appContext;

    public EndOrder(Order order, double expectedPrice, Context appContext, SessionCallback caller) {
        super(caller);
        this.order = order;
        this.expectedPrice = expectedPrice;
        this.appContext = appContext;
        this.password = fetchPassword();
    }

    @Override
    public byte[] getBytes() {
        byte[] pwdBytes = password.getBytes(StandardCharsets.UTF_8);

        // Layout: [Flag | 1B Type(11) | 4B ReqID | 4B OrderID | 8B Price | 1B PwdLen | Password]
        int len = 1 + 1 + 4 + 4 + 8 + 1 + pwdBytes.length;

        ByteBuffer buffer = ByteBuffer.allocate(4 + len);

        buffer.putInt(len);
        buffer.put(Flag.ENCRYPTED.value);
        buffer.put(RequestType.ENDORDER.value);
        buffer.put(reqId);
        buffer.putInt(order.getOrder_id());
        buffer.putDouble(expectedPrice);
        buffer.put((byte) pwdBytes.length);
        buffer.put(pwdBytes);

        return buffer.array();
    }

    @Override
    public void handle(Response response) {
        switch(response.getType()){
            case ORDEREXITTED:
                EndOrderResponses.Success success = (EndOrderResponses.Success)response;
                order.setInactive(success.getEnd_price(), success.getEnd_ts());
                UserGlobals.updateBalance(order.endValue());
                if(caller != null) caller.onDataReceived(DataType.ORDER_CLOSED_SUCCESS, order);
                break;
            case ORDERFAILEDEXIT:
                EndOrderResponses.Failure failure = (EndOrderResponses.Failure)response;
                if(caller != null) caller.onDataReceived(DataType.ORDER_CLOSED_FAILURE, failure.getError());
                break;
        }
    }

    private String fetchPassword(){
        SharedPreferences preferences = getVault(appContext.getApplicationContext());
        try {
            String password = preferences.getString(KEY_PASS, null);
            if (password != null) return password;
            throw new NullPointerException("Failed to fetch password for EndOrder");
        }
        catch (NullPointerException exception){
            Log.e(NETWORK_LOG_TAG, "Failed to fetch password for EndOrder");
            throw exception;
        }
    }
}