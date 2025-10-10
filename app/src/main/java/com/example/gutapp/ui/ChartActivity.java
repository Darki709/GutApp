package com.example.gutapp.ui;

import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.View;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.gutapp.R;
import com.example.gutapp.database.DB_Helper;
import com.example.gutapp.database.DB_Index;
import com.example.gutapp.database.StockDataHelper;

//charting library imports
import com.github.mikephil.charting.charts.CandleStickChart;
import com.github.mikephil.charting.charts.CombinedChart;
import com.github.mikephil.charting.components.AxisBase;
import com.github.mikephil.charting.data.CandleData;
import com.github.mikephil.charting.data.CandleDataSet;
import com.github.mikephil.charting.data.CandleEntry;
import com.github.mikephil.charting.data.CombinedData;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.formatter.ValueFormatter;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class ChartActivity extends AppCompatActivity implements View.OnClickListener{

    //defining global variables for easy access
    DB_Helper db_helper;
    StockDataHelper stockDataHelper;
    CombinedChart chart;



    /**
     * Called when the activity is first created.
     */
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
        //initialize db helpers
        db_helper = new DB_Helper(this);
        db_helper.getWritableDatabase();
        stockDataHelper = (StockDataHelper)db_helper.getHelper(DB_Index.STOCK_TABLE);


        chart = findViewById(R.id.stockChart);
        String symbol = "TSLA";
        ArrayList<CandleEntry> stockData;
        try {
            stockData = stockDataHelper.getCachedStockData(symbol);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        if (stockData == null || stockData.isEmpty()) {
            Log.e(db_helper.DB_LOG_TAG, "Stock data is empty or null. Cannot create chart.");
            return; // Avoid crashing if there's no data
        }
        CandleDataSet dataSet = new CandleDataSet(stockData, "Stock Price");
        dataSet.setIncreasingColor(Color.GREEN);
        dataSet.setDecreasingColor(Color.RED);
        CandleData data = new CandleData(dataSet);
        CombinedData combinedData = new CombinedData();
        combinedData.setData(data);
        chart.setData(combinedData);
        chart.getXAxis().setValueFormatter(new ValueFormatter() {
            private final SimpleDateFormat fmt = new SimpleDateFormat("HH:mm", Locale.US);


            public String getAxisLabel(float value, AxisBase axis) {
                // The 'value' is the index of the entry.
                int index = (int) value;

                // Ensure the index is within the bounds of your data list.
                if (index >= 0 && index < stockData.size()) {
                    // Retrieve the original CandleEntry using the index.
                    CandleEntry entry = stockData.get(index);
                    // Get the timestamp from the entry's x-value and format it.
                    // Assumes entry.getX() returns a timestamp in milliseconds.
                    return fmt.format(new Date((long) entry.getX()));
                }
                // Return an empty string for out-of-bounds indices.
                return "";
            }
        });
        Log.i(db_helper.DB_LOG_TAG, "end create chart");
    }

    /**
     * Called when the activity is becoming visible to the user.
     */
    @Override
    protected void onStart() {
        super.onStart();
    }

    /**
     * Called when the activity will start interacting with the user.
     */
    @Override
    protected void onResume() {
        super.onResume();
    }

    /**
     * Called when the system is about to start resuming a previous activity.
     */
    @Override
    protected void onPause() {
        super.onPause();
    }

    /**
     * Called when the activity is no longer visible to the user.
     */
    @Override
    protected void onStop() {
        super.onStop();
    }

    /**
     * The final call you receive before your activity is destroyed.
     */
    @Override
    protected void onDestroy() {
        super.onDestroy();
    }


    //onclick method for buttons
    @Override
    public void onClick(View view) {

    }
}
