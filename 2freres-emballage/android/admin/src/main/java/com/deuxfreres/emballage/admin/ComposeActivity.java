package com.deuxfreres.emballage.admin;

import android.os.Bundle;
import android.view.View;
import android.widget.EditText;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Envoi d'une notification : à tous les clients, ou à UN seul client choisi
 * (dans ce cas, seul ce client la reçoit). Peut être liée à un produit.
 */
public class ComposeActivity extends AppCompatActivity {
    static final String EXTRA_USER = "userId", EXTRA_NAME = "username";

    private int userId;
    private int productId;
    private String productImage = "";
    private MaterialButton userBtn, productBtn;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_compose);
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());
        MaterialButtonToggleGroup target = findViewById(R.id.target);
        userBtn = findViewById(R.id.user);
        productBtn = findViewById(R.id.product);
        EditText title = findViewById(R.id.title);
        EditText body = findViewById(R.id.body);

        target.addOnButtonCheckedListener((g, id, checked) -> {
            if (checked) userBtn.setVisibility(id == R.id.one ? View.VISIBLE : View.GONE);
        });
        userBtn.setOnClickListener(v -> pickUser());
        productBtn.setOnClickListener(v -> pickProduct());

        // Ouvert depuis la fiche d'un client : destinataire déjà choisi
        int preset = getIntent().getIntExtra(EXTRA_USER, 0);
        if (preset != 0) {
            target.check(R.id.one);
            userId = preset;
            userBtn.setText(getString(R.string.for_user, getIntent().getStringExtra(EXTRA_NAME)));
        }

        findViewById(R.id.send).setOnClickListener(v -> {
            String text = body.getText().toString().trim();
            if (text.isEmpty()) { body.setError(getString(R.string.need_body)); return; }
            boolean one = target.getCheckedButtonId() == R.id.one;
            if (one && userId == 0) { Ui.toast(this, R.string.need_user); return; }
            JSONObject b = new JSONObject();
            try {
                b.put("title", title.getText().toString().trim());
                b.put("body", text);
                if (one) b.put("userId", userId);
                if (productId != 0) b.put("productId", productId).put("image", productImage);
            } catch (Exception ignored) {}
            v.setEnabled(false);
            Api.async(this, "POST", "/api/admin/notifications", b,
                    r -> { Ui.toast(this, R.string.sent); finish(); },
                    e -> { v.setEnabled(true); Ui.error(this, e); });
        });
    }

    private void pickUser() {
        Api.async(this, "GET", "/api/admin/users", null, r -> {
            JSONArray arr = r.optJSONArray("users");
            List<String> names = new ArrayList<>();
            List<Integer> ids = new ArrayList<>();
            for (int i = 0; arr != null && i < arr.length(); i++) {
                JSONObject u = arr.optJSONObject(i);
                if ("admin".equals(u.optString("role"))) continue;
                names.add("👤 " + u.optString("username"));
                ids.add(u.optInt("id"));
            }
            if (names.isEmpty()) { Ui.toast(this, R.string.no_users); return; }
            new AlertDialog.Builder(this)
                    .setTitle(R.string.choose_user)
                    .setItems(names.toArray(new String[0]), (d, i) -> {
                        userId = ids.get(i);
                        userBtn.setText(getString(R.string.for_user, names.get(i).replace("👤 ", "")));
                    })
                    .show();
        }, e -> Ui.error(this, e));
    }

    private void pickProduct() {
        Api.async(this, "GET", "/api/admin/products", null, r -> {
            JSONArray arr = r.optJSONArray("products");
            List<String> names = new ArrayList<>();
            List<JSONObject> list = new ArrayList<>();
            names.add(getString(R.string.no_product));
            list.add(null);
            for (int i = 0; arr != null && i < arr.length(); i++) {
                JSONObject p = arr.optJSONObject(i);
                names.add(p.optString("name") + " — " + Ui.price(p.optDouble("price")));
                list.add(p);
            }
            new AlertDialog.Builder(this)
                    .setTitle(R.string.link_product)
                    .setItems(names.toArray(new String[0]), (d, i) -> {
                        JSONObject p = list.get(i);
                        productId = p == null ? 0 : p.optInt("id");
                        productImage = p == null ? "" : p.optString("image");
                        productBtn.setText(p == null ? getString(R.string.link_product) : names.get(i));
                    })
                    .show();
        }, e -> Ui.error(this, e));
    }
}
