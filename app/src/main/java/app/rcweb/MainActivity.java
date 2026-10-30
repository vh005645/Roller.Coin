package app.rcweb;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.webkit.RenderProcessGoneDetail;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

public class MainActivity extends Activity {
    static final String HOME = "https://rollercoin.com/";

    // Các domain quảng cáo phổ biến. Có thể thêm domain khác trong menu (☰ > Domain đã tải).
    static final String[] AD_HOSTS = {
        "doubleclick.net", "googlesyndication.com", "googleadservices.com",
        "adservice.google.com", "googletagservices.com", "adnxs.com", "adsrvr.org",
        "criteo.com", "criteo.net", "taboola.com", "outbrain.com", "rubiconproject.com",
        "pubmatic.com", "openx.net", "casalemedia.com", "amazon-adsystem.com",
        "adform.net", "smartadserver.com", "33across.com", "moatads.com",
        "adsafeprotected.com", "popads.net", "propellerads.com", "adcash.com",
        "exoclick.com", "mgid.com", "revcontent.com", "yieldmo.com", "sharethrough.com"
    };

    static final int[] ORI = {
        ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED,
        ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE,
        ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
    };
    static final String[] ORI_NAME = {"Tự động", "Ngang", "Dọc"};

    WebView web;
    SharedPreferences sp;
    boolean adblock, popupBlock;
    int oriMode;
    volatile int blocked;
    long lastBack;
    final Set<String> custom = new CopyOnWriteArraySet<>();
    final Map<String, Integer> seen = new HashMap<>();

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        sp = getSharedPreferences("rc", MODE_PRIVATE);
        adblock = sp.getBoolean("adblock2", false);
        popupBlock = sp.getBoolean("popup", true);
        oriMode = sp.getInt("ori", 0);
        custom.addAll(sp.getStringSet("custom", new HashSet<String>()));
        setRequestedOrientation(ORI[oriMode]);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        if (Build.VERSION.SDK_INT >= 28) {
            WindowManager.LayoutParams a = getWindow().getAttributes();
            a.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            getWindow().setAttributes(a);
        }

        web = new WebView(this);
        setupWeb();

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        root.addView(web, new FrameLayout.LayoutParams(-1, -1));

