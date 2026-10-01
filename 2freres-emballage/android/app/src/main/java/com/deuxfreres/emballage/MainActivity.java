package com.deuxfreres.emballage;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import com.google.android.material.snackbar.Snackbar;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class MainActivity extends AppCompatActivity {

    private static final String ALL = "__all";
    private static final String PROMO = "__promo";

    private final List<Product> all = new ArrayList<>();
    private String filter = ALL;
    private String query = "";

    private ProductRepository repo;
    private ProductAdapter adapter;
    private SwipeRefreshLayout swipe;
    private ChipGroup chips;
    private TextView empty;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setTitle(Config.STORE_NAME);

        swipe = findViewById(R.id.swipe);
        chips = findViewById(R.id.chips);
        empty = findViewById(R.id.empty);

        RecyclerView list = findViewById(R.id.list);
        int span = getResources().getConfiguration().screenWidthDp >= 600 ? 3 : 2;
        list.setLayoutManager(new GridLayoutManager(this, span));
        adapter = new ProductAdapter(p -> {
            Intent i = new Intent(this, DetailActivity.class);
            i.putExtra(DetailActivity.EXTRA_PRODUCT, p);
            startActivity(i);
        });
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
        swipe.setOnRefreshListener(this::load);

        repo = new ProductRepository(this);
        all.addAll(repo.cached());
        swipe.setRefreshing(true);
        buildChips();
        apply();
        load();
    }

    private void load() {
        repo.load((products, error) -> {
            if (isDestroyed()) return;
            swipe.setRefreshing(false);
            all.clear();
            all.addAll(products);
            buildChips();
            apply();
            if (error != null) {
                Snackbar.make(swipe, R.string.offline, Snackbar.LENGTH_LONG)
                        .setAction(R.string.retry, v -> { swipe.setRefreshing(true); load(); })
                        .show();
            }
        });
    }

    private void buildChips() {
        Set<String> cats = new LinkedHashSet<>();
        boolean hasPromo = false;
        for (Product p : all) {
            if (!p.category.isEmpty()) cats.add(p.category);
            if (p.isOffer) hasPromo = true;
        }
        boolean valid = filter.equals(ALL) || (filter.equals(PROMO) && hasPromo) || cats.contains(filter);
        if (!valid) filter = ALL;

        chips.removeAllViews();
        addChip(ALL, getString(R.string.all));
        if (hasPromo) addChip(PROMO, getString(R.string.promos));
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
            if (checked) { filter = key; apply(); }
        });
    }

    private void apply() {
        List<Product> out = new ArrayList<>();
        for (Product p : all) {
            if (filter.equals(PROMO) && !p.isOffer) continue;
            if (!filter.equals(ALL) && !filter.equals(PROMO) && !filter.equals(p.category)) continue;
            if (!query.isEmpty() && !p.searchText().contains(query)) continue;
            out.add(p);
        }
        adapter.submit(out);
        empty.setText(all.isEmpty() ? R.string.empty_catalog : R.string.no_results);
        empty.setVisibility(out.isEmpty() && !swipe.isRefreshing() ? View.VISIBLE : View.GONE);
    }
}
