package com.oneuibook.jupiter;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Persistent bookmarks, favorites, reading list, history, content blocker & Safari-style settings. */
final class BrowserStore {
    static final class Link {
        String title;
        String url;
        int color;
        long time;

        Link(String title, String url, int color, long time) {
            this.title = title;
            this.url = url;
            this.color = color;
            this.time = time;
        }
    }

    private static final String PREF = "jupiter_browser";
    private static final String K_FAV = "favorites";
    private static final String K_BM = "bookmarks";
    private static final String K_READ = "reading_list";
    private static final String K_HIST = "history";
    private static final String K_BLOCK = "content_blocker";
    private static final String K_BLOCK_COUNT = "blocked_count";
    private static final String K_ENGINE = "search_engine";
    private static final String K_DARK_WEB = "dark_web";
    private static final String K_ZOOM = "page_zoom";

    private static final Set<String> TRACKER_HOSTS = new HashSet<>(Arrays.asList(
            "doubleclick.net", "googlesyndication.com", "googleadservices.com",
            "google-analytics.com", "googletagmanager.com", "adservice.google.com",
            "facebook.net", "connect.facebook.net", "analytics.tiktok.com",
            "ads.twitter.com", "taboola.com", "outbrain.com", "criteo.com",
            "scorecardresearch.com", "quantserve.com", "hotjar.com", "mixpanel.com",
            "amplitude.com", "adnxs.com", "pubmatic.com", "rubiconproject.com",
            "t1.daumcdn.net", "adcr.naver.com", "siape.veta.naver.com"
    ));

    private final SharedPreferences prefs;

    BrowserStore(Context ctx) {
        this.prefs = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }

    SharedPreferences prefs() { return prefs; }

    boolean contentBlocker() { return prefs.getBoolean(K_BLOCK, true); }
    void setContentBlocker(boolean on) { prefs.edit().putBoolean(K_BLOCK, on).apply(); }

    int blockedCount() { return prefs.getInt(K_BLOCK_COUNT, 0); }
    void recordBlocked() { prefs.edit().putInt(K_BLOCK_COUNT, blockedCount() + 1).apply(); }

    boolean darkWeb() { return prefs.getBoolean(K_DARK_WEB, false); }
    void setDarkWeb(boolean on) { prefs.edit().putBoolean(K_DARK_WEB, on).apply(); }

    int pageZoom() { return Math.max(50, Math.min(200, prefs.getInt(K_ZOOM, 100))); }
    void setPageZoom(int pct) { prefs.edit().putInt(K_ZOOM, Math.max(50, Math.min(200, pct))).apply(); }

    /** 0 = Google, 1 = Naver, 2 = DuckDuckGo */
    int searchEngine() { return prefs.getInt(K_ENGINE, 0); }
    void setSearchEngine(int idx) { prefs.edit().putInt(K_ENGINE, Math.max(0, Math.min(2, idx))).apply(); }
    String searchEngineName() {
        switch (searchEngine()) {
            case 1: return "네이버";
            case 2: return "DuckDuckGo";
            default: return "Google";
        }
    }

    static boolean isTrackerHost(String url) {
        if (url == null || url.isEmpty()) return false;
        try {
            String host = Uri.parse(url).getHost();
            if (host == null) return false;
            host = host.toLowerCase(Locale.ROOT);
            for (String t : TRACKER_HOSTS) {
                if (host.equals(t) || host.endsWith("." + t)) return true;
            }
        } catch (Exception ignored) {}
        return false;
    }

    String resolveInput(String raw) {
        String s = raw == null ? "" : raw.trim();
        if (s.isEmpty()) return "";
        if (s.startsWith("http://") || s.startsWith("https://") || s.startsWith("about:")) return s;
        boolean hasSpace = s.indexOf(' ') >= 0;
        boolean looksLikeHost = !hasSpace && s.contains(".") && !s.startsWith(".") && !s.endsWith(".");
        if (looksLikeHost) return "https://" + s;
        try {
            String q = URLEncoder.encode(s, "UTF-8");
            switch (searchEngine()) {
                case 1: return "https://search.naver.com/search.naver?query=" + q;
                case 2: return "https://duckduckgo.com/?q=" + q;
                default: return "https://www.google.com/search?q=" + q;
            }
        } catch (Exception e) {
            return "https://www.google.com/search?q=" + s;
        }
    }

