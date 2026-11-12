package com.aire.launcher;

import android.content.ComponentName;
import android.content.pm.ApplicationInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.PorterDuffXfermode;
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

/**
 * Íconos circulares uniformes.
 * Modo temático (forzado): capa monocroma si existe; si no, silueta extraída del propio ícono
 * y teñida con el color del tema. Solo si la silueta no sirve se usa el ícono original.
 */
final class Icons {

    static Bitmap render(Drawable d, int s, Palette p, boolean themed) {
        if (d == null) return null;
        Bitmap out = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(out);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        int pad = Math.round(s * 0.25f);
        AdaptiveIconDrawable ad = d instanceof AdaptiveIconDrawable ? (AdaptiveIconDrawable) d : null;

        if (themed) {
            if (ad != null && Build.VERSION.SDK_INT >= 33) {
                Drawable mono = ad.getMonochrome();
                if (mono != null) {
                    paint.setColor(p.plate);
                    c.drawPaint(paint);
                    Drawable m = mono.mutate();
                    m.setBounds(-pad, -pad, s + pad, s + pad);
                    m.setColorFilter(new PorterDuffColorFilter(p.text, PorterDuff.Mode.SRC_IN));
                    m.draw(c);
                    return circle(out);
                }
            }
            Bitmap mask = silhouette(ad, d, s, pad, p);
            if (mask != null) {
                paint.setColor(p.plate);
                c.drawPaint(paint);
                c.drawBitmap(mask, 0, 0, null);
                mask.recycle();
                return circle(out);
            }
        }

        // Original (modo "originales" o silueta inservible)
        if (ad != null) {
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
        } else {
            paint.setColor(p.plate);
            c.drawPaint(paint);
            int in = Math.round(s * 0.17f);
            d.setBounds(in, in, s - in, s - in);
            d.draw(c);
        }
        return circle(out);
    }

    /** Recorta el bitmap en círculo con borde suavizado. */
    private static Bitmap circle(Bitmap b) {
        Canvas c = new Canvas(b);
        Paint m = new Paint(Paint.ANTI_ALIAS_FLAG);
        m.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_IN));
        int s = b.getWidth();
        c.drawCircle(s / 2f, s / 2f, s / 2f, m);
        return b;
    }

    /** Silueta del ícono con el color del tema, o null si no se puede obtener algo útil. */
    private static Bitmap silhouette(AdaptiveIconDrawable ad, Drawable d, int s, int pad, Palette p) {
        Bitmap src = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888);
        Canvas sc = new Canvas(src);
        if (ad != null) {
            Drawable fg = ad.getForeground();
            if (fg == null) {
                src.recycle();
                return null;
            }
            fg.setBounds(-pad, -pad, s + pad, s + pad);
            fg.draw(sc);
        } else {
            int in = Math.round(s * 0.08f);
            d.setBounds(in, in, s - in, s - in);
            d.draw(sc);
        }
        int[] px = new int[s * s];
        src.getPixels(px, 0, s, 0, 0, s, s);
        src.recycle();

        float cx = s / 2f;
        float r2 = cx * cx;
        float ringIn = (cx * 0.76f) * (cx * 0.76f);
        int inside = 0, opaque = 0;
        int[] cnt = new int[4096], rs = new int[4096], gs = new int[4096], bs = new int[4096];

        for (int y = 0; y < s; y++) {
            for (int x = 0; x < s; x++) {
                float dx = x + 0.5f - cx, dy = y + 0.5f - cx;
                float d2 = dx * dx + dy * dy;
                if (d2 > r2) continue;
                inside++;
                int v = px[y * s + x];
                int a = v >>> 24;
                if (a > 48) opaque++;
                if (d2 >= ringIn && a > 200) {
                    int R = (v >> 16) & 255, G = (v >> 8) & 255, B = v & 255;
                    int k = ((R >> 4) << 8) | ((G >> 4) << 4) | (B >> 4);
                    cnt[k]++;
                    rs[k] += R;
                    gs[k] += G;
                    bs[k] += B;
                }
            }
        }
        if (inside == 0) return null;

        // Ícono "a sangre" (fondo opaco): el glifo se separa por diferencia con el color de fondo.
        boolean full = opaque > inside * 0.80f;
        int bgR = 0, bgG = 0, bgB = 0;
        if (full) {
            int best = 0, bc = 0;
            for (int k = 0; k < 4096; k++) {
                if (cnt[k] > bc) {
                    bc = cnt[k];
                    best = k;
                }
            }
            if (bc == 0) return null;
            bgR = rs[best] / bc;
            bgG = gs[best] / bc;
            bgB = bs[best] / bc;
        }

        int rgb = p.text & 0xFFFFFF;
        int on = 0;
        for (int i = 0; i < px.length; i++) {
            int v = px[i];
            int a = v >>> 24;
            if (a == 0) {
                px[i] = 0;
                continue;
            }
            float m;
            if (!full) {
                m = a / 255f;
            } else {
                int dd = Math.abs(((v >> 16) & 255) - bgR) + Math.abs(((v >> 8) & 255) - bgG)
                        + Math.abs((v & 255) - bgB);
                m = Math.min(1f, Math.max(0f, (dd / 765f - 0.07f) / 0.22f)) * (a / 255f);
            }
            int aa = Math.round(m * 255f);
            if (aa > 40) on++;
            px[i] = (aa << 24) | rgb;
        }
        float cov = on / (float) inside;
        if (cov < 0.02f || cov > 0.92f) return null;
        return Bitmap.createBitmap(px, s, s, Bitmap.Config.ARGB_8888);
    }
        }
