package com.deuxfreres.emballage.admin;

import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;

import androidx.appcompat.app.AlertDialog;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** Avis, clients, mot de passe, déconnexion. */
public class MoreFragment extends ListFragment {

    @Override protected void setup() {
        toolbar.setTitle(R.string.tab_more);
        swipe.setEnabled(false);
    }

    @Override protected void load() {
        List<Rows.Row> rows = new ArrayList<>();
        rows.add(item(R.string.more_reviews, R.drawable.ic_star, "reviews"));
        rows.add(item(R.string.more_users, R.drawable.ic_people, "users"));
        rows.add(item(R.string.more_password, R.drawable.ic_lock, "password"));
        rows.add(item(R.string.logout, R.drawable.ic_logout, "logout"));
        show(rows, R.string.error);
    }

    private Rows.Row item(int title, int icon, String key) {
        Rows.Row r = new Rows.Row();
        r.title = getString(title);
        r.icon = icon;
        r.meta = "";
        try { r.data = new JSONObject().put("key", key); } catch (Exception ignored) {}
        return r;
    }

    @Override protected void onRow(Rows.Row r) {
        switch (r.data.optString("key")) {
            case "reviews": startActivity(new Intent(getContext(), ReviewsActivity.class)); break;
            case "users": startActivity(new Intent(getContext(), UsersActivity.class)); break;
            case "password": changePassword(); break;
            case "logout":
                Api.clear(requireContext());
                startActivity(new Intent(getContext(), LoginActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK));
                break;
        }
    }

    private void changePassword() {
        float d = getResources().getDisplayMetrics().density;
        LinearLayout box = new LinearLayout(getContext());
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding((int) (24 * d), (int) (8 * d), (int) (24 * d), 0);
        EditText old = new EditText(getContext());
        old.setHint(R.string.old_password);
        old.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        EditText neu = new EditText(getContext());
        neu.setHint(R.string.new_password);
        neu.setInputType(old.getInputType());
        box.addView(old);
        box.addView(neu);
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.more_password)
                .setView(box)
                .setPositiveButton(R.string.save, (dlg, w) -> {
                    JSONObject b = new JSONObject();
                    try { b.put("oldPassword", old.getText().toString()).put("newPassword", neu.getText().toString()); } catch (Exception ignored) {}
                    Api.async(requireContext(), "POST", "/api/me/password", b,
                            x -> Ui.toast(requireContext(), R.string.password_changed),
                            e -> Ui.toast(requireContext(), R.string.bad_credentials));
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }
}
