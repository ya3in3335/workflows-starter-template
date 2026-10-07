package com.deuxfreres.emballage.admin;

import android.os.Bundle;
import android.widget.EditText;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** Tous les avis : répondre (le client est notifié) ou supprimer. */
public class ReviewsActivity extends AppCompatActivity {
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
            toolbar.setTitle(R.string.more_reviews);
            toolbar.setNavigationIcon(androidx.appcompat.R.drawable.abc_ic_ab_back_material);
            toolbar.setNavigationOnClickListener(v -> requireActivity().finish());
            search.setVisibility(android.view.View.VISIBLE);
        }

        @Override protected void load() {
            swipe.setRefreshing(true);
            Api.async(requireContext(), "GET", "/api/admin/reviews", null, r -> {
                if (!isAdded()) return;
                JSONArray arr = r.optJSONArray("reviews");
                List<Rows.Row> rows = new ArrayList<>();
                for (int i = 0; arr != null && i < arr.length(); i++) {
                    JSONObject v = arr.optJSONObject(i);
                    Rows.Row row = new Rows.Row();
                    row.data = v;
                    row.title = Ui.stars(v.optInt("rating")) + "  " + v.optString("productName");
                    String text = v.optString("text");
                    String reply = v.optString("reply");
                    row.subtitle = (text.isEmpty() ? "—" : text) + (reply.isEmpty() ? "" : "\n" + getString(R.string.store_reply, reply));
                    row.meta = "👤 " + v.optString("username") + " · " + Ui.ago(v.optLong("createdAt"));
                    rows.add(row);
                }
                show(rows, R.string.no_reviews);
            }, this::failed);
        }

        @Override protected void onRow(Rows.Row r) {
            EditText input = new EditText(requireContext());
            input.setText(r.data.optString("reply"));
            input.setHint(R.string.reply);
            int pad = (int) (20 * getResources().getDisplayMetrics().density);
            android.widget.FrameLayout box = new android.widget.FrameLayout(requireContext());
            box.setPadding(pad, pad / 2, pad, 0);
            box.addView(input);
            new AlertDialog.Builder(requireContext())
                    .setTitle(R.string.reply_review)
                    .setMessage(r.data.optString("text"))
                    .setView(box)
                    .setPositiveButton(R.string.send, (d, w) -> {
                        JSONObject b = new JSONObject();
                        try { b.put("reply", input.getText().toString().trim()); } catch (Exception ignored) {}
                        Api.async(requireContext(), "POST", "/api/admin/reviews/" + r.data.optInt("id") + "/reply", b, x -> load(), this::failed);
                    })
                    .setNeutralButton(R.string.delete, (d, w) -> Api.async(requireContext(), "DELETE",
                            "/api/admin/reviews/" + r.data.optInt("id"), null, x -> load(), this::failed))
                    .setNegativeButton(R.string.cancel, null)
                    .show();
        }
    }
}
