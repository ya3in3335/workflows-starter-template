package com.deuxfreres.emballage.admin;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.text.format.DateUtils;
import android.widget.Toast;

import java.text.NumberFormat;
import java.util.Locale;

/** Petits outils d'affichage communs. */
final class Ui {
    private Ui() {}

    static String price(double v) {
        NumberFormat nf = NumberFormat.getNumberInstance(Locale.FRANCE);
        nf.setMaximumFractionDigits(2);
        return nf.format(v) + " DA";
    }

    static CharSequence ago(long t) {
        if (t <= 0) return "";
        return DateUtils.getRelativeTimeSpanString(t, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS);
    }

    static String stars(int n) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 5; i++) sb.append(i < n ? '★' : '☆');
        return sb.toString();
    }

    static void toast(Context c, int res) { Toast.makeText(c, res, Toast.LENGTH_SHORT).show(); }

    /** Message d'erreur ; session expirée → retour à l'écran de connexion. */
    static void error(Activity a, Exception e) {
        if (e instanceof Api.ApiException) {
            Api.ApiException x = (Api.ApiException) e;
            if (x.status == 401 || x.status == 403) {
                Api.clear(a);
                a.startActivity(new Intent(a, LoginActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK));
                return;
            }
            Toast.makeText(a, a.getString(R.string.error) + " (" + x.code + ")", Toast.LENGTH_LONG).show();
            return;
        }
        Toast.makeText(a, R.string.offline, Toast.LENGTH_LONG).show();
    }
}
