package com.deuxfreres.emballage;

import android.animation.AnimatorInflater;
import android.app.Activity;
import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.PathInterpolator;

import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

/** Petits outils pour le design « verre » : plein écran, marges système, animations. */
final class Ui {
    private Ui() {}

    static final PathInterpolator SMOOTH = new PathInterpolator(0.2f, 0.9f, 0.25f, 1f);

    static int dp(Context c, float v) {
        return Math.round(v * c.getResources().getDisplayMetrics().density);
    }

    /** Le contenu passe sous la barre d'état et la barre de navigation (fond aurore visible partout). */
    static void edgeToEdge(Activity a) {
        WindowCompat.setDecorFitsSystemWindows(a.getWindow(), false);
    }

    /** Ajoute la hauteur de la barre d'état au padding haut de la vue. */
    static void padTop(View v) {
        int base = v.getPaddingTop();
        ViewCompat.setOnApplyWindowInsetsListener(v, (view, ins) -> {
            Insets s = ins.getInsets(WindowInsetsCompat.Type.statusBars());
            view.setPadding(view.getPaddingLeft(), base + s.top, view.getPaddingRight(), view.getPaddingBottom());
            return ins;
        });
    }

    /** Ajoute la barre de navigation (et le clavier si ime) au padding bas de la vue. */
    static void padBottom(View v, boolean ime) {
        int base = v.getPaddingBottom();
        ViewCompat.setOnApplyWindowInsetsListener(v, (view, ins) -> {
            int type = WindowInsetsCompat.Type.navigationBars() | (ime ? WindowInsetsCompat.Type.ime() : 0);
            Insets s = ins.getInsets(type);
            view.setPadding(view.getPaddingLeft(), view.getPaddingTop(), view.getPaddingRight(), base + s.bottom);
            return ins;
        });
    }

    /** Marge basse = barre de navigation + valeur de base. */
    static void marginBottom(View v) {
        ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) v.getLayoutParams();
        int base = lp.bottomMargin;
        ViewCompat.setOnApplyWindowInsetsListener(v, (view, ins) -> {
            Insets s = ins.getInsets(WindowInsetsCompat.Type.navigationBars());
            ViewGroup.MarginLayoutParams p = (ViewGroup.MarginLayoutParams) view.getLayoutParams();
            p.bottomMargin = base + s.bottom;
            view.setLayoutParams(p);
            return ins;
        });
    }

    /** Effet « appui » : la vue se contracte puis rebondit. */
    static void press(View v) {
        v.setStateListAnimator(AnimatorInflater.loadStateListAnimator(v.getContext(), R.animator.press_scale));
    }

    /** Apparition en cascade des enfants (montée + fondu). */
    static void stagger(ViewGroup parent, long startDelay) {
        for (int i = 0; i < parent.getChildCount(); i++) {
            View c = parent.getChildAt(i);
            if (c.getVisibility() != View.VISIBLE) continue;
            rise(c, startDelay + i * 45L);
        }
    }

    static void rise(View c, long delay) {
        c.setAlpha(0f);
        c.setTranslationY(dp(c.getContext(), 14));
        c.animate().alpha(1f).translationY(0f).setStartDelay(delay)
                .setDuration(620).setInterpolator(SMOOTH).start();
    }

    /** Apparition douce (léger zoom + fondu) : sobre, sans rebond. */
    static void bounce(View v) {
        // animateur séparé : ne coupe pas un fondu/montée en cours sur la même vue
        android.animation.ObjectAnimator a = android.animation.ObjectAnimator.ofPropertyValuesHolder(v,
                android.animation.PropertyValuesHolder.ofFloat(View.SCALE_X, 0.9f, 1f),
                android.animation.PropertyValuesHolder.ofFloat(View.SCALE_Y, 0.9f, 1f));
        a.setDuration(520);
        a.setInterpolator(SMOOTH);
        a.start();
    }
}
