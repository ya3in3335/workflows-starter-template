package com.deuxfreres.emballage.admin;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;
import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import org.json.JSONObject;

import java.util.concurrent.TimeUnit;

/** Vérifie (~15 min) les nouveaux messages et avis des clients et prévient l'admin. */
public class Watcher extends Worker {
    private static final String CHANNEL = "inbox";

    public Watcher(@NonNull Context c, @NonNull WorkerParameters p) { super(c, p); }

    static void schedule(Context c) {
        PeriodicWorkRequest req = new PeriodicWorkRequest.Builder(Watcher.class, 15, TimeUnit.MINUTES)
                .setConstraints(new Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build();
        WorkManager.getInstance(c).enqueueUniquePeriodicWork("admin_inbox", ExistingPeriodicWorkPolicy.KEEP, req);
    }

    private static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences("watch", Context.MODE_PRIVATE);
    }

    /** Ce que l'admin a déjà vu (appelé quand le tableau de bord se charge). */
    static void remember(Context c, JSONObject stats) {
        prefs(c).edit().putInt("msg", stats.optInt("lastMessageId")).putInt("rev", stats.optInt("lastReviewId")).apply();
    }

    @NonNull @Override
    public Result doWork() {
        Context c = getApplicationContext();
        if (Api.token(c) == null) return Result.success();
        try {
            JSONObject s = Api.call(c, "GET", "/api/admin/stats", null);
            SharedPreferences p = prefs(c);
            boolean first = !p.contains("msg");
            int msg = s.optInt("lastMessageId"), rev = s.optInt("lastReviewId");
            if (!first) {
                if (msg > p.getInt("msg", 0) && s.optInt("unreadMessages") > 0) {
                    post(c, 1, c.getString(R.string.alert_messages, s.optInt("unreadMessages")), R.id.tab_messages);
                }
                if (rev > p.getInt("rev", 0)) post(c, 2, c.getString(R.string.alert_reviews), R.id.tab_more);
            }
            p.edit().putInt("msg", msg).putInt("rev", rev).apply();
            return Result.success();
        } catch (Exception e) {
            return Result.retry();
        }
    }

    private static void post(Context c, int id, String text, int tab) {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(c,
                Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return;
        if (Build.VERSION.SDK_INT >= 26) {
            c.getSystemService(NotificationManager.class).createNotificationChannel(
                    new NotificationChannel(CHANNEL, c.getString(R.string.channel_name), NotificationManager.IMPORTANCE_HIGH));
        }
        Intent i = new Intent(c, MainActivity.class).putExtra(MainActivity.EXTRA_TAB, tab)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pi = PendingIntent.getActivity(c, id, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        NotificationManagerCompat.from(c).notify(id, new NotificationCompat.Builder(c, CHANNEL)
                .setSmallIcon(R.drawable.ic_chat)
                .setColor(0xFFE0457B)
                .setContentTitle(c.getString(R.string.app_name))
                .setContentText(text)
                .setContentIntent(pi)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .build());
    }
}
