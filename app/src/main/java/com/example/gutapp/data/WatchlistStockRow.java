package com.example.gutapp.data;

import android.app.Activity;
import android.app.AlertDialog;
import android.widget.PopupMenu;
import android.widget.Toast;

import com.example.gutapp.session.DataType;
import com.example.gutapp.session.NetworkClient;
import com.example.gutapp.session.Requests.ModifyWatchlistItems;

public class WatchlistStockRow extends StockRow {
    private final String currentListName;

    public WatchlistStockRow(String symbol, String name, String listName, Activity activity) {
        super(symbol, name, activity);
        this.currentListName = listName;
        overrideLongClick();
    }

    private void overrideLongClick() {
        // We override the behavior set in the parent constructor
        getRow().setOnLongClickListener(v -> {
            PopupMenu popup = new PopupMenu(callerActivity, v);
            popup.getMenu().add("Remove from " + currentListName);
            popup.setOnMenuItemClickListener(item -> {
                showRemoveConfirmation();
                return true;
            });
            popup.show();
            return true;
        });
    }

    private void showRemoveConfirmation() {
        new AlertDialog.Builder(callerActivity, AlertDialog.THEME_HOLO_DARK)
                .setTitle("Remove Ticker")
                .setMessage("Are you sure you want to remove " + symbol + " from " + currentListName + "?")
                .setPositiveButton("Remove", (dialog, which) -> {
                    // Action 1 is REMOVE in our ModifyWatchlistItems implementation
                    ModifyWatchlistItems removeReq = new ModifyWatchlistItems(
                            ModifyWatchlistItems.Action.REMOVE,
                            currentListName,
                            symbol,
                            this
                    );
                    NetworkClient.getInstance(null).getSessionManager().pushRequest(removeReq);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    @Override
    public void onDataReceived(DataType msgType, Object parsedData) {
        super.onDataReceived(msgType, parsedData);
        if (msgType == DataType.WATCHLIST_OPERATION_RESULT) {
            byte status = (byte) parsedData;
            if (status == 0) {
                mainHandler.post(() -> {
                    Toast.makeText(callerActivity, symbol + " removed", Toast.LENGTH_SHORT).show();
                    // Optional: Hide the row immediately after removal
                    getRow().setVisibility(android.view.View.GONE);
                });
            }
        }
    }
}