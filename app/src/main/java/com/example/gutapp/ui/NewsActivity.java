package com.example.gutapp.ui;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.bumptech.glide.Glide;
import com.example.gutapp.R;
import com.example.gutapp.data.news.NewsArticle;
import com.example.gutapp.session.DataType;
import com.example.gutapp.session.news.NewsRepository;

import java.util.List;
import java.util.Locale;

/**
 * NewsActivity — latest stock / market news from Finnhub.
 *
 * Two modes:
 *  · Market mode (default)  — general market news with category chips
 *                             (General / Crypto / M&amp;A).
 *  · Symbol mode            — launched with a "symbol" extra; shows the last
 *                             7 days of company news for that ticker and hides
 *                             the category chips.
 *
 * Networking goes through {@link NewsRepository} (Retrofit, off the UI thread);
 * results are rendered as tappable cards that open the source article in a
 * browser. All failures land in the empty/error state — never a crash.
 */
public class NewsActivity extends SessionActivity {

    /** Optional intent extra: a ticker symbol to scope news to one company. */
    public static final String EXTRA_SYMBOL = "symbol";

    private LinearLayout listContainer;
    private ProgressBar  loading;
    private View         emptyState;
    private TextView     emptyText;

    private TextView chipGeneral, chipCrypto, chipMerger;

