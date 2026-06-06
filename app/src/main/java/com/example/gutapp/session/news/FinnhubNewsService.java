package com.example.gutapp.session.news;

import com.example.gutapp.data.news.NewsArticle;

import java.util.List;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Query;

/**
 * FinnhubNewsService — Retrofit interface for Finnhub's free news endpoints.
 *
 * Base URL: https://finnhub.io/api/v1/
 * Auth: the API token is passed as the {@code token} query parameter.
 *
 * Free tier: 60 requests/minute — plenty for an on-demand news screen.
 * Docs: https://finnhub.io/docs/api/market-news
 */
public interface FinnhubNewsService {

    /**
     * General market news.
     * @param category one of: general, forex, crypto, merger
     */
    @GET("news")
    Call<List<NewsArticle>> getMarketNews(@Query("category") String category,
                                          @Query("token") String token);

    /**
     * Company-specific news within a date window (YYYY-MM-DD).
     */
    @GET("company-news")
    Call<List<NewsArticle>> getCompanyNews(@Query("symbol") String symbol,
                                           @Query("from") String from,
                                           @Query("to") String to,
                                           @Query("token") String token);
}
