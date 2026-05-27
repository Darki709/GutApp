package com.example.gutapp.ui.fragments;

import static com.example.gutapp.ui.HomeActivity.HOME_LOG_TAG;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Rect;
import android.health.connect.datatypes.units.Length;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.gutapp.R;
import com.example.gutapp.data.StockRow;
import com.example.gutapp.data.WatchlistStockRow;
import com.example.gutapp.data.models.TickerInfo;
import com.example.gutapp.session.DataType;
import com.example.gutapp.session.NetworkClient;
import com.example.gutapp.session.Requests.GetWatchlistContent;
import com.example.gutapp.session.SessionCallback;

import java.util.ArrayList;

public class StockLiveList extends Fragment implements SessionCallback {
    private ArrayList<StockRow> stockList = new ArrayList<>();
    private LinearLayout container;
    private ScrollView scrollView;
    private Button loadBtn;
    private ProgressBar loadProgress;
    private LoadMoreCallback activityCallback;

    private int currentOffset = 0;
    private final int PAGE_SIZE = 50;

    @Nullable
    private String currentWatchlistName = null;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public interface LoadMoreCallback {
        void onLoadMore();
        void loadingEnd();
    }

    public static StockLiveList newInstance(@Nullable ArrayList<TickerInfo> source) {
        return newInstance(source, null);
    }

    public static StockLiveList newInstance(@Nullable ArrayList<TickerInfo> source, @Nullable String listName) {
        StockLiveList fragment = new StockLiveList();
        Bundle args = new Bundle();
        args.putParcelableArrayList("source", source);
        args.putString("list_name", listName);
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
        this.loadBtn = view.findViewById(R.id.loadBtn);
        this.loadProgress = view.findViewById(R.id.loadProgress);

        container.removeAllViews();

        ArrayList<TickerInfo> source = null;
        if (getArguments() != null) {
            this.currentWatchlistName = getArguments().getString("list_name");
            source = getArguments().getParcelableArrayList("source");

            if (source != null) {
                loadStockList(source);
            } else if (currentWatchlistName != null) {
                requestNextPage(); // Initial internal load
            }
        }


        loadBtn.setOnClickListener(v -> {
            loadBtn.setVisibility(View.GONE);
            loadProgress.setVisibility(View.VISIBLE);

            if (currentWatchlistName != null) {
                requestNextPage(); // Internal Pagination
            } else if (activityCallback != null) {
                activityCallback.onLoadMore(); // External (ExploreActivity)
            }
        });

        scrollView.getViewTreeObserver().addOnScrollChangedListener(this::updateVisibleTickerSubscriptions);
        scrollView.getViewTreeObserver().addOnGlobalLayoutListener(this::updateVisibleTickerSubscriptions);
    }

    /**
     * Internal logic for Watchlist Pagination
     */
    private void requestNextPage() {
        if (currentWatchlistName == null) return;

        GetWatchlistContent task = new GetWatchlistContent(
                currentWatchlistName,
                currentOffset,
                PAGE_SIZE,
                this // The Fragment is now the callback
        );

        NetworkClient.getInstance(requireActivity()).getSessionManager().pushRequest(task);
    }

    @Override
    public void onDataReceived(DataType msgType, Object parsedData) {
        if (msgType == DataType.WATCHLIST_CONTENT_LOADED) {
            ArrayList<String> newTickers = (ArrayList<String>) parsedData;
            mainHandler.post(() -> {
                activityCallback.loadingEnd();
                if (newTickers.isEmpty()) {
                    stopNothingFound();
                } else {
                    addTickersToContainer(newTickers);
                    currentOffset += newTickers.size();
                    if(newTickers.size() == PAGE_SIZE){
                        enableBtn();
                    }
                    else{
                        stopNothingFound();
                    }
                }
            });
        }
    }

    @Override
    public void onActionRequired(int actionType, @Nullable Object data) {
        // Handle specific actions if needed
    }

    public void loadStockList(ArrayList<TickerInfo> source) {
        for (TickerInfo ticker : source) {
            container.addView(createStockRow(ticker.name, ticker.symbol));
        }
        refreshVisibleRows();
        if(source.size() < PAGE_SIZE) stopNothingFound();
    }

    private void addTickersToContainer(ArrayList<String> tickers) {
        for (String symbol : tickers) {
            container.addView(createStockRow("", symbol));
        }
        refreshVisibleRows();
    }

    private View createStockRow(String name, String symbol) {
        StockRow stockRow;
        if (currentWatchlistName != null) {
            stockRow = new WatchlistStockRow(symbol, name, currentWatchlistName, requireActivity());
        } else {
            stockRow = new StockRow(symbol, name, requireActivity());
        }

        stockList.add(stockRow);

        View divider = new View(requireActivity());
        divider.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1));
        divider.setBackgroundColor(Color.parseColor("#222222"));

        LinearLayout wrapper = new LinearLayout(requireActivity());
        wrapper.setOrientation(LinearLayout.VERTICAL);
        wrapper.addView(stockRow.getRow());
        wrapper.addView(divider);

        return wrapper;
    }

    private void updateVisibleTickerSubscriptions() {

        for (StockRow row : stockList) {

            View rowView = row.getRow();

            Rect rect = new Rect();

            boolean visible = rowView.getLocalVisibleRect(rect);

            if (visible) {
                if (!row.isActive()) {
                    row.loadPrice();
                }
            } else {
                if (row.isActive()) {
                    row.discard();
                }
            }
        }
    }

    public void refreshVisibleRows() {
        if (scrollView != null) {
            scrollView.post(this::updateVisibleTickerSubscriptions);
        }
    }

    public void enableBtn() {
        loadBtn.setVisibility(View.VISIBLE);
        loadProgress.setVisibility(View.GONE);
    }

    public void stopNothingFound() {
        loadProgress.setVisibility(View.GONE);
        loadBtn.setVisibility(View.GONE);
    }

    public boolean isEmpty() {
        return stockList.isEmpty();
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        if (context instanceof LoadMoreCallback) {
            activityCallback = (LoadMoreCallback) context;
        }
    }

    @Override
    public void onDetach() {
        super.onDetach();
        activityCallback = null;
    }

    @Override
    public void onResume() {
        super.onResume();
        refreshVisibleRows();
    }

    @Override
    public void onPause() {
        super.onPause();
        stop();
    }

    public void stop(){
        for (StockRow row : stockList) row.discard();
    }
}