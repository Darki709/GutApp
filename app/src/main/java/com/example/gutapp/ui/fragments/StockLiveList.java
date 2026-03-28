package com.example.gutapp.ui.fragments;


import android.app.sdksandbox.LoadSdkException;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Rect;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.gutapp.R;
import com.example.gutapp.data.StockRow;
import com.example.gutapp.data.models.TickerInfo;

import java.util.ArrayList;

public class StockLiveList extends Fragment {
    private ArrayList<StockRow> stockList = new ArrayList<>();
    private LinearLayout container;
    private ScrollView scrollView;
    private Button loadBtn;
    private LoadMoreCallback callback;
    ProgressBar loadProgress;

    public interface LoadMoreCallback {
        void onLoadMore();
    }

    public static StockLiveList newInstance(@Nullable ArrayList<TickerInfo> source) {
        StockLiveList fragment = new StockLiveList();
        Bundle args = new Bundle();
        args.putParcelableArrayList("source", source);
        fragment.setArguments(args);
        return fragment;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.stock_scroll_list, container, false);
        return view;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        this.container = view.findViewById(R.id.stockContainer);
        this.scrollView = view.findViewById(R.id.scrollView);
        this.loadBtn = view.findViewById(R.id.loadBtn);
        this.loadProgress = view.findViewById(R.id.loadProgress);
        loadBtn.setOnClickListener(v -> {
            callback.onLoadMore();
            loadBtn.setVisibility(View.GONE);
            loadProgress.setVisibility(View.VISIBLE);
        });
        if(callback == null){
            loadBtn.setVisibility(View.GONE);
        }
        container.removeAllViews();
        scrollView.getViewTreeObserver().addOnScrollChangedListener(() -> {
            updateVisibleTickerSubscriptions();
        });
        if (getArguments() != null){
            ArrayList<TickerInfo> source = getArguments().getParcelableArrayList("source");
            if (source != null) {
                loadStockList(source);
            }
        }
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        if (context instanceof LoadMoreCallback) {
            callback = (LoadMoreCallback) context;
        } else{
            callback = null;
        }
    }

    @Override
    public void onDetach() {
        super.onDetach();
        callback = null; // Clean up to avoid memory leaks
    }

    private void updateVisibleTickerSubscriptions() {
        Rect scrollBounds = new Rect();
        scrollView.getHitRect(scrollBounds); // Get the visible area of the ScrollView

        for (StockRow row : stockList) {
            View rowView = row.getRow();

            // Check if the row is at least partially visible
            if (rowView.getLocalVisibleRect(scrollBounds)) {
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


    public void loadStockList(ArrayList<TickerInfo> source) {
        for(TickerInfo ticker : source){
            container.addView(createStockRow(ticker.name, ticker.symbol));
        }
        scrollView.post(() -> {
            // Manually call your check function once the UI is ready
            updateVisibleTickerSubscriptions();
        });
    }

    private View createStockRow(String name, String symbol) {
        StockRow stockRow = new StockRow(symbol, name, requireActivity());
        stockList.add(stockRow);

        // Divider
        View divider = new View(requireActivity());
        divider.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1));
        divider.setBackgroundColor(Color.parseColor("#222222"));

        LinearLayout wrapper = new LinearLayout(requireActivity());
        wrapper.setOrientation(LinearLayout.VERTICAL);
        wrapper.addView(stockRow.getRow());
        wrapper.addView(divider);

        return wrapper;
    }

    public void enableBtn(){
        loadBtn.setVisibility(View.VISIBLE);
        loadProgress.setVisibility(View.GONE);
    }

    public void stopNothingFound(){
        loadProgress.setVisibility(View.GONE);
        loadBtn.setVisibility(View.GONE);
    }

    public boolean isEmpty(){
        return stockList.isEmpty();
    }


    @Override
    public void onResume() {
        super.onResume();
        //only when visible we want to update the prices
        for(StockRow stockRow : stockList){
            stockRow.loadPrice();
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        //we don't need live updates if the activity isn't visible
        for(StockRow stockRow : stockList){
            stockRow.discard();
        }
    }
}
