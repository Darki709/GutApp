package com.example.gutapp.ui;

import android.database.Cursor;
import android.graphics.Color;
import android.os.Bundle;
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
import com.example.gutapp.data.StockRow;
import com.example.gutapp.data.UserGlobals;
import com.example.gutapp.database.DB_Helper;
import com.example.gutapp.database.LastFetchCacheHelper;
import com.example.gutapp.session.DataType;
import com.example.gutapp.session.NetworkClient;
import com.example.gutapp.session.SessionCallback;

import java.util.ArrayList;

public class HomeActivity extends SessionActivity {
    public static final String HOME_LOG_TAG = "GutHome";

    //load global pointers
    LinearLayout stockContainer;
    ArrayList<StockRow> stockList = new ArrayList();

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

        stockContainer = findViewById(R.id.stockContainer);

        //ready the home page for presentation
        setUserTitle();
        loadStockList();
    }

    @Override
    protected void onResume() {
        super.onResume();
        //only when visible we want to update the prices
        for(StockRow stockRow : stockList){
            stockRow.loadPrice();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        //we don't need live updates if the activity isn't visible
        for(StockRow stockRow : stockList){
            stockRow.discard();
        }
    }

    private void setUserTitle(){
        TextView userTitle = findViewById(R.id.textViewUserTitle);
        if(UserGlobals.LOGGED_IN)
            userTitle.setText("Hello " + UserGlobals.USER_NAME + "!");
    }

    private void loadStockList() {
        Cursor cursor = (new LastFetchCacheHelper(DB_Helper.getInstance(this)).getStocks());
        LinearLayout container = findViewById(R.id.stockContainer);
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
        StockRow stockRow = new StockRow(symbol, name, this);
        stockList.add(stockRow);

        // Divider
        View divider = new View(this);
        divider.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1));
        divider.setBackgroundColor(Color.parseColor("#222222"));

        LinearLayout wrapper = new LinearLayout(this);
        wrapper.setOrientation(LinearLayout.VERTICAL);
        wrapper.addView(stockRow.getRow());
        wrapper.addView(divider);

        return wrapper;
    }

    @Override
    public void onDataReceived(DataType msgType, Object parsedData) {
        //currently not in use
    }

    @Override
    public void onActionRequired(int actionType, Object data) {
        //currently not in use
    }
}
