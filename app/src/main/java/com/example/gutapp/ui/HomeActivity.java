package com.example.gutapp.ui;

import android.content.Intent;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.gutapp.R;
import com.example.gutapp.data.UserGlobals;
import com.example.gutapp.database.DB_Helper;
import com.example.gutapp.database.StockDataHelper;
import com.example.gutapp.database.LastFetchCacheHelper;
import com.example.gutapp.session.NetworkClient;
import com.example.gutapp.session.SessionCallback;

import java.util.Locale;

public class HomeActivity extends AppCompatActivity implements SessionCallback {

    //load global pointers
    LinearLayout stockContainer;
    DB_Helper db_helper;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_home);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        db_helper = DB_Helper.getInstance(this);//make sure db is ready

        stockContainer = findViewById(R.id.stockContainer);

        //ready the home page for presentation
        setUserTitle();
        loadStockList();

        //set the session caller to this activity
        NetworkClient.getInstance(this).getSessionManager().setCallback(this);
    }

    private void setUserTitle(){
        TextView userTitle = findViewById(R.id.textViewUserTitle);
        if(UserGlobals.LOGGED_IN)
            userTitle.setText("Hello " + UserGlobals.USER_NAME + "!");
    }

    private void loadStockList() {
        Cursor cursor = (new LastFetchCacheHelper(db_helper)).getStocks();
        LinearLayout container = findViewById(R.id.stockContainer);
        container.removeAllViews();

        if (cursor.moveToFirst()) {
            do {
                String symbol = cursor.getString(cursor.getColumnIndexOrThrow("symbol"));
                String name = cursor.getString(cursor.getColumnIndexOrThrow("name"));
                float close = (float)(new StockDataHelper(db_helper)).getLatestPrice(symbol);
                boolean isUp = close > 0;
                close = Math.abs(close);

                container.addView(createStockRow(name, symbol, close, isUp));
            } while (cursor.moveToNext());
        }
        cursor.close();
    }

    private View createStockRow(String name, String symbol, float price, boolean isUp) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(20, 24, 20, 24);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setClickable(true);

        // Add a modern ripple effect for clicks
        TypedValue outValue = new TypedValue();
        getTheme().resolveAttribute(android.R.attr.selectableItemBackground, outValue, true);
        row.setBackgroundResource(outValue.resourceId);

        // Stock name + symbol
        LinearLayout textGroup = new LinearLayout(this);
        textGroup.setOrientation(LinearLayout.VERTICAL);
        textGroup.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

        TextView nameView = new TextView(this);
        nameView.setText(name);
        nameView.setTextColor(Color.WHITE);
        nameView.setTextSize(16);

        TextView symbolView = new TextView(this);
        symbolView.setText(symbol);
        symbolView.setTextColor(Color.GRAY);
        symbolView.setTextSize(13);

        textGroup.addView(nameView);
        textGroup.addView(symbolView);

        // Price
        TextView priceView = new TextView(this);
        priceView.setText(String.format(Locale.US, "%.2f", price));
        priceView.setTextSize(17);
        priceView.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        priceView.setTextColor(isUp ? Color.parseColor("#00FF88") : Color.parseColor("#FF4444"));

        row.addView(textGroup);
        row.addView(priceView);

        // 🔹 Handle click → go to ChartActivity
        row.setOnClickListener(v -> {
            Intent intent = new Intent(this, ChartActivity.class);
            intent.putExtra("symbol", symbol);
            intent.putExtra("name", name);
            startActivity(intent);
        });

        // Divider
        View divider = new View(this);
        divider.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1));
        divider.setBackgroundColor(Color.parseColor("#222222"));

        LinearLayout wrapper = new LinearLayout(this);
        wrapper.setOrientation(LinearLayout.VERTICAL);
        wrapper.addView(row);
        wrapper.addView(divider);

        return wrapper;
    }

    @Override
    public void onDataReceived(int msgType, Object parsedData) {
        //currently not in use
    }

    @Override
    public void onActionRequired(int actionType) {
        //currently not in use
    }
}
