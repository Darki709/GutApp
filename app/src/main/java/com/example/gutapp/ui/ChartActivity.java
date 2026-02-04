package com.example.gutapp.ui;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.PopupWindow;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.gutapp.R;
import com.example.gutapp.data.StockChart;
import com.example.gutapp.data.models.Candle;
import com.example.gutapp.database.DB_Helper;
import com.example.gutapp.database.LastFetchCacheHelper;
import com.example.gutapp.database.StockDataHelper;

import com.example.gutapp.session.NetworkClient;
import com.example.gutapp.session.Requests.RequestTickerData;
import com.example.gutapp.session.SessionCallback;
import com.github.mikephil.charting.charts.CombinedChart;
import com.github.mikephil.charting.components.AxisBase;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.CandleData;
import com.github.mikephil.charting.data.CandleDataSet;
import com.github.mikephil.charting.data.CandleEntry;
import com.github.mikephil.charting.data.CombinedData;
import com.github.mikephil.charting.formatter.ValueFormatter;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class ChartActivity extends AppCompatActivity implements View.OnClickListener, SessionCallback {

    public static final String CHART_LOG_TAG = "GutChart";

    private DB_Helper db_helper;
    private StockDataHelper stockDataHelper;
    private StockChart chartContainer;
    private CombinedChart chart;
    private String symbol; // Default symbol
    private TextView textViewTitle;
    private StockDataHelper.Timeframe interval;

    @SuppressLint("SetTextI11n")
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
        String name = intent.getStringExtra("name");

        //initialize important database objects
        db_helper = DB_Helper.getInstance(this);

        chart = findViewById(R.id.stockChart);

        chartContainer = new StockChart(chart);
        chartContainer.setupChart();


        // Set up button listeners
        findViewById(R.id.button5m).setOnClickListener(this);
        findViewById(R.id.button15m).setOnClickListener(this);
        findViewById(R.id.button1h).setOnClickListener(this);
        findViewById(R.id.button1d).setOnClickListener(this);
        findViewById(R.id.indicatorsButton).setOnClickListener(this);


        textViewTitle = findViewById(R.id.textViewTitle);

        ImageButton buttonHome = findViewById(R.id.buttonHome);
        buttonHome.setOnClickListener(this);
        StockDataHelper.Timeframe interval = StockDataHelper.Timeframe.DAILY;
        formatTile(interval.value);

        this.interval = StockDataHelper.Timeframe.DAILY;
        updateChartData(interval);
        NetworkClient.getInstance(this).getSessionManager().setCallback(this);
    }





    @Override
    public void onClick(View v) {
        int id = v.getId();
        if (id == R.id.button5m) {
            interval = StockDataHelper.Timeframe.FIVE_MIN;
            updateChartData(interval);
            formatTile(interval.value);
        } else if (id == R.id.button15m) {
            interval = StockDataHelper.Timeframe.FIFTEEN_MIN;
            updateChartData(interval);
            formatTile(interval.value);
        }
        else if (id == R.id.button1h) {
            interval = StockDataHelper.Timeframe.HOURLY;
            updateChartData(interval);
            formatTile(interval.value);
        }
        else if (id == R.id.button1d) {
            interval = StockDataHelper.Timeframe.DAILY;
            updateChartData(interval);
            formatTile(interval.value);
        }
        else if (id == R.id.buttonHome) {
            Intent intent = new Intent(this, HomeActivity.class);
            startActivity(intent);
        }
    }

    public void formatTile(String timeFrame){
        textViewTitle.setText(symbol + " (" + timeFrame + ")");
    }


    //checks cache and asks server from data that isn't cached and returns cached data if available
    private void updateChartData(StockDataHelper.Timeframe timeframe) {
        stockDataHelper = new StockDataHelper( db_helper);
        LastFetchCacheHelper cacheHelper = new LastFetchCacheHelper(db_helper);
        //first we check latest cached data on the device
        long lastFetchTime = cacheHelper.getLastFetchTime(symbol, timeframe);
        RequestTickerData request = getRequest(symbol, timeframe, lastFetchTime, 0, true, false,  chartContainer);
        NetworkClient.getInstance(this).getSessionManager().pushRequest(request);

        try{
         ArrayList<Candle> stockData = stockDataHelper.getCachedStockData(symbol, timeframe);
            if(stockData != null && stockData.size() > 0){
                chartContainer.addChunk(stockData, interval);
            }
            else throw new Exception("Cache is empty");
        }
        catch (Exception e) {
            Log.e(DB_Helper.DB_LOG_TAG, "Error fetching cached stock data: " + e.getMessage());
            RequestTickerData request2 = getRequest(symbol, timeframe, 0, lastFetchTime,true, false,  chartContainer);
            NetworkClient.getInstance(this).getSessionManager().pushRequest(request2);
        }
    }

    private RequestTickerData getRequest(String symbol, StockDataHelper.Timeframe timeframe, long start_ts, long end_ts ,boolean isSnapshot, boolean IsStream, SessionCallback caller){
        RequestTickerData request = new RequestTickerData(symbol, timeframe, start_ts, end_ts, isSnapshot, IsStream, caller);
        chartContainer.addToCurrentRequest(request.getReqId());
        return request;
    }


    @Override
    public void onDataReceived(int msgType, Object parsedData) {
        //not needed yet
    }

    @Override
    public void onActionRequired(int actionType) {
        //used to send error messages to the user
    }
}
