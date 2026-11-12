package com.aire.launcher;

import android.app.WallpaperColors;
import android.app.WallpaperManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Color;
import android.os.Build;

/** Colores adaptativos (cristal) y paleta de papel en capas, con contraste garantizado. */
final class Palette {
    int text, textDim, accent, plate, surface, scrim;
    float homeDim, drawerDim;
    boolean light, paper;
    int canvas, card, cardLo, shadow;

    static Palette from(Context c) {
        SharedPreferences sp = Cfg.sp(c);
        Palette p = new Palette();
        p.paper = sp.getInt(Cfg.STYLE, 0) == 1;
        int bgm = sp.getInt(Cfg.BGMODE, 0);

        int primary = 0xFF20242B;
        int secondary = 0xFF8FA3B8;
        boolean have = false;
        if (p.paper) {
            primary = sp.getInt(Cfg.PBASE, 0xFFE8E2D6);
            secondary = primary;
            have = true;
        } else if (bgm == 2) {
            primary = sp.getInt(Cfg.BGCOLOR, 0xFF2B2F3A);
            secondary = lum(primary) > 0.35 ? mix(primary, 0xFF000000, 0.45f) : mix(primary, 0xFFFFFFFF, 0.55f);
            have = true;
        } else if (bgm == 1 && Cfg.bgFile(c).exists() && sp.contains(Cfg.BGP)) {
            primary = sp.getInt(Cfg.BGP, primary);
            secondary = sp.getInt(Cfg.BGS, primary);
            have = true;
        } else if (Build.VERSION.SDK_INT >= 27) {
            try {
                WallpaperColors wc = WallpaperManager.getInstance(c)
                        .getWallpaperColors(WallpaperManager.FLAG_SYSTEM);
                if (wc != null) {
                    primary = wc.getPrimaryColor().toArgb();
                    Color s = wc.getSecondaryColor();
                    secondary = s != null ? s.toArgb() : primary;
                    have = true;
                }
            } catch (Throwable ignored) {
            }
        }
        boolean night = (c.getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        boolean autoLight = have ? lum(primary) > 0.35 : !night;
        int theme = p.paper ? 0 : sp.getInt(Cfg.THEME, 0);
        p.light = theme == 1 ? true : theme == 2 ? false : autoLight;
        boolean mismatch = p.light != autoLight;

        int textC = p.paper ? (p.light ? 0xFF2A2623 : 0xFFECE7DF) : (p.light ? 0xFF111317 : 0xFFF4F5F7);
        int base = p.light ? mix(0xFFFFFFFF, primary, 0.12f) : mix(0xFF000000, primary, 0.15f);

        float a;
        if (p.paper) {
            a = 0f;
        } else {
            int hd = sp.getInt(Cfg.HOMEDIM, -1);
            float cap = mismatch ? 0.85f : 0.36f;
            a = 0.08f;
            if (hd >= 0) a = hd / 100f;
            else if (bgm == 2) a = 0f;
            else while (a < cap && contrast(textC, mix(primary, base, a)) < 3.0) a += 0.04f;
        }
        p.homeDim = a;
        int dd = sp.getInt(Cfg.DRAWERDIM, -1);
        p.drawerDim = p.paper ? 0f : dd >= 0 ? dd / 100f : Math.min(0.90f, Math.max(0.78f, a + 0.45f));

        int ai = sp.getInt(Cfg.ACCENT, 0);
        if (ai == 6) secondary = sp.getInt(Cfg.ACUSTOM, 0xFFFF6B4A);
        else if (ai > 0 && ai < Cfg.ACCENTS.length) secondary = Cfg.ACCENTS[ai];
        else if (p.paper) secondary = 0xFFFF6B4A;
        int acc = secondary;
        if (!p.paper) {
            int bgHome = mix(primary, base, a);
            for (int i = 1; i <= 10 && contrast(acc, bgHome) < 3.0; i++) acc = mix(secondary, textC, i / 10f);
        }

        p.text = textC;
        p.textDim = alpha(textC, 0xB8);
        p.accent = acc;
        p.plate = alpha(textC, p.paper ? 0x14 : 0x26);
        p.scrim = base | 0xFF000000;

        // paleta de papel
        p.canvas = primary;
        p.card = p.light ? mix(primary, 0xFFFFFFFF, 0.40f) : mix(primary, 0xFFFFFFFF, 0.07f);
        p.cardLo = mix(primary, 0xFF000000, p.light ? 0.13f : 0.38f);
        p.shadow = mix(primary, 0xFF000000, 0.72f);
        p.surface = p.paper ? (p.card | 0xFF000000) : alpha(mix(base, primary, 0.08f), 0xE6);
        return p;
    }

    int onAccent() {
        return contrast(0xFF111317, accent) > contrast(0xFFFFFFFF, accent) ? 0xFF111317 : 0xFFFFFFFF;
    }

    static int alpha(int c, int a) {
        return (a << 24) | (c & 0xFFFFFF);
    }

    static int mix(int a, int b, float t) {
        int r = Math.round(Color.red(a) * (1 - t) + Color.red(b) * t);
        int g = Math.round(Color.green(a) * (1 - t) + Color.green(b) * t);
        int bl = Math.round(Color.blue(a) * (1 - t) + Color.blue(b) * t);
        return 0xFF000000 | (r << 16) | (g << 8) | bl;
    }

    private static double lin(int v) {
        double c = v / 255.0;
        return c <= 0.03928 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4);
    }

    static double lum(int c) {
        return 0.2126 * lin(Color.red(c)) + 0.7152 * lin(Color.green(c)) + 0.0722 * lin(Color.blue(c));
    }

    static double contrast(int a, int b) {
        double x = lum(a), y = lum(b);
        return (Math.max(x, y) + 0.05) / (Math.min(x, y) + 0.05);
    }
}
