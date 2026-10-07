package com.deuxfreres.emballage.admin;

import android.content.Intent;
import android.view.View;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** Tous les produits (même cachés). Toucher = modifier, + = ajouter. */
public class ProductsFragment extends ListFragment {

    @Override protected void setup() {
        toolbar.setTitle(R.string.tab_products);
        search.setVisibility(View.VISIBLE);
        search.setHint(R.string.search_products);
        fab.setVisibility(View.VISIBLE);
        fab.setText(R.string.quick_add);
        fab.setOnClickListener(v -> startActivity(new Intent(getContext(), ProductEditActivity.class)));
    }

    @Override protected void load() {
        swipe.setRefreshing(true);
        Api.async(requireContext(), "GET", "/api/admin/products", null, r -> {
            if (!isAdded()) return;
            JSONArray arr = r.optJSONArray("products");
            List<Rows.Row> rows = new ArrayList<>();
            for (int i = 0; arr != null && i < arr.length(); i++) {
                JSONObject p = arr.optJSONObject(i);
                Rows.Row row = new Rows.Row();
                row.data = p;
                row.title = p.optString("name");
                row.subtitle = p.optBoolean("isOffer")
                        ? Ui.price(p.optDouble("price")) + "   (" + Ui.price(p.optDouble("oldPrice")) + ")"
                        : Ui.price(p.optDouble("price"));
                row.meta = "#" + p.optInt("id") + (p.optString("category").isEmpty() ? "" : " · " + p.optString("category"));
                row.image = p.optString("image");
                row.icon = R.drawable.ic_photo;
                if (p.optBoolean("hidden")) { row.badge = getString(R.string.badge_hidden); row.badgeColor = 0xFF757575; }
                else if (p.optBoolean("isOffer")) { row.badge = getString(R.string.badge_promo); row.badgeColor = 0xFFD84315; }
                rows.add(row);
            }
            show(rows, R.string.no_products);
        }, this::failed);
    }

    @Override protected void onRow(Rows.Row r) {
        startActivity(new Intent(getContext(), ProductEditActivity.class).putExtra(ProductEditActivity.EXTRA, r.data.toString()));
    }
}
