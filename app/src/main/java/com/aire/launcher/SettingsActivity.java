package com.aire.launcher;

import android.app.Activity;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Insets;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;

import java.util.Locale;

public class SettingsActivity extends Activity {
    private static final int MATCH = ViewGroup.LayoutParams.MATCH_PARENT;
    private static final int WRAP = ViewGroup.LayoutParams.WRAP_CONTENT;

    private SharedPreferences sp;
    private Palette pal;
    private float dens;
    private ScrollView sv;
    private LinearLayout col;
    private int onAccent;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        sp = Cfg.sp(this);
        dens = getResources().getDisplayMetrics().density;
        Window w = getWindow();
        if (Build.VERSION.SDK_INT >= 30) w.setDecorFitsSystemWindows(false);
        w.setStatusBarColor(Color.TRANSPARENT);
        w.setNavigationBarColor(Color.TRANSPARENT);
        if (Build.VERSION.SDK_INT >= 29) w.setNavigationBarContrastEnforced(false);

        sv = new ScrollView(this);
        sv.setFillViewport(true);
        sv.setOverScrollMode(View.OVER_SCROLL_NEVER);
        col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(20), dp(44), dp(20), dp(32));
        sv.addView(col, new ViewGroup.LayoutParams(MATCH, WRAP));
        sv.setOnApplyWindowInsetsListener((v, ins) -> {
            int top, bottom;
            if (Build.VERSION.SDK_INT >= 30) {
                Insets sb = ins.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                top = sb.top;
                bottom = sb.bottom;
            } else {
                top = ins.getSystemWindowInsetTop();
                bottom = ins.getSystemWindowInsetBottom();
            }
            col.setPadding(dp(20), top + dp(12), dp(20), bottom + dp(28));
            return ins;
        });
        setContentView(sv);
        recolor();
        build();
    }

    private int dp(float v) {
        return Math.round(v * dens);
    }

    private void recolor() {
        pal = Palette.from(this);
        getWindow().setBackgroundDrawable(new ColorDrawable(Palette.mix(pal.scrim, pal.text, 0.04f)));
        onAccent = Palette.contrast(0xFF111317, pal.accent) > Palette.contrast(0xFFFFFFFF, pal.accent)
                ? 0xFF111317 : 0xFFFFFFFF;
        boolean l = pal.light;
        if (Build.VERSION.SDK_INT >= 30) {
            WindowInsetsController c = getWindow().getInsetsController();
            if (c != null) {
                int m = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                        | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;
                c.setSystemBarsAppearance(l ? m : 0, m);
            }
        } else {
            int f = View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION;
            if (l) f |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            getWindow().getDecorView().setSystemUiVisibility(f);
        }
    }

    private void redraw(boolean recolor) {
        final int y = sv.getScrollY();
        if (recolor) recolor();
        build();
        sv.post(() -> sv.scrollTo(0, y));
    }

    // ------------------------------------------------------------ pantalla

    private void build() {
        col.removeAllViews();

        TextView title = txt(L.t("Personalizar", "Customize"), 32, pal.text,
                Typeface.create("sans-serif-light", Typeface.NORMAL));
        title.setGravity(Gravity.START);
        col.addView(title, MATCH, WRAP);
        TextView sub = txt(L.t("Los cambios se aplican al volver al inicio.",
                "Changes apply when you go back home."), 13, pal.textDim, Typeface.DEFAULT);
        sub.setGravity(Gravity.START);
        sub.setPadding(0, dp(4), 0, 0);
        col.addView(sub, MATCH, WRAP);

        section(L.t("Íconos", "Icons"));
        choice(Cfg.SHAPE, L.t("Forma", "Shape"), 0,
                new String[]{L.t("Círculo", "Circle"), "Squircle", L.t("Cuadrado", "Square"), L.t("Gota", "Drop")},
                new int[]{0, 1, 2, 3});
        toggle(Cfg.THEMED, L.t("Íconos temáticos (se adaptan al fondo)", "Themed icons (match wallpaper)"), true);
        choice(Cfg.ISIZE, L.t("Tamaño", "Size"), 1,
                new String[]{L.t("Pequeño", "Small"), L.t("Mediano", "Medium"), L.t("Grande", "Large")},
                new int[]{0, 1, 2});
        choice(Cfg.COLS, L.t("Columnas del cajón", "Drawer columns"), 4, new String[]{"4", "5"}, new int[]{4, 5});
        toggle(Cfg.LABELS, L.t("Mostrar nombres", "Show names"), true);

        section(L.t("Reloj", "Clock"));
        choice(Cfg.CSTYLE, L.t("Estilo", "Style"), 0,
                new String[]{L.t("Dividido", "Split"), L.t("Una línea", "One line")}, new int[]{0, 1});
        choice(Cfg.CSIZE, L.t("Tamaño", "Size"), 1,
                new String[]{L.t("Pequeño", "Small"), L.t("Mediano", "Medium"), L.t("Grande", "Large")},
                new int[]{0, 1, 2});
        choice(Cfg.CALIGN, L.t("Alineación", "Alignment"), 0,
                new String[]{L.t("Centro", "Center"), L.t("Izquierda", "Left")}, new int[]{0, 1});
        toggle(Cfg.DATE, L.t("Mostrar fecha", "Show date"), true);
        accentRow();

        section(L.t("Fondo y tema", "Wallpaper & theme"));
        choice(Cfg.THEME, L.t("Tema", "Theme"), 0,
                new String[]{L.t("Automático", "Auto"), L.t("Claro", "Light"), L.t("Oscuro", "Dark")},
                new int[]{0, 1, 2});
        slider(Cfg.HOMEDIM, L.t("Oscurecer fondo (inicio)", "Dim wallpaper (home)"), 0, 70, true);
        slider(Cfg.DRAWERDIM, L.t("Opacidad del cajón", "Drawer opacity"), 40, 95, false);

        section("Dock");
        toggle(Cfg.DOCKSHOW, L.t("Mostrar dock", "Show dock"), true);
        choice(Cfg.DOCKMAX, L.t("Apps en el dock", "Dock apps"), 6, new String[]{"4", "5", "6"},
                new int[]{4, 5, 6});
        toggle(Cfg.DOCKPILL, L.t("Fondo en forma de píldora", "Pill background"), false);

        section(L.t("Cajón de apps", "App drawer"));
        toggle(Cfg.GROUP, L.t("Agrupar por categorías", "Group by category"), true);
        toggle(Cfg.FREQ, L.t("Página de frecuentes", "Frequent apps page"), true);
        toggle(Cfg.KBD, L.t("Abrir teclado al abrir el cajón", "Open keyboard with the drawer"), false);
        int hidden = hiddenCount();
        action(L.t("Restaurar apps ocultas (" + hidden + ")", "Restore hidden apps (" + hidden + ")"), () -> {
            sp.edit().remove(Cfg.HIDDEN).putInt(Cfg.REV, sp.getInt(Cfg.REV, 0) + 1).apply();
            redraw(false);
        });

        section(L.t("Gestos", "Gestures"));
        choice(Cfg.SWIPE, L.t("Deslizar hacia abajo", "Swipe down"), 0,
                new String[]{L.t("Notificaciones", "Notifications"), L.t("Nada", "Nothing")}, new int[]{0, 1});

        action(L.t("Restablecer personalización", "Reset customization"), () -> {
            SharedPreferences.Editor e = sp.edit();
            for (String k : Cfg.RESETTABLE) e.remove(k);
            e.putInt(Cfg.REV, sp.getInt(Cfg.REV, 0) + 1).apply();
            redraw(true);
        });
        TextView ver = txt("Aire 1.1", 12, pal.textDim, Typeface.DEFAULT);
        ver.setPadding(0, dp(24), 0, 0);
        col.addView(ver, MATCH, WRAP);
    }

    // ------------------------------------------------------------ componentes

    private void put(String k, int v) {
        sp.edit().putInt(k, v).putInt(Cfg.REV, sp.getInt(Cfg.REV, 0) + 1).apply();
    }

    private void put(String k, boolean v) {
        sp.edit().putBoolean(k, v).putInt(Cfg.REV, sp.getInt(Cfg.REV, 0) + 1).apply();
    }

    private int hiddenCount() {
        int n = 0;
        String s = sp.getString(Cfg.HIDDEN, "");
        if (s != null) for (String k : s.split("\n")) if (!k.isEmpty()) n++;
        return n;
    }

    private TextView txt(String s, float size, int color, Typeface tf) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, size);
        t.setTextColor(color);
        t.setTypeface(tf);
        t.setIncludeFontPadding(false);
        return t;
    }

    private void section(String s) {
        TextView t = txt(s.toUpperCase(Locale.getDefault()), 12, pal.accent,
                Typeface.create("sans-serif-medium", Typeface.NORMAL));
        t.setLetterSpacing(0.14f);
        t.setPadding(0, dp(28), 0, dp(4));
        col.addView(t, MATCH, WRAP);
    }

    private TextView label(String s) {
        TextView t = txt(s, 15, pal.text, Typeface.DEFAULT);
        t.setPadding(0, dp(14), 0, dp(8));
        return t;
    }

    private void styleChip(TextView t, boolean sel) {
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(dp(18));
        g.setColor(sel ? pal.accent : pal.plate);
        t.setBackground(g);
        t.setTextColor(sel ? onAccent : pal.text);
    }

    private TextView chip(String s, boolean sel) {
        TextView t = txt(s, 14, pal.text, Typeface.DEFAULT);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(14), dp(8), dp(14), dp(8));
        styleChip(t, sel);
        return t;
    }

    private void choice(String key, String title, int def, String[] labels, int[] vals) {
        col.addView(label(title), MATCH, WRAP);
        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        hs.setOverScrollMode(View.OVER_SCROLL_NEVER);
        LinearLayout row = new LinearLayout(this);
        int cur = sp.getInt(key, def);
        for (int i = 0; i < labels.length; i++) {
            final int v = vals[i];
            TextView c = chip(labels[i], v == cur);
            c.setOnClickListener(x -> {
                put(key, v);
                redraw(key.equals(Cfg.THEME));
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(WRAP, WRAP);
            lp.rightMargin = dp(8);
            row.addView(c, lp);
        }
        hs.addView(row, new ViewGroup.LayoutParams(WRAP, WRAP));
        col.addView(hs, MATCH, WRAP);
    }

    private void toggle(String key, String title, boolean def) {
        LinearLayout r = new LinearLayout(this);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(0, dp(10), 0, dp(10));
        TextView t = txt(title, 15, pal.text, Typeface.DEFAULT);
        r.addView(t, new LinearLayout.LayoutParams(0, WRAP, 1f));
        Switch s = new Switch(this);
        s.setChecked(sp.getBoolean(key, def));
        s.setThumbTintList(ColorStateList.valueOf(pal.accent));
        s.setTrackTintList(ColorStateList.valueOf(Palette.alpha(pal.accent, 0x66)));
        s.setOnCheckedChangeListener((btn, on) -> put(key, on));
        r.addView(s, new LinearLayout.LayoutParams(WRAP, WRAP));
        col.addView(r, MATCH, WRAP);
    }

    private void action(String s, Runnable r) {
        TextView t = chip(s, false);
        t.setOnClickListener(v -> r.run());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(WRAP, WRAP);
        lp.topMargin = dp(14);
        col.addView(t, lp);
    }

    private void accentRow() {
        col.addView(label(L.t("Color de acento", "Accent color")), MATCH, WRAP);
        LinearLayout row = new LinearLayout(this);
        int cur = sp.getInt(Cfg.ACCENT, 0);
        for (int i = 0; i < Cfg.ACCENTS.length; i++) {
            final int idx = i;
            TextView dot = txt(i == 0 ? "A" : "", 12, pal.text, Typeface.DEFAULT);
            dot.setGravity(Gravity.CENTER);
            GradientDrawable g = new GradientDrawable();
            g.setShape(GradientDrawable.OVAL);
            g.setColor(i == 0 ? pal.plate : Cfg.ACCENTS[i]);
            if (i == cur) g.setStroke(dp(2), pal.text);
            dot.setBackground(g);
            dot.setOnClickListener(v -> {
                put(Cfg.ACCENT, idx);
                redraw(true);
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(38), dp(38));
            lp.rightMargin = dp(10);
            row.addView(dot, lp);
        }
        col.addView(row, MATCH, WRAP);
    }

    private void slider(String key, String title, int min, int max, boolean home) {
        int cur = sp.getInt(key, -1);
        LinearLayout head = new LinearLayout(this);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setPadding(0, dp(14), 0, 0);
        TextView t = txt(title, 15, pal.text, Typeface.DEFAULT);
        head.addView(t, new LinearLayout.LayoutParams(0, WRAP, 1f));
        final TextView val = txt(cur < 0 ? "Auto" : cur + "%", 14, pal.textDim, Typeface.DEFAULT);
        val.setPadding(0, 0, dp(10), 0);
        head.addView(val, new LinearLayout.LayoutParams(WRAP, WRAP));
        final TextView auto = chip("Auto", cur < 0);
        auto.setPadding(dp(12), dp(4), dp(12), dp(4));
        head.addView(auto, new LinearLayout.LayoutParams(WRAP, WRAP));
        col.addView(head, MATCH, WRAP);

        final SeekBar sb = new SeekBar(this);
        sb.setMax(max - min);
        int start = cur < 0 ? Math.round((home ? pal.homeDim : pal.drawerDim) * 100) : cur;
        sb.setProgress(Math.max(0, Math.min(max - min, start - min)));
        ColorStateList cs = ColorStateList.valueOf(pal.accent);
        sb.setProgressTintList(cs);
        sb.setThumbTintList(cs);
        sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar s, int p, boolean fromUser) {
                if (!fromUser) return;
                int v = min + p;
                put(key, v);
                val.setText(v + "%");
                styleChip(auto, false);
            }

            @Override
            public void onStartTrackingTouch(SeekBar s) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar s) {
            }
        });
        auto.setOnClickListener(x -> {
            sp.edit().remove(key).putInt(Cfg.REV, sp.getInt(Cfg.REV, 0) + 1).apply();
            pal = Palette.from(this);
            val.setText("Auto");
            styleChip(auto, true);
            int v = Math.round((home ? pal.homeDim : pal.drawerDim) * 100);
            sb.setProgress(Math.max(0, Math.min(max - min, v - min)));
        });
        col.addView(sb, MATCH, WRAP);
    }
  }
