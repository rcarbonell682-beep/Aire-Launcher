package com.aire.launcher;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.text.TextPaint;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.ScrollView;

/** Celda de app dibujada a mano (ligera y fluida), con insignia opcional. */
final class AppCell extends View {
    static int badgeMode; // 0 no, 1 punto, 2 número

    private static final Paint ICON = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private static final TextPaint TEXT = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private static final Paint BADGE = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final TextPaint BTEXT = new TextPaint(Paint.ANTI_ALIAS_FLAG);

    private final AppInfo app;
    private final Palette pal;
    private final boolean label;
    private final int iconPx;
    private final float d, textSize;
    private final RectF dst = new RectF();
    private String shown;

    AppCell(Context c, AppInfo app, Palette pal, boolean label, int iconPx) {
        super(c);
        this.app = app;
        this.pal = pal;
        this.label = label;
        this.iconPx = iconPx;
        DisplayMetrics dm = c.getResources().getDisplayMetrics();
        this.d = dm.density;
        this.textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 11.5f, dm);
        setClickable(true);
        setContentDescription(app.label);
    }

    @Override
    protected void drawableStateChanged() {
        super.drawableStateChanged();
        invalidate();
    }

    @Override
    protected void onDraw(Canvas c) {
        float w = getWidth(), h = getHeight();
        boolean pr = isPressed();
        float k = pr ? 0.5f : 1f;
        boolean sq = pal.paper && pr;
        if (sq) {
            c.save();
            c.scale(0.93f, 0.93f, w / 2f, h / 2f);
            k = 0.92f;
        }
        float gap = 5 * d;
        float lh = label ? textSize * 1.2f + gap : 0;
        float top = (h - iconPx - lh) / 2f;
        float left = (w - iconPx) / 2f;
        dst.set(left, top, left + iconPx, top + iconPx);
        if (app.icon != null) {
            ICON.setAlpha((int) (255 * k));
            c.drawBitmap(app.icon, null, dst, ICON);
        } else {
            ICON.setColor(pal.plate);
            c.drawCircle(dst.centerX(), dst.centerY(), iconPx / 2f, ICON);
        }
        if (label) {
            TEXT.setTextSize(textSize);
            TEXT.setTextAlign(Paint.Align.CENTER);
            TEXT.setColor(pal.text);
            TEXT.setAlpha((int) (255 * k));
            if (shown == null) {
                shown = TextUtils.ellipsize(app.label, TEXT, w - 6 * d, TextUtils.TruncateAt.END).toString();
            }
            c.drawText(shown, w / 2f, top + iconPx + gap + textSize * 0.92f, TEXT);
        }
        int n = badgeMode > 0 ? Badges.get(app.pkg) : 0;
        if (n > 0) {
            float r = (badgeMode == 1 ? 5f : 8.5f) * d;
            float bx = dst.right - r * 0.5f, by = dst.top + r * 0.5f;
            BADGE.setColor(pal.accent);
            c.drawCircle(bx, by, r, BADGE);
            if (badgeMode == 2) {
                BTEXT.setTextSize((n > 9 ? 8f : 10f) * d);
                BTEXT.setTextAlign(Paint.Align.CENTER);
                BTEXT.setFakeBoldText(true);
                BTEXT.setColor(pal.onAccent());
                c.drawText(n > 99 ? "99+" : String.valueOf(n), bx, by + BTEXT.getTextSize() * 0.35f, BTEXT);
            }
        }
        if (sq) c.restore();
    }
}

/**
 * Grid de N columnas. Si el contenido cabe, se acomoda ABAJO (fila 1 abajo, las demás hacia arriba).
 * Si no cabe, se ordena de arriba hacia abajo y se ve el resto deslizando hacia arriba.
 * Con topOnly=true siempre va de arriba hacia abajo.
 */
final class ReverseGrid extends ViewGroup {
    private final int cols, cellH;
    private final boolean topOnly;

    ReverseGrid(Context c, int cols, int cellH, boolean topOnly) {
        super(c);
        this.cols = cols;
        this.cellH = cellH;
        this.topOnly = topOnly;
    }

    @Override
    protected void onMeasure(int wSpec, int hSpec) {
        int w = MeasureSpec.getSize(wSpec);
        int n = getChildCount();
        int rows = (n + cols - 1) / cols;
        int cellW = w / cols;
        for (int i = 0; i < n; i++) {
            getChildAt(i).measure(
                    MeasureSpec.makeMeasureSpec(cellW, MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(cellH, MeasureSpec.EXACTLY));
        }
        int need = rows * cellH;
        int h = (!topOnly && MeasureSpec.getMode(hSpec) == MeasureSpec.EXACTLY)
                ? Math.max(need, MeasureSpec.getSize(hSpec)) : need;
        setMeasuredDimension(w, h);
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        int h = b - t;
        int n = getChildCount();
        int rows = (n + cols - 1) / cols;
        boolean up = !topOnly && h > rows * cellH;
        int cellW = (r - l) / cols;
        for (int i = 0; i < n; i++) {
            int row = i / cols, col = i % cols;
            int top = up ? h - (row + 1) * cellH : row * cellH;
            int left = col * cellW;
            getChildAt(i).layout(left, top, left + cellW, top + cellH);
        }
    }
}

/** ScrollView que siempre arranca desde el principio al (re)construirse. */
final class BottomScroll extends ScrollView {
    private boolean stick = true;

