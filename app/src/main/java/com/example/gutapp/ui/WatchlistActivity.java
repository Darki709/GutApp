package com.example.gutapp.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.*;
import androidx.activity.EdgeToEdge;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.gutapp.R;
import com.example.gutapp.session.DataType;
import com.example.gutapp.session.NetworkClient;
import com.example.gutapp.session.Requests.GetWatchlists;
import com.example.gutapp.session.Requests.ManageWatchlist;
import com.example.gutapp.session.Responses.WatchlistResponses;
import com.example.gutapp.ui.fragments.StockLiveList;

import java.util.ArrayList;
import java.util.List;

public class WatchlistActivity extends SessionActivity implements StockLiveList.LoadMoreCallback {

    private Spinner spinner;
    private StockLiveList stock_list_fragment;
    private LinearLayout loading_layout;
    private View stock_list_container;
    private TextView nothingFound;
    private View management_container; // Added to control visibility

    private List<WatchlistResponses.WatchlistInfo> currentLists = new ArrayList<>();
    private String selectedListName = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_watchlist);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        initUI();
        refreshLists();
    }

    private void initUI() {
        spinner = findViewById(R.id.watchlistSpinner);
        loading_layout = findViewById(R.id.loading_layout);
        stock_list_container = findViewById(R.id.stock_list_container);
        nothingFound = findViewById(R.id.nothingFound);
        management_container = findViewById(R.id.management_container);

        findViewById(R.id.buttonHome).setOnClickListener(v -> {
            startActivity(new Intent(this, HomeActivity.class));
        });

        findViewById(R.id.btnDelete).setOnClickListener(v -> confirmDelete());
        findViewById(R.id.btnEdit).setOnClickListener(v -> showRenameDialog());

        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
                selectedListName = currentLists.get(pos).name;
                resetAndLoadFragment();
            }
            @Override public void onNothingSelected(AdapterView<?> p) {}
        });
    }

    private void resetAndLoadFragment() {
        if (selectedListName == null) return;

        stock_list_fragment = StockLiveList.newInstance(null, selectedListName);
        getSupportFragmentManager().beginTransaction()
                .replace(R.id.stock_list_container, stock_list_fragment)
                .commit();

        stock_list_container.setVisibility(View.GONE);
        loading_layout.setVisibility(View.VISIBLE);
    }

    private void refreshLists() {
        // Show loading while we fetch list of names
        management_container.setVisibility(View.GONE);
        loading_layout.setVisibility(View.VISIBLE);
        nothingFound.setVisibility(View.GONE);

        NetworkClient.getInstance(this).getSessionManager().pushRequest(new GetWatchlists(this));
    }

    @Override
    public void onDataReceived(DataType msgType, Object parsedData) {
        switch (msgType) {
            case WATCHLISTS_LOADED:
                handleListsLoaded((List<WatchlistResponses.WatchlistInfo>) parsedData);
                break;

            case WATCHLIST_OPERATION_RESULT:
                byte status = (byte) parsedData;
                runOnUiThread(() -> {
                    Toast.makeText(this, WatchlistResponses.translate(status), Toast.LENGTH_SHORT).show();
                    if (status == 0) refreshLists();
                });
                break;
        }
    }

    private void handleListsLoaded(List<WatchlistResponses.WatchlistInfo> lists) {
        runOnUiThread(() -> {
            currentLists = lists;
            loading_layout.setVisibility(View.GONE);

            if (lists.isEmpty()) {
                selectedListName = null;
                management_container.setVisibility(View.GONE);
                stock_list_container.setVisibility(View.GONE);
                nothingFound.setVisibility(View.VISIBLE);
                nothingFound.setText("No Watchlists Found\nCreate one from the dashboard!");
                return;
            }

            // Lists found: show management bar and hide empty state
            management_container.setVisibility(View.VISIBLE);
            nothingFound.setVisibility(View.GONE);

            List<String> names = new ArrayList<>();
            for (WatchlistResponses.WatchlistInfo info : lists) names.add(info.name);

            ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, names);
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            spinner.setAdapter(adapter);

            // If we have lists but none selected, spinner will trigger onItemSelected(0)
        });
    }

    @Override
    public void loadingEnd() {
        runOnUiThread(() -> {
            stock_list_container.setVisibility(View.VISIBLE);
            loading_layout.setVisibility(View.GONE);
        });
    }

    @Override public void onLoadMore() {}
    @Override protected void networkReconnect() { stock_list_fragment.refreshVisibleRows(); }

    @Override
    protected void networkDisconnect() {
        stock_list_fragment.stop();
    }

    private void confirmDelete() {
        if (selectedListName == null) return;
        new android.app.AlertDialog.Builder(this, android.app.AlertDialog.THEME_HOLO_DARK)
                .setTitle("Delete List")
                .setMessage("Delete '" + selectedListName + "'? This cannot be undone.")
                .setPositiveButton("Delete", (d, w) -> {
                    ManageWatchlist req = new ManageWatchlist(ManageWatchlist.Action.DELETE, selectedListName, null, this);
                    NetworkClient.getInstance(null).getSessionManager().pushRequest(req);
                })
                .setNegativeButton("Cancel", null).show();
    }

    private void showRenameDialog() {
        if (selectedListName == null) return;
        EditText input = new EditText(this);
        input.setText(selectedListName);
        input.setPadding(40, 40, 40, 40);

        new android.app.AlertDialog.Builder(this, android.app.AlertDialog.THEME_HOLO_DARK)
                .setTitle("Rename List")
                .setView(input)
                .setPositiveButton("Rename", (d, w) -> {
                    String newName = input.getText().toString().trim();
                    if (!newName.isEmpty()) {
                        ManageWatchlist req = new ManageWatchlist(ManageWatchlist.Action.RENAME, selectedListName, newName, this);
                        NetworkClient.getInstance(null).getSessionManager().pushRequest(req);
                    }
                })
                .setNegativeButton("Cancel", null).show();
    }
}