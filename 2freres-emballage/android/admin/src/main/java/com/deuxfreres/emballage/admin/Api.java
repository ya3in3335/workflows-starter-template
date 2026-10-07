package com.deuxfreres.emballage.admin;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Client de l'API admin (Cloudflare Worker) + session de l'administrateur. */
final class Api {
    static final String BASE = "https://flat-violet-af44.yacincianai.workers.dev";
    static final ExecutorService EXEC = Executors.newFixedThreadPool(3);
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    interface Ok { void run(JSONObject r); }
    interface Fail { void run(Exception e); }

    static final class ApiException extends Exception {
        final int status; final String code;
        ApiException(int status, String code) { super(code); this.status = status; this.code = code; }
    }

    private Api() {}

    /* ---------- session ---------- */
    private static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences("admin_session", Context.MODE_PRIVATE);
    }
    static String token(Context c) { return prefs(c).getString("token", null); }
    static String username(Context c) { return prefs(c).getString("username", ""); }
    static void save(Context c, String token, String user) { prefs(c).edit().putString("token", token).putString("username", user).apply(); }
    static void clear(Context c) { prefs(c).edit().remove("token").remove("username").apply(); }

    /* ---------- appels ---------- */
    static JSONObject call(Context c, String method, String path, JSONObject body) throws Exception {
        return send(method, path, body == null ? null : body.toString().getBytes(StandardCharsets.UTF_8),
                "application/json; charset=utf-8", token(c));
    }

    static JSONObject upload(Context c, byte[] jpeg) throws Exception {
        return send("POST", "/api/admin/images", jpeg, "image/jpeg", token(c));
    }

    static JSONObject send(String method, String path, byte[] body, String type, String token) throws Exception {
        HttpURLConnection h = (HttpURLConnection) new URL(BASE + path).openConnection();
        h.setConnectTimeout(15000);
        h.setReadTimeout(30000);
        h.setRequestMethod(method);
        h.setRequestProperty("Accept", "application/json");
        if (token != null) h.setRequestProperty("Authorization", "Bearer " + token);
        try {
            if (body != null) {
                h.setDoOutput(true);
                h.setRequestProperty("Content-Type", type);
                try (OutputStream os = h.getOutputStream()) { os.write(body); }
            }
            int code = h.getResponseCode();
            String text = read(code < 400 ? h.getInputStream() : h.getErrorStream());
            JSONObject o = text.isEmpty() ? new JSONObject() : new JSONObject(text);
            if (code >= 400) throw new ApiException(code, o.optString("error", "http_" + code));
            return o;
        } finally {
            h.disconnect();
        }
    }

    /** Appel en arrière-plan, résultat sur le thread principal. */
    static void async(Context c, String method, String path, JSONObject body, Ok ok, Fail fail) {
        Context app = c.getApplicationContext();
        EXEC.execute(() -> {
            try {
                JSONObject r = call(app, method, path, body);
                MAIN.post(() -> ok.run(r));
            } catch (Exception e) {
                MAIN.post(() -> { if (fail != null) fail.run(e); });
            }
        });
    }

    private static String read(InputStream in) throws Exception {
        if (in == null) return "";
        try (BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = r.readLine()) != null) sb.append(line);
            return sb.toString();
        }
    }
}
