package com.example.gutapp.ui;

import android.annotation.SuppressLint;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.OnBackPressedCallback;
import androidx.annotation.Nullable;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.gutapp.R;
import com.example.gutapp.data.gemini.GeminiHelper;
import com.example.gutapp.session.DataType;
import com.google.android.material.bottomsheet.BottomSheetDialog;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Locale;

/**
 * ArticleActivity — reads a news article inside the app (no browser hop) and
 * offers an on-demand Gemini "who does this move?" sentiment analysis.
 * Launched by {@link NewsActivity} with the article's url / headline / summary /
 * related-tickers as extras. The page renders in an embedded {@link WebView};
 * the AI button asks Gemini which tradable symbols the article is likely to
 * affect and how, then lists them as tappable rows that jump to the chart.
 * Reliability: every failure (bad url, load error, AI error) lands in a visible
 * state with an "open in browser" escape hatch — never a crash.
 */
public class ArticleActivity extends SessionActivity {

    public static final String EXTRA_URL      = "article_url";
    public static final String EXTRA_HEADLINE = "article_headline";
    public static final String EXTRA_SUMMARY  = "article_summary";
    public static final String EXTRA_RELATED  = "article_related";
    public static final String EXTRA_SOURCE   = "article_source";

    private static final String TAG = "ArticleActivity";

    private WebView     webView;
    private ProgressBar progress;
    private View        errorState;
    private TextView    errorText;

    @Nullable private String url;
    @Nullable private String headline;
    @Nullable private String summary;
    @Nullable private String related;

