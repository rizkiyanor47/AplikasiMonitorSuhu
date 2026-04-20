package com.example.aplikasimonitorsuhu;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Button;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;

import com.google.android.material.checkbox.MaterialCheckBox;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.io.IOException;
import java.security.GeneralSecurityException;

public class LoginActivity extends AppCompatActivity {

    private static final String TAG = "LoginActivity";
    private TextInputLayout tilEmail, tilPassword;
    private TextInputEditText etEmail, etPassword;
    private MaterialCheckBox cbRemember;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        // Inisialisasi View
        tilEmail = findViewById(R.id.til_email);
        tilPassword = findViewById(R.id.til_password);
        etEmail = (TextInputEditText) tilEmail.getEditText();
        etPassword = (TextInputEditText) tilPassword.getEditText();
        cbRemember = findViewById(R.id.cb_remember);
        Button btnLogin = findViewById(R.id.btn_login);

        btnLogin.setOnClickListener(v -> performLogin());
    }

    private void performLogin() {
        String email = sanitizeInput(etEmail.getText().toString().trim());
        String password = sanitizeInput(etPassword.getText().toString().trim());

        tilEmail.setError(null);
        tilPassword.setError(null);

        boolean isValid = true;

        if (TextUtils.isEmpty(email)) {
            tilEmail.setError("Email tidak boleh kosong");
            isValid = false;
        } else if (!email.endsWith("@gmail.com")) {
            tilEmail.setError("Email wajib berakhiran @gmail.com");
            isValid = false;
        }

        if (TextUtils.isEmpty(password)) {
            tilPassword.setError("Password tidak boleh kosong");
            isValid = false;
        } else if (password.length() < 6) {
            tilPassword.setError("Password minimal 6 karakter");
            isValid = false;
        }

        if (isValid) {
            // Ambil status apakah checkbox dicentang atau tidak
            boolean isChecked = cbRemember.isChecked();
            
            // Simpan status login HANYA jika dicentang
            saveLoginStatus(isChecked, email);

            Toast.makeText(this, "Login Berhasil!", Toast.LENGTH_SHORT).show();
            startActivity(new Intent(LoginActivity.this, MainActivity.class));
            finish();
        } else {
            Toast.makeText(this, "Mohon periksa kembali input Anda", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Menyimpan status login menggunakan EncryptedSharedPreferences (AES-256)
     */
    private void saveLoginStatus(boolean isLoggedIn, String userEmail) {
        try {
            MasterKey masterKey = new MasterKey.Builder(this)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build();

            SharedPreferences sharedPreferences = EncryptedSharedPreferences.create(
                    this,
                    "user_session",
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            );

            sharedPreferences.edit()
                    .putBoolean("is_logged_in", isLoggedIn)
                    .putString("user_email", userEmail)
                    .apply();

        } catch (GeneralSecurityException | IOException e) {
            Log.e(TAG, "Gagal menyimpan sesi terenkripsi", e);
        }
    }

    private String sanitizeInput(String input) {
        if (input == null) return "";
        return input.replaceAll("[<>\"'%;()&]", "").trim();
    }
}