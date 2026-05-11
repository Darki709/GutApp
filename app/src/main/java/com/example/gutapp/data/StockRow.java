package com.example.gutapp.data;


import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;

import com.example.gutapp.data.models.Candle;
import com.example.gutapp.data.models.PriceChunk;
import com.example.gutapp.data.models.TickerInfo;
import com.example.gutapp.database.DB_Helper;
import com.example.gutapp.database.LastFetchCacheHelper;
import com.example.gutapp.database.StockDataHelper;
import com.example.gutapp.session.DataType;
import com.example.gutapp.session.NetworkClient;
import com.example.gutapp.session.Requests.RequestTickerData;
import com.example.gutapp.session.SessionCallback;
import com.example.gutapp.ui.ChartActivity;

import android.app.AlertDialog;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.PopupMenu;
import android.widget.Spinner;
import android.widget.Toast;

import com.example.gutapp.session.Requests.ManageWatchlist;
import com.example.gutapp.session.Requests.ModifyWatchlistItems;
import com.example.gutapp.session.Requests.GetWatchlists;
import com.example.gutapp.session.Responses.WatchlistResponses;

import java.util.ArrayList;
import java.util.List;

import java.time.Instant;

public class StockRow implements SessionCallback {
    LinearLayout stockRow;
    double lastPrice;
    String symbol;
    String name;
    Activity callerActivity;
    int reqId = -1; //keeps the request id of te streaming request used to update the prices. -1 means row inactive
    Handler mainHandler = new Handler(Looper.getMainLooper()); //to change prices live from the background

    TextView priceView;

    public StockRow(String symbol, String name, Activity callerActivity){
        this.symbol= symbol;
        this.name = name;
        this.callerActivity = callerActivity;
        setUpRow();
    }

    public StockRow(TickerInfo ticker, Activity callerActivity){
        this.symbol= ticker.symbol;
        this.name = ticker.name;
        this.callerActivity = callerActivity;
        setUpRow();
    }

    //sends the live update request to the api
    public void loadPrice(){
        //long lastFetchTime = (new LastFetchCacheHelper(DB_Helper.getInstance(null))).getLastFetchTime(symbol, StockDataHelper.Timeframe.DAILY); depracted for perfomance issues
        RequestTickerData requestPrice = new RequestTickerData(symbol, StockDataHelper.Timeframe.ONE_MIN, 0
                , 0, false, true, this);
        reqId = requestPrice.getReqId();
        NetworkClient.getInstance(null).getSessionManager().pushRequest(requestPrice);
    }

    private void setUpRow(){
        //set up the row view itself with all the styling, effects and logic
        this.stockRow = new LinearLayout(callerActivity);
        stockRow.setOrientation(LinearLayout.HORIZONTAL);
        stockRow.setPadding(20, 24, 20, 24);
        stockRow.setGravity(Gravity.CENTER_VERTICAL);
        stockRow.setClickable(true);
        TypedValue outValue = new TypedValue();
        callerActivity.getTheme().resolveAttribute(android.R.attr.selectableItemBackground, outValue, true);
        stockRow.setBackgroundResource(outValue.resourceId);
        LinearLayout textGroup = new LinearLayout(callerActivity);
        textGroup.setOrientation(LinearLayout.VERTICAL);
        textGroup.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

        //name
        TextView nameView = new TextView(callerActivity);
        nameView.setText(name);
        nameView.setTextColor(Color.WHITE);
        nameView.setTextSize(16);

        //symbol
        TextView symbolView = new TextView(callerActivity);
        symbolView.setText(symbol);
        symbolView.setTextColor(Color.GRAY);
        symbolView.setTextSize(13);

        textGroup.addView(nameView);
        textGroup.addView(symbolView);

        //price
        this.priceView = new TextView(callerActivity);
        //default price is 0.00 or what ever is on the machine until server responds with live prices
        StockDataHelper stockDataHelper = new StockDataHelper(DB_Helper.getInstance(null));
        Candle cachedPrice = stockDataHelper.getLatestPrice(symbol);
        if(cachedPrice== null) {
            priceView.setText("Price not available");
            priceView.setTextColor(Color.parseColor("#FF4444"));
            this.lastPrice = 0;
        }
        else {
            String errMsg = Instant.now().getEpochSecond() - cachedPrice.timestamp > 3600 ? " (outdated)" : "";
            priceView.setText(String.format("%.4f" + errMsg, cachedPrice.close));
            this.lastPrice = cachedPrice.close;
            Candle.Direction direction = cachedPrice.getDirection();
            boolean isUp = direction == Candle.Direction.UP;
            priceView.setTextColor(isUp ? Color.parseColor("#00FF88") : Color.parseColor("#FF4444"));
        }
        priceView.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);

        priceView.setTextSize(17);
        stockRow.addView(textGroup);
        stockRow.addView(priceView);

        //click logic
        stockRow.setOnClickListener(v -> {
            Intent intent = new Intent(callerActivity, ChartActivity.class);
            intent.putExtra("symbol", symbol);
            intent.putExtra("name", name);
            callerActivity.startActivity(intent);
        });

