package com.aire.launcher;

import android.content.Context;
import android.content.SharedPreferences;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

final class Cfg {
    static final String FILE = "aire";

    static final String STYLE = "style";          // 0 cristal, 1 papel
    static final String PBASE = "pbase";          // color del lienzo (papel)
    static final String THEME = "theme";          // 0 auto, 1 claro, 2 oscuro
    static final String SHAPE = "shape";
    static final String THEMED = "themed";
    static final String ISIZE = "isize";
    static final String COLS = "cols";
    static final String LABELS = "labels";
    static final String CSTYLE = "cstyle";
    static final String CSIZE = "csize";
    static final String CALIGN = "calign";
    static final String DATE = "showdate";
    static final String ACCENT = "accent";        // 0 auto, 1..5 preset, 6 personalizado
    static final String ACUSTOM = "acustom";
    static final String BGMODE = "bgmode";        // 0 sistema, 1 foto, 2 color
    static final String BGCOLOR = "bgcolor";
    static final String BGP = "bgp";
    static final String BGS = "bgs";
    static final String HOMEDIM = "homedim";
    static final String DRAWERDIM = "drawerdim";
    static final String DOCKMAX = "dockmax";
    static final String DOCKPILL = "dockpill";
    static final String DOCKSHOW = "dockshow";
    static final String SORT = "sort";            // 0 nombre, 1 uso reciente, 2 frecuencia
    static final String GROUP = "group";          // páginas por categoría
    static final String FREQ = "freq";
    static final String GROUPSON = "groupson";
    static final String GROUPS = "groups";
    static final String PORDER = "porder";
    static final String KBD = "kbd";
    static final String BADGE = "badge";          // 0 no, 1 punto, 2 número
    static final String SWIPE = "swipe";
    static final String DBLTAP = "dbltap";
    static final String HIDDEN = "hidden";
    static final String USAGE = "usage";
    static final String REV = "rev";

    static final String[] PAGE_IDS = {"freq", "c0", "c1", "c2", "c3", "c4", "c5", "all", "groups"};

    static final String[] RESETTABLE = {STYLE, PBASE, THEME, SHAPE, THEMED, ISIZE, COLS, LABELS, CSTYLE, CSIZE,
            CALIGN, DATE, ACCENT, ACUSTOM, BGMODE, BGCOLOR, HOMEDIM, DRAWERDIM, DOCKMAX, DOCKPILL, DOCKSHOW,
            SORT, GROUP, FREQ, GROUPSON, PORDER, KBD, BADGE, SWIPE, DBLTAP};

    static final int[] ACCENTS = {0, 0xFF7FE0C3, 0xFFFF8A80, 0xFF82B1FF, 0xFFFFD180, 0xFFB388FF};

    static SharedPreferences sp(Context c) {
        return c.getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    static File bgFile(Context c) {
        return new File(c.getFilesDir(), "bg.jpg");
    }

    /** Orden de páginas guardado (siempre completo y sin repetidos). */
    static List<String> order(SharedPreferences sp) {
        List<String> r = new ArrayList<>();
        String s = sp.getString(PORDER, "");
        if (s != null) {
            for (String k : s.split(",")) {
                if (!k.isEmpty() && !r.contains(k) && contains(k)) r.add(k);
            }
        }
        for (String k : PAGE_IDS) if (!r.contains(k)) r.add(k);
        return r;
    }

    private static boolean contains(String k) {
        for (String x : PAGE_IDS) if (x.equals(k)) return true;
        return false;
    }

    static boolean applicable(SharedPreferences sp, String id) {
        switch (id) {
            case "freq":
                return sp.getBoolean(FREQ, true);
            case "groups":
                return sp.getBoolean(GROUPSON, true);
            case "all":
                return !sp.getBoolean(GROUP, true);
            default:
                return sp.getBoolean(GROUP, true);
        }
    }

    static String pageName(String id) {
        switch (id) {
            case "freq":
                return L.t("Frecuentes", "Frequent");
            case "all":
                return L.t("Todas", "All");
            case "groups":
                return L.t("Grupos", "Groups");
            default:
                return AppInfo.catName(id.charAt(1) - '0');
        }
    }
}

/** Texto en español o inglés según el idioma del teléfono. */
final class L {
    static String t(String es, String en) {
        return "es".equals(Locale.getDefault().getLanguage()) ? es : en;
    }
}
