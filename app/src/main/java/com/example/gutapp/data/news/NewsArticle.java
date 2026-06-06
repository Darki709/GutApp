package com.example.gutapp.data.news;

/**
 * NewsArticle — one financial-news item.
 *
 * Field names match the Finnhub JSON shape exactly so Gson maps them directly
 * (no @SerializedName needed). Returned by both /news and /company-news.
 *
 * Example item:
 * {
 *   "category":"company news", "datetime":1596589501, "headline":"...",
 *   "id":4640208, "image":"https://...", "related":"AAPL",
 *   "source":"CNBC", "summary":"...", "url":"https://..."
 * }
 */
public class NewsArticle {
    public String category;
    public long   datetime;   // epoch SECONDS
    public String headline;
    public long   id;
    public String image;
    public String related;    // related ticker symbol(s)
    public String source;
    public String summary;
    public String url;

    public boolean hasImage() { return image != null && !image.trim().isEmpty(); }
}
