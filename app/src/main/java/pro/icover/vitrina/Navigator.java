package pro.icover.vitrina;

/**
 * Выбор следующего элемента по стрелке пульта — по геометрии центров, как в согласованном демо.
 * Чистая логика без Android: проверяется JUnit.
 */
public final class Navigator {
    public static final int LEFT = 0;
    public static final int RIGHT = 1;
    public static final int UP = 2;
    public static final int DOWN = 3;

    /** Элемент считается «в нужной стороне», только если сдвиг по главной оси больше этого порога, px. */
    static final float MIN_STEP = 4f;
    /** Штраф за уход с ряда при движении влево-вправо: держимся текущего ряда. */
    static final float ROW_PENALTY = 4f;
    /** Штраф за уход со столбца при движении вверх-вниз. */
    static final float COLUMN_PENALTY = 1.2f;
    /** Влево-вправо — только внутри своего ряда: центры по Y расходятся не больше, px. */
    static final float ROW_TOLERANCE = 48f;

    private Navigator() {
    }

    /**
     * @param cx      центры элементов по X
     * @param cy      центры элементов по Y
     * @param current индекс текущего элемента
     * @param dir     направление: {@link #LEFT}, {@link #RIGHT}, {@link #UP}, {@link #DOWN}
     * @param allowed какие элементы сейчас доступны (null — все)
     * @return индекс следующего элемента или -1, если в этой стороне ничего нет
     */
    public static int pick(float[] cx, float[] cy, int current, int dir, boolean[] allowed) {
        int best = -1;
        float bestScore = Float.MAX_VALUE;
        boolean horizontal = dir == LEFT || dir == RIGHT;
        for (int i = 0; i < cx.length; i++) {
            if (i == current || (allowed != null && !allowed[i])) {
                continue;
            }
            float dx = cx[i] - cx[current];
            float dy = cy[i] - cy[current];
            float main;
            switch (dir) {
                case RIGHT: main = dx; break;
                case LEFT: main = -dx; break;
                case DOWN: main = dy; break;
                default: main = -dy; break;
            }
            if (main <= MIN_STEP || (horizontal && Math.abs(dy) > ROW_TOLERANCE)) {
                continue;
            }
            float cross = horizontal ? Math.abs(dy) * ROW_PENALTY : Math.abs(dx) * COLUMN_PENALTY;
            float score = main + cross;
            if (score < bestScore) {
                bestScore = score;
                best = i;
            }
        }
        return best;
    }

    /** Ближайший по X элемент среди разрешённых — для входа в сетку сверху. */
    public static int nearestByX(float[] cx, float x, boolean[] allowed) {
        int best = -1;
        float bestDx = Float.MAX_VALUE;
        for (int i = 0; i < cx.length; i++) {
            if (allowed != null && !allowed[i]) {
                continue;
            }
            float d = Math.abs(cx[i] - x);
            if (d < bestDx) {
                bestDx = d;
                best = i;
            }
        }
        return best;
    }
}
