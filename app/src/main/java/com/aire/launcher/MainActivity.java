package com.aire.launcher;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.ActivityOptions;
import android.app.WallpaperManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Insets;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.AlarmClock;
import android.provider.MediaStore;
import android.provider.Settings;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.text.format.DateFormat;
import android.text.format.DateUtils;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.Collator;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

public class MainActivity extends Activity {

    private static final int MATCH = ViewGroup.LayoutParams.MATCH_PARENT;
    private static final int WRAP = ViewGroup.LayoutParams.WRAP_CONTENT;

    private static final class Group {
        String name;
        final List<String> ids = new ArrayList<>();

        Group(String n) {
            name = n;
        }
    }

    private final Handler ui = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private SharedPreferences prefs;
    private Palette pal;
    private float density = 1f;
    private int W, H, insetTop, insetBottom;

    // ajustes (loadSettings)
    private int cols = 4, dockMax = 6, cstyle, csize = 1, calign, swipe, shape, sortMode, badgeMode, appliedRev;
    private float isc = 1f;
    private boolean themed = true, labels = true, group = true, freq = true, groupsOn = true, kbd,
            showDate = true, dockPill, dockShow = true, paper, sysBlur = true;
    private final Set<String> hiddenIds = new HashSet<>();
    private final Map<String, Integer> usage = new HashMap<>();
    private final Map<String, Long> lastUse = new HashMap<>();
    private final List<Group> groups = new ArrayList<>();
    private boolean usageDirty;

    private Root root;
    private View scrim;
    private ImageView bgBlur;
    private FrameLayout home;
    private LinearLayout drawer, dock, pagesRow;
    private FrameLayout pagerHost;
    private View resultsHost;
    private TextView hourTv, minTv, dateTv;
    private EditText search;
    private Dots dots;
    private BottomScroll resultsScroll;
    private ReverseGrid resultsGrid;
    private FrameLayout menuLayer;
    private final List<BottomScroll> pageScrolls = new ArrayList<>();
    private List<AppInfo> currentResults = new ArrayList<>();

    private List<AppInfo> apps = new ArrayList<>();
    private final Map<String, AppInfo> byId = new HashMap<>();

    private float pagePos;
    private int pageCount;
    private ValueAnimator pageAnim;

    private int dockSlot;
    private final List<String> dockShown = new ArrayList<>();
    private boolean dDragging;
    private float dLastRaw;
    private int dFrom, dTo;

    private float progress;
    private ValueAnimator anim;
    private int lastBlur = -1;
    private String shownH = "", shownM = "", shownD = "";
    private boolean timeOn;
    private WallpaperManager.OnColorsChangedListener wallpaperListener;

    private final Runnable reloadTask = this::loadApps;
    private final Runnable paletteTask = () -> {
        pal = Palette.from(this);
        rebuildUi();
    };

