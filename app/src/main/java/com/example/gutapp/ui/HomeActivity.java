package com.example.gutapp.ui;

import static android.view.View.GONE;
import static android.view.View.INVISIBLE;
import static android.view.View.VISIBLE;

import android.content.Intent;
import android.database.Cursor;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.core.graphics.Insets;
import androidx.core.view.GravityCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.drawerlayout.widget.DrawerLayout;

import com.example.gutapp.R;
import com.example.gutapp.data.UserGlobals;
import com.example.gutapp.data.models.Order;
import com.example.gutapp.data.models.TickerInfo;
import com.example.gutapp.database.DB_Helper;
import com.example.gutapp.database.LastFetchCacheHelper;
import com.example.gutapp.session.DataType;
import com.example.gutapp.session.NetworkClient;
import com.example.gutapp.session.Requests.FetchOrders;
import com.example.gutapp.ui.fragments.OrdersList;
import com.example.gutapp.ui.fragments.SearchFragment;
import com.example.gutapp.ui.fragments.StockLiveList;

import java.util.ArrayList;
import java.util.Locale;

public class HomeActivity extends SessionActivity implements OrdersList.Listener {

    public static final String HOME_LOG_TAG = "GutHome";

    private TextView PL;
    private OrdersList ordersList;
    private StockLiveList stockLiveListFragment;
    private DrawerLayout drawerLayout;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_home);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets sb = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(sb.left, sb.top, sb.right, sb.bottom);
            return insets;
        });

        drawerLayout = findViewById(R.id.drawerLayout);

        // ── Logo → opens drawer ───────────────────────────────────
        findViewById(R.id.imageView).setOnClickListener(v ->
                drawerLayout.openDrawer(GravityCompat.START));

        // ── Drawer nav items ──────────────────────────────────────
        findViewById(R.id.navProfile).setOnClickListener(v -> {
            drawerLayout.closeDrawers();
            startActivity(new Intent(this, ProfileActivity.class));
        });

        findViewById(R.id.navOrdersHistory).setOnClickListener(v -> {
            drawerLayout.closeDrawers();
            startActivity(new Intent(this, AllOrdersActivity.class));
        });

        findViewById(R.id.navHome).setOnClickListener(v ->
                drawerLayout.closeDrawers());

        findViewById(R.id.navLogout).setOnClickListener( v -> {
            NetworkClient.getInstance(null).stop();
            Intent intent = new Intent(this, LoginPage.class);
            startActivity(intent);
        });

        // ── Drawer username ───────────────────────────────────────
        TextView drawerName = findViewById(R.id.drawerUserName);
        if (drawerName != null && UserGlobals.USER_NAME != null)
            drawerName.setText("Hello " + UserGlobals.USER_NAME + "!");

        // ── Stock list fragment ───────────────────────────────────
        stockLiveListFragment = StockLiveList.newInstance(loadStockList());
        if (savedInstanceState == null) {
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.stock_list_container, stockLiveListFragment)
                    .commit();
        }

        setUserTitle();

        // ── Search fragment ───────────────────────────────────────
        if (savedInstanceState == null) {
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.search_container, new SearchFragment())
                    .commit();
        }

        PL = findViewById(R.id.totalPLValue);
        PL.setVisibility(INVISIBLE);

        TextView balance = findViewById(R.id.totalBalanceValue);
        UserGlobals.getBalance().observe(this, newBalance ->
                balance.setText(String.format(Locale.US, "$%.4f", newBalance)));

        findViewById(R.id.orders_container).setVisibility(GONE);
        findViewById(R.id.ordersTitle).setVisibility(GONE);
    }



    private ArrayList<TickerInfo> loadStockList() {
        Cursor cursor = new LastFetchCacheHelper(DB_Helper.getInstance(this)).getStocks();
        ArrayList<TickerInfo> list = new ArrayList<>();
        if (cursor.moveToFirst()) {
            do {
                list.add(new TickerInfo(
                        cursor.getString(cursor.getColumnIndexOrThrow("name")),
                        cursor.getString(cursor.getColumnIndexOrThrow("symbol"))));
            } while (cursor.moveToNext());
        }
        cursor.close();
        return list;
    }

    private void setUserTitle() {
        TextView tv = findViewById(R.id.textViewUserTitle);
        if (UserGlobals.LOGGED_IN && tv != null)
            tv.setText("Hello " + UserGlobals.USER_NAME + "!");
    }

    @Override
    public void onDataReceived(DataType msgType, Object parsedData) {
        if (msgType != DataType.ORDERS_BATCH) return;
        ArrayList<Order> orders = (ArrayList<Order>) parsedData;
        if (orders == null || orders.isEmpty()) {
            runOnUiThread(() -> {
                findViewById(R.id.orders_container).setVisibility(View.GONE);
                findViewById(R.id.ordersTitle).setVisibility(GONE);
                PL.setVisibility(View.INVISIBLE);
            });
        } else {
            updateOrdersList(orders);
        }
    }

    private void updateOrdersList(ArrayList<Order> orders) {
        runOnUiThread(() -> {
            findViewById(R.id.orders_container).setVisibility(VISIBLE);
            PL.setVisibility(VISIBLE);
            findViewById(R.id.ordersTitle).setVisibility(VISIBLE);
            if (ordersList == null) {
                ordersList = OrdersList.newInstance(orders);
                getSupportFragmentManager().beginTransaction()
                        .replace(R.id.orders_container, ordersList)
                        .commit();
            }
            ordersList.setListener(this);
        });
    }

    @Override
    public void PLUpdate(double totalPL) {
        runOnUiThread(() -> {
            String fmt = totalPL > 0 ? "+$%.10f" : (totalPL == 0 ? "$0" : "-$%.10f");
            PL.setText(String.format(Locale.US, fmt, Math.abs(totalPL)));
            if      (totalPL > 0) PL.setTextColor(Color.parseColor("#00FF88"));
            else if (totalPL < 0) PL.setTextColor(Color.parseColor("#FF4444"));
            else                   PL.setTextColor(Color.WHITE);
        });
    }

    @Override
    public void notifyOrderRemoved(Order order) {
        if (ordersList != null && ordersList.isEmpty()) {
            findViewById(R.id.orders_container).setVisibility(GONE);
            findViewById(R.id.ordersTitle).setVisibility(GONE);
            PL.setVisibility(GONE);
            if (stockLiveListFragment != null) stockLiveListFragment.refreshVisibleRows();
        }
    }

    @Override
    protected void refreshNetwork() { onResume(); }

    @Override
    protected void onResume(){
        super.onResume();
        ordersList = null;
        FetchOrders fetchOrder = new FetchOrders(null, FetchOrders.OrderView.ACTIVE, 0, this);
        NetworkClient.getInstance(null).getSessionManager().pushRequest(fetchOrder);
    }
}