package com.example.aplikasimonitorsuhu;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;
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

    private DatabaseReference monitoringRef;
    private ValueEventListener monitoringListener;
    private long lastSeenTime = 0;
    private boolean isEspConnected = true; 
    private boolean firstCheckDone = false;
    private final Handler connHandler = new Handler(Looper.getMainLooper());
    private Runnable connRunnable;

    // URL Database yang seragam (Tanpa tanda miring di akhir)
    private final String DB_URL = "https://tofumonitor-default-rtdb.asia-southeast1.firebasedatabase.app";

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
        checkNotificationPermission();

        isJustLoggedIn = getIntent().getBooleanExtra("IS_NEW_LOGIN", false);
        loadLocalSession();
        
        String intentSessionId = getIntent().getStringExtra("EXTRA_SESSION_ID");
        if (intentSessionId != null && !intentSessionId.isEmpty()) {
            localSessionId = intentSessionId;
        }
        
        setupSessionSecurity(user.getUid());

        monitoringRef = FirebaseDatabase.getInstance(DB_URL).getReference("monitoring");
        setupConnectionMonitoring();

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

    private void checkNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.POST_NOTIFICATIONS}, 101);
            }
        }
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
        sessionRef = FirebaseDatabase.getInstance(DB_URL)
                .getReference("users")
                .child(userId)
                .child("current_session_id");

        sessionListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (!snapshot.exists()) return;
                String remoteSessionId = snapshot.getValue(String.class);
                if (remoteSessionId == null) return;
                if (remoteSessionId.equals(localSessionId)) {
                    isJustLoggedIn = false;
                } else if (!isJustLoggedIn && !localSessionId.isEmpty()) {
                    if (!isFinishing()) handleMultiLogin();
                }
            }
            @Override
            public void onCancelled(@NonNull DatabaseError error) { }
        };
        sessionRef.addValueEventListener(sessionListener);
    }

    private void setupConnectionMonitoring() {
        monitoringListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.hasChild("last_seen")) {
                    lastSeenTime = snapshot.child("last_seen").getValue(Long.class);
                }
            }
            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        };
        monitoringRef.addValueEventListener(monitoringListener);

        connRunnable = new Runnable() {
            @Override
            public void run() {
                if (lastSeenTime > 0) {
                    boolean currentConn = (System.currentTimeMillis() - lastSeenTime < 15000);
                    if (!firstCheckDone) {
                        isEspConnected = currentConn;
                        firstCheckDone = true;
                        if (!currentConn) showConnectionNotification("Alat Terputus", "Koneksi ke alat tidak terdeteksi.");
                    } else if (currentConn != isEspConnected) {
                        isEspConnected = currentConn;
                        if (currentConn) {
                            Toast.makeText(MainActivity.this, "Alat Terhubung Kembali", Toast.LENGTH_SHORT).show();
                            showConnectionNotification("ESP32 Tersambung", "Alat termonitor kembali online.");
                        } else {
                            Toast.makeText(MainActivity.this, "Koneksi Alat Terputus!", Toast.LENGTH_LONG).show();
                            showConnectionNotification("ESP32 Terputus", "Koneksi ke alat terputus! Mohon periksa daya atau internet alat.");
                        }
                    }
                }
                connHandler.postDelayed(this, 5000);
            }
        };
        connHandler.post(connRunnable);
    }

    private void showConnectionNotification(String title, String message) {
        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        String channelId = "esp_conn_status";
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(channelId, "Status Koneksi ESP32", NotificationManager.IMPORTANCE_HIGH);
            if (notificationManager != null) notificationManager.createNotificationChannel(channel);
        }
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, channelId)
                .setSmallIcon(R.drawable.suhutahu)
                .setContentTitle(title)
                .setContentText(message)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true);
        if (notificationManager != null) notificationManager.notify(102, builder.build());
    }

    public void handleMultiLogin() {
        if (sessionRef != null && sessionListener != null) sessionRef.removeEventListener(sessionListener);
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
        if (sessionRef != null && sessionListener != null) sessionRef.removeEventListener(sessionListener);
        if (monitoringRef != null && monitoringListener != null) monitoringRef.removeEventListener(monitoringListener);
        if (connHandler != null && connRunnable != null) connHandler.removeCallbacks(connRunnable);
    }
}
