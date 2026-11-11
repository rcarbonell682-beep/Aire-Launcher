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
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.text.Collator;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {

    private static final int MATCH = ViewGroup.LayoutParams.MATCH_PARENT;
    private static final int WRAP = ViewGroup.LayoutParams.WRAP_CONTENT;
    private static final int DOCK_MAX = 4;

    private final Handler ui = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private SharedPreferences prefs;
    private Palette pal;
    private float density = 1f;
    private int W, H, insetTop, insetBottom;

    private Root root;
    private View scrim;
    private FrameLayout home;
    private LinearLayout drawer, dock, pagesRow;
    private TextView hourTv, minTv, dateTv;
    private EditText search;
    private SnapPager pager;
    private Dots dots;
    private BottomScroll resultsScroll;
    private ReverseGrid resultsGrid;
    private FrameLayout menuLayer;
    private final List<BottomScroll> pageScrolls = new ArrayList<>();
    private List<AppInfo> currentResults = new ArrayList<>();

    private List<AppInfo> apps = new ArrayList<>();
    private final Map<String, AppInfo> byId = new HashMap<>();

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
        prefs = getSharedPreferences("aire", MODE_PRIVATE);
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
        updateClock();
    }

    @Override
    protected void onResume() {
        super.onResume();
        applyBars();
        updateClock();
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (timeOn) {
            try {
                unregisterReceiver(timeRx);
            } catch (IllegalArgumentException ignored) {
            }
            timeOn = false;
        }
        dismissMenu();
        if (progress > 0f) closeInstant();
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
        refreshDock();
        buildPages();
        loadApps();
    }

    private void buildUi() {
        if (anim != null) anim.cancel();
        DisplayMetrics dm = getResources().getDisplayMetrics();
        W = dm.widthPixels;
        H = dm.heightPixels;
        density = dm.density;
        menuLayer = null;

        root = new Root(this);
        root.setFocusableInTouchMode(true);

        scrim = new View(this);
        scrim.setBackgroundColor(pal.scrim);
        root.addView(scrim, new FrameLayout.LayoutParams(MATCH, MATCH));

        home = new FrameLayout(this);
        root.addView(home, new FrameLayout.LayoutParams(MATCH, MATCH));
        buildHome();

        drawer = new LinearLayout(this);
        drawer.setOrientation(LinearLayout.VERTICAL);
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
        t.setShadowLayer(dp(5), 0, 0, Palette.alpha(pal.scrim, 0x55));
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

    private void buildHome() {
        Typeface light = Typeface.create("sans-serif-light", Typeface.NORMAL);
        hourTv = text(112, pal.text, light);
        minTv = text(112, pal.accent, light);
        halo(hourTv);
        halo(minTv);

        LinearLayout clockBox = new LinearLayout(this);
        clockBox.setOrientation(LinearLayout.VERTICAL);
        clockBox.setGravity(Gravity.CENTER_HORIZONTAL);
        clockBox.setPadding(dp(28), dp(4), dp(28), dp(4));
        clockBox.addView(hourTv, new LinearLayout.LayoutParams(WRAP, WRAP));
        LinearLayout.LayoutParams mlp = new LinearLayout.LayoutParams(WRAP, WRAP);
        mlp.topMargin = -dp(22);
        clockBox.addView(minTv, mlp);
        clockBox.setOnClickListener(v -> openClock());
        press(clockBox);

        dateTv = text(16, pal.text, Typeface.create("sans-serif-medium", Typeface.NORMAL));
        dateTv.setLetterSpacing(0.04f);
        halo(dateTv);
        dateTv.setPadding(dp(20), dp(8), dp(20), dp(8));
        dateTv.setOnClickListener(v -> openCalendar());
        press(dateTv);

        LinearLayout widget = new LinearLayout(this);
        widget.setOrientation(LinearLayout.VERTICAL);
        widget.setGravity(Gravity.CENTER_HORIZONTAL);
        widget.addView(clockBox, WRAP, WRAP);
        widget.addView(dateTv, WRAP, WRAP);
        FrameLayout.LayoutParams wl = new FrameLayout.LayoutParams(WRAP, WRAP,
                Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        wl.topMargin = Math.round(H * 0.10f);
        home.addView(widget, wl);

        dock = new LinearLayout(this);
        dock.setOrientation(LinearLayout.HORIZONTAL);
        dock.setGravity(Gravity.CENTER_VERTICAL);
        FrameLayout.LayoutParams dl = new FrameLayout.LayoutParams(WRAP, dp(60),
                Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        dl.bottomMargin = dp(28);
        home.addView(dock, dl);
    }

    private void buildDrawerShell() {
        search = new EditText(this);
        search.setHint("Buscar");
        search.setSingleLine(true);
        search.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        search.setTextColor(pal.text);
        search.setHintTextColor(pal.textDim);
        search.setBackground(roundRect(pal.plate, dp(24)));
        search.setPadding(dp(20), 0, dp(20), 0);
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
        LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(MATCH, dp(48));
        sl.setMargins(dp(20), dp(10), dp(20), 0);
        drawer.addView(search, sl);

        dots = new Dots(this, pal);
        LinearLayout.LayoutParams dl = new LinearLayout.LayoutParams(WRAP, dp(26));
        dl.gravity = Gravity.CENTER_HORIZONTAL;
        dl.topMargin = dp(6);
        drawer.addView(dots, dl);

        FrameLayout content = new FrameLayout(this);
        pager = new SnapPager(this);
        pagesRow = new LinearLayout(this);
        pagesRow.setOrientation(LinearLayout.HORIZONTAL);
        pager.addView(pagesRow, new FrameLayout.LayoutParams(WRAP, MATCH));
        pager.setListener(p -> dots.setPos(p));
        content.addView(pager, new FrameLayout.LayoutParams(MATCH, MATCH));

        resultsScroll = scroller();
        resultsGrid = new ReverseGrid(this, 4, dp(92));
        resultsScroll.addView(resultsGrid, new ViewGroup.LayoutParams(MATCH, WRAP));
        resultsScroll.setVisibility(View.GONE);
        content.addView(resultsScroll, new FrameLayout.LayoutParams(MATCH, MATCH));

        drawer.addView(content, new LinearLayout.LayoutParams(MATCH, 0, 1f));
        pageScrolls.clear();
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

    private AppCell cell(AppInfo a, boolean label, boolean inDock) {
        AppCell c = new AppCell(this, a, pal, label, dp(label ? 50 : 38));
        c.setOnClickListener(v -> launch(a, v));
        c.setOnLongClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
            appMenu(v, a, inDock);
            return true;
        });
        return c;
    }

    private void buildPages() {
        if (pagesRow == null) return;
        int keep = pager.cur();
        pagesRow.removeAllViews();
        pageScrolls.clear();
        for (int c = 0; c < AppInfo.NAMES.length; c++) {
            List<AppInfo> g = new ArrayList<>();
            for (AppInfo a : apps) if (a.cat == c) g.add(a);
            if (g.isEmpty()) continue;
            pagesRow.addView(makePage(AppInfo.NAMES[c], g), new LinearLayout.LayoutParams(W, MATCH));
        }
        int n = pagesRow.getChildCount();
        dots.setCount(n);
        final int target = Math.max(0, Math.min(keep, n - 1));
        pager.post(() -> pager.jumpTo(target));
    }

    private View makePage(String title, List<AppInfo> list) {
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        TextView t = text(12, pal.textDim, Typeface.create("sans-serif-medium", Typeface.NORMAL));
        t.setLetterSpacing(0.14f);
        t.setGravity(Gravity.START);
        t.setText(title.toUpperCase(Locale.getDefault()) + "  ·  " + list.size());
        t.setPadding(dp(28), dp(6), dp(28), dp(6));

        BottomScroll sv = scroller();
        ReverseGrid g = new ReverseGrid(this, 4, dp(92));
        for (AppInfo a : list) g.addView(cell(a, true, false));
        sv.addView(g, new ViewGroup.LayoutParams(MATCH, WRAP));
        pageScrolls.add(sv);

        page.addView(t, new LinearLayout.LayoutParams(MATCH, WRAP));
        page.addView(sv, new LinearLayout.LayoutParams(MATCH, 0, 1f));
        return page;
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
        Calendar ca
