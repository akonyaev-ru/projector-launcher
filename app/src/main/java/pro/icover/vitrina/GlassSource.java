package pro.icover.vitrina;

import android.graphics.Bitmap;
import android.view.View;

/**
 * Источник «стекла»: маленькая размытая копия фона. Каждая стеклянная панель рисует свой кусок
 * через BitmapShader с матрицей «увеличить и сдвинуть» — это бесплатно для GPU в каждом кадре.
 */
final class GlassSource {
    /** Корень раскладки: от него считаются координаты панелей. */
    View root;
    /** Размытый фон {@link Tokens#BLUR_W}×{@link Tokens#BLUR_H}. Меняется только на главном потоке. */
    Bitmap blurred;
    /** Во сколько раз растянуть размытый фон до экрана. */
    float scale = 1f;

    /** Тяжёлая часть — вне главного потока: уменьшить, размыть, поднять насыщенность, притемнить, тонировать. */
    static Bitmap blur(Bitmap sharp) {
        Bitmap cur = sharp;
        // Уменьшаем вдвое за шаг: билинейное уменьшение сразу в 8 раз даёт рябь.
        while (cur.getWidth() / 2 >= Tokens.BLUR_W * 2) {
            Bitmap next = Bitmap.createScaledBitmap(cur, cur.getWidth() / 2, cur.getHeight() / 2, true);
            if (cur != sharp) {
                cur.recycle();
            }
            cur = next;
        }
        Bitmap small = Bitmap.createScaledBitmap(cur, Tokens.BLUR_W, Tokens.BLUR_H, true);
        if (cur != sharp) {
            cur.recycle();
        }
        int[] px = new int[Tokens.BLUR_W * Tokens.BLUR_H];
        small.getPixels(px, 0, Tokens.BLUR_W, 0, 0, Tokens.BLUR_W, Tokens.BLUR_H);
        small.recycle();
        Blur.boxBlur(px, Tokens.BLUR_W, Tokens.BLUR_H, Tokens.BLUR_RADIUS);
        Blur.saturateBrighten(px, Tokens.BLUR_SATURATION, Tokens.BLUR_BRIGHTNESS);
        Blur.overlay(px, Tokens.GLASS_TINT);
        return Bitmap.createBitmap(px, Tokens.BLUR_W, Tokens.BLUR_H, Bitmap.Config.ARGB_8888);
    }
}
