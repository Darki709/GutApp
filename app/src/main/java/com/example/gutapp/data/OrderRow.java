package com.example.gutapp.data;

import static android.widget.Toast.LENGTH_SHORT;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;

import com.example.gutapp.data.models.Order;
import com.example.gutapp.data.models.PriceChunk;
import com.example.gutapp.session.DataType;
import com.example.gutapp.session.NetworkClient;
import com.example.gutapp.session.Requests.EndOrder;
import com.example.gutapp.session.Requests.RequestTickerData;
import com.example.gutapp.session.SessionCallback;
import com.example.gutapp.database.StockDataHelper;
import com.example.gutapp.ui.ChartActivity;

import lombok.Getter;
import lombok.Setter;

public class OrderRow implements SessionCallback {
    private final LinearLayout rowLayout;
    @Getter
    private Order order;
    private final Activity context;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private TextView priceView;
    private TextView plView;
    private int reqId = -1;
    private double currentPrice;
    private double totalPL = 0;
    private android.widget.Button closeButton;
    private boolean isClosing = false;
    private final java.text.SimpleDateFormat dateFormat = new java.text.SimpleDateFormat("MMM dd, HH:mm", java.util.Locale.getDefault());
    @Nullable
    @Setter
    private OrderRowContainer container;

    public interface OrderRowContainer{
        void notifyPLChange(); //notify container for PL update
        void notifyClosed(Order order);
    }

    public OrderRow(Order order, Activity context) {
        this.order = order;
        this.context = context;

        // 1. Initialize price based on state
        if (order.isActive()) {
            this.currentPrice = order.getEntry_price();
        } else {
            // For closed orders, use the end_price if available
            this.currentPrice = order.getEnd_price().orElse(order.getEntry_price());
        }

        this.rowLayout = createLayout();

        // 2. State-dependent logic
        if (order.isActive()) {
            startStreaming();
        } else {
            renderInactiveState();
        }
    }

    private void renderInactiveState() {
        // One-time UI update for closed orders using final data
        updateUI(currentPrice);

        // Hide UI elements that don't apply to history
        closeButton.setVisibility(View.GONE);
        rowLayout.setAlpha(0.85f); // Subtly dim inactive rows
    }

    public void startStreaming() {
        if (!order.isActive() || reqId != -1)  return;

        RequestTickerData streamReq = new RequestTickerData(
                order.getSymbol(),
                StockDataHelper.Timeframe.ONE_MIN,
                0, 0, false, true, this);

        this.reqId = streamReq.getReqId();
        NetworkClient.getInstance(null).getSessionManager().pushRequest(streamReq);
    }

    private LinearLayout createLayout() {
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.HORIZONTAL);
        layout.setPadding(30, 32, 30, 32);
        layout.setGravity(Gravity.CENTER_VERTICAL);

        TypedValue outValue = new TypedValue();
        context.getTheme().resolveAttribute(android.R.attr.selectableItemBackground, outValue, true);
        layout.setBackgroundResource(outValue.resourceId);

