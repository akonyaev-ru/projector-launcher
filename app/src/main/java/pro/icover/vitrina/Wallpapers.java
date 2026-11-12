package pro.icover.vitrina;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.Shader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Фон по времени суток: wallpapers/{morning,day,evening,night}.jpg. Встроены в APK (assets, фото
 * владельца 2026-11-09, 1280×720); файл с тем же именем в папке приложения их заменяет (adb push,
 * без пересборки). Нет ни того, ни другого — честная заглушка с подписью «фото-заглушка».
 */
final class Wallpapers {
    static final String DIR = "wallpapers";

    static final class Result {
        final Bitmap bitmap;
        final boolean placeholder;

        Result(Bitmap bitmap, boolean placeholder) {
            this.bitmap = bitmap;
            this.placeholder = placeholder;
        }
    }

    /** Палитры заглушки — из демо. */
    private static final class Palette {
        final int[] sky;
        final float[] stops;
        final float sunX;
        final float sunY;
        final float sunR;
        final int glow;
        final int disc;
        final int[] hills;
        final boolean stars;

        Palette(int[] sky, float[] stops, float sunX, float sunY, float sunR, int glow, int disc,
                int[] hills, boolean stars) {
            this.sky = sky;
            this.stops = stops;
            this.sunX = sunX;
            this.sunY = sunY;
            this.sunR = sunR;
            this.glow = glow;
            this.disc = disc;
            this.hills = hills;
            this.stars = stars;
        }
    }

    private static final Palette[] PALETTES = {
        new Palette(new int[] {0xFF7F9CC6, 0xFFE9C3A6, 0xFFF6DCC0}, new float[] {0f, 0.55f, 1f},
                0.72f, 0.56f, 0.035f, 0x8CFFD6AA, 0xFFFFF1DC,
                new int[] {0xFF7C8C8F, 0xFF566A6B, 0xFF33474A}, false),
        new Palette(new int[] {0xFF2F6FB8, 0xFF8FC2E8, 0xFFCFE6F2}, new float[] {0f, 0.6f, 1f},
                0.82f, 0.16f, 0.03f, 0x99FFFAE1, 0xFFFFFBEA,
                new int[] {0xFF6E9A5C, 0xFF4C7C43, 0xFF2E5A2E}, false),
        new Palette(new int[] {0xFF2E2350, 0xFF8C4A6A, 0xFFE5835A, 0xFFF2A66A}, new float[] {0f, 0.5f, 0.8f, 1f},
                0.30f, 0.70f, 0.04f, 0x99FFAA6E, 0xFFFFD3A0,
                new int[] {0xFF5A3550, 0xFF3A2340, 0xFF1F1426}, false),
        new Palette(new int[] {0xFF05081A, 0xFF121B3A, 0xFF22305A}, new float[] {0f, 0.6f, 1f},
                0.78f, 0.18f, 0.022f, 0x40C8D7FF, 0xFFE6ECF8,
                new int[] {0xFF141B33, 0xFF0C1226, 0xFF060914}, true),
    };

    /** Холмы: базовая линия, амплитуда, частота, фаза — из демо. */
    private static final float[][] HILLS = {{0.70f, 0.05f, 1.3f, 0.4f}, {0.80f, 0.05f, 1.8f, 1.9f}, {0.90f, 0.04f, 2.4f, 3.1f}};
    private static final int HILL_STEPS = 96;
    private static final int STAR_COUNT = 90;
    private static final int STAR_COLOR = 0xBFFFFFFF;
    private static final float STAR_MAX = 1.4f;
    private static final float STAR_MIN = 0.4f;
    private static final float STAR_SKY = 0.55f;
    private static final float GLOW_RADIUS = 7f;
    private static final float SECOND_WAVE_FREQ = 2.3f;
    private static final float SECOND_WAVE_PHASE = 1.7f;
    private static final float SECOND_WAVE_AMP = 0.4f;
    private static final long LCG_SEED = 11;
    private static final long LCG_MUL = 16807;
    private static final long LCG_MOD = 2147483647L;

    private Wallpapers() {
    }

    static Result load(Context ctx, int slot, int w, int h) {
        Bitmap photo = loadPhoto(ctx, slot, w, h);
        boolean placeholder = photo == null;
        Bitmap b = placeholder ? paintPlaceholder(slot, w, h) : photo;
        scrim(b);
        Bitmap compact = b.copy(Bitmap.Config.RGB_565, false);
        b.recycle();
        return new Result(compact, placeholder);
    }

    /** Источник фото: сначала файл владельца в папке приложения, иначе встроенный в APK (assets). */
    private interface Source {
        InputStream open() throws IOException;
    }

