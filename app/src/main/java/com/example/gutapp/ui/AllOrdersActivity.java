package com.example.gutapp.ui;

import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.gutapp.R;
import com.example.gutapp.data.OrderRow; // Added
import com.example.gutapp.data.models.Order;
import com.example.gutapp.session.DataType;
import com.example.gutapp.session.NetworkClient;
import com.example.gutapp.session.Requests.FetchOrders;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class AllOrdersActivity extends SessionActivity implements OrderRow.OrderRowContainer {

    private android.widget.LinearLayout ordersContainer;
    private android.widget.ProgressBar loadingBar;
    private TextView emptyText;
    private List<Order> allOrders = new ArrayList<>();
    private final List<OrderRow> activeRows = new ArrayList<>(); // Track rows to stop streams
    private String currentSort = "date_desc";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_all_orders);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets sb = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(sb.left, sb.top, sb.right, sb.bottom);
            return insets;
        });

        ordersContainer = findViewById(R.id.ordersContainer);
        loadingBar      = findViewById(R.id.loadingBar);
        emptyText       = findViewById(R.id.emptyText);

        View backBtn = findViewById(R.id.btnBack);
        if (backBtn != null) backBtn.setOnClickListener(v -> finish());

        setupSortButtons();
        loadOrders();
        updateSortButtonsUI();
    }

    private void setupSortButtons() {
        int[] sortBtnIds = {R.id.btnSortDate, R.id.btnSortProfit, R.id.btnSortTicker, R.id.btnSortStatus};
        String[] sortKeys = {"date_desc", "profit_desc", "ticker_asc", "status"};
        for (int i = 0; i < sortBtnIds.length; i++) {
            String key = sortKeys[i];
            View btn = findViewById(sortBtnIds[i]);
            if (btn != null) btn.setOnClickListener(v -> {
                if (currentSort.startsWith(key.split("_")[0]) && !key.equals("ticker_asc") && !key.equals("status")) {
                    currentSort = currentSort.endsWith("asc") ? key.replace("asc","desc") : key.replace("desc","asc");
                } else {
                    currentSort = key;
                }
                updateSortButtonsUI();
                renderOrders();
            });
        }
    }

    private void loadOrders() {
        loadingBar.setVisibility(View.VISIBLE);
        emptyText.setVisibility(View.GONE);
        clearExistingRows();
        FetchOrders req = new FetchOrders(null, FetchOrders.OrderView.ALL, 0, this);
        NetworkClient.getInstance(this).getSessionManager().pushRequest(req);
    }

    private void clearExistingRows() {
        for (OrderRow row : activeRows) {
            row.stop(); // Stop all network streams
        }
        activeRows.clear();
        ordersContainer.removeAllViews();
    }

    @Override
    public void onDataReceived(DataType msgType, Object parsedData) {
        if (msgType != DataType.ORDERS_BATCH) return;
        allOrders = (ArrayList<Order>) parsedData;
        runOnUiThread(() -> {
            loadingBar.setVisibility(View.GONE);
            if (allOrders.isEmpty()) { emptyText.setVisibility(View.VISIBLE); return; }
            renderOrders();
        });
    }

    private void renderOrders() {
        clearExistingRows();
        List<Order> sorted = new ArrayList<>(allOrders);
        sortOrders(sorted, currentSort);

        String prevDate = "";
        SimpleDateFormat dayFmt = new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault());

        for (Order order : sorted) {
            // Date Header Logic
            String dayStr = dayFmt.format(new Date(order.getEntry_ts()));
            if (!dayStr.equals(prevDate)) {
                ordersContainer.addView(makeDateHeader(dayStr));
                prevDate = dayStr;
            }

            // Use your OrderRow class instead of makeOrderCard
            OrderRow row = new OrderRow(order, this);
            row.setContainer(this); // Listen for close events
            activeRows.add(row);
            ordersContainer.addView(row.getView());
        }
    }

    // Implementation of OrderRowContainer
    @Override public void notifyPLChange() { /* Optional: update a total portfolio P&L here */ }

    @Override
    public void notifyClosed(Order order) {
        // Refresh the list when an order is closed from this screen
        loadOrders();
    }

    private void sortOrders(List<Order> orders, String sortKey) {
        Comparator<Order> cmp;
        switch (sortKey) {
            case "date_asc": cmp = Comparator.comparingLong(Order::getEntry_ts); break;
            case "profit_desc": cmp = (a, b) -> Double.compare(getProfit(b), getProfit(a)); break;
            case "profit_asc": cmp = Comparator.comparingDouble(this::getProfit); break;
            case "ticker_asc": cmp = Comparator.comparing(Order::getSymbol); break;
            case "status": cmp = (a, b) -> Boolean.compare(b.isActive(), a.isActive()); break;
            default: cmp = (a, b) -> Long.compare(b.getEntry_ts(), a.getEntry_ts());
        }
        orders.sort(cmp);
    }

    private double getProfit(Order order) {
        if (order.isActive()) return 0;
        return order.getEnd_price().map(ep -> {
            double diff = order.getType() == Order.OrderType.Long ? ep - order.getEntry_price() : order.getEntry_price() - ep;
            return diff * order.getQuantity();
        }).orElse(0.0);
    }

    private View makeDateHeader(String dateStr) {
        TextView tv = new TextView(this);
        tv.setText(dateStr);
        tv.setTextColor(Color.parseColor("#546E7A"));
        tv.setTextSize(11f);
        tv.setTypeface(null, android.graphics.Typeface.BOLD);
        android.widget.LinearLayout.LayoutParams lp = new android.widget.LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, dp(12), 0, dp(4));
        tv.setLayoutParams(lp);
        tv.setPadding(dp(16), 0, 0, 0);
        return tv;
    }

    private void updateSortButtonsUI() {
        int[] ids = {R.id.btnSortDate,R.id.btnSortProfit,R.id.btnSortTicker,R.id.btnSortStatus};
        String[] keys = {"date","profit","ticker","status"};
        for (int i=0;i<ids.length;i++) {
            View btn = findViewById(ids[i]);
            if (btn instanceof TextView) {
                boolean active = currentSort.startsWith(keys[i]);
                ((TextView)btn).setTextColor(active ? Color.parseColor("#2196F3") : Color.parseColor("#78909C"));
                btn.setBackgroundResource(active ? R.drawable.chart_btn_active : R.drawable.chart_btn_inactive);
            }
        }
    }

    private int dp(int val) {
        return Math.round(val * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        for (OrderRow row : activeRows) row.stop(); // Prevent memory leaks/network waste
    }

    @Override protected void refreshNetwork() { loadOrders(); }
    @Override public void onActionRequired(int a, Object d) {}
}