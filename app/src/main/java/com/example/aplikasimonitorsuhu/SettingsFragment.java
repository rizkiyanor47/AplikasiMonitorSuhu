package com.example.aplikasimonitorsuhu;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.io.IOException;
import java.security.GeneralSecurityException;

public class SettingsFragment extends Fragment {

    private static final String TAG = "SettingsFragment";
    private FirebaseAuth mAuth;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_settings, container, false);

        mAuth = FirebaseAuth.getInstance();
        FirebaseUser currentUser = mAuth.getCurrentUser();

        // 1. Tampilkan Inisial Nama di Tengah (Besar)
        // Elemen header (kecil) sudah dihapus dari XML, jadi kodenya juga dihapus di sini
        TextView tvInitialLarge = view.findViewById(R.id.tv_settings_initial);
        
        if (currentUser != null && currentUser.getEmail() != null) {
            String email = currentUser.getEmail();
            String initial = email.substring(0, Math.min(email.length(), 2)).toUpperCase();
            
            if (tvInitialLarge != null) tvInitialLarge.setText(initial);
        }

        // 2. Tampilkan Email di Bawah
        TextView tvUserEmail = view.findViewById(R.id.tv_user_email);
        if (currentUser != null) {
            tvUserEmail.setText("Masuk sebagai: " + currentUser.getEmail());
        }

        view.findViewById(R.id.btn_logout).setOnClickListener(v -> performLogout());

        return view;
    }

    private void performLogout() {
        mAuth.signOut();
        try {
            Context context = getContext();
            if (context != null) {
                MasterKey masterKey = new MasterKey.Builder(context)
                        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                        .build();

                SharedPreferences sharedPreferences = EncryptedSharedPreferences.create(
                        context,
                        "user_session",
                        masterKey,
                        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
                );

                sharedPreferences.edit().clear().apply();

                Intent intent = new Intent(getActivity(), LoginActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(intent);
            }
        } catch (GeneralSecurityException | IOException e) {
            Log.e(TAG, "Gagal logout", e);
            startActivity(new Intent(getActivity(), LoginActivity.class));
            if (getActivity() != null) getActivity().finishAffinity();
        }
    }
}
