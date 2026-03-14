package com.example.gutapp.data.models;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.example.gutapp.ui.ChartActivity;

//simpler way to save a row of a ticker without prices
public class TickerRow{
    LinearLayout tickerRow;
    String name;
    String symbol;
    
    public TickerRow(String name, String symbol, Activity callerActivity){
        this.name = name;
        this.symbol = symbol;

        tickerRow = new LinearLayout(callerActivity);
        tickerRow.setOrientation(LinearLayout.HORIZONTAL);
        tickerRow.setPadding(20, 24, 20, 24);
        tickerRow.setGravity(Gravity.CENTER_VERTICAL);
        tickerRow.setClickable(true);
        TypedValue outValue = new TypedValue();
        callerActivity.getTheme().resolveAttribute(android.R.attr.selectableItemBackground, outValue, true);
        tickerRow.setBackgroundResource(outValue.resourceId);

        //name
        TextView nameView = new TextView(callerActivity);
        nameView.setText(name);
        nameView.setTextColor(Color.WHITE);
        nameView.setTextSize(16);

        //symbol
        this.symbol = symbol;
        TextView symbolView = new TextView(callerActivity);
        symbolView.setText(symbol);
        symbolView.setTextColor(Color.GRAY);
        symbolView.setTextSize(13);

        tickerRow.addView(nameView);
        tickerRow.addView(symbolView);

        //click logic
        tickerRow.setOnClickListener(v -> {
            Intent intent = new Intent(callerActivity, ChartActivity.class);
            intent.putExtra("symbol", symbol);
            intent.putExtra("name", name);
            callerActivity.startActivity(intent);
        });
    }
}
