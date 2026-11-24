package com.deuxfreres.emballage;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.transition.TransitionManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityOptionsCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.snackbar.Snackbar;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class MainActivity extends AppCompatActivity {

    private static final String ALL = "__all";
    private static final String PROMO = "__promo";

    private static final int TAB_HOME = 0, TAB_PROMOS = 1, TAB_MESSAGES = 2, TAB_ALERTS = 3, TAB_PROFILE = 4;
    private static final int[] TAB_ICONS = {R.drawable.ic_home, R.drawable.ic_fire, R.drawable.ic_chat,
            R.drawable.ic_bell, R.drawable.ic_person};
    private static final int[] TAB_LABELS = {R.string.nav_home, R.string.nav_promos, R.string.nav_messages,
            R.string.nav_alerts, R.string.nav_profile};

    private Catalog catalog = new Catalog();
    private String filter = ALL;
    private String query = "";

    private ProductRepository repo;
    private ProductAdapter adapter;
    private SwipeRefreshLayout swipe;
    private RecyclerView list;
    private LinearLayout chips;
    private HorizontalScrollView chipsScroll;
    private TextView empty, greeting, fabLabel;
    private View emptyBox, header, titleRow, bottomBar, navIndicator, fab;
    private AuroraView aurora;
    private final List<View> tabs = new ArrayList<>();
    private int selectedTab = -1;
    private boolean barHidden;
    private boolean firstLayoutDone;

    private final ActivityResultLauncher<String> askNotifications =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (granted) askBackgroundOnce();
                else showNotificationsOff();
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Ui.edgeToEdge(this);
        setContentView(R.layout.activity_main);

        aurora = findViewById(R.id.aurora);
        header = findViewById(R.id.header);
        titleRow = findViewById(R.id.titleRow);
        greeting = findViewById(R.id.greeting);
        swipe = findViewById(R.id.swipe);
        chips = findViewById(R.id.chips);
        chipsScroll = findViewById(R.id.chipsScroll);
        empty = findViewById(R.id.empty);
        emptyBox = findViewById(R.id.emptyBox);
        bottomBar = findViewById(R.id.bottomBar);
        navIndicator = findViewById(R.id.navIndicator);
        fab = findViewById(R.id.fab);
        fabLabel = findViewById(R.id.fabLabel);

        Ui.padTop(header);
        Ui.marginBottom(bottomBar);
        Ui.marginBottom(fab);
        header.getBackground().mutate().setAlpha(0);

        list = findViewById(R.id.list);
        int span = getResources().getConfiguration().screenWidthDp >= 600 ? 3 : 2;
        list.setLayoutManager(new GridLayoutManager(this, span));
        adapter = new ProductAdapter(this::openProduct);
        list.setAdapter(adapter);
        list.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override public void onScrolled(RecyclerView rv, int dx, int dy) { onListScrolled(dy); }
        });
        header.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
            int h = b - t;
            if (list.getPaddingTop() != h) {
                list.setPadding(list.getPaddingLeft(), h, list.getPaddingRight(), list.getPaddingBottom());
                swipe.setProgressViewOffset(false, h - Ui.dp(this, 24), h + Ui.dp(this, 36));
                if (!firstLayoutDone) { firstLayoutDone = true; list.scrollToPosition(0); }
            }
        });

        EditText search = findViewById(R.id.search);
        View clear = findViewById(R.id.clearSearch);
        clear.setOnClickListener(v -> search.setText(""));
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void afterTextChanged(Editable s) {
                query = s.toString().trim().toLowerCase(Locale.ROOT);
                if ((clear.getVisibility() == View.VISIBLE) != (s.length() > 0)) {
                    clear.setVisibility(s.length() > 0 ? View.VISIBLE : View.GONE);
                    if (s.length() > 0) Ui.bounce(clear);
                }
                apply(false);
            }
        });

        Ui.press(fab);
        fab.setOnClickListener(v -> Utils.openWhatsApp(this, getString(R.string.wa_general)));
        View call = findViewById(R.id.call);
        Ui.press(call);
        call.setOnClickListener(v -> Utils.call(this));

        swipe.setColorSchemeColors(0xFFD6BC8A);
        swipe.setProgressBackgroundColorSchemeColor(0xFF1B1917);
        swipe.setOnRefreshListener(() -> { load(); loadRatings(); });

        buildBottomBar();
        introAnimation();

        Notifier.createChannel(this);
        Notifier.schedule(this);
        askNotificationPermission();

        repo = new ProductRepository(this);
        catalog = repo.cached();
        swipe.setRefreshing(true);
        refreshUi(true);
        load();
        if (!handleOpenExtra(getIntent())) Updater.checkOnLaunch(this);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleOpenExtra(intent);
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateBell();
        updateGreeting();
        loadRatings();
        selectTab(filter.equals(PROMO) ? TAB_PROMOS : TAB_HOME, true);
        Updater.resumePendingInstall(this);
    }

    /* ======================= Animations d'entrée ======================= */

    private void introAnimation() {
        header.setAlpha(0f);
        header.setTranslationY(-Ui.dp(this, 12));
        header.animate().alpha(1f).translationY(0f).setDuration(650).setInterpolator(Ui.SMOOTH).start();
        bottomBar.setTranslationY(Ui.dp(this, 90));
        bottomBar.animate().translationY(0f).setStartDelay(220).setDuration(700)
                .setInterpolator(Ui.SMOOTH).start();
        fab.setAlpha(0f);
        fab.animate().alpha(1f).setStartDelay(500).setDuration(600)
                .setInterpolator(Ui.SMOOTH).start();
        Ui.bounce(findViewById(R.id.logo));
    }

    /** En-tête qui se replie, fond qui s'opacifie, barre du bas qui se cache au défilement. */
    private void onListScrolled(int dy) {
        int offset = list.computeVerticalScrollOffset();
        int collapse = titleRow.getHeight() + Ui.dp(this, 4);
        float frac = collapse == 0 ? 0 : Math.min(1f, offset / (float) collapse);
        header.setTranslationY(-collapse * frac);
        titleRow.setAlpha(1f - frac);
        Drawable bg = header.getBackground();
        bg.setAlpha((int) (255 * Math.min(1f, offset / (float) Ui.dp(this, 60))));
        aurora.setScrollShift(offset);

        if (dy > 8 && !barHidden && offset > Ui.dp(this, 120)) {
            barHidden = true;
            bottomBar.animate().translationY(bottomBar.getHeight() + Ui.dp(this, 40)).setDuration(320)
                    .setInterpolator(Ui.SMOOTH).start();
            fab.animate().translationY(bottomBar.getHeight()).setDuration(320).setInterpolator(Ui.SMOOTH).start();
            TransitionManager.beginDelayedTransition((ViewGroup) fab);
            fabLabel.setVisibility(View.GONE);
        } else if ((dy < -8 || offset == 0) && barHidden) {
            barHidden = false;
            bottomBar.animate().translationY(0).setDuration(420)
                    .setInterpolator(Ui.SMOOTH).start();
            fab.animate().translationY(0).setDuration(420).setInterpolator(Ui.SMOOTH).start();
            TransitionManager.beginDelayedTransition((ViewGroup) fab);
            fabLabel.setVisibility(View.VISIBLE);
        }
    }

    /* ======================= Barre du bas ======================= */

    private void buildBottomBar() {
        bottomBar.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        LinearLayout items = findViewById(R.id.navItems);
        LayoutInflater inf = LayoutInflater.from(this);
        for (int i = 0; i < TAB_ICONS.length; i++) {
            View t = inf.inflate(R.layout.item_nav, items, false);
            ((ImageView) t.findViewById(R.id.navIcon)).setImageResource(TAB_ICONS[i]);
            ((TextView) t.findViewById(R.id.navLabel)).setText(TAB_LABELS[i]);
            t.setContentDescription(getString(TAB_LABELS[i]));
            int index = i;
            t.setOnClickListener(v -> onTab(index));
            items.addView(t);
            tabs.add(t);
        }
        items.post(() -> {
            ViewGroup.LayoutParams lp = navIndicator.getLayoutParams();
            lp.width = tabs.get(0).getWidth();
            navIndicator.setLayoutParams(lp);
            int sel = selectedTab < 0 ? TAB_HOME : selectedTab;
            selectedTab = -1;
            selectTab(sel, false);
        });
    }

    private void selectTab(int index, boolean animate) {
        if (tabs.isEmpty() || index == selectedTab) return;
        int prev = selectedTab;
        selectedTab = index;
        View tab = tabs.get(index);
        float x = tab.getLeft();
        if (tab.getWidth() == 0) return;   // pas encore mesuré : buildBottomBar s'en chargera
        if (animate && prev >= 0) {
            navIndicator.animate().translationX(x).setDuration(380)
                    .setInterpolator(Ui.SMOOTH).start();
        } else {
            navIndicator.setTranslationX(x);
        }
        for (int i = 0; i < tabs.size(); i++) {
            View t = tabs.get(i);
            boolean on = i == index;
            View icon = t.findViewById(R.id.navIcon);
            TextView label = t.findViewById(R.id.navLabel);
            int color = on ? 0xFFD6BC8A : 0x99F5F1EA;
            ((ImageView) icon).setImageTintList(android.content.res.ColorStateList.valueOf(color));
            label.setTextColor(color);
        }
    }

    private void onTab(int index) {
        switch (index) {
            case TAB_HOME:
                if (filter.equals(ALL) && selectedTab == TAB_HOME) list.smoothScrollToPosition(0);
                setFilter(ALL);
                selectTab(TAB_HOME, true);
                break;
            case TAB_PROMOS:
                if (!hasPromo()) {
                    Snackbar.make(swipe, R.string.no_promos, Snackbar.LENGTH_SHORT).show();
                    selectTab(selectedTab, true);
                    return;
                }
                setFilter(PROMO);
                selectTab(TAB_PROMOS, true);
                break;
            case TAB_MESSAGES:
                selectTab(TAB_MESSAGES, true);
                bottomBar.postDelayed(() -> startActivity(new Intent(this, SupportActivity.class)), 160);
                break;
            case TAB_ALERTS:
                selectTab(TAB_ALERTS, true);
                bottomBar.postDelayed(this::openNotifications, 160);
                break;
            case TAB_PROFILE:
                selectTab(TAB_PROFILE, true);
                showProfile();
                break;
        }
    }

    /* ======================= Profil (feuille de verre) ======================= */

    private void showProfile() {
        View v = LayoutInflater.from(this).inflate(R.layout.sheet_profile, null);
        BottomSheetDialog d = new BottomSheetDialog(this);
        d.setContentView(v);
        d.getBehavior().setState(BottomSheetBehavior.STATE_EXPANDED);
        d.getBehavior().setSkipCollapsed(true);
        d.setOnDismissListener(x -> selectTab(filter.equals(PROMO) ? TAB_PROMOS : TAB_HOME, true));

        boolean in = Session.loggedIn(this);
        String user = Session.username(this);
        TextView avatar = v.findViewById(R.id.profAvatar);
        if (in && user != null && !user.isEmpty()) {
            avatar.setText(user.substring(0, 1).toUpperCase(Locale.ROOT));
        } else {
            avatar.setText("");
            avatar.setCompoundDrawablesRelativeWithIntrinsicBounds(0, R.drawable.ic_person, 0, 0);
            avatar.setCompoundDrawableTintList(android.content.res.ColorStateList.valueOf(0xFFD6BC8A));
            avatar.setPadding(0, Ui.dp(this, 17), 0, 0);
        }
        ((TextView) v.findViewById(R.id.profName)).setText(in ? user : getString(R.string.guest));
        ((TextView) v.findViewById(R.id.profHint)).setText(in ? getString(R.string.profile_hint_in)
                : getString(R.string.profile_hint_guest));

        LinearLayout rows = v.findViewById(R.id.profRows);
        if (in) {
            addRow(rows, R.drawable.ic_logout, getString(R.string.logout), null, () -> { d.dismiss(); logout(); });
        } else {
            addRow(rows, R.drawable.ic_person, getString(R.string.login), null, () -> {
                d.dismiss();
                startActivity(new Intent(this, LoginActivity.class));
            });
        }
        addRow(rows, R.drawable.ic_chat, getString(R.string.support), null, () -> {
            d.dismiss();
            startActivity(new Intent(this, SupportActivity.class));
        });
        addRow(rows, R.drawable.ic_chat, getString(R.string.whatsapp), null,
                () -> Utils.openWhatsApp(this, getString(R.string.wa_general)));
        addRow(rows, R.drawable.ic_phone, getString(R.string.call), Config.PHONE_NUMBER, () -> Utils.call(this));
        addRow(rows, R.drawable.ic_share, getString(R.string.share_app), null, () -> Utils.share(this));
        addRow(rows, R.drawable.ic_refresh, getString(R.string.update_check),
                getString(R.string.version_label, BuildConfig.VERSION_NAME), () -> {
                    d.dismiss();
                    Updater.checkManually(this);
                });
        addRow(rows, R.drawable.ic_info, getString(R.string.about), null, () -> new AlertDialog.Builder(this)
                .setTitle(Config.STORE_NAME)
                .setMessage(getString(R.string.about_text, BuildConfig.VERSION_NAME))
                .setPositiveButton(android.R.string.ok, null)
                .show());

        d.show();
        Ui.stagger(rows, 140);
        Ui.bounce(v.findViewById(R.id.profAvatar));
    }

    private void addRow(LinearLayout rows, int icon, String title, String sub, Runnable action) {
        View r = LayoutInflater.from(this).inflate(R.layout.item_sheet_row, rows, false);
        ((ImageView) r.findViewById(R.id.rowIcon)).setImageResource(icon);
        ((TextView) r.findViewById(R.id.rowTitle)).setText(title);
        TextView s = r.findViewById(R.id.rowSub);
        if (sub == null) s.setVisibility(View.GONE);
        else s.setText(sub);
        r.setOnClickListener(v -> action.run());
        rows.addView(r);
    }

    private void logout() {
        String token = Session.token(this);
        Session.clear(this);
        com.google.firebase.auth.FirebaseAuth.getInstance().signOut();
        androidx.credentials.CredentialManager.create(this).clearCredentialStateAsync(
                new androidx.credentials.ClearCredentialStateRequest(), null,
                androidx.core.content.ContextCompat.getMainExecutor(this),
                new androidx.credentials.CredentialManagerCallback<Void, androidx.credentials.exceptions.ClearCredentialException>() {
                    @Override public void onResult(Void v) {}
                    @Override public void onError(@androidx.annotation.NonNull androidx.credentials.exceptions.ClearCredentialException e) {}
                });
        updateGreeting();
        Snackbar.make(swipe, R.string.logged_out, Snackbar.LENGTH_SHORT).show();
        ApiClient.EXEC.execute(() -> {
            try { ApiClient.call("POST", "/api/logout", new JSONObject(), token); } catch (Exception ignored) {}
        });
    }

    private void updateGreeting() {
        String u = Session.username(this);
        greeting.setText(Session.loggedIn(this) && u != null ? getString(R.string.hello_user, u)
                : getString(R.string.greeting));
    }

    /* ======================= Données ======================= */

    /** Notes moyennes affichées sous chaque produit. */
    private void loadRatings() {
        ApiClient.EXEC.execute(() -> {
            try {
                JSONObject r = ApiClient.call("GET", "/api/ratings", null, null).getJSONObject("ratings");
                Map<Integer, double[]> map = new HashMap<>();
                for (Iterator<String> it = r.keys(); it.hasNext(); ) {
                    String k = it.next();
                    JSONObject o = r.getJSONObject(k);
                    map.put(Integer.parseInt(k), new double[]{o.optDouble("avg"), o.optInt("count")});
                }
                runOnUiThread(() -> { if (!isDestroyed()) adapter.setRatings(map); });
            } catch (Exception ignored) {
            }
        });
    }

    /** Ouverture depuis une notification système. Retourne vrai si un écran a été ouvert. */
    private boolean handleOpenExtra(Intent intent) {
        String open = intent == null ? null : intent.getStringExtra(Notifier.EXTRA_OPEN);
        if (open == null) return false;
        intent.removeExtra(Notifier.EXTRA_OPEN);
        if ("update".equals(open)) {
            Updater.checkManually(this);
            return true;
        }
        if ("support".equals(open)) {
            startActivity(new Intent(this, SupportActivity.class));
            return true;
        }
        if ("notifications".equals(open)) {
            openNotifications();
            return true;
        }
        try {
            Product p = catalog.product(Integer.parseInt(open));
            if (p != null) openProduct(p, null);
            else openNotifications();
        } catch (NumberFormatException e) {
            openNotifications();
        }
        return true;
    }

    private void askNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this,
                Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS);
        } else if (!Notifier.enabled(this)) {
            showNotificationsOff();
        } else {
            askBackgroundOnce();
        }
    }

    private void showNotificationsOff() {
        Snackbar.make(swipe, R.string.notif_off, Snackbar.LENGTH_LONG)
                .setAnchorView(bottomBar)
                .setAction(R.string.enable, v -> Notifier.openSettings(this))
                .show();
    }

    /**
     * Certains téléphones (Xiaomi, Samsung, Oppo…) coupent les tâches en arrière-plan.
     * On demande une seule fois de laisser l'application vérifier les nouveautés quand elle est fermée.
     */
    private void askBackgroundOnce() {
        android.os.PowerManager pm = (android.os.PowerManager) getSystemService(POWER_SERVICE);
        if (pm == null || pm.isIgnoringBatteryOptimizations(getPackageName())) return;
        android.content.SharedPreferences prefs = getSharedPreferences("notifications", MODE_PRIVATE);
        if (prefs.getBoolean("asked_battery", false)) return;
        prefs.edit().putBoolean("asked_battery", true).apply();
        new AlertDialog.Builder(this)
                .setTitle(R.string.battery_title)
                .setMessage(R.string.battery_text)
                .setPositiveButton(R.string.allow, (d, w) -> {
                    try {
                        startActivity(new Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                                android.net.Uri.parse("package:" + getPackageName())));
                    } catch (Exception e) {
                        startActivity(new Intent(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
                    }
                })
                .setNegativeButton(R.string.later, null)
                .show();
    }

    private void load() {
        repo.load((c, error) -> {
            if (isDestroyed()) return;
            swipe.setRefreshing(false);
            catalog = c;
            if (error == null) Notifier.markNotified(this, c.maxNotificationId());
            refreshUi(true);
            if (error != null) {
                Snackbar.make(swipe, R.string.offline, Snackbar.LENGTH_LONG)
                        .setAnchorView(bottomBar)
                        .setAction(R.string.retry, v -> { swipe.setRefreshing(true); load(); })
                        .show();
            }
        });
    }

    private void refreshUi(boolean animate) {
        buildChips();
        apply(animate);
        updateBell();
    }

    private void updateBell() {
        if (tabs.size() <= TAB_ALERTS) return;
        TextView badge = tabs.get(TAB_ALERTS).findViewById(R.id.navBadge);
        int n = Notifier.unread(this, catalog);
        boolean was = badge.getVisibility() == View.VISIBLE;
        badge.setVisibility(n > 0 ? View.VISIBLE : View.GONE);
        badge.setText(n > 9 ? "9+" : String.valueOf(n));
        if (n > 0 && !was) Ui.bounce(badge);
    }

    private void openNotifications() {
        startActivity(new Intent(this, NotificationsActivity.class));
    }

    /** Ouvre la fiche produit : l'image « vole » de la carte vers la page (élément partagé). */
    private void openProduct(Product p, View image) {
        Intent i = new Intent(this, DetailActivity.class);
        i.putExtra(DetailActivity.EXTRA_PRODUCT, p);
        if (image != null && image.getTransitionName() != null) {
            i.putExtra(DetailActivity.EXTRA_TRANSITION, image.getTransitionName());
            startActivity(i, ActivityOptionsCompat.makeSceneTransitionAnimation(this, image,
                    image.getTransitionName()).toBundle());
        } else {
            startActivity(i);
        }
    }

    /* ======================= Filtres ======================= */

    private void setFilter(String f) {
        if (f.equals(filter)) return;
        filter = f;
        buildChips();
        apply(true);
    }

    private Set<String> categories() {
        Set<String> cats = new LinkedHashSet<>();
        for (Product p : catalog.products) if (!p.category.isEmpty()) cats.add(p.category);
        return cats;
    }

    private boolean hasPromo() {
        for (Product p : catalog.products) if (p.isOffer) return true;
        return false;
    }

    private void buildChips() {
        Set<String> cats = categories();
        boolean promo = hasPromo();
        boolean valid = filter.equals(ALL) || (filter.equals(PROMO) && promo) || cats.contains(filter);
        if (!valid) filter = ALL;

        chips.removeAllViews();
        addChip(ALL, getString(R.string.all));
        if (promo) addChip(PROMO, getString(R.string.promos));
        for (String c : cats) addChip(c, c);
    }

    private void addChip(String key, String label) {
        TextView c = (TextView) LayoutInflater.from(this).inflate(R.layout.item_chip, chips, false);
        c.setText(label);
        c.setTag(key);
        boolean on = key.equals(filter);
        c.setSelected(on);
        c.setTypeface(null, on ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
        c.setOnClickListener(v -> {
            if (key.equals(filter)) return;
            filter = key;
            for (int i = 0; i < chips.getChildCount(); i++) {
                TextView o = (TextView) chips.getChildAt(i);
                boolean sel = key.equals(o.getTag());
                o.setSelected(sel);
                o.setTypeface(null, sel ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
            }
            chipsScroll.smoothScrollTo(Math.max(0, v.getLeft() - Ui.dp(this, 40)), 0);
            selectTab(filter.equals(PROMO) ? TAB_PROMOS : TAB_HOME, true);
            apply(true);
        });
        chips.addView(c);
    }

    private void apply(boolean animate) {
        List<Product> out = new ArrayList<>();
        for (Product p : catalog.products) {
            if (filter.equals(PROMO) && !p.isOffer) continue;
            if (!filter.equals(ALL) && !filter.equals(PROMO) && !filter.equals(p.category)) continue;
            if (!query.isEmpty() && !p.searchText().contains(query)) continue;
            out.add(p);
        }
        adapter.submit(out);
        if (animate) {
            list.scrollToPosition(0);
            list.scheduleLayoutAnimation();
        }
        empty.setText(catalog.products.isEmpty() ? R.string.empty_catalog : R.string.no_results);
        boolean show = out.isEmpty() && !swipe.isRefreshing();
        if (show && emptyBox.getVisibility() != View.VISIBLE) Ui.rise(emptyBox, 0);
        emptyBox.setVisibility(show ? View.VISIBLE : View.GONE);
    }
}
