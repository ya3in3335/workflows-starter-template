package com.deuxfreres.emballage;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Menu;
import android.view.MenuItem;
import android.view.SubMenu;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import com.google.android.material.navigation.NavigationView;
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

    private Catalog catalog = new Catalog();
    private String filter = ALL;
    private String query = "";

    private ProductRepository repo;
    private ProductAdapter adapter;
    private SwipeRefreshLayout swipe;
    private ChipGroup chips;
    private TextView empty;
    private DrawerLayout drawer;
    private NavigationView nav;
    private TextView bellCount;

    private final ActivityResultLauncher<String> askNotifications =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (granted) askBackgroundOnce();
                else showNotificationsOff();
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        drawer = findViewById(R.id.drawer);
        nav = findViewById(R.id.nav);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setTitle(Config.STORE_NAME);
        toolbar.setNavigationOnClickListener(v -> drawer.openDrawer(GravityCompat.START));
        toolbar.inflateMenu(R.menu.main);
        View bell = toolbar.getMenu().findItem(R.id.action_notifications).getActionView();
        bellCount = bell.findViewById(R.id.count);
        bell.setOnClickListener(v -> openNotifications());

        nav.setNavigationItemSelectedListener(this::onNavItem);
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override public void handleOnBackPressed() {
                if (drawer.isDrawerOpen(GravityCompat.START)) {
                    drawer.closeDrawer(GravityCompat.START);
                } else {
                    setEnabled(false);
                    getOnBackPressedDispatcher().onBackPressed();
                }
            }
        });

        swipe = findViewById(R.id.swipe);
        chips = findViewById(R.id.chips);
        empty = findViewById(R.id.empty);

        RecyclerView list = findViewById(R.id.list);
        int span = getResources().getConfiguration().screenWidthDp >= 600 ? 3 : 2;
        list.setLayoutManager(new GridLayoutManager(this, span));
        adapter = new ProductAdapter(this::openProduct);
        list.setAdapter(adapter);

        EditText search = findViewById(R.id.search);
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void afterTextChanged(Editable s) {
                query = s.toString().trim().toLowerCase(Locale.ROOT);
                apply();
            }
        });

        ExtendedFloatingActionButton fab = findViewById(R.id.fab);
        fab.setOnClickListener(v -> Utils.openWhatsApp(this, getString(R.string.wa_general)));

        swipe.setColorSchemeResources(R.color.brand);
        swipe.setOnRefreshListener(() -> { load(); loadRatings(); });

        Notifier.createChannel(this);
        Notifier.schedule(this);
        askNotificationPermission();

        repo = new ProductRepository(this);
        catalog = repo.cached();
        swipe.setRefreshing(true);
        refreshUi();
        load();
        handleOpenExtra(getIntent());
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
        updateAccount();
        loadRatings();
    }

    private void updateAccount() {
        boolean in = Session.loggedIn(this);
        MenuItem acc = nav.getMenu().findItem(R.id.nav_account);
        acc.setTitle(in ? getString(R.string.hello_user, Session.username(this)) : getString(R.string.login));
        nav.getMenu().findItem(R.id.nav_logout).setVisible(in);
    }

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

    /** Ouverture depuis une notification système. */
    private void handleOpenExtra(Intent intent) {
        String open = intent == null ? null : intent.getStringExtra(Notifier.EXTRA_OPEN);
        if (open == null) return;
        intent.removeExtra(Notifier.EXTRA_OPEN);
        if ("notifications".equals(open)) {
            openNotifications();
            return;
        }
        try {
            Product p = catalog.product(Integer.parseInt(open));
            if (p != null) openProduct(p);
            else openNotifications();
        } catch (NumberFormatException e) {
            openNotifications();
        }
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
            refreshUi();
            if (error != null) {
                Snackbar.make(swipe, R.string.offline, Snackbar.LENGTH_LONG)
                        .setAction(R.string.retry, v -> { swipe.setRefreshing(true); load(); })
                        .show();
            }
        });
    }

    private void refreshUi() {
        buildChips();
        buildDrawerCategories();
        apply();
        updateBell();
    }

    private void updateBell() {
        if (bellCount == null) return;
        int n = Notifier.unread(this, catalog);
        bellCount.setVisibility(n > 0 ? View.VISIBLE : View.GONE);
        bellCount.setText(n > 9 ? "9+" : String.valueOf(n));
    }

    private void openNotifications() {
        startActivity(new Intent(this, NotificationsActivity.class));
    }

    private void openProduct(Product p) {
        Intent i = new Intent(this, DetailActivity.class);
        i.putExtra(DetailActivity.EXTRA_PRODUCT, p);
        startActivity(i);
    }

    /* ---------------- Menu latéral ---------------- */

    private boolean onNavItem(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.nav_home) {
            setFilter(ALL);
        } else if (id == R.id.nav_promos) {
            setFilter(PROMO);
        } else if (id == R.id.nav_notifications) {
            openNotifications();
        } else if (id == R.id.nav_account) {
            if (!Session.loggedIn(this)) startActivity(new Intent(this, LoginActivity.class));
        } else if (id == R.id.nav_logout) {
            String token = Session.token(this);
            Session.clear(this);
            updateAccount();
            Snackbar.make(swipe, R.string.logged_out, Snackbar.LENGTH_SHORT).show();
            ApiClient.EXEC.execute(() -> {
                try { ApiClient.call("POST", "/api/logout", new JSONObject(), token); } catch (Exception ignored) {}
            });
        } else if (id == R.id.nav_whatsapp) {
            Utils.openWhatsApp(this, getString(R.string.wa_general));
        } else if (id == R.id.nav_call) {
            Utils.call(this);
        } else if (id == R.id.nav_share) {
            Utils.share(this);
        } else if (id == R.id.nav_about) {
            new AlertDialog.Builder(this)
                    .setTitle(Config.STORE_NAME)
                    .setMessage(getString(R.string.about_text, BuildConfig.VERSION_NAME))
                    .setPositiveButton(android.R.string.ok, null)
                    .show();
        } else if (item.getGroupId() == R.id.nav_categories) {
            setFilter(item.getTitle().toString());
        }
        drawer.closeDrawer(GravityCompat.START);
        return false;
    }

    private void buildDrawerCategories() {
        MenuItem holder = nav.getMenu().findItem(R.id.nav_categories);
        SubMenu sub = holder.getSubMenu();
        if (sub == null) return;
        sub.clear();
        for (String c : categories()) {
            sub.add(R.id.nav_categories, Menu.NONE, Menu.NONE, c).setIcon(R.drawable.ic_tag);
        }
        holder.setVisible(sub.size() > 0);
        nav.getMenu().findItem(R.id.nav_promos).setVisible(hasPromo());
        nav.getMenu().findItem(R.id.nav_home).setChecked(filter.equals(ALL));
        nav.getMenu().findItem(R.id.nav_promos).setChecked(filter.equals(PROMO));
    }

    private void setFilter(String f) {
        filter = f;
        buildChips();
        buildDrawerCategories();
        apply();
    }

    /* ---------------- Filtres ---------------- */

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
        Chip c = new Chip(this);
        c.setId(View.generateViewId());
        c.setText(label);
        c.setCheckable(true);
        c.setCheckedIconVisible(false);
        chips.addView(c);
        c.setChecked(key.equals(filter));
        c.setOnCheckedChangeListener((b, checked) -> {
            if (checked && !key.equals(filter)) {
                filter = key;
                buildDrawerCategories();
                apply();
            }
        });
    }

    private void apply() {
        List<Product> out = new ArrayList<>();
        for (Product p : catalog.products) {
            if (filter.equals(PROMO) && !p.isOffer) continue;
            if (!filter.equals(ALL) && !filter.equals(PROMO) && !filter.equals(p.category)) continue;
            if (!query.isEmpty() && !p.searchText().contains(query)) continue;
            out.add(p);
        }
        adapter.submit(out);
        empty.setText(catalog.products.isEmpty() ? R.string.empty_catalog : R.string.no_results);
        empty.setVisibility(out.isEmpty() && !swipe.isRefreshing() ? View.VISIBLE : View.GONE);
    }
}
