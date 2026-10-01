package com.deuxfreres.emballage;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;
import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/** Notifications système + mémoire de ce qui a déjà été vu / notifié. */
final class Notifier {
    static final String CHANNEL = "news";
    static final String EXTRA_OPEN = "open";            // "notifications" ou id produit
    private static final String PREFS = "notifications";
    private static final String KEY_NOTIFIED = "last_notified";
    private static final String KEY_SEEN = "last_seen";

    private Notifier() {}

    static void schedule(Context ctx) {
        PeriodicWorkRequest req = new PeriodicWorkRequest.Builder(NotifyWorker.class, 15, TimeUnit.MINUTES)
                .setConstraints(new Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build();
        WorkManager.getInstance(ctx).enqueueUniquePeriodicWork("catalog_check", ExistingPeriodicWorkPolicy.KEEP, req);
    }

    static boolean enabled(Context ctx) {
        return NotificationManagerCompat.from(ctx).areNotificationsEnabled();
    }

    /** Ouvre les réglages de notifications de l'application. */
    static void openSettings(Context ctx) {
        Intent i = new Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, ctx.getPackageName())
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        ctx.startActivity(i);
    }

    static void createChannel(Context ctx) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(CHANNEL,
                    ctx.getString(R.string.channel_name), NotificationManager.IMPORTANCE_HIGH);
            ch.setDescription(ctx.getString(R.string.channel_desc));
            ctx.getSystemService(NotificationManager.class).createNotificationChannel(ch);
        }
    }

    private static SharedPreferences prefs(Context ctx) {
        return ctx.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /** Nombre de notifications pas encore ouvertes dans l'application. */
    static int unread(Context ctx, Catalog c) {
        long seen = prefs(ctx).getLong(KEY_SEEN, -1);
        if (seen < 0) {                    // première ouverture : l'historique n'est pas "non lu"
            if (!c.notifications.isEmpty()) markSeen(ctx, c);
            return 0;
        }
        int n = 0;
        for (AppNotification a : c.notifications) if (a.id > seen) n++;
        return n;
    }

    static long lastSeen(Context ctx) {
        return prefs(ctx).getLong(KEY_SEEN, 0);
    }

    static void markSeen(Context ctx, Catalog c) {
        long max = c.maxNotificationId();
        SharedPreferences p = prefs(ctx);
        if (max > p.getLong(KEY_SEEN, -1)) p.edit().putLong(KEY_SEEN, max).apply();
        markNotified(ctx, max);
    }

    /** L'utilisateur a déjà le catalogue sous les yeux : inutile de le notifier pour ça. */
    static void markNotified(Context ctx, long id) {
        SharedPreferences p = prefs(ctx);
        if (id > p.getLong(KEY_NOTIFIED, -1)) p.edit().putLong(KEY_NOTIFIED, id).apply();
    }

    /** Affiche les notifications plus récentes que la dernière notifiée. */
    static void notifyNew(Context ctx, Catalog c) {
        SharedPreferences p = prefs(ctx);
        long last = p.getLong(KEY_NOTIFIED, -1);
        long max = c.maxNotificationId();
        if (last < 0) {                    // premier passage : on ne rejoue pas l'historique
            p.edit().putLong(KEY_NOTIFIED, max).apply();
            return;
        }
        List<AppNotification> fresh = new ArrayList<>();
        for (AppNotification n : c.notifications) if (n.id > last) fresh.add(n);
        if (fresh.isEmpty()) return;
        p.edit().putLong(KEY_NOTIFIED, max).apply();

        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(ctx,
                Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return;
        if (!enabled(ctx)) return;
        createChannel(ctx);
        NotificationManagerCompat nm = NotificationManagerCompat.from(ctx);

        if (fresh.size() <= 3) {
            for (AppNotification n : fresh) {
                String open = n.productId > 0 ? String.valueOf(n.productId) : "notifications";
                nm.notify((int) n.id, base(ctx, open, (int) n.id)
                        .setContentTitle(n.title)
                        .setContentText(n.body)
                        .setStyle(new NotificationCompat.BigTextStyle().bigText(n.body))
                        .build());
            }
        } else {
            NotificationCompat.InboxStyle inbox = new NotificationCompat.InboxStyle();
            for (AppNotification n : fresh) inbox.addLine(n.title + " · " + n.body);
            String title = ctx.getString(R.string.notif_summary, fresh.size());
            nm.notify(0, base(ctx, "notifications", 0)
                    .setContentTitle(title)
                    .setContentText(fresh.get(0).body)
                    .setStyle(inbox.setBigContentTitle(title))
                    .build());
        }
    }

    private static NotificationCompat.Builder base(Context ctx, String open, int requestCode) {
        Intent i = new Intent(ctx, MainActivity.class)
                .putExtra(EXTRA_OPEN, open)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pi = PendingIntent.getActivity(ctx, requestCode, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        return new NotificationCompat.Builder(ctx, CHANNEL)
                .setSmallIcon(R.drawable.ic_stat_box)
                .setColor(ContextCompat.getColor(ctx, R.color.card_pink))
                .setContentIntent(pi)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setDefaults(NotificationCompat.DEFAULT_ALL)
                .setAutoCancel(true);
    }
}