    private static Bitmap loadPhoto(Context ctx, int slot, int w, int h) {
        String name = DIR + "/" + TimeOfDay.FILE_NAMES[slot] + ".jpg";
        File base = ctx.getExternalFilesDir(null);
        File f = base == null ? null : new File(base, name);
        Source src = f != null && f.isFile()
                ? () -> new FileInputStream(f)
                : () -> ctx.getAssets().open(name);
        try {
            return decodeCover(src, w, h);
        } catch (IOException e) {
            // Нет ни своего файла, ни встроенного — будет заглушка.
            return null;
        }
    }

    private static Bitmap decodeCover(Source source, int w, int h) throws IOException {
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        try (InputStream in = source.open()) {
            BitmapFactory.decodeStream(in, null, o);
        }
        if (o.outWidth <= 0 || o.outHeight <= 0) {
            return null;
        }
        int sample = 1;
        while (o.outWidth / (sample * 2) >= w && o.outHeight / (sample * 2) >= h) {
            sample *= 2;
        }
        o = new BitmapFactory.Options();
        o.inSampleSize = sample;
        Bitmap src;
        try (InputStream in = source.open()) {
            src = BitmapFactory.decodeStream(in, null, o);
        }
        if (src == null) {
            return null;
        }
        // Обрезка по центру под 16:9, как background-size: cover.
        float scale = Math.max(w / (float) src.getWidth(), h / (float) src.getHeight());
        int cw = Math.round(w / scale);
        int ch = Math.round(h / scale);
        int cx = (src.getWidth() - cw) / 2;
        int cy = (src.getHeight() - ch) / 2;
        Bitmap out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        new Canvas(out).drawBitmap(src, new Rect(cx, cy, cx + cw, cy + ch), new Rect(0, 0, w, h),
                new Paint(Paint.FILTER_BITMAP_FLAG));
        src.recycle();
        return out;
    }

    static Bitmap paintPlaceholder(int slot, int w, int h) {
        Palette p = PALETTES[slot];
        Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setShader(new LinearGradient(0, 0, 0, h, p.sky, p.stops, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, paint);
        paint.setShader(null);
        if (p.stars) {
            long s = LCG_SEED;
            paint.setColor(STAR_COLOR);
            for (int i = 0; i < STAR_COUNT; i++) {
                s = (s * LCG_MUL) % LCG_MOD;
                float x = s / (float) LCG_MOD * w;
                s = (s * LCG_MUL) % LCG_MOD;
                float y = s / (float) LCG_MOD * h * STAR_SKY;
                s = (s * LCG_MUL) % LCG_MOD;
                float r = Math.max(STAR_MIN, w / Tokens.DESIGN_W * (s / (float) LCG_MOD * STAR_MAX));
                c.drawCircle(x, y, r, paint);
            }
        }
        float sx = p.sunX * w;
        float sy = p.sunY * h;
        float sr = p.sunR * w;
        paint.setShader(new RadialGradient(sx, sy, sr * GLOW_RADIUS, p.glow, p.glow & 0x00FFFFFF, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, paint);
        paint.setShader(null);
        paint.setColor(p.disc);
        c.drawCircle(sx, sy, sr, paint);
        for (int i = 0; i < HILLS.length; i++) {
            float base = HILLS[i][0];
            float amp = HILLS[i][1];
            float freq = HILLS[i][2];
            float phase = HILLS[i][3];
            Path path = new Path();
            path.moveTo(0, h);
            for (int k = 0; k <= HILL_STEPS; k++) {
                float x = w * k / (float) HILL_STEPS;
                double t = x / w;
                double y = h * base
                        - Math.sin(t * freq * Math.PI * 2 + phase) * amp * h
                        - Math.sin(t * freq * SECOND_WAVE_FREQ * Math.PI + phase * SECOND_WAVE_PHASE) * amp * SECOND_WAVE_AMP * h;
                path.lineTo(x, (float) y);
            }
            path.lineTo(w, h);
            path.close();
            paint.setColor(p.hills[i]);
            c.drawPath(path, paint);
        }
        return b;
    }

    /** Притемнение верха под часы (токен Дизайнера) — одинаково для фото и заглушки. */
    private static void scrim(Bitmap b) {
        int w = b.getWidth();
        int h = b.getHeight();
        Paint paint = new Paint();
        float end = h * Tokens.TOP_SCRIM_HEIGHT;
        paint.setShader(new LinearGradient(0, 0, 0, end, Tokens.TOP_SCRIM, 0, Shader.TileMode.CLAMP));
        new Canvas(b).drawRect(0, 0, w, end, paint);
    }
}
