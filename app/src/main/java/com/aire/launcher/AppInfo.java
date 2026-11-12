package com.aire.launcher;

import android.content.ComponentName;
import android.content.pm.ApplicationInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.PorterDuffXfermode;
import android.graphics.RectF;
import android.graphics.drawable.AdaptiveIconDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;

import java.text.Normalizer;
import java.util.Locale;

final class AppInfo {
    static final int CATS = 6;

    static String catName(int i) {
        switch (i) {
            case 0:
                return "Social";
            case 1:
                return L.t("Multimedia", "Media");
            case 2:
                return L.t("Juegos", "Games");
            case 3:
                return L.t("Herramientas", "Tools");
            case 4:
                return L.t("Sistema", "System");
            default:
                return L.t("Otras", "Other");
        }
    }

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
 * Íconos con forma elegible. Modo temático (forzado): capa monocroma si existe; si no, silueta con
 * detalle de luminancia; y si el ícono es una imagen completa, un duotono con los tonos del tema.
 */
final class Icons {

    static Bitmap render(Drawable d, int s, Palette p, boolean themed, int shape) {
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
                    return cut(out, shape);
                }
            }
            Bitmap t = themedBitmap(ad, d, s, pad, p);
            if (t != null) {
                paint.setColor(p.plate);
                c.drawPaint(paint);
                c.drawBitmap(t, 0, 0, null);
                t.recycle();
                return cut(out, shape);
            }
        }

        // Originales (o caso degenerado)
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
        return cut(out, shape);
    }

    private static Path shapePath(int shape, int s) {
        Path path = new Path();
        RectF r = new RectF(0, 0, s, s);
        switch (shape) {
            case 1: // squircle
                path.addRoundRect(r, s * 0.36f, s * 0.36f, Path.Direction.CW);
                break;
            case 2: // cuadrado redondeado
                path.addRoundRect(r, s * 0.20f, s * 0.20f, Path.Direction.CW);
                break;
            case 3: { // gota
                float big = s * 0.5f, small = s * 0.12f;
                path.addRoundRect(r, new float[]{big, big, big, big, big, big, small, small},
                        Path.Direction.CW);
                break;
            }
            default: // círculo
                path.addCircle(s / 2f, s / 2f, s / 2f, Path.Direction.CW);
                break;
        }
        return path;
    }

    /** Borra todo lo que queda fuera de la forma (con borde suave). */
    private static Bitmap cut(Bitmap b, int shape) {
        Path path = shapePath(shape, b.getWidth());
        path.setFillType(Path.FillType.INVERSE_WINDING);
        Paint m = new Paint(Paint.ANTI_ALIAS_FLAG);
        m.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
        new Canvas(b).drawPath(path, m);
        return b;
    }

    private static float luma(int v) {
        return (0.299f * ((v >> 16) & 255) + 0.587f * ((v >> 8) & 255) + 0.114f * (v & 255)) / 255f;
    }

    private static Bitmap themedBitmap(AdaptiveIconDrawable ad, Drawable d, int s, int pad, Palette p) {
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
        int inside = 0, opaque = 0, nL = 0;
        float minL = 1f, maxL = 0f, sumL = 0f;
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
                if (a > 128) {
                    float l = luma(v);
                    if (l < minL) minL = l;
                    if (l > maxL) maxL = l;
                    sumL += l;
                    nL++;
                }
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
        if (inside == 0 || nL == 0) return null;

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

        float range = maxL - minL;
        boolean invertL = (sumL / nL) < 0.45f;
        int rgb = p.text & 0xFFFFFF;
        int on = 0;
        int[] res = new int[px.length];
        for (int i = 0; i < px.length; i++) {
            int v = px[i];
            int a = v >>> 24;
            if (a == 0) continue;
            float m;
            if (!full) {
                // silueta que conserva el detalle interior del logo
                float w = 1f;
                if (range > 0.12f) {
                    w = (luma(v) - minL) / range;
                    if (invertL) w = 1f - w;
                }
                m = (0.40f + 0.60f * w) * (a / 255f);
            } else {
                // glifo separado del fondo por diferencia de color
                int dd = Math.abs(((v >> 16) & 255) - bgR) + Math.abs(((v >> 8) & 255) - bgG)
                        + Math.abs((v & 255) - bgB);
                m = Math.min(1f, Math.max(0f, (dd / 765f - 0.07f) / 0.22f)) * (a / 255f);
            }
            int aa = Math.round(m * 255f);
            if (aa > 40) on++;
            res[i] = (aa << 24) | rgb;
        }
        float cov = on / (float) inside;
        if (cov >= 0.02f && (!full || cov <= 0.60f)) {
            return Bitmap.createBitmap(res, s, s, Bitmap.Config.ARGB_8888);
        }
        return duotone(px, s, minL, maxL, p);
    }

    /** Imágenes completas (p. ej. juegos): la foto en tonos del tema. */
    private static Bitmap duotone(int[] px, int s, float minL, float maxL, Palette p) {
        float range = Math.max(0.08f, maxL - minL);
        int[] res = new int[px.length];
        for (int i = 0; i < px.length; i++) {
            int v = px[i];
            int a = v >>> 24;
            if (a == 0) continue;
            float t = (luma(v) - minL) / range;
            t = Math.max(0f, Math.min(1f, t));
            if (p.light) t = 1f - t;
            int col = Palette.mix(p.scrim, p.text, t);
            res[i] = (a << 24) | (col & 0xFFFFFF);
        }
        return Bitmap.createBitmap(res, s, s, Bitmap.Config.ARGB_8888);
    }
}
