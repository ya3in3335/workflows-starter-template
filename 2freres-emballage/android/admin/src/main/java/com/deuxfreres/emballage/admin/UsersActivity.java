package com.deuxfreres.emballage.admin;

import android.content.Intent;
import android.os.Bundle;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** Clients : écrire, envoyer une notification personnelle, bloquer / débloquer. */
public class UsersActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_host);
        if (savedInstanceState == null) {
            getSupportFragmentManager().beginTransaction().replace(R.id.container, new Screen()).commit();
        }
    }

    public static class Screen extends ListFragment {
        @Override protected void setup() {
            toolbar.setTitle(R.string.more_users);
            toolbar.setNavigationIcon(androidx.appcompat.R.drawable.abc_ic_ab_back_material);
            toolbar.setNavigationOnClickListener(v -> requireActivity().finish());
            search.setVisibility(android.view.View.VISIBLE);
            search.setHint(R.string.username);
        }

        @Override protected void load() {
            swipe.setRefreshing(true);
            Api.async(requireContext(), "GET", "/api/admin/users", null, r -> {
                if (!isAdded()) return;
                JSONArray arr = r.optJSONArray("users");
                List<Rows.Row> rows = new ArrayList<>();
                for (int i = 0; arr != null && i < arr.length(); i++) {
                    JSONObject u = arr.optJSONObject(i);
                    if ("admin".equals(u.optString("role"))) continue;
                    Rows.Row row = new Rows.Row();
                    row.data = u;
                    row.title = "👤 " + u.optString("username");
                    row.subtitle = getString(R.string.user_meta, u.optInt("reviews"), u.optInt("messages"));
                    row.meta = "🕒 " + Ui.ago(u.optLong("lastSeen") > 0 ? u.optLong("lastSeen") : u.optLong("createdAt"));
                    row.icon = R.drawable.ic_person;
                    if (u.optBoolean("banned")) { row.badge = getString(R.string.banned); row.badgeColor = 0xFFD84315; }
                    rows.add(row);
                }
                show(rows, R.string.no_users);
            }, this::failed);
        }

        @Override protected void onRow(Rows.Row r) {
            JSONObject u = r.data;
            boolean banned = u.optBoolean("banned");
            String[] actions = {
                    getString(R.string.action_message),
                    getString(R.string.action_notify),
                    getString(banned ? R.string.action_unban : R.string.action_ban)
            };
            new AlertDialog.Builder(requireContext())
                    .setTitle("👤 " + u.optString("username"))
                    .setItems(actions, (d, i) -> {
                        if (i == 0) {
                            startActivity(new Intent(getContext(), ChatActivity.class)
                                    .putExtra(ChatActivity.EXTRA_USER, u.optInt("id"))
                                    .putExtra(ChatActivity.EXTRA_NAME, u.optString("username")));
                        } else if (i == 1) {
                            startActivity(new Intent(getContext(), ComposeActivity.class)
                                    .putExtra(ComposeActivity.EXTRA_USER, u.optInt("id"))
                                    .putExtra(ComposeActivity.EXTRA_NAME, u.optString("username")));
                        } else {
                            JSONObject b = new JSONObject();
                            try { b.put("banned", !banned); } catch (Exception ignored) {}
                            Api.async(requireContext(), "POST", "/api/admin/users/" + u.optInt("id") + "/ban", b, x -> load(), this::failed);
                        }
                    })
                    .show();
        }
    }
}
