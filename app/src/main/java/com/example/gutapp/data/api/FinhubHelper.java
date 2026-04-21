package com.example.gutapp.data.api;

import java.util.List;

import retrofit2.Call;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;
import retrofit2.http.GET;
import retrofit2.http.Query;

public class FinhubHelper {
    public static class NewsArticle {
        public String headline;
        public String summary;
        public String url;
        public String image; // URL to the thumbnail
        public String source;
        public long datetime; // Unix timestamp
        public String category;
    }

    public interface FinnhubService {
        // category can be "general", "crypto", "forex", or "merger"
        @GET("api/v1/news")
        Call<List<NewsArticle>> getMarketNews(
                @Query("category") String category,
                @Query("token") String apiKey
        );
    }

    public static class RetrofitClient {
        private static Retrofit retrofit = null;

        public static Retrofit getClient() {
            if (retrofit == null) {
                retrofit = new Retrofit.Builder()
                        .baseUrl("https://finnhub.io/")
                        .addConverterFactory(GsonConverterFactory.create())
                        .build();
            }
            return retrofit;
        }
    }


}
