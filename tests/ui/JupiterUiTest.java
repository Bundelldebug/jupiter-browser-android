package com.oneuibook.jupiter;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Path;
import android.graphics.drawable.AdaptiveIconDrawable;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.ViewGroup;

import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import java.io.File;
import java.io.FileOutputStream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = 34)
public class JupiterUiTest {
    private static File outDir;

    @BeforeClass
    public static void setupOut() {
        outDir = new File(System.getProperty("jupiter.output", "build/ui-out"));
        outDir.mkdirs();
    }

    @Test
    @Config(qualifiers = "w412dp-h915dp-xxhdpi")
    public void renderPhoneStartPageAndIcon() throws Exception {
        ActivityController<MainActivity> ctl = Robolectric.buildActivity(MainActivity.class).setup();
        MainActivity act = ctl.get();
        assertNotNull(act);
        assertEquals("9.5.0", MainActivity.VERSION);

        // Squircle icon render
        Drawable icon = act.getDrawable(R.mipmap.ic_launcher);
        assertNotNull(icon);
        saveSquircleIcon(icon, new File(outDir, "icon.png"));

        // Phone Safari Start Page
        View root = act.findViewById(android.R.id.content);
        layoutAndSave(root, 1080, 2400, new File(outDir, "phone-start.png"));

        // Tab Switcher overlay
        act.openTabSwitcher();
        layoutAndSave(root, 1080, 2400, new File(outDir, "phone-tabs.png"));
        act.closeTabSwitcher();
    }

    @Test
    @Config(qualifiers = "w1024dp-h720dp-xhdpi")
    public void renderTabletStartPage() throws Exception {
        ActivityController<MainActivity> ctl = Robolectric.buildActivity(MainActivity.class).setup();
        MainActivity act = ctl.get();
        View root = act.findViewById(android.R.id.content);
        layoutAndSave(root, 2048, 1440, new File(outDir, "tablet-start.png"));
    }

    @Test
    public void urlResolutionAndContentBlocker() {
        ActivityController<MainActivity> ctl = Robolectric.buildActivity(MainActivity.class).setup();
        BrowserStore store = ctl.get().store;

        assertEquals("https://example.com", store.resolveInput("example.com"));
        assertEquals("https://github.com/Bundelldebug", store.resolveInput("https://github.com/Bundelldebug"));
        assertTrue(store.resolveInput("삼성 갤럭시").startsWith("https://www.google.com/search?q="));
        assertEquals("github.com", BrowserStore.displayHost("https://www.github.com/Bundelldebug"));

        assertTrue(BrowserStore.isTrackerHost("https://pagead2.googlesyndication.com/pagead/js/adsbygoogle.js"));
        assertTrue(BrowserStore.isTrackerHost("https://www.google-analytics.com/analytics.js"));
        assertFalse(BrowserStore.isTrackerHost("https://github.com/Bundelldebug"));

        assertTrue(Updater.newer("9.5.1", "9.5.0"));
        assertFalse(Updater.newer("9.5.0", "9.5.0"));
    }

    private static void layoutAndSave(View root, int w, int h, File out) throws Exception {
        root.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY));
        root.layout(0, 0, w, h);
        Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        root.draw(c);
        try (FileOutputStream fos = new FileOutputStream(out)) {
            bmp.compress(Bitmap.CompressFormat.PNG, 92, fos);
        }
        bmp.recycle();
    }

    private static void saveSquircleIcon(Drawable d, File out) throws Exception {
        int s = 512;
        Bitmap bmp = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        Path squircle = new Path();
        double n = 2.76;
        float half = s / 2f;
        float rad = s * 0.46f;
        int steps = 180;
        for (int i = 0; i <= steps; i++) {
            double t = 2.0 * Math.PI * i / steps;
            double ct = Math.cos(t), st = Math.sin(t);
            float x = half + (float) (Math.signum(ct) * Math.pow(Math.abs(ct), 2.0 / n) * rad);
            float y = half + (float) (Math.signum(st) * Math.pow(Math.abs(st), 2.0 / n) * rad);
            if (i == 0) squircle.moveTo(x, y);
            else squircle.lineTo(x, y);
        }
        squircle.close();
        c.clipPath(squircle);
        if (d instanceof AdaptiveIconDrawable) {
            AdaptiveIconDrawable aid = (AdaptiveIconDrawable) d;
            int pad = (int) (s * 0.16f);
            if (aid.getBackground() != null) {
                aid.getBackground().setBounds(-pad, -pad, s + pad, s + pad);
                aid.getBackground().draw(c);
            }
            if (aid.getForeground() != null) {
                aid.getForeground().setBounds(-pad, -pad, s + pad, s + pad);
                aid.getForeground().draw(c);
            }
        } else {
            d.setBounds(0, 0, s, s);
            d.draw(c);
        }
        try (FileOutputStream fos = new FileOutputStream(out)) {
            bmp.compress(Bitmap.CompressFormat.PNG, 95, fos);
        }
        bmp.recycle();
    }
}
