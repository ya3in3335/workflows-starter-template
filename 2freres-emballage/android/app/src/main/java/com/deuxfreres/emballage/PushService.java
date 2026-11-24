package com.deuxfreres.emballage;

import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import java.util.Map;

/** Reçoit les push du serveur (messages « data ») et affiche la notification tout de suite. */
public class PushService extends FirebaseMessagingService {

    @Override
    public void onNewToken(@NonNull String token) {
        Push.send(getApplicationContext(), token);
    }

    @Override
    public void onMessageReceived(@NonNull RemoteMessage msg) {
        Map<String, String> d = msg.getData();
        if (!"notif".equals(d.get("type"))) return;
        long id = parse(d.get("id"));
        String link = d.get("link") == null ? "" : d.get("link");
        long productId = parse(d.get("productId"));
        String open = "support".equals(link) ? "support" : productId > 0 ? String.valueOf(productId) : "notifications";
        // Évite que la vérification périodique ré-affiche la même notification
        Notifier.markNotified(this, id);
        Notifier.show(this, (int) id, d.get("title"), d.get("body"), open);
        new Handler(Looper.getMainLooper()).post(() -> Push.dispatch(d));
    }

    private static long parse(String s) {
        try { return s == null ? 0 : Long.parseLong(s); } catch (NumberFormatException e) { return 0; }
    }
}
