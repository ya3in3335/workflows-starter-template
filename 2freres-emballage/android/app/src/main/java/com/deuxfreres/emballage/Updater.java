package com.deuxfreres.emballage;

import android.Manifest;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.format.Formatter;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * Mises à jour de l'application sans Play Store :
 * lit download/version.json, affiche une fenêtre « Mettre à jour », télécharge l'APK et lance l'installation.
 */
final class Updater {
    private static final String PREFS = "updates";
    private static final String KEY_SNOOZE_CODE = "snooze_code";
    private static final String KEY_SNOOZE_UNTIL = "snooze_until";
    private static final String KEY_NOTIFIED_CODE = "notified_code";
    private static final long SNOOZE_MS = 24L * 60 * 60 * 1000;   // « Plus tard » = on redemande demain
    private static final int NOTIF_ID = 9_000;

    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static boolean sheetShowing;
    private static File pendingInstall;          // APK prêt, en attente de l'autorisation « sources inconnues »

    private Updater() {}

    static final class Info {
        int code; String name, notes, apk; boolean mandatory; long size;
    }

    /* ======================= Réseau ======================= */

    static Info fetch() throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(Config.UPDATE_URL + "?t=" + System.currentTimeMillis() / 60000)
                .openConnection();
        c.setConnectTimeout(12000);
        c.setReadTimeout(15000);
        c.setUseCaches(false);
        try {
            if (c.getResponseCode() != 200) throw new IllegalStateException("http_" + c.getResponseCode());
            StringBuilder sb = new StringBuilder();
            try (BufferedReader r = new BufferedReader(new InputStreamReader(c.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null) sb.append(line).append('\n');
            }
            JSONObject o = new JSONObject(sb.toString());
            Info i = new Info();
            i.code = o.getInt("versionCode");
            i.name = o.optString("versionName", String.valueOf(i.code));
            i.notes = o.optString("notes", "");
            i.apk = o.optString("apk", Config.APK_URL);
            i.mandatory = o.optBoolean("mandatory", false);
            i.size = o.optLong("size", 0);
            return i;
        } finally {
            c.disconnect();
        }
    }

    static boolean isNewer(Info i) {
        return i != null && i.code > BuildConfig.VERSION_CODE;
    }

    /* ======================= Vérification ======================= */

    /** Au démarrage : vérifie en silence, affiche la fenêtre si une version plus récente existe (sauf « Plus tard » récent). */
    static void checkOnLaunch(AppCompatActivity a) {
        check(a, false);
    }

    /** Depuis le profil : affiche toujours un résultat. */
    static void checkManually(AppCompatActivity a) {
        check(a, true);
    }

    private static void check(AppCompatActivity a, boolean manual) {
        if (manual) Toast.makeText(a, R.string.update_checking, Toast.LENGTH_SHORT).show();
        ApiClient.EXEC.execute(() -> {
            Info info;
            try { info = fetch(); } catch (Exception e) { info = null; }
            Info fi = info;
            MAIN.post(() -> {
                if (a.isFinishing() || a.isDestroyed()) return;
                if (fi == null) {
                    if (manual) Toast.makeText(a, R.string.offline, Toast.LENGTH_SHORT).show();
                    return;
                }
                if (!isNewer(fi)) {
                    if (manual) Toast.makeText(a, a.getString(R.string.update_uptodate, BuildConfig.VERSION_NAME),
                            Toast.LENGTH_LONG).show();
                    return;
                }
                if (!manual && !fi.mandatory && snoozed(a, fi)) return;
                showSheet(a, fi);
            });
        });
    }

    private static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static boolean snoozed(Context c, Info i) {
        SharedPreferences p = prefs(c);
        return p.getInt(KEY_SNOOZE_CODE, -1) == i.code && System.currentTimeMillis() < p.getLong(KEY_SNOOZE_UNTIL, 0);
    }

    private static void snooze(Context c, Info i) {
        prefs(c).edit().putInt(KEY_SNOOZE_CODE, i.code)
                .putLong(KEY_SNOOZE_UNTIL, System.currentTimeMillis() + SNOOZE_MS).apply();
    }

