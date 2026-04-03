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
import com.example.gutapp.session.Responses.OrderResponses;
import com.example.gutapp.session.SessionCallback;

import java.nio.ByteBuffer;

public class SendOrder extends AsyncRequest {

    String symbol;
    int quantity;
    double asking_price;
    Order.OrderType type;
    Context appContext;

    public SendOrder(String symbol, int quantity, double asking_price, Order.OrderType type , Context appContext, SessionCallback caller) {
        super(caller);
        this.symbol = symbol;
        this.quantity = quantity;
        this.asking_price = asking_price;
        this.type = type;
        this.appContext = appContext;
    }

    @Override
    public byte[] getBytes() {
        String password = fetchPassword();
        int length = 21 + symbol.length() + password.length();
        ByteBuffer buffer = ByteBuffer.allocate(4 + length);
        buffer.putInt(length);
        buffer.put(Flag.ENCRYPTED.value);
        buffer.put(RequestType.SENDORDER.value);
        buffer.put(reqId);
        buffer.put(type.value);
        buffer.put((byte)symbol.length());
        buffer.put(symbol.getBytes());
        buffer.putInt(quantity);
        buffer.putDouble(asking_price);
        buffer.put((byte)password.length());
        buffer.put(password.getBytes());
        return buffer.array();
    }

    @Override
    public void handle(Response response) {
        if(caller == null) return;
        switch(response.getType()){
            case ORDERCOMMITED:
            {
                OrderResponses.Commited orderResponse = (OrderResponses.Commited)response;
                Order order = new Order(orderResponse.order_id ,symbol, type, orderResponse.ts ,orderResponse.paid_price, quantity);
                UserGlobals.updateBalance(-order.paidPrice());
                caller.onDataReceived(DataType.ORDER_RECEIVED, order);
                Log.d(NETWORK_LOG_TAG, "New order commited for: " + symbol);
                return;
            }
            case INVALIDORDER:
            {
                OrderResponses.Invalid orderResponse = (OrderResponses.Invalid)response;
                String error;
                switch(orderResponse.status){
                    case INVALIDSYMBOL:
                        error = symbol + " is unavailable for for trading";
                        break;
                    case INVALIDBALANCE:
                        error = "You don't have enough balance to execute the order";
                        break;
                    default:
                        error = "Unknown error";
                        break;
                }
                //passes the error as a formatted string
                caller.onDataReceived(DataType.ORDER_INVALID, "Order failed: " + error);
                Log.d(NETWORK_LOG_TAG,"Order failed: " + error );
                break;
            }
            case ORDERSLIPPED:
            {
                caller.onDataReceived(DataType.ORDER_SLIP, "Order canceled to prevent price slippage");
                Log.d(NETWORK_LOG_TAG, "Order canceled to prevent price slip");
                break;
            }
        }

    }

    private String fetchPassword(){
        SharedPreferences preferences = getVault(appContext);
        try {
            String password = preferences.getString(KEY_PASS, null);
            if (password != null) return password;
            throw new NullPointerException("Failed to fetch password for sendOrder");
        }
        catch (NullPointerException exception){
            Log.e(NETWORK_LOG_TAG, "Failed to fetch password for sendOrder");
            throw exception;
        }
    }
}