        TextView btn = new TextView(this);
        btn.setText("☰");
        btn.setTextColor(Color.WHITE);
        btn.setTextSize(18);
        btn.setGravity(Gravity.CENTER);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0x66000000);
        bg.setCornerRadius(dp(20));
        btn.setBackground(bg);
        btn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { menu(); }
        });
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(dp(40), dp(40), Gravity.BOTTOM | Gravity.START);
        lp.setMargins(dp(8), 0, 0, dp(8));
        root.addView(btn, lp);

        setContentView(root);
        web.loadUrl(HOME);
    }

    void setupWeb() {
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setSupportMultipleWindows(true);
        s.setUseWideViewPort(true);
        s.setLoadWithOverviewMode(true);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);
        s.setUserAgentString(s.getUserAgentString().replace("; wv", "").replace("Version/4.0 ", ""));
        CookieManager cm = CookieManager.getInstance();
        cm.setAcceptCookie(true);
        cm.setAcceptThirdPartyCookies(web, true);
        web.setBackgroundColor(Color.BLACK);
        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onCreateWindow(WebView v, boolean dlg, final boolean gesture, android.os.Message msg) {
                final WebView t = new WebView(MainActivity.this);
                t.getSettings().setJavaScriptEnabled(true);
                t.setWebViewClient(new WebViewClient() {
                    boolean done;
                    void go(Uri u) {
                        if (done || u == null || "about:blank".equals(u.toString())) return;
                        done = true;
                        String h = u.getHost();
                        boolean http = "http".equals(u.getScheme()) || "https".equals(u.getScheme());
                        boolean trusted = h != null && (isOwn(h) || isLogin(h));
                        if (!http || (adblock && h != null && isBlocked(h))) {
                            blocked++;
                        } else if (trusted && (!popupBlock || gesture)) {
                            web.loadUrl(u.toString());
                        } else if (!popupBlock) {
                            try { startActivity(new android.content.Intent(android.content.Intent.ACTION_VIEW, u)); } catch (Exception e) { }
                        } else {
                            blocked++;
                        }
                        web.post(new Runnable() { public void run() { t.destroy(); } });
                    }
                    @Override public boolean shouldOverrideUrlLoading(WebView w, WebResourceRequest r) { go(r.getUrl()); return true; }
                    @Override public void onPageStarted(WebView w, String url, android.graphics.Bitmap f) { go(url == null ? null : Uri.parse(url)); }
                });
                ((WebView.WebViewTransport) msg.obj).setWebView(t);
                msg.sendToTarget();
                return true;
            }
        });
        web.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView v, WebResourceRequest r) {
                String h = r.getUrl().getHost();
                if (h == null || isOwn(h)) return null;
                if (adblock && isBlocked(h)) {
                    blocked++;
                    return new WebResourceResponse("text/plain", "utf-8", new ByteArrayInputStream(new byte[0]));
                }
                synchronized (seen) {
                    Integer n = seen.get(h);
                    seen.put(h, n == null ? 1 : n + 1);
                }
                return null;
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) {
                Uri u = r.getUrl();
                String sch = u.getScheme();
                if ("http".equals(sch) || "https".equals(sch)) {
                    String h = u.getHost();
                    if (adblock && h != null && !isOwn(h) && isBlocked(h)) {
                        blocked++;
                        return true;
                    }
                    if (popupBlock && r.isForMainFrame() && !r.hasGesture() && h != null && !isOwn(h) && !isLogin(h)) {
                        blocked++;
                        return true;
                    }
                    return false;
                }
                // about/blob/data/javascript: cho qua; các scheme khác (market://, intent://...) bị chặn
                return !("about".equals(sch) || "blob".equals(sch) || "data".equals(sch)
                        || "javascript".equals(sch) || "file".equals(sch));
            }

            @Override
            public boolean onRenderProcessGone(WebView v, RenderProcessGoneDetail d) {
                recreate();
                return true;
            }
        });
    }

    static final String[] LOGIN = {
        "google.com", "gstatic.com", "facebook.com", "apple.com", "x.com", "twitter.com",
        "discord.com", "telegram.org", "t.me", "microsoftonline.com", "live.com",
        "recaptcha.net", "hcaptcha.com", "paypal.com", "stripe.com"
    };

    static boolean isLogin(String h) {
        for (String d : LOGIN) if (h.equals(d) || h.endsWith("." + d)) return true;
        return false;
    }

    static boolean isOwn(String h) {
        return h.equals("rollercoin.com") || h.endsWith(".rollercoin.com");
    }

    boolean isBlocked(String h) {
        for (String d : AD_HOSTS) if (h.equals(d) || h.endsWith("." + d)) return true;
        for (String d : custom) if (h.equals(d) || h.endsWith("." + d)) return true;
        return false;
    }

    int dp(float v) {
        return (int) (v * getResources().getDisplayMetrics().density);
    }

    void menu() {
        String[] items = {
            "Chặn quảng cáo: " + (adblock ? "BẬT" : "TẮT"),
            "Chặn popup/chuyển hướng lạ: " + (popupBlock ? "BẬT" : "TẮT"),
            "Tải lại trang",
            "Xoay màn hình: " + ORI_NAME[oriMode],
            "Domain đã tải (chạm để chặn)",
            "Xóa danh sách chặn thêm (" + custom.size() + ")",
            "Xóa cache",
            "Về trang chủ"
        };
        new AlertDialog.Builder(this)
            .setTitle("Đã chặn " + blocked + " yêu cầu")
            .setItems(items, new android.content.DialogInterface.OnClickListener() {
                public void onClick(android.content.DialogInterface d, int i) {
                    switch (i) {
                        case 0:
                            adblock = !adblock;
                            sp.edit().putBoolean("adblock2", adblock).apply();
                            web.reload();
                            toast("Chặn quảng cáo: " + (adblock ? "BẬT" : "TẮT"));
                            break;
                        case 1:
                            popupBlock = !popupBlock;
                            sp.edit().putBoolean("popup", popupBlock).apply();
                            toast("Chặn popup/chuyển hướng lạ: " + (popupBlock ? "BẬT" : "TẮT"));
                            break;
                        case 2: web.reload(); break;
                        case 3:
                            oriMode = (oriMode + 1) % ORI.length;
                            sp.edit().putInt("ori", oriMode).apply();
                            setRequestedOrientation(ORI[oriMode]);
                            toast("Xoay màn hình: " + ORI_NAME[oriMode]);
                            break;
                        case 4: showSeen(); break;
                        case 5:
                            custom.clear();
                            sp.edit().remove("custom").apply();
                            web.reload();
                            break;
                        case 6: web.clearCache(true); toast("Đã xóa cache"); break;
                        case 7: web.loadUrl(HOME); break;
                    }
                }
            }).show();
    }

    void showSeen() {
        final List<String> hosts;
        synchronized (seen) {
            hosts = new ArrayList<>(seen.keySet());
            Collections.sort(hosts, new Comparator<String>() {
                public int compare(String a, String c) { return seen.get(c) - seen.get(a); }
            });
            for (int i = 0; i < hosts.size(); i++) hosts.set(i, hosts.get(i));
            final int n = Math.min(hosts.size(), 50);
            hosts.subList(n, hosts.size()).clear();
        }
        if (hosts.isEmpty()) { toast("Chưa có domain ngoài nào"); return; }
        String[] labels = new String[hosts.size()];
        synchronized (seen) {
            for (int i = 0; i < labels.length; i++) labels[i] = hosts.get(i) + "  (" + seen.get(hosts.get(i)) + ")";
        }
        new AlertDialog.Builder(this)
            .setTitle("Chạm vào domain để chặn")
            .setItems(labels, new android.content.DialogInterface.OnClickListener() {
                public void onClick(android.content.DialogInterface d, int i) {
                    String h = hosts.get(i);
                    custom.add(h);
                    sp.edit().putStringSet("custom", new HashSet<String>(custom)).apply();
                    synchronized (seen) { seen.remove(h); }
                    toast("Đã chặn " + h);
                    web.reload();
                }
            }).show();
    }

    void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }

    void immersive() {
        getWindow().getDecorView().setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            | View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
    }

    @Override
    public void onWindowFocusChanged(boolean f) {
        super.onWindowFocusChanged(f);
        if (f) immersive();
    }

    @Override
    public void onBackPressed() {
        if (web.canGoBack()) {
            web.goBack();
        } else {
            long n = System.currentTimeMillis();
            if (n - lastBack < 2000) finish();
            else { lastBack = n; toast("Nhấn Back lần nữa để thoát"); }
        }
    }

    @Override protected void onPause() { super.onPause(); web.onPause(); CookieManager.getInstance().flush(); }
    @Override protected void onResume() { super.onResume(); web.onResume(); }
    @Override protected void onDestroy() { super.onDestroy(); web.destroy(); }
}
