package com.example.aplikasimonitorsuhu;

import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.cardview.widget.CardView;
import androidx.fragment.app.Fragment;

import com.google.android.material.switchmaterial.SwitchMaterial;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.HashMap;
import java.util.Map;

public class SettingsFragment extends Fragment {

    private SwitchMaterial switchSound, switchVibrate;
    private TextView tvConnStatus, tvCurrentWifi;
    private TextInputEditText etBatasPanas, etBatasAman;
    private DatabaseReference dbMonitoring;
    private ValueEventListener configListener;
    private boolean isInitialLoad = true;
    private final String DB_URL = "https://tofumonitor-default-rtdb.asia-southeast1.firebasedatabase.app";

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_settings, container, false);

        switchSound = view.findViewById(R.id.switch_sound);
        switchVibrate = view.findViewById(R.id.switch_vibrate);
        tvConnStatus = view.findViewById(R.id.tv_conn_status);
        tvCurrentWifi = view.findViewById(R.id.tv_current_wifi);
        etBatasPanas = view.findViewById(R.id.et_batas_panas);
        etBatasAman = view.findViewById(R.id.et_batas_aman);
        
        CardView cardWifiInfo = view.findViewById(R.id.card_wifi_info);
        Button btnSimpanSuhu = view.findViewById(R.id.btn_simpan_suhu);

        SharedPreferences sp = requireContext().getSharedPreferences("app_settings", Context.MODE_PRIVATE);
        dbMonitoring = FirebaseDatabase.getInstance(DB_URL).getReference("monitoring");
        
        btnSimpanSuhu.setOnClickListener(v -> {
            try {
                float panas = Float.parseFloat(etBatasPanas.getText().toString());
                float aman = Float.parseFloat(etBatasAman.getText().toString());
                if (aman >= panas) {
                    Toast.makeText(getContext(), "Batas Aman harus lebih kecil!", Toast.LENGTH_SHORT).show();
                    return;
                }
                sp.edit().putFloat("batas_panas", panas).putFloat("batas_aman", aman).apply();
                Map<String, Object> updates = new HashMap<>();
                updates.put("batas_panas", panas);
                updates.put("batas_aman", aman);
                dbMonitoring.updateChildren(updates);
                Toast.makeText(getContext(), "Batas Suhu Berhasil Diperbarui!", Toast.LENGTH_SHORT).show();
            } catch (Exception e) {
                Toast.makeText(getContext(), "Input tidak valid", Toast.LENGTH_SHORT).show();
            }
        });

        cardWifiInfo.setOnClickListener(v -> showWifiConfigDialog());

        switchSound.setChecked(sp.getBoolean("use_sound", true));
        switchSound.setOnCheckedChangeListener((bv, isChecked) -> sp.edit().putBoolean("use_sound", isChecked).apply());
        switchVibrate.setChecked(sp.getBoolean("use_vibrate", true));
        switchVibrate.setOnCheckedChangeListener((bv, isChecked) -> sp.edit().putBoolean("use_vibrate", isChecked).apply());

        monitorConfigAndConnection();

        return view;
    }

    private void showWifiConfigDialog() {
        View dialogView = LayoutInflater.from(getContext()).inflate(R.layout.dialog_config_wifi, null);
        EditText etSsid = dialogView.findViewById(R.id.et_dialog_ssid);
        EditText etPass = dialogView.findViewById(R.id.et_dialog_password);

        new AlertDialog.Builder(getContext())
                .setTitle("Ganti WiFi Alat")
                .setMessage("ESP32 akan mencoba menyambung ke WiFi baru. Status koneksi akan diperbarui setelah berhasil.")
                .setView(dialogView)
                .setPositiveButton("Sambungkan", (dialog, which) -> {
                    String s = etSsid.getText().toString().trim();
                    String p = etPass.getText().toString().trim();
                    if (!s.isEmpty() && !p.isEmpty()) {
                        Map<String, Object> w = new HashMap<>();
                        w.put("ssid", s); 
                        w.put("password", p);
                        w.put("pending_update", true); // Trigger tanda sedang proses
                        dbMonitoring.child("wifi_config").updateChildren(w);
                        
                        tvConnStatus.setText("Sedang Menghubungkan...");
                        tvConnStatus.setTextColor(getResources().getColor(android.R.color.holo_orange_dark));
                        Toast.makeText(getContext(), "Instruksi dikirim ke alat...", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Batal", null)
                .show();
    }

    private void monitorConfigAndConnection() {
        configListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (!isAdded()) return;
                
                String currentSsid = snapshot.child("wifi_config").child("current_ssid").getValue(String.class);
                tvCurrentWifi.setText(currentSsid != null ? currentSsid : "Tidak Terdeteksi");

                if (isInitialLoad) {
                    Object pObj = snapshot.child("batas_panas").getValue();
                    Object aObj = snapshot.child("batas_aman").getValue();
                    if (pObj != null) etBatasPanas.setText(pObj.toString());
                    if (aObj != null) etBatasAman.setText(aObj.toString());
                    isInitialLoad = false;
                }

                // Logika Deteksi Koneksi yang lebih cepat (20 Detik)
                Object lastSeenObj = snapshot.child("last_seen").getValue();
                if (lastSeenObj instanceof Number) {
                    long lastSeen = ((Number) lastSeenObj).longValue();
                    long currentTime = System.currentTimeMillis();
                    long diff = Math.abs(currentTime - lastSeen);
                    
                    if (diff < 20000) { // Toleransi 20 detik
                        tvConnStatus.setText("Terhubung");
                        tvConnStatus.setTextColor(getResources().getColor(android.R.color.holo_green_dark));
                    } else {
                        tvConnStatus.setText("Alat Offline");
                        tvConnStatus.setTextColor(getResources().getColor(android.R.color.holo_red_dark));
                    }
                }
            }
            @Override public void onCancelled(@NonNull DatabaseError error) {}
        };
        dbMonitoring.addValueEventListener(configListener);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (dbMonitoring != null && configListener != null) dbMonitoring.removeEventListener(configListener);
    }
}
