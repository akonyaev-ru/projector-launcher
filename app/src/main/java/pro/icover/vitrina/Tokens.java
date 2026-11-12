package pro.icover.vitrina;

/**
 * Токены варианта 3 «Витрина» (Дизайнер, 2026-11-08; выбран владельцем).
 * Размеры — в px макета 1280×720, в код идут через {@link Ui#f(float)}.
 * Источник: .proto/design/runs/2026-11-08-glass-variants/demo.html, класс .v3.
 */
final class Tokens {
    private Tokens() {
    }

    // ---------- экран ----------
    static final float DESIGN_W = 1280f;
    static final float DESIGN_H = 720f;
    static final float SIDE = 48f;
    static final float TOP = 32f;

    // ---------- стекло: фон уменьшен до 160×90 и размыт один раз ----------
    static final int BLUR_W = 160;
    static final int BLUR_H = 90;
    static final int BLUR_RADIUS = 8;
    static final float BLUR_SATURATION = 1.3f;
    static final float BLUR_BRIGHTNESS = 0.72f;
    /** rgba(120,124,132,.28) */
    static final int GLASS_TINT = 0x47787C84;
    /** 64% размытого слоя. Через док и круги видно резкий фон. */
    static final int HOME_GLASS_ALPHA = 163;
    /** Обводка внутри: 1.5px rgba(255,255,255,.22). */
    static final int EDGE_COLOR = 0x38FFFFFF;
    static final float EDGE_WIDTH = 1.5f;
    /** Блик по верхней кромке: 1px rgba(255,255,255,.55). */
    static final int HIGHLIGHT_COLOR = 0x8CFFFFFF;
    static final float HIGHLIGHT_WIDTH = 1f;
    /** Высота, на которой блик гаснет, — доля радиуса. */
    static final float HIGHLIGHT_FADE = 0.6f;
    /** Тень панели: 0 20 48 rgba(0,0,0,.28). */
    static final float PANEL_SHADOW_Y = 20f;
    static final float PANEL_SHADOW_BLUR = 48f;
    static final int PANEL_SHADOW_ALPHA = 71;
    /** Притемнение верха под часы: от rgba(0,0,0,.32) до 0 на 42% высоты — запекается в фон. */
    static final int TOP_SCRIM = 0x52000000;
    static final float TOP_SCRIM_HEIGHT = 0.42f;

    // ---------- текст ----------
    static final int INK = 0xFFFFFFFF;
    /** Тень подписей: 0 1 3 rgba(0,0,0,.45). */
    static final int TEXT_SHADOW = 0x73000000;
    static final float TEXT_SHADOW_RADIUS = 1.5f;
    static final float TEXT_SHADOW_Y = 1f;
    static final int WEIGHT_LIGHT = 300;
    static final int WEIGHT_MEDIUM = 500;

    // ---------- фокус: белая подложка, тёмная подпись, ×1.12 ----------
    static final int PLATE_COLOR = 0xFFFFFFFF;
    static final int INK_FOCUSED = 0xFF111215;
    /** Тень подложки: 0 14 30 rgba(0,0,0,.30). */
    static final float PLATE_SHADOW_Y = 14f;
    static final float PLATE_SHADOW_BLUR = 30f;
    static final int PLATE_SHADOW_ALPHA = 77;
    static final float FOCUS_SCALE = 1.12f;
    static final float PRESS_SCALE = 0.95f;
    static final long FOCUS_IN_MS = 180;
    static final long FOCUS_OUT_MS = 130;
    static final long PRESS_MS = 70;

    // ---------- часы ----------
    static final float CLOCK_TOP = 96f;
    static final float TIME_SIZE = 144f;
    /** CSS line-height .9 — нужен, чтобы поставить цифры туда же, где в демо. */
    static final float TIME_LINE_HEIGHT = 0.9f;
    static final float TIME_TRACKING = -0.03f;
    static final float DATE_SIZE = 28f;
    static final float DATE_GAP = 14f;
    /** Тень часов: 0 2 12 rgba(0,0,0,.35). */
    static final int CLOCK_SHADOW = 0x59000000;
    static final float CLOCK_SHADOW_RADIUS = 6f;
    static final float CLOCK_SHADOW_Y = 2f;
    static final long CLOCK_FADE_MS = 180;

