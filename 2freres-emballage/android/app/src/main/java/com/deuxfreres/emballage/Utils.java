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

    static void call(Context ctx) {
        try {
            ctx.startActivity(new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Config.PHONE_NUMBER)));
        } catch (ActivityNotFoundException e) {
            Toast.makeText(ctx, R.string.no_phone_app, Toast.LENGTH_LONG).show();
        }
    }

    static void share(Context ctx) {
        Intent i = new Intent(Intent.ACTION_SEND)
                .setType("text/plain")
                .putExtra(Intent.EXTRA_TEXT, ctx.getString(R.string.share_text, Config.WHATSAPP_NUMBER));
        ctx.startActivity(Intent.createChooser(i, ctx.getString(R.string.share_app)));
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
