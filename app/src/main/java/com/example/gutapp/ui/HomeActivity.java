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
import android.widget.ImageButton;
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
import com.example.gutapp.data.models.TickerInformation;
import com.example.gutapp.database.DB_Helper;
import com.example.gutapp.database.LastFetchCacheHelper;
import com.example.gutapp.session.DataType;
import com.example.gutapp.session.NetworkClient;
import com.example.gutapp.session.Requests.SearchTicker;
import com.example.gutapp.session.Requests.TickerInfoRequest;
import com.example.gutapp.ui.fragments.SearchFragment;
import com.example.gutapp.ui.fragments.StockLiveList;


import java.util.ArrayList;

public class HomeActivity extends SessionActivity {
    public static final String HOME_LOG_TAG = "GutHome";

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

        StockLiveList stockLiveListFragment = StockLiveList.newInstance(loadStockList());
        //initialize stock list fragment
        if(savedInstanceState == null){
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.stock_list_container, stockLiveListFragment)
                    .commit();
        }

        //ready the home page for presentation
        setUserTitle();

        //initialize search fragment
        if(savedInstanceState == null){
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.search_container, new SearchFragment())
                    .commit();
        }
    }

    private ArrayList<TickerInfo> loadStockList() {
        Cursor cursor = (new LastFetchCacheHelper(DB_Helper.getInstance(this)).getStocks());
        ArrayList<TickerInfo> tickerList = new ArrayList<>();

        if (cursor.moveToFirst()) {
            do {
                String symbol = cursor.getString(cursor.getColumnIndexOrThrow("symbol"));
                String name = cursor.getString(cursor.getColumnIndexOrThrow("name"));
                tickerList.add(new TickerInfo(name, symbol));
            } while (cursor.moveToNext());
        }
        cursor.close();
        return tickerList;
    }

    private void setUserTitle(){
        TextView userTitle = findViewById(R.id.textViewUserTitle);
        if(UserGlobals.LOGGED_IN)
            userTitle.setText("Hello " + UserGlobals.USER_NAME + "!");
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