    BottomScroll(Context c) {
        super(c);
    }

    void toStart() {
        stick = true;
        requestLayout();
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);
        if (stick) {
            stick = false;
            scrollTo(0, 0);
        }
    }
}

/** Puntos indicadores de página. */
final class Dots extends View {
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Palette pal;
    private final float d, gap;
    private int n;
    private float pos;

    Dots(Context c, Palette pal) {
        super(c);
        this.pal = pal;
        d = c.getResources().getDisplayMetrics().density;
        gap = 14 * d;
    }

    void setCount(int n) {
        this.n = n;
        requestLayout();
        invalidate();
    }

    void setPos(float pos) {
        this.pos = pos;
        invalidate();
    }

    @Override
    protected void onMeasure(int w, int h) {
        setMeasuredDimension((int) Math.ceil(Math.max(1, n) * gap + gap / 2), (int) (26 * d));
    }

    @Override
    protected void onDraw(Canvas c) {
        if (n < 2) return;
        float x = (getWidth() - (n - 1) * gap) / 2f;
        float cy = getHeight() / 2f;
        for (int i = 0; i < n; i++) {
            float t = Math.max(0f, 1f - Math.abs(i - pos));
            p.setColor(pal.paper ? Palette.mix(pal.text, pal.accent, t) : pal.text);
            p.setAlpha((int) (80 + 175 * t));
            c.drawCircle(x + i * gap, cy, (2.4f + 1.4f * t) * d, p);
        }
    }
}

/** Utilidades del estilo Papel: tarjetas con luz, sombras direccionales y botones presionables. */
final class Paper {
    static GradientDrawable card(Palette p, float r) {
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{Palette.mix(p.card, 0xFFFFFFFF, 0.28f), p.card, Palette.mix(p.card, p.cardLo, 0.30f)});
        g.setCornerRadius(r);
        return g;
    }

    static void raise(View v, Palette p, float elev, final float r) {
        v.setElevation(elev);
        v.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline o) {
                o.setRoundRect(0, 0, view.getWidth(), view.getHeight(), r);
                o.setAlpha(1f);
            }
        });
        if (Build.VERSION.SDK_INT >= 28) {
            v.setOutlineAmbientShadowColor(p.shadow);
            v.setOutlineSpotShadowColor(p.shadow);
        }
    }

    /** Al pulsar, la sombra se reduce y el botón se hunde. */
    static void pressable(final View v, final float elev) {
        v.setOnTouchListener((x, e) -> {
            int a = e.getActionMasked();
            if (a == MotionEvent.ACTION_DOWN) {
                x.setElevation(elev * 0.25f);
                x.setScaleX(0.97f);
                x.setScaleY(0.97f);
            } else if (a == MotionEvent.ACTION_UP || a == MotionEvent.ACTION_CANCEL) {
                x.setElevation(elev);
                x.setScaleX(1f);
                x.setScaleY(1f);
            }
            return false;
        });
    }
}

/** Lienzo del estilo Papel: capas onduladas de cartulina con sombra entre ellas. */
final class PaperArt {
    static Bitmap backdrop(int w, int h, Palette p) {
        int bw = Math.max(2, w / 2), bh = Math.max(2, h / 2);
        Bitmap b = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        c.drawColor(p.canvas);
        float[] base = {0.60f, 0.72f, 0.84f};
        float[] amp = {0.045f, 0.040f, 0.030f};
        float[] tone = {0.40f, 0.70f, 1.00f};
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        for (int i = 0; i < 3; i++) {
            Path path = wave(bw, bh, base[i] * bh, amp[i] * bh, i * 1.9f);
            paint.setColor(Palette.alpha(p.shadow, 0x70));
            paint.setMaskFilter(new BlurMaskFilter(bw * 0.025f, BlurMaskFilter.Blur.NORMAL));
            c.save();
            c.translate(0, -bh * 0.006f);
            c.drawPath(path, paint);
            c.restore();
            paint.setMaskFilter(null);
            paint.setColor(Palette.mix(p.canvas, p.cardLo, tone[i]));
            c.drawPath(path, paint);
        }
        return b;
    }

    private static Path wave(int bw, int bh, float y0, float amp, float ph) {
        Path path = new Path();
        path.moveTo(0, bh);
        int steps = 48;
        for (int i = 0; i <= steps; i++) {
            float x = bw * i / (float) steps;
            double t = x / bw * 2 * Math.PI;
            float y = (float) (y0 + amp * Math.sin(t * 1.3 + ph) + amp * 0.5 * Math.sin(t * 3.1 + ph * 1.7));
            path.lineTo(x, y);
        }
        path.lineTo(bw, bh);
        path.close();
        return path;
    }
}
