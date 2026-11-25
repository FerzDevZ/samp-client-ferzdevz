package com.ferzdevzgta.game.ui;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.webkit.JavascriptInterface;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.LinearLayout;

import androidx.appcompat.app.AppCompatActivity;

import com.ferzdevzgta.game.BuildConfig;
import com.ferzdevzgta.game.R;

/**
 * Offline guide, rendered by the system WebView from an asset in the APK.
 *
 * <p>This exists because the client has three UI layers and none of them can hold formatted help
 * text: the screens are Java, the HUD is ImGui, and TextDraw belongs to the server. A WebView adds
 * zero bytes to the APK because Android ships it, so the guide can look like a document.
 *
 * <p>It is deliberately small in authority: JavaScript is on for the asset, file access from file
 * URLs is off, storage is off, and any link that is not the asset is handed to the browser. The
 * only bridge methods are the three below.
 */
public class WebPanelActivity extends AppCompatActivity {

    private static final String PAGE = "file:///android_asset/help/help.html";

    private WebView web;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Ui.paintWindow(this);
        setContentView(buildUi());
        Ui.applyInsets(this, findViewById(android.R.id.content));
    }

    private LinearLayout buildUi() {
        LinearLayout root = Ui.column(this);
        root.addView(Ui.eyebrow(this, getString(R.string.app_name)));
        Ui.gap(this, root, 6);
        root.addView(Ui.title(this, getString(R.string.guide_title)));
        Ui.gap(this, root, 4);
        root.addView(Ui.subtitle(this, getString(R.string.guide_subtitle)));

        Ui.gap(this, root, 14);
        web = new WebView(this);
        configure(web);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f);
        root.addView(web, params);

        Ui.gap(this, root, 12);
        Button close = Ui.button(this, getString(R.string.guide_close), Ui.SECONDARY);
        close.setOnClickListener(v -> finish());
        root.addView(close, Ui.fill());
        return root;
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void configure(WebView view) {
        view.setBackgroundColor(Ui.BG);
        WebSettings settings = view.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setAllowFileAccess(true);
        // The page is local. Nothing on the network may read it through a file URL.
        settings.setAllowFileAccessFromFileURLs(false);
        settings.setAllowUniversalAccessFromFileURLs(false);
        settings.setDomStorageEnabled(false);
        settings.setSupportZoom(false);
        settings.setTextZoom(100);
        view.addJavascriptInterface(new Bridge(), "Panel");
        view.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest request) {
                Uri uri = request.getUrl();
                if (uri != null && "file".equals(uri.getScheme())) {
                    return false;
                }
                if (uri != null) {
                    Ui.openUrl(WebPanelActivity.this, uri.toString());
                }
                return true;
            }
        });
        view.loadUrl(PAGE);
    }

    /** The whole surface the page may touch. No file access, no navigation, no data. */
    private final class Bridge {

        @JavascriptInterface
        public String locale() {
            return LanguageStore.current(WebPanelActivity.this);
        }

        @JavascriptInterface
        public String version() {
            return BuildConfig.VERSION_NAME;
        }

        @JavascriptInterface
        public void openDiagnostics() {
            runOnUiThread(() -> {
                startActivity(new Intent(WebPanelActivity.this, DiagnosticsActivity.class));
                finish();
            });
        }

        @JavascriptInterface
        public void openDiscord() {
            runOnUiThread(() -> {
                if (!Ui.openUrl(WebPanelActivity.this, Community.INVITE)) {
                    android.widget.Toast.makeText(WebPanelActivity.this,
                            getString(R.string.report_discord_missing),
                            android.widget.Toast.LENGTH_LONG).show();
                }
            });
        }
    }

    @Override
    protected void onDestroy() {
        if (web != null) {
            // Without this the page keeps a reference to the activity and leaks it.
            web.removeJavascriptInterface("Panel");
            web.destroy();
            web = null;
        }
        super.onDestroy();
    }
}
