package com.oneuibook.jupiter;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * 주피터 (Jupiter) — Chromium WebView mobile browser styled after Apple Safari with One UI 9.5 Liquid Glass.
 */
public class MainActivity extends Activity {
    static final String VERSION = "9.5.0";
    private static final String DESKTOP_UA =
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 14_5) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36";

    static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("jupiter_browser", Context.MODE_PRIVATE);
    }

    static final class Tab {
        final int id;
        String title = "시작 페이지";
        String url = "";
        boolean isPrivate;
        boolean desktopMode;
        boolean readerMode;
        boolean loading;
        int progress;
        WebView webView;

        Tab(int id, boolean isPrivate) {
            this.id = id;
            this.isPrivate = isPrivate;
        }

        boolean isStartPage() {
            return url == null || url.isEmpty() || "about:blank".equals(url);
        }
    }

    BrowserStore store;
    private final List<Tab> normalTabs = new ArrayList<>();
    private final List<Tab> privateTabs = new ArrayList<>();
    private boolean privateMode = false;
    private int activeNormalIdx = 0;
    private int activePrivateIdx = 0;
    private int nextTabId = 1;

    private Glass.Backdrop backdrop;
    private FrameLayout stage;
    private ScrollView startPageScroll;
    private LinearLayout startPageBody;
    private ProgressBar progressBar;

    // Bottom Safari Liquid Glass chrome
    private GlassBarHost bottomGlassHost;
    private LinearLayout bottomChrome;
    private LinearLayout addressPill;
    private TextView aaBtn;
    private TextView urlDisplay;
    private EditText urlInput;
    private TextView reloadBtn;
    private TextView cancelEditBtn;
    private LinearLayout toolRow;
    private TextView backBtn, fwdBtn, shareBtn, bmBtn, tabsBtn;
    private boolean editingUrl = false;
    private boolean chromeCollapsed = false;

    // Find in page bar
    private LinearLayout findBar;
    private EditText findInput;
    private TextView findCount;

    // Safari Tab Switcher overlay
    private FrameLayout tabSwitcherOverlay;
    private ScrollView tabGridScroll;
    private GridLayout tabGrid;
    private TextView segNormal, segPrivate;

    private int insetTop, insetBottom;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        store = new BrowserStore(this);
        Ui.init(this, store.prefs());

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Ui.bg());

        // 1. Backdrop wrapping the web stage + start page so floating Safari chrome refracts live content
        backdrop = new Glass.Backdrop(this);
        stage = new FrameLayout(this);
        stage.setBackgroundColor(Ui.bg());
        backdrop.addView(stage, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        startPageScroll = new ScrollView(this);
        startPageScroll.setFillViewport(true);
        startPageScroll.setOverScrollMode(View.OVER_SCROLL_ALWAYS);
        startPageBody = new LinearLayout(this);
        startPageBody.setOrientation(LinearLayout.VERTICAL);
        startPageBody.setPadding(Ui.dp(20), Ui.dp(24), Ui.dp(20), Ui.dp(136));
        startPageScroll.addView(startPageBody, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        stage.addView(startPageScroll, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        root.addView(backdrop, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // 2. Top thin progress bar
        progressBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progressBar.setMax(100);
        progressBar.setVisibility(View.GONE);
        FrameLayout.LayoutParams plp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(3), Gravity.TOP);
        root.addView(progressBar, plp);

        // 3. Find-in-page floating bar (top)
        buildFindBar();
        FrameLayout.LayoutParams flp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP);
        flp.leftMargin = Ui.dp(14);
        flp.rightMargin = Ui.dp(14);
        flp.topMargin = Ui.dp(44);
        root.addView(findBar, flp);

        // 4. Bottom Safari Floating Liquid Glass Bar
        buildBottomChrome();
        FrameLayout.LayoutParams blp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM);
        root.addView(bottomGlassHost, blp);

        // 5. Safari Tab Switcher overlay
        buildTabSwitcher();
        root.addView(tabSwitcherOverlay, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        setContentView(root);
        applySystemBars();

        root.setOnApplyWindowInsetsListener((v, insets) -> {
            android.graphics.Insets sys = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.ime());
            insetTop = sys.top;
            insetBottom = sys.bottom;
            applyInsets();
            return insets;
        });

        // Initial tab
        createTab(false, true);
        handleIntent(getIntent());
        renderActiveTab();
        Updater.autoCheck(this);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIntent(intent);
    }

    private void handleIntent(Intent intent) {
        if (intent == null) return;
        if (Intent.ACTION_VIEW.equals(intent.getAction()) && intent.getData() != null) {
            String url = intent.getDataString();
            if (url != null && (url.startsWith("http://") || url.startsWith("https://"))) {
                privateMode = false;
                Tab t = activeTab();
                if (!t.isStartPage()) {
                    t = createTab(false, true);
                }
                loadUrlInTab(t, url);
            }
        }
    }

    private void applySystemBars() {
        WindowInsetsController ctrl = getWindow().getInsetsController();
        if (ctrl != null) {
            int light = (Ui.dark || privateMode) ? 0
                    : (WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS);
            ctrl.setSystemBarsAppearance(light,
                    WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS);
        }
    }

    private void applyInsets() {
        int topPad = insetTop + Ui.dp(12);
        int bottomPad = insetBottom + Ui.dp(128);
        startPageBody.setPadding(Ui.dp(20), topPad, Ui.dp(20), bottomPad);
        bottomGlassHost.setPadding(Ui.dp(14), Ui.dp(6), Ui.dp(14), Math.max(Ui.dp(10), insetBottom + Ui.dp(6)));
        FrameLayout.LayoutParams flp = (FrameLayout.LayoutParams) findBar.getLayoutParams();
        flp.topMargin = insetTop + Ui.dp(8);
        findBar.setLayoutParams(flp);
        FrameLayout.LayoutParams plp = (FrameLayout.LayoutParams) progressBar.getLayoutParams();
        plp.topMargin = insetTop;
        progressBar.setLayoutParams(plp);
    }

    // ---- Tabs Management ----
    private List<Tab> currentList() {
        return privateMode ? privateTabs : normalTabs;
    }

    private int currentIdx() {
        return privateMode ? activePrivateIdx : activeNormalIdx;
    }

    private void setCurrentIdx(int idx) {
        if (privateMode) activePrivateIdx = idx;
        else activeNormalIdx = idx;
    }

    Tab activeTab() {
        List<Tab> list = currentList();
        if (list.isEmpty()) return createTab(privateMode, true);
        int idx = Math.max(0, Math.min(currentIdx(), list.size() - 1));
        setCurrentIdx(idx);
        return list.get(idx);
    }

    Tab createTab(boolean isPrivate, boolean select) {
        Tab t = new Tab(nextTabId++, isPrivate);
        List<Tab> list = isPrivate ? privateTabs : normalTabs;
        list.add(t);
        if (select) {
            privateMode = isPrivate;
            setCurrentIdx(list.size() - 1);
            chromeCollapsed = false;
            renderActiveTab();
        }
        return t;
    }

    private void closeTab(Tab t) {
        List<Tab> list = t.isPrivate ? privateTabs : normalTabs;
        int idx = list.indexOf(t);
        if (idx < 0) return;
        if (t.webView != null) {
            stage.removeView(t.webView);
            t.webView.destroy();
            t.webView = null;
        }
        list.remove(idx);
        if (list.isEmpty()) {
            createTab(t.isPrivate, true);
        } else {
            int next = Math.min(idx, list.size() - 1);
            if (t.isPrivate) activePrivateIdx = next;
            else activeNormalIdx = next;
        }
        refreshTabGrid();
        renderActiveTab();
    }

    private WebView ensureWebView(Tab tab) {
        if (tab.webView != null) return tab.webView;
        WebView wv = new WebView(this);
        wv.setBackgroundColor(Ui.bg());
        WebSettings ws = wv.getSettings();
        ws.setJavaScriptEnabled(true);
        ws.setDomStorageEnabled(true);
        ws.setDatabaseEnabled(!tab.isPrivate);
        ws.setSupportZoom(true);
        ws.setBuiltInZoomControls(true);
        ws.setDisplayZoomControls(false);
        ws.setUseWideViewPort(true);
        ws.setLoadWithOverviewMode(true);
        ws.setTextZoom(store.pageZoom());
        if (tab.isPrivate) {
            ws.setCacheMode(WebSettings.LOAD_NO_CACHE);
            ws.setSaveFormData(false);
        }
        CookieManager.getInstance().setAcceptCookie(!tab.isPrivate);

        wv.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                if (store.contentBlocker() && request != null && request.getUrl() != null) {
                    String reqUrl = request.getUrl().toString();
                    if (BrowserStore.isTrackerHost(reqUrl)) {
                        store.recordBlocked();
                        return new WebResourceResponse("text/plain", "utf-8", new ByteArrayInputStream(new byte[0]));
                    }
                }
                return super.shouldInterceptRequest(view, request);
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                if (request == null || request.getUrl() == null) return false;
                Uri u = request.getUrl();
                String scheme = u.getScheme();
                if ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme)) {
                    return false;
                }
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, u));
                    return true;
                } catch (Exception ignored) {
                    return true;
                }
            }

            @Override
            public void onPageStarted(WebView view, String url, android.graphics.Bitmap favicon) {
                tab.url = url;
                tab.loading = true;
                tab.readerMode = false;
                if (tab == activeTab()) updateChromeState();
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                tab.url = url;
                tab.loading = false;
                String title = view.getTitle();
                if (title != null && !title.trim().isEmpty()) tab.title = title.trim();
                if (!tab.isPrivate) store.recordHistory(tab.title, url);
                if (store.darkWeb()) injectDarkWeb(view, true);
                if (tab == activeTab()) updateChromeState();
            }
        });

        wv.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                tab.progress = newProgress;
                if (tab == activeTab()) {
                    if (newProgress >= 100) {
                        progressBar.setVisibility(View.GONE);
                    } else {
                        progressBar.setVisibility(View.VISIBLE);
                        progressBar.setProgress(newProgress);
                    }
                }
            }

            @Override
            public void onReceivedTitle(WebView view, String title) {
                if (title != null && !title.trim().isEmpty()) {
                    tab.title = title.trim();
                    if (tab == activeTab()) updateChromeState();
                }
            }
        });

        wv.setOnScrollChangeListener((v, scrollX, scrollY, oldScrollX, oldScrollY) -> {
            if (editingUrl) return;
            int dy = scrollY - oldScrollY;
            if (dy > Ui.dp(10) && !chromeCollapsed && scrollY > Ui.dp(60)) {
                setChromeCollapsed(true);
            } else if (dy < -Ui.dp(10) && chromeCollapsed) {
                setChromeCollapsed(false);
            }
        });

        tab.webView = wv;
        stage.addView(wv, 0, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        return wv;
    }

    void loadUrlInTab(Tab tab, String rawInput) {
        String resolved = store.resolveInput(rawInput);
        if (resolved.isEmpty()) return;
        tab.url = resolved;
        tab.title = BrowserStore.displayHost(resolved);
        tab.loading = true;
        WebView wv = ensureWebView(tab);
        applyTabSettings(tab);
        wv.loadUrl(resolved);
        exitUrlEdit();
        setChromeCollapsed(false);
        renderActiveTab();
    }

    private void applyTabSettings(Tab tab) {
        if (tab.webView == null) return;
        WebSettings ws = tab.webView.getSettings();
        ws.setUserAgentString(tab.desktopMode ? DESKTOP_UA : null);
        ws.setTextZoom(store.pageZoom());
    }

    private void renderActiveTab() {
        Tab active = activeTab();
        for (int i = 0; i < stage.getChildCount(); i++) {
            View child = stage.getChildAt(i);
            if (child instanceof WebView) {
                child.setVisibility(child == active.webView && !active.isStartPage() ? View.VISIBLE : View.GONE);
            }
        }
        if (active.isStartPage()) {
            startPageScroll.setVisibility(View.VISIBLE);
            rebuildStartPage();
        } else {
            startPageScroll.setVisibility(View.GONE);
        }
        applySystemBars();
        updateChromeState();
    }

    // ---- Safari Start Page ----
    private void rebuildStartPage() {
        startPageBody.removeAllViews();
        int bgColor = privateMode ? 0xFF14151B : Ui.bg();
        stage.setBackgroundColor(bgColor);
        startPageScroll.setBackgroundColor(bgColor);

        boolean darkLook = Ui.dark || privateMode;
        int primaryText = darkLook ? 0xFFF4F6FA : 0xFF111318;
        int secondaryText = darkLook ? 0xFF9EA6B6 : 0xFF6B7280;
        int cardBg = darkLook ? 0xFF1E212B : 0xFFFFFFFF;

        // Header row
        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = makeText(privateMode ? "개인정보 보호 브라우징" : "시작 페이지", 28, Typeface.BOLD, primaryText);
        head.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView modePill = makeText(privateMode ? "비공개" : "주피터", 12, Typeface.BOLD,
                privateMode ? 0xFFFFFFFF : 0xFF0E68EE);
        modePill.setPadding(Ui.dp(12), Ui.dp(6), Ui.dp(12), Ui.dp(6));
        modePill.setBackground(roundBg(privateMode ? 0xFF4F46E5 : 0x1A0E68EE, Ui.dp(16)));
        modePill.setOnClickListener(v -> openTabSwitcher());
        head.addView(modePill);
        startPageBody.addView(head);

        if (privateMode) {
            LinearLayout banner = new LinearLayout(this);
            banner.setOrientation(LinearLayout.VERTICAL);
            banner.setPadding(Ui.dp(18), Ui.dp(16), Ui.dp(18), Ui.dp(16));
            banner.setBackground(roundBg(cardBg, Ui.dp(22)));
            banner.addView(makeText("방문 기록이 저장되지 않습니다", 15, Typeface.BOLD, primaryText));
            TextView desc = makeText("이 탭에서 본 페이지, 검색 기록, 쿠키는 기기에 남지 않으며 알려진 추적기를 자동으로 차단합니다.", 13, Typeface.NORMAL, secondaryText);
            LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            dlp.topMargin = Ui.dp(4);
            banner.addView(desc, dlp);
            LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            blp.topMargin = Ui.dp(16);
            startPageBody.addView(banner, blp);
        }

        // Favorites section
        startPageBody.addView(sectionHeader("즐겨찾기", primaryText));
        int cols = isTablet() ? 6 : 4;
        GridLayout favGrid = new GridLayout(this);
        favGrid.setColumnCount(cols);
        for (BrowserStore.Link item : store.favorites()) {
            LinearLayout tile = new LinearLayout(this);
            tile.setOrientation(LinearLayout.VERTICAL);
            tile.setGravity(Gravity.CENTER_HORIZONTAL);
            tile.setPadding(Ui.dp(4), Ui.dp(8), Ui.dp(4), Ui.dp(8));

            TextView badge = makeText(initialOf(item.title), 20, Typeface.BOLD, Color.WHITE);
            badge.setGravity(Gravity.CENTER);
            badge.setBackground(roundBg(item.color, Ui.dp(16)));
            tile.addView(badge, new LinearLayout.LayoutParams(Ui.dp(56), Ui.dp(56)));

            TextView name = makeText(item.title, 12, Typeface.NORMAL, primaryText);
            name.setGravity(Gravity.CENTER);
            name.setMaxLines(1);
            name.setEllipsize(TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams nlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            nlp.topMargin = Ui.dp(6);
            tile.addView(name, nlp);

            tile.setOnClickListener(v -> loadUrlInTab(activeTab(), item.url));
            GridLayout.LayoutParams glp = new GridLayout.LayoutParams();
            glp.width = 0;
            glp.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
            favGrid.addView(tile, glp);
        }
        startPageBody.addView(favGrid, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // Safari Privacy Report Card
        startPageBody.addView(sectionHeader("개인정보 보호 리포트", primaryText));
        LinearLayout privCard = new LinearLayout(this);
        privCard.setOrientation(LinearLayout.HORIZONTAL);
        privCard.setGravity(Gravity.CENTER_VERTICAL);
        privCard.setPadding(Ui.dp(18), Ui.dp(16), Ui.dp(18), Ui.dp(16));
        privCard.setBackground(roundBg(cardBg, Ui.dp(22)));

        TextView shield = makeText("🛡", 24, Typeface.NORMAL, 0xFF0E68EE);
        shield.setGravity(Gravity.CENTER);
        shield.setBackground(roundBg(0x1A0E68EE, Ui.dp(14)));
        privCard.addView(shield, new LinearLayout.LayoutParams(Ui.dp(46), Ui.dp(46)));

        LinearLayout privText = new LinearLayout(this);
        privText.setOrientation(LinearLayout.VERTICAL);
        privText.setPadding(Ui.dp(14), 0, 0, 0);
        int blocked = store.blockedCount();
        privText.addView(makeText("추적기 " + blocked + "개 차단됨", 15.5f, Typeface.BOLD, primaryText));
        privText.addView(makeText(store.contentBlocker()
                ? "콘텐츠 차단기가 활성 상태입니다 · 검색 엔진: " + store.searchEngineName()
                : "콘텐츠 차단기가 꺼져 있습니다", 12.5f, Typeface.NORMAL, secondaryText));
        privCard.addView(privText, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        privCard.setOnClickListener(v -> showPageToolsSheet());
        startPageBody.addView(privCard, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // Reading List preview (if any)
        List<BrowserStore.Link> reading = store.readingList();
        if (!reading.isEmpty()) {
            startPageBody.addView(sectionHeader("읽기 목록", primaryText));
            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setBackground(roundBg(cardBg, Ui.dp(22)));
            int count = Math.min(reading.size(), 4);
            for (int i = 0; i < count; i++) {
                BrowserStore.Link l = reading.get(i);
                card.addView(linkRow(l, primaryText, secondaryText, i > 0));
            }
            startPageBody.addView(card, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }

        // Bookmarks preview (if any)
        List<BrowserStore.Link> bms = store.bookmarks();
        if (!bms.isEmpty()) {
            startPageBody.addView(sectionHeader("북마크", primaryText));
            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setBackground(roundBg(cardBg, Ui.dp(22)));
            int count = Math.min(bms.size(), 4);
            for (int i = 0; i < count; i++) {
                BrowserStore.Link l = bms.get(i);
                card.addView(linkRow(l, primaryText, secondaryText, i > 0));
            }
            startPageBody.addView(card, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }
    }

    private View linkRow(BrowserStore.Link l, int primaryText, int secondaryText, boolean divider) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        if (divider) {
            View div = new View(this);
            div.setBackgroundColor(Ui.dark || privateMode ? 0x1AFFFFFF : 0x14000000);
            LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1);
            dlp.leftMargin = Ui.dp(18);
            box.addView(div, dlp);
        }
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(Ui.dp(18), Ui.dp(12), Ui.dp(18), Ui.dp(12));
        TextView t = makeText(l.title, 14.5f, Typeface.BOLD, primaryText);
        t.setMaxLines(1);
        t.setEllipsize(TextUtils.TruncateAt.END);
        TextView u = makeText(BrowserStore.displayHost(l.url), 12f, Typeface.NORMAL, secondaryText);
        u.setMaxLines(1);
        u.setEllipsize(TextUtils.TruncateAt.END);
        row.addView(t);
        row.addView(u);
        row.setOnClickListener(v -> loadUrlInTab(activeTab(), l.url));
        box.addView(row);
        return box;
    }

    // ---- Bottom Safari Liquid Glass Chrome ----
    private void buildBottomChrome() {
        bottomGlassHost = new GlassBarHost(this, backdrop);
        bottomGlassHost.setPadding(Ui.dp(14), Ui.dp(6), Ui.dp(14), Ui.dp(12));

        bottomChrome = new LinearLayout(this);
        bottomChrome.setOrientation(LinearLayout.VERTICAL);
        bottomChrome.setPadding(Ui.dp(12), Ui.dp(10), Ui.dp(12), Ui.dp(8));

        // Address / Search pill row
        addressPill = new LinearLayout(this);
        addressPill.setOrientation(LinearLayout.HORIZONTAL);
        addressPill.setGravity(Gravity.CENTER_VERTICAL);
        addressPill.setPadding(Ui.dp(10), Ui.dp(6), Ui.dp(10), Ui.dp(6));
        addressPill.setBackground(roundBg(Ui.dark ? 0x26FFFFFF : 0x14000000, Ui.dp(20)));

        aaBtn = makeText("AA", 13.5f, Typeface.BOLD, Ui.text());
        aaBtn.setGravity(Gravity.CENTER);
        aaBtn.setPadding(Ui.dp(8), Ui.dp(6), Ui.dp(8), Ui.dp(6));
        aaBtn.setOnClickListener(v -> showPageToolsSheet());
        addressPill.addView(aaBtn);

        urlDisplay = makeText("검색 또는 웹사이트 입력", 14.5f, Typeface.NORMAL, Ui.text());
        urlDisplay.setGravity(Gravity.CENTER);
        urlDisplay.setMaxLines(1);
        urlDisplay.setEllipsize(TextUtils.TruncateAt.END);
        urlDisplay.setPadding(Ui.dp(8), Ui.dp(6), Ui.dp(8), Ui.dp(6));
        urlDisplay.setOnClickListener(v -> {
            if (chromeCollapsed) {
                setChromeCollapsed(false);
            } else {
                enterUrlEdit();
            }
        });
        addressPill.addView(urlDisplay, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        urlInput = new EditText(this);
        urlInput.setSingleLine(true);
        urlInput.setBackground(null);
        urlInput.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14.5f);
        urlInput.setTextColor(Ui.text());
        urlInput.setHintTextColor(Ui.text2());
        urlInput.setHint("검색 또는 웹사이트 입력");
        urlInput.setImeOptions(EditorInfo.IME_ACTION_GO);
        urlInput.setPadding(Ui.dp(8), Ui.dp(6), Ui.dp(8), Ui.dp(6));
        urlInput.setVisibility(View.GONE);
        urlInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_GO || (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
                loadUrlInTab(activeTab(), urlInput.getText().toString());
                return true;
            }
            return false;
        });
        addressPill.addView(urlInput, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        reloadBtn = makeText("↻", 18, Typeface.BOLD, Ui.text());
        reloadBtn.setGravity(Gravity.CENTER);
        reloadBtn.setPadding(Ui.dp(8), Ui.dp(4), Ui.dp(8), Ui.dp(4));
        reloadBtn.setOnClickListener(v -> {
            Tab t = activeTab();
            if (t.isStartPage() || t.webView == null) return;
            if (t.loading) t.webView.stopLoading();
            else t.webView.reload();
        });
        addressPill.addView(reloadBtn);

        cancelEditBtn = makeText("취소", 13.5f, Typeface.BOLD, 0xFF0E68EE);
        cancelEditBtn.setPadding(Ui.dp(8), Ui.dp(6), Ui.dp(4), Ui.dp(6));
        cancelEditBtn.setVisibility(View.GONE);
        cancelEditBtn.setOnClickListener(v -> exitUrlEdit());
        addressPill.addView(cancelEditBtn);

        bottomChrome.addView(addressPill, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // Safari 5-button toolbar row
        toolRow = new LinearLayout(this);
        toolRow.setOrientation(LinearLayout.HORIZONTAL);
        toolRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams trlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(42));
        trlp.topMargin = Ui.dp(4);

        backBtn = makeToolBtn("‹", 24, v -> {
            Tab t = activeTab();
            if (t.webView != null && t.webView.canGoBack()) {
                t.webView.goBack();
            } else if (!t.isStartPage()) {
                t.url = "";
                t.title = "시작 페이지";
                renderActiveTab();
            }
        });
        fwdBtn = makeToolBtn("›", 24, v -> {
            Tab t = activeTab();
            if (t.webView != null && t.webView.canGoForward()) t.webView.goForward();
        });
        shareBtn = makeToolBtn("↑", 19, v -> shareCurrentPage());
        bmBtn = makeToolBtn("☆", 20, v -> showLibrarySheet());
        tabsBtn = makeToolBtn("1", 13.5f, v -> openTabSwitcher());

        toolRow.addView(backBtn, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
        toolRow.addView(fwdBtn, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
        toolRow.addView(shareBtn, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
        toolRow.addView(bmBtn, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
        toolRow.addView(tabsBtn, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));

        bottomChrome.addView(toolRow, trlp);
        bottomGlassHost.addView(bottomChrome, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    private TextView makeToolBtn(String label, float sp, View.OnClickListener onClick) {
        TextView tv = makeText(label, sp, Typeface.BOLD, Ui.text());
        tv.setGravity(Gravity.CENTER);
        tv.setOnClickListener(onClick);
        return tv;
    }

    private void enterUrlEdit() {
        editingUrl = true;
        aaBtn.setVisibility(View.GONE);
        urlDisplay.setVisibility(View.GONE);
        reloadBtn.setVisibility(View.GONE);
        urlInput.setVisibility(View.VISIBLE);
        cancelEditBtn.setVisibility(View.VISIBLE);
        Tab t = activeTab();
        urlInput.setText(t.isStartPage() ? "" : t.url);
        urlInput.requestFocus();
        urlInput.selectAll();
        InputMethodManager imm = getSystemService(InputMethodManager.class);
        if (imm != null) imm.showSoftInput(urlInput, InputMethodManager.SHOW_IMPLICIT);
    }

    private void exitUrlEdit() {
        editingUrl = false;
        aaBtn.setVisibility(View.VISIBLE);
        urlDisplay.setVisibility(View.VISIBLE);
        reloadBtn.setVisibility(View.VISIBLE);
        urlInput.setVisibility(View.GONE);
        cancelEditBtn.setVisibility(View.GONE);
        InputMethodManager imm = getSystemService(InputMethodManager.class);
        if (imm != null) imm.hideSoftInputFromWindow(urlInput.getWindowToken(), 0);
        updateChromeState();
    }

    private void setChromeCollapsed(boolean collapsed) {
        if (chromeCollapsed == collapsed) return;
        chromeCollapsed = collapsed;
        toolRow.setVisibility(collapsed ? View.GONE : View.VISIBLE);
        aaBtn.setVisibility(collapsed ? View.GONE : View.VISIBLE);
        reloadBtn.setVisibility(collapsed ? View.GONE : View.VISIBLE);
        bottomChrome.animate().scaleX(collapsed ? 0.88f : 1f).scaleY(collapsed ? 0.92f : 1f)
                .setDuration(200).start();
        bottomGlassHost.invalidate();
    }

    private void updateChromeState() {
        Tab t = activeTab();
        boolean darkLook = Ui.dark || privateMode;
        int fg = darkLook ? 0xFFF4F6FA : 0xFF111318;
        int dim = darkLook ? 0x55FFFFFF : 0x44000000;

        addressPill.setBackground(roundBg(privateMode ? 0x336366F1 : (darkLook ? 0x22FFFFFF : 0x12000000), Ui.dp(20)));
        aaBtn.setTextColor(fg);
        urlDisplay.setTextColor(t.isStartPage() ? (darkLook ? 0xFF9EA6B6 : 0xFF6B7280) : fg);
        urlInput.setTextColor(fg);
        String label = t.isStartPage()
                ? (privateMode ? "개인정보 보호 · 검색 또는 주소 입력" : "검색 또는 웹사이트 입력")
                : ((t.url.startsWith("https://") ? "🔒 " : "") + BrowserStore.displayHost(t.url));
        urlDisplay.setText(label);

        reloadBtn.setText(t.loading ? "✕" : "↻");
        reloadBtn.setTextColor(t.isStartPage() ? dim : fg);

        boolean canBack = !t.isStartPage();
        boolean canFwd = t.webView != null && t.webView.canGoForward();
        backBtn.setTextColor(canBack ? fg : dim);
        fwdBtn.setTextColor(canFwd ? fg : dim);
        shareBtn.setTextColor(t.isStartPage() ? dim : fg);
        bmBtn.setTextColor(fg);
        tabsBtn.setText("❐ " + currentList().size());
        tabsBtn.setTextColor(privateMode ? 0xFFA5B4FC : fg);
        bottomGlassHost.setPrivateTint(privateMode);
    }

    // ---- Find in Page Bar ----
    private void buildFindBar() {
        findBar = new LinearLayout(this);
        findBar.setOrientation(LinearLayout.HORIZONTAL);
        findBar.setGravity(Gravity.CENTER_VERTICAL);
        findBar.setPadding(Ui.dp(14), Ui.dp(8), Ui.dp(10), Ui.dp(8));
        findBar.setBackground(roundBg(Ui.dark ? 0xEE1E222D : 0xF5FFFFFF, Ui.dp(22)));
        findBar.setElevation(Ui.dp(8));
        findBar.setVisibility(View.GONE);

        findInput = new EditText(this);
        findInput.setSingleLine(true);
        findInput.setBackground(null);
        findInput.setHint("페이지에서 찾기");
        findInput.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f);
        findInput.setTextColor(Ui.text());
        findInput.setHintTextColor(Ui.text2());
        findInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                Tab t = activeTab();
                if (t.webView != null) {
                    if (s.length() == 0) t.webView.clearMatches();
                    else t.webView.findAllAsync(s.toString());
                }
            }
            @Override public void afterTextChanged(Editable s) {}
        });
        findBar.addView(findInput, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        findCount = makeText("", 12, Typeface.NORMAL, Ui.text2());
        findCount.setPadding(Ui.dp(6), 0, Ui.dp(6), 0);
        findBar.addView(findCount);

        TextView prev = makeText("▲", 14, Typeface.BOLD, Ui.text());
        prev.setPadding(Ui.dp(8), Ui.dp(4), Ui.dp(8), Ui.dp(4));
        prev.setOnClickListener(v -> { if (activeTab().webView != null) activeTab().webView.findNext(false); });
        TextView next = makeText("▼", 14, Typeface.BOLD, Ui.text());
        next.setPadding(Ui.dp(8), Ui.dp(4), Ui.dp(8), Ui.dp(4));
        next.setOnClickListener(v -> { if (activeTab().webView != null) activeTab().webView.findNext(true); });
        TextView close = makeText("완료", 13.5f, Typeface.BOLD, 0xFF0E68EE);
        close.setPadding(Ui.dp(8), Ui.dp(4), Ui.dp(6), Ui.dp(4));
        close.setOnClickListener(v -> hideFindBar());

        findBar.addView(prev);
        findBar.addView(next);
        findBar.addView(close);
    }

    private void showFindBar() {
        Tab t = activeTab();
        if (t.isStartPage() || t.webView == null) {
            Toast.makeText(this, "열린 웹페이지가 없습니다", Toast.LENGTH_SHORT).show();
            return;
        }
        findBar.setVisibility(View.VISIBLE);
        findInput.requestFocus();
    }

    private void hideFindBar() {
        findBar.setVisibility(View.GONE);
        findInput.setText("");
        if (activeTab().webView != null) activeTab().webView.clearMatches();
    }

    // ---- Safari Card-Grid Tab Switcher ----
    private void buildTabSwitcher() {
        tabSwitcherOverlay = new FrameLayout(this);
        tabSwitcherOverlay.setVisibility(View.GONE);

        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.VERTICAL);

        tabGridScroll = new ScrollView(this);
        tabGrid = new GridLayout(this);
        tabGrid.setPadding(Ui.dp(16), Ui.dp(52), Ui.dp(16), Ui.dp(24));
        tabGridScroll.addView(tabGrid, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        wrap.addView(tabGridScroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        // Bottom Safari switcher bar: [+] [일반 | 개인정보 보호] [완료]
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(Ui.dp(18), Ui.dp(12), Ui.dp(18), Ui.dp(24));

        TextView addBtn = makeText("+", 26, Typeface.NORMAL, Color.WHITE);
        addBtn.setGravity(Gravity.CENTER);
        addBtn.setPadding(Ui.dp(12), Ui.dp(4), Ui.dp(12), Ui.dp(4));
        addBtn.setOnClickListener(v -> {
            createTab(privateMode, true);
            closeTabSwitcher();
        });
        bar.addView(addBtn);

        LinearLayout segWrap = new LinearLayout(this);
        segWrap.setOrientation(LinearLayout.HORIZONTAL);
        segWrap.setGravity(Gravity.CENTER);
        segWrap.setPadding(Ui.dp(4), Ui.dp(4), Ui.dp(4), Ui.dp(4));
        segWrap.setBackground(roundBg(0x28FFFFFF, Ui.dp(22)));

        segNormal = makeText("일반", 13, Typeface.BOLD, Color.WHITE);
        segNormal.setPadding(Ui.dp(16), Ui.dp(8), Ui.dp(16), Ui.dp(8));
        segNormal.setOnClickListener(v -> {
            privateMode = false;
            refreshTabGrid();
        });
        segPrivate = makeText("개인정보 보호", 13, Typeface.BOLD, Color.WHITE);
        segPrivate.setPadding(Ui.dp(16), Ui.dp(8), Ui.dp(16), Ui.dp(8));
        segPrivate.setOnClickListener(v -> {
            privateMode = true;
            if (privateTabs.isEmpty()) createTab(true, false);
            refreshTabGrid();
        });
        segWrap.addView(segNormal);
        segWrap.addView(segPrivate);

        LinearLayout centerSlot = new LinearLayout(this);
        centerSlot.setGravity(Gravity.CENTER);
        centerSlot.addView(segWrap);
        bar.addView(centerSlot, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView doneBtn = makeText("완료", 15, Typeface.BOLD, Color.WHITE);
        doneBtn.setPadding(Ui.dp(12), Ui.dp(6), Ui.dp(8), Ui.dp(6));
        doneBtn.setOnClickListener(v -> closeTabSwitcher());
        bar.addView(doneBtn);

        wrap.addView(bar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        tabSwitcherOverlay.addView(wrap, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    void openTabSwitcher() {
        exitUrlEdit();
        refreshTabGrid();
        tabSwitcherOverlay.setVisibility(View.VISIBLE);
        if ("robolectric".equals(Build.FINGERPRINT)) {
            tabSwitcherOverlay.setAlpha(1f);
        } else {
            tabSwitcherOverlay.setAlpha(0f);
            tabSwitcherOverlay.animate().alpha(1f).setDuration(180).start();
        }
    }

    void closeTabSwitcher() {
        tabSwitcherOverlay.setVisibility(View.GONE);
        renderActiveTab();
    }

    private void refreshTabGrid() {
        tabSwitcherOverlay.setBackgroundColor(privateMode ? 0xF512131A : (Ui.dark ? 0xF510131C : 0xF51A202E));
        segNormal.setBackground(privateMode ? null : roundBg(0xFF0E68EE, Ui.dp(18)));
        segPrivate.setBackground(privateMode ? roundBg(0xFF4F46E5, Ui.dp(18)) : null);

        tabGrid.removeAllViews();
        int cols = isTablet() ? 3 : 2;
        tabGrid.setColumnCount(cols);
        List<Tab> list = currentList();
        Tab active = activeTab();

        for (int i = 0; i < list.size(); i++) {
            final int idx = i;
            Tab t = list.get(i);
            boolean isSel = t == active;

            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(Ui.dp(14), Ui.dp(12), Ui.dp(14), Ui.dp(14));
            GradientDrawable bg = roundBg(privateMode ? 0xFF232533 : 0xFF242A38, Ui.dp(20));
            if (isSel) bg.setStroke(Ui.dp(2), privateMode ? 0xFF818CF8 : 0xFF3898FF);
            card.setBackground(bg);

            LinearLayout top = new LinearLayout(this);
            top.setOrientation(LinearLayout.HORIZONTAL);
            top.setGravity(Gravity.CENTER_VERTICAL);
            TextView title = makeText(t.title, 13.5f, Typeface.BOLD, Color.WHITE);
            title.setMaxLines(1);
            title.setEllipsize(TextUtils.TruncateAt.END);
            top.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            TextView close = makeText("✕", 14, Typeface.BOLD, 0xCCFFFFFF);
            close.setPadding(Ui.dp(8), Ui.dp(2), Ui.dp(2), Ui.dp(2));
            close.setOnClickListener(v -> closeTab(t));
            top.addView(close);
            card.addView(top);

            TextView host = makeText(t.isStartPage() ? "시작 페이지" : BrowserStore.displayHost(t.url), 11.5f, Typeface.NORMAL, 0xAAFFFFFF);
            host.setMaxLines(1);
            host.setEllipsize(TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams hlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            hlp.topMargin = Ui.dp(4);
            card.addView(host, hlp);

            // Preview area placeholder
            View preview = new View(this);
            preview.setBackground(roundBg(privateMode ? 0xFF181924 : 0xFF1A1F2B, Ui.dp(12)));
            LinearLayout.LayoutParams pvp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(96));
            pvp.topMargin = Ui.dp(10);
            card.addView(preview, pvp);

            card.setOnClickListener(v -> {
                setCurrentIdx(idx);
                closeTabSwitcher();
            });

            GridLayout.LayoutParams glp = new GridLayout.LayoutParams();
            glp.width = 0;
            glp.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
            glp.setMargins(Ui.dp(6), Ui.dp(6), Ui.dp(6), Ui.dp(6));
            tabGrid.addView(card, glp);
        }
    }

    // ---- Safari AA Page Tools Sheet ----
    private void showPageToolsSheet() {
        Tab t = activeTab();
        boolean hasPage = !t.isStartPage() && t.webView != null;
        List<String> items = new ArrayList<>();
        items.add("텍스트 크기: " + store.pageZoom() + "% (눌러서 변경)");
        if (hasPage) {
            items.add(t.readerMode ? "읽기 도구 끄기" : "읽기 도구 보기");
            items.add(t.desktopMode ? "모바일 웹사이트 요청" : "데스크탑 웹사이트 요청");
            items.add("페이지에서 찾기");
            items.add(store.isBookmarked(t.url) ? "북마크 제거" : "북마크에 추가");
            items.add("읽기 목록에 추가");
        }
        items.add("웹 다크 모드: " + (store.darkWeb() ? "켜짐" : "꺼짐"));
        items.add("콘텐츠 차단기: " + (store.contentBlocker() ? "켜짐" : "꺼짐") + " (" + store.blockedCount() + "개 차단)");
        items.add("검색 엔진: " + store.searchEngineName());
        items.add("업데이트 확인 (주피터 " + VERSION + ")");

        new AlertDialog.Builder(this)
                .setTitle(hasPage ? BrowserStore.displayHost(t.url) : "주피터 설정")
                .setItems(items.toArray(new String[0]), (d, which) -> {
                    String picked = items.get(which);
                    if (picked.startsWith("텍스트 크기")) {
                        int[] zooms = {85, 100, 115, 130, 150};
                        int cur = store.pageZoom();
                        int next = zooms[0];
                        for (int i = 0; i < zooms.length; i++) {
                            if (zooms[i] == cur) { next = zooms[(i + 1) % zooms.length]; break; }
                        }
                        store.setPageZoom(next);
                        if (t.webView != null) t.webView.getSettings().setTextZoom(next);
                        Toast.makeText(this, "텍스트 크기 " + next + "%", Toast.LENGTH_SHORT).show();
                    } else if (picked.contains("읽기 도구")) {
                        toggleReaderMode(t);
                    } else if (picked.contains("웹사이트 요청")) {
                        t.desktopMode = !t.desktopMode;
                        applyTabSettings(t);
                        if (t.webView != null) t.webView.reload();
                    } else if (picked.equals("페이지에서 찾기")) {
                        showFindBar();
                    } else if (picked.equals("북마크에 추가")) {
                        store.addBookmark(t.title, t.url);
                        Toast.makeText(this, "북마크에 추가되었습니다", Toast.LENGTH_SHORT).show();
                    } else if (picked.equals("북마크 제거")) {
                        store.removeBookmark(t.url);
                        Toast.makeText(this, "북마크에서 제거되었습니다", Toast.LENGTH_SHORT).show();
                    } else if (picked.equals("읽기 목록에 추가")) {
                        store.addReading(t.title, t.url);
                        Toast.makeText(this, "읽기 목록에 추가되었습니다", Toast.LENGTH_SHORT).show();
                    } else if (picked.startsWith("웹 다크 모드")) {
                        boolean on = !store.darkWeb();
                        store.setDarkWeb(on);
                        if (t.webView != null) injectDarkWeb(t.webView, on);
                    } else if (picked.startsWith("콘텐츠 차단기")) {
                        store.setContentBlocker(!store.contentBlocker());
                        rebuildStartPage();
                    } else if (picked.startsWith("검색 엔진")) {
                        store.setSearchEngine((store.searchEngine() + 1) % 3);
                        Toast.makeText(this, "검색 엔진: " + store.searchEngineName(), Toast.LENGTH_SHORT).show();
                        rebuildStartPage();
                    } else if (picked.startsWith("업데이트 확인")) {
                        Updater.checkInteractive(this);
                    }
                })
                .setNegativeButton("닫기", null)
                .show();
    }

    private void toggleReaderMode(Tab t) {
        if (t.webView == null) return;
        if (t.readerMode) {
            t.readerMode = false;
            t.webView.reload();
            return;
        }
        t.readerMode = true;
        String js = "(function(){"
                + "var art=document.querySelector('article')||document.querySelector('main')||document.body;"
                + "var title=document.title||'';"
                + "var ps=Array.from(art.querySelectorAll('h1,h2,h3,p,blockquote,li')).map(function(e){return '<'+e.tagName.toLowerCase()+'>'+e.innerText+'</'+e.tagName.toLowerCase()+'>';}).join('');"
                + "if(!ps) ps='<p>'+art.innerText.replace(/\\n/g,'<br>')+'</p>';"
                + "document.documentElement.innerHTML='<head><meta name=viewport content=\"width=device-width,initial-scale=1\"><style>"
                + "body{max-width:680px;margin:0 auto;padding:24px 20px 120px;font-family:sans-serif;line-height:1.75;font-size:18px;background:"
                + (Ui.dark ? "#111318;color:#ECEFF4" : "#FAF9F6;color:#1D1F24")
                + ";}h1{font-size:24px;line-height:1.35;margin-bottom:18px;}p{margin:0 0 14px;}</style></head><body><h1>'+title+'</h1>'+ps+'</body>';"
                + "})();";
        t.webView.evaluateJavascript(js, null);
    }

    private void injectDarkWeb(WebView wv, boolean enable) {
        String js = enable
                ? "(function(){var s=document.getElementById('jupiter-dark');if(!s){s=document.createElement('style');s.id='jupiter-dark';s.textContent='html{filter:invert(0.92) hue-rotate(180deg)!important;background:#111!important;}img,video,picture,canvas{filter:invert(1) hue-rotate(180deg)!important;}';document.head.appendChild(s);}})();"
                : "(function(){var s=document.getElementById('jupiter-dark');if(s)s.remove();})();";
        wv.evaluateJavascript(js, null);
    }

    // ---- Bookmarks / Reading List / History Sheet ----
    private void showLibrarySheet() {
        String[] tabs = {"북마크 (" + store.bookmarks().size() + ")", "읽기 목록 (" + store.readingList().size() + ")", "방문 기록 (" + store.history().size() + ")"};
        new AlertDialog.Builder(this)
                .setTitle("보관함")
                .setItems(tabs, (d, which) -> {
                    if (which == 0) showLinkListDialog("북마크", store.bookmarks(), false);
                    else if (which == 1) showLinkListDialog("읽기 목록", store.readingList(), false);
                    else showLinkListDialog("방문 기록", store.history(), true);
                })
                .setNegativeButton("닫기", null)
                .show();
    }

    private void showLinkListDialog(String title, List<BrowserStore.Link> links, boolean isHistory) {
        if (links.isEmpty()) {
            Toast.makeText(this, title + "이(가) 비어 있습니다", Toast.LENGTH_SHORT).show();
            return;
        }
        String[] labels = new String[links.size()];
        for (int i = 0; i < links.size(); i++) {
            labels[i] = links.get(i).title + "\n" + BrowserStore.displayHost(links.get(i).url);
        }
        AlertDialog.Builder b = new AlertDialog.Builder(this)
                .setTitle(title)
                .setItems(labels, (d, which) -> loadUrlInTab(activeTab(), links.get(which).url))
                .setNegativeButton("닫기", null);
        if (isHistory) {
            b.setNeutralButton("기록 지우기", (d, w) -> {
                store.clearHistory();
                Toast.makeText(this, "방문 기록을 지웠습니다", Toast.LENGTH_SHORT).show();
            });
        }
        b.show();
    }

    private void shareCurrentPage() {
        Tab t = activeTab();
        if (t.isStartPage()) return;
        Intent share = new Intent(Intent.ACTION_SEND);
        share.setType("text/plain");
        share.putExtra(Intent.EXTRA_SUBJECT, t.title);
        share.putExtra(Intent.EXTRA_TEXT, t.url);
        try { startActivity(Intent.createChooser(share, "공유")); } catch (Exception ignored) {}
    }

    @Override
    public void onBackPressed() {
        if (tabSwitcherOverlay.getVisibility() == View.VISIBLE) {
            closeTabSwitcher();
            return;
        }
        if (findBar.getVisibility() == View.VISIBLE) {
            hideFindBar();
            return;
        }
        if (editingUrl) {
            exitUrlEdit();
            return;
        }
        Tab t = activeTab();
        if (t.webView != null && t.webView.canGoBack()) {
            t.webView.goBack();
            return;
        }
        if (!t.isStartPage()) {
            t.url = "";
            t.title = "시작 페이지";
            renderActiveTab();
            return;
        }
        super.onBackPressed();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        Ui.init(this, store.prefs());
        rebuildStartPage();
        updateChromeState();
    }

    // ---- Helpers ----
    private boolean isTablet() {
        return getResources().getConfiguration().smallestScreenWidthDp >= 600;
    }

    private TextView sectionHeader(String label, int color) {
        TextView tv = makeText(label, 17, Typeface.BOLD, color);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = Ui.dp(22);
        lp.bottomMargin = Ui.dp(10);
        tv.setLayoutParams(lp);
        return tv;
    }

    private TextView makeText(String text, float sp, int style, int color) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        tv.setTypeface(Typeface.create("sans-serif", style));
        tv.setTextColor(color);
        return tv;
    }

    private GradientDrawable roundBg(int color, int radiusPx) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(radiusPx);
        return d;
    }

    private static String initialOf(String title) {
        if (title == null || title.isEmpty()) return "J";
        return title.substring(0, 1).toUpperCase();
    }

    /** Floating Safari Liquid Glass container backed by Prism's Glass.Surface. */
    private static final class GlassBarHost extends FrameLayout {
        private final Glass.Backdrop backdrop;
        private final Glass.Surface surface = new Glass.Surface();
        private boolean privateTint;

        GlassBarHost(Context c, Glass.Backdrop backdrop) {
            super(c);
            this.backdrop = backdrop;
            setWillNotDraw(false);
            surface.blurDp = 24f;
            surface.refractDp = 16f;
            surface.edgeDp = 18f;
            surface.shadowAlpha = 0.16f;
        }

        void setPrivateTint(boolean priv) {
            this.privateTint = priv;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            int l = getPaddingLeft();
            int t = getPaddingTop();
            int r = getWidth() - getPaddingRight();
            int b = getHeight() - getPaddingBottom();
            if (r > l && b > t) {
                surface.a.set(l, t, r, b);
                surface.ra = Ui.dpf(28f);
                surface.tint = privateTint ? 0xD8202230 : (Ui.dark ? 0xC81C202B : 0xD8FFFFFF);
                surface.strokeColor = Ui.dark || privateTint ? 0x2AFFFFFF : 0x44FFFFFF;
                surface.draw(canvas, this, backdrop);
            }
            super.onDraw(canvas);
        }
    }
}