    /* ======================= Fenêtre ======================= */

    static void showSheet(AppCompatActivity a, Info info) {
        if (sheetShowing) return;
        View v = LayoutInflater.from(a).inflate(R.layout.sheet_update, null);
        BottomSheetDialog d = new BottomSheetDialog(a);
        d.setContentView(v);
        d.setCancelable(!info.mandatory);
        d.getBehavior().setState(BottomSheetBehavior.STATE_EXPANDED);
        d.getBehavior().setSkipCollapsed(true);
        sheetShowing = true;
        d.setOnDismissListener(x -> sheetShowing = false);

        TextView subtitle = v.findViewById(R.id.updSubtitle);
        String sub = a.getString(R.string.update_subtitle, BuildConfig.VERSION_NAME, info.name);
        if (info.size > 0) sub += "  ·  " + Formatter.formatShortFileSize(a, info.size);
        subtitle.setText(sub);
        ((TextView) v.findViewById(R.id.updNotes)).setText(
                info.notes.isEmpty() ? a.getString(R.string.update_default_notes) : info.notes);

        ProgressBar bar = v.findViewById(R.id.updProgress);
        TextView status = v.findViewById(R.id.updStatus);
        MaterialButton now = v.findViewById(R.id.updNow);
        MaterialButton later = v.findViewById(R.id.updLater);
        View icon = v.findViewById(R.id.updIcon);

        later.setVisibility(info.mandatory ? View.GONE : View.VISIBLE);
        later.setOnClickListener(b -> { snooze(a, info); d.dismiss(); });
        now.setOnClickListener(b -> {
            now.setEnabled(false);
            later.setEnabled(false);
            d.setCancelable(false);
            bar.setVisibility(View.VISIBLE);
            status.setVisibility(View.VISIBLE);
            bar.setProgress(0);
            status.setText(a.getString(R.string.update_downloading, 0));
            pulse(icon);
            download(a, info, pct -> {
                bar.setProgress(pct);
                status.setText(a.getString(R.string.update_downloading, pct));
            }, file -> {
                icon.animate().cancel();
                if (file == null) {
                    status.setText(R.string.update_failed);
                    now.setEnabled(true);
                    now.setText(R.string.retry);
                    later.setEnabled(true);
                    d.setCancelable(!info.mandatory);
                    return;
                }
                bar.setProgress(100);
                status.setText(R.string.update_installing);
                now.setEnabled(true);
                now.setText(R.string.update_install);
                now.setOnClickListener(x -> install(a, file));
                install(a, file);
            });
        });

        d.show();
        Ui.stagger(v.findViewById(R.id.sheet), 120);
        Ui.bounce(icon);
    }

    private static void pulse(View v) {
        v.animate().scaleX(1.12f).scaleY(1.12f).setDuration(600).withEndAction(() ->
                v.animate().scaleX(1f).scaleY(1f).setDuration(600).withEndAction(() -> {
                    if (v.isAttachedToWindow() && v.getTag() == null) pulse(v);
                }).start()).start();
    }

    /* ======================= Téléchargement ======================= */

    interface Progress { void on(int pct); }
    interface Done { void on(File apk); }

    private static void download(AppCompatActivity a, Info info, Progress progress, Done done) {
        Context app = a.getApplicationContext();
        ApiClient.EXEC.execute(() -> {
            File dir = new File(app.getCacheDir(), "updates");
            //noinspection ResultOfMethodCallIgnored
            dir.mkdirs();
            File out = new File(dir, "update.apk");
            File ok = null;
            HttpURLConnection c = null;
            try {
                c = (HttpURLConnection) new URL(info.apk).openConnection();
                c.setInstanceFollowRedirects(true);
                c.setConnectTimeout(15000);
                c.setReadTimeout(30000);
                if (c.getResponseCode() != 200) throw new IllegalStateException("http_" + c.getResponseCode());
                long total = c.getContentLengthLong();
                if (total <= 0) total = info.size;
                try (InputStream in = c.getInputStream(); OutputStream os = new FileOutputStream(out)) {
                    byte[] buf = new byte[64 * 1024];
                    long read = 0;
                    int n, last = -1;
                    while ((n = in.read(buf)) > 0) {
                        os.write(buf, 0, n);
                        read += n;
                        if (total > 0) {
                            int pct = (int) Math.min(99, read * 100 / total);
                            if (pct != last) {
                                last = pct;
                                MAIN.post(() -> progress.on(pct));
                            }
                        }
                    }
                }
                if (isValidApk(app, out)) ok = out;
            } catch (Exception ignored) {
            } finally {
                if (c != null) c.disconnect();
            }
            File result = ok;
            MAIN.post(() -> done.on(result));
        });
    }

