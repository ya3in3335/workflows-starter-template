package com.deuxfreres.emballage;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.widget.Toast;

import java.text.NumberFormat;
import java.util.Locale;

final class Utils {
    private Utils() {}

    static String price(double v) {
        NumberFormat nf = NumberFormat.getNumberInstance(Locale.FRANCE);
        nf.setMaximumFractionDigits(2);
        return nf.format(v) + " " + Config.CURRENCY;
    }

    static void openWhatsApp(Context ctx, String message) {
        Uri uri = Uri.parse("https://wa.me/" + Config.WHATSAPP_NUMBER + "?text=" + Uri.encode(message));
        try {
            ctx.startActivity(new Intent(Intent.ACTION_VIEW, uri));
        } catch (ActivityNotFoundException e) {
            Toast.makeText(ctx, R.string.no_whatsapp, Toast.LENGTH_LONG).show();
        }
    }
}
