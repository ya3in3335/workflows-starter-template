package com.deuxfreres.emballage.admin;

import android.content.Intent;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.credentials.Credential;
import androidx.credentials.CredentialManager;
import androidx.credentials.CredentialManagerCallback;
import androidx.credentials.CustomCredential;
import androidx.credentials.GetCredentialRequest;
import androidx.credentials.GetCredentialResponse;
import androidx.credentials.exceptions.GetCredentialCancellationException;
import androidx.credentials.exceptions.GetCredentialException;
import androidx.credentials.exceptions.NoCredentialException;

import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption;
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential;

import org.json.JSONObject;

/**
 * Connexion admin par Google uniquement (aucun mot de passe).
 * Le Worker vérifie le Google ID token et n'accepte que les adresses des comptes admin.
 */
public class LoginActivity extends AppCompatActivity {

    /** Client OAuth « Web » du projet Firebase : l'audience du jeton vérifiée par le Worker. */
    static final String WEB_CLIENT_ID = "956106850630-19isn3en66ig2h9sickrvsvno3q207o5.apps.googleusercontent.com";

    private TextView error;
    private View progress, submit;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (Api.token(this) != null) { openMain(); return; }
        setContentView(R.layout.activity_login);
        error = findViewById(R.id.error);
        progress = findViewById(R.id.progress);
        submit = findViewById(R.id.submit);
        submit.setOnClickListener(v -> signIn());
    }

    private void signIn() {
        busy(true);
        GetCredentialRequest request = new GetCredentialRequest.Builder()
                .addCredentialOption(new GetSignInWithGoogleOption.Builder(WEB_CLIENT_ID).build())
                .build();
        CredentialManager.create(this).getCredentialAsync(this, request, new CancellationSignal(),
                ContextCompat.getMainExecutor(this),
                new CredentialManagerCallback<GetCredentialResponse, GetCredentialException>() {
                    @Override public void onResult(GetCredentialResponse r) { onCredential(r.getCredential()); }
                    @Override public void onError(@NonNull GetCredentialException e) {
                        busy(false);
                        if (e instanceof GetCredentialCancellationException) return;
                        showError(e instanceof NoCredentialException ? R.string.google_none : R.string.google_failed);
                    }
                });
    }

    private void onCredential(Credential c) {
        if (!(c instanceof CustomCredential)
                || !GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL.equals(c.getType())) {
            busy(false);
            showError(R.string.google_failed);
            return;
        }
        String idToken = GoogleIdTokenCredential.createFrom(c.getData()).getIdToken();
        Api.EXEC.execute(() -> {
            try {
                byte[] body = new JSONObject().put("idToken", idToken).toString().getBytes("UTF-8");
                JSONObject r = Api.send("POST", "/api/admin/google-login", body, "application/json", null);
                JSONObject u = r.getJSONObject("user");
                if (!"admin".equals(u.optString("role"))) throw new Api.ApiException(403, "not_admin");
                Api.save(this, r.getString("token"), u.getString("username"));
                runOnUiThread(this::openMain);
            } catch (Exception e) {
                runOnUiThread(() -> {
                    busy(false);
                    int msg = R.string.offline;
                    if (e instanceof Api.ApiException) {
                        String code = ((Api.ApiException) e).code;
                        msg = "not_admin".equals(code) ? R.string.not_admin : R.string.google_failed;
                    }
                    showError(msg);
                });
            }
        });
    }

    private void busy(boolean b) {
        error.setVisibility(View.GONE);
        progress.setVisibility(b ? View.VISIBLE : View.GONE);
        submit.setEnabled(!b);
    }

    private void showError(int msg) {
        error.setText(msg);
        error.setVisibility(View.VISIBLE);
    }

    private void openMain() {
        startActivity(new Intent(this, MainActivity.class));
        finish();
    }
}
