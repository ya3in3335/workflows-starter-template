package com.deuxfreres.emballage.admin;

import android.content.Intent;
import android.view.View;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** Avis, clients, déconnexion (connexion Google : plus de mot de passe). */
public class MoreFragment extends ListFragment {

    @Override protected void setup() {
        toolbar.setTitle(R.string.tab_more);
        swipe.setEnabled(false);
    }

    @Override protected void load() {
        List<Rows.Row> rows = new ArrayList<>();
        rows.add(item(R.string.more_reviews, R.drawable.ic_star, "reviews"));
        rows.add(item(R.string.more_users, R.drawable.ic_people, "users"));
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
            case "logout":
                Api.clear(requireContext());
                // oublie le compte Google choisi, pour pouvoir en prendre un autre
                androidx.credentials.CredentialManager.create(requireContext()).clearCredentialStateAsync(
                        new androidx.credentials.ClearCredentialStateRequest(), null,
                        androidx.core.content.ContextCompat.getMainExecutor(requireContext()),
                        new androidx.credentials.CredentialManagerCallback<Void, androidx.credentials.exceptions.ClearCredentialException>() {
                            @Override public void onResult(Void v) {}
                            @Override public void onError(@androidx.annotation.NonNull androidx.credentials.exceptions.ClearCredentialException e) {}
                        });
                startActivity(new Intent(getContext(), LoginActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK));
                break;
        }
    }
}
