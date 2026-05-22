package com.example.aplikasimonitorsuhu;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.material.switchmaterial.SwitchMaterial;

public class SettingsFragment extends Fragment {

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_settings, container, false);

        // Inisialisasi Switch untuk Suara dan Getar dari fragment_settings.xml
        SwitchMaterial switchSound = view.findViewById(R.id.switch_sound);
        SwitchMaterial switchVibrate = view.findViewById(R.id.switch_vibrate);

        // Tambahkan listener jika Anda ingin menangani perubahan status switch (opsional)
        if (switchSound != null) {
            switchSound.setOnCheckedChangeListener((buttonView, isChecked) -> {
                // Logika untuk menyimpan pengaturan suara
            });
        }

        if (switchVibrate != null) {
            switchVibrate.setOnCheckedChangeListener((buttonView, isChecked) -> {
                // Logika untuk menyimpan pengaturan getaran
            });
        }

        return view;
    }
}
