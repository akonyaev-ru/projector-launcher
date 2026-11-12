package pro.icover.vitrina;

import android.animation.TimeInterpolator;
import android.view.animation.PathInterpolator;

/** Перевод px макета 1280×720 в px экрана и общие кривые движения. */
final class Ui {
    /** Фокус и прокрутка: cubic-bezier(.22,1,.36,1) — без перелёта. */
    static final TimeInterpolator EASE_FOCUS = new PathInterpolator(0.22f, 1f, 0.36f, 1f);
    /** Лист: cubic-bezier(.32,.72,0,1). */
    static final TimeInterpolator EASE_SHEET = new PathInterpolator(0.32f, 0.72f, 0f, 1f);

    final float k;

    Ui(int screenWidthPx) {
        k = screenWidthPx / Tokens.DESIGN_W;
    }

    float f(float designPx) {
        return designPx * k;
    }

    int px(float designPx) {
        return Math.round(designPx * k);
    }
}
