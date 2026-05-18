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
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;

import java.io.IOException;
import java.security.GeneralSecurityException;

public class LoginActivity extends AppCompatActivity {

    private static final String TAG = "LoginActivity";
    private static final int RC_SIGN_IN = 9001;

    private TextInputLayout tilEmail, tilPassword;
    private TextInputEditText etEmail, etPassword;
    private MaterialCheckBox cbRemember;
    private TextView tvForgotPassword;
    
    private FirebaseAuth mAuth;
    private GoogleSignInClient mGoogleSignInClient;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        mAuth = FirebaseAuth.getInstance();

        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser != null) {
            goToMainActivity();
            return;
        }

        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(getString(R.string.default_web_client_id)) 
                .requestEmail()
                .build();

        mGoogleSignInClient = GoogleSignIn.getClient(this, gso);

        setContentView(R.layout.activity_login);

        tilEmail = findViewById(R.id.til_email);
        tilPassword = findViewById(R.id.til_password);
        etEmail = (TextInputEditText) tilEmail.getEditText();
        etPassword = (TextInputEditText) tilPassword.getEditText();
        cbRemember = findViewById(R.id.cb_remember);
        tvForgotPassword = findViewById(R.id.tv_forgot_password);
        
        findViewById(R.id.btn_login).setOnClickListener(v -> performLogin());
        findViewById(R.id.btn_google_signin).setOnClickListener(v -> signInWithGoogle());
        
        if (tvForgotPassword != null) {
            tvForgotPassword.setOnClickListener(v -> showForgotPasswordDialog());
        }
    }

    private void showForgotPasswordDialog() {
        EditText resetMail = new EditText(this);
        AlertDialog.Builder passwordResetDialog = new AlertDialog.Builder(this);
        passwordResetDialog.setTitle("Lupa Kata Sandi?");
        passwordResetDialog.setMessage("Masukkan alamat email Anda untuk menerima kiriman pengaturan ulang kata sandi.");
        passwordResetDialog.setView(resetMail);

        passwordResetDialog.setPositiveButton("Kirim", (dialog, which) -> {
            String mail = resetMail.getText().toString().trim();
            if (!TextUtils.isEmpty(mail)) {
                mAuth.sendPasswordResetEmail(mail).addOnSuccessListener(unused -> 
                    Toast.makeText(LoginActivity.this, "Petunjuk pengaturan ulang telah dikirim ke email Anda.", Toast.LENGTH_SHORT).show()
                ).addOnFailureListener(e -> 
                    Toast.makeText(LoginActivity.this, translateAuthError(e), Toast.LENGTH_SHORT).show()
                );
            }
        });

        passwordResetDialog.setNegativeButton("Batal", (dialog, which) -> dialog.dismiss());
        passwordResetDialog.create().show();
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
                firebaseAuthWithGoogle(account.getIdToken());
            } catch (ApiException e) {
                Log.w(TAG, "Google sign in failed", e);
                Toast.makeText(this, "Gagal masuk dengan Google. Periksa koneksi internet Anda.", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void firebaseAuthWithGoogle(String idToken) {
        AuthCredential credential = GoogleAuthProvider.getCredential(idToken, null);
        mAuth.signInWithCredential(credential).addOnCompleteListener(this, task -> {
            if (task.isSuccessful()) {
                FirebaseUser user = mAuth.getCurrentUser();
                if (user != null) saveLoginStatus(true, user.getEmail());
                goToMainActivity();
            } else {
                Toast.makeText(this, "Gagal masuk. Silakan coba lagi.", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void performLogin() {
        if (etEmail == null || etPassword == null) return;
        String email = etEmail.getText().toString().trim();
        String password = etPassword.getText().toString().trim();

        if (TextUtils.isEmpty(email) || TextUtils.isEmpty(password)) {
            Toast.makeText(this, "Email dan Kata Sandi wajib diisi", Toast.LENGTH_SHORT).show();
            return;
        }

        mAuth.signInWithEmailAndPassword(email, password).addOnCompleteListener(this, task -> {
            if (task.isSuccessful()) {
                boolean remember = cbRemember != null && cbRemember.isChecked();
                saveLoginStatus(remember, email);
                goToMainActivity();
            } else {
                Toast.makeText(LoginActivity.this, translateAuthError(task.getException()), Toast.LENGTH_SHORT).show();
            }
        });
    }

    /**
     * Mengubah pesan error teknis Firebase ke Bahasa Indonesia yang mudah dimengerti awam.
     */
    private String translateAuthError(Exception e) {
        String message = "Terjadi kesalahan. Silakan coba lagi nanti.";
        if (e instanceof FirebaseAuthException) {
            String errorCode = ((FirebaseAuthException) e).getErrorCode();
            switch (errorCode) {
                case "ERROR_INVALID_EMAIL":
                    message = "Format alamat email tidak benar.";
                    break;
                case "ERROR_WRONG_PASSWORD":
                    message = "Kata sandi salah. Silakan periksa kembali.";
                    break;
                case "ERROR_USER_NOT_FOUND":
                    message = "Alamat email tidak terdaftar.";
                    break;
                case "ERROR_USER_DISABLED":
                    message = "Akun ini telah dinonaktifkan.";
                    break;
                case "ERROR_TOO_MANY_REQUESTS":
                    message = "Terlalu banyak percobaan masuk. Silakan tunggu beberapa saat.";
                    break;
                case "ERROR_NETWORK_REQUEST_FAILED":
                    message = "Koneksi internet bermasalah. Pastikan Anda online.";
                    break;
            }
        }
        return message;
    }

    private void goToMainActivity() {
        startActivity(new Intent(LoginActivity.this, MainActivity.class));
        finish();
    }

    private void saveLoginStatus(boolean isLoggedIn, String userEmail) {
        try {
            MasterKey masterKey = new MasterKey.Builder(this).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build();
            SharedPreferences sharedPreferences = EncryptedSharedPreferences.create(this, "user_session", masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM);
            sharedPreferences.edit().putBoolean("is_logged_in", isLoggedIn).putString("user_email", userEmail).apply();
        } catch (GeneralSecurityException | IOException e) {
            Log.e(TAG, "Error saving session", e);
        }
    }
}
