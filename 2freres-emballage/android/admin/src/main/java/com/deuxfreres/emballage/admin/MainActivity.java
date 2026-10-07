package com.deuxfreres.emballage.admin;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.google.android.material.badge.BadgeDrawable;
import com.google.android.material.bottomnavigation.BottomNavigationView;

public class MainActivity extends AppCompatActivity {
    static final String EXTRA_TAB = "tab";

    private BottomNavigationView bottom;
    private final ActivityResultLauncher<String> askNotif =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), g -> {});

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        bottom = findViewById(R.id.bottom);
        bottom.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            Fragment f = id == R.id.tab_products ? new ProductsFragment()
                    : id == R.id.tab_messages ? new ConversationsFragment()
                    : id == R.id.tab_notifs ? new NotifsFragment()
                    : id == R.id.tab_more ? new MoreFragment()
                    : new HomeFragment();
            getSupportFragmentManager().beginTransaction().replace(R.id.container, f).commit();
            return true;
        });
        if (savedInstanceState == null) {
            bottom.setSelectedItemId(getIntent().getIntExtra(EXTRA_TAB, R.id.tab_home));
        }
        Watcher.schedule(this);
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this,
                Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            askNotif.launch(Manifest.permission.POST_NOTIFICATIONS);
        }
    }

    @Override
    protected void onNewIntent(android.content.Intent intent) {
        super.onNewIntent(intent);
        int tab = intent.getIntExtra(EXTRA_TAB, 0);
        if (tab != 0) bottom.setSelectedItemId(tab);
    }

    /** Pastille rouge sur l'onglet « Messages ». */
    void setUnread(int n) {
        BadgeDrawable b = bottom.getOrCreateBadge(R.id.tab_messages);
        b.setVisible(n > 0);
        b.setNumber(n);
    }

    void goTo(int tab) { bottom.setSelectedItemId(tab); }
}
