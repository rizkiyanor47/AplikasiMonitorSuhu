package com.example.aplikasimonitorsuhu;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    private TextView tvLiveTimer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.fragment_dashboard);

        tvLiveTimer = findViewById(R.id.tv_live_timer);
        BottomNavigationView bottomNav = findViewById(R.id.bottom_nav);
        bottomNav.setSelectedItemId(R.id.nav_dashboard);

        // 1. Hubungkan ke Firebase Realtime Database
        FirebaseDatabase database = FirebaseDatabase.getInstance();
        // "suhu" adalah nama KEY yang dikirim oleh Indra dari ESP32
        DatabaseReference myRef = database.getReference("suhu");

        // 2. Pasang Listener untuk memantau perubahan data secara real-time
        myRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    // Ambil nilai dari Firebase
                    Object value = snapshot.getValue();
                    if (value != null) {
                        String nilaiSuhu = value.toString();
                        // 3. Update UI: Ganti angka 15.30 jadi suhu asli
                        tvLiveTimer.setText(nilaiSuhu);
                    }
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.w(TAG, "Gagal membaca data dari Firebase", error.toException());
            }
        });

        // Logika Navigasi
        bottomNav.setOnItemSelectedListener(item -> {
            int itemId = item.getItemId();
            if (itemId == R.id.nav_dashboard) return true;

            Intent intent = null;
            if (itemId == R.id.nav_history) {
                intent = new Intent(this, HistoryActivity.class);
            } else if (itemId == R.id.nav_settings) {
                intent = new Intent(this, SettingsActivity.class);
            } else if (itemId == R.id.nav_profile) {
                intent = new Intent(this, ProfileActivity.class);
            }

            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                startActivity(intent);
                overridePendingTransition(0, 0);
                return true;
            }
            return false;
        });
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        BottomNavigationView bottomNav = findViewById(R.id.bottom_nav);
        if (bottomNav != null) {
            bottomNav.setSelectedItemId(R.id.nav_dashboard);
        }
    }
}