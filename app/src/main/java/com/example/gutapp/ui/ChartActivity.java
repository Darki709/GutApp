package com.example.gutapp.ui;

import static android.widget.Toast.LENGTH_LONG;
import static android.widget.Toast.LENGTH_SHORT;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.gutapp.R;
import com.example.gutapp.data.OrderDialog;
import com.example.gutapp.data.StockChart;
import com.example.gutapp.data.UserGlobals;
import com.example.gutapp.data.models.Candle;
import com.example.gutapp.data.models.Order;
import com.example.gutapp.database.DB_Helper;
import com.example.gutapp.database.LastFetchCacheHelper;
import com.example.gutapp.database.StockDataHelper;

import com.example.gutapp.session.DataType;
import com.example.gutapp.session.NetworkClient;
import com.example.gutapp.session.Requests.FetchOrders;
import com.example.gutapp.session.Requests.RequestTickerData;
import com.example.gutapp.session.Requests.SendOrder;
import com.example.gutapp.session.Requests.TickerInfoRequest;
import com.example.gutapp.session.SessionCallback;
import com.example.gutapp.ui.fragments.OrdersList;
import com.github.mikephil.charting.charts.CombinedChart;

import java.util.ArrayList;
import java.util.Locale;

public class ChartActivity extends SessionActivity implements View.OnClickListener, OrderDialog.OrderDialogListener {

    public static final String CHART_LOG_TAG = "GutChart";

    private DB_Helper db_helper;
    private StockChart chartContainer;
    private String symbol; // Default symbol
    private String name;
    private TextView textViewTitle;
    private TextView textViewName;
    private TextView textViewPrice;
    private StockDataHelper.Timeframe interval;
    private OrderDialog activeDialog = null;
    private volatile double current_price;