    @Nullable private String symbol;          // non-null → symbol mode
    private String category = "general";      // active market category

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_news);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets sb = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(sb.left, sb.top, sb.right, sb.bottom);
            return insets;
        });

        symbol = getIntent().getStringExtra(EXTRA_SYMBOL);

        listContainer = findViewById(R.id.newsListContainer);
        loading       = findViewById(R.id.newsLoading);
        emptyState    = findViewById(R.id.newsEmptyState);
        emptyText     = findViewById(R.id.newsEmptyText);
        chipGeneral   = findViewById(R.id.chipGeneral);
        chipCrypto    = findViewById(R.id.chipCrypto);
        chipMerger    = findViewById(R.id.chipMerger);

        findViewById(R.id.btnNewsBack).setOnClickListener(v -> finish());
        findViewById(R.id.btnNewsRefresh).setOnClickListener(v -> load());

        TextView title = findViewById(R.id.newsTitle);

        if (symbol != null && !symbol.trim().isEmpty()) {
            // Symbol mode — hide category chips, show company news.
            title.setText("📰  " + symbol + " News");
            findViewById(R.id.newsCategoryBar).setVisibility(View.GONE);
        } else {
            // Market mode — wire up category chips.
            chipGeneral.setOnClickListener(v -> selectCategory("general", chipGeneral));
            chipCrypto.setOnClickListener(v -> selectCategory("crypto", chipCrypto));
            chipMerger.setOnClickListener(v -> selectCategory("merger", chipMerger));
            setActiveChip(chipGeneral);
        }

        load();
    }

    // ── SessionActivity plumbing (news is independent of the socket session) ──
    @Override public void onDataReceived(DataType t, Object d) { /* not used here */ }
    @Override protected void networkReconnect() { }
    @Override protected void networkDisconnect() { }

    // ── Category selection ──────────────────────────────────────────────
    private void selectCategory(String cat, TextView chip) {
        if (cat.equals(category)) return;
        category = cat;
        setActiveChip(chip);
        load();
    }

    private void setActiveChip(TextView active) {
        for (TextView chip : new TextView[]{chipGeneral, chipCrypto, chipMerger}) {
            if (chip == null) continue;
            boolean on = chip == active;
            chip.setBackgroundResource(on ? R.drawable.chart_btn_active : R.drawable.chart_btn_inactive);
            chip.setTextColor(Color.parseColor(on ? "#ECEFF1" : "#9E9E9E"));
        }
    }

    // ── Load ────────────────────────────────────────────────────────────
    private void load() {
        showLoading();
        NewsRepository.Callback cb = new NewsRepository.Callback() {
            @Override public void onNews(@NonNull List<NewsArticle> articles) {
                if (isFinishing() || isDestroyed()) return;
                render(articles);
            }
            @Override public void onError(@NonNull String message) {
                if (isFinishing() || isDestroyed()) return;
                showEmpty(message);
            }
        };
        if (symbol != null && !symbol.trim().isEmpty())
            NewsRepository.getInstance().fetchCompanyNews(symbol, cb);
        else
            NewsRepository.getInstance().fetchMarketNews(category, cb);
    }

    // ── Render ──────────────────────────────────────────────────────────
    private void render(List<NewsArticle> articles) {
        loading.setVisibility(View.GONE);
        listContainer.removeAllViews();

        if (articles.isEmpty()) {
            showEmpty(symbol != null ? "No recent news for " + symbol : "No news right now");
            return;
        }
        emptyState.setVisibility(View.GONE);
        listContainer.setVisibility(View.VISIBLE);

        for (NewsArticle a : articles) {
            if (a == null || a.headline == null || a.headline.trim().isEmpty()) continue;
            listContainer.addView(buildCard(a));
        }
    }

    private View buildCard(NewsArticle a) {
        View card = LayoutInflater.from(this).inflate(R.layout.item_news, listContainer, false);

        ((TextView) card.findViewById(R.id.newsHeadline)).setText(a.headline);

        TextView summary = card.findViewById(R.id.newsSummary);
        if (a.summary != null && !a.summary.trim().isEmpty()) {
            summary.setText(a.summary.trim());
            summary.setVisibility(View.VISIBLE);
        } else {
            summary.setVisibility(View.GONE);
        }

        ((TextView) card.findViewById(R.id.newsMeta)).setText(buildMeta(a));

        ImageView img = card.findViewById(R.id.newsImage);
        if (a.hasImage()) {
            img.setVisibility(View.VISIBLE);
            Glide.with(this).load(a.image).centerCrop().into(img);
        } else {
            // Clear any recycled bitmap and collapse the thumbnail slot.
            Glide.with(this).clear(img);
            img.setVisibility(View.GONE);
        }

        card.setOnClickListener(v -> openArticle(a));
        return card;
    }

    /** "Source · 2h ago · AAPL" — pieces are omitted when missing. */
    private String buildMeta(NewsArticle a) {
        StringBuilder sb = new StringBuilder();
        if (a.source != null && !a.source.trim().isEmpty()) sb.append(a.source.trim());
        if (a.datetime > 0) {
            if (sb.length() > 0) sb.append("  ·  ");
            sb.append(DateUtils.getRelativeTimeSpanString(
                    a.datetime * 1000L, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS));
        }
        // Only show "related" in market mode (in symbol mode it's redundant).
        if (symbol == null && a.related != null && !a.related.trim().isEmpty()) {
            if (sb.length() > 0) sb.append("  ·  ");
            sb.append(a.related.trim());
        }
        return sb.toString();
    }

    private void openArticle(NewsArticle a) {
        if (a.url == null || a.url.trim().isEmpty()) {
            Toast.makeText(this, "No link for this article", Toast.LENGTH_SHORT).show();
            return;
        }
        // Open inside the app (WebView reader + on-demand Gemini analysis) rather
        // than handing the URL off to an external browser.
        Intent intent = new Intent(this, ArticleActivity.class);
        intent.putExtra(ArticleActivity.EXTRA_URL,      a.url.trim());
        intent.putExtra(ArticleActivity.EXTRA_HEADLINE, a.headline);
        intent.putExtra(ArticleActivity.EXTRA_SUMMARY,  a.summary);
        intent.putExtra(ArticleActivity.EXTRA_RELATED,  a.related);
        intent.putExtra(ArticleActivity.EXTRA_SOURCE,   a.source);
        startActivity(intent);
    }

    // ── State helpers ───────────────────────────────────────────────────
    private void showLoading() {
        emptyState.setVisibility(View.GONE);
        listContainer.setVisibility(View.GONE);
        listContainer.removeAllViews();
        loading.setVisibility(View.VISIBLE);
    }

    private void showEmpty(String message) {
        loading.setVisibility(View.GONE);
        listContainer.setVisibility(View.GONE);
        if (!TextUtils.isEmpty(message)) emptyText.setText(message);
        emptyState.setVisibility(View.VISIBLE);
    }
}
