package com.deuxfreres.emballage;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;

public class NotificationsActivity extends AppCompatActivity {

    private ProductRepository repo;
    private NotificationAdapter adapter;
    private View empty;
    private Catalog catalog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_notifications);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        empty = findViewById(R.id.empty);
        RecyclerView list = findViewById(R.id.list);
        list.setLayoutManager(new LinearLayoutManager(this));
        adapter = new NotificationAdapter(n -> {
            Product p = catalog == null ? null : catalog.product(n.productId);
            if (p != null) {
                startActivity(new Intent(this, DetailActivity.class).putExtra(DetailActivity.EXTRA_PRODUCT, p));
            }
        });
        list.setAdapter(adapter);

        repo = new ProductRepository(this);
        show(repo.cached());
        repo.load((c, error) -> {
            if (!isDestroyed()) show(c);
        });
    }

    private void show(Catalog c) {
        catalog = c;
        long seen = Notifier.lastSeen(this);
        adapter.submit(c.notifications, seen);
        empty.setVisibility(c.notifications.isEmpty() ? View.VISIBLE : View.GONE);
        Notifier.markSeen(this, c);
    }
}
