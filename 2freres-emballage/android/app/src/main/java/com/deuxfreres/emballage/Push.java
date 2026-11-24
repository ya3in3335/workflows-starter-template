package com.deuxfreres.emballage;

import android.content.Context;

import com.google.firebase.messaging.FirebaseMessaging;

import org.json.JSONObject;

import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Notifications instantanées (Firebase Cloud Messaging).
 * Le téléphone s'abonne au sujet « all » (annonces à tous) et envoie son jeton au serveur
 * avec le compte connecté (messages du magasin, réponses aux avis…).
 */
final class Push {
    private Push() {}

    /** Écrans ouverts qui veulent être prévenus tout de suite (chat, cloche…). */
    interface Listener { void onPush(Map<String, String> data); }
    static final CopyOnWriteArrayList<Listener> LISTENERS = new CopyOnWriteArrayList<>();

    /** À appeler au démarrage, après connexion et après déconnexion. */
    static void register(Context ctx) {
        Context app = ctx.getApplicationContext();
        try {
            FirebaseMessaging fm = FirebaseMessaging.getInstance();
            fm.subscribeToTopic("all");
            fm.getToken().addOnSuccessListener(t -> send(app, t));
        } catch (Exception ignored) {
        }
    }

    static void send(Context app, String token) {
        if (token == null) return;
        ApiClient.EXEC.execute(() -> {
            try {
                ApiClient.call("POST", "/api/push/register",
                        new JSONObject().put("token", token).put("app", "client"), Session.token(app));
            } catch (Exception ignored) {
            }
        });
    }

    static void dispatch(Map<String, String> data) {
        for (Listener l : LISTENERS) l.onPush(data);
    }
}
