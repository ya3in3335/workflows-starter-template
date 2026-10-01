package com.deuxfreres.emballage;

import android.content.Context;
import android.content.SharedPreferences;

/** Utilisateur connecté (jeton de session gardé sur le téléphone). */
final class Session {
    private Session() {}

    private static SharedPreferences prefs(Context ctx) {
        return ctx.getApplicationContext().getSharedPreferences("session", Context.MODE_PRIVATE);
    }

    static String token(Context ctx) { return prefs(ctx).getString("token", null); }
    static String username(Context ctx) { return prefs(ctx).getString("username", null); }
    static boolean loggedIn(Context ctx) { return token(ctx) != null; }

    static void save(Context ctx, String token, String username) {
        prefs(ctx).edit().putString("token", token).putString("username", username).apply();
    }

    static void clear(Context ctx) { prefs(ctx).edit().clear().apply(); }
}
