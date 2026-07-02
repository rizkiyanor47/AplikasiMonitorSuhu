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
import java.util.Locale;
import java.util.Map;

public class SettingsFragment extends Fragment implements MainActivity.EspConnectionListener {

    private SwitchMaterial switchSound, switchVibrate;
    private TextView tvConnStatus, tvCurrentWifi;
    private TextInputEditText etBatasPanas, etBatasAman;
    private DatabaseReference dbMonitoring;
    private ValueEventListener configListener;
    
    private boolean isInitialLoad = true;
    private Boolean lastKnownConnection = null;
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
                String pStr = etBatasPanas.getText().toString().trim();
                String aStr = etBatasAman.getText().toString().trim();
                if (pStr.isEmpty() || aStr.isEmpty()) return;

                float panas = Float.parseFloat(pStr);
                float aman = Float.parseFloat(aStr);
                if (aman >= panas) {
                    Toast.makeText(requireContext(), "Batas Aman harus lebih kecil!", Toast.LENGTH_SHORT).show();
                    return;
                }
                Map<String, Object> updates = new HashMap<>();
                updates.put("batas_panas", panas);
                updates.put("batas_aman", aman);
                dbMonitoring.updateChildren(updates);
                Toast.makeText(requireContext(), "Batas Suhu Disimpan!", Toast.LENGTH_SHORT).show();
            } catch (Exception e) {
                Toast.makeText(requireContext(), "Input tidak valid", Toast.LENGTH_SHORT).show();
            }
        });

        cardWifiInfo.setOnClickListener(v -> showWifiConfigDialog());

        switchSound.setChecked(sp.getBoolean("use_sound", true));
        switchSound.setOnCheckedChangeListener((bv, isChecked) -> sp.edit().putBoolean("use_sound", isChecked).apply());
        switchVibrate.setChecked(sp.getBoolean("use_vibrate", true));
        switchVibrate.setOnCheckedChangeListener((bv, isChecked) -> sp.edit().putBoolean("use_vibrate", isChecked).apply());

        monitorConfig();
        updateConnectionUI();

        return view;
    }

    private void updateConnectionUI() {
        if (!isAdded()) return;
        boolean isConnected = false;
        if (getActivity() instanceof MainActivity) {
            isConnected = ((MainActivity) getActivity()).getEspConnectionStatus();
        }

        if (isConnected) {
            tvConnStatus.setText("Terhubung");
            tvConnStatus.setTextColor(getResources().getColor(android.R.color.holo_green_dark));
        } else {
            tvConnStatus.setText("Alat Offline");
            tvConnStatus.setTextColor(getResources().getColor(android.R.color.holo_red_dark));
        }

        if (lastKnownConnection != null && lastKnownConnection != isConnected) {
            String msg = isConnected ? "Alat Terhubung Kembali" : "Alat Terputus!";
            Toast.makeText(getContext(), msg, Toast.LENGTH_SHORT).show();
        }
        lastKnownConnection = isConnected;
    }

    @Override
    public void onEspConnectionChanged(boolean isConnected) {
        if (isAdded()) updateConnectionUI();
    }

    private void showWifiConfigDialog() {
        boolean isConnected = false;
        if (getActivity() instanceof MainActivity) isConnected = ((MainActivity) getActivity()).getEspConnectionStatus();
        
        if (!isConnected) {
            new AlertDialog.Builder(requireContext())
                .setTitle("Alat Sedang Offline")
                .setMessage("Ganti WiFi hanya bisa dilakukan saat alat sedang Online agar perintah dapat diterima.")
                .setPositiveButton("Ok", null)
                .show();
            return;
        }

        View dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_config_wifi, null);
        EditText etSsid = dialogView.findViewById(R.id.et_dialog_ssid);
        EditText etPass = dialogView.findViewById(R.id.et_dialog_password);

        new AlertDialog.Builder(requireContext())
                .setTitle("Ganti WiFi Alat")
                .setMessage("Masukkan SSID dan Password baru. Status WiFi akan menjadi 'Sinkronisasi' sementara alat menghubungkan ulang.")
                .setView(dialogView)
                .setPositiveButton("Hubungkan", (dialog, which) -> {
                    String s = etSsid.getText().toString().trim();
                    String p = etPass.getText().toString().trim();
                    if (!s.isEmpty()) {
                        Map<String, Object> wifiUpdates = new HashMap<>();
                        wifiUpdates.put("wifi_config/ssid", s);
                        wifiUpdates.put("wifi_config/password", p);
                        wifiUpdates.put("wifi_config/pending_update", true);
                        
                        dbMonitoring.updateChildren(wifiUpdates).addOnSuccessListener(aVoid -> {
                            Toast.makeText(requireContext(), "Konfigurasi terkirim! Menunggu sinkronisasi alat...", Toast.LENGTH_LONG).show();
                            tvCurrentWifi.setText("Sinkronisasi...");
                            tvCurrentWifi.setTextColor(getResources().getColor(android.R.color.holo_orange_dark));
                        });
                    }
                })
                .setNegativeButton("Batal", null)
                .show();
    }

    private void monitorConfig() {
        configListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (!isAdded()) return;
                
                String currentSsid = snapshot.child("wifi_config").child("current_ssid").getValue(String.class);
                Boolean isPending = snapshot.child("wifi_config").child("pending_update").getValue(Boolean.class);
                
                if (Boolean.TRUE.equals(isPending)) {
                    tvCurrentWifi.setText("Sinkronisasi...");
                    tvCurrentWifi.setTextColor(getResources().getColor(android.R.color.holo_orange_dark));
                } else {
                    tvCurrentWifi.setText(currentSsid != null ? currentSsid : "WiFi Terhubung");
                    tvCurrentWifi.setTextColor(getResources().getColor(R.color.tofu_orange));
                }

                Object pObj = snapshot.child("batas_panas").getValue();
                Object aObj = snapshot.child("batas_aman").getValue();

                if (pObj != null && (isInitialLoad || !etBatasPanas.hasFocus())) {
                    try {
                        float p = Float.parseFloat(pObj.toString());
                        etBatasPanas.setText(String.format(Locale.US, "%.1f", p));
                    } catch (Exception e) {
                        etBatasPanas.setText(pObj.toString());
                    }
                }

                if (aObj != null && (isInitialLoad || !etBatasAman.hasFocus())) {
                    try {
                        float a = Float.parseFloat(aObj.toString());
                        etBatasAman.setText(String.format(Locale.US, "%.1f", a));
                    } catch (Exception e) {
                        etBatasAman.setText(aObj.toString());
                    }
                }
                
                isInitialLoad = false;
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
