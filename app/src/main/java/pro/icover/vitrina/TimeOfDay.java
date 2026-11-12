package pro.icover.vitrina;

/** Время суток для фона: утро, день, вечер, ночь. Границы — как в согласованном демо. */
public final class TimeOfDay {
    public static final int MORNING = 0;
    public static final int DAY = 1;
    public static final int EVENING = 2;
    public static final int NIGHT = 3;

    /** Имена файлов фото владельца: wallpapers/<имя>.jpg. */
    public static final String[] FILE_NAMES = {"morning", "day", "evening", "night"};
    /** Подписи для честной заглушки. */
    public static final String[] LABELS = {"утро", "день", "вечер", "ночь"};

    static final int MORNING_FROM = 6;
    static final int DAY_FROM = 11;
    static final int EVENING_FROM = 17;
    static final int NIGHT_FROM = 22;

    private TimeOfDay() {
    }

    public static int slot(int hour) {
        if (hour < MORNING_FROM || hour >= NIGHT_FROM) {
            return NIGHT;
        }
        if (hour < DAY_FROM) {
            return MORNING;
        }
        if (hour < EVENING_FROM) {
            return DAY;
        }
        return EVENING;
    }
}
