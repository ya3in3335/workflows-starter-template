package com.deuxfreres.emballage;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;

import org.json.JSONObject;

/** Connexion / création de compte : pseudo + mot de passe seulement. */
public class LoginActivity extends AppCompatActivity {

    private boolean registerMode;
    private EditText username, password;
    private TextView error;
    private MaterialButton submit, toggle;
    private View progress;
    private MaterialToolbar toolbar;
    private final Handler main = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);
        toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());
        username = findViewById(R.id.username);
        password = findViewById(R.id.password);
        error = findViewById(R.id.error);
        submit = findViewById(R.id.submit);
        toggle = findViewById(R.id.toggle);
        progress = findViewById(R.id.progress);
        submit.setOnClickListener(v -> send());
        toggle.setOnClickListener(v -> { registerMode = !registerMode; render(); });
        render();
    }

    private void render() {
        toolbar.setTitle(registerMode ? R.string.create_account : R.string.login);
        submit.setText(registerMode ? R.string.create_account : R.string.login);
        toggle.setText(registerMode ? R.string.have_account : R.string.no_account);
        error.setVisibility(View.GONE);
    }

    private void send() {
        String u = username.getText().toString().trim();
        String p = password.getText().toString();
        if (u.length() < 3) { showError(getString(R.string.err_bad_username)); return; }
        if (p.length() < 6) { showError(getString(R.string.err_bad_password)); return; }
        busy(true);
        String path = registerMode ? "/api/register" : "/api/login";
        ApiClient.EXEC.execute(() -> {
            try {
                JSONObject body = new JSONObject().put("username", u).put("password", p);
                JSONObject r = ApiClient.call("POST", path, body, null);
                Session.save(this, r.getString("token"), r.getJSONObject("user").getString("username"));
                main.post(() -> {
                    setResult(RESULT_OK);
                    finish();
                });
            } catch (ApiClient.ApiException e) {
                main.post(() -> { busy(false); showError(message(e.code)); });
            } catch (Exception e) {
                main.post(() -> { busy(false); showError(getString(R.string.offline)); });
            }
        });
    }

    private String message(String code) {
        switch (code) {
            case "username_taken": return getString(R.string.err_taken);
            case "bad_username": return getString(R.string.err_bad_username);
            case "bad_password": return getString(R.string.err_bad_password);
            case "bad_credentials": return getString(R.string.err_credentials);
            case "banned": return getString(R.string.err_banned);
            default: return getString(R.string.err_generic);
        }
    }

    private void busy(boolean b) {
        progress.setVisibility(b ? View.VISIBLE : View.GONE);
        submit.setEnabled(!b);
        toggle.setEnabled(!b);
    }

    private void showError(String s) {
        error.setText(s);
        error.setVisibility(View.VISIBLE);
    }
}
