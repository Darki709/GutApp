package com.example.gutapp.ui.fragments;

import static android.app.Activity.RESULT_OK;

import android.content.Intent;
import android.os.Bundle;
import android.speech.RecognizerIntent;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
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
import com.example.gutapp.ui.ExploreActivity;
import com.example.gutapp.ui.HomeActivity;

import java.util.ArrayList;
import java.util.Locale;

public class SearchFragment extends Fragment implements SessionCallback {
    private EditText searchInput;
    private RecyclerView searchDropdown;
    private SearchAdapter searchAdapter;
    private ImageButton micButton;

    private ActivityResultLauncher<Intent> speechToTextLauncher;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_search, container, false);

        searchInput = view.findViewById(R.id.search_input);
        searchDropdown = view.findViewById(R.id.suggestions_dropdown);
        micButton = view.findViewById(R.id.micButton);
        searchAdapter = new SearchAdapter();
        searchDropdown.setLayoutManager(new LinearLayoutManager(requireContext()));
        searchDropdown.setAdapter(searchAdapter);
        searchAdapter.setOnItemClickListener(result -> {
            Intent intent = new Intent(requireContext(), ChartActivity.class);
            intent.putExtra("symbol", result.symbol);
            intent.putExtra("name", result.name);
            startActivity(intent);
        });

        initSpeechRecognizerLauncher();
        micButton.setOnClickListener(v -> startVoiceRecognition());


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

        searchInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                // Run your search method here
                String query = searchInput.getText().toString();
                if(query.length() >= 2) callSearch(query);
                else Toast.makeText(requireActivity(), "Search query too short", Toast.LENGTH_SHORT).show();
                return true;
            }
            return false;
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

    private void callSearch(String query) {
        Intent intent = new Intent(requireContext(), ExploreActivity.class);
        intent.putExtra("query", query);
        startActivity(intent);
    }

    @Override
    public void onActionRequired(int actionType, @Nullable Object data) {

    }

    private void initSpeechRecognizerLauncher() {
        speechToTextLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        ArrayList<String> matches = result.getData()
                                .getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);

                        if (matches != null && !matches.isEmpty()) {
                            String spokenText = matches.get(0); //most accurate word

                            //we change the searchInput text which automatically triggers the search
                            searchInput.setText(spokenText);
                        }
                    }
                }
        );
    }

    private void startVoiceRecognition() {
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.US.toString());
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "Say a stock name or ticker (e.g., Apple or AAPL)...");
        try {
            speechToTextLauncher.launch(intent);
        } catch (Exception e) {
            Toast.makeText(requireActivity(), "Your device does not support Speech to Text", Toast.LENGTH_SHORT).show();
        }
    }
}