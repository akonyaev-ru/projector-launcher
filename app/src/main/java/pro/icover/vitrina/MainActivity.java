package pro.icover.vitrina;

import android.animation.ArgbEvaluator;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Домашний экран «Витрина», раскладка «Ряд кругов»: часы, док избранного, под ним — ряд кругов
 * (ярлыки проектора и «Все приложения»), лист со всеми приложениями и меню настройки дока.
 */
public class MainActivity extends Activity {
    private static final String PREFS = "vitrina";
    private static final String KEY_DOCK = "dock";

    /** Ярлыки проектора — найдены на устройстве по ADB 2026-11-08 (docs/ТЕХНИКА.md §0). */
    private static final ComponentName KEYSTONE = new ComponentName("com.softwinner.tcorrection",
            "com.softwinner.tcorrection.projection.ProjectionSettingActivity");
    private static final ComponentName PROJECTOR = new ComponentName("com.htc.htcsettings",
            "com.htc.activity.HomeActivity");
    /** Кандидат: Live TV вендора открывает внешние входы. Проверить на устройстве. */
    private static final ComponentName HDMI = new ComponentName("com.droidlogic.android.tv",
            "com.android.tv.TvActivity");

    private static final Locale RU = new Locale("ru");
    private static final float CHEVRON_UP = 180f;
    /** Тень подписи выключается, когда подложка фокуса проявилась наполовину (не в каждом кадре). */
    private static final float SHADOW_SWITCH = 0.5f;

    private Ui ui;
    private int screenW;
    private int screenH;
    private FrameLayout root;
    private ImageView background;
    private TextView placeholderLabel;
    private LinearLayout clock;
    private TextView time;
    private TextView date;
    private TextView caption;
    private PanelView dock;
    private PanelView sheet;
    private PanelView allApps;
    private PanelView menu;
    private FrameLayout gridViewport;
    private FrameLayout gridContent;
    private ImageView sheetChevron;

    private final GlassSource glass = new GlassSource();
    private final List<PanelView> glassPanels = new ArrayList<>();
    private final List<PanelView> shortcuts = new ArrayList<>();
    private final List<PanelView> dockTiles = new ArrayList<>();
    private final List<PanelView> gridTiles = new ArrayList<>();
    private final List<PanelView> menuRows = new ArrayList<>();

    private List<AppRepository.App> apps = Collections.emptyList();
    private List<String> dockPkgs = Collections.emptyList();

    private PanelView current;
    private PanelView lastDock;
    private PanelView menuOwner;
    private boolean sheetOpen;
    private boolean menuOpen;
    private boolean longPressHandled;
    private float gridScroll;
    private int slot = -1;
    private boolean launchedAsHome;

    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm", RU);
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("EEEE, d MMMM", RU);

