package com.deuxfreres.emballage;

import android.graphics.Paint;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;

public class DetailActivity extends AppCompatActivity {

    public static final String EXTRA_PRODUCT = "product";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_detail);

        Product p = (Product) getIntent().getSerializableExtra(EXTRA_PRODUCT);
        if (p == null) { finish(); return; }

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setTitle(p.category.isEmpty() ? Config.STORE_NAME : p.category);
        toolbar.setNavigationOnClickListener(v -> finish());

        ImageView image = findViewById(R.id.image);
        Glide.with(this).load(p.image).placeholder(R.drawable.placeholder)
                .error(R.drawable.placeholder).fitCenter().into(image);

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
        if (p.description.isEmpty()) desc.setVisibility(View.GONE);
        else desc.setText(p.description);

        MaterialButton order = findViewById(R.id.order);
        order.setOnClickListener(v -> Utils.openWhatsApp(this,
                getString(R.string.wa_product, p.name, p.id, Utils.price(p.price))));
    }
}
