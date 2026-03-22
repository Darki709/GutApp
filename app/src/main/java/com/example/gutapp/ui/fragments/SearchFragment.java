package com.example.gutapp.ui.fragments;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.gutapp.R;
import com.example.gutapp.data.SearchAdapter;
import com.example.gutapp.data.models.TickerInfo;
import com.example.gutapp.session.DataType;
import com.example.gutapp.session.NetworkClient;
import com.example.gutapp.session.Requests.SearchTicker;
import com.example.gutapp.session.SessionCallback;
import com.example.gutapp.ui.ChartActivity;
import com.example.gutapp.ui.HomeActivity;

import java.util.ArrayList;

public class SearchFragment extends Fragment implements SessionCallback {
    private EditText searchInput;
    private RecyclerView searchDropdown;
    private SearchAdapter searchAdapter;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_search, container, false);

        searchInput = view.findViewById(R.id.search_input);
        searchDropdown = view.findViewById(R.id.suggestions_dropdown);
        searchAdapter = new SearchAdapter();
        searchDropdown.setLayoutManager(new LinearLayoutManager(requireContext()));
        searchDropdown.setAdapter(searchAdapter);
        searchAdapter.setOnItemClickListener(result -> {
            Intent intent = new Intent(requireContext(), ChartActivity.class);
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
                    SearchTicker searchTicker = new SearchTicker(query, SearchFragment.this);
                    NetworkClient.getInstance(requireContext()).getSessionManager().pushRequest(searchTicker);
                } else {
                    searchDropdown.setVisibility(View.GONE);
                }
            }

            @Override
            public void afterTextChanged(Editable s) {
                // Leave empty - required by Interface
            }
        });
        return view;
    }

    @Override
    public void onDataReceived(DataType msgType, Object parsedData) {
        switch(msgType){
            case SEARCH_NO_RESULT:
                requireActivity().runOnUiThread(() -> {
                    searchDropdown.setVisibility(View.GONE);
                    Toast.makeText(requireActivity(), "No results found", Toast.LENGTH_SHORT).show();
                });
                break;
            case SEARCH_RESULT:
                if(parsedData == null) return;
                requireActivity().runOnUiThread( () -> {
                    searchAdapter.updateData((ArrayList<TickerInfo>) parsedData);
                    searchDropdown.setVisibility(View.VISIBLE);
                });
                break;
            default:
                break;
        }
    }

    @Override
    public void onActionRequired(int actionType, @Nullable Object data) {

    }
}