        stockRow.setOnLongClickListener(v -> {
            showSavePrompt(v);
            return true; // consumes the click
        });
    }

    private void showSavePrompt(View v) {
        PopupMenu popup = new PopupMenu(callerActivity, v);
        popup.getMenu().add("Save to Watchlist");
        popup.setOnMenuItemClickListener(item -> {
            if (item.getTitle().equals("Save to Watchlist")) {
                openWatchlistSelectionDialog();
            }
            return true;
        });
        popup.show();
    }

    private void openWatchlistSelectionDialog() {
        // First, we fetch the current lists from the server
        GetWatchlists fetchTask = new GetWatchlists(new SessionCallback() {
            @Override
            public void onDataReceived(DataType msgType, Object parsedData) {
                if (msgType == DataType.WATCHLISTS_LOADED) {
                    List<WatchlistResponses.WatchlistInfo> lists = (List<WatchlistResponses.WatchlistInfo>) parsedData;
                    mainHandler.post(() -> buildListSelectionDialog(lists));
                }
            }
            @Override
            public void onActionRequired(int actionType, @Nullable Object data) {}
        });
        NetworkClient.getInstance(null).getSessionManager().pushRequest(fetchTask);
    }

    private void buildListSelectionDialog(List<WatchlistResponses.WatchlistInfo> existingLists) {
        AlertDialog.Builder builder = new AlertDialog.Builder(callerActivity, AlertDialog.THEME_HOLO_DARK);
        builder.setTitle("Add " + symbol + " to List");

        LinearLayout layout = new LinearLayout(callerActivity);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(40, 20, 40, 20);

        // Spinner for existing lists
        final Spinner spinner = new Spinner(callerActivity);
        List<String> listNames = new ArrayList<>();
        listNames.add("-- Create New List --");
        for (WatchlistResponses.WatchlistInfo info : existingLists) listNames.add(info.name);

        ArrayAdapter<String> adapter = new ArrayAdapter<>(callerActivity, android.R.layout.simple_spinner_item, listNames);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        layout.addView(spinner);

        // Edit text for new list name (hidden unless "Create New" is selected)
        final EditText input = new EditText(callerActivity);
        input.setHint("Enter new list name");
        input.setTextColor(Color.WHITE);
        input.setVisibility(View.GONE);
        layout.addView(input);

        spinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                input.setVisibility(position == 0 ? View.VISIBLE : View.GONE);
            }
            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {}
        });

        builder.setView(layout);

        builder.setPositiveButton("Save", (dialog, which) -> {
            String targetList;
            if (spinner.getSelectedItemPosition() == 0) {
                targetList = input.getText().toString();
                if (targetList.isEmpty()) return;
                // 1. Create the list first
                ManageWatchlist createReq = new ManageWatchlist(ManageWatchlist.Action.CREATE, targetList, null, this);
                NetworkClient.getInstance(null).getSessionManager().pushRequest(createReq);
            } else {
                targetList = (String) spinner.getSelectedItem();
            }

            // 2. Add the ticker to the list
            ModifyWatchlistItems addReq = new ModifyWatchlistItems(ModifyWatchlistItems.Action.ADD, targetList, symbol, this);
            NetworkClient.getInstance(null).getSessionManager().pushRequest(addReq);
            Toast.makeText(callerActivity, "Adding to " + targetList, Toast.LENGTH_SHORT).show();
        });

        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.cancel());
        builder.show();
    }

    public LinearLayout getRow() {
        return stockRow;
    }

    public void discard(){
        NetworkClient.getInstance(null).getSessionManager().discardRequest(reqId);
        reqId = -1;
    }

    private synchronized void updatePrice(PriceChunk chunk){
        long data_ts = chunk.chunk.get(chunk.chunk.size() - 1).timestamp;
        String errMsg = Instant.now().getEpochSecond() - data_ts > 3600 ? " (outdated)" : "";
        double price = chunk.chunk.get(chunk.chunk.size() - 1).close;
        double oldPrice = this.lastPrice;
        mainHandler.post(() -> {
            priceView.setText(String.format("%.4f", price) + errMsg);
            if(price != oldPrice) {
                priceView.setTextColor(price > oldPrice ?  Color.parseColor("#00FF88") : Color.parseColor("#FF4444"));
            }
        });
        this.lastPrice = price;
    }

    public boolean isActive(){
        return reqId != -1;
    }

    @Override
    public void onDataReceived(DataType msgType, Object parsedData) {
        switch (msgType) {
            case TICKER_ERROR:
                mainHandler.post(() -> {
                    priceView.setText(String.format("%.4f", (this.lastPrice)) + " Price not available");
                    priceView.setTextColor(Color.parseColor("#FF4444"));
                });
                break;

            case TICKER_STREAM:
            case TICKER_SNAPSHOT:
                PriceChunk chunk = (PriceChunk) parsedData;
                // Validate the chunk belongs to this specific row request
                if (chunk != null && chunk.reqId == reqId && !chunk.chunk.isEmpty()) {
                    // For snapshots, only update if it's the final piece of data
                    if (msgType == DataType.TICKER_STREAM || chunk.isLast) {
                        updatePrice(chunk);
                    }
                }
                break;

            case WATCHLIST_OPERATION_RESULT:
                if (parsedData instanceof Byte) {
                    byte status = (byte) parsedData;
                    mainHandler.post(() -> {
                        if (status == 0) {
                            Toast.makeText(callerActivity, "Success!", Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(callerActivity, "Operation failed: Code " + WatchlistResponses.translate(status), Toast.LENGTH_SHORT).show();
                        }
                    });
                }
                break;

            case TICKER_REQUEST_DONE:
            default:
                break;
        }
    }

    @Override
    public void onActionRequired(int actionType, @Nullable Object data) {

    }
}
