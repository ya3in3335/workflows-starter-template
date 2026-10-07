package com.deuxfreres.emballage;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.animation.ValueAnimator;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.BounceInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;

import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Écran de démarrage animé en 3D : le « 2 » pivote dans l'espace, les lettres de FRÈRES
 * se retournent une à une, le cadeau tombe en rebondissant et des étoiles scintillent
 * avec un effet de profondeur (parallaxe).
 */
public class SplashActivity extends AppCompatActivity {

    private boolean done;
    private android.media.SoundPool sounds;
    private final android.os.Handler timer = new android.os.Handler(android.os.Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        float density = getResources().getDisplayMetrics().density;
        float camera = 9000 * density;

        View logo = findViewById(R.id.logo);
        View two = findViewById(R.id.two);
        View gift = findViewById(R.id.gift);
        View tagline = findViewById(R.id.tagline);
        ViewGroup letters = findViewById(R.id.letters);

        logo.setCameraDistance(camera);
        two.setCameraDistance(camera);

        // État initial
        two.setAlpha(0f);
        two.setRotationY(-110f);
        two.setRotationX(25f);
        two.setScaleX(0.3f);
        two.setScaleY(0.3f);
        gift.setAlpha(0f);
        gift.setTranslationY(-420 * density);
        gift.setRotation(-35f);
        tagline.setAlpha(0f);
        tagline.setTranslationY(16 * density);
        for (int i = 0; i < letters.getChildCount(); i++) {
            View l = letters.getChildAt(i);
            l.setCameraDistance(camera);
            l.setAlpha(0f);
            l.setRotationX(-100f);
            l.setTranslationZ(24 * density);
        }

        List<Animator> all = new ArrayList<>();

        // 1) Le « 2 » arrive du fond en tournant en 3D
        ObjectAnimator twoIn = ObjectAnimator.ofPropertyValuesHolder(two,
                PropertyValuesHolder.ofFloat(View.ALPHA, 0f, 1f),
                PropertyValuesHolder.ofFloat(View.ROTATION_Y, -110f, 12f, 0f),
                PropertyValuesHolder.ofFloat(View.ROTATION_X, 25f, -6f, 0f),
                PropertyValuesHolder.ofFloat(View.SCALE_X, 0.3f, 1.08f, 1f),
                PropertyValuesHolder.ofFloat(View.SCALE_Y, 0.3f, 1.08f, 1f));
        twoIn.setDuration(1000);
        twoIn.setInterpolator(new DecelerateInterpolator(1.6f));
        all.add(twoIn);

        // 2) Les lettres se retournent l'une après l'autre
        for (int i = 0; i < letters.getChildCount(); i++) {
            ObjectAnimator a = ObjectAnimator.ofPropertyValuesHolder(letters.getChildAt(i),
                    PropertyValuesHolder.ofFloat(View.ALPHA, 0f, 1f),
                    PropertyValuesHolder.ofFloat(View.ROTATION_X, -100f, 0f),
                    PropertyValuesHolder.ofFloat(View.TRANSLATION_Z, 24 * density, 0f));
            a.setStartDelay(550 + i * 90L);
            a.setDuration(520);
            a.setInterpolator(new OvershootInterpolator(1.4f));
            all.add(a);
        }

        // 3) Le cadeau tombe et rebondit
        ObjectAnimator giftIn = ObjectAnimator.ofPropertyValuesHolder(gift,
                PropertyValuesHolder.ofFloat(View.ALPHA, 0f, 1f),
                PropertyValuesHolder.ofFloat(View.TRANSLATION_Y, -420 * density, 0f),
                PropertyValuesHolder.ofFloat(View.ROTATION, -35f, 0f));
        giftIn.setStartDelay(900);
        giftIn.setDuration(850);
        giftIn.setInterpolator(new BounceInterpolator());
        all.add(giftIn);

        ObjectAnimator tag = ObjectAnimator.ofPropertyValuesHolder(tagline,
                PropertyValuesHolder.ofFloat(View.ALPHA, 0f, 1f),
                PropertyValuesHolder.ofFloat(View.TRANSLATION_Y, 16 * density, 0f));
        tag.setStartDelay(1250);
        tag.setDuration(450);
        all.add(tag);

        // 4) Léger balancement 3D de tout le logo (effet « flottant »)
        ObjectAnimator sway = ObjectAnimator.ofPropertyValuesHolder(logo,
                PropertyValuesHolder.ofFloat(View.ROTATION_Y, 0f, 10f, -8f, 0f),
                PropertyValuesHolder.ofFloat(View.ROTATION_X, 0f, -6f, 4f, 0f));
        sway.setStartDelay(1100);
        sway.setDuration(1300);
        sway.setInterpolator(new DecelerateInterpolator());
        all.add(sway);

        addStars(findViewById(R.id.stars), density, all);

        playSounds(letters.getChildCount());

        AnimatorSet set = new AnimatorSet();
        set.playTogether(all);
        set.addListener(new AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(Animator animation) { exit(logo, density); }
        });
        set.start();

