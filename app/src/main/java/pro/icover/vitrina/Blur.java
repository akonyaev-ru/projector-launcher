package pro.icover.vitrina;

/**
 * Размытие маленькой копии фона для стекла. Выполняется один раз при смене фона, не в кадре:
 * три прохода box blur по горизонтали и вертикали ≈ гауссово (как в демо Дизайнера),
 * затем насыщенность и яркость. Чистая логика на int[] ARGB — проверяется JUnit.
 */
public final class Blur {
    static final int PASSES = 3;

    private Blur() {
    }

    public static void boxBlur(int[] argb, int w, int h, int radius) {
        if (radius < 1) {
            return;
        }
        int[] tmp = new int[argb.length];
        for (int p = 0; p < PASSES; p++) {
            pass(argb, tmp, w, h, radius, true);
            pass(tmp, argb, w, h, radius, false);
        }
    }

    private static void pass(int[] src, int[] dst, int w, int h, int r, boolean horizontal) {
        int n = 2 * r + 1;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int sr = 0;
                int sg = 0;
                int sb = 0;
                for (int k = -r; k <= r; k++) {
                    int xx = horizontal ? clamp(x + k, w - 1) : x;
                    int yy = horizontal ? y : clamp(y + k, h - 1);
                    int c = src[yy * w + xx];
                    sr += (c >> 16) & 0xFF;
                    sg += (c >> 8) & 0xFF;
                    sb += c & 0xFF;
                }
                int half = n / 2;
                dst[y * w + x] = 0xFF000000 | (((sr + half) / n) << 16) | (((sg + half) / n) << 8) | ((sb + half) / n);
            }
        }
    }

    private static int clamp(int v, int max) {
        return v < 0 ? 0 : Math.min(v, max);
    }

    /** Насыщенность относительно яркости Rec. 709, затем множитель яркости. */
    public static void saturateBrighten(int[] argb, float saturation, float brightness) {
        for (int i = 0; i < argb.length; i++) {
            int c = argb[i];
            float r = (c >> 16) & 0xFF;
            float g = (c >> 8) & 0xFF;
            float b = c & 0xFF;
            float l = 0.2126f * r + 0.7152f * g + 0.0722f * b;
            argb[i] = 0xFF000000
                    | (channel((l + (r - l) * saturation) * brightness) << 16)
                    | (channel((l + (g - l) * saturation) * brightness) << 8)
                    | channel((l + (b - l) * saturation) * brightness);
        }
    }

    /**
     * Наложить полупрозрачный цвет (тонировку стекла) поверх каждого пикселя — один раз,
     * чтобы панель рисовала стекло одной заливкой, а не двумя.
     */
    public static void overlay(int[] argb, int color) {
        float a = ((color >>> 24) & 0xFF) / 255f;
        float cr = (color >> 16) & 0xFF;
        float cg = (color >> 8) & 0xFF;
        float cb = color & 0xFF;
        for (int i = 0; i < argb.length; i++) {
            int c = argb[i];
            argb[i] = 0xFF000000
                    | (channel(cr * a + ((c >> 16) & 0xFF) * (1 - a)) << 16)
                    | (channel(cg * a + ((c >> 8) & 0xFF) * (1 - a)) << 8)
                    | channel(cb * a + (c & 0xFF) * (1 - a));
        }
    }

    private static int channel(float v) {
        return Math.max(0, Math.min(255, Math.round(v)));
    }
}
