package com.example.gutapp.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentContainerView;

import com.example.gutapp.R;
import com.example.gutapp.data.models.TickerInfo;
import com.example.gutapp.session.DataType;
import com.example.gutapp.session.NetworkClient;
import com.example.gutapp.session.Requests.SearchTicker;
import com.example.gutapp.ui.fragments.SearchFragment;
import com.example.gutapp.ui.fragments.StockLiveList;

import java.util.ArrayList;

public class ExploreActivity extends SessionActivity implements StockLiveList.LoadMoreCallback{
    public static final String EXPLORE_LOG_TAG = "GutExplore";
    StockLiveList stock_list_fragment;
    int lastTickerId; //id of the last ticker loaded, used for pagination
    @Nullable
    String query; //query that opened the pages
    LinearLayout loading_layout;
    FragmentContainerView stock_list_container;
    TextView nothingFound;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_explore);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
        query = getIntent().getStringExtra("query");


        //initialize search fragment
        if(savedInstanceState == null){
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.search_container, new SearchFragment())
                    .commit();
        }

        ImageButton buttonHome = findViewById(R.id.buttonHome);
        buttonHome.setOnClickListener(v -> {
            Intent intent = new Intent(this, HomeActivity.class);
            startActivity(intent);
        });

        stock_list_fragment = StockLiveList.newInstance(null);
        //initialize stock list fragment
        getSupportFragmentManager().beginTransaction()
                .replace(R.id.stock_list_container, stock_list_fragment)
                .commit();

        loading_layout = findViewById(R.id.loading_layout);
        stock_list_container = findViewById(R.id.stock_list_container);
        nothingFound = findViewById(R.id.nothingFound);

        if(query != null){
            SearchTicker searchTicker = new SearchTicker(query, 50, 0, this);
            NetworkClient.getInstance(this).getSessionManager().pushRequest(searchTicker);
        }
    }

    private void loadMore() {
        SearchTicker searchTicker = new SearchTicker(query, 50, lastTickerId, this);
        NetworkClient.getInstance(this).getSessionManager().pushRequest(searchTicker);
    }


    @Override
    public void onDataReceived(DataType msgType, Object parsedData) {
            switch(msgType){
                case SEARCH_NO_RESULT:
                    runOnUiThread( () -> {
                    Toast.makeText(this, "No results found", Toast.LENGTH_SHORT).show();
                    loading_layout.setVisibility(View.GONE);
                    stock_list_fragment.stopNothingFound();
                    if(stock_list_fragment.isEmpty()){
                        stock_list_container.setVisibility(View.GONE);
                        loading_layout.setVisibility(View.GONE);
                        nothingFound.setText("No results found for: " + query);
                        nothingFound.setVisibility(View.VISIBLE);
                    }
                    });
                    break;
                case SEARCH_RESULT:
                    if(parsedData == null) return;
                    //initialize stock list fragment
                    runOnUiThread( () -> {
                         ArrayList<TickerInfo> tickers = (ArrayList<TickerInfo>) parsedData;
                         stock_list_fragment.loadStockList(tickers);
                         lastTickerId = tickers.get(tickers.size() - 1).tickerId;
                         stock_list_fragment.enableBtn();
                         stock_list_container.setVisibility(View.VISIBLE);
                         loading_layout.setVisibility(View.GONE);
                    });
                    break;
                default:
                    break;
            }
    }

    @Override
    public void onLoadMore() {
        loadMore();
    }

    @Override
    public void loadingEnd() {
        //not needed here
    }

    @Override
    protected void networkReconnect() {
        stock_list_fragment.refreshVisibleRows();
    }

    @Override
    protected void networkDisconnect() {
        stock_list_fragment.stop();
    }
}