        findViewById(R.id.root).setOnClickListener(v -> { set.end(); });
    }

    /** Effets sonores synchronisés avec l'animation (rien si le téléphone est en silencieux/vibreur). */
    private void playSounds(int letterCount) {
        android.media.AudioManager am = (android.media.AudioManager) getSystemService(AUDIO_SERVICE);
        if (am == null || am.getRingerMode() != android.media.AudioManager.RINGER_MODE_NORMAL) return;
        sounds = new android.media.SoundPool.Builder().setMaxStreams(4)
                .setAudioAttributes(new android.media.AudioAttributes.Builder()
                        .setUsage(android.media.AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                        .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
                .build();
        int whoosh = sounds.load(this, R.raw.splash_whoosh, 1);
        int tick = sounds.load(this, R.raw.splash_tick, 1);
        int pop = sounds.load(this, R.raw.splash_pop, 1);
        int chime = sounds.load(this, R.raw.splash_chime, 1);
        sounds.setOnLoadCompleteListener((pool, id, status) -> {
            if (id != chime || status != 0) return;            // tout est chargé (ordre de chargement)
            play(whoosh, 0, 0.55f, 1f);
            for (int i = 0; i < letterCount; i++) play(tick, 600 + i * 90L, 0.35f, 0.85f + i * 0.08f);
            play(pop, 1210, 0.6f, 1f);                          // le cadeau touche le sol…
            play(pop, 1520, 0.35f, 1.15f);                      // …et rebondit
            play(pop, 1680, 0.18f, 1.3f);
            play(chime, 1300, 0.45f, 1f);
        });
    }

    private void play(int sound, long delayMs, float volume, float rate) {
        timer.postDelayed(() -> { if (sounds != null) sounds.play(sound, volume, volume, 1, 0, rate); }, delayMs);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        timer.removeCallbacksAndMessages(null);
        if (sounds != null) { sounds.release(); sounds = null; }
    }

    /** Étoiles à plusieurs profondeurs : les plus proches bougent plus (parallaxe). */
    private void addStars(FrameLayout box, float density, List<Animator> out) {
        Random rnd = new Random(7);
        int w = getResources().getDisplayMetrics().widthPixels;
        int h = getResources().getDisplayMetrics().heightPixels;
        for (int i = 0; i < 18; i++) {
            float depth = 0.3f + rnd.nextFloat() * 0.7f;          // 0.3 = loin, 1 = proche
            int size = (int) ((5 + depth * 11) * density);
            ImageView s = new ImageView(this);
            s.setImageResource(R.drawable.ic_sparkle);
            s.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(size, size, android.view.Gravity.TOP | android.view.Gravity.LEFT);
            lp.leftMargin = rnd.nextInt(Math.max(1, w - size));
            lp.topMargin = rnd.nextInt(Math.max(1, h - size));
            box.addView(s, lp);
            s.setAlpha(0f);
            s.setScaleX(0.2f);
            s.setScaleY(0.2f);

            ObjectAnimator twinkle = ObjectAnimator.ofPropertyValuesHolder(s,
                    PropertyValuesHolder.ofFloat(View.ALPHA, 0f, 0.35f + depth * 0.65f, 0.15f, 0.9f * depth),
                    PropertyValuesHolder.ofFloat(View.SCALE_X, 0.2f, 1f, 0.6f, 1f),
                    PropertyValuesHolder.ofFloat(View.SCALE_Y, 0.2f, 1f, 0.6f, 1f),
                    PropertyValuesHolder.ofFloat(View.ROTATION, 0f, 90f * depth),
                    PropertyValuesHolder.ofFloat(View.TRANSLATION_Y, 0f, -60 * depth * density));
            twinkle.setStartDelay(rnd.nextInt(700));
            twinkle.setDuration(1800 + rnd.nextInt(600));
            twinkle.setInterpolator(new DecelerateInterpolator());
            out.add(twinkle);
        }
    }

    /** Sortie : le logo s'approche de la caméra et s'efface, puis on ouvre le catalogue. */
    private void exit(View logo, float density) {
        if (done) return;
        done = true;
        ValueAnimator.setFrameDelay(10);
        logo.animate()
                .scaleX(1.6f).scaleY(1.6f)
                .rotationY(25f)
                .translationZ(40 * density)
                .alpha(0f)
                .setDuration(380)
                .setInterpolator(new AccelerateInterpolator())
                .withEndAction(() -> {
                    Intent i = new Intent(this, MainActivity.class);
                    if (getIntent() != null && getIntent().getExtras() != null) i.putExtras(getIntent().getExtras());
                    startActivity(i);
                    overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
                    finish();
                })
                .start();
    }
}
