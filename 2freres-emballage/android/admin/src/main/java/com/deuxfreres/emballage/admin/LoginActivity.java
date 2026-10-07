package com.deuxfreres.emballage.admin;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONObject;

public class LoginActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (Api.token(this) != null) { openMain(); return; }
        setContentView(R.layout.activity_login);
        EditText user = findViewById(R.id.username);
        EditText pass = findViewById(R.id.password);
        TextView error = findViewById(R.id.error);
        View progress = findViewById(R.id.progress);
        View submit = findViewById(R.id.submit);
        submit.setOnClickListener(v -> {
            error.setVisibility(View.GONE);
            progress.setVisibility(View.VISIBLE);
            submit.setEnabled(false);
            JSONObject body = new JSONObject();
            try {
                body.put("username", user.getText().toString().trim()).put("password", pass.getText().toString());
            } catch (Exception ignored) {}
            Api.EXEC.execute(() -> {
                try {
                    JSONObject r = Api.send("POST", "/api/login", body.toString().getBytes(), "application/json", null);
                    JSONObject u = r.getJSONObject("user");
                    if (!"admin".equals(u.optString("role"))) throw new Api.ApiException(403, "not_admin");
                    Api.save(this, r.getString("token"), u.getString("username"));
                    runOnUiThread(this::openMain);
                } catch (Exception e) {
                    runOnUiThread(() -> {
                        progress.setVisibility(View.GONE);
                        submit.setEnabled(true);
                        int msg = R.string.offline;
                        if (e instanceof Api.ApiException) {
                            String code = ((Api.ApiException) e).code;
                            msg = "not_admin".equals(code) ? R.string.not_admin
                                    : "bad_credentials".equals(code) ? R.string.bad_credentials : R.string.error;
                        }
                        error.setText(msg);
                        error.setVisibility(View.VISIBLE);
                    });
                }
            });
        });
    }

    private void openMain() {
        startActivity(new Intent(this, MainActivity.class));
        finish();
    }
}
