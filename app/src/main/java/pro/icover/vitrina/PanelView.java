package pro.icover.vitrina;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.View;
import android.view.ViewParent;
import android.widget.FrameLayout;

/**
 * Панель «Витрины»: стекло (кусок размытого фона + тонировка + кромка), тень и белая подложка фокуса.
 * Всё рисуется заранее подготовленными битмапами и простыми фигурами — без живого размытия.
 */
final class PanelView extends FrameLayout {
    /** Без стекла (плитка дока). */
    static final int GLASS_NONE = 0;
    /** Полное стекло: кусок размытого фона + тонировка + кромка (док, лист, ярлыки). */
    static final int GLASS_FULL = 1;
    /** Только кромка — плитка на стеклянном листе: её фон совпадает с фоном листа. */
    static final int GLASS_EDGE = 2;

    /** Круги-ярлыки в ряду под доком. */
    static final int GROUP_SHORTCUT = 0;
    static final int GROUP_DOCK = 1;
    static final int GROUP_GRID = 2;
    /** Круг «Все приложения». */
    static final int GROUP_ALL = 3;
    /** Строки меню дока. */
    static final int GROUP_MENU = 4;

    /** Как перекрашивать содержимое при фокусе: t = 0 — обычное состояние, 1 — в фокусе. */
    interface FocusInk {
        void apply(float t);
    }

    /** Отладка рывков (am start --es off "sge"): выключить тени / стекло / кромки. Только для замеров. */
    static boolean debugNoShadow;
    static boolean debugNoGlass;
    static boolean debugNoEdge;

    private final Ui ui;
    private int glass = GLASS_NONE;
    private float radius;
    private boolean topOnly;
    private GlassSource source;
    private BitmapShader shader;
    private Bitmap shaderBitmap;
    private boolean panelShadow;
    private Bitmap panelShadowMask;
    private boolean plate;
    private float plateT;
    private Bitmap plateShadowMask;
    private FocusInk ink;
    private ValueAnimator plateAnim;

    int group = -1;
    Runnable action;
    /** Подпись под кругом при фокусе; null — без подписи. */
    String caption;
    /** Строки меню при фокусе не увеличиваются: ×1.12 вылез бы за край меню. */
    boolean scaleOnFocus = true;

    private final RectF rect = new RectF();
    private final RectF edgeRect = new RectF();
    private final RectF highlightRect = new RectF();
    private final RectF dst = new RectF();
    private final Path shape = new Path();
    private final Path edgePath = new Path();
    private final Path highlightPath = new Path();
    private final Matrix matrix = new Matrix();
    private final Paint glassPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint edgePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint highlightPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint platePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint maskPaint = new Paint(Paint.FILTER_BITMAP_FLAG);

    PanelView(Context ctx, Ui ui) {
        super(ctx);
        this.ui = ui;
        setWillNotDraw(false);
        setClipChildren(false);
        setClipToPadding(false);
        edgePaint.setStyle(Paint.Style.STROKE);
        edgePaint.setStrokeWidth(ui.f(Tokens.EDGE_WIDTH));
        edgePaint.setColor(Tokens.EDGE_COLOR);
        highlightPaint.setStyle(Paint.Style.STROKE);
        highlightPaint.setStrokeWidth(ui.f(Tokens.HIGHLIGHT_WIDTH));
        platePaint.setColor(Tokens.PLATE_COLOR);
    }

    PanelView glass(int mode, GlassSource src) {
        glass = mode;
        source = src;
        return this;
    }

    PanelView glassAlpha(int alpha) {
        glassPaint.setAlpha(alpha);
        return this;
    }

    PanelView shape(float designRadius, boolean onlyTopCorners) {
        radius = ui.f(designRadius);
        topOnly = onlyTopCorners;
        return this;
    }

    PanelView panelShadow() {
        panelShadow = true;
        return this;
    }

    PanelView focusPlate(FocusInk focusInk) {
        plate = true;
        ink = focusInk;
        setFocusable(true);
        return this;
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        rect.set(0, 0, w, h);
        float e = ui.f(Tokens.EDGE_WIDTH) / 2f;
        edgeRect.set(e, e, w - e, h - e);
        float hl = ui.f(Tokens.HIGHLIGHT_WIDTH) / 2f;
        highlightRect.set(hl, hl, w - hl, h - hl);
        buildPath(shape, rect, radius);
        buildPath(edgePath, edgeRect, Math.max(0, radius - e));
        buildPath(highlightPath, highlightRect, Math.max(0, radius - hl));
        highlightPaint.setShader(new LinearGradient(0, 0, 0, Math.max(1f, radius * Tokens.HIGHLIGHT_FADE),
                Tokens.HIGHLIGHT_COLOR, Tokens.HIGHLIGHT_COLOR & 0x00FFFFFF, Shader.TileMode.CLAMP));
        panelShadowMask = null;
        plateShadowMask = null;
    }

    /**
     * Контур нужен только листу (скруглены лишь верхние углы). Остальные фигуры рисуются
     * drawRoundRect: на Mali-450 это дешёвая готовая фигура, а не растеризация контура.
     */
    private void fill(Canvas c, Paint p) {
        if (topOnly) {
            c.drawPath(shape, p);
        } else {
            c.drawRoundRect(rect, radius, radius, p);
        }
    }

