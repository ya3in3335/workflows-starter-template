package com.deuxfreres.emballage.admin;

import android.content.Intent;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import org.json.JSONObject;

/** Tableau de bord : chiffres clés + raccourcis. */
public class HomeFragment extends Fragment {
    private GridLayout grid;
    private SwipeRefreshLayout swipe;

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inf, @Nullable ViewGroup c, @Nullable Bundle b) {
        View v = inf.inflate(R.layout.fragment_home, c, false);
        grid = v.findViewById(R.id.grid);
        swipe = v.findViewById(R.id.swipe);
        swipe.setColorSchemeResources(R.color.pink);
        swipe.setOnRefreshListener(this::load);
        ((TextView) v.findViewById(R.id.hello)).setText(getString(R.string.hello, Api.username(requireContext())));
        v.findViewById(R.id.addProduct).setOnClickListener(x -> startActivity(new Intent(getContext(), ProductEditActivity.class)));
        v.findViewById(R.id.notify).setOnClickListener(x -> startActivity(new Intent(getContext(), ComposeActivity.class)));
        v.findViewById(R.id.messages).setOnClickListener(x -> ((MainActivity) requireActivity()).goTo(R.id.tab_messages));
        return v;
    }

    @Override public void onResume() { super.onResume(); load(); }

    private void load() {
        swipe.setRefreshing(true);
        Api.async(requireContext(), "GET", "/api/admin/stats", null, r -> {
            if (!isAdded()) return;
            swipe.setRefreshing(false);
            grid.removeAllViews();
            card(String.valueOf(r.optInt("unreadMessages")), getString(R.string.stat_unread), r.optInt("unreadMessages") > 0 ? 0xFFE0457B : 0xFF2A2526);
            card(String.valueOf(r.optInt("products")), getString(R.string.stat_products), 0xFF2A2526);
            card(String.valueOf(r.optInt("users")), getString(R.string.stat_users), 0xFF2A2526);
            card(String.valueOf(r.optInt("promos")), getString(R.string.stat_promos), 0xFFD84315);
            card(String.valueOf(r.optInt("reviews")), getString(R.string.stat_reviews), 0xFF2A2526);
            card(r.optDouble("avgRating", 0) > 0 ? String.valueOf(r.optDouble("avgRating")) : "–", getString(R.string.stat_rating), 0xFFF5A623);
            ((MainActivity) requireActivity()).setUnread(r.optInt("unreadMessages"));
            Watcher.remember(requireContext(), r);
        }, e -> { if (isAdded()) { swipe.setRefreshing(false); Ui.error(requireActivity(), e); } });
    }

    private void card(String value, String label, int color) {
        float d = getResources().getDisplayMetrics().density;
        LinearLayout box = new LinearLayout(getContext());
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        box.setBackgroundResource(R.drawable.bg_card);
        box.setElevation(3 * d);
        box.setPadding((int) (12 * d), (int) (18 * d), (int) (12 * d), (int) (18 * d));
        TextView v = new TextView(getContext());
        v.setText(value);
        v.setTextColor(color);
        v.setTextSize(TypedValue.COMPLEX_UNIT_SP, 30);
        v.setTypeface(v.getTypeface(), android.graphics.Typeface.BOLD);
        TextView l = new TextView(getContext());
        l.setText(label);
        l.setTextColor(0xFF757575);
        box.addView(v);
        box.addView(l);
        GridLayout.LayoutParams lp = new GridLayout.LayoutParams(GridLayout.spec(GridLayout.UNDEFINED), GridLayout.spec(GridLayout.UNDEFINED, 1f));
        lp.width = 0;
        lp.setMargins((int) (6 * d), (int) (6 * d), (int) (6 * d), (int) (6 * d));
        grid.addView(box, lp);
    }
}
