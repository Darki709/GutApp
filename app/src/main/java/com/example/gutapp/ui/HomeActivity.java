package com.example.gutapp.ui;

import android.content.Intent;
import android.database.Cursor;
import android.graphics.Color;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.gutapp.R;
import com.example.gutapp.data.SearchAdapter;
import com.example.gutapp.data.StockRow;
import com.example.gutapp.data.UserGlobals;
import com.example.gutapp.data.models.TickerInfo;
import com.example.gutapp.database.DB_Helper;
import com.example.gutapp.database.LastFetchCacheHelper;
import com.example.gutapp.session.DataType;
import com.example.gutapp.session.NetworkClient;
import com.example.gutapp.session.Requests.SearchTicker;


import java.util.ArrayList;

public class HomeActivity extends SessionActivity {
    public static final String HOME_LOG_TAG = "GutHome";

    //load global pointers
    LinearLayout stockContainer;
    ArrayList<StockRow> stockList = new ArrayList();

    private SearchAdapter searchAdapter;
    private RecyclerView searchDropdown;
    private EditText searchInput;

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

        searchInput = findViewById(R.id.search_input);
        searchDropdown = findViewById(R.id.suggestions_dropdown);
        searchAdapter = new SearchAdapter();
        searchDropdown.setLayoutManager(new LinearLayoutManager(this));
        searchDropdown.setAdapter(searchAdapter);
        searchAdapter.setOnItemClickListener(result -> {
            Intent intent = new Intent(this, ChartActivity.class);
            intent.putExtra("symbol", result.symbol);
            intent.putExtra("name", result.name);
            startActivity(intent);
        });

        //set the quick search
        searchInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                // Leave empty - required by Interface
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                String query = s.toString();
                if (query.length() >= 2) {
                    SearchTicker searchTicker = new SearchTicker(query, HomeActivity.this);
                    NetworkClient.getInstance(HomeActivity.this).getSessionManager().pushRequest(searchTicker);
                } else {
                    searchDropdown.setVisibility(View.GONE);
                }
            }

            @Override
            public void afterTextChanged(Editable s) {
                // Leave empty - required by Interface
            }
        });
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
        switch(msgType){
            case SEARCH_NO_RESULT:
                runOnUiThread(() -> {
                    searchDropdown.setVisibility(View.GONE);
                    Toast.makeText(this, "No results found", Toast.LENGTH_SHORT).show();
                });
                break;
            case SEARCH_RESULT:
                if(parsedData == null) return;
                runOnUiThread( () -> {
                searchAdapter.updateData((ArrayList<TickerInfo>) parsedData);
                searchDropdown.setVisibility(View.VISIBLE);
                });
                break;
            default:
                break;
        }
    }

    @Override
    public void onActionRequired(int actionType, Object data) {
        //currently not in use
    }
}
