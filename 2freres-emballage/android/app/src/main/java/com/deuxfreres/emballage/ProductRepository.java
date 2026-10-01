package com.deuxfreres.emballage;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Charge le catalogue depuis l'API + garde une copie hors-ligne. */
public class ProductRepository {

    public interface Callback {
        /** error == null si chargé depuis Internet, sinon catalog = cache. */
        void onResult(Catalog catalog, String error);
    }

    private static final ExecutorService EXEC = Executors.newSingleThreadExecutor();
    private final SharedPreferences prefs;
    private final Handler main = new Handler(Looper.getMainLooper());

    public ProductRepository(Context ctx) {
        prefs = ctx.getApplicationContext().getSharedPreferences("catalog_cache", Context.MODE_PRIVATE);
    }

    public Catalog cached() {
        String json = prefs.getString("json", null);
        if (json == null) return new Catalog();
        try {
            return parse(json);
        } catch (Exception e) {
            return new Catalog();
        }
    }

    public void load(Callback cb) {
        EXEC.execute(() -> {
            try {
                Catalog c = fetch();
                main.post(() -> cb.onResult(c, null));
            } catch (Exception e) {
                Catalog c = cached();
                String msg = e.getMessage() == null ? "error" : e.getMessage();
                main.post(() -> cb.onResult(c, msg));
            }
        });
    }

    /** Appel bloquant (à utiliser hors du thread principal). */
    public Catalog fetch() throws Exception {
        String json = get(Config.API_URL);
        Catalog c = parse(json);
        prefs.edit().putString("json", json).apply();
        return c;
    }

    private static Catalog parse(String json) throws Exception {
        JSONObject root = new JSONObject(json);
        Catalog c = new Catalog();
        JSONArray arr = root.getJSONArray("products");
        for (int i = 0; i < arr.length(); i++) c.products.add(Product.from(arr.getJSONObject(i)));
        JSONArray notifs = root.optJSONArray("notifications");
        if (notifs != null) {
            for (int i = 0; i < notifs.length(); i++) c.notifications.add(AppNotification.from(notifs.getJSONObject(i)));
        }
        return c;
    }

    private static String get(String url) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(15000);
        c.setReadTimeout(20000);
        c.setInstanceFollowRedirects(true); // Apps Script redirige vers googleusercontent
        try {
            int code = c.getResponseCode();
            if (code != 200) throw new IOException("HTTP " + code);
            try (BufferedReader r = new BufferedReader(
                    new InputStreamReader(c.getInputStream(), StandardCharsets.UTF_8))) {
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = r.readLine()) != null) sb.append(line);
                return sb.toString();
            }
        } finally {
            c.disconnect();
        }
    }
}
