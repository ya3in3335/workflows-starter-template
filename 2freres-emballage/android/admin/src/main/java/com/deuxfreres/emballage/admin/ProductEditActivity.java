package com.deuxfreres.emballage.admin;

import android.app.ProgressDialog;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageDecoder;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.checkbox.MaterialCheckBox;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.LinkedHashSet;
import java.util.Set;

/** Ajouter / modifier un produit : photo de la galerie, prix, promo, catégorie, visibilité. */
public class ProductEditActivity extends AppCompatActivity {
    static final String EXTRA = "product";

    private JSONObject product;
    private Uri newPhoto;
    private EditText name, price, description, promoPrice;
    private MaterialAutoCompleteTextView category;
    private MaterialSwitch promo, hidden;
    private MaterialCheckBox notify;
    private ImageView photo;
    private TextView photoLabel;

    private final ActivityResultLauncher<String> pick =
            registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
                if (uri == null) return;
                newPhoto = uri;
                Glide.with(this).load(uri).centerCrop().into(photo);
                photoLabel.setText(R.string.change_photo);
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_product_edit);
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());
        name = findViewById(R.id.name);
        price = findViewById(R.id.price);
        description = findViewById(R.id.description);
        promoPrice = findViewById(R.id.promoPrice);
        category = findViewById(R.id.category);
        promo = findViewById(R.id.promo);
        hidden = findViewById(R.id.hidden);
        notify = findViewById(R.id.notify);
        photo = findViewById(R.id.photo);
        photoLabel = findViewById(R.id.photoLabel);
        View promoBox = findViewById(R.id.promoBox);

        promo.setOnCheckedChangeListener((b, on) -> promoBox.setVisibility(on ? View.VISIBLE : View.GONE));
        findViewById(R.id.photoBox).setOnClickListener(v -> pick.launch("image/*"));
        findViewById(R.id.save).setOnClickListener(v -> save());

        try {
            String raw = getIntent().getStringExtra(EXTRA);
            product = raw == null ? null : new JSONObject(raw);
        } catch (Exception e) { product = null; }

        if (product == null) {
            toolbar.setTitle(R.string.new_product);
            notify.setChecked(true);
        } else {
            toolbar.setTitle(R.string.edit_product);
            name.setText(product.optString("name"));
            category.setText(product.optString("category"));
            description.setText(product.optString("description"));
            boolean offer = product.optBoolean("isOffer");
            promo.setChecked(offer);
            if (offer) {
                // En promo : « Prix » = prix d'origine, « Prix promo » = prix actuel
                price.setText(num(product.optDouble("oldPrice")));
                promoPrice.setText(num(product.optDouble("price")));
            } else {
                price.setText(num(product.optDouble("price")));
            }
            hidden.setChecked(product.optBoolean("hidden"));
            String img = product.optString("image");
            if (!img.isEmpty()) { Glide.with(this).load(img).centerCrop().into(photo); photoLabel.setText(R.string.change_photo); }
            View del = findViewById(R.id.delete);
            del.setVisibility(View.VISIBLE);
            del.setOnClickListener(v -> confirmDelete());
        }
        loadCategories();
    }

    private static String num(double v) {
        return v == Math.floor(v) ? String.valueOf((long) v) : String.valueOf(v);
    }

    private void loadCategories() {
        Api.async(this, "GET", "/api/admin/products", null, r -> {
            Set<String> cats = new LinkedHashSet<>();
            JSONArray arr = r.optJSONArray("products");
            for (int i = 0; arr != null && i < arr.length(); i++) {
                String c = arr.optJSONObject(i).optString("category");
                if (!c.isEmpty()) cats.add(c);
            }
            category.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, cats.toArray(new String[0])));
        }, null);
    }

    private double parse(EditText e) {
        try { return Double.parseDouble(e.getText().toString().replace(',', '.').replace(" ", "")); }
        catch (Exception x) { return 0; }
    }

    private void save() {
        String n = name.getText().toString().trim();
        double p = parse(price);
        double pp = parse(promoPrice);
        if (n.isEmpty()) { name.setError(getString(R.string.need_name)); return; }
        if (p <= 0) { price.setError(getString(R.string.need_price)); return; }
        if (promo.isChecked() && (pp <= 0 || pp >= p)) { promoPrice.setError(getString(R.string.bad_promo)); return; }

        ProgressDialog wait = ProgressDialog.show(this, null, getString(newPhoto != null ? R.string.uploading : R.string.save), true);
        Api.EXEC.execute(() -> {
            try {
                JSONObject body = new JSONObject();
                if (product != null) body.put("id", product.optInt("id"));
                body.put("name", n);
                if (promo.isChecked()) {
                    body.put("isOffer", true).put("price", pp).put("oldPrice", p);
                } else {
                    body.put("isOffer", false).put("price", p);
                }
                body.put("category", category.getText().toString().trim());
                body.put("description", description.getText().toString().trim());
                body.put("hidden", hidden.isChecked());
                body.put("notify", notify.isChecked());
                if (newPhoto != null) body.put("image", Api.upload(this, compress(newPhoto)).getString("id"));
                Api.call(this, "POST", "/api/admin/products", body);
                runOnUiThread(() -> { wait.dismiss(); Ui.toast(this, R.string.saved); finish(); });
            } catch (Exception e) {
                runOnUiThread(() -> { wait.dismiss(); Ui.error(this, e); });
            }
        });
    }

    /** Redimensionne (max 1080 px) et compresse en JPEG pour un envoi rapide. */
    private byte[] compress(Uri uri) throws Exception {
        Bitmap bmp;
        if (Build.VERSION.SDK_INT >= 28) {
            bmp = ImageDecoder.decodeBitmap(ImageDecoder.createSource(getContentResolver(), uri), (dec, info, src) -> {
                int w = info.getSize().getWidth(), h = info.getSize().getHeight();
                float s = Math.min(1f, 1080f / Math.max(w, h));
                dec.setTargetSize(Math.max(1, Math.round(w * s)), Math.max(1, Math.round(h * s)));
                dec.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE);
            });
        } else {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            try (InputStream in = getContentResolver().openInputStream(uri)) { BitmapFactory.decodeStream(in, null, o); }
            int sample = 1;
            while (Math.max(o.outWidth, o.outHeight) / (sample * 2) >= 1080) sample *= 2;
            BitmapFactory.Options o2 = new BitmapFactory.Options();
            o2.inSampleSize = sample;
            try (InputStream in = getContentResolver().openInputStream(uri)) { bmp = BitmapFactory.decodeStream(in, null, o2); }
            float s = Math.min(1f, 1080f / Math.max(bmp.getWidth(), bmp.getHeight()));
            if (s < 1f) bmp = Bitmap.createScaledBitmap(bmp, Math.round(bmp.getWidth() * s), Math.round(bmp.getHeight() * s), true);
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int q = 85;
        bmp.compress(Bitmap.CompressFormat.JPEG, q, out);
        while (out.size() > 900_000 && q > 40) { out.reset(); q -= 15; bmp.compress(Bitmap.CompressFormat.JPEG, q, out); }
        return out.toByteArray();
    }

    private void confirmDelete() {
        new AlertDialog.Builder(this)
                .setMessage(R.string.confirm_delete)
                .setPositiveButton(R.string.yes_delete, (d, w) -> Api.async(this, "DELETE",
                        "/api/admin/products/" + product.optInt("id"), null,
                        r -> { Ui.toast(this, R.string.deleted); finish(); }, e -> Ui.error(this, e)))
                .setNegativeButton(R.string.cancel, null)
                .show();
    }
}