    // Cache the raw AI JSON so re-opening the sheet is instant.
    @Nullable private String cachedAnalysis;
    // Live references to the open analysis sheet (null when closed).
    @Nullable private BottomSheetDialog analysisDialog;
    @Nullable private View              analysisView;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_article);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets sb = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(sb.left, sb.top, sb.right, sb.bottom);
            return insets;
        });

        url      = getIntent().getStringExtra(EXTRA_URL);
        headline = getIntent().getStringExtra(EXTRA_HEADLINE);
        summary  = getIntent().getStringExtra(EXTRA_SUMMARY);
        related  = getIntent().getStringExtra(EXTRA_RELATED);
        String source = getIntent().getStringExtra(EXTRA_SOURCE);

        webView    = findViewById(R.id.articleWebView);
        progress   = findViewById(R.id.articleProgress);
        errorState = findViewById(R.id.articleErrorState);
        errorText  = findViewById(R.id.articleErrorText);

        TextView title = findViewById(R.id.articleTitle);
        title.setText(!TextUtils.isEmpty(source) ? source
                : (!TextUtils.isEmpty(headline) ? headline : "Article"));

        findViewById(R.id.btnArticleBack).setOnClickListener(v -> handleBack());
        findViewById(R.id.btnArticleBrowser).setOnClickListener(v -> openInBrowser());
        findViewById(R.id.btnArticleAi).setOnClickListener(v -> showAnalysisSheet());
        findViewById(R.id.articleErrorOpenBrowser).setOnClickListener(v -> openInBrowser());

        // WebView "back" should walk the page history before leaving the screen.
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override public void handleOnBackPressed() { handleBack(); }
        });

        setupWebView();

        if (url == null || url.trim().isEmpty()) {
            showError("No link for this article");
        } else {
            webView.loadUrl(url.trim());
        }
    }

    // ── WebView ───────────────────────────────────────────────────────────
    @SuppressLint("SetJavaScriptEnabled")
    private void setupWebView() {
        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        s.setBuiltInZoomControls(true);
        s.setDisplayZoomControls(false);
        s.setMediaPlaybackRequiresUserGesture(true);

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri u = request.getUrl();
                String scheme = u != null ? u.getScheme() : null;
                // Keep normal web navigation inside the WebView.
                if (scheme == null || scheme.equals("http") || scheme.equals("https")) {
                    return false;
                }
                // Hand off non-web schemes (mailto:, tel:, intent:, market:…) to the system.
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, u));
                } catch (ActivityNotFoundException ignored) {
                    // Nothing can handle it — swallow rather than crash the page.
                }
                return true;
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request,
                                        WebResourceError error) {
                // Only surface failures of the main document, not sub-resources.
                if (request.isForMainFrame()) {
                    showError("Couldn't load this article");
                }
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                if (newProgress >= 100) {
                    progress.setVisibility(View.GONE);
                } else {
                    progress.setVisibility(View.VISIBLE);
                    progress.setProgress(newProgress);
                }
            }
        });
    }

    private void handleBack() {
        if (webView != null && webView.canGoBack()) {
            webView.goBack();
        } else {
            finish();
        }
    }

    private void showError(String message) {
        progress.setVisibility(View.GONE);
        webView.setVisibility(View.GONE);
        if (!TextUtils.isEmpty(message)) errorText.setText(message);
        errorState.setVisibility(View.VISIBLE);
    }

    private void openInBrowser() {
        if (url == null || url.trim().isEmpty()) {
            Toast.makeText(this, "No link for this article", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url.trim())));
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, "No app can open this link", Toast.LENGTH_SHORT).show();
        }
    }

    // ── AI article analysis ─────────────────────────────────────────────
    private void showAnalysisSheet() {
        analysisView = LayoutInflater.from(this).inflate(R.layout.dialog_article_analysis, null);
        analysisDialog = new BottomSheetDialog(this);
        analysisDialog.setContentView(analysisView);
        analysisDialog.setOnDismissListener(d -> { analysisDialog = null; analysisView = null; });

        analysisView.findViewById(R.id.btnArticleAiClose).setOnClickListener(v -> {
            if (analysisDialog != null) analysisDialog.dismiss();
        });

        if (cachedAnalysis != null) {
            renderAnalysis(cachedAnalysis);
        } else {
            showAnalysisLoading();
            new GeminiHelper().getArticleAnalysis(headline, summary, related,
                    new GeminiHelper.AnalysisCallback() {
                        @Override public void onSuccess(String result) {
                            cachedAnalysis = result;
                            runOnUiThread(() -> renderAnalysis(result));
                        }
                        @Override public void onError(String error) {
                            Log.e(TAG, "Article analysis failed: " + error);
                            runOnUiThread(ArticleActivity.this::renderAnalysisError);
                        }
                    });
        }

        analysisDialog.show();
    }

    private void showAnalysisLoading() {
        if (analysisView == null) return;
        analysisView.findViewById(R.id.aiLoadingRow).setVisibility(View.VISIBLE);
        analysisView.findViewById(R.id.aiResultContainer).setVisibility(View.GONE);
    }

    private void renderAnalysisError() {
        if (analysisView == null) return;   // sheet was closed
        analysisView.findViewById(R.id.aiLoadingRow).setVisibility(View.GONE);
        View result = analysisView.findViewById(R.id.aiResultContainer);
        result.setVisibility(View.VISIBLE);
        ((TextView) analysisView.findViewById(R.id.aiOverallSentiment)).setText("—");
        ((TextView) analysisView.findViewById(R.id.aiSummary))
                .setText("AI analysis is unavailable right now. Please try again.");
        analysisView.findViewById(R.id.aiSymbolsContainer).setVisibility(View.GONE);
        analysisView.findViewById(R.id.aiNoSymbols).setVisibility(View.GONE);
    }

    private void renderAnalysis(String rawJson) {
        if (analysisView == null) return;   // sheet was closed before result arrived
        analysisView.findViewById(R.id.aiLoadingRow).setVisibility(View.GONE);
        analysisView.findViewById(R.id.aiResultContainer).setVisibility(View.VISIBLE);

        TextView overall = analysisView.findViewById(R.id.aiOverallSentiment);
        TextView summaryTv = analysisView.findViewById(R.id.aiSummary);
        LinearLayout container = analysisView.findViewById(R.id.aiSymbolsContainer);
        TextView noSymbols = analysisView.findViewById(R.id.aiNoSymbols);
        container.removeAllViews();

        try {
            JSONObject j = new JSONObject(rawJson);

            String overallSentiment = j.optString("overall_sentiment", "Neutral");
            overall.setText(overallSentiment);
            overall.setTextColor(sentimentTextColor(overallSentiment));

            summaryTv.setText(j.optString("summary", ""));

            JSONArray arr = j.optJSONArray("affected_symbols");
            if (arr == null || arr.length() == 0) {
                container.setVisibility(View.GONE);
                noSymbols.setVisibility(View.VISIBLE);
                return;
            }
            container.setVisibility(View.VISIBLE);
            noSymbols.setVisibility(View.GONE);

            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.optJSONObject(i);
                if (o == null) continue;
                container.addView(buildSymbolRow(container, o));
            }
        } catch (JSONException ex) {
            Log.e(TAG, "AI parse", ex);
            summaryTv.setText("Error parsing analysis.");
            container.setVisibility(View.GONE);
            noSymbols.setVisibility(View.GONE);
        }
    }

    private View buildSymbolRow(LinearLayout parent, JSONObject o) {
        View row = LayoutInflater.from(this)
                .inflate(R.layout.item_affected_symbol, parent, false);

        final String symbol  = o.optString("symbol", "").trim();
        final String company = o.optString("company", "").trim();
        String sentiment     = o.optString("sentiment", "Neutral").trim();
        String impact        = o.optString("impact", "").trim();
        int confidence       = o.optInt("confidence", -1);

        ((TextView) row.findViewById(R.id.affSymbol)).setText(symbol.isEmpty() ? "—" : symbol);

        TextView companyTv = row.findViewById(R.id.affCompany);
        if (company.isEmpty()) companyTv.setVisibility(View.GONE);
        else companyTv.setText(company);

        TextView sentimentTv = row.findViewById(R.id.affSentiment);
        sentimentTv.setText(sentiment);
        GradientDrawable badge = new GradientDrawable();
        badge.setCornerRadius(dp(10));
        badge.setColor(sentimentBadgeColor(sentiment));
        sentimentTv.setBackground(badge);

        TextView impactTv = row.findViewById(R.id.affImpact);
        if (impact.isEmpty()) impactTv.setVisibility(View.GONE);
        else impactTv.setText(impact);

        TextView confTv = row.findViewById(R.id.affConfidence);
        if (confidence >= 0) confTv.setText(String.format(Locale.US, "Confidence %d%%", confidence));
        else confTv.setVisibility(View.INVISIBLE);

        if (!symbol.isEmpty()) {
            row.setOnClickListener(v -> openChart(symbol, company));
        } else {
            row.setClickable(false);
        }
        return row;
    }

    private void openChart(String symbol, String name) {
        if (analysisDialog != null) analysisDialog.dismiss();
        Intent intent = new Intent(this, ChartActivity.class);
        intent.putExtra("symbol", symbol);
        intent.putExtra("name", name == null ? "" : name);
        startActivity(intent);
    }

    /** Bold-label color for the OVERALL line (green / red / white). */
    private static int sentimentTextColor(String s) {
        String t = s == null ? "" : s.trim().toLowerCase(Locale.US);
        if (t.startsWith("bull")) return Color.parseColor("#4CAF50");
        if (t.startsWith("bear")) return Color.parseColor("#F44336");
        return Color.WHITE;
    }

    /** Solid badge fill for the per-symbol chip. */
    private static int sentimentBadgeColor(String s) {
        String t = s == null ? "" : s.trim().toLowerCase(Locale.US);
        if (t.startsWith("bull")) return Color.parseColor("#2E7D32");
        if (t.startsWith("bear")) return Color.parseColor("#C62828");
        return Color.parseColor("#455A64");
    }

    private float dp(float v) {
        return v * getResources().getDisplayMetrics().density;
    }

    // ── WebView lifecycle ────────────────────────────────────────────────
    @Override protected void onPause()  { super.onPause();  if (webView != null) webView.onPause(); }
    @Override protected void onResume() { super.onResume(); if (webView != null) webView.onResume(); }

    @Override
    protected void onDestroy() {
        if (webView != null) {
            webView.stopLoading();
            webView.setWebChromeClient(null);
            webView.destroy();
            webView = null;
        }
        super.onDestroy();
    }

    // ── SessionActivity plumbing (article view is independent of the socket) ──
    @Override public void onDataReceived(DataType t, Object d) { /* not used here */ }
    @Override protected void networkReconnect() { }
    @Override protected void networkDisconnect() { }
}
