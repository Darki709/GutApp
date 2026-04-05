package com.example.gutapp.ui;

import static android.view.View.GONE;
import static android.view.View.INVISIBLE;
import static android.view.View.VISIBLE;

import android.content.Intent;
import android.database.Cursor;
import android.graphics.Color;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.gutapp.R;
import com.example.gutapp.data.SearchAdapter;
import com.example.gutapp.data.StockRow;
import com.example.gutapp.data.UserGlobals;
import com.example.gutapp.data.models.Order;
import com.example.gutapp.data.models.TickerInfo;
import com.example.gutapp.data.models.TickerInformation;
import com.example.gutapp.database.DB_Helper;
import com.example.gutapp.database.LastFetchCacheHelper;
import com.example.gutapp.session.DataType;
import com.example.gutapp.session.NetworkClient;
import com.example.gutapp.session.Requests.FetchOrders;
import com.example.gutapp.session.Requests.SearchTicker;
import com.example.gutapp.session.Requests.TickerInfoRequest;
import com.example.gutapp.ui.fragments.OrdersList;
import com.example.gutapp.ui.fragments.SearchFragment;
import com.example.gutapp.ui.fragments.StockLiveList;


import java.util.ArrayList;
import java.util.Locale;

public class HomeActivity extends SessionActivity implements OrdersList.Listener {
    public static final String HOME_LOG_TAG = "GutHome";

    TextView PL;
    OrdersList ordersList;
    StockLiveList stockLiveListFragment;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_home);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        stockLiveListFragment = StockLiveList.newInstance(loadStockList());
        //initialize stock list fragment
        if (savedInstanceState == null) {
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.stock_list_container, stockLiveListFragment)
                    .commit();
        }

        //ready the home page for presentation
        setUserTitle();

        //initialize search fragment
        if (savedInstanceState == null) {
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.search_container, new SearchFragment())
                    .commit();
        }
        PL = findViewById(R.id.totalPLValue);
        PL.setVisibility(INVISIBLE);
        FetchOrders fetchOrder = new FetchOrders(null, FetchOrders.OrderView.ACTIVE, 0, this);
        NetworkClient.getInstance(null).getSessionManager().pushRequest(fetchOrder);

        TextView balance = findViewById(R.id.totalBalanceValue);
        UserGlobals.getBalance().observe(this, newBalance -> {
            balance.setText(String.format(Locale.US, "$%.4f", newBalance));
        });
        findViewById(R.id.orders_container).setVisibility(GONE);
        findViewById(R.id.ordersTitle).setVisibility(GONE);
    }

    private ArrayList<TickerInfo> loadStockList() {
        Cursor cursor = (new LastFetchCacheHelper(DB_Helper.getInstance(this)).getStocks());
        ArrayList<TickerInfo> tickerList = new ArrayList<>();

        if (cursor.moveToFirst()) {
            do {
                String symbol = cursor.getString(cursor.getColumnIndexOrThrow("symbol"));
                String name = cursor.getString(cursor.getColumnIndexOrThrow("name"));
                tickerList.add(new TickerInfo(name, symbol));
            } while (cursor.moveToNext());
        }
        cursor.close();
        return tickerList;
    }

    private void setUserTitle() {
        TextView userTitle = findViewById(R.id.textViewUserTitle);
        if (UserGlobals.LOGGED_IN)
            userTitle.setText("Hello " + UserGlobals.USER_NAME + "!");
    }

    @Override
    public void onDataReceived(DataType msgType, Object parsedData) {
        switch (msgType) {
            case ORDERS_BATCH:
                ArrayList<Order> orders = (ArrayList<Order>) parsedData;
                if (orders != null && !orders.isEmpty()) {
                    updateOrdersList(orders);
                }
                else {
                    // No active orders? Hide the section
                    findViewById(R.id.orders_container).setVisibility(View.GONE);
                    PL.setVisibility(View.INVISIBLE);
                }
                break;
        }
    }

    private void updateOrdersList(ArrayList<Order> orders) {
        runOnUiThread( () -> {
            findViewById(R.id.orders_container).setVisibility(VISIBLE);
            PL.setVisibility(VISIBLE);
            findViewById(R.id.ordersTitle).setVisibility(VISIBLE);
            if (ordersList == null) {
                // Create new instance of the fragment
                // Passing null for symbol as we want to see ALL active orders on Home
                ordersList = OrdersList.newInstance(orders);

                getSupportFragmentManager().beginTransaction()
                        .replace(R.id.orders_container, ordersList)
                        .commit();
            }
            ordersList.setListener(this);
        });
    }

    @Override
    public void onActionRequired(int actionType, Object data) {
        //currently not in use
    }

    @Override
    public void PLUpdate(double totalPL) {
        runOnUiThread(() -> {
            String PL_format = totalPL > 1 ? "+$%.10f" : "-$%.10f";
            // 1. Update the Text
            PL.setText(String.format(Locale.US, PL_format, Math.abs(totalPL)));

            // 2. Change the Color based on value
            if (totalPL > 0.00001) {
                // GREEN: Making money
                PL.setTextColor(Color.parseColor("#00FF88"));
            } else if (totalPL < -0.00001) {
                // RED: Losing money
                PL.setTextColor(Color.parseColor("#FF4444"));
            } else {
                // WHITE: Break even or neutral
                PL.setTextColor(Color.WHITE);
            }
        });
    }

    @Override
    public void notifyOrderRemoved(Order order) {
        if(ordersList.isEmpty()){
            findViewById(R.id.orders_container).setVisibility(GONE);
            findViewById(R.id.ordersTitle).setVisibility(GONE);
            PL.setVisibility(GONE);
            if (stockLiveListFragment != null) {
                stockLiveListFragment.refreshVisibleRows();
            }
        }
    }
}