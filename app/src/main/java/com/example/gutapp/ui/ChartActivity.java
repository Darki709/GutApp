package com.example.gutapp.ui;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.gutapp.R;
import com.example.gutapp.data.StockChart;
import com.example.gutapp.data.models.Candle;
import com.example.gutapp.database.DB_Helper;
import com.example.gutapp.database.LastFetchCacheHelper;
import com.example.gutapp.database.StockDataHelper;

import com.example.gutapp.session.DataType;
import com.example.gutapp.session.NetworkClient;
import com.example.gutapp.session.Requests.RequestTickerData;
import com.example.gutapp.session.Requests.TickerInfoRequest;
import com.example.gutapp.session.SessionCallback;
import com.github.mikephil.charting.charts.CombinedChart;

import java.util.ArrayList;

public class ChartActivity extends SessionActivity implements View.OnClickListener {

    public static final String CHART_LOG_TAG = "GutChart";

    private DB_Helper db_helper;
    private StockChart chartContainer;
    private String symbol; // Default symbol
    private String name;
    private TextView textViewTitle;
    private StockDataHelper.Timeframe interval;

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


        // Set up button listeners
        findViewById(R.id.button5m).setOnClickListener(this);
        findViewById(R.id.button15m).setOnClickListener(this);
        findViewById(R.id.button1h).setOnClickListener(this);
        findViewById(R.id.button1d).setOnClickListener(this);
        findViewById(R.id.indicatorsButton).setOnClickListener(this);


        textViewTitle = findViewById(R.id.textViewTitle);

        ImageButton buttonHome = findViewById(R.id.buttonHome);
        buttonHome.setOnClickListener(this);
        this.interval = StockDataHelper.Timeframe.DAILY;
        formatTile(this.interval.value);

        TickerInfoRequest req = new TickerInfoRequest(symbol, this);
        NetworkClient.getInstance(null).getSessionManager().pushRequest(req);
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
        }
    }

    public void formatTile(String timeFrame){
        textViewTitle.setText(name + " (" + timeFrame + ")");
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
                    Toast.makeText(this, (String) parsedData, Toast.LENGTH_SHORT).show();
                });
        }
    }

    @Override
    public void onActionRequired(int actionType, Object data) {
        //not needed yet
    }
}