    private void buildPath(Path p, RectF r, float rad) {
        p.reset();
        float b = topOnly ? 0 : rad;
        p.addRoundRect(r, new float[] {rad, rad, rad, rad, b, b, b, b}, Path.Direction.CW);
    }

    /** Фон сменился (время суток): пересоздать шейдер при следующем кадре. */
    void glassChanged() {
        shader = null;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas c) {
        int w = getWidth();
        int h = getHeight();
        if (panelShadow && !debugNoShadow) {
            float blur = ui.f(Tokens.PANEL_SHADOW_BLUR);
            if (panelShadowMask == null) {
                panelShadowMask = Shadows.make(w, h, radius, topOnly, blur);
            }
            drawMask(c, panelShadowMask, blur, ui.f(Tokens.PANEL_SHADOW_Y), Tokens.PANEL_SHADOW_ALPHA);
        }
        if (glass == GLASS_FULL && source != null && source.blurred != null && !debugNoGlass) {
            if (shader == null || shaderBitmap != source.blurred) {
                shaderBitmap = source.blurred;
                shader = new BitmapShader(shaderBitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
                glassPaint.setShader(shader);
            }
            float[] pos = positionIn(source.root);
            matrix.setScale(source.scale, source.scale);
            matrix.postTranslate(-pos[0], -pos[1]);
            shader.setLocalMatrix(matrix);
            // Тонировка уже запечена в размытый фон (GlassSource.blur): одна заливка вместо двух.
            fill(c, glassPaint);
        }
        if (glass != GLASS_NONE && !debugNoEdge) {
            if (topOnly) {
                c.drawPath(edgePath, edgePaint);
                c.drawPath(highlightPath, highlightPaint);
            } else {
                float e = ui.f(Tokens.EDGE_WIDTH) / 2f;
                float hl = ui.f(Tokens.HIGHLIGHT_WIDTH) / 2f;
                c.drawRoundRect(edgeRect, Math.max(0, radius - e), Math.max(0, radius - e), edgePaint);
                c.drawRoundRect(highlightRect, Math.max(0, radius - hl), Math.max(0, radius - hl), highlightPaint);
            }
        }
        if (plate && plateT > 0f) {
            float blur = ui.f(Tokens.PLATE_SHADOW_BLUR);
            if (plateShadowMask == null) {
                plateShadowMask = Shadows.make(w, h, radius, topOnly, blur);
            }
            drawMask(c, plateShadowMask, blur, ui.f(Tokens.PLATE_SHADOW_Y),
                    Math.round(Tokens.PLATE_SHADOW_ALPHA * plateT));
            platePaint.setAlpha(Math.round(255 * plateT));
            fill(c, platePaint);
        }
    }

    private void drawMask(Canvas c, Bitmap mask, float pad, float dy, int alpha) {
        dst.set(-pad, -pad + dy, getWidth() + pad, getHeight() + pad + dy);
        maskPaint.setColor(0xFF000000);
        maskPaint.setAlpha(alpha);
        c.drawBitmap(mask, null, dst, maskPaint);
    }

    /** Положение панели в корне с учётом сдвигов (лист выезжает — стекло под ним остаётся на месте). */
    private final float[] pos = new float[2];

    private float[] positionIn(View root) {
        float x = 0;
        float y = 0;
        View v = this;
        while (v != null && v != root) {
            x += v.getLeft() + v.getTranslationX();
            y += v.getTop() + v.getTranslationY();
            ViewParent p = v.getParent();
            v = p instanceof View ? (View) p : null;
        }
        pos[0] = x;
        pos[1] = y;
        return pos;
    }

    void setFocusedAnimated(boolean on) {
        if (plateAnim != null) {
            plateAnim.cancel();
        }
        long ms = on ? Tokens.FOCUS_IN_MS : Tokens.FOCUS_OUT_MS;
        plateAnim = ValueAnimator.ofFloat(plateT, on ? 1f : 0f);
        plateAnim.setDuration(ms);
        plateAnim.setInterpolator(Ui.EASE_FOCUS);
        plateAnim.addUpdateListener(a -> {
            plateT = (float) a.getAnimatedValue();
            if (ink != null) {
                ink.apply(plateT);
            }
            invalidate();
        });
        plateAnim.start();
        if (scaleOnFocus) {
            float s = on ? Tokens.FOCUS_SCALE : 1f;
            animate().scaleX(s).scaleY(s).setDuration(ms).setInterpolator(Ui.EASE_FOCUS).start();
        }
    }

    void press() {
        float base = scaleOnFocus ? Tokens.FOCUS_SCALE : 1f;
        float down = base * Tokens.PRESS_SCALE;
        animate().scaleX(down).scaleY(down).setDuration(Tokens.PRESS_MS).setInterpolator(Ui.EASE_FOCUS)
                .withEndAction(() -> animate().scaleX(base).scaleY(base)
                        .setDuration(Tokens.PRESS_MS).setInterpolator(Ui.EASE_FOCUS).start())
                .start();
    }
}
