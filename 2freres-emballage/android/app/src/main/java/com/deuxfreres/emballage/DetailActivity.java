package com.deuxfreres.emballage;

import android.content.Intent;
import android.graphics.Paint;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RatingBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.google.android.material.button.MaterialButton;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Locale;

public class DetailActivity extends AppCompatActivity {

    public static final String EXTRA_PRODUCT = "product";
    public static final String EXTRA_TRANSITION = "transition";

    private Product product;
    private final Handler main = new Handler(Looper.getMainLooper());
    private LinearLayout reviewsBox;
    private View reviewsProgress, noReviews;
    private TextView avg, count;
    private RatingBar avgStars;
    private MaterialButton writeReview;
    private JSONObject myReview;
    private boolean writeAfterLogin;

    private final ActivityResultLauncher<Intent> loginLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), r -> {
                if (Session.loggedIn(this)) {
                    loadReviews();
                    if (writeAfterLogin) openReviewDialog();
                }
                writeAfterLogin = false;
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Ui.edgeToEdge(this);
        setContentView(R.layout.activity_detail);

        Product p = (Product) getIntent().getSerializableExtra(EXTRA_PRODUCT);
        if (p == null) { finish(); return; }
        product = p;

        View topBar = findViewById(R.id.topBar);
        View orderBar = findViewById(R.id.orderBar);
        Ui.padTop(topBar);
        Ui.marginBottom(orderBar);
        topBar.getBackground().mutate().setAlpha(0);
        TextView title = findViewById(R.id.title);
        title.setText(p.name);
        title.setAlpha(0f);
        View back = findViewById(R.id.back);
        Ui.press(back);
        back.setOnClickListener(v -> supportFinishAfterTransition());
        View share = findViewById(R.id.share);
        Ui.press(share);
        share.setOnClickListener(v -> shareProduct());

        // Élément partagé : l'image arrive depuis la carte du catalogue
        ImageView image = findViewById(R.id.image);
        String tn = getIntent().getStringExtra(EXTRA_TRANSITION);
        if (tn != null) {
            image.setTransitionName(tn);
            supportPostponeEnterTransition();
            main.postDelayed(this::supportStartPostponedEnterTransition, 450);   // filet de sécurité
        }
        Glide.with(this).load(p.image).placeholder(R.drawable.placeholder).error(R.drawable.placeholder)
                .dontAnimate().fitCenter()
                .listener(new com.bumptech.glide.request.RequestListener<android.graphics.drawable.Drawable>() {
                    @Override public boolean onLoadFailed(com.bumptech.glide.load.engine.GlideException e, Object m,
                            com.bumptech.glide.request.target.Target<android.graphics.drawable.Drawable> t, boolean f) {
                        supportStartPostponedEnterTransition();
                        return false;
                    }
                    @Override public boolean onResourceReady(android.graphics.drawable.Drawable r, Object m,
                            com.bumptech.glide.request.target.Target<android.graphics.drawable.Drawable> t,
                            com.bumptech.glide.load.DataSource d, boolean f) {
                        supportStartPostponedEnterTransition();
                        return false;
                    }
                })
                .into(image);
        ImageView heroBg = findViewById(R.id.heroBg);
        Glide.with(this).load(p.image).centerCrop().into(heroBg);
        if (android.os.Build.VERSION.SDK_INT >= 31) {
            heroBg.setRenderEffect(android.graphics.RenderEffect.createBlurEffect(60f, 60f,
                    android.graphics.Shader.TileMode.CLAMP));
        } else {
            heroBg.setAlpha(0.25f);
        }

        // Parallaxe de l'image + barre du haut qui devient du verre
        View hero = findViewById(R.id.hero);
        AuroraView aurora = findViewById(R.id.aurora);
        androidx.core.widget.NestedScrollView scroll = findViewById(R.id.scroll);
        scroll.setOnScrollChangeListener((androidx.core.widget.NestedScrollView.OnScrollChangeListener)
                (v, x, y, ox, oy) -> {
                    image.setTranslationY(y * 0.45f);
                    heroBg.setTranslationY(y * 0.45f);
                    float k = 1f + Math.min(0.12f, y / 4000f);
                    image.setScaleX(k);
                    image.setScaleY(k);
                    float f = Math.min(1f, y / (float) Math.max(1, hero.getHeight() - topBar.getHeight() * 2));
                    topBar.getBackground().setAlpha((int) (255 * f));
                    title.setAlpha(f);
                    aurora.setScrollShift(y);
                });

        // Entrée : les cartes montent en cascade, la barre de commande glisse depuis le bas
        Ui.rise(findViewById(R.id.content), 180);
        Ui.rise(findViewById(R.id.reviewsCard), 300);
        orderBar.setTranslationY(Ui.dp(this, 140));
        orderBar.animate().translationY(0).setStartDelay(350).setDuration(650)
                .setInterpolator(new android.view.animation.OvershootInterpolator(1.1f)).start();

        ((TextView) findViewById(R.id.name)).setText(p.name);
        ((TextView) findViewById(R.id.price)).setText(Utils.price(p.price));
        ((TextView) findViewById(R.id.ref)).setText(getString(R.string.ref, p.id));
        findViewById(R.id.badge).setVisibility(p.isOffer ? View.VISIBLE : View.GONE);

        TextView old = findViewById(R.id.oldPrice);
        if (p.hasDiscount()) {
            old.setText(Utils.price(p.oldPrice));
            old.setPaintFlags(old.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
        } else {
            old.setVisibility(View.GONE);
        }

        TextView desc = findViewById(R.id.description);
        if (p.description.replaceAll("[\\s.\\-_]", "").isEmpty()) {
            desc.setVisibility(View.GONE);
            findViewById(R.id.descTitle).setVisibility(View.GONE);
        }
        else desc.setText(p.description);

        MaterialButton order = findViewById(R.id.order);
        order.setBackgroundTintList(null);
        order.setOnClickListener(v -> Utils.openWhatsApp(this,
                getString(R.string.wa_product, p.name, p.id, Utils.price(p.price))));

        reviewsBox = findViewById(R.id.reviews);
        reviewsProgress = findViewById(R.id.reviewsProgress);
        noReviews = findViewById(R.id.noReviews);
        avg = findViewById(R.id.avg);
        count = findViewById(R.id.count);
        avgStars = findViewById(R.id.avgStars);
        writeReview = findViewById(R.id.writeReview);
        writeReview.setOnClickListener(v -> {
            if (!Session.loggedIn(this)) {
                writeAfterLogin = true;
                Toast.makeText(this, R.string.login_to_review, Toast.LENGTH_SHORT).show();
                loginLauncher.launch(new Intent(this, LoginActivity.class));
            } else {
                openReviewDialog();
            }
        });
        loadReviews();
    }

    private void shareProduct() {
        String text = product.name + " — " + Utils.price(product.price) + "\n"
                + getString(R.string.share_text, Config.WHATSAPP_NUMBER);
        startActivity(Intent.createChooser(new Intent(Intent.ACTION_SEND).setType("text/plain")
                .putExtra(Intent.EXTRA_TEXT, text), getString(R.string.share_app)));
    }

    /* ===================== Avis ===================== */

    private void loadReviews() {
        reviewsProgress.setVisibility(View.VISIBLE);
        String token = Session.token(this);
        ApiClient.EXEC.execute(() -> {
            try {
                JSONObject r = ApiClient.call("GET", "/api/reviews?product=" + product.id, null, token);
                main.post(() -> { if (!isDestroyed()) showReviews(r); });
            } catch (ApiClient.ApiException e) {
                if (e.status == 401) Session.clear(this);
                main.post(() -> reviewsProgress.setVisibility(View.GONE));
            } catch (Exception e) {
                main.post(() -> reviewsProgress.setVisibility(View.GONE));
            }
        });
    }

    private void showReviews(JSONObject r) {
        reviewsProgress.setVisibility(View.GONE);
        JSONObject sum = r.optJSONObject("summary");
        int n = sum == null ? 0 : sum.optInt("count");
        double a = sum == null ? 0 : sum.optDouble("avg", 0);
        avg.setText(n > 0 ? String.format(Locale.FRANCE, "%.1f", a) : "–");
        avgStars.setRating((float) a);
        count.setText(getResources().getQuantityString(R.plurals.reviews_count, n, n));

        reviewsBox.removeAllViews();
        myReview = null;
        JSONArray list = r.optJSONArray("reviews");
        LayoutInflater inf = LayoutInflater.from(this);
        for (int i = 0; list != null && i < list.length(); i++) {
            JSONObject o = list.optJSONObject(i);
            if (o.optBoolean("mine")) myReview = o;
            View rv = bindReview(inf.inflate(R.layout.item_review, reviewsBox, false), o);
            reviewsBox.addView(rv);
            Ui.rise(rv, 60L * i);
        }
        noReviews.setVisibility(n == 0 ? View.VISIBLE : View.GONE);
        writeReview.setText(myReview != null ? R.string.edit_review : R.string.write_review);
    }

    private View bindReview(View v, JSONObject o) {
        String user = o.optString("username");
        ((TextView) v.findViewById(R.id.avatar)).setText(user.isEmpty() ? "?" : user.substring(0, 1).toUpperCase(Locale.ROOT));
        ((TextView) v.findViewById(R.id.username)).setText(user);
        ((RatingBar) v.findViewById(R.id.stars)).setRating(o.optInt("rating"));
        ((TextView) v.findViewById(R.id.date)).setText(DateUtils.getRelativeTimeSpanString(
                o.optLong("createdAt"), System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS));
        TextView text = v.findViewById(R.id.text);
        String t = o.optString("text");
        text.setText(t);
        text.setVisibility(t.isEmpty() ? View.GONE : View.VISIBLE);
        TextView reply = v.findViewById(R.id.reply);
        String rep = o.optString("reply");
        if (!rep.isEmpty()) {
            reply.setText(getString(R.string.store_reply, rep));
            reply.setVisibility(View.VISIBLE);
        }

        MaterialButton like = v.findViewById(R.id.like);
        renderLike(like, o.optBoolean("likedByMe"), o.optInt("likes"));
        like.setOnClickListener(b -> toggleLike(like, o.optInt("id")));

        if (o.optBoolean("mine")) {
            View edit = v.findViewById(R.id.edit);
            View delete = v.findViewById(R.id.delete);
            edit.setVisibility(View.VISIBLE);
            delete.setVisibility(View.VISIBLE);
            edit.setOnClickListener(b -> openReviewDialog());
            delete.setOnClickListener(b -> new AlertDialog.Builder(this)
                    .setMessage(R.string.delete_review_q)
                    .setPositiveButton(R.string.delete, (d, w) -> deleteReview(o.optInt("id")))
                    .setNegativeButton(android.R.string.cancel, null)
                    .show());
        }
        return v;
    }

    private void renderLike(MaterialButton like, boolean liked, int likes) {
        int color = getResources().getColor(liked ? R.color.brand : R.color.text_secondary, null);
        like.setText(likes > 0 ? String.valueOf(likes) : getString(R.string.like));
        like.setTextColor(color);
        like.setIconTint(android.content.res.ColorStateList.valueOf(color));
    }

    private void toggleLike(MaterialButton like, int reviewId) {
        if (!Session.loggedIn(this)) {
            Toast.makeText(this, R.string.login_to_like, Toast.LENGTH_SHORT).show();
            loginLauncher.launch(new Intent(this, LoginActivity.class));
            return;
        }
        like.setEnabled(false);
        String token = Session.token(this);
        ApiClient.EXEC.execute(() -> {
            try {
                JSONObject r = ApiClient.call("POST", "/api/reviews/" + reviewId + "/like", new JSONObject(), token);
                main.post(() -> { like.setEnabled(true); renderLike(like, r.optBoolean("liked"), r.optInt("likes")); });
            } catch (Exception e) {
                main.post(() -> { like.setEnabled(true); onApiError(e); });
            }
        });
    }

    private void openReviewDialog() {
        View form = LayoutInflater.from(this).inflate(R.layout.dialog_review, null);
        RatingBar rating = form.findViewById(R.id.rating);
        EditText text = form.findViewById(R.id.text);
        if (myReview != null) {
            rating.setRating(myReview.optInt("rating"));
            text.setText(myReview.optString("text"));
        } else {
            rating.setRating(5);
        }
        AlertDialog dlg = new AlertDialog.Builder(this)
                .setTitle(getString(R.string.review_as, Session.username(this)))
                .setView(form)
                .setPositiveButton(R.string.publish, null)
                .setNegativeButton(android.R.string.cancel, null)
                .create();
        dlg.setOnShowListener(d -> dlg.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(b -> {
            int stars = Math.round(rating.getRating());
            if (stars < 1) {
                Toast.makeText(this, R.string.choose_stars, Toast.LENGTH_SHORT).show();
                return;
            }
            dlg.dismiss();
            postReview(stars, text.getText().toString().trim());
        }));
        dlg.show();
    }

    private void postReview(int stars, String text) {
        reviewsProgress.setVisibility(View.VISIBLE);
        String token = Session.token(this);
        ApiClient.EXEC.execute(() -> {
            try {
                JSONObject body = new JSONObject()
                        .put("productId", product.id)
                        .put("productName", product.name)
                        .put("rating", stars)
                        .put("text", text);
                ApiClient.call("POST", "/api/reviews", body, token);
                main.post(() -> {
                    Toast.makeText(this, R.string.review_saved, Toast.LENGTH_SHORT).show();
                    loadReviews();
                });
            } catch (Exception e) {
                main.post(() -> { reviewsProgress.setVisibility(View.GONE); onApiError(e); });
            }
        });
    }

    private void deleteReview(int id) {
        String token = Session.token(this);
        ApiClient.EXEC.execute(() -> {
            try {
                ApiClient.call("DELETE", "/api/reviews/" + id, null, token);
                main.post(this::loadReviews);
            } catch (Exception e) {
                main.post(() -> onApiError(e));
            }
        });
    }

    private void onApiError(Exception e) {
        int msg = R.string.offline;
        if (e instanceof ApiClient.ApiException) {
            ApiClient.ApiException a = (ApiClient.ApiException) e;
            if (a.status == 401) { Session.clear(this); msg = R.string.session_expired; }
            else if ("banned".equals(a.code)) msg = R.string.err_banned;
            else msg = R.string.err_generic;
        }
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
    }
}