    private OrdersList ordersFragment;
    private ArrayList<Order> allOrders = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_chart);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
        //loading chart symbol from the caller
        Intent intent = getIntent();
        symbol = intent.getStringExtra("symbol");
        name = intent.getStringExtra("name");

        //initialize important database objects
        db_helper = DB_Helper.getInstance(this);

        CombinedChart chart = findViewById(R.id.stockChart);

        chartContainer = new StockChart(chart, this);
        chartContainer.setupChart(findViewById(R.id.candleDataTextView));
        chartContainer.bindListener(this);


        // Set up button listeners
        findViewById(R.id.button5m).setOnClickListener(this);
        findViewById(R.id.button15m).setOnClickListener(this);
        findViewById(R.id.button1h).setOnClickListener(this);
        findViewById(R.id.button1d).setOnClickListener(this);
        findViewById(R.id.indicatorsButton).setOnClickListener(this);
        findViewById(R.id.buttonBuy).setOnClickListener(this);
        findViewById(R.id.buttonSell).setOnClickListener(this);


        textViewTitle = findViewById(R.id.textViewTitle);
        textViewPrice = findViewById(R.id.textViewPrice);
        textViewName = findViewById(R.id.textViewName);

        textViewName.setText(name);

        ImageButton buttonHome = findViewById(R.id.buttonHome);
        buttonHome.setOnClickListener(this);
        this.interval = StockDataHelper.Timeframe.DAILY;
        formatTile(this.interval.value);

        TickerInfoRequest req = new TickerInfoRequest(symbol, this);
        NetworkClient.getInstance(null).getSessionManager().pushRequest(req);

        // Initial UI state: Hidden until data arrives
        findViewById(R.id.ordersFragmentContainer).setVisibility(View.GONE);
        findViewById(R.id.emptyOrdersView).setVisibility(View.GONE);

        //Request orders from server
        FetchOrders fetchOrders = new FetchOrders(symbol, FetchOrders.OrderView.ACTIVE, 0, this);
        NetworkClient.getInstance(null).getSessionManager().pushRequest(fetchOrders);
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateChartData();
    }

    @Override
    protected void onPause(){
        super.onPause();
        //we don't need the chart updating in the background
        chartContainer.flushRequests();
        chartContainer.clearChart();
    }


    @Override
    public void onClick(View v) {
        int id = v.getId();
        if (id == R.id.button5m) {
            interval = StockDataHelper.Timeframe.FIVE_MIN;
            updateChartData();
            formatTile(interval.value);
        } else if (id == R.id.button15m) {
            interval = StockDataHelper.Timeframe.FIFTEEN_MIN;
            updateChartData();
            formatTile(interval.value);
        }
        else if (id == R.id.button1h) {
            interval = StockDataHelper.Timeframe.HOURLY;
            updateChartData();
            formatTile(interval.value);
        }
        else if (id == R.id.button1d) {
            interval = StockDataHelper.Timeframe.DAILY;
            updateChartData();
            formatTile(interval.value);
        }
        else if (id == R.id.buttonHome) {
            Intent intent = new Intent(this, HomeActivity.class);
            startActivity(intent);
        } else if (id == R.id.buttonBuy) {
            activeDialog = new OrderDialog(this, symbol, current_price, Order.OrderType.Long, this);
            activeDialog.show();
        } else if (id == R.id.buttonSell) {
            activeDialog = new OrderDialog(this, symbol, current_price, Order.OrderType.Short, this);
            activeDialog.show();
        }
    }

    public void formatTile(String timeFrame){
        textViewTitle.setText(symbol + " (" + timeFrame + ")");
    }


    //checks cache and asks server from data that isn't cached and returns cached data if available
    private void updateChartData() {
        chartContainer.setInterval(interval);
        chartContainer.flushRequests();
        chartContainer.clearChart();
        StockDataHelper stockDataHelper = new StockDataHelper( db_helper);
        LastFetchCacheHelper cacheHelper = new LastFetchCacheHelper(db_helper);
        //first we check latest cached data on the device
        long lastFetchTime = cacheHelper.getLastFetchTime(symbol, interval);
        RequestTickerData request = getRequest(symbol, interval, lastFetchTime, 0, true, false,  chartContainer);
        NetworkClient.getInstance(this).getSessionManager().pushRequest(request);
        RequestTickerData requestStream = getRequest(symbol, interval, 0, 0, false, true,  chartContainer);
        NetworkClient.getInstance(this).getSessionManager().pushRequest(requestStream);

        try{
         ArrayList<Candle> stockData = stockDataHelper.getCachedStockData(symbol, interval);
            if(stockData != null && stockData.size() > 0){
                chartContainer.addChunk(stockData);
                textViewPrice.setText(String.format(Locale.US,"%.6f", stockData.get(stockData.size()-1).close));
                current_price = stockData.get(stockData.size()-1).close;
            }
            else throw new Exception("Cache is empty");
        }
        catch (Exception e) {
            Log.e(DB_Helper.DB_LOG_TAG, "Error fetching cached stock data: " + e.getMessage());
            if(lastFetchTime != 0) {
                RequestTickerData request2 = getRequest(symbol, interval, 0, lastFetchTime,true, false,  chartContainer);
                NetworkClient.getInstance(this).getSessionManager().pushRequest(request2);
            }
        }
    }

    private RequestTickerData getRequest(String symbol, StockDataHelper.Timeframe timeframe, long start_ts, long end_ts ,boolean isSnapshot, boolean IsStream, SessionCallback caller){
        RequestTickerData request = new RequestTickerData(symbol, timeframe, start_ts, end_ts, isSnapshot, IsStream, caller);
        chartContainer.addToCurrentRequest(request.getReqId());
        return request;
    }


    @Override
    public void onDataReceived(DataType msgType, Object parsedData) {
        switch(msgType){
            case SEARCH_NO_RESULT:
                runOnUiThread( () -> {
                    Toast.makeText(this, (String) parsedData, LENGTH_SHORT).show();
                });
                break;
            case MARKET_DATA:
                current_price = (Double)parsedData;
                if(chartContainer.isDone()){
                runOnUiThread( () -> {
                    textViewPrice.setText(String.format(Locale.US,"%.6f", (Double)parsedData));
                    if(activeDialog != null && activeDialog.isShowing()){
                        activeDialog.updateLivePrice((Double)parsedData);
                    }
                });}
                break;
            case ORDER_INVALID:
            case ORDER_SLIP:
                runOnUiThread( () -> {
                    Toast.makeText(this, (String)parsedData, LENGTH_LONG).show();
                });
                break;
            case ORDER_RECEIVED:
                Order order = (Order)parsedData;
                runOnUiThread( () -> {
                    //update any order list that will appear in the chart activity
                    Toast.makeText(this, String.format("Order completed for %s, paid $%.16f per unit", symbol, order.getEntry_price()), LENGTH_LONG).show();
                    synchronized (allOrders){
                        allOrders.add(order);
                    }
                    updateOrdersUI();
                });
                break;
            case ORDERS_BATCH:
                runOnUiThread( () -> {
                    synchronized (allOrders) {
                        allOrders.addAll((ArrayList<Order>) parsedData);
                    }
                    updateOrdersUI();
                });
                break;
            case ORDER_CLOSED_SUCCESS:
                synchronized (allOrders) {
                    allOrders.remove((Order) parsedData);
                }
                runOnUiThread( () -> {
                    if (ordersFragment != null) {
                        ordersFragment.removeByOrderReference((Order)parsedData);

                        // 3. If that was the last order for this ticker, show the "Empty" view
                        if (ordersFragment.isEmpty()) {
                            findViewById(R.id.ordersFragmentContainer).setVisibility(View.GONE);
                            findViewById(R.id.emptyOrdersView).setVisibility(View.VISIBLE);
                        }
                    }
                });
                break;
        }
    }

    private void updateOrdersUI() {
        View container = findViewById(R.id.ordersFragmentContainer);
        View emptyView = findViewById(R.id.emptyOrdersView);

        if (allOrders.isEmpty()) {
            container.setVisibility(View.GONE);
            emptyView.setVisibility(View.VISIBLE);
        } else {
            emptyView.setVisibility(View.GONE);
            container.setVisibility(View.VISIBLE);

            // Initialize or Update the Fragment
            this.ordersFragment = OrdersList.newInstance(allOrders);
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.ordersFragmentContainer, ordersFragment)
                    .commit();
        }
    }

    @Override
    public void onActionRequired(int actionType, Object data) {
        //not needed yet
    }

    @Override
    public void onConfirmOrder(int quantity, double price, Order.OrderType type) {
        SendOrder request = new SendOrder(symbol, quantity, price, type, this, this);
        NetworkClient.getInstance(null).getSessionManager().pushRequest(request);
        Toast.makeText(this, String.format("Sent order for symbol: %s, order price: $%.16f", symbol, quantity * price), LENGTH_SHORT).show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (ordersFragment != null) {
            // This ensures all RequestTickerData requests are discarded
            ordersFragment.onPause();
        }
    }
}
