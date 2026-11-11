package com.aire.launcher;

import android.content.ComponentName;
import android.content.pm.ApplicationInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.AdaptiveIconDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;

import java.text.Normalizer;
import java.util.Locale;

final class AppInfo {
    static final String[] NAMES = {"Social", "Multimedia", "Juegos", "Herramientas", "Sistema", "Otras"};

    private static final String[] GAMES = {"game", "games"};
    private static final String[] SOCIAL = {"whatsapp", "telegram", "facebook", "instagram", "twitter",
            "snapchat", "discord", "messenger", "signal", "tiktok", "reddit", "linkedin",
            "dialer", "contacts", "mms", "messaging"};
    private static final String[] MEDIA = {"camera", "gallery", "photos", "music", "video", "youtube",
            "spotify", "vlc", "player", "netflix", "podcast", "radio", "recorder"};
    private static final String[] TOOLS = {"chrome", "browser", "firefox", "opera", "brave", "mail",
            "calendar", "clock", "calculator", "notes", "files", "filemanager", "documents", "maps",
            "translate", "pdf", "drive", "docs", "office"};

    final ComponentName cn;
    final String id, pkg, label, key;
    final int cat;
    final boolean system;
    Bitmap icon;

    AppInfo(ComponentName cn, String label, ApplicationInfo ai) {
        this.cn = cn;
        this.id = cn.flattenToShortString();
        this.pkg = cn.getPackageName();
        this.label = label;
        this.key = norm(label);
        this.system = (ai.flags & (ApplicationInfo.FLAG_SYSTEM | ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)) != 0;
        this.cat = classify(ai, pkg, system);
    }

    static String norm(String s) {
        return Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}+", "").toLowerCase(Locale.ROOT);
    }

    private static boolean has(String p, String[] words) {
        for (String w : words) if (p.contains(w)) return true;
        return false;
    }

    private static int classify(ApplicationInfo ai, String pkg, boolean system) {
        switch (ai.category) {
            case ApplicationInfo.CATEGORY_GAME:
                return 2;
            case ApplicationInfo.CATEGORY_SOCIAL:
                return 0;
            case ApplicationInfo.CATEGORY_AUDIO:
            case ApplicationInfo.CATEGORY_VIDEO:
            case ApplicationInfo.CATEGORY_IMAGE:
                return 1;
            case ApplicationInfo.CATEGORY_NEWS:
            case ApplicationInfo.CATEGORY_MAPS:
            case ApplicationInfo.CATEGORY_PRODUCTIVITY:
                return 3;
            default:
                break;
        }
        if ((ai.flags & ApplicationInfo.FLAG_IS_GAME) != 0) return 2;
        String p = pkg.toLowerCase(Locale.ROOT);
        if (has(p, GAMES)) return 2;
        if (has(p, SOCIAL)) return 0;
        if (has(p, MEDIA)) return 1;
        if (has(p, TOOLS)) return 3;
        return system ? 4 : 5;
    }
}

/** Dibuja cada ícono como círculo uniforme; usa la capa monocroma (Android 13+) teñida con el color del tema. */
final class Icons {
    static Bitmap render(Drawable d, int s, Palette p) {
        if (d == null) return null;
        Bitmap bmp = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        Path circle = new Path();
        circle.addCircle(s / 2f, s / 2f, s / 2f, Path.Direction.CW);
        c.clipPath(circle);
        int pad = Math.round(s * 0.25f);

        if (d instanceof AdaptiveIconDrawable) {
            AdaptiveIconDrawable ad = (AdaptiveIconDrawable) d;
            Drawable mono = Build.VERSION.SDK_INT >= 33 ? ad.getMonochrome() : null;
            if (mono != null) {
                paint.setColor(p.plate);
                c.drawPaint(paint);
                Drawable m = mono.mutate();
                m.setBounds(-pad, -pad, s + pad, s + pad);
                m.setColorFilter(new PorterDuffColorFilter(p.text, PorterDuff.Mode.SRC_IN));
                m.draw(c);
            } else {
                Drawable bg = ad.getBackground();
                Drawable fg = ad.getForeground();
                if (bg != null) {
                    bg.setBounds(-pad, -pad, s + pad, s + pad);
                    bg.draw(c);
                }
                if (fg != null) {
                    fg.setBounds(-pad, -pad, s + pad, s + pad);
                    fg.draw(c);
                }
            }
        } else {
            paint.setColor(p.plate);
            c.drawPaint(paint);
            int in = Math.round(s * 0.17f);
            d.setBounds(in, in, s - in, s - in);
            d.draw(c);
        }
        return bmp;
    }
          }