    // ---------- ряд кругов под доком (раскладка «Ряд кругов», Дизайнер 2026-11-09, выбрана владельцем) ----------
    /** Владелец 2026-11-09: док и ряд кругов «чуть-чуть поднять» — на 40 px от варианта Дизайнера. */
    static final float LIFT = 40f;
    static final float ROW_CENTER_Y = 632f - LIFT;
    /** Центры кругов-ярлыков по X: Трапеция, Проектор | Все приложения | HDMI, Настройки. */
    static final float[] SHORTCUT_X = {460f, 540f, 740f, 820f};
    static final float SHORTCUT_D = 56f;
    static final float ALL_X = 640f;
    static final float ALL_D = 64f;
    static final float ICON = 26f;
    static final float CHEVRON = 30f;
    /** Подпись при фокусе: по центру круга, ниже на max(d/2, 32) + 12. */
    static final float CAPTION_TEXT = 20f;
    static final float CAPTION_MIN_HALF = 32f;
    static final float CAPTION_GAP = 12f;
    static final long CAPTION_IN_MS = 180;
    /** Пока лист открыт, часы и ряд кругов гаснут. */
    static final long CHROME_FADE_MS = 180;

    // ---------- док ----------
    static final float DOCK_X = 92f;
    static final float DOCK_Y = 388f - LIFT;
    static final float DOCK_W = 1096f;
    static final float DOCK_H = 196f;
    static final float DOCK_RADIUS = 40f;
    static final float DOCK_PAD = 28f;
    /** Промежуток между плитками дока; при неполном доке панель сжимается под плитки и стоит по центру. */
    static final float DOCK_GAP = 16f;
    static final float TILE_W = 160f;
    static final float TILE_H = 172f;
    static final float TILE_RADIUS = 28f;
    static final float TILE_ICON = 96f;
    static final float TILE_ICON_RADIUS = 24f;
    static final float TILE_TEXT_GAP = 12f;
    static final float TILE_TEXT = 22f;

    // ---------- лист «Все приложения» ----------
    static final float SHEET_Y = 128f;
    static final float SHEET_H = 592f;
    static final float SHEET_RADIUS = 36f;
    static final float SHEET_PEEK = 64f;
    /** Закрытый лист уходит за экран целиком (128 + 640 = 768 > 720), тень тоже. */
    static final float SHEET_CLOSED_DY = 640f;
    static final long SHEET_MS = 280;
    static final float PEEK_TEXT = 22f;
    static final float PEEK_GAP = 8f;
    static final float GRID_INSET = 12f;
    /** (1160 − 7×150 − 6×14) / 2 = 13: колонки целыми пикселями, поля слева и справа равны. */
    static final float GRID_PAD = 13f;
    static final float GRID_GAP = 14f;
    static final int GRID_COLUMNS = 7;
    static final float GRID_TILE_W = 150f;
    static final float GRID_TILE_H = 136f;
    static final float GRID_RADIUS = 22f;
    static final float GRID_ICON = 68f;
    static final float GRID_ICON_RADIUS = 17f;
    static final float GRID_TEXT_GAP = 10f;
    static final float GRID_TEXT = 20f;
    static final long GRID_SCROLL_MS = 220;
    static final float LABEL_PAD = 8f;

    // ---------- меню дока (строки — по меню варианта 4 Дизайнера, 2026-11-09) ----------
    static final float MENU_W = 340f;
    /** Меню расширяется под самую длинную строку («В док вместо «…»»), но не шире этого. */
    static final float MENU_W_MAX = 520f;
    static final float MENU_PAD = 10f;
    static final float MENU_RADIUS = 28f;
    static final float MENU_ROW_H = 60f;
    /** Концентрично меню: 28 − 10 (отступ) = 18. */
    static final float MENU_ROW_RADIUS = 18f;
    static final float MENU_ROW_GAP = 4f;
    static final float MENU_ROW_PAD = 18f;
    static final float MENU_ICON_GAP = 14f;
    static final float MENU_TEXT = 22f;
    /** Зазор между меню и плиткой, к которой оно относится. */
    static final float MENU_OFFSET = 16f;
    /** Появление: alpha 0→1 и сдвиг −8→0 за 180 мс; уход — 130 мс. */
    static final float MENU_SHIFT = 8f;
    static final long MENU_IN_MS = 180;
    static final long MENU_OUT_MS = 130;
    /** Пока открыто меню, часы приглушены. */
    static final float MENU_DIM = 0.35f;

    // ---------- иконки приложений ----------
    /** Подложка и кромка иконки: rgba(255,255,255,.14). */
    static final int ICON_BACKING = 0x24FFFFFF;

    // ---------- метка заглушки фона ----------
    static final float PH_LABEL_TOP = 6f;
    static final float PH_LABEL_TEXT = 13f;
    static final float PH_LABEL_RADIUS = 6f;
    static final float PH_LABEL_PAD_H = 10f;
    static final float PH_LABEL_PAD_V = 5f;
    static final int PH_LABEL_BG = 0x66000000;
    static final int PH_LABEL_INK = 0xCCFFFFFF;
}
