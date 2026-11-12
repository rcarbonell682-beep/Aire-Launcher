package com.aire.launcher;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.Locale;

/** Claves de ajustes y valores compartidos. */
final class Cfg {
    static final String FILE = "aire";

    static final String THEME = "theme";          // 0 auto, 1 claro, 2 oscuro
    static final String SHAPE = "shape";          // 0 círculo, 1 squircle, 2 cuadrado, 3 gota
    static final String THEMED = "themed";        // boolean
    static final String ISIZE = "isize";          // 0 pequeño, 1 mediano, 2 grande
    static final String COLS = "cols";            // 4 o 5
    static final String LABELS = "labels";        // boolean
    static final String CSTYLE = "cstyle";        // 0 dividido, 1 una línea
    static final String CSIZE = "csize";          // 0, 1, 2
    static final String CALIGN = "calign";        // 0 centro, 1 izquierda
    static final String DATE = "showdate";        // boolean
    static final String ACCENT = "accent";        // índice en ACCENTS (0 = automático)
    static final String HOMEDIM = "homedim";      // % (ausente = automático)
    static final String DRAWERDIM = "drawerdim";  // % (ausente = automático)
    static final String DOCKMAX = "dockmax";      // 4..6
    static final String DOCKPILL = "dockpill";    // boolean
    static final String DOCKSHOW = "dockshow";    // boolean
    static final String GROUP = "group";          // boolean
    static final String FREQ = "freq";            // boolean
    static final String KBD = "kbd";              // boolean
    static final String SWIPE = "swipe";          // 0 notificaciones, 1 nada
    static final String HIDDEN = "hidden";
    static final String USAGE = "usage";
    static final String REV = "rev";

    static final String[] RESETTABLE = {THEME, SHAPE, THEMED, ISIZE, COLS, LABELS, CSTYLE, CSIZE, CALIGN,
            DATE, ACCENT, HOMEDIM, DRAWERDIM, DOCKMAX, DOCKPILL, DOCKSHOW, GROUP, FREQ, KBD, SWIPE};

    static final int[] ACCENTS = {0, 0xFF7FE0C3, 0xFFFF8A80, 0xFF82B1FF, 0xFFFFD180, 0xFFB388FF};

    static SharedPreferences sp(Context c) {
        return c.getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }
}

/** Texto en español o inglés según el idioma del teléfono. */
final class L {
    static String t(String es, String en) {
        return "es".equals(Locale.getDefault().getLanguage()) ? es : en;
    }
  }