    private final BroadcastReceiver timeRx = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent i) {
            updateClock();
        }
    };
    private final BroadcastReceiver pkgRx = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent i) {
            ui.removeCallbacks(reloadTask);
            ui.postDelayed(reloadTask, 500);
        }
    };

    // ------------------------------------------------------------ ciclo de vida

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        prefs = Cfg.sp(this);
        setupWindow();
        pal = Palette.from(this);
        buildUi();

        IntentFilter pf = new IntentFilter();
        pf.addAction(Intent.ACTION_PACKAGE_ADDED);
        pf.addAction(Intent.ACTION_PACKAGE_REMOVED);
        pf.addAction(Intent.ACTION_PACKAGE_CHANGED);
        pf.addAction(Intent.ACTION_PACKAGE_REPLACED);
        pf.addDataScheme("package");
        reg(pkgRx, pf);

        if (Build.VERSION.SDK_INT >= 27) {
            wallpaperListener = (colors, which) -> {
                ui.removeCallbacks(paletteTask);
                ui.postDelayed(paletteTask, 400);
            };
            WallpaperManager.getInstance(this).addOnColorsChangedListener(wallpaperListener, ui);
        }
        loadApps();
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (!timeOn) {
            IntentFilter f = new IntentFilter();
            f.addAction(Intent.ACTION_TIME_TICK);
            f.addAction(Intent.ACTION_TIME_CHANGED);
            f.addAction(Intent.ACTION_TIMEZONE_CHANGED);
            f.addAction(Intent.ACTION_DATE_CHANGED);
            reg(timeRx, f);
            timeOn = true;
        }
        Badges.listener = () -> ui.post(this::refreshBadges);
        refreshBadges();
        updateClock();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (prefs.getInt(Cfg.REV, 0) != appliedRev) {
            pal = Palette.from(this);
            rebuildUi();
        }
        applyBars();
        updateClock();
    }

    @Override
    protected void onStop() {
        super.onStop();
        Badges.listener = null;
        if (timeOn) {
            try {
                unregisterReceiver(timeRx);
            } catch (IllegalArgumentException ignored) {
            }
            timeOn = false;
        }
        dismissMenu();
        if (progress > 0f) closeInstant();
        if (usageDirty) {
            usageDirty = false;
            buildPages();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        ui.removeCallbacksAndMessages(null);
        try {
            unregisterReceiver(pkgRx);
        } catch (IllegalArgumentException ignored) {
        }
        if (Build.VERSION.SDK_INT >= 27 && wallpaperListener != null) {
            WallpaperManager.getInstance(this).removeOnColorsChangedListener(wallpaperListener);
        }
        io.shutdownNow();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        dismissMenu();
        if (progress > 0.01f) closeDrawer();
    }

    @Override
    public void onConfigurationChanged(Configuration c) {
        super.onConfigurationChanged(c);
        pal = Palette.from(this);
        rebuildUi();
    }

    @Override
    public void onBackPressed() {
        if (menuLayer != null) {
            dismissMenu();
            return;
        }
        if (progress > 0.01f) {
            if (search.getText().length() > 0 || search.hasFocus()) {
                search.setText("");
                hideKeyboard();
            } else {
                closeDrawer();
            }
        }
    }

    private void reg(BroadcastReceiver r, IntentFilter f) {
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(r, f, Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(r, f);
    }

    // ------------------------------------------------------------ ajustes y datos

    private void loadSettings() {
        SharedPreferences sp = prefs;
        paper = pal.paper;
        cols = sp.getInt(Cfg.COLS, 4) == 5 ? 5 : 4;
        int is = sp.getInt(Cfg.ISIZE, 1);
        isc = is == 0 ? 0.86f : is == 2 ? 1.14f : 1f;
        shape = sp.getInt(Cfg.SHAPE, 0);
        themed = sp.getBoolean(Cfg.THEMED, true);
        labels = sp.getBoolean(Cfg.LABELS, true);
        cstyle = sp.getInt(Cfg.CSTYLE, 0);
        csize = sp.getInt(Cfg.CSIZE, 1);
        calign = sp.getInt(Cfg.CALIGN, 0);
        showDate = sp.getBoolean(Cfg.DATE, true);
        dockMax = Math.max(4, Math.min(6, sp.getInt(Cfg.DOCKMAX, 6)));
        dockPill = sp.getBoolean(Cfg.DOCKPILL, false);
        dockShow = sp.getBoolean(Cfg.DOCKSHOW, true);
        sortMode = sp.getInt(Cfg.SORT, 0);
        group = sp.getBoolean(Cfg.GROUP, true);
        freq = sp.getBoolean(Cfg.FREQ, true);
        groupsOn = sp.getBoolean(Cfg.GROUPSON, true);
        kbd = sp.getBoolean(Cfg.KBD, false);
        swipe = sp.getInt(Cfg.SWIPE, 0);
        badgeMode = sp.getInt(Cfg.BADGE, 0);
        AppCell.badgeMode = badgeMode;

        hiddenIds.clear();
        String h = sp.getString(Cfg.HIDDEN, "");
        if (h != null) for (String k : h.split("\n")) if (!k.isEmpty()) hiddenIds.add(k);

        usage.clear();
        lastUse.clear();
        String u = sp.getString(Cfg.USAGE, "");
        if (u != null) {
            for (String line : u.split("\n")) {
                int e = line.lastIndexOf('=');
                if (e <= 0) continue;
                String v = line.substring(e + 1);
                int bar = v.indexOf('|');
                try {
                    usage.put(line.substring(0, e), Integer.parseInt(bar < 0 ? v : v.substring(0, bar)));
                    if (bar >= 0) lastUse.put(line.substring(0, e), Long.parseLong(v.substring(bar + 1)));
                } catch (NumberFormatException ignored) {
                }
            }
        }

        groups.clear();
        try {
            JSONArray arr = new JSONArray(sp.getString(Cfg.GROUPS, "[]"));
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                Group g = new Group(o.getString("n"));
                JSONArray ids = o.getJSONArray("a");
                for (int j = 0; j < ids.length(); j++) g.ids.add(ids.getString(j));
                groups.add(g);
            }
        } catch (JSONException ignored) {
        }
        appliedRev = sp.getInt(Cfg.REV, 0);
    }

    private void saveGroups() {
        try {
            JSONArray arr = new JSONArray();
            for (Group g : groups) {
                JSONObject o = new JSONObject();
                o.put("n", g.name);
                JSONArray ids = new JSONArray();
                for (String id : g.ids) ids.put(id);
                o.put("a", ids);
                arr.put(o);
            }
            prefs.edit().putString(Cfg.GROUPS, arr.toString()).apply();
        } catch (JSONException ignored) {
        }
    }

    private void saveHidden() {
        StringBuilder sb = new StringBuilder();
        for (String k : hiddenIds) {
            if (sb.length() > 0) sb.append('\n');
            sb.append(k);
        }
        prefs.edit().putString(Cfg.HIDDEN, sb.toString()).apply();
    }

    private void bumpUsage(String id) {
        Integer c = usage.get(id);
        usage.put(id, c == null ? 1 : c + 1);
        lastUse.put(id, System.currentTimeMillis());
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Integer> e : usage.entrySet()) {
            if (sb.length() > 0) sb.append('\n');
            Long l = lastUse.get(e.getKey());
            sb.append(e.getKey()).append('=').append(e.getValue()).append('|').append(l == null ? 0L : l);
        }
        prefs.edit().putString(Cfg.USAGE, sb.toString()).apply();
        usageDirty = true;
    }

    private int cnt(AppInfo a) {
        Integer c = usage.get(a.id);
        return c == null ? 0 : c;
    }

    private long last(AppInfo a) {
        Long l = lastUse.get(a.id);
        return l == null ? 0L : l;
    }

    private List<AppInfo> sorted(List<AppInfo> in) {
        List<AppInfo> r = new ArrayList<>(in);
        final Collator col = Collator.getInstance();
        col.setStrength(Collator.PRIMARY);
        Collections.sort(r, (x, y) -> {
            int c = sortMode == 1 ? Long.compare(last(y), last(x)) : sortMode == 2 ? cnt(y) - cnt(x) : 0;
            return c != 0 ? c : col.compare(x.label, y.label);
        });
        return r;
    }

    private List<AppInfo> frequent(List<AppInfo> vis) {
        List<AppInfo> r = new ArrayList<>();
        for (AppInfo a : vis) if (cnt(a) > 0) r.add(a);
        Collections.sort(r, (x, y) -> cnt(y) - cnt(x));
        return r.size() > 8 ? new ArrayList<>(r.subList(0, 8)) : r;
    }

    private int cellH() {
        return Math.round(dp(labels ? 92 : 80) * isc);
    }

    // ------------------------------------------------------------ ventana y barras

    private void setupWindow() {
        Window w = getWindow();
        if (Build.VERSION.SDK_INT >= 30) w.setDecorFitsSystemWindows(false);
        w.setStatusBarColor(Color.TRANSPARENT);
        w.setNavigationBarColor(Color.TRANSPARENT);
        if (Build.VERSION.SDK_INT >= 29) w.setNavigationBarContrastEnforced(false);
        if (Build.VERSION.SDK_INT >= 28) {
            WindowManager.LayoutParams lp = w.getAttributes();
            lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            w.setAttributes(lp);
        }
    }

    private void applyBars() {
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
            if (l) {
                f |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
                f |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            }
            getWindow().getDecorView().setSystemUiVisibility(f);
        }
    }

    // ------------------------------------------------------------ construcción de la UI

    private int dp(float v) {
        return Math.round(v * density);
    }

    private void rebuildUi() {
        buildUi();
        refreshAll();
        loadApps();
    }

    private void refreshAll() {
        refreshDock();
        buildPages();
    }

    private void buildUi() {
        if (anim != null) anim.cancel();
        if (pageAnim != null) pageAnim.cancel();
        DisplayMetrics dm = getResources().getDisplayMetrics();
        W = dm.widthPixels;
        H = dm.heightPixels;
        density = dm.density;
        loadSettings();
        dockSlot = W / (dockMax + 2);
        menuLayer = null;
        dDragging = false;

        root = new Root(this);
        root.setFocusableInTouchMode(true);
        root.setClipChildren(false);
        addBackground();

        scrim = new View(this);
        scrim.setBackgroundColor(pal.scrim);
        root.addView(scrim, new FrameLayout.LayoutParams(MATCH, MATCH));

        home = new FrameLayout(this);
        home.setClipChildren(false);
        root.addView(home, new FrameLayout.LayoutParams(MATCH, MATCH));
        buildHome();

        drawer = new LinearLayout(this);
        drawer.setOrientation(LinearLayout.VERTICAL);
        drawer.setClipChildren(false);
        root.addView(drawer, new FrameLayout.LayoutParams(MATCH, MATCH));
        buildDrawerShell();

        root.setOnApplyWindowInsetsListener((v, ins) -> {
            int top, bottom;
            if (Build.VERSION.SDK_INT >= 30) {
                Insets sb = ins.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                Insets ime = ins.getInsets(WindowInsets.Type.ime());
                top = sb.top;
                bottom = Math.max(sb.bottom, ime.bottom);
            } else {
                top = ins.getSystemWindowInsetTop();
                bottom = ins.getSystemWindowInsetBottom();
            }
            if (top != insetTop || bottom != insetBottom) {
                insetTop = top;
                insetBottom = bottom;
                applyInsets();
            }
            return ins;
        });

        setContentView(root);
        applyInsets();
        progress = 0f;
        lastBlur = -1;
        setProgress(0f);
        applyBars();
        shownH = "";
        shownM = "";
        shownD = "";
        updateClock();
        root.requestFocus();
    }

    /** Fondo propio: lienzo de papel, color entero o foto (con copia borrosa para el cajón). */
    private void addBackground() {
        bgBlur = null;
        int bgm = prefs.getInt(Cfg.BGMODE, 0);
        boolean photo = bgm == 1 && Cfg.bgFile(this).exists();
        sysBlur = !paper && bgm != 2 && !photo;
        if (paper) {
            root.setBackgroundColor(pal.canvas);
            ImageView art = new ImageView(this);
            art.setScaleType(ImageView.ScaleType.FIT_XY);
            art.setImageBitmap(PaperArt.backdrop(W, H, pal));
            root.addView(art, new FrameLayout.LayoutParams(MATCH, MATCH));
        } else if (bgm == 2) {
            root.setBackgroundColor(prefs.getInt(Cfg.BGCOLOR, 0xFF2B2F3A));
        } else if (photo) {
            Bitmap bmp = BitmapFactory.decodeFile(Cfg.bgFile(this).getAbsolutePath());
            if (bmp != null) {
                ImageView sharp = new ImageView(this);
                sharp.setScaleType(ImageView.ScaleType.FIT_XY);
                sharp.setImageBitmap(bmp);
                root.addView(sharp, new FrameLayout.LayoutParams(MATCH, MATCH));
                bgBlur = new ImageView(this);
                bgBlur.setScaleType(ImageView.ScaleType.FIT_XY);
                bgBlur.setImageBitmap(Bitmap.createScaledBitmap(bmp, Math.max(2, W / 14), Math.max(2, H / 14), true));
                bgBlur.setAlpha(0f);
                root.addView(bgBlur, new FrameLayout.LayoutParams(MATCH, MATCH));
            }
        }
    }

    private void applyInsets() {
        if (home != null) home.setPadding(0, insetTop, 0, insetBottom);
        if (drawer != null) drawer.setPadding(0, insetTop, 0, insetBottom);
    }

    private TextView text(float sp, int color, Typeface tf) {
        TextView t = new TextView(this);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        t.setTypeface(tf);
        t.setIncludeFontPadding(false);
        t.setGravity(Gravity.CENTER);
        return t;
    }

    private void halo(TextView t) {
        t.setShadowLayer(dp(6), 0, 0, Palette.alpha(pal.scrim, 0x90));
    }

    private void emboss(TextView t) {
        t.setShadowLayer(dp(1), 0, dp(1.5f), Palette.alpha(pal.light ? 0xFFFFFFFF : 0xFF000000, 0xC0));
    }

    private GradientDrawable roundRect(int color, float r) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(r);
        return g;
    }

    private void press(View v) {
        v.setOnTouchListener((x, e) -> {
            int a = e.getActionMasked();
            if (a == MotionEvent.ACTION_DOWN) x.setAlpha(0.55f);
            else if (a == MotionEvent.ACTION_UP || a == MotionEvent.ACTION_CANCEL) x.setAlpha(1f);
            return false;
        });
    }

    private TextView btn(String s, boolean cta) {
        TextView t = text(14, cta ? pal.onAccent() : pal.text, Typeface.DEFAULT);
        t.setText(s);
        t.setPadding(dp(18), dp(10), dp(18), dp(10));
        if (paper) {
            t.setBackground(cta ? roundRect(pal.accent, dp(20)) : Paper.card(pal, dp(20)));
            Paper.raise(t, pal, dp(4), dp(20));
            Paper.pressable(t, dp(4));
        } else {
            t.setBackground(roundRect(cta ? pal.accent : pal.plate, dp(20)));
        }
        return t;
    }

    private void buildHome() {
        Typeface light = Typeface.create("sans-serif-light", Typeface.NORMAL);
        boolean line = cstyle == 1;
        boolean left = calign == 1;
        float[] sizes = line ? new float[]{56, 72, 92} : new float[]{88, 112, 136};
        float sp = sizes[Math.max(0, Math.min(2, csize))];
        hourTv = text(sp, pal.text, light);
        minTv = text(sp, paper ? pal.textDim : pal.accent, light);
        if (paper) {
            emboss(hourTv);
            emboss(minTv);
        } else {
            halo(hourTv);
            halo(minTv);
        }

        LinearLayout clockBox = new LinearLayout(this);
        clockBox.setOrientation(line ? LinearLayout.HORIZONTAL : LinearLayout.VERTICAL);
        clockBox.setGravity(line ? Gravity.CENTER_VERTICAL : (left ? Gravity.START : Gravity.CENTER_HORIZONTAL));
        if (paper) {
            clockBox.setBackground(Paper.card(pal, dp(34)));
            Paper.raise(clockBox, pal, dp(10), dp(34));
            clockBox.setPadding(dp(34), dp(14), dp(34), dp(14));
        } else {
            clockBox.setPadding(dp(left ? 6 : 28), dp(4), dp(28), dp(4));
        }
        clockBox.addView(hourTv, new LinearLayout.LayoutParams(WRAP, WRAP));
        if (line) {
            TextView colon = text(sp, pal.textDim, light);
            colon.setText(":");
            if (paper) emboss(colon);
            else halo(colon);
            clockBox.addView(colon, new LinearLayout.LayoutParams(WRAP, WRAP));
            clockBox.addView(minTv, new LinearLayout.LayoutParams(WRAP, WRAP));
        } else {
            LinearLayout.LayoutParams mlp = new LinearLayout.LayoutParams(WRAP, WRAP);
            mlp.topMargin = -dp(sp * 0.2f);
            clockBox.addView(minTv, mlp);
        }
        clockBox.setOnClickListener(v -> openClock());
        press(clockBox);

        dateTv = text(16, pal.text, Typeface.create("sans-serif-medium", Typeface.NORMAL));
        dateTv.setLetterSpacing(0.04f);
        if (paper) {
            dateTv.setBackground(Paper.card(pal, dp(18)));
            Paper.raise(dateTv, pal, dp(5), dp(18));
            dateTv.setPadding(dp(18), dp(8), dp(18), dp(8));
        } else {
            halo(dateTv);
            dateTv.setPadding(dp(left ? 8 : 20), dp(8), dp(20), dp(8));
        }
        dateTv.setOnClickListener(v -> openCalendar());
        dateTv.setVisibility(showDate ? View.VISIBLE : View.GONE);
        press(dateTv);

        LinearLayout widget = new LinearLayout(this);
        widget.setOrientation(LinearLayout.VERTICAL);
        widget.setGravity(left ? Gravity.START : Gravity.CENTER_HORIZONTAL);
        widget.setClipChildren(false);
        widget.setClipToPadding(false);
        widget.setPadding(dp(16), dp(10), dp(16), dp(16));
        widget.addView(clockBox, WRAP, WRAP);
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(WRAP, WRAP);
        dlp.topMargin = dp(paper ? 18 : 0);
        widget.addView(dateTv, dlp);
        FrameLayout.LayoutParams wl = new FrameLayout.LayoutParams(WRAP, WRAP,
                Gravity.TOP | (left ? Gravity.START : Gravity.CENTER_HORIZONTAL));
        wl.topMargin = Math.round(H * 0.09f);
        if (left) wl.leftMargin = dp(10);
        home.addView(widget, wl);

        dock = new LinearLayout(this);
        dock.setOrientation(LinearLayout.HORIZONTAL);
        dock.setGravity(Gravity.CENTER_VERTICAL);
        dock.setClipChildren(false);
        if (paper) {
            dock.setBackground(Paper.card(pal, dp(32)));
            Paper.raise(dock, pal, dp(14), dp(32)); // barra flotante: sombra más profunda
            dock.setPadding(dp(8), 0, dp(8), 0);
        } else if (dockPill) {
            dock.setBackground(roundRect(pal.plate, dp(30)));
            dock.setPadding(dp(6), 0, dp(6), 0);
        }
        dock.setVisibility(dockShow ? View.VISIBLE : View.GONE);
        FrameLayout.LayoutParams dl = new FrameLayout.LayoutParams(WRAP, dp(paper ? 66 : 60),
                Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        dl.bottomMargin = dp(28);
        home.addView(dock, dl);
    }

    private void buildDrawerShell() {
        search = new EditText(this);
        search.setHint(L.t("Buscar", "Search"));
        search.setSingleLine(true);
        search.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        search.setTextColor(pal.text);
        search.setHintTextColor(pal.textDim);
        if (paper) {
            search.setBackground(Paper.card(pal, dp(26)));
            Paper.raise(search, pal, dp(10), dp(26));
        } else {
            search.setBackground(roundRect(pal.plate, dp(24)));
        }
        search.setPadding(dp(22), 0, dp(22), 0);
        search.setGravity(Gravity.CENTER_VERTICAL);
        search.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        search.setImeOptions(EditorInfo.IME_ACTION_GO | EditorInfo.IME_FLAG_NO_EXTRACT_UI);
        search.setHighlightColor(Palette.alpha(pal.accent, 0x66));
        search.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {
            }

            @Override
            public void onTextChanged(CharSequence s, int a, int b, int c) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                onQuery(s.toString());
            }
        });
        search.setOnEditorActionListener((v, id, ev) -> {
            launchFirstResult();
            return true;
        });
        LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(MATCH, dp(50));
        sl.setMargins(dp(paper ? 26 : 20), dp(paper ? 14 : 10), dp(paper ? 26 : 20), dp(paper ? 4 : 0));
        drawer.addView(search, sl);

        dots = new Dots(this, pal);
        LinearLayout.LayoutParams dl = new LinearLayout.LayoutParams(WRAP, dp(26));
        dl.gravity = Gravity.CENTER_HORIZONTAL;
        dl.topMargin = dp(6);
        drawer.addView(dots, dl);

        FrameLayout content = new FrameLayout(this);
        content.setClipChildren(false);
        pagerHost = new FrameLayout(this);
        pagerHost.setClipChildren(true);
        pagesRow = new LinearLayout(this);
        pagesRow.setOrientation(LinearLayout.HORIZONTAL);
        pagesRow.setClipChildren(false);
        pagerHost.addView(pagesRow, new FrameLayout.LayoutParams(W, MATCH));
        content.addView(pagerHost, new FrameLayout.LayoutParams(MATCH, MATCH));

        resultsScroll = scroller();
        resultsGrid = new ReverseGrid(this, cols, cellH(), false);
        resultsScroll.addView(resultsGrid, new ViewGroup.LayoutParams(MATCH, WRAP));
        resultsHost = wrapCard(resultsScroll);
        resultsHost.setVisibility(View.GONE);
        content.addView(resultsHost, new FrameLayout.LayoutParams(MATCH, MATCH));

        drawer.addView(content, new LinearLayout.LayoutParams(MATCH, 0, 1f));
        pageScrolls.clear();
        pageCount = 0;
        pagePos = 0f;
    }

    private BottomScroll scroller() {
        BottomScroll s = new BottomScroll(this);
        s.setFillViewport(true);
        s.setVerticalScrollBarEnabled(false);
        s.setOverScrollMode(View.OVER_SCROLL_NEVER);
        s.setClipToPadding(false);
        s.setPadding(0, 0, 0, dp(14));
        return s;
    }

    /** Estilo Papel: la página es una tarjeta con relieve sobre el lienzo. */
    private View wrapCard(View inner) {
        if (!paper) return inner;
        FrameLayout w = new FrameLayout(this);
        w.setPadding(dp(16), dp(6), dp(16), dp(14));
        w.setClipChildren(false);
        w.setClipToPadding(false);
        inner.setBackground(Paper.card(pal, dp(28)));
        Paper.raise(inner, pal, dp(6), dp(28));
        inner.setClipToOutline(true);
        w.addView(inner, new FrameLayout.LayoutParams(MATCH, MATCH));
        return w;
    }

    private AppCell cell(AppInfo a, boolean inDock) {
        boolean lab = !inDock && labels;
        int icon = inDock ? Math.round(dockSlot * 0.74f) : Math.round(dp(50) * isc);
        if (paper) icon = Math.round(icon * 1.22f); // el ícono de papel lleva margen de sombra
        AppCell c = new AppCell(this, a, pal, lab, icon);
        c.setOnClickListener(v -> launch(a, v));
        if (inDock) {
            c.setOnLongClickListener(v -> {
                startDockDrag(v);
                return true;
            });
            c.setOnTouchListener((v, e) -> dockTouch(v, a, e));
        } else {
            c.setOnLongClickListener(v -> {
                v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                appMenu(v, a, false);
                return true;
            });
        }
        return c;
    }

    private void invalidateTree(View v) {
        if (v == null) return;
        v.invalidate();
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) invalidateTree(g.getChildAt(i));
        }
    }

    private void refreshBadges() {
        if (badgeMode == 0) return;
        invalidateTree(dock);
        invalidateTree(pagesRow);
        invalidateTree(resultsGrid);
    }

    // ------------------------------------------------------------ paginador de categorías

    private void setPagePos(float p) {
        pagePos = p;
        if (pagesRow != null) pagesRow.setTranslationX(-p * W);
        if (dots != null) dots.setPos(p);
    }

    private int curPage() {
        return Math.max(0, Math.min(Math.max(0, pageCount - 1), Math.round(pagePos)));
    }

    private float rubber(float pos) {
        float max = pageCount - 1;
        if (pos < 0f) return pos * 0.35f;
        if (pos > max) return max + (pos - max) * 0.35f;
        return pos;
    }

    private void animatePage(int target) {
        if (pageAnim != null) pageAnim.cancel();
        float from = pagePos;
        float dist = Math.abs(target - from);
        if (dist < 0.001f) {
            setPagePos(target);
            return;
        }
        pageAnim = ValueAnimator.ofFloat(from, target);
        pageAnim.setDuration((long) (140 + 200 * Math.min(1f, dist)));
        pageAnim.setInterpolator(new DecelerateInterpolator(1.8f));
        pageAnim.addUpdateListener(a -> setPagePos((float) a.getAnimatedValue()));
        pageAnim.start();
    }

    private void buildPages() {
        if (pagesRow == null) return;
        pagesRow.removeAllViews();
        pageScrolls.clear();

        Set<String> placed = new HashSet<>(dockShown);
        if (groupsOn) for (Group g : groups) placed.addAll(g.ids);
        List<AppInfo> vis = new ArrayList<>();
        for (AppInfo a : apps) if (!hiddenIds.contains(a.id) && !placed.contains(a.id)) vis.add(a);

        int n = 0;
        for (String id : Cfg.order(prefs)) {
            View page = null;
            if (id.equals("freq")) {
                if (freq) {
                    List<AppInfo> fr = frequent(vis);
                    if (!fr.isEmpty()) page = makePage(Cfg.pageName(id), fr);
                }
            } else if (id.equals("all")) {
                if (!group && !vis.isEmpty()) page = makePage(Cfg.pageName(id), sorted(vis));
            } else if (id.equals("groups")) {
                if (groupsOn) page = makeGroupsPage();
            } else if (group) {
                int c = id.charAt(1) - '0';
                List<AppInfo> g = new ArrayList<>();
                for (AppInfo a : vis) if (a.cat == c) g.add(a);
                if (!g.isEmpty()) page = makePage(Cfg.pageName(id), sorted(g));
            }
            if (page != null) {
                pagesRow.addView(wrapCard(page), new LinearLayout.LayoutParams(W, MATCH));
                n++;
            }
        }
        pageCount = n;
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) pagesRow.getLayoutParams();
        lp.width = Math.max(1, n) * W;
        pagesRow.setLayoutParams(lp);
        dots.setCount(n);
        setPagePos(Math.max(0, Math.min(Math.round(pagePos), Math.max(0, n - 1))));
    }

    private TextView pageTitle(String s) {
        TextView t = text(12, pal.textDim, Typeface.create("sans-serif-medium", Typeface.NORMAL));
        t.setLetterSpacing(0.14f);
        t.setGravity(Gravity.START);
        t.setText(s.toUpperCase(Locale.getDefault()));
        t.setPadding(dp(28), dp(paper ? 16 : 6), dp(28), dp(6));
        return t;
    }

    private View makePage(String title, List<AppInfo> list) {
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        BottomScroll sv = scroller();
        ReverseGrid g = new ReverseGrid(this, cols, cellH(), false);
        for (AppInfo a : list) g.addView(cell(a, false));
        sv.addView(g, new ViewGroup.LayoutParams(MATCH, WRAP));
        pageScrolls.add(sv);
        page.addView(pageTitle(title + "  ·  " + list.size()), new LinearLayout.LayoutParams(MATCH, WRAP));
        page.addView(sv, new LinearLayout.LayoutParams(MATCH, 0, 1f));
        return page;
    }

    // ------------------------------------------------------------ grupos

    private View makeGroupsPage() {
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        BottomScroll sv = scroller();
        LinearLayout gcol = new LinearLayout(this);
        gcol.setOrientation(LinearLayout.VERTICAL);
        gcol.setGravity(Gravity.BOTTOM);
        gcol.setClipChildren(false);
        for (final Group g : groups) {
            List<AppInfo> list = new ArrayList<>();
            for (String id : g.ids) {
                AppInfo a = byId.get(id);
                if (a != null) list.add(a);
            }
            TextView h = text(13, pal.text, Typeface.create("sans-serif-medium", Typeface.NORMAL));
            h.setGravity(Gravity.START);
            h.setText(g.name + "  ·  " + list.size());
            h.setPadding(dp(28), dp(16), dp(28), dp(4));
            h.setOnLongClickListener(v -> {
                v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                groupMenu(v, g);
                return true;
            });
            gcol.addView(h, MATCH, WRAP);
            ReverseGrid grid = new ReverseGrid(this, cols, cellH(), true);
            for (AppInfo a : sorted(list)) grid.addView(cell(a, false));
            gcol.addView(grid, MATCH, WRAP);
        }
        TextView add = btn("＋  " + L.t("Nuevo grupo", "New group"), true);
        add.setOnClickListener(v -> promptText(L.t("Nuevo grupo", "New group"), "", name -> {
            groups.add(new Group(name));
            saveGroups();
            buildPages();
            animatePage(Math.max(0, pageCount - 1));
        }));
        LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(WRAP, WRAP);
        alp.gravity = Gravity.CENTER_HORIZONTAL;
        alp.topMargin = dp(20);
        alp.bottomMargin = dp(8);
        gcol.addView(add, alp);
        sv.addView(gcol, new ViewGroup.LayoutParams(MATCH, WRAP));
        pageScrolls.add(sv);
        page.addView(pageTitle(Cfg.pageName("groups") + "  ·  " + groups.size()),
                new LinearLayout.LayoutParams(MATCH, WRAP));
        page.addView(sv, new LinearLayout.LayoutParams(MATCH, 0, 1f));
        return page;
    }

    private int groupIndexOf(String id) {
        if (!groupsOn) return -1;
        for (int i = 0; i < groups.size(); i++) if (groups.get(i).ids.contains(id)) return i;
        return -1;
    }

    private void removeFromGroups(String id) {
        for (Group g : groups) g.ids.remove(id);
    }

    private void removeFromDock(String id) {
        List<String> ids = new ArrayList<>(dockShown);
        ids.remove(id);
        saveDock(ids);
    }

    private void moveToDock(String id) {
        if (dockShown.contains(id)) return;
        if (dockShown.size() >= dockMax) {
            toast(L.t("El dock está lleno (máx. " + dockMax + ")", "Dock is full (max " + dockMax + ")"));
            return;
        }
        removeFromGroups(id);
        saveGroups();
        hiddenIds.remove(id);
        saveHidden();
        List<String> ids = new ArrayList<>(dockShown);
        ids.add(id);
        saveDock(ids);
        refreshAll();
    }

    private void addToGroup(int gi, String id) {
        removeFromDock(id);
        removeFromGroups(id);
        hiddenIds.remove(id);
        saveHidden();
        groups.get(gi).ids.add(id);
        saveGroups();
        refreshAll();
    }

    private void groupPicker(View v, final AppInfo a) {
        List<String> names = new ArrayList<>();
        List<Runnable> acts = new ArrayList<>();
        for (int i = 0; i < groups.size(); i++) {
            final int gi = i;
            names.add(groups.get(i).name);
            acts.add(() -> addToGroup(gi, a.id));
        }
        names.add("＋  " + L.t("Nuevo grupo…", "New group…"));
        acts.add(() -> promptText(L.t("Nuevo grupo", "New group"), "", name -> {
            groups.add(new Group(name));
            addToGroup(groups.size() - 1, a.id);
        }));
        int[] loc = new int[2];
        v.getLocationInWindow(loc);
        showMenu(loc[0] + v.getWidth() / 2f, loc[1], loc[1] + v.getHeight(),
                names.toArray(new String[0]), acts.toArray(new Runnable[0]));
    }

    private void groupMenu(View v, final Group g) {
        int[] loc = new int[2];
        v.getLocationInWindow(loc);
        showMenu(loc[0] + dp(90), loc[1], loc[1] + v.getHeight(),
                new String[]{L.t("Renombrar", "Rename"), L.t("Eliminar grupo", "Delete group")},
                new Runnable[]{
                        () -> promptText(L.t("Renombrar grupo", "Rename group"), g.name, name -> {
                            g.name = name;
                            saveGroups();
                            buildPages();
                        }),
                        () -> {
                            groups.remove(g);
                            saveGroups();
                            refreshAll();
                        }
                });
    }

    private void promptText(String title, String initial, final Consumer<String> ok) {
        dismissMenu();
        FrameLayout layer = new FrameLayout(this);
        layer.setBackgroundColor(0x66000000);
        layer.setClickable(true);
        layer.setOnClickListener(v -> {
            dismissMenu();
            hideKeyboard();
        });

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(22), dp(20), dp(22), dp(16));
        card.setClickable(true);
        if (paper) {
            card.setBackground(Paper.card(pal, dp(26)));
            Paper.raise(card, pal, dp(16), dp(26));
        } else {
            GradientDrawable bg = roundRect(pal.surface, dp(22));
            bg.setStroke(Math.max(1, dp(1)), Palette.alpha(pal.text, 0x22));
            card.setBackground(bg);
        }
        TextView t = text(16, pal.text, Typeface.create("sans-serif-medium", Typeface.NORMAL));
        t.setGravity(Gravity.START);
        t.setText(title);
        card.addView(t, MATCH, WRAP);

        final EditText et = new EditText(this);
        et.setText(initial);
        et.setSingleLine(true);
        et.setTextColor(pal.text);
        et.setHintTextColor(pal.textDim);
        et.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        et.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        et.setHighlightColor(Palette.alpha(pal.accent, 0x66));
        et.setSelection(et.getText().length());
        LinearLayout.LayoutParams elp = new LinearLayout.LayoutParams(MATCH, WRAP);
        elp.topMargin = dp(10);
        card.addView(et, elp);

        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.END);
        row.setClipChildren(false);
        TextView cancel = btn(L.t("Cancelar", "Cancel"), false);
        cancel.setOnClickListener(v -> {
            dismissMenu();
            hideKeyboard();
        });
        TextView okb = btn(L.t("Aceptar", "OK"), true);
        okb.setOnClickListener(v -> {
            String s = et.getText().toString().trim();
            dismissMenu();
            hideKeyboard();
            if (!s.isEmpty()) ok.accept(s);
        });
        LinearLayout.LayoutParams b1 = new LinearLayout.LayoutParams(WRAP, WRAP);
        b1.rightMargin = dp(10);
        row.addView(cancel, b1);
        row.addView(okb, new LinearLayout.LayoutParams(WRAP, WRAP));
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(MATCH, WRAP);
        rlp.topMargin = dp(16);
        card.addView(row, rlp);

        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(W - dp(56), WRAP,
                Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        lp.topMargin = Math.round(H * 0.16f);
        layer.addView(card, lp);
        root.addView(layer, new FrameLayout.LayoutParams(MATCH, MATCH));
        menuLayer = layer;

        et.requestFocus();
        et.postDelayed(() -> {
            InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            if (imm != null) imm.showSoftInput(et, 0);
        }, 150);
    }

    // ------------------------------------------------------------ reloj y fecha

    private String two(int v) {
        return v < 10 ? "0" + v : String.valueOf(v);
    }

    private String dateText(Calendar cal) {
        Locale loc = Locale.getDefault();
        Date d = cal.getTime();
        if ("es".equals(loc.getLanguage())) {
            String wd = new SimpleDateFormat("EEE", loc).format(d).replace(".", "");
            String mo = new SimpleDateFormat("MMM", loc).format(d).replace(".", "");
            return (wd + ", " + cal.get(Calendar.DAY_OF_MONTH) + " de " + mo + ".").toLowerCase(loc);
        }
        return DateUtils.formatDateTime(this, d.getTime(), DateUtils.FORMAT_SHOW_WEEKDAY
                | DateUtils.FORMAT_SHOW_DATE | DateUtils.FORMAT_ABBREV_ALL | DateUtils.FORMAT_NO_YEAR);
    }

    private void updateClock() {
        if (hourTv == null) return;
        Calendar cal = Calendar.getInstance();
        boolean h24 = DateFormat.is24HourFormat(this);
        int hr = cal.get(h24 ? Calendar.HOUR_OF_DAY : Calendar.HOUR);
        if (!h24 && hr == 0) hr = 12;
        String h = two(hr), m = two(cal.get(Calendar.MINUTE)), d = dateText(cal);
        if (!h.equals(shownH)) {
            shownH = h;
            hourTv.setText(h);
        }
        if (!m.equals(shownM)) {
            shownM = m;
            minTv.setText(m);
        }
        if (!d.equals(shownD)) {
            shownD = d;
            dateTv.setText(d);
        }
    }

    private void openClock() {
        try {
            startActivity(new Intent(AlarmClock.ACTION_SHOW_ALARMS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (Exception e) {
            openByKeyword("clock");
        }
    }

    private void openCalendar() {
        try {
            Intent i = Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_CALENDAR);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
        } catch (Exception e) {
            try {
                Uri u = Uri.parse("content://com.android.calendar/time/" + System.currentTimeMillis());
                startActivity(new Intent(Intent.ACTION_VIEW, u).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            } catch (Exception e2) {
                openByKeyword("calendar");
            }
        }
    }

    private void openByKeyword(String k) {
        for (AppInfo a : apps) {
            if (a.pkg.toLowerCase(Locale.ROOT).contains(k)) {
                launch(a, null);
                return;
            }
        }
        toast(L.t("No se encontró la app", "App not found"));
    }

    private void openSettings() {
        try {
            startActivity(new Intent(this, SettingsActivity.class));
        } catch (Exception e) {
            toast(L.t("No disponible", "Unavailable"));
        }
    }

    private void lockScreen() {
        if (!LockService.lock()) {
            toast(L.t("Activa Aire en Accesibilidad para apagar con doble toque",
                    "Enable Aire in Accessibility to lock with a double tap"));
            startSafe(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
        }
    }

    // ------------------------------------------------------------ apps, dock y búsqueda

    private void loadApps() {
        final Palette p = pal;
        final int px = dp(60);
        final boolean th = themed;
        final int sh = shape;
        io.execute(() -> {
            try {
                PackageManager pm = getPackageManager();
                Intent q = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
                List<ResolveInfo> ris = pm.queryIntentActivities(q, 0);
                final List<AppInfo> list = new ArrayList<>();
                for (ResolveInfo ri : ris) {
                    ActivityInfo ai = ri.activityInfo;
                    if (ai == null || ai.packageName.equals(getPackageName())) continue;
                    AppInfo app = new AppInfo(new ComponentName(ai.packageName, ai.name),
                            String.valueOf(ri.loadLabel(pm)), ai.applicationInfo);
                    try {
                        app.icon = Icons.render(ri.loadIcon(pm), px, p, th, sh);
                    } catch (Throwable ignored) {
                    }
                    list.add(app);
                }
                final Collator col = Collator.getInstance();
                col.setStrength(Collator.PRIMARY);
                Collections.sort(list, (x, y) -> col.compare(x.label, y.label));
                ensureDefaultDock(list, pm);
                ui.post(() -> {
                    if (isDestroyed()) return;
                    apps = list;
                    byId.clear();
                    for (AppInfo it : list) byId.put(it.id, it);
                    refreshAll();
                });
            } catch (Throwable ignored) {
            }
        });
    }

    private void ensureDefaultDock(List<AppInfo> list, PackageManager pm) {
        if (prefs.contains("dock")) return;
        Intent[] probes = {
                new Intent(Intent.ACTION_DIAL, Uri.parse("tel:")),
                new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_MESSAGING),
                new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_BROWSER),
                new Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        };
        List<String> ids = new ArrayList<>();
        for (Intent it : probes) {
            ResolveInfo ri = pm.resolveActivity(it, PackageManager.MATCH_DEFAULT_ONLY);
            if (ri == null || ri.activityInfo == null) continue;
            String pkg = ri.activityInfo.packageName;
            if ("android".equals(pkg)) continue;
            for (AppInfo a : list) {
                if (a.pkg.equals(pkg)) {
                    if (!ids.contains(a.id)) ids.add(a.id);
                    break;
                }
            }
        }
        if (!ids.isEmpty()) saveDock(ids);
    }

    private List<String> dockIds() {
        List<String> r = new ArrayList<>();
        String s = prefs.getString("dock", "");
        if (s != null) for (String k : s.split("\n")) if (!k.isEmpty()) r.add(k);
        return r;
    }

    private void saveDock(List<String> ids) {
        StringBuilder sb = new StringBuilder();
        for (String k : ids) {
            if (sb.length() > 0) sb.append('\n');
            sb.append(k);
        }
        prefs.edit().putString("dock", sb.toString()).apply();
    }

    private void refreshDock() {
        if (dock == null) return;
        dock.removeAllViews();
        dockShown.clear();
        for (String id : dockIds()) {
            AppInfo a = byId.get(id);
            if (a == null) continue;
            if (dockShown.size() >= dockMax) break;
            dockShown.add(id);
            dock.addView(cell(a, true), new LinearLayout.LayoutParams(dockSlot, dp(paper ? 66 : 60)));
        }
    }

    // ---- arrastrar para reordenar el dock

    private boolean dockTouch(View v, AppInfo a, MotionEvent e) {
        int act = e.getActionMasked();
        float rx = e.getRawX();
        if (act == MotionEvent.ACTION_DOWN) {
            dDragging = false;
            dLastRaw = rx;
            return false;
        }
        if (!dDragging) {
            if (act == MotionEvent.ACTION_MOVE) dLastRaw = rx;
            return false;
        }
        if (act == MotionEvent.ACTION_MOVE) {
            dockDrag(v, rx - dLastRaw);
            dLastRaw = rx;
        } else if (act == MotionEvent.ACTION_UP || act == MotionEvent.ACTION_CANCEL) {
            dockDrop(v, a, act == MotionEvent.ACTION_UP);
        }
        return true;
    }

    private void startDockDrag(View v) {
        dDragging = true;
        dFrom = dock.indexOfChild(v);
        dTo = dFrom;
        v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
        if (v.getParent() != null) v.getParent().requestDisallowInterceptTouchEvent(true);
        v.setTranslationZ(dp(6));
        v.animate().scaleX(1.25f).scaleY(1.25f).setDuration(90).start();
    }

    private void dockDrag(View v, float dx) {
        int n = dock.getChildCount();
        int slot = dockSlot;
        float t = v.getTranslationX() + dx;
        t = Math.max(-dFrom * slot, Math.min((n - 1 - dFrom) * slot, t));
        v.setTranslationX(t);
        int to = (int) Math.floor((dFrom * slot + t + slot / 2f) / slot);
        to = Math.max(0, Math.min(n - 1, to));
        if (to != dTo) {
            dTo = to;
            for (int i = 0; i < n; i++) {
                if (i == dFrom) continue;
                float shift = 0f;
                if (i > dFrom && i <= to) shift = -slot;
                else if (i < dFrom && i >= to) shift = slot;
                dock.getChildAt(i).animate().translationX(shift).setDuration(130).start();
            }
        }
    }

    private void resetDockVisuals() {
        for (int i = 0; i < dock.getChildCount(); i++) {
            View c = dock.getChildAt(i);
            c.setTranslationZ(0f);
            c.animate().translationX(0f).scaleX(1f).scaleY(1f).setDuration(100).start();
        }
    }

    private void dockDrop(View v, AppInfo a, boolean up) {
        dDragging = false;
        v.setPressed(false);
        if (!up) {
            refreshDock();
            return;
        }
        if (dTo == dFrom && Math.abs(v.getTranslationX()) < dp(10)) {
            resetDockVisuals();
            appMenu(v, a, true);
            return;
        }
        final int from = dFrom, to = dTo;
        List<String> ids = new ArrayList<>(dockShown);
        if (from >= 0 && from < ids.size()) {
            String id = ids.remove(from);
            ids.add(Math.min(to, ids.size()), id);
            saveDock(ids);
        }
        v.animate().translationX((to - from) * dockSlot).scaleX(1f).scaleY(1f).setDuration(110)
                .withEndAction(this::refreshDock).start();
    }

    // ---- búsqueda

    private void onQuery(String s) {
        String q = AppInfo.norm(s.trim());
        if (q.isEmpty()) {
            currentResults = new ArrayList<>();
            resultsHost.setVisibility(View.GONE);
            pagerHost.setVisibility(View.VISIBLE);
            dots.setVisibility(View.VISIBLE);
            return;
        }
        List<AppInfo> starts = new ArrayList<>();
        List<AppInfo> contains = new ArrayList<>();
        for (AppInfo a : apps) { // incluye apps ocultas, en grupos y en el dock
            if (a.key.startsWith(q)) starts.add(a);
            else if (a.key.contains(q)) contains.add(a);
        }
        starts.addAll(contains);
        currentResults = starts;
        resultsGrid.removeAllViews();
        for (AppInfo a : currentResults) resultsGrid.addView(cell(a, false));
        resultsHost.setVisibility(View.VISIBLE);
        resultsScroll.toStart();
        pagerHost.setVisibility(View.GONE);
        dots.setVisibility(View.INVISIBLE);
    }

    private void launchFirstResult() {
        hideKeyboard();
        if (!currentResults.isEmpty()) launch(currentResults.get(0), null);
    }

    private void launch(AppInfo a, View v) {
        Intent i = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                .setComponent(a.cn)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        Bundle opts = null;
        if (v != null) {
            try {
                opts = ActivityOptions.makeScaleUpAnimation(v, 0, 0, v.getWidth(), v.getHeight()).toBundle();
            } catch (Throwable ignored) {
            }
        }
        try {
            startActivity(i, opts);
            bumpUsage(a.id);
        } catch (Exception e) {
            toast(L.t("No se pudo abrir la app", "Couldn't open the app"));
        }
    }

    private void startSafe(Intent i) {
        try {
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
        } catch (Exception e) {
            toast(L.t("No disponible", "Unavailable"));
        }
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }

    // ------------------------------------------------------------ menús flotantes

    private void appMenu(View v, final AppInfo a, boolean inDock) {
        List<String> names = new ArrayList<>();
        List<Runnable> acts = new ArrayList<>();
        final int gi = groupIndexOf(a.id);
        if (inDock) {
            names.add(L.t("Quitar del dock", "Remove from dock"));
            acts.add(() -> {
                removeFromDock(a.id);
                refreshAll();
            });
        } else {
            names.add(L.t("Agregar al dock", "Add to dock"));
            acts.add(() -> moveToDock(a.id));
        }
        if (gi >= 0) {
            names.add(L.t("Quitar del grupo", "Remove from group"));
            acts.add(() -> {
                groups.get(gi).ids.remove(a.id);
                saveGroups();
                refreshAll();
            });
        }
        if (groupsOn) {
            names.add(L.t("Agregar a grupo…", "Add to group…"));
            acts.add(() -> {
                View anchor = v;
                groupPicker(anchor, a);
            });
        }
        if (!inDock && gi < 0) {
            final boolean hid = hiddenIds.contains(a.id);
            names.add(hid ? L.t("Mostrar en el cajón", "Show in drawer") : L.t("Ocultar del cajón", "Hide from drawer"));
            acts.add(() -> {
                if (hid) hiddenIds.remove(a.id);
                else hiddenIds.add(a.id);
                saveHidden();
                buildPages();
            });
        }
        names.add(L.t("Información de la app", "App info"));
        acts.add(() -> startSafe(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:" + a.pkg))));
        if (!a.system) {
            names.add(L.t("Desinstalar", "Uninstall"));
            acts.add(() -> startSafe(new Intent(Intent.ACTION_DELETE, Uri.parse("package:" + a.pkg))));
        }
        int[] loc = new int[2];
        v.getLocationInWindow(loc);
        showMenu(loc[0] + v.getWidth() / 2f, loc[1], loc[1] + v.getHeight(),
                names.toArray(new String[0]), acts.toArray(new Runnable[0]));
    }

    private void homeMenu(float x, float y) {
        showMenu(x, y - dp(8), y + dp(8),
                new String[]{L.t("Personalizar", "Customize"), L.t("Ajustes del teléfono", "Phone settings")},
                new Runnable[]{
                        this::openSettings,
                        () -> startSafe(new Intent(Settings.ACTION_SETTINGS))
                });
    }

    private void showMenu(float cx, float top, float bottom, String[] items, Runnable[] acts) {
        dismissMenu();
        FrameLayout layer = new FrameLayout(this);
        layer.setClipChildren(false);
        layer.setOnClickListener(v -> dismissMenu());

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        if (paper) {
            card.setBackground(Paper.card(pal, dp(22)));
            Paper.raise(card, pal, dp(18), dp(22)); // capa superior: sombra más profunda
        } else {
            GradientDrawable bg = roundRect(pal.surface, dp(18));
            bg.setStroke(Math.max(1, dp(1)), Palette.alpha(pal.text, 0x22));
            card.setBackground(bg);
            card.setElevation(dp(8));
        }
        card.setPadding(0, dp(6), 0, dp(6));
        card.setMinimumWidth(dp(190));
        for (int i = 0; i < items.length; i++) {
            final int idx = i;
            TextView t = text(15, pal.text, Typeface.DEFAULT);
            t.setText(items[i]);
            t.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
            t.setPadding(dp(20), dp(13), dp(20), dp(13));
            t.setOnClickListener(v -> {
                dismissMenu();
                acts[idx].run();
            });
            press(t);
            card.addView(t, new LinearLayout.LayoutParams(MATCH, WRAP));
        }
        card.measure(View.MeasureSpec.makeMeasureSpec(W - dp(24), View.MeasureSpec.AT_MOST),
                View.MeasureSpec.makeMeasureSpec(H, View.MeasureSpec.AT_MOST));
        int cw = card.getMeasuredWidth(), ch = card.getMeasuredHeight();
        int x = Math.max(dp(12), Math.min(W - cw - dp(12), Math.round(cx - cw / 2f)));
        int y = Math.round(top) - ch - dp(6);
        if (y < insetTop + dp(8)) y = Math.round(bottom) + dp(6);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(cw, ch);
        lp.leftMargin = x;
        lp.topMargin = y;
        layer.addView(card, lp);
        root.addView(layer, new FrameLayout.LayoutParams(MATCH, MATCH));
        menuLayer = layer;

        card.setAlpha(0f);
        card.setScaleX(0.92f);
        card.setScaleY(0.92f);
        card.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(120).start();
    }

    private void dismissMenu() {
        if (menuLayer != null && root != null) {
            root.removeView(menuLayer);
        }
        menuLayer = null;
    }

    // ------------------------------------------------------------ cajón de apps (animación y fondo)

    private float clamp(float v) {
        return v < 0f ? 0f : Math.min(v, 1f);
    }

    private void setProgress(float p) {
        progress = p;
        drawer.setVisibility(p > 0.001f ? View.VISIBLE : View.GONE);
        home.setVisibility(p < 0.999f ? View.VISIBLE : View.INVISIBLE);
        drawer.setTranslationY((1f - p) * dp(72));
        drawer.setAlpha(p);
        home.setAlpha(1f - Math.min(1f, p * 1.6f));
        float sc = 1f - 0.05f * p;
        home.setScaleX(sc);
        home.setScaleY(sc);
        scrim.setAlpha(paper ? 0f : pal.homeDim + (pal.drawerDim - pal.homeDim) * p);
        if (bgBlur != null) bgBlur.setAlpha(p);
        if (sysBlur && Build.VERSION.SDK_INT >= 31) {
            int r = Math.round(p * dp(26));
            if (r != lastBlur) {
                lastBlur = r;
                try {
                    getWindow().setBackgroundBlurRadius(r);
                } catch (Throwable ignored) {
                }
            }
        }
    }

    private void setLayers(boolean on) {
        int t = on ? View.LAYER_TYPE_HARDWARE : View.LAYER_TYPE_NONE;
        home.setLayerType(t, null);
        drawer.setLayerType(t, null);
    }

    private void resetDrawer() {
        if (search.getText().length() > 0) search.setText("");
        search.clearFocus();
        if (pageAnim != null) pageAnim.cancel();
        setPagePos(0f);
        for (BottomScroll s : pageScrolls) s.toStart();
    }

    private void prepareOpen() {
        if (progress < 0.01f) resetDrawer();
    }

    private void animateTo(final float target) {
        if (anim != null) anim.cancel();
        setLayers(true);
        anim = ValueAnimator.ofFloat(progress, target);
        anim.setDuration((long) (240 * Math.abs(target - progress) + 80));
        anim.setInterpolator(new DecelerateInterpolator(1.4f));
        anim.addUpdateListener(a -> setProgress((float) a.getAnimatedValue()));
        anim.addListener(new AnimatorListenerAdapter() {
            private boolean cancelled;

            @Override
            public void onAnimationCancel(Animator a) {
                cancelled = true;
            }

            @Override
            public void onAnimationEnd(Animator a) {
                if (cancelled) return;
                setLayers(false);
                if (target == 0f) {
                    hideKeyboard();
                    resetDrawer();
                } else if (kbd) {
                    search.requestFocus();
                    InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
                    if (imm != null) imm.showSoftInput(search, 0);
                }
            }
        });
        anim.start();
    }

    private void closeDrawer() {
        hideKeyboard();
        animateTo(0f);
    }

    private void closeInstant() {
        if (anim != null) anim.cancel();
        setLayers(false);
        setProgress(0f);
        hideKeyboard();
        resetDrawer();
    }

    private void hideKeyboard() {
        if (root == null) return;
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(root.getWindowToken(), 0);
        search.clearFocus();
        root.requestFocus();
    }

    /** Se puede cerrar el cajón arrastrando hacia abajo cuando la lista está al principio. */
    private boolean canCloseByDrag(float y0) {
        if (y0 < insetTop + dp(120)) return true;
        BottomScroll sv = null;
        if (resultsHost.getVisibility() == View.VISIBLE) sv = resultsScroll;
        else if (!pageScrolls.isEmpty()) {
            sv = pageScrolls.get(Math.max(0, Math.min(pageScrolls.size() - 1, curPage())));
        }
        if (sv == null || sv.getChildCount() == 0) return true;
        return sv.getScrollY() <= 2;
    }

    private void expandNotifications() {
        try {
            Object sb = getSystemService("statusbar");
            Class<?> c = Class.forName("android.app.StatusBarManager");
            c.getMethod("expandNotificationsPanel").invoke(sb);
        } catch (Throwable t) {
            try {
                Object sb = getSystemService("statusbar");
                Class<?> c = Class.forName("android.app.StatusBarManager");
                c.getMethod("expand").invoke(sb);
            } catch (Throwable ignored) {
            }
        }
    }

    // ------------------------------------------------------------ gestos

    private final class Root extends FrameLayout {
        private final int slop;
        private final GestureDetector gd;
        private float downX, downY, dragX0, pageStart;
        private boolean dragging;
        private int mode; // 1 abrir cajón, 2 cerrar cajón, 3 notificaciones, 4 cambiar página
        private VelocityTracker vt;

        Root(Context c) {
            super(c);
            slop = ViewConfiguration.get(c).getScaledTouchSlop();
            gd = new GestureDetector(c, new GestureDetector.SimpleOnGestureListener() {
                @Override
                public void onLongPress(MotionEvent e) {
                    if (progress < 0.01f && menuLayer == null && !dragging) {
                        performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                        homeMenu(e.getX(), e.getY());
                    }
                }

                @Override
                public boolean onDoubleTap(MotionEvent e) {
                    if (progress < 0.01f && menuLayer == null && !dragging
                            && prefs.getBoolean(Cfg.DBLTAP, false)) {
                        lockScreen();
                        return true;
                    }
                    return false;
                }
            });
        }

        private void begin(MotionEvent e) {
            downX = e.getX();
            downY = e.getY();
            dragging = false;
            mode = 0;
            if (vt != null) vt.recycle();
            vt = VelocityTracker.obtain();
            vt.addMovement(e);
        }

        private void end() {
            dragging = false;
            mode = 0;
            if (vt != null) {
                vt.recycle();
                vt = null;
            }
        }

        private void cancelLong(MotionEvent e) {
            MotionEvent c = MotionEvent.obtain(e);
            c.setAction(MotionEvent.ACTION_CANCEL);
            gd.onTouchEvent(c);
            c.recycle();
        }

        private void tryStart(MotionEvent e) {
            if (menuLayer != null) return;
            float dx = e.getX() - downX, dy = e.getY() - downY;
            float ax = Math.abs(dx), ay = Math.abs(dy);
            if (progress > 0.99f) {
                if (pageCount > 1 && resultsHost.getVisibility() != View.VISIBLE
                        && ax > slop * 0.75f && ax > ay * 0.8f) {
                    mode = 4;
                    dragging = true;
                    dragX0 = e.getX();
                    pageStart = pagePos;
                    if (pageAnim != null) pageAnim.cancel();
                } else if (ay > slop && ay > ax * 1.3f && dy > 0 && canCloseByDrag(downY)) {
                    mode = 2;
                    dragging = true;
                }
            } else if (progress < 0.01f && ay > slop && ay >= ax * 1.3f) {
                if (dy < 0) {
                    mode = 1;
                    dragging = true;
                    prepareOpen();
                } else if (dy > dp(28) && swipe == 0) {
                    mode = 3;
                    dragging = true;
                    expandNotifications();
                }
            }
            if (dragging) {
                if (mode == 1 || mode == 2) {
                    if (anim != null) anim.cancel();
                    setLayers(true);
                }
                cancelLong(e);
            }
        }

        private void finish(boolean up) {
            if (mode == 3) {
                end();
                return;
            }
            float vy = 0f, vx = 0f;
            if (vt != null) {
                vt.computeCurrentVelocity(1000);
                vy = vt.getYVelocity();
                vx = vt.getXVelocity();
            }
            if (mode == 4) {
                int t = Math.round(pagePos);
                if (up) {
                    if (vx < -700) t = Math.max(t, (int) Math.floor(pageStart) + 1);
                    else if (vx > 700) t = Math.min(t, (int) Math.ceil(pageStart) - 1);
                }
                t = Math.max(0, Math.min(pageCount - 1, t));
                end();
                animatePage(t);
                return;
            }
            float target;
            if (!up) target = progress > 0.5f ? 1f : 0f;
            else if (mode == 1) target = (vy < -800 || (progress > 0.3f && vy < 800)) ? 1f : 0f;
            else target = (vy > 800 || (progress < 0.7f && vy > -800)) ? 0f : 1f;
            end();
            animateTo(target);
        }

        @Override
        public boolean onInterceptTouchEvent(MotionEvent e) {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    begin(e);
                    break;
                case MotionEvent.ACTION_MOVE:
                    if (vt != null) vt.addMovement(e);
                    if (!dragging) tryStart(e);
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    if (!dragging) end();
                    break;
                default:
                    if (vt != null) vt.addMovement(e);
                    break;
            }
            return dragging;
        }

        @Override
        public boolean onTouchEvent(MotionEvent e) {
            int a = e.getActionMasked();
            if (a == MotionEvent.ACTION_DOWN) {
                begin(e);
                gd.onTouchEvent(e);
                return true;
            }
            if (vt != null) vt.addMovement(e);
            if (!dragging) {
                gd.onTouchEvent(e);
                if (a == MotionEvent.ACTION_MOVE) tryStart(e);
                else if (a == MotionEvent.ACTION_UP || a == MotionEvent.ACTION_CANCEL) end();
                return true;
            }
            float range = H * 0.5f;
            switch (a) {
                case MotionEvent.ACTION_MOVE:
                    if (mode == 1) setProgress(clamp((downY - e.getY()) / range));
                    else if (mode == 2) setProgress(clamp(1f - (e.getY() - downY) / range));
                    else if (mode == 4) setPagePos(rubber(pageStart - (e.getX() - dragX0) / W));
                    break;
                case MotionEvent.ACTION_UP:
                    finish(true);
                    break;
                case MotionEvent.ACTION_CANCEL:
                    finish(false);
                    break;
                default:
                    break;
            }
            return true;
        }
    }
}
