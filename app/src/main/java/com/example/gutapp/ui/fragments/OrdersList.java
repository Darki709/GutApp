package com.example.gutapp.ui.fragments;

import android.graphics.Color;
import android.graphics.Rect;
import android.location.GnssAntennaInfo;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.gutapp.R;
import com.example.gutapp.data.OrderRow;
import com.example.gutapp.data.models.Order;

import java.util.ArrayList;


import lombok.Setter;

public class OrdersList extends Fragment implements OrderRow.OrderRowContainer{
    private final ArrayList<OrderRow> activeOrderRows = new ArrayList<>();
    private LinearLayout container;
    private ScrollView scrollView;

    @Nullable
    @Setter
    private Listener listener;

    public interface Listener{
        void PLUpdate(double totalPL);
        void notifyOrderRemoved(Order order);
    }

    public static OrdersList newInstance(ArrayList<Order> initialOrders) {
        OrdersList fragment = new OrdersList();
        Bundle args = new Bundle();
        args.putSerializable("orders", initialOrders); // Assuming Order is Serializable
        fragment.setArguments(args);
        return fragment;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.stock_scroll_list, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        this.container = view.findViewById(R.id.stockContainer);
        this.scrollView = view.findViewById(R.id.scrollView);

        // Hide buttons from the original layout that aren't needed for active orders
        view.findViewById(R.id.loadBtn).setVisibility(View.GONE);
        view.findViewById(R.id.loadProgress).setVisibility(View.GONE);

        container.removeAllViews();

        //we want all orders to update so we can calculate P&L
        // Smart Visibility Logic: Only stream prices for rows you can see
        //scrollView.getViewTreeObserver().addOnScrollChangedListener(this::updateVisibleOrderSubscriptions);

        if (getArguments() != null) {
            ArrayList<Order> orders = (ArrayList<Order>) getArguments().getSerializable("orders");
            if (orders != null) {
                displayOrders(orders);
            }
        }
    }

    public void displayOrders(ArrayList<Order> orders) {
        for (Order order : orders) {
            if (order.isActive()) {
                addOrderRow(order);
            }
        }
        // Initial check for visibility after layout
        //scrollView.post(this::updateVisibleOrderSubscriptions);
    }

    public void addOrderRow(Order order) {
        OrderRow orderRow = new OrderRow(order, requireActivity());
        orderRow.setContainer(this);
        activeOrderRows.add(orderRow);

        // Add to UI with a divider
        View rowView = orderRow.getView();

        View divider = new View(requireActivity());
        divider.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1));
        divider.setBackgroundColor(Color.parseColor("#222222"));

        container.addView(rowView);
        container.addView(divider);
        orderRow.startStreaming();
    }

    private void updateVisibleOrderSubscriptions() {
        Rect scrollBounds = new Rect();
        scrollView.getHitRect(scrollBounds);

        for (OrderRow row : activeOrderRows) {
            // NEW: Only run streaming logic if the underlying order is ACTIVE
            if (!row.getOrder().isActive()) continue;

            View view = row.getView();
            if (view.getLocalVisibleRect(scrollBounds)) {
                if (!row.isActive()) {
                    row.startStreaming();
                }
            } else {
                if (row.isActive()) {
                    row.stop();
                }
            }
        }
    }

    /*
     * Call this from your Activity when a 'Close Order' network request succeeds.
     */
    /**
     * Finds the row associated with a specific Order object and removes it.
     */
    public void removeByOrderReference(Order orderToRemove) {
        OrderRow target = null;

        // Search for the row that holds this exact order instance
        for (OrderRow row : activeOrderRows) {
            if (row.getOrder().equals(orderToRemove)) {
                target = row;
                break;
            }
        }

        if (target != null) {
            // Clean up networking and UI
            target.stop();
            container.removeView(target.getView()); //
            activeOrderRows.remove(target); //
        }
    }

    public void removeByOrderId(int id) {
        OrderRow target = null;

        // Search for the row that holds this exact order instance
        for (OrderRow row : activeOrderRows) {
            if(row.getOrder().getOrder_id() == id){
                target = row;
                break;
            }
        }

        if (target != null) {
            // Clean up networking and UI
            target.stop();
            container.removeView(target.getView()); //
            activeOrderRows.remove(target); //
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        // Only resume what is visible AND active
        //updateVisibleOrderSubscriptions();
        for (OrderRow row : activeOrderRows) {
            row.startStreaming();
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        // Stop all active streams (Inactive rows are already stopped, so this is safe)
        for (OrderRow row : activeOrderRows) {
            row.stop();
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        for (OrderRow row : activeOrderRows) {
            row.stop();
        }
    }

    public boolean isEmpty() {
        return activeOrderRows.isEmpty();
    }



    //when PL of an orderRow changes it will make it's container notify the listener
    @Override
    public void notifyPLChange(){
        if(listener == null) return;
        double totalPL = 0;
        for(OrderRow row : activeOrderRows){
            totalPL += row.getPL();
        }
        listener.PLUpdate(totalPL);
    }

    @Override
    public void notifyClosed(Order order) {
        removeByOrderId(order.getOrder_id());
        notifyPLChange();
        if(listener != null){
            listener.notifyOrderRemoved(order);
        }
    }

    public OrderRow getOrderRow(int orderId){
        for(OrderRow row : activeOrderRows){
            if(row.getOrder().getOrder_id() == orderId) return row;
        }
        throw new IllegalArgumentException("There is no order with this order id");
    }
}