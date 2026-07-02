package com.example.aplikasimonitorsuhu;

import android.Manifest;
import android.app.AlertDialog;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.media.AudioAttributes;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.Log;
import android.view.WindowManager;
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

    public interface EspConnectionListener {
        void onEspConnectionChanged(boolean isConnected);
    }

    private FirebaseAuth mAuth;
    private DatabaseReference sessionRef;
    private ValueEventListener sessionListener;
    private String localSessionId = "";
    private boolean isJustLoggedIn = false;

    private DatabaseReference monitoringRef;
    private ValueEventListener monitoringListener;
    private long lastSeenTime = 0;
    private boolean mIsEspConnected = true; 
    private boolean firstCheckDone = false;
    private long serverTimeOffset = 0;

    private final Handler connHandler = new Handler(Looper.getMainLooper());
    private Runnable connRunnable;

    private final String DB_URL = "https://tofumonitor-default-rtdb.asia-southeast1.firebasedatabase.app";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE);
        setContentView(R.layout.activity_main);
        
        mAuth = FirebaseAuth.getInstance();
        FirebaseUser user = mAuth.getCurrentUser();

        if (user == null) {
            redirectToLogin();
            return;
        }

        checkNotificationPermission();
        isJustLoggedIn = getIntent().getBooleanExtra("IS_NEW_LOGIN", false);
        loadLocalSession();

        setupSessionSecurity(user.getUid());

        DatabaseReference offsetRef = FirebaseDatabase.getInstance(DB_URL).getReference(".info/serverTimeOffset");
        offsetRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                Long offset = snapshot.getValue(Long.class);
                if (offset != null) serverTimeOffset = offset;
            }
            @Override public void onCancelled(@NonNull DatabaseError error) {}
        });

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
        sessionRef = FirebaseDatabase.getInstance(DB_URL).getReference("users").child(userId).child("current_session_id");
        sessionListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (!snapshot.exists()) return;
                String remoteSessionId = snapshot.getValue(String.class);
                if (remoteSessionId != null && !remoteSessionId.equals(localSessionId) && !localSessionId.isEmpty() && !isJustLoggedIn) {
                    handleMultiLogin();
                }
            }
            @Override public void onCancelled(@NonNull DatabaseError error) { }
        };
        sessionRef.addValueEventListener(sessionListener);
    }

    private void setupConnectionMonitoring() {
        monitoringListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                Object obj = snapshot.child("last_seen").getValue();
                if (obj != null) {
                    try {
                        lastSeenTime = Long.parseLong(obj.toString());
                    } catch (Exception ignored) {}
                }
            }
            @Override public void onCancelled(@NonNull DatabaseError error) {}
        };
        monitoringRef.addValueEventListener(monitoringListener);

        connRunnable = new Runnable() {
            @Override
            public void run() {
                if (lastSeenTime > 0) {
                    long currentTime = System.currentTimeMillis() + serverTimeOffset;
                    long diff = Math.abs(currentTime - lastSeenTime);

                    // PERBAIKAN: Lebih toleran terhadap kesalahan waktu alat (misal alat tahun 2026)
                    // Kita anggap online jika selisih < 2 menit ATAU timestamp di masa depan (error RTC)
                    boolean isConnected = (diff < 120000) || (lastSeenTime > currentTime);

                    if (!firstCheckDone || isConnected != mIsEspConnected) {
                        mIsEspConnected = isConnected;
                        firstCheckDone = true;
                        broadcastEspStatus(isConnected);
                    }
                }
                connHandler.postDelayed(this, 5000);
            }
        };
        connHandler.post(connRunnable);
    }

    public boolean getEspConnectionStatus() {
        return mIsEspConnected;
    }

    private void broadcastEspStatus(boolean isConnected) {
        for (Fragment fragment : getSupportFragmentManager().getFragments()) {
            if (fragment instanceof EspConnectionListener && fragment.isAdded()) {
                ((EspConnectionListener) fragment).onEspConnectionChanged(isConnected);
            }
        }
    }

    public void handleMultiLogin() {
        if (sessionRef != null && sessionListener != null) sessionRef.removeEventListener(sessionListener);
        mAuth.signOut();
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
        connHandler.removeCallbacks(connRunnable);
    }
}
