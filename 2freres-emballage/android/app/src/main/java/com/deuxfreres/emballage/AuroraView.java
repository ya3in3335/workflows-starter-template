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

    private static final int[] COLORS = {0x80E0457B, 0x66C8A165, 0x553FAE7A, 0x557A4DFF};
    private static final float[][] ORBITS = {
            // x, y (fraction de l'écran), rayon (fraction de la largeur), amplitude, vitesse, phase
            {0.10f, 0.10f, 0.95f, 0.10f, 0.00011f, 0f},
            {0.95f, 0.50f, 0.90f, 0.12f, 0.00009f, 2f},
            {0.15f, 0.95f, 0.85f, 0.10f, 0.00013f, 4f},
            {0.85f, 0.05f, 0.60f, 0.08f, 0.00017f, 1f},
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
        canvas.drawColor(0xFF0E0B10);
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
        if (running) postInvalidateDelayed(40);
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
