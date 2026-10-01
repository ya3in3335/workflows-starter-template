package com.deuxfreres.emballage;

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

/** Appels à l'API (Cloudflare Worker + D1) : comptes, avis, « j'aime ». */
final class ApiClient {
    static final ExecutorService EXEC = Executors.newFixedThreadPool(2);

    static final class ApiException extends Exception {
        final int status; final String code;
        ApiException(int status, String code) { super(code); this.status = status; this.code = code; }
    }

    private ApiClient() {}

    static JSONObject call(String method, String path, JSONObject body, String token) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(Config.API_BASE + path).openConnection();
        c.setConnectTimeout(15000);
        c.setReadTimeout(20000);
        c.setRequestMethod(method);
        c.setRequestProperty("Accept", "application/json");
        if (token != null) c.setRequestProperty("Authorization", "Bearer " + token);
        try {
            if (body != null) {
                c.setDoOutput(true);
                c.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                try (OutputStream os = c.getOutputStream()) {
                    os.write(body.toString().getBytes(StandardCharsets.UTF_8));
                }
            }
            int code = c.getResponseCode();
            InputStream in = code < 400 ? c.getInputStream() : c.getErrorStream();
            String text = read(in);
            JSONObject o = text.isEmpty() ? new JSONObject() : new JSONObject(text);
            if (code >= 400) throw new ApiException(code, o.optString("error", "http_" + code));
            return o;
        } finally {
            c.disconnect();
        }
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