    static String displayHost(String url) {
        if (url == null || url.isEmpty() || url.startsWith("about:")) return "검색 또는 웹사이트 입력";
        try {
            Uri u = Uri.parse(url);
            String host = u.getHost();
            if (host == null || host.isEmpty()) return url;
            return host.startsWith("www.") ? host.substring(4) : host;
        } catch (Exception e) {
            return url;
        }
    }

    List<Link> favorites() {
        List<Link> list = readList(K_FAV);
        if (!list.isEmpty()) return list;
        list.add(new Link("Google", "https://www.google.com", 0xFF4285F4, 0));
        list.add(new Link("네이버", "https://m.naver.com", 0xFF03C75A, 0));
        list.add(new Link("YouTube", "https://m.youtube.com", 0xFFFF0033, 0));
        list.add(new Link("GitHub", "https://github.com", 0xFF24292F, 0));
        list.add(new Link("위키백과", "https://ko.m.wikipedia.org", 0xFF54595D, 0));
        list.add(new Link("나무위키", "https://namu.wiki", 0xFF00A495, 0));
        return list;
    }

    List<Link> bookmarks() { return readList(K_BM); }
    List<Link> readingList() { return readList(K_READ); }
    List<Link> history() { return readList(K_HIST); }

    void addBookmark(String title, String url) { addUnique(K_BM, title, url, 0xFF0E68EE, 200); }
    void removeBookmark(String url) { removeByUrl(K_BM, url); }
    boolean isBookmarked(String url) {
        for (Link l : bookmarks()) if (l.url.equals(url)) return true;
        return false;
    }

    void addReading(String title, String url) { addUnique(K_READ, title, url, 0xFF34C759, 100); }
    void removeReading(String url) { removeByUrl(K_READ, url); }

    void recordHistory(String title, String url) {
        if (url == null || url.isEmpty() || url.startsWith("about:")) return;
        addUnique(K_HIST, title == null || title.isEmpty() ? displayHost(url) : title, url, 0xFF64748B, 120);
    }

    void clearHistory() { prefs.edit().remove(K_HIST).apply(); }

    private void addUnique(String key, String title, String url, int color, int max) {
        if (url == null || url.isEmpty()) return;
        List<Link> list = readList(key);
        for (int i = list.size() - 1; i >= 0; i--) {
            if (list.get(i).url.equals(url)) list.remove(i);
        }
        String t = (title == null || title.trim().isEmpty()) ? displayHost(url) : title.trim();
        list.add(0, new Link(t, url, color, System.currentTimeMillis()));
        while (list.size() > max) list.remove(list.size() - 1);
        writeList(key, list);
    }

    private void removeByUrl(String key, String url) {
        List<Link> list = readList(key);
        for (int i = list.size() - 1; i >= 0; i--) {
            if (list.get(i).url.equals(url)) list.remove(i);
        }
        writeList(key, list);
    }

    private List<Link> readList(String key) {
        List<Link> out = new ArrayList<>();
        String raw = prefs.getString(key, null);
        if (raw == null || raw.isEmpty()) return out;
        try {
            JSONArray arr = new JSONArray(raw);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.optJSONObject(i);
                if (o == null) continue;
                out.add(new Link(
                        o.optString("title"),
                        o.optString("url"),
                        o.optInt("color", 0xFF0E68EE),
                        o.optLong("time", 0)
                ));
            }
        } catch (Exception ignored) {}
        return out;
    }

    private void writeList(String key, List<Link> list) {
        JSONArray arr = new JSONArray();
        try {
            for (Link l : list) {
                JSONObject o = new JSONObject();
                o.put("title", l.title);
                o.put("url", l.url);
                o.put("color", l.color);
                o.put("time", l.time);
                arr.put(o);
            }
        } catch (Exception ignored) {}
        prefs.edit().putString(key, arr.toString()).apply();
    }
}