    /** L'APK téléchargé est bien notre application, en version plus récente. */
    @SuppressWarnings("deprecation")
    private static boolean isValidApk(Context c, File f) {
        PackageInfo pi = c.getPackageManager().getPackageArchiveInfo(f.getAbsolutePath(), 0);
        if (pi == null || !c.getPackageName().equals(pi.packageName)) return false;
        long code = Build.VERSION.SDK_INT >= 28 ? pi.getLongVersionCode() : pi.versionCode;
        return code > BuildConfig.VERSION_CODE;
    }

    /* ======================= Installation ======================= */

    private static void install(AppCompatActivity a, File apk) {
        if (Build.VERSION.SDK_INT >= 26 && !a.getPackageManager().canRequestPackageInstalls()) {
            pendingInstall = apk;
            Toast.makeText(a, R.string.update_allow_install, Toast.LENGTH_LONG).show();
            try {
                a.startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:" + a.getPackageName())));
            } catch (Exception e) {
                a.startActivity(new Intent(Settings.ACTION_SECURITY_SETTINGS));
            }
            return;
        }
        pendingInstall = null;
        Uri uri = FileProvider.getUriForFile(a, a.getPackageName() + ".files", apk);
        Intent i = new Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            a.startActivity(i);
        } catch (Exception e) {
            Toast.makeText(a, R.string.update_failed, Toast.LENGTH_LONG).show();
        }
    }

    /** À appeler dans onResume : reprend l'installation après l'autorisation « sources inconnues ». */
    static void resumePendingInstall(AppCompatActivity a) {
        File f = pendingInstall;
        if (f == null || !f.exists()) return;
        if (Build.VERSION.SDK_INT >= 26 && !a.getPackageManager().canRequestPackageInstalls()) return;
        install(a, f);
    }

    /* ======================= Notification (arrière-plan) ======================= */

    /** Appelé par NotifyWorker : prévient une seule fois par version, même si l'application est fermée. */
    static void notifyIfNew(Context ctx) {
        SharedPreferences p = prefs(ctx);
        long now = System.currentTimeMillis();
        if (now - p.getLong("last_bg_check", 0) < 3L * 60 * 60 * 1000) return;   // au plus toutes les 3 h
        p.edit().putLong("last_bg_check", now).apply();
        Info i;
        try { i = fetch(); } catch (Exception e) { return; }
        if (!isNewer(i)) return;
        if (p.getInt(KEY_NOTIFIED_CODE, -1) >= i.code) return;
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(ctx,
                Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return;
        if (!Notifier.enabled(ctx)) return;
        p.edit().putInt(KEY_NOTIFIED_CODE, i.code).apply();
        Notifier.createChannel(ctx);

        Intent open = new Intent(ctx, MainActivity.class)
                .putExtra(Notifier.EXTRA_OPEN, "update")
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pi = PendingIntent.getActivity(ctx, NOTIF_ID, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        NotificationManagerCompat.from(ctx).notify(NOTIF_ID, new NotificationCompat.Builder(ctx, Notifier.CHANNEL)
                .setSmallIcon(R.drawable.ic_stat_box)
                .setColor(ContextCompat.getColor(ctx, R.color.card_pink))
                .setContentTitle(ctx.getString(R.string.update_notif_title))
                .setContentText(ctx.getString(R.string.update_notif_text, i.name))
                .setContentIntent(pi)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .build());
    }
}
