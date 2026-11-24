package com.deuxfreres.emballage;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.View;

/**
 * Fond « aurore » : taches de couleur floues qui dérivent lentement derrière les panneaux de verre.
 * Couleurs de la marque (rose, kraft doré, vert) sur fond charbon.
 */
public class AuroraView extends View {

    // Lueurs discrètes : or champagne, bronze, bleu ardoise
    private static final int[] COLORS = {0x4DC9A96E, 0x337A5A36, 0x2E4F6B78, 0x22C9A96E};
    private static final float[][] ORBITS = {
            // x, y (fraction de l'écran), rayon (fraction de la largeur), amplitude, vitesse, phase
            {0.10f, 0.06f, 1.00f, 0.06f, 0.00006f, 0f},
            {0.95f, 0.55f, 0.95f, 0.07f, 0.00005f, 2f},
            {0.20f, 1.00f, 0.90f, 0.06f, 0.00007f, 4f},
            {0.85f, 0.10f, 0.55f, 0.05f, 0.00008f, 1f},
    };

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RadialGradient[] shaders = new RadialGradient[COLORS.length];
    private final android.graphics.Matrix matrix = new android.graphics.Matrix();
    private float shift;          // parallaxe liée au défilement
    private boolean running;

    public AuroraView(Context c) { this(c, null); }
    public AuroraView(Context c, AttributeSet a) {
        super(c, a);
        setWillNotDraw(false);
    }

    /** Décale doucement l'aurore quand l'utilisateur fait défiler. */
    public void setScrollShift(float px) {
        shift = px * 0.15f;
        invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        for (int i = 0; i < COLORS.length; i++) {
            float r = Math.max(1, ORBITS[i][2] * w);
            shaders[i] = new RadialGradient(0, 0, r, COLORS[i], COLORS[i] & 0x00FFFFFF, Shader.TileMode.CLAMP);
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int w = getWidth(), h = getHeight();
        canvas.drawColor(0xFF0D0C0B);
        if (shaders[0] == null) return;
        long t = SystemClock.uptimeMillis();
        for (int i = 0; i < COLORS.length; i++) {
            float[] o = ORBITS[i];
            double a = t * o[4] + o[5];
            float cx = (float) ((o[0] + Math.cos(a) * o[3]) * w);
            float cy = (float) ((o[1] + Math.sin(a * 1.3) * o[3]) * h) - shift * (i % 2 == 0 ? 1 : -0.6f);
            matrix.setTranslate(cx, cy);
            shaders[i].setLocalMatrix(matrix);
            paint.setShader(shaders[i]);
            canvas.drawRect(0, 0, w, h, paint);
        }
        if (running) postInvalidateDelayed(50);
    }

    @Override protected void onAttachedToWindow() { super.onAttachedToWindow(); running = true; invalidate(); }
    @Override protected void onDetachedFromWindow() { running = false; super.onDetachedFromWindow(); }

    @Override
    protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility);
        running = visibility == VISIBLE;
        if (running) invalidate();
    }
}
