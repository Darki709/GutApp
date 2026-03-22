package com.example.gutapp.ui.fragments;

import android.database.Cursor;
import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.gutapp.R;
import com.example.gutapp.data.StockRow;
import com.example.gutapp.data.models.TickerInfo;
import com.example.gutapp.database.DB_Helper;
import com.example.gutapp.database.LastFetchCacheHelper;

import java.util.ArrayList;

public class StockLiveList extends Fragment {
    private ArrayList<StockRow> stockList = new ArrayList<>();
    private LinearLayout container;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.stock_scroll_list, container, false);
        this.container = view.findViewById(R.id.stockContainer);
        loadStockList();
        return view;
    }


    private void loadStockList() {
        Cursor cursor = (new LastFetchCacheHelper(DB_Helper.getInstance(requireActivity())).getStocks());
        container.removeAllViews();

        if (cursor.moveToFirst()) {
            do {
                String symbol = cursor.getString(cursor.getColumnIndexOrThrow("symbol"));
                String name = cursor.getString(cursor.getColumnIndexOrThrow("name"));
                container.addView(createStockRow(name, symbol));
            } while (cursor.moveToNext());
        }
        cursor.close();
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
