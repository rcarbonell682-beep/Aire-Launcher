package com.aire.launcher;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Insets;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.DisplayMetrics;
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
import android.widget.Toast;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class SettingsActivity extends Activity {
    private static final int MATCH = ViewGroup.LayoutParams.MATCH_PARENT;
    private static final int WRAP = ViewGroup.LayoutParams.WRAP_CONTENT;
    private static final int REQ_PHOTO = 1, REQ_EXPORT = 2, REQ_IMPORT = 3;

    private static final int[] PAPER = {0xFFE8E2D6, 0xFFDCE3EA, 0xFFEBD9D6, 0xFFD9E2D4, 0xFF2B2D33, 0xFF1E2535};
    private static final int[] SOLID = {0xFF0E1116, 0xFF1B2A41, 0xFF2D1B3D, 0xFF123524, 0xFF3B1F1F,
            0xFFF2EFE9, 0xFFDDE7F0, 0xFFF3DCE0, 0xFFE0EBD9, 0xFFFFE9B8};

    private SharedPreferences sp;
    private Palette pal;
    private float dens;
    private ScrollView sv;
    private LinearLayout col, cur;
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
        col.setClipChildren(false);
        col.setClipToPadding(false);
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

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }

    private void recolor() {
        pal = Palette.from(this);
        getWindow().setBackgroundDrawable(new ColorDrawable(
                pal.paper ? pal.canvas : Palette.mix(pal.scrim, pal.text, 0.04f)));
        onAccent = pal.onAccent();
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
        cur = col;

        TextView title = txt(L.t("Personalizar", "Customize"), 32, pal.text,
                Typeface.create("sans-serif-light", Typeface.NORMAL));
        title.setGravity(Gravity.START);
        col.addView(title, MATCH, WRAP);
        TextView sub = txt(L.t("Los cambios se aplican al volver al inicio.",
                "Changes apply when you go back home."), 13, pal.textDim, Typeface.DEFAULT);
        sub.setGravity(Gravity.START);
        sub.setPadding(0, dp(4), 0, 0);
        col.addView(sub, MATCH, WRAP);

        section(L.t("Estilo de interfaz", "Interface style"));
        choice(Cfg.STYLE, L.t("Estilo", "Style"), 0,
                new String[]{L.t("Cristal", "Glass"), L.t("Papel en capas", "Layered paper")}, new int[]{0, 1});
        if (pal.paper) {
            cur.addView(label(L.t("Color del lienzo", "Canvas color")), MATCH, WRAP);
            colorPicker(Cfg.PBASE, 0xFFE8E2D6, PAPER);
        }
        accentRow();

        section(L.t("Íconos", "Icons"));
        choice(Cfg.SHAPE, L.t("Forma", "Shape"), 0,
                new String[]{L.t("Círculo", "Circle"), "Squircle", L.t("Cuadrado", "Square"), L.t("Gota", "Drop")},
                new int[]{0, 1, 2, 3});
        if (!pal.paper) {
            toggle(Cfg.THEMED, L.t("Íconos temáticos (se adaptan al fondo)", "Themed icons (match wallpaper)"), true);
        }
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

        section(L.t("Fondo y tema", "Wallpaper & theme"));
        if (pal.paper) {
            cur.addView(note(L.t("El estilo Papel usa su propio lienzo.", "Paper style uses its own canvas.")),
                    MATCH, WRAP);
        } else {
            choice(Cfg.BGMODE, L.t("Fondo de pantalla", "Wallpaper"), 0,
                    new String[]{L.t("Sistema", "System"), L.t("Foto", "Photo"), "Color"}, new int[]{0, 1, 2});
            int mode = sp.getInt(Cfg.BGMODE, 0);
            if (mode == 0) {
                action(L.t("Abrir selector del sistema", "Open system picker"), () ->
                        startActivity(Intent.createChooser(new Intent(Intent.ACTION_SET_WALLPAPER),
                                L.t("Fondo de pantalla", "Wallpaper"))));
            } else if (mode == 1) {
                action(Cfg.bgFile(this).exists() ? L.t("Cambiar foto", "Change photo")
                        : L.t("Elegir foto", "Choose photo"), this::pickPhoto);
            } else {
                colorPicker(Cfg.BGCOLOR, 0xFF2B2F3A, SOLID);
            }
            choice(Cfg.THEME, L.t("Tema", "Theme"), 0,
                    new String[]{L.t("Automático", "Auto"), L.t("Claro", "Light"), L.t("Oscuro", "Dark")},
                    new int[]{0, 1, 2});
            slider(Cfg.HOMEDIM, L.t("Oscurecer fondo (inicio)", "Dim wallpaper (home)"), 0, 70, true);
            slider(Cfg.DRAWERDIM, L.t("Opacidad del cajón", "Drawer opacity"), 40, 95, false);
        }

        section("Dock");
        toggle(Cfg.DOCKSHOW, L.t("Mostrar dock", "Show dock"), true);
        choice(Cfg.DOCKMAX, L.t("Apps en el dock", "Dock apps"), 6, new String[]{"4", "5", "6"},
                new int[]{4, 5, 6});
        if (!pal.paper) toggle(Cfg.DOCKPILL, L.t("Fondo en forma de píldora", "Pill background"), false);

        section(L.t("Cajón de apps", "App drawer"));
        choice(Cfg.SORT, L.t("Ordenar por", "Sort by"), 0,
                new String[]{L.t("Nombre", "Name"), L.t("Uso reciente", "Recent use"),
                        L.t("Frecuencia", "Frequency")}, new int[]{0, 1, 2});
        toggle(Cfg.GROUP, L.t("Páginas por categoría", "Category pages"), true);
        toggle(Cfg.FREQ, L.t("Página de frecuentes", "Frequent apps page"), true);
        toggle(Cfg.GROUPSON, L.t("Página de grupos", "Groups page"), true);
        toggle(Cfg.KBD, L.t("Abrir teclado al abrir el cajón", "Open keyboard with the drawer"), false);
        pageOrderUi();
        int hidden = hiddenCount();
        action(L.t("Restaurar apps ocultas (" + hidden + ")", "Restore hidden apps (" + hidden + ")"), () -> {
            sp.edit().remove(Cfg.HIDDEN).putInt(Cfg.REV, sp.getInt(Cfg.REV, 0) + 1).apply();
            redraw(false);
        });

        section(L.t("Insignias de notificaciones", "Notification badges"));
        choice(Cfg.BADGE, L.t("Estilo", "Style"), 0,
                new String[]{L.t("Apagadas", "Off"), L.t("Punto", "Dot"), L.t("Número", "Number")},
                new int[]{0, 1, 2});
        cur.addView(note(notifEnabled() ? L.t("Acceso concedido ✓", "Access granted ✓")
                : L.t("Falta dar acceso a las notificaciones.", "Notification access is needed.")), MATCH, WRAP);
        action(L.t("Dar acceso a notificaciones", "Grant notification access"), () ->
                openSafe(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)));
        cur.addView(note(restrictedHint()), MATCH, WRAP);

        section(L.t("Gestos", "Gestures"));
        choice(Cfg.SWIPE, L.t("Deslizar hacia abajo", "Swipe down"), 0,
                new String[]{L.t("Notificaciones", "Notifications"), L.t("Nada", "Nothing")}, new int[]{0, 1});
        toggle(Cfg.DBLTAP, L.t("Doble toque para apagar la pantalla", "Double tap to turn the screen off"), false);
        cur.addView(note(lockEnabled() ? L.t("Servicio activado ✓", "Service enabled ✓")
                : L.t("Falta activar Aire en Accesibilidad.", "Aire must be enabled in Accessibility.")),
                MATCH, WRAP);
        action(L.t("Abrir ajustes de accesibilidad", "Open accessibility settings"), () ->
                openSafe(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));

        section(L.t("Copia de seguridad", "Backup"));
        cur.addView(note(L.t("Incluye ajustes, grupos, dock y apps ocultas. No incluye la foto de fondo.",
                "Includes settings, groups, dock and hidden apps. Does not include the wallpaper photo.")),
                MATCH, WRAP);
        action(L.t("Exportar copia", "Export backup"), this::startExport);
        action(L.t("Importar copia", "Import backup"), this::startImport);

        cur = col;
        action(L.t("Restablecer personalización", "Reset customization"), () -> {
            SharedPreferences.Editor e = sp.edit();
            for (String k : Cfg.RESETTABLE) e.remove(k);
            e.putInt(Cfg.REV, sp.getInt(Cfg.REV, 0) + 1).apply();
            redraw(true);
        });
        TextView ver = txt("Aire 1.2", 12, pal.textDim, Typeface.DEFAULT);
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

    private void openSafe(Intent i) {
        try {
            startActivity(i);
        } catch (Exception e) {
            toast(L.t("No disponible", "Unavailable"));
        }
    }

    private boolean notifEnabled() {
        String s = Settings.Secure.getString(getContentResolver(), "enabled_notification_listeners");
        return s != null && s.contains(getPackageName());
    }

    private boolean lockEnabled() {
        String s = Settings.Secure.getString(getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        return s != null && s.contains(getPackageName());
    }

    private String restrictedHint() {
        return L.t("Si Android bloquea la opción: Ajustes → Apps → Aire → ⋮ → «Permitir ajustes restringidos».",
                "If Android blocks it: Settings → Apps → Aire → ⋮ → “Allow restricted settings”.");
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

    private TextView note(String s) {
        TextView t = txt(s, 12, pal.textDim, Typeface.DEFAULT);
        t.setGravity(Gravity.START);
        t.setPadding(0, dp(8), 0, dp(2));
        return t;
    }

    private void section(String s) {
        if (pal.paper) {
            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(dp(20), dp(6), dp(20), dp(16));
            card.setClipChildren(false);
            card.setClipToPadding(false);
            card.setBackground(Paper.card(pal, dp(26)));
            Paper.raise(card, pal, dp(6), dp(26));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(MATCH, WRAP);
            lp.topMargin = dp(22);
            col.addView(card, lp);
            cur = card;
        } else {
            cur = col;
        }
        TextView t = txt(s.toUpperCase(Locale.getDefault()), 12, pal.paper ? pal.textDim : pal.accent,
                Typeface.create("sans-serif-medium", Typeface.NORMAL));
        t.setLetterSpacing(0.14f);
        t.setPadding(0, pal.paper ? dp(14) : dp(28), 0, dp(4));
        cur.addView(t, MATCH, WRAP);
    }

    private TextView label(String s) {
        TextView t = txt(s, 15, pal.text, Typeface.DEFAULT);
        t.setPadding(0, dp(14), 0, dp(8));
        return t;
    }

    private void styleChip(TextView t, boolean sel) {
        if (pal.paper) {
            if (sel) {
                GradientDrawable g = new GradientDrawable();
                g.setCornerRadius(dp(18));
                g.setColor(pal.accent);
                t.setBackground(g);
            } else {
                t.setBackground(Paper.card(pal, dp(18)));
            }
            Paper.raise(t, pal, dp(3), dp(18));
            Paper.pressable(t, dp(3));
        } else {
            GradientDrawable g = new GradientDrawable();
            g.setCornerRadius(dp(18));
            g.setColor(sel ? pal.accent : pal.plate);
            t.setBackground(g);
        }
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
        cur.addView(label(title), MATCH, WRAP);
        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        hs.setOverScrollMode(View.OVER_SCROLL_NEVER);
        LinearLayout row = new LinearLayout(this);
        row.setPadding(dp(4), dp(4), dp(4), dp(10));
        int c = sp.getInt(key, def);
        for (int i = 0; i < labels.length; i++) {
            final int v = vals[i];
            TextView ch = chip(labels[i], v == c);
            ch.setOnClickListener(x -> {
                put(key, v);
                if (key.equals(Cfg.BGMODE) && v == 1 && !Cfg.bgFile(this).exists()) pickPhoto();
                redraw(true);
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(WRAP, WRAP);
            lp.rightMargin = dp(8);
            row.addView(ch, lp);
        }
        hs.addView(row, new ViewGroup.LayoutParams(WRAP, WRAP));
        cur.addView(hs, MATCH, WRAP);
    }

    private void toggle(String key, String title, boolean def) {
        LinearLayout r = new LinearLayout(this);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(0, dp(10), 0, dp(10));
        r.addView(txt(title, 15, pal.text, Typeface.DEFAULT), new LinearLayout.LayoutParams(0, WRAP, 1f));
        Switch s = new Switch(this);
        s.setChecked(sp.getBoolean(key, def));
        s.setThumbTintList(ColorStateList.valueOf(pal.accent));
        s.setTrackTintList(ColorStateList.valueOf(Palette.alpha(pal.accent, 0x66)));
        s.setOnCheckedChangeListener((btn, on) -> put(key, on));
        r.addView(s, new LinearLayout.LayoutParams(WRAP, WRAP));
        cur.addView(r, MATCH, WRAP);
    }

    private void action(String s, Runnable r) {
        TextView t = chip(s, false);
        t.setOnClickListener(v -> r.run());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(WRAP, WRAP);
        lp.topMargin = dp(14);
        lp.bottomMargin = dp(4);
        cur.addView(t, lp);
    }

    private void accentRow() {
        cur.addView(label(L.t("Color de acento", "Accent color")), MATCH, WRAP);
        LinearLayout row = new LinearLayout(this);
        row.setPadding(dp(4), dp(4), dp(4), dp(8));
        int c = sp.getInt(Cfg.ACCENT, 0);
        for (int i = 0; i <= Cfg.ACCENTS.length; i++) {
            final int idx = i;
            boolean custom = i == Cfg.ACCENTS.length;
            TextView dot = txt(i == 0 ? "A" : custom ? "✎" : "", 13, pal.text, Typeface.DEFAULT);
            dot.setGravity(Gravity.CENTER);
            GradientDrawable g = new GradientDrawable();
            g.setShape(GradientDrawable.OVAL);
            g.setColor(i == 0 ? pal.plate : custom ? sp.getInt(Cfg.ACUSTOM, 0xFFFF6B4A) : Cfg.ACCENTS[i]);
            if ((custom ? 6 : i) == c) g.setStroke(dp(3), pal.text);
            dot.setBackground(g);
            dot.setOnClickListener(v -> {
                put(Cfg.ACCENT, custom ? 6 : idx);
                redraw(true);
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(38), dp(38));
            lp.rightMargin = dp(10);
            row.addView(dot, lp);
        }
        cur.addView(row, MATCH, WRAP);
        if (c == 6) colorPicker(Cfg.ACUSTOM, 0xFFFF6B4A, new int[0]);
    }

    private void colorPicker(String key, int def, int[] presets) {
        int c = sp.getInt(key, def);
        if (presets.length > 0) {
            HorizontalScrollView hs = new HorizontalScrollView(this);
            hs.setHorizontalScrollBarEnabled(false);
            LinearLayout row = new LinearLayout(this);
            row.setPadding(dp(4), dp(4), dp(4), dp(8));
            for (int p : presets) {
                final int col2 = p;
                View dot = new View(this);
                GradientDrawable g = new GradientDrawable();
                g.setShape(GradientDrawable.OVAL);
                g.setColor(p);
                if (p == c) g.setStroke(dp(3), pal.accent);
                else g.setStroke(dp(1), Palette.alpha(pal.text, 0x30));
                dot.setBackground(g);
                dot.setOnClickListener(v -> {
                    put(key, col2);
                    redraw(true);
                });
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(38), dp(38));
                lp.rightMargin = dp(10);
                row.addView(dot, lp);
            }
            hs.addView(row, new ViewGroup.LayoutParams(WRAP, WRAP));
            cur.addView(hs, MATCH, WRAP);
        }
        final float[] hsv = new float[3];
        Color.colorToHSV(c, hsv);
        View prev = new View(this);
        final GradientDrawable pg = new GradientDrawable();
        pg.setCornerRadius(dp(12));
        pg.setColor(c);
        prev.setBackground(pg);
        LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(MATCH, dp(34));
        plp.topMargin = dp(6);
        cur.addView(prev, plp);
        hsvBar(L.t("Tono", "Hue"), hsv, 0, 360, key, pg);
        hsvBar(L.t("Saturación", "Saturation"), hsv, 1, 100, key, pg);
        hsvBar(L.t("Brillo", "Brightness"), hsv, 2, 100, key, pg);
    }

    private void hsvBar(String name, final float[] hsv, final int idx, int max, final String key,
                        final GradientDrawable pg) {
        cur.addView(txt(name, 12, pal.textDim, Typeface.DEFAULT), MATCH, WRAP);
        SeekBar sb = new SeekBar(this);
        sb.setMax(max);
        sb.setProgress(Math.round(idx == 0 ? hsv[0] : hsv[idx] * 100f));
        ColorStateList cs = ColorStateList.valueOf(pal.accent);
        sb.setProgressTintList(cs);
        sb.setThumbTintList(cs);
        sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar s, int p, boolean fromUser) {
                if (!fromUser) return;
                hsv[idx] = idx == 0 ? p : p / 100f;
                int c = Color.HSVToColor(hsv);
                pg.setColor(c);
                put(key, c);
            }

            @Override
            public void onStartTrackingTouch(SeekBar s) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar s) {
                redraw(true);
            }
        });
        cur.addView(sb, MATCH, WRAP);
    }

    private void slider(String key, String title, int min, int max, boolean home) {
        int c = sp.getInt(key, -1);
        LinearLayout head = new LinearLayout(this);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setPadding(0, dp(14), 0, 0);
        head.addView(txt(title, 15, pal.text, Typeface.DEFAULT), new LinearLayout.LayoutParams(0, WRAP, 1f));
        final TextView val = txt(c < 0 ? "Auto" : c + "%", 14, pal.textDim, Typeface.DEFAULT);
        val.setPadding(0, 0, dp(10), 0);
        head.addView(val, new LinearLayout.LayoutParams(WRAP, WRAP));
        final TextView auto = chip("Auto", c < 0);
        auto.setPadding(dp(12), dp(4), dp(12), dp(4));
        head.addView(auto, new LinearLayout.LayoutParams(WRAP, WRAP));
        cur.addView(head, MATCH, WRAP);

        final SeekBar sb = new SeekBar(this);
        sb.setMax(max - min);
        int start = c < 0 ? Math.round((home ? pal.homeDim : pal.drawerDim) * 100) : c;
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
        cur.addView(sb, MATCH, WRAP);
    }

    // ---- orden de páginas

    private void pageOrderUi() {
        cur.addView(label(L.t("Orden de páginas", "Page order")), MATCH, WRAP);
        final List<String> full = Cfg.order(sp);
        final List<String> disp = new ArrayList<>();
        for (String id : full) if (Cfg.applicable(sp, id)) disp.add(id);
        for (int i = 0; i < disp.size(); i++) {
            final int pos = i;
            LinearLayout row = new LinearLayout(this);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(0, dp(4), 0, dp(6));
            row.setClipChildren(false);
            row.addView(txt((i + 1) + ".  " + Cfg.pageName(disp.get(i)), 15, pal.text, Typeface.DEFAULT),
                    new LinearLayout.LayoutParams(0, WRAP, 1f));
            if (i > 0) row.addView(moveChip("▲", full, disp, pos, -1), moveLp());
            if (i < disp.size() - 1) row.addView(moveChip("▼", full, disp, pos, 1), moveLp());
            cur.addView(row, MATCH, WRAP);
        }
    }

    private LinearLayout.LayoutParams moveLp() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(WRAP, WRAP);
        lp.leftMargin = dp(8);
        return lp;
    }

    private TextView moveChip(String s, final List<String> full, final List<String> disp, final int pos,
                              final int dir) {
        TextView t = chip(s, false);
        t.setOnClickListener(v -> {
            Collections.swap(full, full.indexOf(disp.get(pos)), full.indexOf(disp.get(pos + dir)));
            sp.edit().putString(Cfg.PORDER, TextUtils.join(",", full))
                    .putInt(Cfg.REV, sp.getInt(Cfg.REV, 0) + 1).apply();
            redraw(false);
        });
        return t;
    }

    // ------------------------------------------------------------ foto de fondo

    private void pickPhoto() {
        Intent i = new Intent(Intent.ACTION_GET_CONTENT);
        i.setType("image/*");
        i.addCategory(Intent.CATEGORY_OPENABLE);
        try {
            startActivityForResult(Intent.createChooser(i, L.t("Elegir foto", "Choose photo")), REQ_PHOTO);
        } catch (Exception e) {
            toast(L.t("No disponible", "Unavailable"));
        }
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (res != RESULT_OK || data == null || data.getData() == null) return;
        Uri u = data.getData();
        if (req == REQ_PHOTO) importPhoto(u);
        else if (req == REQ_EXPORT) exportBackup(u);
        else if (req == REQ_IMPORT) importBackup(u);
    }

    private void importPhoto(final Uri u) {
        toast(L.t("Procesando foto…", "Processing photo…"));
        new Thread(() -> {
            try {
                DisplayMetrics dm = getResources().getDisplayMetrics();
                int w = dm.widthPixels, h = dm.heightPixels;
                BitmapFactory.Options o = new BitmapFactory.Options();
                o.inJustDecodeBounds = true;
                InputStream in = getContentResolver().openInputStream(u);
                BitmapFactory.decodeStream(in, null, o);
                in.close();
                int ss = 1;
                while (o.outWidth / (ss * 2) >= w * 1.5f && o.outHeight / (ss * 2) >= h * 1.5f) ss *= 2;
                BitmapFactory.Options o2 = new BitmapFactory.Options();
                o2.inSampleSize = ss;
                in = getContentResolver().openInputStream(u);
                Bitmap src = BitmapFactory.decodeStream(in, null, o2);
                in.close();
                if (src == null) throw new Exception("decode");

                int rot = 0;
                try {
                    in = getContentResolver().openInputStream(u);
                    int ori = new ExifInterface(in).getAttributeInt(ExifInterface.TAG_ORIENTATION,
                            ExifInterface.ORIENTATION_NORMAL);
                    in.close();
                    if (ori == ExifInterface.ORIENTATION_ROTATE_90) rot = 90;
                    else if (ori == ExifInterface.ORIENTATION_ROTATE_180) rot = 180;
                    else if (ori == ExifInterface.ORIENTATION_ROTATE_270) rot = 270;
                } catch (Exception ignored) {
                }
                if (rot != 0) {
                    Matrix rm = new Matrix();
                    rm.postRotate(rot);
                    src = Bitmap.createBitmap(src, 0, 0, src.getWidth(), src.getHeight(), rm, true);
                }
                float sc = Math.max(w / (float) src.getWidth(), h / (float) src.getHeight());
                Bitmap out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
                Matrix m = new Matrix();
                m.postScale(sc, sc);
                m.postTranslate((w - src.getWidth() * sc) / 2f, (h - src.getHeight() * sc) / 2f);
                new Canvas(out).drawBitmap(src, m, new Paint(Paint.FILTER_BITMAP_FLAG));

                File f = Cfg.bgFile(this);
                FileOutputStream fo = new FileOutputStream(f);
                out.compress(Bitmap.CompressFormat.JPEG, 88, fo);
                fo.close();

                Bitmap tiny = Bitmap.createScaledBitmap(out, 24, 24, true);
                long r = 0, g = 0, b = 0;
                float bestScore = -1f;
                int vivid = 0;
                float[] hsv = new float[3];
                for (int y = 0; y < 24; y++) {
                    for (int x = 0; x < 24; x++) {
                        int px = tiny.getPixel(x, y);
                        r += Color.red(px);
                        g += Color.green(px);
                        b += Color.blue(px);
                        Color.colorToHSV(px, hsv);
                        float score = hsv[1] * hsv[2];
                        if (score > bestScore) {
                            bestScore = score;
                            vivid = px;
                        }
                    }
                }
                int avg = Color.rgb((int) (r / 576), (int) (g / 576), (int) (b / 576));
                final int fAvg = avg, fVivid = vivid;
                runOnUiThread(() -> {
                    sp.edit().putInt(Cfg.BGMODE, 1).putInt(Cfg.BGP, fAvg).putInt(Cfg.BGS, fVivid)
                            .putInt(Cfg.REV, sp.getInt(Cfg.REV, 0) + 1).apply();
                    redraw(true);
                });
            } catch (Exception e) {
                runOnUiThread(() -> toast(L.t("No se pudo usar la foto", "Couldn't use the photo")));
            }
        }).start();
    }

    // ------------------------------------------------------------ copia de seguridad

    private void startExport() {
        Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("application/json");
        i.putExtra(Intent.EXTRA_TITLE, "aire-backup.json");
        try {
            startActivityForResult(i, REQ_EXPORT);
        } catch (Exception e) {
            toast(L.t("No disponible", "Unavailable"));
        }
    }

    private void startImport() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("*/*");
        try {
            startActivityForResult(i, REQ_IMPORT);
        } catch (Exception e) {
            toast(L.t("No disponible", "Unavailable"));
        }
    }

    private void exportBackup(Uri u) {
        try {
            JSONObject root = new JSONObject();
            JSONObject ints = new JSONObject(), bools = new JSONObject(), strs = new JSONObject();
            for (Map.Entry<String, ?> e : sp.getAll().entrySet()) {
                Object v = e.getValue();
                if (v instanceof Integer) ints.put(e.getKey(), ((Integer) v).intValue());
                else if (v instanceof Boolean) bools.put(e.getKey(), ((Boolean) v).booleanValue());
                else if (v instanceof String) strs.put(e.getKey(), v);
            }
            root.put("app", "aire");
            root.put("v", 1);
            root.put("i", ints);
            root.put("b", bools);
            root.put("s", strs);
            OutputStream os = getContentResolver().openOutputStream(u);
            os.write(root.toString().getBytes("UTF-8"));
            os.close();
            toast(L.t("Copia guardada", "Backup saved"));
        } catch (Exception e) {
            toast(L.t("No se pudo guardar", "Couldn't save"));
        }
    }

    private void importBackup(Uri u) {
        try {
            InputStream in = getContentResolver().openInputStream(u);
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
            in.close();
            JSONObject root = new JSONObject(bo.toString("UTF-8"));
            if (!"aire".equals(root.optString("app"))) {
                toast(L.t("Archivo no válido", "Invalid file"));
                return;
            }
            int rev = sp.getInt(Cfg.REV, 0);
            SharedPreferences.Editor e = sp.edit();
            e.clear();
            JSONObject ints = root.optJSONObject("i"), bools = root.optJSONObject("b"), strs = root.optJSONObject("s");
            if (ints != null) {
                for (Iterator<String> it = ints.keys(); it.hasNext(); ) {
                    String k = it.next();
                    e.putInt(k, ints.getInt(k));
                }
            }
            if (bools != null) {
                for (Iterator<String> it = bools.keys(); it.hasNext(); ) {
                    String k = it.next();
                    e.putBoolean(k, bools.getBoolean(k));
                }
            }
            if (strs != null) {
                for (Iterator<String> it = strs.keys(); it.hasNext(); ) {
                    String k = it.next();
                    e.putString(k, strs.getString(k));
                }
            }
            e.putInt(Cfg.REV, rev + 1).apply();
            toast(L.t("Copia restaurada", "Backup restored"));
            redraw(true);
        } catch (Exception ex) {
            toast(L.t("No se pudo restaurar", "Couldn't restore"));
        }
    }
}
