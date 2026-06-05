package com.example.aplikasimonitorsuhu;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
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
    
    // Site Key reCAPTCHA Anda
    private static final String RECAPTCHA_SITE_KEY = "6LdkmQwtAAAAAOkk8XDhnRqIhX1diJXiS_f06tQi";

    private TextInputLayout tilEmail, tilPassword;
    private TextInputEditText etEmail, etPassword;
    private MaterialCheckBox cbRemember;
    private TextView tvForgotPassword;
    
    private FirebaseAuth mAuth;
    private GoogleSignInClient mGoogleSignInClient;
    private RecaptchaTasksClient recaptchaTasksClient;
    private final String DB_URL = "https://tofumonitor-default-rtdb.asia-southeast1.firebasedatabase.app/";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        mAuth = FirebaseAuth.getInstance();
        
        // Cek Login Session
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser != null && hasValidLocalSession()) {
            goToMainActivity(null, false);
            return;
        }

        setContentView(R.layout.activity_login);

        // Inisialisasi reCAPTCHA saat aplikasi dibuka
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
        
        findViewById(R.id.btn_login).setOnClickListener(v -> executeRecaptchaAndLogin());
        findViewById(R.id.btn_google_signin).setOnClickListener(v -> signInWithGoogle());

        if (tvForgotPassword != null) {
            tvForgotPassword.setOnClickListener(v -> showForgotPasswordDialog());
        }
    }

    private void initializeRecaptcha() {
        Recaptcha.getTasksClient(getApplication(), RECAPTCHA_SITE_KEY)
                .addOnSuccessListener(this, client -> {
                    this.recaptchaTasksClient = client;
                    Log.d(TAG, "reCAPTCHA Terhubung");
                    // Memberi tahu pengguna bahwa sistem keamanan aktif
                    Toast.makeText(this, "Keamanan reCAPTCHA Aktif", Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(this, e -> {
                    Log.e(TAG, "reCAPTCHA Gagal: " + e.getMessage());
                    Toast.makeText(this, "reCAPTCHA Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
    }

    private void executeRecaptchaAndLogin() {
        if (etEmail == null || etPassword == null) return;
        String email = etEmail.getText().toString().trim();
        String password = etPassword.getText().toString().trim();

        if (TextUtils.isEmpty(email)) {
            tilEmail.setError("Email wajib diisi");
            return;
        }
        if (TextUtils.isEmpty(password)) {
            tilPassword.setError("Password wajib diisi");
            return;
        }

        if (recaptchaTasksClient == null) {
            // Jika belum siap, coba login langsung agar user tidak terhambat
            performLogin();
            return;
        }

        // Tampilkan pesan bahwa sedang diverifikasi
        Toast.makeText(this, "Memverifikasi keamanan...", Toast.LENGTH_SHORT).show();

        recaptchaTasksClient.executeTask(RecaptchaAction.LOGIN)
                .addOnSuccessListener(this, token -> {
                    Log.d(TAG, "Verifikasi Berhasil");
                    performLogin();
                })
                .addOnFailureListener(this, e -> {
                    Log.e(TAG, "Verifikasi Gagal: " + e.getMessage());
                    performLogin(); // Tetap lanjut jika gagal (opsi toleransi developer)
                });
    }

    private void performLogin() {
        String email = etEmail.getText().toString().trim();
        String password = etPassword.getText().toString().trim();

        mAuth.signInWithEmailAndPassword(email, password).addOnCompleteListener(this, task -> {
            if (task.isSuccessful()) {
                checkWhitelistAndProceed(mAuth.getCurrentUser(), cbRemember.isChecked());
            } else {
                Toast.makeText(this, "Login Gagal: Periksa Email/Password", Toast.LENGTH_SHORT).show();
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
                    String sessionId = UUID.randomUUID().toString();
                    saveLoginStatus(remember, userEmail, sessionId);
                    updateSessionInFirebase(user.getUid(), sessionId);
                } else {
                    mAuth.signOut();
                    Toast.makeText(LoginActivity.this, "Akses Ditolak: Email tidak terdaftar.", Toast.LENGTH_LONG).show();
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                mAuth.signOut();
                Toast.makeText(LoginActivity.this, "Error Database", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void updateSessionInFirebase(String userId, String sessionId) {
        FirebaseDatabase.getInstance(DB_URL).getReference("users").child(userId).child("current_session_id")
                .setValue(sessionId)
                .addOnSuccessListener(aVoid -> goToMainActivity(sessionId, true));
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
            sp.edit().putBoolean("is_logged_in", isLoggedIn)
                    .putString("user_email", userEmail)
                    .putString("session_id", sessionId).apply();
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
            } catch (ApiException e) {
                Toast.makeText(this, "Login Google Gagal", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void firebaseAuthWithGoogle(String idToken) {
        AuthCredential credential = GoogleAuthProvider.getCredential(idToken, null);
        mAuth.signInWithCredential(credential).addOnCompleteListener(this, task -> {
            if (task.isSuccessful()) {
                checkWhitelistAndProceed(mAuth.getCurrentUser(), true);
            }
        });
    }

    private void showForgotPasswordDialog() {
        EditText resetMail = new EditText(this);
        resetMail.setHint("Email Anda");
        AlertDialog.Builder passwordResetDialog = new AlertDialog.Builder(this);
        passwordResetDialog.setTitle("Lupa Kata Sandi?");
        passwordResetDialog.setMessage("Masukkan alamat email.");
        passwordResetDialog.setView(resetMail);
        passwordResetDialog.setPositiveButton("Kirim", (dialog, which) -> {
            String mail = resetMail.getText().toString().trim();
            if (!TextUtils.isEmpty(mail)) {
                mAuth.sendPasswordResetEmail(mail).addOnSuccessListener(unused -> 
                    Toast.makeText(this, "Cek email Anda.", Toast.LENGTH_SHORT).show()
                );
            }
        });
        passwordResetDialog.setNegativeButton("Batal", (dialog, which) -> dialog.dismiss());
        passwordResetDialog.create().show();
    }
}
