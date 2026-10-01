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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Charge les produits depuis l'API + garde une copie hors-ligne. */
public class ProductRepository {

    public interface Callback {
        /** error == null si chargé depuis Internet, sinon products = cache. */
        void onResult(List<Product> products, String error);
    }

    private static final ExecutorService EXEC = Executors.newSingleThreadExecutor();
    private final SharedPreferences prefs;
    private final Handler main = new Handler(Looper.getMainLooper());

    public ProductRepository(Context ctx) {
        prefs = ctx.getSharedPreferences("catalog_cache", Context.MODE_PRIVATE);
    }

    public List<Product> cached() {
        String json = prefs.getString("json", null);
        if (json == null) return new ArrayList<>();
        try {
            return parse(json);
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    public void load(Callback cb) {
        EXEC.execute(() -> {
            try {
                String json = get(Config.API_URL);
                List<Product> list = parse(json);
                prefs.edit().putString("json", json).apply();
                main.post(() -> cb.onResult(list, null));
            } catch (Exception e) {
                List<Product> list = cached();
                String msg = e.getMessage() == null ? "error" : e.getMessage();
                main.post(() -> cb.onResult(list, msg));
            }
        });
    }

    private static List<Product> parse(String json) throws Exception {
        JSONArray arr = new JSONObject(json).getJSONArray("products");
        List<Product> out = new ArrayList<>();
        for (int i = 0; i < arr.length(); i++) out.add(Product.from(arr.getJSONObject(i)));
        return out;
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
