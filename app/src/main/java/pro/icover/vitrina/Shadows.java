package pro.icover.vitrina;

import android.graphics.Bitmap;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;

/**
 * Тени рисуются один раз в маленькую альфа-маску и затем растягиваются: живой тени на Mali-450 нет.
 * Маска ALPHA_8 в четверть размера — тень всё равно размыта, а памяти в 64 раза меньше ARGB.
 */
final class Shadows {
    static final float SCALE = 0.25f;
    /** Skia: sigma = 0.57735 × radius + 0.5. */
    private static final float SKIA_RADIUS_TO_SIGMA = 0.57735f;
    private static final float SKIA_SIGMA_BIAS = 0.5f;

    private Shadows() {
    }

    /**
     * @param w      ширина фигуры, px экрана
     * @param h      высота фигуры, px экрана
     * @param radius радиус скругления, px экрана
     * @param blur   размытие как в CSS box-shadow (sigma = blur / 2), px экрана; оно же поле вокруг
     */
    static Bitmap make(float w, float h, float radius, boolean topOnly, float blur) {
        float s = SCALE;
        int bw = (int) Math.ceil((w + 2 * blur) * s);
        int bh = (int) Math.ceil((h + 2 * blur) * s);
        Bitmap b = Bitmap.createBitmap(Math.max(1, bw), Math.max(1, bh), Bitmap.Config.ALPHA_8);
        Canvas c = new Canvas(b);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(Color.BLACK);
        float sigma = blur / 2f * s;
        if (sigma > SKIA_SIGMA_BIAS) {
            p.setMaskFilter(new BlurMaskFilter((sigma - SKIA_SIGMA_BIAS) / SKIA_RADIUS_TO_SIGMA,
                    BlurMaskFilter.Blur.NORMAL));
        }
        RectF r = new RectF(blur * s, blur * s, (blur + w) * s, (blur + h) * s);
        float rs = radius * s;
        if (topOnly) {
            Path path = new Path();
            path.addRoundRect(r, new float[] {rs, rs, rs, rs, 0, 0, 0, 0}, Path.Direction.CW);
            c.drawPath(path, p);
        } else {
            c.drawRoundRect(r, rs, rs, p);
        }
        return b;
    }
}
