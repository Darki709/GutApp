package com.example.gutapp.session.news;

import androidx.annotation.NonNull;

import com.example.gutapp.BuildConfig;
import com.example.gutapp.data.news.NewsArticle;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import retrofit2.Call;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

/**
 * NewsRepository — thin async wrapper around {@link FinnhubNewsService}.
 *
 * Keeps Retrofit setup and the API key out of the UI layer. All calls run off
 * the UI thread (Retrofit's enqueue) and deliver results back on the main
 * thread via {@link Callback}. Failures never throw to the caller — they come
 * back through {@link Callback#onError(String)} so the screen can degrade
 * gracefully instead of crashing.
 */
public class NewsRepository {

    private static final String BASE_URL = "https://finnhub.io/api/v1/";

    /** Result callback — exactly one of these fires, on the main thread. */
    public interface Callback {
        void onNews(@NonNull List<NewsArticle> articles);
        void onError(@NonNull String message);
    }

    private static volatile NewsRepository instance;
    private final FinnhubNewsService service;

    private NewsRepository() {
        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(BASE_URL)
                .addConverterFactory(GsonConverterFactory.create())
                .build();
        service = retrofit.create(FinnhubNewsService.class);
    }

    public static NewsRepository getInstance() {
        if (instance == null) {
            synchronized (NewsRepository.class) {
                if (instance == null) instance = new NewsRepository();
            }
        }
        return instance;
    }

    private String token() { return BuildConfig.FINHUB_API_KEY; }

    private boolean hasKey() {
        String k = token();
        return k != null && !k.trim().isEmpty();
    }

    /**
     * Latest general market news.
     * @param category one of: general, forex, crypto, merger
     */
    public void fetchMarketNews(String category, Callback cb) {
        if (!hasKey()) { cb.onError("No news API key configured."); return; }
        service.getMarketNews(category, token()).enqueue(wrap(cb));
    }

    /** Latest news for a single symbol, covering the last 7 days. */
    public void fetchCompanyNews(String symbol, Callback cb) {
        if (!hasKey()) { cb.onError("No news API key configured."); return; }
        long now = System.currentTimeMillis();
        SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        String to   = fmt.format(new Date(now));
        String from = fmt.format(new Date(now - 7L * 24 * 60 * 60 * 1000));
        service.getCompanyNews(symbol, from, to, token()).enqueue(wrap(cb));
    }

    /** Shared Retrofit→Callback adapter with uniform error handling. */
    private retrofit2.Callback<List<NewsArticle>> wrap(Callback cb) {
        return new retrofit2.Callback<List<NewsArticle>>() {
            @Override
            public void onResponse(@NonNull Call<List<NewsArticle>> call,
                                   @NonNull Response<List<NewsArticle>> response) {
                if (response.isSuccessful()) {
                    List<NewsArticle> body = response.body();
                    cb.onNews(body != null ? body : new ArrayList<>());
                } else if (response.code() == 429) {
                    cb.onError("Rate limit reached — try again in a moment.");
                } else if (response.code() == 401 || response.code() == 403) {
                    cb.onError("News API key rejected (" + response.code() + ").");
                } else {
                    cb.onError("News request failed (" + response.code() + ").");
                }
            }

            @Override
            public void onFailure(@NonNull Call<List<NewsArticle>> call, @NonNull Throwable t) {
                cb.onError("Network error: " + t.getMessage());
            }
        };
    }
}
