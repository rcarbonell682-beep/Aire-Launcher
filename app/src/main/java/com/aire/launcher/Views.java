package com.aire.launcher;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.text.TextPaint;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.HorizontalScrollView;
import android.widget.ScrollView;

/** Celda de app dibujada a mano (ligera y fluida). */
final class AppCell extends View {
    private static final Paint ICON = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private static final TextPaint TEXT = new TextPaint(Paint.ANTI_ALIAS_FLAG);

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
        float k = isPressed() ? 0.5f : 1f;
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
    }
}

/** Grid de N columnas con la fila 0 ABAJO y las demás hacia arriba. */
final class ReverseGrid extends ViewGroup {
    private final int cols, cellH;

    ReverseGrid(Context c, int cols, int cellH) {
        super(c);
        this.cols = cols;
        this.cellH = cellH;
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
        int h = MeasureSpec.getMode(hSpec) == MeasureSpec.EXACTLY
                ? Math.max(need, MeasureSpec.getSize(hSpec)) : need;
        setMeasuredDimension(w, h);
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        int h = b - t;
        int cellW = (r - l) / cols;
        for (int i = 0; i < getChildCount(); i++) {
            int row = i / cols, col = i % cols;
            int bottom = h - row * cellH;
            int left = col * cellW;
            getChildAt(i).layout(left, bottom - cellH, left + cellW, bottom);
        }
    }
}

/** ScrollView que arranca (y se reinicia) con el contenido pegado abajo. */
final class BottomScroll extends ScrollView {
    private boolean stick = true;

    BottomScroll(Context c) {
        super(c);
    }

    void toBottom() {
        stick = true;
        requestLayout();
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);
        if (stick) {
            stick = false;
            scrollTo(0, 1000000);
        }
    }
}

/** Scroll horizontal por páginas con imán. */
final class SnapPager extends HorizontalScrollView {
    interface Listener {
        void onPage(float pos);
    }

    private Listener listener;
    private int startPage;
    private boolean flung;

    SnapPager(Context c) {
        super(c);
        setHorizontalScrollBarEnabled(false);
        setOverScrollMode(OVER_SCROLL_NEVER);
        setFillViewport(true);
    }

    void setListener(Listener l) {
        listener = l;
    }

    int cur() {
        int w = getWidth();
        return w == 0 ? 0 : Math.round(getScrollX() / (float) w);
    }

    private int count() {
        View c = getChildAt(0);
        return c instanceof ViewGroup ? ((ViewGroup) c).getChildCount() : 0;
    }

    void snap(int p) {
        int n = count();
        p = Math.max(0, Math.min(Math.max(0, n - 1), p));
        smoothScrollTo(p * getWidth(), 0);
    }

    void jumpTo(int p) {
        scrollTo(p * getWidth(), 0);
    }

    @Override
    public void fling(int vx) {
        flung = true;
        if (vx > 500) snap(startPage + 1);
        else if (vx < -500) snap(startPage - 1);
        else snap(cur());
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        int a = e.getActionMasked();
        if (a == MotionEvent.ACTION_DOWN) {
            startPage = cur();
            flung = false;
        }
        boolean r = super.onTouchEvent(e);
        if ((a == MotionEvent.ACTION_UP || a == MotionEvent.ACTION_CANCEL) && !flung) snap(cur());
        return r;
    }

    @Override
    protected void onScrollChanged(int l, int t, int ol, int ot) {
        super.onScrollChanged(l, t, ol, ot);
        if (listener != null && getWidth() > 0) listener.onPage(l / (float) getWidth());
    }
}

/** Puntos indicadores de página. */
final class Dots extends View {
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float d, gap;
    private int n;
    private float pos;

    Dots(Context c, Palette pal) {
        super(c);
        d = c.getResources().getDisplayMetrics().density;
        gap = 14 * d;
        p.setColor(pal.text);
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
            p.setAlpha((int) (80 + 175 * t));
            c.drawCircle(x + i * gap, cy, (2.4f + 1.4f * t) * d, p);
        }
    }
  }
