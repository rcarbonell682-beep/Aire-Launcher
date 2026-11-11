package com.aire.launcher;

import android.app.WallpaperColors;
import android.app.WallpaperManager;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Color;
import android.os.Build;

/** Colores adaptativos calculados desde el fondo de pantalla, con contraste garantizado. */
final class Palette {
    int text, textDim, accent, plate, surface, scrim;
    float homeDim, drawerDim;
    boolean light;

    static Palette from(Context c) {
        Palette p = new Palette();
        int primary = 0xFF20242B;
        int secondary = 0xFF8FA3B8;
        boolean have = false;
        if (Build.VERSION.SDK_INT >= 27) {
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
        p.light = have ? lum(primary) > 0.35 : !night;

        int textC = p.light ? 0xFF111317 : 0xFFF4F5F7;
        int base = p.light ? mix(0xFFFFFFFF, primary, 0.12f) : mix(0xFF000000, primary, 0.15f);

        float a = 0.30f;
        while (a < 0.70f && contrast(textC, mix(primary, base, a)) < 4.5) a += 0.08f;
        p.homeDim = a;
        p.drawerDim = Math.min(0.92f, Math.max(0.80f, a + 0.20f));

        int bgHome = mix(primary, base, a);
        int acc = secondary;
        for (int i = 1; i <= 10 && contrast(acc, bgHome) < 3.0; i++) {
            acc = mix(secondary, textC, i / 10f);
        }

        p.text = textC;
        p.textDim = alpha(textC, 0xB8);
        p.accent = acc;
        p.plate = alpha(textC, 0x26);
        p.scrim = base | 0xFF000000;
        p.surface = alpha(mix(base, primary, 0.08f), 0xE6);
        return p;
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
