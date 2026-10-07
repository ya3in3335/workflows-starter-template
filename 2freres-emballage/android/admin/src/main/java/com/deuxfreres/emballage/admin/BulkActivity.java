package com.deuxfreres.emballage.admin;

import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.checkbox.MaterialCheckBox;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Ajout en masse : plusieurs photos + une liste « nom - prix ».
 * L'IA (Workers AI) propose quel nom va avec chaque photo ; l'admin corrige puis enregistre tout.
 */
public class BulkActivity extends AppCompatActivity {

    private final List<Uri> photos = new ArrayList<>();
    private final List<String> names = new ArrayList<>();
    private final List<Double> prices = new ArrayList<>();
    private final List<String> imageIds = new ArrayList<>();
    private final List<Integer> choice = new ArrayList<>();   // index dans names, -1 = rien
    private Rows adapter;
    private MaterialButton pick, match, save;
    private TextView status;
    private EditText lines, category;
    private MaterialCheckBox notify;

    private final ActivityResultLauncher<String> picker =
            registerForActivityResult(new ActivityResultContracts.GetMultipleContents(), uris -> {
                if (uris == null || uris.isEmpty()) return;
                photos.clear();
                photos.addAll(uris);
                imageIds.clear();
                choice.clear();
                pick.setText(getString(R.string.bulk_pick, photos.size()));
                render();
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_bulk);
        ((com.google.android.material.appbar.MaterialToolbar) findViewById(R.id.toolbar)).setNavigationOnClickListener(v -> finish());
        pick = findViewById(R.id.pick);
        match = findViewById(R.id.match);
        save = findViewById(R.id.save);
        status = findViewById(R.id.status);
        lines = findViewById(R.id.lines);
        category = findViewById(R.id.category);
        notify = findViewById(R.id.notify);
        RecyclerView list = findViewById(R.id.list);
        list.setLayoutManager(new LinearLayoutManager(this));
        adapter = new Rows(this::choose, null);
        list.setAdapter(adapter);
        pick.setText(getString(R.string.bulk_pick, 0));
        pick.setOnClickListener(v -> picker.launch("image/*"));
        match.setOnClickListener(v -> runMatch());
        save.setOnClickListener(v -> saveAll());
        render();
    }

    /** « Boîte pizza 33cm - 1 500 » → nom + prix (dernier nombre de la ligne). */
    private void parse() {
        names.clear();
        prices.clear();
        Pattern num = Pattern.compile("(\\d[\\d\\s.,]*)\\s*(da|dzd|دج)?\\s*$", Pattern.CASE_INSENSITIVE);
        for (String raw : lines.getText().toString().split("\n")) {
            String line = raw.trim();
            if (line.isEmpty()) continue;
            Matcher m = num.matcher(line);
            double price = 0;
            String name = line;
            if (m.find()) {
                try { price = Double.parseDouble(m.group(1).replace(" ", "").replace(",", ".")); } catch (Exception ignored) {}
                name = line.substring(0, m.start()).replaceAll("[\\s:=\\-–—|]+$", "").trim();
            }
            if (!name.isEmpty()) { names.add(name); prices.add(price); }
        }
    }

    private void runMatch() {
        parse();
        if (photos.isEmpty() || names.isEmpty()) { Ui.toast(this, R.string.bulk_need); return; }
        match.setEnabled(false);
        save.setEnabled(false);
        status.setVisibility(View.VISIBLE);
        List<Uri> todo = new ArrayList<>(photos);
        List<String> nm = new ArrayList<>(names);
        Api.EXEC.execute(() -> {
            List<String> ids = new ArrayList<>();
            List<Integer> picks = new ArrayList<>();
            JSONArray jn = new JSONArray(nm);
            for (int i = 0; i < todo.size(); i++) {
                int done = i;
                runOnUiThread(() -> status.setText(getString(R.string.bulk_working, done + 1, todo.size())));
                String id = "";
                int idx = -1;
                try {
                    id = Api.upload(this, ProductEditActivity.compress(this, todo.get(i))).getString("id");
                    idx = Api.call(this, "POST", "/api/admin/ai/match",
                            new JSONObject().put("imageId", id).put("names", jn)).optInt("index", -1);
                } catch (Exception ignored) {}
                if (idx < 0 && i < nm.size()) idx = i;                 // repli : même ordre
                ids.add(id);
                picks.add(idx);
            }
            runOnUiThread(() -> {
                imageIds.clear(); imageIds.addAll(ids);
                choice.clear(); choice.addAll(picks);
                match.setEnabled(true);
                status.setText(R.string.bulk_tap);
                render();
            });
        });
    }

    private void render() {
        List<Rows.Row> rows = new ArrayList<>();
        int ready = 0;
        for (int i = 0; i < photos.size(); i++) {
            Rows.Row r = new Rows.Row();
            r.image = photos.get(i).toString();
            int c = i < choice.size() ? choice.get(i) : -1;
            if (c >= 0 && c < names.size()) {
                r.title = names.get(c);
                r.subtitle = Ui.price(prices.get(c));
                if (prices.get(c) > 0 && i < imageIds.size() && !imageIds.get(i).isEmpty()) ready++;
            } else {
                r.title = choice.isEmpty() ? "📷 " + (i + 1) : getString(R.string.bulk_none);
            }
            try { r.data = new JSONObject().put("i", i); } catch (Exception ignored) {}
            rows.add(r);
        }
        adapter.submit(rows);
        save.setText(getString(R.string.bulk_save, ready));
        save.setEnabled(ready > 0);
    }

    private void choose(Rows.Row row) {
        if (choice.isEmpty()) return;
        int i = row.data.optInt("i");
        String[] opts = new String[names.size() + 1];
        for (int k = 0; k < names.size(); k++) opts[k] = names.get(k) + " — " + Ui.price(prices.get(k));
        opts[names.size()] = getString(R.string.bulk_skip);
        new AlertDialog.Builder(this).setItems(opts, (d, k) -> {
            choice.set(i, k == names.size() ? -1 : k);
            render();
        }).show();
    }

    private void saveAll() {
        save.setEnabled(false);
        match.setEnabled(false);
        String cat = category.getText().toString().trim();
        boolean notif = notify.isChecked();
        List<JSONObject> bodies = new ArrayList<>();
        for (int i = 0; i < photos.size(); i++) {
            int c = i < choice.size() ? choice.get(i) : -1;
            if (c < 0 || prices.get(c) <= 0 || i >= imageIds.size() || imageIds.get(i).isEmpty()) continue;
            try {
                bodies.add(new JSONObject().put("name", names.get(c)).put("price", prices.get(c))
                        .put("category", cat).put("image", imageIds.get(i)).put("notify", false));
            } catch (Exception ignored) {}
        }
        Api.EXEC.execute(() -> {
            int ok = 0;
            String firstImage = "";
            for (JSONObject b : bodies) {
                try {
                    Api.call(this, "POST", "/api/admin/products", b);
                    if (ok == 0) firstImage = b.optString("image");
                    ok++;
                } catch (Exception ignored) {}
            }
            if (notif && ok > 0) {
                try {
                    Api.call(this, "POST", "/api/admin/notifications", new JSONObject()
                            .put("title", "🆕 Nouveaux produits")
                            .put("body", ok + " nouveaux produits chez 2 Frères Emballage !")
                            .put("image", firstImage));
                } catch (Exception ignored) {}
            }
            int n = ok;
            runOnUiThread(() -> {
                android.widget.Toast.makeText(this, getString(R.string.bulk_done, n), android.widget.Toast.LENGTH_LONG).show();
                finish();
            });
        });
    }
}
