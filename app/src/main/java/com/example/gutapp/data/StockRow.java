package com.example.gutapp.data;

import static com.example.gutapp.ui.HomeActivity.HOME_LOG_TAG;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;

import com.example.gutapp.data.models.Candle;
import com.example.gutapp.data.models.PriceChunk;
import com.example.gutapp.data.models.TickerInfo;
import com.example.gutapp.database.DB_Helper;
import com.example.gutapp.database.LastFetchCacheHelper;
import com.example.gutapp.database.StockDataHelper;
import com.example.gutapp.session.DataType;
import com.example.gutapp.session.NetworkClient;
import com.example.gutapp.session.Requests.RequestTickerData;
import com.example.gutapp.session.SessionCallback;
import com.example.gutapp.ui.ChartActivity;

import java.time.Instant;

public class StockRow implements SessionCallback {
    LinearLayout stockRow;
    double lastPrice;
    String symbol;
    String name;
    Activity callerActivity;
    int reqId = -1; //keeps the request id of te streaming request used to update the prices. -1 means row inactive
    Handler mainHandler = new Handler(Looper.getMainLooper()); //to change prices live from the background

    TextView priceView;

    public StockRow(String symbol, String name, Activity callerActivity){
        this.symbol= symbol;
        this.name = name;
        this.callerActivity = callerActivity;
        setUpRow();
    }

    public StockRow(TickerInfo ticker, Activity callerActivity){
        this.symbol= ticker.symbol;
        this.name = ticker.name;
        this.callerActivity = callerActivity;
        setUpRow();
    }

    //sends the live update request to the api
    public void loadPrice(){
        //long lastFetchTime = (new LastFetchCacheHelper(DB_Helper.getInstance(null))).getLastFetchTime(symbol, StockDataHelper.Timeframe.DAILY); depracted for perfomance issues
        RequestTickerData requestPrice = new RequestTickerData(symbol, StockDataHelper.Timeframe.ONE_MIN, 0
                , 0, false, true, this);
        reqId = requestPrice.getReqId();
        NetworkClient.getInstance(null).getSessionManager().pushRequest(requestPrice);
    }

    private void setUpRow(){
        //set up the row view itself with all the styling, effects and logic
        this.stockRow = new LinearLayout(callerActivity);
        stockRow.setOrientation(LinearLayout.HORIZONTAL);
        stockRow.setPadding(20, 24, 20, 24);
        stockRow.setGravity(Gravity.CENTER_VERTICAL);
        stockRow.setClickable(true);
        TypedValue outValue = new TypedValue();
        callerActivity.getTheme().resolveAttribute(android.R.attr.selectableItemBackground, outValue, true);
        stockRow.setBackgroundResource(outValue.resourceId);
        LinearLayout textGroup = new LinearLayout(callerActivity);
        textGroup.setOrientation(LinearLayout.VERTICAL);
        textGroup.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

        //name
        TextView nameView = new TextView(callerActivity);
        nameView.setText(name);
        nameView.setTextColor(Color.WHITE);
        nameView.setTextSize(16);

        //symbol
        TextView symbolView = new TextView(callerActivity);
        symbolView.setText(symbol);
        symbolView.setTextColor(Color.GRAY);
        symbolView.setTextSize(13);

        textGroup.addView(nameView);
        textGroup.addView(symbolView);

        //price
        this.priceView = new TextView(callerActivity);
        //default price is 0.00 or what ever is on the machine until server responds with live prices
        StockDataHelper stockDataHelper = new StockDataHelper(DB_Helper.getInstance(null));
        Candle cachedPrice = stockDataHelper.getLatestPrice(symbol);
        if(cachedPrice== null) {
            priceView.setText("Price not available");
            priceView.setTextColor(Color.parseColor("#FF4444"));
            this.lastPrice = 0;
        }
        else {
            String errMsg = Instant.now().getEpochSecond() - cachedPrice.timestamp > 3600 ? " (outdated)" : "";
            priceView.setText(String.format("%.4f" + errMsg, cachedPrice.close));
            priceView.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
            this.lastPrice = cachedPrice.close;
            Candle.Direction direction = cachedPrice.getDirection();
            boolean isUp = direction == Candle.Direction.UP;
            priceView.setTextColor(isUp ? Color.parseColor("#00FF88") : Color.parseColor("#FF4444"));
        }
        priceView.setTextSize(17);
        stockRow.addView(textGroup);
        stockRow.addView(priceView);

        //click logic
        stockRow.setOnClickListener(v -> {
            Intent intent = new Intent(callerActivity, ChartActivity.class);
            intent.putExtra("symbol", symbol);
            intent.putExtra("name", name);
            callerActivity.startActivity(intent);
        });
    }

    public LinearLayout getRow() {
        return stockRow;
    }

    public void discard(){
        NetworkClient.getInstance(null).getSessionManager().discardRequest(reqId);
        reqId = -1;
    }

    private synchronized void updatePrice(PriceChunk chunk){
        long data_ts = chunk.chunk.get(chunk.chunk.size() - 1).timestamp;
        String errMsg = Instant.now().getEpochSecond() - data_ts > 3600 ? " (outdated)" : "";
        double price = chunk.chunk.get(chunk.chunk.size() - 1).close;
        double oldPrice = this.lastPrice;
        mainHandler.post(() -> {
            priceView.setText(String.format("%.4f", price) + errMsg);
            if(price != oldPrice) {
                priceView.setTextColor(price > oldPrice ?  Color.parseColor("#00FF88") : Color.parseColor("#FF4444"));
            }
        });
        this.lastPrice = price;
    }

    public boolean isActive(){
        return reqId != -1;
    }

    @Override
    public void onDataReceived(DataType msgType, Object parsedData) {
        if(msgType == DataType.TICKER_ERROR){
            mainHandler.post(() -> {
                priceView.setText(String.format("%.4f",(this.lastPrice)) + " Price not available");
                priceView.setTextColor(Color.parseColor("#FF4444"));
            });
            return;
        }
        if(msgType == DataType.TICKER_REQUEST_DONE){
            return;
        }
        PriceChunk chunk = (PriceChunk) parsedData;
        if(chunk == null || chunk.reqId != reqId || chunk.chunk.isEmpty()){
            return;
        }
        if(msgType == DataType.TICKER_STREAM || (msgType == DataType.TICKER_SNAPSHOT && chunk.isLast)){
            updatePrice(chunk);
        }
    }

    @Override
    public void onActionRequired(int actionType, @Nullable Object data) {

    }
}
