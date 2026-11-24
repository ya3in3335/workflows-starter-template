package com.deuxfreres.emballage.admin;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import org.json.JSONObject;

import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/** Push instantanés pour l'admin : nouveaux messages des clients et nouveaux avis. */
public class AdminPush extends FirebaseMessagingService {
    private static final String CHANNEL = "inbox";

    interface Listener { void onPush(Map<String, String> data); }
    static final CopyOnWriteArrayList<Listener> LISTENERS = new CopyOnWriteArrayList<>();

    /** Envoie le jeton du téléphone au serveur (compte admin connecté). */
    static void register(Context ctx) {
        Context app = ctx.getApplicationContext();
        if (Api.token(app) == null) return;
        try {
            FirebaseMessaging.getInstance().getToken().addOnSuccessListener(t -> send(app, t));
        } catch (Exception ignored) {
        }
    }

    private static void send(Context app, String token) {
        if (token == null || Api.token(app) == null) return;
        Api.EXEC.execute(() -> {
            try {
                Api.call(app, "POST", "/api/push/register", new JSONObject().put("token", token).put("app", "admin"));
            } catch (Exception ignored) {
            }
        });
    }

    /** À la déconnexion : le téléphone ne doit plus recevoir les messages des clients. */
    static void unregister() {
        try { FirebaseMessaging.getInstance().deleteToken(); } catch (Exception ignored) {}
    }

    @Override public void onNewToken(@NonNull String token) { send(getApplicationContext(), token); }

    @Override
    public void onMessageReceived(@NonNull RemoteMessage msg) {
        Map<String, String> d = msg.getData();
        String type = d.get("type");
        Intent open;
        int id;
        if ("support".equals(type)) {
            int uid = parse(d.get("userId"));
            id = 10_000 + uid;   // une notification par client, mise à jour à chaque message
            open = new Intent(this, ChatActivity.class)
                    .putExtra(ChatActivity.EXTRA_USER, uid)
                    .putExtra(ChatActivity.EXTRA_NAME, d.get("username"));
        } else if ("review".equals(type)) {
            id = 20_000 + parse(d.get("id"));
            open = new Intent(this, ReviewsActivity.class);
        } else {
            return;
        }
        show(this, id, d.get("title"), d.get("body"), open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        new Handler(Looper.getMainLooper()).post(() -> { for (Listener l : LISTENERS) l.onPush(d); });
    }

    private static void show(Context c, int id, String title, String body, Intent open) {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(c,
                Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return;
        if (Build.VERSION.SDK_INT >= 26) {
            c.getSystemService(NotificationManager.class).createNotificationChannel(
                    new NotificationChannel(CHANNEL, c.getString(R.string.channel_name), NotificationManager.IMPORTANCE_HIGH));
        }
        PendingIntent pi = PendingIntent.getActivity(c, id, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        String text = body == null ? "" : body;
        NotificationManagerCompat.from(c).notify(id, new NotificationCompat.Builder(c, CHANNEL)
                .setSmallIcon(R.drawable.ic_chat)
                .setColor(0xFFC9A96E)
                .setContentTitle(title == null ? c.getString(R.string.app_name) : title)
                .setContentText(text)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(text))
                .setContentIntent(pi)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setDefaults(NotificationCompat.DEFAULT_ALL)
                .build());
    }

    private static int parse(String s) {
        try { return s == null ? 0 : Integer.parseInt(s); } catch (NumberFormatException e) { return 0; }
    }
}
