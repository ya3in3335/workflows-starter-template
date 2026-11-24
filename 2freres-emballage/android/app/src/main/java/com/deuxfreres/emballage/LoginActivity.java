package com.deuxfreres.emballage;

import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.Handler;
import android.os.Looper;
import android.util.Patterns;
import android.view.View;
import android.widget.EditText;
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
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.FirebaseNetworkException;
import com.google.firebase.FirebaseTooManyRequestsException;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException;
import com.google.firebase.auth.FirebaseAuthInvalidUserException;
import com.google.firebase.auth.FirebaseAuthUserCollisionException;
import com.google.firebase.auth.FirebaseAuthWeakPasswordException;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;
import com.google.firebase.auth.UserProfileChangeRequest;

import org.json.JSONObject;

/**
 * Connexion sécurisée :
 *  - « Continuer avec Google » (Credential Manager → Firebase Auth) ;
 *  - e-mail + mot de passe (Firebase Auth), adresse confirmée obligatoire, « Mot de passe oublié » ;
 *  - ancien compte « nom d'utilisateur + mot de passe » (API du magasin), pour ne perdre personne.
 * Après Firebase, le Worker vérifie l'ID token et renvoie la session habituelle de l'application.
 */
public class LoginActivity extends AppCompatActivity {

    private enum Mode { LOGIN, REGISTER, LEGACY }

    private Mode mode = Mode.LOGIN;
    private FirebaseAuth fa;
    private EditText name, email, username, password;
    private View nameBox, emailBox, usernameBox, google, orRow, forgot, resend, progress;
    private TextView error, info, legacy;
    private MaterialButton submit, toggle;
    private MaterialToolbar toolbar;
    private final Handler main = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Ui.edgeToEdge(this);
        setContentView(R.layout.activity_login);
        Ui.padTop(findViewById(R.id.headerBox));
        Ui.padBottom(findViewById(R.id.formScroll), true);
        fa = FirebaseAuth.getInstance();
        fa.setLanguageCode("fr");

        toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());
        name = findViewById(R.id.name);
        email = findViewById(R.id.email);
        username = findViewById(R.id.username);
        password = findViewById(R.id.password);
        nameBox = findViewById(R.id.nameBox);
        emailBox = findViewById(R.id.emailBox);
        usernameBox = findViewById(R.id.usernameBox);
        google = findViewById(R.id.google);
        orRow = findViewById(R.id.orRow);
        forgot = findViewById(R.id.forgot);
        resend = findViewById(R.id.resend);
        error = findViewById(R.id.error);
        info = findViewById(R.id.info);
        submit = findViewById(R.id.submit);
        toggle = findViewById(R.id.toggle);
        progress = findViewById(R.id.progress);
        legacy = findViewById(R.id.legacy);

        google.setOnClickListener(v -> signInWithGoogle());
        submit.setOnClickListener(v -> submit());
        forgot.setOnClickListener(v -> resetPassword());
        resend.setOnClickListener(v -> resendVerification());
        toggle.setOnClickListener(v -> setMode(mode == Mode.REGISTER ? Mode.LOGIN : Mode.REGISTER));
        legacy.setOnClickListener(v -> setMode(mode == Mode.LEGACY ? Mode.LOGIN : Mode.LEGACY));
        setMode(Mode.LOGIN);

        Ui.stagger(findViewById(R.id.form), 120);
        Ui.bounce(findViewById(R.id.loginLogo));
    }

    private void setMode(Mode m) {
        mode = m;
        boolean legacyMode = m == Mode.LEGACY;
        toolbar.setTitle(legacyMode ? R.string.legacy_title : m == Mode.REGISTER ? R.string.create_account : R.string.login);
        submit.setText(m == Mode.REGISTER ? R.string.create_account : R.string.login);
        toggle.setText(m == Mode.REGISTER ? R.string.have_account : R.string.no_account);
        toggle.setVisibility(legacyMode ? View.GONE : View.VISIBLE);
        nameBox.setVisibility(m == Mode.REGISTER ? View.VISIBLE : View.GONE);
        emailBox.setVisibility(legacyMode ? View.GONE : View.VISIBLE);
        usernameBox.setVisibility(legacyMode ? View.VISIBLE : View.GONE);
        google.setVisibility(legacyMode ? View.GONE : View.VISIBLE);
        orRow.setVisibility(legacyMode ? View.GONE : View.VISIBLE);
        forgot.setVisibility(m == Mode.LOGIN ? View.VISIBLE : View.GONE);
        legacy.setText(legacyMode ? R.string.back_to_email : R.string.legacy_account);
        resend.setVisibility(View.GONE);
        error.setVisibility(View.GONE);
    }

    /* ======================= Google ======================= */

    private void signInWithGoogle() {
        clearMessages();
        busy(true);
        GetSignInWithGoogleOption option =
                new GetSignInWithGoogleOption.Builder(getString(R.string.default_web_client_id)).build();
        GetCredentialRequest request = new GetCredentialRequest.Builder().addCredentialOption(option).build();
        CredentialManager.create(this).getCredentialAsync(this, request, new CancellationSignal(),
                ContextCompat.getMainExecutor(this),
                new CredentialManagerCallback<GetCredentialResponse, GetCredentialException>() {
                    @Override public void onResult(GetCredentialResponse r) { onGoogleCredential(r.getCredential()); }
                    @Override public void onError(@NonNull GetCredentialException e) {
                        busy(false);
                        if (e instanceof GetCredentialCancellationException) return;   // l'utilisateur a fermé
                        showError(getString(e instanceof NoCredentialException ? R.string.err_google_none : R.string.err_google));
                    }
                });
    }

    private void onGoogleCredential(Credential c) {
        if (!(c instanceof CustomCredential)
                || !GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL.equals(c.getType())) {
            busy(false);
            showError(getString(R.string.err_google));
            return;
        }
        try {
            GoogleIdTokenCredential g = GoogleIdTokenCredential.createFrom(c.getData());
            fa.signInWithCredential(GoogleAuthProvider.getCredential(g.getIdToken(), null))
                    .addOnSuccessListener(r -> finishWithFirebase(r.getUser(), null))
                    .addOnFailureListener(e -> { busy(false); showError(firebaseMessage(e)); });
        } catch (Exception e) {
            busy(false);
            showError(getString(R.string.err_google));
        }
    }

    /* ======================= E-mail ======================= */

    private void submit() {
        clearMessages();
        if (mode == Mode.LEGACY) { legacyLogin(); return; }
        String mail = text(email);
        String pw = password.getText().toString();
        if (!Patterns.EMAIL_ADDRESS.matcher(mail).matches()) { showError(getString(R.string.err_email)); return; }
        if (pw.length() < 8) { showError(getString(R.string.err_password_short)); return; }
        busy(true);
        if (mode == Mode.REGISTER) {
            String display = text(name);
            if (display.length() < 3) { busy(false); showError(getString(R.string.err_name)); return; }
            fa.createUserWithEmailAndPassword(mail, pw)
                    .addOnSuccessListener(r -> {
                        FirebaseUser u = r.getUser();
                        if (u == null) { busy(false); showError(getString(R.string.err_generic)); return; }
                        u.updateProfile(new UserProfileChangeRequest.Builder().setDisplayName(display).build())
                                .continueWithTask(t -> u.sendEmailVerification())
                                .addOnCompleteListener(t -> {
                                    fa.signOut();
                                    busy(false);
                                    setMode(Mode.LOGIN);
                                    password.setText("");
                                    showInfo(getString(R.string.verify_sent, mail));
                                });
                    })
                    .addOnFailureListener(e -> { busy(false); showError(firebaseMessage(e)); });
        } else {
            fa.signInWithEmailAndPassword(mail, pw)
                    .addOnSuccessListener(r -> {
                        FirebaseUser u = r.getUser();
                        if (u != null && !u.isEmailVerified()) {
                            // Adresse pas encore confirmée : on garde l'utilisateur pour « Renvoyer », sans session.
                            busy(false);
                            showError(getString(R.string.err_not_verified));
                            resend.setVisibility(View.VISIBLE);
                            return;
                        }
                        finishWithFirebase(u, null);
                    })
                    .addOnFailureListener(e -> { busy(false); showError(firebaseMessage(e)); });
        }
    }

    private void resendVerification() {
        FirebaseUser u = fa.getCurrentUser();
        if (u == null) { showError(getString(R.string.err_not_verified)); return; }
        busy(true);
        u.sendEmailVerification().addOnCompleteListener(t -> {
            busy(false);
            fa.signOut();
            resend.setVisibility(View.GONE);
            clearMessages();
            if (t.isSuccessful()) showInfo(getString(R.string.verify_sent, u.getEmail()));
            else showError(firebaseMessage(t.getException()));
        });
    }

    private void resetPassword() {
        clearMessages();
        String mail = text(email);
        if (!Patterns.EMAIL_ADDRESS.matcher(mail).matches()) { showInfo(getString(R.string.reset_need_email)); return; }
        busy(true);
        // Même message que le compte existe ou non (pas d'indice pour un attaquant).
        fa.sendPasswordResetEmail(mail).addOnCompleteListener(t -> {
            busy(false);
            Exception e = t.getException();
            if (e instanceof FirebaseNetworkException || e instanceof FirebaseTooManyRequestsException) showError(firebaseMessage(e));
            else showInfo(getString(R.string.reset_sent, mail));
        });
    }

    /* ======================= Session du magasin ======================= */

    /** Échange l'ID token Firebase contre la session de l'application (vérifiée côté Worker). */
    private void finishWithFirebase(FirebaseUser u, String displayName) {
        if (u == null) { busy(false); showError(getString(R.string.err_generic)); return; }
        u.getIdToken(true).addOnSuccessListener(t -> ApiClient.EXEC.execute(() -> {
            try {
                JSONObject body = new JSONObject().put("idToken", t.getToken());
                String dn = displayName != null ? displayName : u.getDisplayName();
                if (dn != null) body.put("displayName", dn);
                JSONObject r = ApiClient.call("POST", "/api/firebase-login", body, null);
                Session.save(this, r.getString("token"), r.getJSONObject("user").getString("username"));
                main.post(() -> { setResult(RESULT_OK); finish(); });
            } catch (ApiClient.ApiException e) {
                fa.signOut();
                main.post(() -> { busy(false); showError(message(e.code)); });
            } catch (Exception e) {
                fa.signOut();
                main.post(() -> { busy(false); showError(getString(R.string.offline)); });
            }
        })).addOnFailureListener(e -> { busy(false); showError(firebaseMessage(e)); });
    }

    /** Ancien compte : nom d'utilisateur + mot de passe (API du magasin). */
    private void legacyLogin() {
        String u = text(username);
        String p = password.getText().toString();
        if (u.length() < 3) { showError(getString(R.string.err_bad_username)); return; }
        if (p.length() < 6) { showError(getString(R.string.err_bad_password)); return; }
        busy(true);
        ApiClient.EXEC.execute(() -> {
            try {
                JSONObject body = new JSONObject().put("username", u).put("password", p);
                JSONObject r = ApiClient.call("POST", "/api/login", body, null);
                Session.save(this, r.getString("token"), r.getJSONObject("user").getString("username"));
                main.post(() -> { setResult(RESULT_OK); finish(); });
            } catch (ApiClient.ApiException e) {
                main.post(() -> { busy(false); showError(message(e.code)); });
            } catch (Exception e) {
                main.post(() -> { busy(false); showError(getString(R.string.offline)); });
            }
        });
    }

    /* ======================= Messages ======================= */

    private String firebaseMessage(Exception e) {
        if (e instanceof FirebaseNetworkException) return getString(R.string.offline);
        if (e instanceof FirebaseTooManyRequestsException) return getString(R.string.err_too_many);
        if (e instanceof FirebaseAuthUserCollisionException) return getString(R.string.err_email_used);
        if (e instanceof FirebaseAuthWeakPasswordException) return getString(R.string.err_weak_password);
        if (e instanceof FirebaseAuthInvalidUserException) {
            String code = ((FirebaseAuthInvalidUserException) e).getErrorCode();
            if ("ERROR_USER_DISABLED".equals(code)) return getString(R.string.err_banned);
            return getString(R.string.err_credentials);
        }
        if (e instanceof FirebaseAuthInvalidCredentialsException) return getString(R.string.err_credentials);
        return getString(R.string.err_generic);
    }

    private String message(String code) {
        switch (code) {
            case "bad_username": return getString(R.string.err_bad_username);
            case "bad_password": return getString(R.string.err_bad_password);
            case "bad_credentials": return getString(R.string.err_credentials);
            case "banned": return getString(R.string.err_banned);
            case "email_not_verified": return getString(R.string.err_not_verified);
            default: return getString(R.string.err_generic);
        }
    }

    private static String text(EditText e) { return e.getText().toString().trim(); }

    private void busy(boolean b) {
        progress.setVisibility(b ? View.VISIBLE : View.GONE);
        submit.setEnabled(!b);
        toggle.setEnabled(!b);
        google.setEnabled(!b);
        legacy.setEnabled(!b);
    }

    private void clearMessages() {
        error.setVisibility(View.GONE);
        info.setVisibility(View.GONE);
    }

    private void showInfo(String s) {
        info.setText(s);
        info.setVisibility(View.VISIBLE);
        Ui.rise(info, 0);
    }

    private void showError(String s) {
        error.setText(s);
        error.setVisibility(View.VISIBLE);
        error.setTranslationX(0);
        android.animation.ObjectAnimator.ofFloat(error, "translationX", 0, 8, -6, 4, -2, 0)
                .setDuration(420).start();
    }
}
