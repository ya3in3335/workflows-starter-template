package com.deuxfreres.emballage.admin;

import android.content.Intent;
import android.view.View;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** Discussions avec les clients (non lus en premier par date). */
public class ConversationsFragment extends ListFragment {

    @Override protected void setup() {
        toolbar.setTitle(R.string.tab_messages);
        search.setVisibility(View.VISIBLE);
        search.setHint(R.string.username);
    }

    @Override protected void load() {
        swipe.setRefreshing(true);
        Api.async(requireContext(), "GET", "/api/admin/conversations", null, r -> {
            if (!isAdded()) return;
            JSONArray arr = r.optJSONArray("conversations");
            List<Rows.Row> rows = new ArrayList<>();
            int unread = 0;
            for (int i = 0; arr != null && i < arr.length(); i++) {
                JSONObject c = arr.optJSONObject(i);
                Rows.Row row = new Rows.Row();
                row.data = c;
                row.title = "👤 " + c.optString("username");
                String last = c.optString("lastText");
                row.subtitle = c.optBoolean("lastFromAdmin") ? getString(R.string.you, last) : last;
                row.meta = Ui.ago(c.optLong("lastAt")).toString();
                int n = c.optInt("unread");
                unread += n;
                if (n > 0) row.badge = String.valueOf(n);
                rows.add(row);
            }
            ((MainActivity) requireActivity()).setUnread(unread);
            show(rows, R.string.no_conversations);
        }, this::failed);
    }

    @Override protected void onRow(Rows.Row r) {
        startActivity(new Intent(getContext(), ChatActivity.class)
                .putExtra(ChatActivity.EXTRA_USER, r.data.optInt("userId"))
                .putExtra(ChatActivity.EXTRA_NAME, r.data.optString("username")));
    }
}
