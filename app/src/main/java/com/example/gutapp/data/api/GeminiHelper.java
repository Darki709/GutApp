package com.example.gutapp.data.api;

import static com.example.gutapp.ui.ChartActivity.CHART_LOG_TAG;

import android.util.Log;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.firebase.ai.FirebaseAI;
import com.google.firebase.ai.GenerativeModel;
import com.google.firebase.ai.java.GenerativeModelFutures;
import com.google.firebase.ai.type.Content;
import com.google.firebase.ai.type.GenerateContentResponse;
import com.google.firebase.ai.type.GenerationConfig;
import com.google.firebase.ai.type.GenerativeBackend;
import com.google.firebase.ai.type.Schema;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class GeminiHelper {
    private final GenerativeModelFutures model;
    private final Executor backgroundExecutor = Executors.newSingleThreadExecutor();

    static final String requestFormat =
            "You are a professional financial analyst for GutApp. " +
                    "Search the internet for the most recent news regarding the ticker (it might be a stock, a crypto, a future or forex find any information you can): %s .\n\n" +

                    "TASK:\n" +
                    "1. Identify the current market sentiment.\n" +
                    "2. Find the single biggest 'Market Driver' (catalyst) from today's news.\n" +
                    "3. Provide a brief, professional outlook.\n\n" +
                    "4. Provide and historical overview and description of the company and their products (information so the user will know what company/asset he is dealing with)" +

                    "OUTPUT STRUCTURE IN JSON (Follow this exactly):\n" +
                    "SENTIMENT: [One word: Bullish, Bearish, or Neutral]\n" +
                    "SCORE: [A number from 0 to 100]\n" +
                    "MARKET DRIVER: [One sentence about the main news catalyst]\n" +
                    "SUMMARY: [A 2-3 sentence professional analysis]\n\n" +
                    "HISTORY/DESCRIPTION: a paragraph about the company's history and it's current practices" +
                    "(combine the market driver with the summary in json in the same header)" +
                    "(don't put headers like \"SCORE:\" or \"MARKET DRIVER:\" and such in the text itself, the json headers are used to recognize each part of the response)" +

                    "EXAMPLE OF WANTED RESPONSE:\n" +
                    "SENTIMENT: Bullish\n" +
                    "SCORE: 85/100\n" +
                    "MARKET DRIVER: NVIDIA announced a new line of AI chips that exceed performance expectations.\n" +
                    "SUMMARY: Demand for data centers remains at an all-time high. Investors are reacting positively to the expanded margins and the lack of immediate competition in the high-end GPU space.\n\n" +
                    "HISTORY/DESCRIPTION: Nvidia is a leading manufacturer of computer chips, mainly graphics cards for ai, professionals and gamers..." +

                    "Now, analyze %s and provide the response in the same structure.";

    public GeminiHelper() {
        // 1. Define the JSON Schema for the output
        Schema jsonSchema = Schema.obj(
                Map.of(
                        "rating_word", Schema.str(),           // e.g., "Bullish"
                        "score_out_of_hundred", Schema.numInt(),  // 1-5 score
                        "sentiment_analysis", Schema.str(),    // Based on news
                        "company_history", Schema.str()        // Brief overview
                ),
                Collections.emptyList() // No optional properties; all are required
        );

        // 2. Setup Generation Config with the Schema
        GenerationConfig config = new GenerationConfig.Builder()
                .setTemperature(0.2f) // Lower temperature for more factual financial data
                .setResponseMimeType("application/json")
                .setResponseSchema(jsonSchema)
                .build();

        // 3. System Instruction for a permanent "Persona"
        Content systemInstruction = new Content.Builder()
                .addText("You are a professional financial analyst with 20 years of experience. Always return data in the requested JSON format. Remember you work for GutApp so make sure your work is gut ;)")
                .build();

        FirebaseAI ai = FirebaseAI.getInstance(GenerativeBackend.googleAI());

        GenerativeModel gm = ai.generativeModel(
                "gemini-2.5-flash",
                config,
                null,
                null,
                null,
                systemInstruction);

        this.model = GenerativeModelFutures.from(gm);
    }

    public void getAiAnalysis(String ticker, final AnalysisCallback callback) {
        String promptText = String.format(
               requestFormat, ticker, ticker);

        Content content = new Content.Builder()
                .addText(promptText)
                .build();

        ListenableFuture<GenerateContentResponse> response = this.model.generateContent(content);

        Futures.addCallback(response, new FutureCallback<GenerateContentResponse>() {
            @Override
            public void onSuccess(GenerateContentResponse result) {
                // result.getText() will now be a raw JSON string
                callback.onSuccess(result.getText());
            }

            @Override
            public void onFailure(Throwable t) {
                Log.e(CHART_LOG_TAG, "Error getting ai analysis: " + t.getMessage());
                callback.onError(t.getMessage());
            }
        }, backgroundExecutor);
    }

    public interface AnalysisCallback {
        void onSuccess(String result);
        void onError(String error);
    }
}