    private final BroadcastReceiver timeReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent i) {
            updateClock();
        }
    };

    private final BroadcastReceiver packageReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent i) {
            loadApps();
        }
    };

    // ---------------------------------------------------------------- жизненный цикл

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        DisplayMetrics dm = getResources().getDisplayMetrics();
        screenW = Math.max(dm.widthPixels, dm.heightPixels);
        screenH = Math.min(dm.widthPixels, dm.heightPixels);
        ui = new Ui(screenW);
        launchedAsHome = getIntent() != null && getIntent().hasCategory(Intent.CATEGORY_HOME);
        applyDebugFlags(getIntent());
        buildViews();
        setContentView(root);
        // Фон окна не нужен: экран целиком закрывает картинка фона. Минус один полноэкранный слой в кадре.
        getWindow().setBackgroundDrawable(null);

        IntentFilter pf = new IntentFilter();
        pf.addAction(Intent.ACTION_PACKAGE_ADDED);
        pf.addAction(Intent.ACTION_PACKAGE_REMOVED);
        pf.addAction(Intent.ACTION_PACKAGE_CHANGED);
        pf.addDataScheme("package");
        registerReceiver(packageReceiver, pf);

        loadApps();
    }

    @Override
    protected void onResume() {
        super.onResume();
        IntentFilter tf = new IntentFilter(Intent.ACTION_TIME_TICK);
        tf.addAction(Intent.ACTION_TIME_CHANGED);
        tf.addAction(Intent.ACTION_TIMEZONE_CHANGED);
        registerReceiver(timeReceiver, tf);
        updateClock();
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(timeReceiver);
    }

    @Override
    protected void onDestroy() {
        unregisterReceiver(packageReceiver);
        io.shutdownNow();
        super.onDestroy();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (intent != null && intent.hasCategory(Intent.CATEGORY_HOME)) {
            launchedAsHome = true;
        }
        // «Домой», когда мы уже дома: закрыть меню и лист, вернуться к началу дока.
        closeMenu(false);
        focusTo(firstHomeTarget());
    }

    /** Отладка рывков: am start -S -n pro.icover.vitrina/.MainActivity --es off "bsgep" (фон, тени, стекло, кромки, подложка). */
    private boolean debugNoBackground;

    private void applyDebugFlags(Intent i) {
        String off = i == null ? null : i.getStringExtra("off");
        if (off == null) {
            return;
        }
        debugNoBackground = off.contains("b");
        PanelView.debugNoShadow = off.contains("s");
        PanelView.debugNoGlass = off.contains("g");
        PanelView.debugNoEdge = off.contains("e");
    }

    // ---------------------------------------------------------------- раскладка

    private void buildViews() {
        root = new FrameLayout(this);
        root.setClipChildren(false);
        // Чёрный — только пока не пришла картинка фона, потом снимается (applyBackground).
        root.setBackgroundColor(Color.BLACK);
        glass.root = root;
        glass.scale = screenW / (float) Tokens.BLUR_W;

        background = new ImageView(this);
        background.setScaleType(ImageView.ScaleType.FIT_XY);
        root.addView(background, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        buildClock();

        dock = new PanelView(this, ui).glass(PanelView.GLASS_FULL, glass)
                .glassAlpha(Tokens.HOME_GLASS_ALPHA).shape(Tokens.DOCK_RADIUS, false).panelShadow();
        root.addView(dock, at(Tokens.DOCK_X, Tokens.DOCK_Y, Tokens.DOCK_W, Tokens.DOCK_H));
        glassPanels.add(dock);

        buildCircleRow();
        buildCaption();
        buildSheet();
        buildPlaceholderLabel();
    }

    private FrameLayout.LayoutParams at(float x, float y, float w, float h) {
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(ui.px(w), ui.px(h));
        lp.leftMargin = ui.px(x);
        lp.topMargin = ui.px(y);
        lp.gravity = Gravity.TOP | Gravity.START;
        return lp;
    }

    private TextView text(float size, int weight, int color) {
        TextView t = new TextView(this);
        t.setTextSize(TypedValue.COMPLEX_UNIT_PX, ui.f(size));
        t.setTypeface(Typeface.create(Typeface.DEFAULT, weight, false));
        t.setTextColor(color);
        t.setSingleLine(true);
        t.setIncludeFontPadding(false);
        return t;
    }

    private void labelShadow(TextView t, boolean on) {
        if (on) {
            t.setShadowLayer(ui.f(Tokens.TEXT_SHADOW_RADIUS), 0, ui.f(Tokens.TEXT_SHADOW_Y), Tokens.TEXT_SHADOW);
        } else {
            t.setShadowLayer(0, 0, 0, 0);
        }
    }

    private void buildClock() {
        clock = new LinearLayout(this);
        clock.setOrientation(LinearLayout.VERTICAL);
        clock.setGravity(Gravity.CENTER_HORIZONTAL);

        time = text(Tokens.TIME_SIZE, Tokens.WEIGHT_LIGHT, Tokens.INK);
        time.setLetterSpacing(Tokens.TIME_TRACKING);
        time.setFontFeatureSettings("tnum");
        time.setShadowLayer(ui.f(Tokens.CLOCK_SHADOW_RADIUS), 0, ui.f(Tokens.CLOCK_SHADOW_Y), Tokens.CLOCK_SHADOW);
        date = text(Tokens.DATE_SIZE, Tokens.WEIGHT_MEDIUM, Tokens.INK);
        date.setShadowLayer(ui.f(Tokens.CLOCK_SHADOW_RADIUS), 0, ui.f(Tokens.CLOCK_SHADOW_Y), Tokens.CLOCK_SHADOW);

        // В макете у цифр line-height .9: глифы выходят за строку на (content − .9)/2 сверху и снизу.
        Paint.FontMetrics fm = time.getPaint().getFontMetrics();
        float overflow = ((fm.descent - fm.ascent) - Tokens.TIME_LINE_HEIGHT * ui.f(Tokens.TIME_SIZE)) / 2f;

        clock.addView(time, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        dlp.topMargin = Math.round(ui.f(Tokens.DATE_GAP) - overflow);
        clock.addView(date, dlp);

        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = Math.round(ui.f(Tokens.CLOCK_TOP) - overflow);
        root.addView(clock, lp);
    }

    /** Круг со значком по центру: ярлык или «Все приложения». */
    private PanelView circle(float cx, float d, int iconRes, float iconSize, String captionText, int group, Runnable action) {
        ImageView icon = new ImageView(this);
        icon.setImageResource(iconRes);
        PanelView c = new PanelView(this, ui).glass(PanelView.GLASS_FULL, glass)
                .glassAlpha(Tokens.HOME_GLASS_ALPHA).shape(d / 2f, false).panelShadow()
                .focusPlate(inkFor(null, icon));
        c.group = group;
        c.caption = captionText;
        c.action = action;
        c.addView(icon, new FrameLayout.LayoutParams(ui.px(iconSize), ui.px(iconSize), Gravity.CENTER));
        root.addView(c, at(cx - d / 2f, Tokens.ROW_CENTER_Y - d / 2f, d, d));
        glassPanels.add(c);
        return c;
    }

    private void buildCircleRow() {
        Object[][] items = {
            {R.string.shortcut_keystone, R.drawable.ic_keystone, (Runnable) () -> start(new Intent().setComponent(KEYSTONE))},
            {R.string.shortcut_projector, R.drawable.ic_projector, (Runnable) () -> start(new Intent().setComponent(PROJECTOR))},
            {R.string.shortcut_hdmi, R.drawable.ic_hdmi, (Runnable) () -> start(new Intent().setComponent(HDMI))},
            {R.string.shortcut_settings, R.drawable.ic_settings, (Runnable) () -> start(new Intent(Settings.ACTION_SETTINGS))},
        };
        for (int i = 0; i < items.length; i++) {
            shortcuts.add(circle(Tokens.SHORTCUT_X[i], Tokens.SHORTCUT_D, (Integer) items[i][1], Tokens.ICON,
                    getString((Integer) items[i][0]), PanelView.GROUP_SHORTCUT, (Runnable) items[i][2]));
        }
        allApps = circle(Tokens.ALL_X, Tokens.ALL_D, R.drawable.ic_chevron_bold, Tokens.CHEVRON,
                getString(R.string.all_apps), PanelView.GROUP_ALL, this::openSheet);
    }

    private void buildCaption() {
        caption = text(Tokens.CAPTION_TEXT, Tokens.WEIGHT_MEDIUM, Tokens.INK);
        labelShadow(caption, true);
        caption.setAlpha(0f);
        root.addView(caption, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.START));
    }

    private void buildSheet() {
        float sheetW = Tokens.DESIGN_W - 2 * Tokens.SIDE;
        sheet = new PanelView(this, ui).glass(PanelView.GLASS_FULL, glass).shape(Tokens.SHEET_RADIUS, true).panelShadow();
        sheet.setTranslationY(ui.f(Tokens.SHEET_CLOSED_DY));
        root.addView(sheet, at(Tokens.SIDE, Tokens.SHEET_Y, sheetW, Tokens.SHEET_H));
        glassPanels.add(sheet);

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER);
        TextView title = text(Tokens.PEEK_TEXT, Tokens.WEIGHT_MEDIUM, Tokens.INK);
        title.setText(R.string.all_apps);
        labelShadow(title, true);
        sheetChevron = new ImageView(this);
        sheetChevron.setImageResource(R.drawable.ic_chevron);
        header.addView(title);
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(ui.px(Tokens.ICON), ui.px(Tokens.ICON));
        clp.leftMargin = ui.px(Tokens.PEEK_GAP);
        header.addView(sheetChevron, clp);
        sheet.addView(header, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ui.px(Tokens.SHEET_PEEK)));

        // Окно сетки режет по своим границам (clipBounds), а не по границам детей: увеличенная ×1.12
        // крайняя плитка помещается в поле GRID_PAD, а прокрученные ряды не заезжают на шапку листа.
        gridViewport = new FrameLayout(this);
        gridViewport.setClipChildren(false);
        gridViewport.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) ->
                v.setClipBounds(new Rect(0, 0, r - l, b - t)));
        float vw = sheetW - 2 * Tokens.GRID_INSET;
        float vh = Tokens.SHEET_H - Tokens.SHEET_PEEK;
        sheet.addView(gridViewport, at(Tokens.GRID_INSET, Tokens.SHEET_PEEK, vw, vh));

        // Содержимое — во всю ширину окна; плитки стоят с отступом GRID_PAD внутри него.
        gridContent = new FrameLayout(this);
        gridContent.setClipChildren(false);
        gridViewport.addView(gridContent, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    private void buildPlaceholderLabel() {
        placeholderLabel = text(Tokens.PH_LABEL_TEXT, Tokens.WEIGHT_MEDIUM, Tokens.PH_LABEL_INK);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Tokens.PH_LABEL_BG);
        bg.setCornerRadius(ui.f(Tokens.PH_LABEL_RADIUS));
        placeholderLabel.setBackground(bg);
        placeholderLabel.setPadding(ui.px(Tokens.PH_LABEL_PAD_H), ui.px(Tokens.PH_LABEL_PAD_V),
                ui.px(Tokens.PH_LABEL_PAD_H), ui.px(Tokens.PH_LABEL_PAD_V));
        placeholderLabel.setVisibility(View.GONE);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        lp.topMargin = ui.px(Tokens.PH_LABEL_TOP);
        root.addView(placeholderLabel, lp);
    }

    /** Подпись и значок темнеют вместе с белой подложкой; тень текста выключается один раз, на полпути. */
    private PanelView.FocusInk inkFor(TextView label, ImageView icon) {
        ArgbEvaluator ev = new ArgbEvaluator();
        boolean[] shadowOn = {true};
        return t -> {
            int c = (Integer) ev.evaluate(t, Tokens.INK, Tokens.INK_FOCUSED);
            if (label != null) {
                label.setTextColor(c);
                boolean want = t < SHADOW_SWITCH;
                if (want != shadowOn[0]) {
                    shadowOn[0] = want;
                    labelShadow(label, want);
                }
            }
            if (icon != null) {
                icon.setColorFilter(c);
            }
        };
    }

    // ---------------------------------------------------------------- приложения и док

    private void loadApps() {
        io.execute(() -> {
            List<AppRepository.App> loaded = AppRepository.load(this);
            List<String> installed = new ArrayList<>();
            for (AppRepository.App a : loaded) {
                installed.add(a.pkg);
            }
            List<String> d = loadDock(installed);
            Set<String> inDock = new HashSet<>(d);
            int gridSize = ui.px(Tokens.GRID_ICON);
            for (AppRepository.App a : loaded) {
                a.iconGrid = AppRepository.renderIcon(a, gridSize, ui.f(Tokens.GRID_ICON_RADIUS), Tokens.ICON_BACKING);
                if (inDock.contains(a.pkg)) {
                    dockIcon(a);
                }
            }
            main.post(() -> applyApps(loaded, d));
        });
    }

    private List<String> loadDock(List<String> installed) {
        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        String saved = prefs.getString(KEY_DOCK, null);
        if (saved != null) {
            return DockModel.sanitize(DockModel.decode(saved), installed);
        }

        List<String> dock = DockModel.defaults(installed);
        prefs.edit().putString(KEY_DOCK, DockModel.encode(dock)).apply();
        return dock;
    }

    private Bitmap dockIcon(AppRepository.App a) {
        if (a.iconDock == null) {
            a.iconDock = AppRepository.renderIcon(a, ui.px(Tokens.TILE_ICON), ui.f(Tokens.TILE_ICON_RADIUS),
                    Tokens.ICON_BACKING);
        }
        return a.iconDock;
    }

    private AppRepository.App appByPkg(String pkg) {
        for (AppRepository.App a : apps) {
            if (a.pkg.equals(pkg)) {
                return a;
            }
        }
        return null;
    }

    private void applyApps(List<AppRepository.App> loaded, List<String> dockList) {
        if (isDestroyed()) {
            return;
        }
        closeMenu(false);
        String focusedPkg = current != null ? (String) current.getTag() : null;
        int focusedGroup = current != null ? current.group : -1;
        apps = loaded;
        dockPkgs = dockList;
        rebuildDock();
        rebuildGrid();
        if (current == null || current.getParent() == null) {
            current = null;
            List<PanelView> pool = focusedGroup == PanelView.GROUP_GRID ? gridTiles : dockTiles;
            PanelView target = null;
            for (PanelView t : pool) {
                if (focusedPkg != null && focusedPkg.equals(t.getTag())) {
                    target = t;
                }
            }
            focusTo(target != null ? target : firstHomeTarget());
        }
        refreshBackground();
    }

    /** Док: плитки по краям панели с равными промежутками (space-between). */
    private void rebuildDock() {
        if (current != null && current.group == PanelView.GROUP_DOCK) {
            current = null;
        }
        dock.removeAllViews();
        dockTiles.clear();
        lastDock = null;
        List<AppRepository.App> dockApps = new ArrayList<>();
        for (String p : dockPkgs) {
            AppRepository.App a = appByPkg(p);
            if (a != null) {
                dockApps.add(a);
            }
        }
        int n = dockApps.size();
        // Панель — под плитки (6 → 1096 px, как в макете), по центру экрана: промежутки всегда DOCK_GAP.
        float dockW = 2 * Tokens.DOCK_PAD + n * Tokens.TILE_W + Math.max(0, n - 1) * Tokens.DOCK_GAP;
        dock.setLayoutParams(at((Tokens.DESIGN_W - dockW) / 2, Tokens.DOCK_Y, dockW, Tokens.DOCK_H));
        dock.setVisibility(n == 0 ? View.INVISIBLE : View.VISIBLE);
        for (int i = 0; i < n; i++) {
            AppRepository.App a = dockApps.get(i);
            PanelView tile = appTile(a, dockIcon(a), Tokens.TILE_W, Tokens.TILE_H, Tokens.TILE_RADIUS,
                    Tokens.TILE_ICON, Tokens.TILE_TEXT_GAP, Tokens.TILE_TEXT, PanelView.GLASS_NONE);
            tile.group = PanelView.GROUP_DOCK;
            dock.addView(tile, at(Tokens.DOCK_PAD + i * (Tokens.TILE_W + Tokens.DOCK_GAP), (Tokens.DOCK_H - Tokens.TILE_H) / 2,
                    Tokens.TILE_W, Tokens.TILE_H));
            dockTiles.add(tile);
        }
    }

    /** Лист: сетка в 7 колонок. */
    private void rebuildGrid() {
        if (current != null && current.group == PanelView.GROUP_GRID) {
            current = null;
        }
        gridContent.removeAllViews();
        gridTiles.clear();
        float tileW = Tokens.GRID_TILE_W;
        for (int i = 0; i < apps.size(); i++) {
            AppRepository.App a = apps.get(i);
            int col = i % Tokens.GRID_COLUMNS;
            int row = i / Tokens.GRID_COLUMNS;
            PanelView tile = appTile(a, a.iconGrid, tileW, Tokens.GRID_TILE_H, Tokens.GRID_RADIUS,
                    Tokens.GRID_ICON, Tokens.GRID_TEXT_GAP, Tokens.GRID_TEXT, PanelView.GLASS_EDGE);
            tile.group = PanelView.GROUP_GRID;
            gridContent.addView(tile, at(Tokens.GRID_PAD + col * (tileW + Tokens.GRID_GAP),
                    Tokens.GRID_PAD + row * (Tokens.GRID_TILE_H + Tokens.GRID_GAP), tileW, Tokens.GRID_TILE_H));
            gridTiles.add(tile);
        }
    }

    private PanelView appTile(AppRepository.App a, Bitmap icon, float w, float h, float radius, float iconSize,
            float textGap, float textSize, int glassMode) {
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER);
        ImageView iv = new ImageView(this);
        iv.setImageBitmap(icon);
        col.addView(iv, new LinearLayout.LayoutParams(ui.px(iconSize), ui.px(iconSize)));
        TextView label = text(textSize, Tokens.WEIGHT_MEDIUM, Tokens.INK);
        label.setText(a.label);
        label.setEllipsize(TextUtils.TruncateAt.END);
        label.setGravity(Gravity.CENTER);
        label.setMaxWidth(ui.px(w - 2 * Tokens.LABEL_PAD));
        labelShadow(label, true);
        LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        llp.topMargin = ui.px(textGap);
        col.addView(label, llp);

        PanelView tile = new PanelView(this, ui).glass(glassMode, glass).shape(radius, false).focusPlate(inkFor(label, null));
        tile.setTag(a.pkg);
        tile.action = () -> launch(a);
        tile.addView(col, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        return tile;
    }

    private void saveDock(List<String> next) {
        dockPkgs = next;
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString(KEY_DOCK, DockModel.encode(next)).apply();
        rebuildDock();
    }

    private void launch(AppRepository.App a) {
        Intent i = new Intent(Intent.ACTION_MAIN)
                .addCategory(a.leanback ? Intent.CATEGORY_LEANBACK_LAUNCHER : Intent.CATEGORY_LAUNCHER)
                .setComponent(new ComponentName(a.pkg, a.cls))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        start(i);
    }

    private void start(Intent i) {
        try {
            startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (ActivityNotFoundException | SecurityException e) {
            Toast.makeText(this, R.string.not_found, Toast.LENGTH_SHORT).show();
        }
    }

    // ---------------------------------------------------------------- меню дока

    /** Долгое OK на плитке дока или листа: что можно сделать с этим приложением в доке. */
    private void openMenuFor(PanelView tile) {
        String pkg = (String) tile.getTag();
        AppRepository.App app = appByPkg(pkg);
        if (app == null) {
            return;
        }
        List<Object[]> items = new ArrayList<>();
        int idx = dockPkgs.indexOf(pkg);
        if (idx >= 0) {
            if (idx > 0) {
                items.add(new Object[] {R.drawable.ic_move_left, getString(R.string.dock_left),
                        (Runnable) () -> saveDockKeepingFocus(DockModel.move(dockPkgs, pkg, -1), pkg)});
            }
            if (idx < dockPkgs.size() - 1) {
                items.add(new Object[] {R.drawable.ic_move_right, getString(R.string.dock_right),
                        (Runnable) () -> saveDockKeepingFocus(DockModel.move(dockPkgs, pkg, 1), pkg)});
            }
            items.add(new Object[] {R.drawable.ic_dock_remove, getString(R.string.dock_remove),
                    (Runnable) () -> saveDockKeepingFocus(DockModel.remove(dockPkgs, pkg), null)});
        } else {
            String displaced = DockModel.displacedBy(dockPkgs, pkg);
            AppRepository.App out = displaced == null ? null : appByPkg(displaced);
            String label = out == null ? getString(R.string.dock_add) : getString(R.string.dock_replace, out.label);
            items.add(new Object[] {R.drawable.ic_dock_add, label,
                    (Runnable) () -> saveDockKeepingFocus(DockModel.add(dockPkgs, pkg), null)});
        }

        menu = new PanelView(this, ui).glass(PanelView.GLASS_FULL, glass).shape(Tokens.MENU_RADIUS, false).panelShadow();
        menuRows.clear();
        // Ширина — под самую длинную строку, в пределах MENU_W…MENU_W_MAX.
        Paint measure = text(Tokens.MENU_TEXT, Tokens.WEIGHT_MEDIUM, Tokens.INK).getPaint();
        float textW = 0;
        for (Object[] it : items) {
            textW = Math.max(textW, measure.measureText((String) it[1]) / ui.k);
        }
        float menuW = Math.max(Tokens.MENU_W, Math.min(Tokens.MENU_W_MAX, 2 * Tokens.MENU_PAD + 2 * Tokens.MENU_ROW_PAD
                + Tokens.ICON + Tokens.MENU_ICON_GAP + (float) Math.ceil(textW)));
        float rowW = menuW - 2 * Tokens.MENU_PAD;
        for (int i = 0; i < items.size(); i++) {
            Object[] it = items.get(i);
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(ui.px(Tokens.MENU_ROW_PAD), 0, ui.px(Tokens.MENU_ROW_PAD), 0);
            ImageView icon = new ImageView(this);
            icon.setImageResource((Integer) it[0]);
            row.addView(icon, new LinearLayout.LayoutParams(ui.px(Tokens.ICON), ui.px(Tokens.ICON)));
            TextView t = text(Tokens.MENU_TEXT, Tokens.WEIGHT_MEDIUM, Tokens.INK);
            t.setText((String) it[1]);
            t.setEllipsize(TextUtils.TruncateAt.END);
            labelShadow(t, true);
            LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            tlp.leftMargin = ui.px(Tokens.MENU_ICON_GAP);
            row.addView(t, tlp);

            PanelView rowPanel = new PanelView(this, ui).shape(Tokens.MENU_ROW_RADIUS, false).focusPlate(inkFor(t, icon));
            rowPanel.scaleOnFocus = false;
            rowPanel.group = PanelView.GROUP_MENU;
            Runnable act = (Runnable) it[2];
            rowPanel.action = () -> {
                closeMenu(true);
                act.run();
            };
            rowPanel.addView(row, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT));
            menu.addView(rowPanel, at(Tokens.MENU_PAD, Tokens.MENU_PAD + i * (Tokens.MENU_ROW_H + Tokens.MENU_ROW_GAP),
                    rowW, Tokens.MENU_ROW_H));
            menuRows.add(rowPanel);
        }
        float menuH = 2 * Tokens.MENU_PAD + items.size() * Tokens.MENU_ROW_H + (items.size() - 1) * Tokens.MENU_ROW_GAP;

        // Над плиткой, по её центру; не помещается сверху — под ней.
        float[] c = new float[2];
        logicalCenter(tile, c);
        float tileTop = c[1] / ui.k - tile.getHeight() / ui.k / 2f;
        float tileBottom = tileTop + tile.getHeight() / ui.k;
        // По центру плитки, но в пределах её панели: края дока или поля сетки листа.
        float minX;
        float maxX;
        if (tile.group == PanelView.GROUP_DOCK) {
            minX = dock.getLeft() / ui.k;
            maxX = dock.getRight() / ui.k;
        } else {
            minX = Tokens.SIDE + Tokens.GRID_INSET + Tokens.GRID_PAD;
            maxX = Tokens.DESIGN_W - minX;
        }
        float x = Math.max(minX, Math.min(maxX - menuW, c[0] / ui.k - menuW / 2f));
        float y = tileTop - Tokens.MENU_OFFSET - menuH;
        if (y < Tokens.TOP) {
            y = Math.min(tileBottom + Tokens.MENU_OFFSET, Tokens.DESIGN_H - Tokens.TOP - menuH);
        }
        root.addView(menu, at(x, y, menuW, menuH));
        menu.setAlpha(0f);
        menu.setTranslationY(-ui.f(Tokens.MENU_SHIFT));
        PanelView openedMenu = menu;
        openedMenu.animate().alpha(1f).translationY(0).setDuration(Tokens.MENU_IN_MS).setInterpolator(Ui.EASE_FOCUS)
                .setUpdateListener(a -> openedMenu.invalidate()).start();
        clock.animate().alpha(sheetOpen ? 0f : Tokens.MENU_DIM).setDuration(Tokens.MENU_IN_MS).start();

        menuOpen = true;
        menuOwner = tile;
        moveFocus(menuRows.get(0));
    }

    /** @param keepFocusOnOwner false — фокус уже уйдёт сам (действие перестроит док). */
    private void closeMenu(boolean keepFocusOnOwner) {
        if (!menuOpen) {
            return;
        }
        menuOpen = false;
        PanelView m = menu;
        menu = null;
        menuRows.clear();
        m.animate().alpha(0f).translationY(-ui.f(Tokens.MENU_SHIFT)).setDuration(Tokens.MENU_OUT_MS)
                .withEndAction(() -> root.removeView(m)).start();
        clock.animate().alpha(sheetOpen ? 0f : 1f).setDuration(Tokens.MENU_OUT_MS).start();
        PanelView owner = menuOwner;
        menuOwner = null;
        if (keepFocusOnOwner && owner != null && owner.getParent() != null) {
            moveFocus(owner);
        } else if (current != null && current.group == PanelView.GROUP_MENU) {
            // Строка меню уходит вместе с меню: фокус назначит вызывающий.
            current = null;
        }
    }

    /** Перестроить док и вернуть фокус: на то же приложение в доке, иначе туда, где были. */
    private void saveDockKeepingFocus(List<String> next, String focusPkg) {
        boolean wasGrid = current != null && current.group == PanelView.GROUP_GRID;
        PanelView gridFocus = wasGrid ? current : null;
        saveDock(next);
        if (gridFocus != null && gridFocus.getParent() != null) {
            moveFocus(gridFocus);
            return;
        }
        PanelView target = null;
        for (PanelView t : dockTiles) {
            if (focusPkg != null && focusPkg.equals(t.getTag())) {
                target = t;
            }
        }
        current = null;
        focusTo(target != null ? target : firstHomeTarget());
    }

    // ---------------------------------------------------------------- фон и часы

    private void updateClock() {
        Calendar now = Calendar.getInstance();
        time.setText(timeFormat.format(now.getTime()));
        String d = dateFormat.format(now.getTime());
        date.setText(d.isEmpty() ? d : d.substring(0, 1).toUpperCase(RU) + d.substring(1));
        refreshBackground();
    }

    private void refreshBackground() {
        int s = TimeOfDay.slot(Calendar.getInstance().get(Calendar.HOUR_OF_DAY));
        if (s == slot) {
            return;
        }
        slot = s;
        io.execute(() -> {
            Wallpapers.Result r = Wallpapers.load(this, s, screenW, screenH);
            Bitmap blurred = GlassSource.blur(r.bitmap);
            main.post(() -> {
                if (isDestroyed()) {
                    return;
                }
                if (!debugNoBackground) {
                    background.setImageBitmap(r.bitmap);
                    root.setBackground(null);
                }
                glass.blurred = blurred;
                placeholderLabel.setText(getString(R.string.placeholder, TimeOfDay.LABELS[s]));
                placeholderLabel.setVisibility(r.placeholder ? View.VISIBLE : View.GONE);
                for (PanelView p : glassPanels) {
                    p.glassChanged();
                }
                if (menu != null) {
                    menu.glassChanged();
                }
            });
        });
    }

    // ---------------------------------------------------------------- фокус, лист, подпись

    private PanelView firstHomeTarget() {
        if (!dockTiles.isEmpty()) {
            return dockTiles.get(0);
        }
        return allApps;
    }

    /** Перенести фокус без смены состояния листа (для меню). */
    private void moveFocus(PanelView v) {
        if (v == null || v == current) {
            return;
        }
        if (current != null) {
            current.setFocusedAnimated(false);
        }
        current = v;
        v.setFocusedAnimated(true);
        showCaption(v);
    }

    private void focusTo(PanelView v) {
        if (v == null) {
            return;
        }
        if (v.group == PanelView.GROUP_GRID) {
            if (!sheetOpen) {
                setSheetOpen(true);
            }
            ensureVisible(v);
        } else if (v.group != PanelView.GROUP_MENU) {
            if (sheetOpen) {
                setSheetOpen(false);
            }
            if (v.group == PanelView.GROUP_DOCK) {
                lastDock = v;
            }
        }
        moveFocus(v);
    }

    private void showCaption(PanelView v) {
        caption.animate().cancel();
        if (v.caption == null || sheetOpen) {
            caption.setAlpha(0f);
            return;
        }
        caption.setText(v.caption);
        float w = caption.getPaint().measureText(v.caption);
        float[] c = new float[2];
        logicalCenter(v, c);
        caption.setX(c[0] - w / 2f);
        caption.setY(c[1] + Math.max(v.getHeight() / 2f, ui.f(Tokens.CAPTION_MIN_HALF)) + ui.f(Tokens.CAPTION_GAP));
        caption.setAlpha(0f);
        caption.animate().alpha(1f).setDuration(Tokens.CAPTION_IN_MS).setInterpolator(Ui.EASE_FOCUS).start();
    }

    private void openSheet() {
        if (gridTiles.isEmpty()) {
            return;
        }
        setGridScroll(0, false);
        focusTo(gridTiles.get(0));
    }

    private void setSheetOpen(boolean open) {
        sheetOpen = open;
        sheet.animate().translationY(open ? 0 : ui.f(Tokens.SHEET_CLOSED_DY)).setDuration(Tokens.SHEET_MS)
                .setInterpolator(Ui.EASE_SHEET)
                // Лист едет, стекло должно стоять: перерисовываем только саму панель листа.
                .setUpdateListener(a -> sheet.invalidate())
                .start();
        sheetChevron.animate().rotation(open ? CHEVRON_UP : 0).setDuration(Tokens.SHEET_MS).setInterpolator(Ui.EASE_SHEET).start();
        float chrome = open ? 0f : 1f;
        clock.animate().alpha(chrome).setDuration(Tokens.CHROME_FADE_MS).setInterpolator(Ui.EASE_FOCUS).start();
        for (PanelView p : shortcuts) {
            p.animate().alpha(chrome).setDuration(Tokens.CHROME_FADE_MS).setInterpolator(Ui.EASE_FOCUS).start();
        }
        allApps.animate().alpha(chrome).setDuration(Tokens.CHROME_FADE_MS).setInterpolator(Ui.EASE_FOCUS).start();
        if (open) {
            caption.animate().cancel();
            caption.setAlpha(0f);
        }
    }

    private void ensureVisible(PanelView tile) {
        float pad = ui.f(Tokens.GRID_PAD);
        float limit = gridViewport.getHeight() - pad;
        float top = tile.getTop();
        float bottom = top + tile.getHeight();
        float y = gridScroll;
        if (top + y < pad) {
            y = pad - top;
        }
        if (bottom + y > limit) {
            y = limit - bottom;
        }
        setGridScroll(y, true);
    }

    private void setGridScroll(float y, boolean animate) {
        gridScroll = y;
        if (animate) {
            gridContent.animate().translationY(y).setDuration(Tokens.GRID_SCROLL_MS).setInterpolator(Ui.EASE_FOCUS).start();
        } else {
            gridContent.animate().cancel();
            gridContent.setTranslationY(y);
        }
    }

    /** Центр элемента в координатах экрана, как если бы лист был открыт, а сетка — на целевой прокрутке. */
    private void logicalCenter(View v, float[] out) {
        float x = v.getWidth() / 2f;
        float y = v.getHeight() / 2f;
        View cur = v;
        while (cur != null && cur != root) {
            x += cur.getLeft();
            y += cur.getTop();
            if (cur == gridContent) {
                y += gridScroll;
            }
            cur = cur.getParent() instanceof View ? (View) cur.getParent() : null;
        }
        out[0] = x;
        out[1] = y;
    }

    /**
     * Пульт. Схема «Ряда кругов» (Дизайнер 2026-11-09):
     * док ↓ → «Все приложения»; ряд кругов ↑ → та же плитка дока; круг «Все» ↓/OK → лист, первое приложение;
     * ярлык ↓ → ничего; лист ↑ из первого ряда → «Все приложения»; меню ← → закрыть.
     */
    private void move(int dir) {
        if (current == null) {
            focusTo(firstHomeTarget());
            return;
        }
        int g = current.group;
        if (!menuOpen && !sheetOpen) {
            if (g == PanelView.GROUP_DOCK && dir == Navigator.DOWN) {
                focusTo(allApps);
                return;
            }
            if ((g == PanelView.GROUP_SHORTCUT || g == PanelView.GROUP_ALL) && dir == Navigator.UP) {
                focusTo(lastDock != null && lastDock.getParent() != null ? lastDock : firstHomeTarget());
                return;
            }
            if (g == PanelView.GROUP_ALL && dir == Navigator.DOWN) {
                openSheet();
                return;
            }
            if (g == PanelView.GROUP_SHORTCUT && dir == Navigator.DOWN) {
                return;
            }
        }
        if (menuOpen && dir == Navigator.LEFT) {
            closeMenu(true);
            return;
        }
        List<PanelView> all = new ArrayList<>();
        if (menuOpen) {
            all.addAll(menuRows);
        } else if (sheetOpen) {
            all.addAll(gridTiles);
        } else {
            all.addAll(dockTiles);
            all.addAll(shortcuts);
            all.add(allApps);
        }
        int n = all.size();
        float[] cx = new float[n];
        float[] cy = new float[n];
        float[] c = new float[2];
        int cur = all.indexOf(current);
        if (cur < 0) {
            return;
        }
        for (int i = 0; i < n; i++) {
            logicalCenter(all.get(i), c);
            cx[i] = c[0];
            cy[i] = c[1];
        }
        int next = Navigator.pick(cx, cy, cur, dir, null);
        if (next >= 0) {
            if (menuOpen) {
                moveFocus(all.get(next));
            } else {
                focusTo(all.get(next));
            }
        } else if (sheetOpen && !menuOpen && dir == Navigator.UP) {
            focusTo(allApps);
        }
    }

    private void back() {
        if (menuOpen) {
            closeMenu(true);
        } else if (sheetOpen) {
            focusTo(allApps);
        } else if (!launchedAsHome) {
            finish();
        }
    }

    private boolean canOpenMenu() {
        return current != null && !menuOpen
                && (current.group == PanelView.GROUP_DOCK || current.group == PanelView.GROUP_GRID);
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        boolean down = e.getAction() == KeyEvent.ACTION_DOWN;
        switch (e.getKeyCode()) {
            case KeyEvent.KEYCODE_DPAD_LEFT:
                if (down) {
                    move(Navigator.LEFT);
                }
                return true;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                if (down) {
                    move(Navigator.RIGHT);
                }
                return true;
            case KeyEvent.KEYCODE_DPAD_UP:
                if (down) {
                    move(Navigator.UP);
                }
                return true;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                if (down) {
                    move(Navigator.DOWN);
                }
                return true;
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_NUMPAD_ENTER:
                onSelectKey(e, down);
                return true;
            case KeyEvent.KEYCODE_MENU:
                if (!down && canOpenMenu()) {
                    openMenuFor(current);
                }
                return true;
            case KeyEvent.KEYCODE_BACK:
                if (!down) {
                    back();
                }
                return true;
            default:
                return super.dispatchKeyEvent(e);
        }
    }

    /**
     * OK: короткое — запуск; долгое (на плитке дока или листа) — меню дока.
     * Пульты по-разному шлют повторы при удержании, поэтому долгое ловим и по повтору, и по длительности.
     */
    private void onSelectKey(KeyEvent e, boolean down) {
        if (current == null) {
            return;
        }
        if (down) {
            if (e.getRepeatCount() == 0) {
                longPressHandled = false;
                current.press();
            } else if (!longPressHandled && e.isLongPress() && canOpenMenu()) {
                longPressHandled = true;
                openMenuFor(current);
            }
            return;
        }
        if (longPressHandled || e.isCanceled()) {
            longPressHandled = false;
            return;
        }
        boolean longHold = e.getEventTime() - e.getDownTime() >= ViewConfiguration.getLongPressTimeout();
        if (longHold && canOpenMenu()) {
            openMenuFor(current);
        } else if (current.action != null) {
            current.action.run();
        }
    }
}
