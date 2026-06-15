package com.example.aplikasimonitorsuhu;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.tasks.Task;
import com.google.android.material.checkbox.MaterialCheckBox;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.android.recaptcha.Recaptcha;
import com.google.android.recaptcha.RecaptchaAction;
import com.google.android.recaptcha.RecaptchaTasksClient;

import java.util.UUID;

public class LoginActivity extends AppCompatActivity {

    private static final String TAG = "LoginActivity";
    private static final int RC_SIGN_IN = 9001;
    private static final String RECAPTCHA_SITE_KEY = "6LdkmQwtAAAAAOkk8XDhnRqIhX1diJXiS_f06tQi";

    private TextInputLayout tilEmail, tilPassword;
    private TextInputEditText etEmail, etPassword;
    private MaterialCheckBox cbRemember;
    private TextView tvForgotPassword;
    
    private FirebaseAuth mAuth;
    private GoogleSignInClient mGoogleSignInClient;
    private RecaptchaTasksClient recaptchaTasksClient;
    
    private final String DB_URL = "https://tofumonitor-default-rtdb.asia-southeast1.firebasedatabase.app";

    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final long LOCKOUT_DURATION_MS = 5 * 60 * 1000; 
    private SharedPreferences securityPrefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        getWindow().setFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE, android.view.WindowManager.LayoutParams.FLAG_SECURE);

        if (SecurityUtils.isDeviceRooted() || SecurityUtils.isEmulator()) {
            finishAffinity();
            System.exit(0);
            return;
        }

        securityPrefs = getSharedPreferences("security_prefs", MODE_PRIVATE);

        mAuth = FirebaseAuth.getInstance();
        
        if (mAuth.getCurrentUser() != null && hasValidLocalSession()) {
            goToMainActivity(null, false);
            return;
        }

        setContentView(R.layout.activity_login);
        initializeRecaptcha();

        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(getString(R.string.default_web_client_id)) 
                .requestEmail()
                .build();

        mGoogleSignInClient = GoogleSignIn.getClient(this, gso);

        tilEmail = findViewById(R.id.til_email);
        tilPassword = findViewById(R.id.til_password);
        etEmail = (TextInputEditText) tilEmail.getEditText();
        etPassword = (TextInputEditText) tilPassword.getEditText();
        cbRemember = findViewById(R.id.cb_remember);
        tvForgotPassword = findViewById(R.id.tv_forgot_password);
        
        findViewById(R.id.btn_login).setOnClickListener(v -> {
            String email = etEmail.getText().toString().trim();
            String password = etPassword.getText().toString().trim();
            if (TextUtils.isEmpty(email)) { tilEmail.setError("Email wajib diisi"); return; }
            if (TextUtils.isEmpty(password)) { tilPassword.setError("Password wajib diisi"); return; }
            showCaptchaDialog(this::performEmailLogin);
        });
        
        findViewById(R.id.btn_google_signin).setOnClickListener(v -> showCaptchaDialog(this::signInWithGoogle));

        if (tvForgotPassword != null) {
            tvForgotPassword.setOnClickListener(v -> showForgotPasswordDialog());
        }
    }

    private void initializeRecaptcha() {
        Recaptcha.getTasksClient(getApplication(), RECAPTCHA_SITE_KEY)
                .addOnSuccessListener(this, client -> this.recaptchaTasksClient = client)
                .addOnFailureListener(this, e -> Log.e(TAG, "reCAPTCHA Error: " + e.getMessage()));
    }

    private void showCaptchaDialog(Runnable onVerifiedAction) {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_captcha, null);
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(dialogView)
                .setCancelable(true)
                .create();

        CardView cvCaptcha = dialogView.findViewById(R.id.cv_captcha_dialog);
        CheckBox checkBox = dialogView.findViewById(R.id.checkbox_captcha_dialog);
        TextView tvStatus = dialogView.findViewById(R.id.tv_captcha_dialog_status);
        View tvCancel = dialogView.findViewById(R.id.tv_cancel_captcha);

        if (tvCancel != null) {
            tvCancel.setOnClickListener(v -> dialog.dismiss());
        }

        cvCaptcha.setOnClickListener(v -> {
            if (recaptchaTasksClient == null) {
                Toast.makeText(this, "Keamanan sedang dipersiapkan...", Toast.LENGTH_SHORT).show();
                return;
            }

            tvStatus.setText("Memverifikasi...");
            recaptchaTasksClient.executeTask(RecaptchaAction.LOGIN)
                    .addOnSuccessListener(this, token -> {
                        checkBox.setChecked(true);
                        tvStatus.setText("Berhasil! Mengalihkan...");
                        tvStatus.setTextColor(Color.parseColor("#2E7D32"));
                        
                        new Handler().postDelayed(() -> {
                            if (!isFinishing()) {
                                dialog.dismiss();
                                onVerifiedAction.run();
                            }
                        }, 800);
                    })
                    .addOnFailureListener(this, e -> {
                        tvStatus.setText("Verifikasi Gagal");
                        tvStatus.setTextColor(Color.RED);
                        Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    });
        });

        dialog.show();
    }

    private void performEmailLogin() {
        if (isLoginLockedOut()) {
            long lockoutTime = securityPrefs.getLong("lockout_time", 0);
            long remainingTime = (lockoutTime - System.currentTimeMillis()) / 1000;
            Toast.makeText(this, "Terlalu banyak percobaan gagal. Coba lagi dalam " + remainingTime + " detik.", Toast.LENGTH_LONG).show();
            return;
        }

        String email = etEmail.getText().toString().trim();
        String password = etPassword.getText().toString().trim();

        mAuth.signInWithEmailAndPassword(email, password).addOnCompleteListener(this, task -> {
            if (task.isSuccessful()) {
                resetFailedLogin();
                checkWhitelistAndProceed(mAuth.getCurrentUser(), cbRemember.isChecked());
            } else {
                recordFailedLogin();
                Toast.makeText(this, "Login Gagal: Akun tidak ditemukan atau salah", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void checkWhitelistAndProceed(FirebaseUser user, boolean remember) {
        if (user == null) return;
        final String userEmail = user.getEmail();
        DatabaseReference whitelistRef = FirebaseDatabase.getInstance(DB_URL).getReference("whitelist");
        whitelistRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                boolean isAuthorized = false;
                if (snapshot.exists()) {
                    for (DataSnapshot child : snapshot.getChildren()) {
                        Object val = child.getValue();
                        if (val != null && userEmail != null && userEmail.trim().equalsIgnoreCase(val.toString().trim())) {
                            isAuthorized = true;
                            break;
                        }
                    }
                }
                if (isAuthorized) {
                    resetFailedLogin();
                    String sessionId = UUID.randomUUID().toString();
                    saveLoginStatus(remember, userEmail, sessionId);
                    updateSessionInFirebase(user.getUid(), sessionId);
                } else {
                    mAuth.signOut();
                    recordFailedLogin();
                    Toast.makeText(LoginActivity.this, "Email tidak terdaftar.", Toast.LENGTH_LONG).show();
                }
            }
            @Override public void onCancelled(@NonNull DatabaseError error) { mAuth.signOut(); }
        });
    }

    private boolean isLoginLockedOut() {
        long lockoutTime = securityPrefs.getLong("lockout_time", 0);
        if (lockoutTime > System.currentTimeMillis()) return true;
        else if (lockoutTime > 0) resetFailedLogin();
        return false;
    }

    private void recordFailedLogin() {
        int attempts = securityPrefs.getInt("failed_login_attempts", 0) + 1;
        SharedPreferences.Editor editor = securityPrefs.edit();
        if (attempts >= MAX_FAILED_ATTEMPTS) editor.putLong("lockout_time", System.currentTimeMillis() + LOCKOUT_DURATION_MS);
        editor.putInt("failed_login_attempts", attempts).apply();
    }

    private void resetFailedLogin() {
        securityPrefs.edit().putInt("failed_login_attempts", 0).putLong("lockout_time", 0).apply();
    }

    private void updateSessionInFirebase(String userId, String sessionId) {
        FirebaseDatabase.getInstance(DB_URL).getReference("users").child(userId).child("current_session_id")
                .setValue(sessionId).addOnSuccessListener(aVoid -> goToMainActivity(sessionId, true));
    }

    private void goToMainActivity(String sessionId, boolean isNewLogin) {
        Intent intent = new Intent(LoginActivity.this, MainActivity.class);
        if (sessionId != null) intent.putExtra("EXTRA_SESSION_ID", sessionId);
        intent.putExtra("IS_NEW_LOGIN", isNewLogin);
        startActivity(intent);
        finish();
    }

    private void saveLoginStatus(boolean isLoggedIn, String userEmail, String sessionId) {
        try {
            MasterKey masterKey = new MasterKey.Builder(this).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build();
            SharedPreferences sp = EncryptedSharedPreferences.create(this, "user_session", masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM);
            sp.edit().putBoolean("is_logged_in", isLoggedIn).putString("user_email", userEmail).putString("session_id", sessionId).apply();
        } catch (Exception ignored) {}
    }

    private boolean hasValidLocalSession() {
        try {
            MasterKey masterKey = new MasterKey.Builder(this).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build();
            SharedPreferences sp = EncryptedSharedPreferences.create(this, "user_session", masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM);
            return !sp.getString("session_id", "").isEmpty();
        } catch (Exception e) { return false; }
    }

    private void signInWithGoogle() {
        Intent signInIntent = mGoogleSignInClient.getSignInIntent();
        startActivityForResult(signInIntent, RC_SIGN_IN);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == RC_SIGN_IN) {
            Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(data);
            try {
                GoogleSignInAccount account = task.getResult(ApiException.class);
                if (account != null) firebaseAuthWithGoogle(account.getIdToken());
            } catch (ApiException e) { Toast.makeText(this, "Gagal login Google", Toast.LENGTH_SHORT).show(); }
        }
    }

    private void firebaseAuthWithGoogle(String idToken) {
        AuthCredential credential = GoogleAuthProvider.getCredential(idToken, null);
        mAuth.signInWithCredential(credential).addOnCompleteListener(this, task -> {
            if (task.isSuccessful()) checkWhitelistAndProceed(mAuth.getCurrentUser(), true);
        });
    }

    private void showForgotPasswordDialog() {
        EditText resetMail = new EditText(this);
        resetMail.setHint("Email Anda");
        new AlertDialog.Builder(this).setTitle("Lupa Password?").setMessage("Masukkan email untuk reset.").setView(resetMail)
                .setPositiveButton("Kirim", (dialog, which) -> {
                    String mail = resetMail.getText().toString().trim();
                    if (!TextUtils.isEmpty(mail)) mAuth.sendPasswordResetEmail(mail);
                }).setNegativeButton("Batal", null).show();
    }
}
