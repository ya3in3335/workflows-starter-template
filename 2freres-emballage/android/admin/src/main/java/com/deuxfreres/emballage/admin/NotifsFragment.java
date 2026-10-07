package com.deuxfreres.emballage.admin;

import android.content.Intent;
import android.view.View;

import androidx.appcompat.app.AlertDialog;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** Notifications envoyées (générales ou personnelles). Appui long = supprimer. */
public class NotifsFragment extends ListFragment {

    @Override protected void setup() {
        toolbar.setTitle(R.string.tab_notifs);
        fab.setVisibility(View.VISIBLE);
        fab.setText(R.string.new_notification);
        fab.setOnClickListener(v -> startActivity(new Intent(getContext(), ComposeActivity.class)));
    }

    @Override protected void load() {
        swipe.setRefreshing(true);
        Api.async(requireContext(), "GET", "/api/admin/notifications", null, r -> {
            if (!isAdded()) return;
            JSONArray arr = r.optJSONArray("notifications");
            List<Rows.Row> rows = new ArrayList<>();
            for (int i = 0; arr != null && i < arr.length(); i++) {
                JSONObject n = arr.optJSONObject(i);
                Rows.Row row = new Rows.Row();
                row.data = n;
                row.title = n.optString("title");
                row.subtitle = n.optString("body");
                String user = n.isNull("username") ? "" : n.optString("username");
                row.badge = user.isEmpty() ? getString(R.string.for_all) : "👤 " + user;
                row.badgeColor = user.isEmpty() ? 0xFF2A2526 : 0xFFE0457B;
                row.meta = n.optString("createdAt").replace('T', ' ').substring(0, Math.min(16, n.optString("createdAt").length()));
                row.image = n.optString("image");
                row.icon = R.drawable.ic_bell;
                rows.add(row);
            }
            show(rows, R.string.no_notifications);
        }, this::failed);
    }

    @Override protected void onRowLong(Rows.Row r) {
        new AlertDialog.Builder(requireContext())
                .setMessage(R.string.confirm_delete)
                .setPositiveButton(R.string.yes_delete, (d, w) -> Api.async(requireContext(), "DELETE",
                        "/api/admin/notifications/" + r.data.optInt("id"), null, x -> load(), this::failed))
                .setNegativeButton(R.string.cancel, null)
                .show();
    }
}
