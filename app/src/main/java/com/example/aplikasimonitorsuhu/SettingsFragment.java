package com.example.aplikasimonitorsuhu;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.material.switchmaterial.SwitchMaterial;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

public class SettingsFragment extends Fragment {

    private SwitchMaterial switchSound, switchVibrate;
    private TextView tvConnStatus;
    private DatabaseReference dbMonitoring;
    private ValueEventListener connListener;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_settings, container, false);

        switchSound = view.findViewById(R.id.switch_sound);
        switchVibrate = view.findViewById(R.id.switch_vibrate);
        tvConnStatus = view.findViewById(R.id.tv_conn_status);
        Button btnLogout = view.findViewById(R.id.btn_logout);

        SharedPreferences sp = requireContext().getSharedPreferences("app_settings", Context.MODE_PRIVATE);
        
        // Load Saved Settings
        if (switchSound != null) {
            switchSound.setChecked(sp.getBoolean("use_sound", true));
            switchSound.setOnCheckedChangeListener((bv, isChecked) -> 
                sp.edit().putBoolean("use_sound", isChecked).apply());
        }

        if (switchVibrate != null) {
            switchVibrate.setChecked(sp.getBoolean("use_vibrate", true));
            switchVibrate.setOnCheckedChangeListener((bv, isChecked) -> 
                sp.edit().putBoolean("use_vibrate", isChecked).apply());
        }

        // Monitoring Connection Status
        dbMonitoring = FirebaseDatabase.getInstance("https://tofumonitor-default-rtdb.asia-southeast1.firebasedatabase.app/").getReference("monitoring");
        checkConnection();

        if (btnLogout != null) {
            btnLogout.setOnClickListener(v -> {
                FirebaseAuth.getInstance().signOut();
                requireActivity().getSharedPreferences("user_session", Context.MODE_PRIVATE).edit().clear().apply();
                Intent intent = new Intent(getActivity(), LoginActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(intent);
            });
        }

        return view;
    }

    private void checkConnection() {
        connListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.hasChild("last_seen") && tvConnStatus != null) {
                    long lastSeen = snapshot.child("last_seen").getValue(Long.class);
                    // Jika data update < 15 detik yang lalu, anggap Online
                    if (System.currentTimeMillis() - lastSeen < 15000) {
                        tvConnStatus.setText("Terhubung");
                        tvConnStatus.setTextColor(getResources().getColor(android.R.color.holo_green_dark));
                    } else {
                        tvConnStatus.setText("Terputus");
                        tvConnStatus.setTextColor(getResources().getColor(android.R.color.holo_red_dark));
                    }
                }
            }
            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        };
        dbMonitoring.addValueEventListener(connListener);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (dbMonitoring != null && connListener != null) {
            dbMonitoring.removeEventListener(connListener);
        }
    }
}