        // Left Info Group
        LinearLayout infoGroup = new LinearLayout(context);
        infoGroup.setOrientation(LinearLayout.VERTICAL);
        infoGroup.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.2f));

        TextView symText = new TextView(context);
        symText.setText(order.getSymbol());
        symText.setTextColor(Color.WHITE);
        symText.setTextSize(18);
        symText.setTypeface(null, Typeface.BOLD);
        symText.setOnClickListener( v -> {
            Intent intent = new Intent(context, ChartActivity.class);
            intent.putExtra("symbol", order.getSymbol());
            intent.putExtra("name", "");
            context.startActivity(intent);
        });

        TextView detailText = new TextView(context);
        String side = order.getType() == Order.OrderType.Long ? "LONG" : "SHORT";
        String dateStr = dateFormat.format(new java.util.Date(order.getEntry_ts()));
        // Show "CLOSED" status in detail text for history
        String status = order.isActive() ? "" : " [CLOSED]";
        detailText.setText(String.format("%s%s • %d units $%.4f\nOpened: %s",
                side, status, order.getQuantity(), order.getEntry_price(), dateStr));
        detailText.setTextColor(Color.GRAY);
        detailText.setTextSize(12);

        infoGroup.addView(symText);
        infoGroup.addView(detailText);

        // Right Price/P&L Group
        LinearLayout priceGroup = new LinearLayout(context);
        priceGroup.setOrientation(LinearLayout.VERTICAL);
        priceGroup.setGravity(Gravity.END);

        priceView = new TextView(context);
        priceView.setText(String.format("%.4f", currentPrice));
        priceView.setTextColor(Color.LTGRAY);
        priceView.setTextSize(16);

        plView = new TextView(context);
        plView.setText("0.00");
        plView.setTextSize(16);
        plView.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);

        priceGroup.addView(priceView);
        priceGroup.addView(plView);

        // Close Button (Only relevant for Active orders)
        closeButton = new android.widget.Button(context);
        closeButton.setText("CLOSE");
        closeButton.setTextSize(11);
        closeButton.setTextColor(Color.WHITE);
        closeButton.setBackgroundColor(Color.parseColor("#333333"));

        LinearLayout.LayoutParams btnParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 36, context.getResources().getDisplayMetrics()));
        btnParams.setMargins(24, 0, 0, 0);
        closeButton.setLayoutParams(btnParams);

        closeButton.setOnClickListener(v -> {
            if (!isClosing) handleCloseInitiated();
        });

        layout.addView(infoGroup);
        layout.addView(priceGroup);
        layout.addView(closeButton);
        return layout;
    }

    private void updateUI(double newPrice) {
        this.currentPrice = newPrice;

        // Calculate P&L: (Current - Entry) * Qty for Long | (Entry - Current) * Qty for Short
        double diff = (order.getType() == Order.OrderType.Long)
                ? (newPrice - order.getEntry_price())
                : (order.getEntry_price() - newPrice);

        totalPL = diff * order.getQuantity();

        mainHandler.post(() -> {
            priceView.setText(String.format("%.4f", newPrice));
            plView.setText(String.format("%+.4f", totalPL));

            if (totalPL > 0) {
                // GREEN: Making money
                int green = Color.parseColor("#00FF88");
                plView.setTextColor(green);
                priceView.setTextColor(green); // Update the price color too
            } else if (totalPL < 0) {
                // RED: Losing money
                int red = Color.parseColor("#FF4444");
                plView.setTextColor(red);
                priceView.setTextColor(red); // Update the price color too
            } else {
                // WHITE: Break even
                plView.setTextColor(Color.WHITE);
                priceView.setTextColor(Color.WHITE);
            }
        });
    }

    public void stop() {
        if (reqId != -1) {
            NetworkClient.getInstance(null).getSessionManager().discardRequest(reqId);
            reqId = -1;
        }
    }

    public boolean isActive() { return reqId != -1; }

    public LinearLayout getView() { return rowLayout; }

    @Override
    public void onDataReceived(DataType msgType, Object parsedData) {
        switch(msgType){
            case TICKER_STREAM:
                PriceChunk chunk = (PriceChunk) parsedData;
                if (chunk != null && chunk.reqId == reqId && !chunk.chunk.isEmpty()) {
                    double lastPrice = chunk.chunk.get(chunk.chunk.size() - 1).close;
                    updateUI(lastPrice);
                    if(container != null) container.notifyPLChange();
                }
                break;
            case ORDER_CLOSED_SUCCESS:
                closeOrder();
                order = (Order)parsedData;
                mainHandler.post( () -> {
                    if (container != null) container.notifyClosed(order);
                });
                break;
            case ORDER_CLOSED_FAILURE:
                mainHandler.post( () -> {
                    Toast.makeText(context, (String) parsedData, LENGTH_SHORT).show();
                });
                break;
        }
    }

    private void handleCloseInitiated() {
        isClosing = true;
        closeButton.setEnabled(false);
        closeButton.setText("...");
        rowLayout.setAlpha(0.6f);

        Log.d("ORDER_ACTION", "Closing ID: " + order.getOrder_id());

        EndOrder request = new EndOrder(order, currentPrice, this.context, this);
        NetworkClient.getInstance(null).getSessionManager().pushRequest(request);
    }

    @Override public void onActionRequired(int actionType, @Nullable Object data) {}

    public double getPL(){
        return totalPL;
    }

    private void closeOrder(){
        stop();
        //logic to change the visibility of the order when it finishes
    }
}