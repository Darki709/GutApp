package com.example.gutapp.data;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.gutapp.R;
import com.example.gutapp.data.models.Order;

import java.util.Locale;

public class OrderDialog {

    private final AlertDialog dialog;
    private final TextView txtPrice, txtTotal;
    private final EditText editQty;
    private double currentPrice;
    private final OrderDialogListener listener;
    Order.OrderType type;
    private final TextView txtBalance;

    // Interface to send the final data back to the Activity
    public interface OrderDialogListener {
        void onConfirmOrder(int quantity, double price, Order.OrderType type);
    }

    public OrderDialog(AppCompatActivity activity, String symbol, double price, Order.OrderType type , OrderDialogListener listener) {
        this.currentPrice = price;
        this.listener = listener;
        this.type = type;

        View view = LayoutInflater.from(activity).inflate(R.layout.order_confirmation, null);

        TextView txtTitle = view.findViewById(R.id.confTitle);
        txtBalance = view.findViewById(R.id.confBalance);
        txtPrice = view.findViewById(R.id.confPrice);
        txtTotal = view.findViewById(R.id.confTotal); // Add a TextView for total in your XML
        editQty = view.findViewById(R.id.editQty);
        Button btnConfirm = view.findViewById(R.id.btnConfirm);

        String title = type == Order.OrderType.Long ? String.format("CONFIRM BUY: %s", symbol) : String.format("CONFIRM SELL: %s", symbol);
        txtTitle.setText(title);
        updatePriceDisplay(price);

        UserGlobals.getBalance().observe(activity, newBalance -> {
            txtBalance.setText(String.format(Locale.US, "$%.4f", newBalance));
        });

        // Watch for quantity changes to update Total Cost
        editQty.addTextChangedListener(new TextWatcher() {
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                updateTotal();
            }
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void afterTextChanged(Editable s) {}
        });

        dialog = new AlertDialog.Builder(activity).setView(view).create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }

        btnConfirm.setOnClickListener(v -> {
            try {
                int qty = Integer.parseInt(editQty.getText().toString());
                listener.onConfirmOrder(qty, currentPrice, type);
                dialog.dismiss();
            } catch (NumberFormatException e) {
                editQty.setError("Invalid quantity");
            }
        });
        updateTotal();
    }

    public void show() { dialog.show(); }

    public boolean isShowing() { return dialog.isShowing(); }

    // This is called by the Activity when a new price arrives via WebSocket
    public void updateLivePrice(double newPrice) {
        this.currentPrice = newPrice;
        updatePriceDisplay(newPrice);
        updateTotal();
    }

    private void updatePriceDisplay(double price) {
        txtPrice.setText(String.format(Locale.US, "%.16f", price));
    }

    private void updateTotal() {
        try {
            double qty = Double.parseDouble(editQty.getText().toString());
            double total = qty * currentPrice;
            txtTotal.setText(String.format(Locale.US, "Total: $%.2f", total));
        } catch (Exception e) {
            txtTotal.setText("Total: $0.00");
        }
    }
}