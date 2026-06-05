package com.example.aplikasimonitorsuhu;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

public class MainActivity extends AppCompatActivity {

    private FirebaseAuth mAuth;
    private DatabaseReference sessionRef;
    private ValueEventListener sessionListener;
    private String localSessionId = "";
    private boolean isJustLoggedIn = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        mAuth = FirebaseAuth.getInstance();
        FirebaseUser user = mAuth.getCurrentUser();
        
        if (user == null) {
            redirectToLogin();
            return;
        }

        setContentView(R.layout.activity_main);
        
        // 1. Cek apakah ini login baru
        isJustLoggedIn = getIntent().getBooleanExtra("IS_NEW_LOGIN", false);

        // 2. Muat Sesi Lokal
        loadLocalSession();
        
        // 3. Prioritas: Ambil ID Sesi dari Intent jika ada
        String intentSessionId = getIntent().getStringExtra("EXTRA_SESSION_ID");
        if (intentSessionId != null && !intentSessionId.isEmpty()) {
            localSessionId = intentSessionId;
        }
        
        // 4. Mulai Pengawasan Sesi
        setupSessionSecurity(user.getUid());

        BottomNavigationView bottomNav = findViewById(R.id.bottom_nav);
        if (savedInstanceState == null) {
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.fragment_container, new DashboardFragment())
                    .commit();
        }

        bottomNav.setOnItemSelectedListener(item -> {
            Fragment selectedFragment = null;
            int itemId = item.getItemId();
            if (itemId == R.id.nav_dashboard) selectedFragment = new DashboardFragment();
            else if (itemId == R.id.nav_history) selectedFragment = new HistoryFragment();
            else if (itemId == R.id.nav_profile) selectedFragment = new ProfileFragment();
            else if (itemId == R.id.nav_settings) selectedFragment = new SettingsFragment();

            if (selectedFragment != null) {
                getSupportFragmentManager().beginTransaction()
                        .replace(R.id.fragment_container, selectedFragment)
                        .commit();
            }
            return true;
        });
    }

    private void loadLocalSession() {
        try {
            MasterKey masterKey = new MasterKey.Builder(this).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build();
            SharedPreferences sp = EncryptedSharedPreferences.create(this, "user_session", masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM);
            localSessionId = sp.getString("session_id", "");
        } catch (Exception e) {
            Log.e("SessionDebug", "Gagal muat sesi", e);
        }
    }

    private void setupSessionSecurity(String userId) {
        sessionRef = FirebaseDatabase.getInstance("https://tofumonitor-default-rtdb.asia-southeast1.firebasedatabase.app/")
                .getReference("users")
                .child(userId)
                .child("current_session_id");

        sessionListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (!snapshot.exists()) return;
                
                String remoteSessionId = snapshot.getValue(String.class);
                if (remoteSessionId == null) return;

                // Logika Toleransi:
                // Jika Sesi di Firebase sudah sama dengan di HP, matikan flag 'Baru Login'
                if (remoteSessionId.equals(localSessionId)) {
                    isJustLoggedIn = false;
                    Log.d("SessionDebug", "Sesi tersinkronisasi.");
                } else if (!isJustLoggedIn && !localSessionId.isEmpty()) {
                    // Hanya logout jika BUKAN sedang dalam masa toleransi login baru
                    Log.w("SessionDebug", "Sesi tidak cocok! Remote: " + remoteSessionId + " | Local: " + localSessionId);
                    if (!isFinishing()) {
                        handleMultiLogin();
                    }
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) { }
        };
        sessionRef.addValueEventListener(sessionListener);
    }

    public void handleMultiLogin() {
        if (sessionRef != null && sessionListener != null) {
            sessionRef.removeEventListener(sessionListener);
        }
        mAuth.signOut();
        try {
            MasterKey masterKey = new MasterKey.Builder(this).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build();
            SharedPreferences sp = EncryptedSharedPreferences.create(this, "user_session", masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM);
            sp.edit().clear().commit();
        } catch (Exception ignored) {}

        Toast.makeText(this, "Akun Anda digunakan di perangkat lain.", Toast.LENGTH_LONG).show();
        redirectToLogin();
    }

    private void redirectToLogin() {
        Intent intent = new Intent(this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (sessionRef != null && sessionListener != null) {
            sessionRef.removeEventListener(sessionListener);
        }
    }
}
