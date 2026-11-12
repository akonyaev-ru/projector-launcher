package pro.icover.vitrina;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Resources;
import android.util.DisplayMetrics;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.drawable.AdaptiveIconDrawable;
import android.graphics.drawable.Drawable;
import java.text.Collator;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Установленные приложения, которые можно запустить с ТВ-экрана. */
final class AppRepository {
    /** Видимая часть адаптивной иконки: 72 из 108 dp. */
    private static final float ADAPTIVE_VISIBLE = 72f / 108f;
    private static final float ICON_EDGE_STROKE = 2f;

    static final class App {
        final String pkg;
        final String cls;
        final String label;
        final boolean leanback;
        final Drawable icon;
        Bitmap iconDock;
        Bitmap iconGrid;

        App(String pkg, String cls, String label, boolean leanback, Drawable icon) {
            this.pkg = pkg;
            this.cls = cls;
            this.label = label;
            this.leanback = leanback;
            this.icon = icon;
        }
    }

    private AppRepository() {
    }

    /** Список приложений, отсортированный по названию. Вызывать вне главного потока. */
    static List<App> load(Context ctx) {
        PackageManager pm = ctx.getPackageManager();
        Map<String, App> byPkg = new LinkedHashMap<>();
        // Сначала ТВ-вход: у ТВ-приложений он правильный, а обычный может открыть версию для телефона.
        collect(ctx, pm, Intent.CATEGORY_LEANBACK_LAUNCHER, true, byPkg);
        collect(ctx, pm, Intent.CATEGORY_LAUNCHER, false, byPkg);
        List<App> out = new ArrayList<>(byPkg.values());
        Collator collator = Collator.getInstance(new Locale("ru"));
        out.sort((a, b) -> collator.compare(a.label, b.label));
        return out;
    }

    private static void collect(Context ctx, PackageManager pm, String category, boolean leanback,
            Map<String, App> into) {
        Intent i = new Intent(Intent.ACTION_MAIN).addCategory(category);
        for (ResolveInfo ri : pm.queryIntentActivities(i, 0)) {
            String pkg = ri.activityInfo.packageName;
            if (pkg.equals(ctx.getPackageName()) || into.containsKey(pkg)) {
                continue;
            }
            CharSequence label = ri.loadLabel(pm);
            Drawable icon = loadSharpIcon(pm, ri);
            into.put(pkg, new App(pkg, ri.activityInfo.name, label == null ? pkg : label.toString().trim(),
                    leanback, icon));
        }
    }

    /**
     * На проекторе плотность 213 (tvdpi), и система отдаёт иконку под неё — в плитке 96 px она мыльная.
     * Берём ту же иконку из ресурсов для xxxhdpi; не вышло — обычная.
     */
    private static Drawable loadSharpIcon(PackageManager pm, ResolveInfo ri) {
        int res = ri.getIconResource();
        if (res != 0) {
            try {
                Drawable d = pm.getResourcesForApplication(ri.activityInfo.applicationInfo)
                        .getDrawableForDensity(res, DisplayMetrics.DENSITY_XXXHIGH, null);
                if (d != null) {
                    return d;
                }
            } catch (PackageManager.NameNotFoundException | Resources.NotFoundException ignored) {
                // Ниже — обычная иконка.
            }
        }
        return ri.loadIcon(pm);
    }

    /** Иконка в скруглённом квадрате: заранее, чтобы в кадре рисовать готовую картинку. */
    static Bitmap renderIcon(App app, int size, float radius, int backing) {
        Drawable d = app.icon;
        Bitmap b = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        Path clip = new Path();
        RectF r = new RectF(0, 0, size, size);
        clip.addRoundRect(r, radius, radius, Path.Direction.CW);
        c.clipPath(clip);
        Paint backingPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        backingPaint.setColor(backing);
        c.drawRect(r, backingPaint);
        if (d instanceof AdaptiveIconDrawable) {
            AdaptiveIconDrawable a = (AdaptiveIconDrawable) d;
            int extra = Math.round(size * (1f / ADAPTIVE_VISIBLE - 1f) / 2f);
            for (Drawable layer : new Drawable[] {a.getBackground(), a.getForeground()}) {
                if (layer != null) {
                    layer.setBounds(-extra, -extra, size + extra, size + extra);
                    layer.draw(c);
                }
            }
        } else if (d != null) {
            d.setBounds(0, 0, size, size);
            d.draw(c);
        }
        // Тонкая светлая кромка, как .ico в демо: штрих по краю, половина срезана клипом — 1 px внутри.
        Paint edge = new Paint(Paint.ANTI_ALIAS_FLAG);
        edge.setStyle(Paint.Style.STROKE);
        edge.setStrokeWidth(ICON_EDGE_STROKE);
        edge.setColor(backing);
        c.drawRoundRect(r, radius, radius, edge);
        return b;
    }
}
