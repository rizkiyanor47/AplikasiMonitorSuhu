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

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.io.IOException;
import java.security.GeneralSecurityException;

public class ProfileFragment extends Fragment {

    private static final String TAG = "ProfileFragment";
    private FirebaseAuth mAuth;
    private GoogleSignInClient mGoogleSignInClient;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_profile, container, false);

        mAuth = FirebaseAuth.getInstance();
        
        // Konfigurasi Google Sign-In agar bisa logout dengan bersih
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(getString(R.string.default_web_client_id))
                .requestEmail()
                .build();
        mGoogleSignInClient = GoogleSignIn.getClient(requireContext(), gso);

        FirebaseUser user = mAuth.getCurrentUser();

        TextView tvInitial = view.findViewById(R.id.tv_profile_initial_large);
        TextView tvEmail = view.findViewById(R.id.tv_profile_email);
        TextView tvName = view.findViewById(R.id.tv_profile_name);

        if (user != null) {
            String email = user.getEmail();
            String name = user.getDisplayName();
            
            // Set Email
            tvEmail.setText(email != null ? email : "-");
            
            // Set Nama (Jika ada dari Google, jika tidak pakai panggil default)
            if (name != null && !name.isEmpty()) {
                tvName.setText(name);
            }

            // Set Inisial (2 Huruf sesuai Dashboard)
            if (email != null && email.length() >= 2) {
                String initial = email.substring(0, 2).toUpperCase();
                tvInitial.setText(initial);
            } else if (email != null && !email.isEmpty()) {
                tvInitial.setText(email.substring(0, 1).toUpperCase());
            }
        }

        view.findViewById(R.id.btn_logout_profile).setOnClickListener(v -> performLogout());

        return view;
    }

    private void performLogout() {
        mAuth.signOut();
        mGoogleSignInClient.signOut().addOnCompleteListener(requireActivity(), task -> {
            clearLocalSession();
        });
    }

    private void clearLocalSession() {
        try {
            Context context = getContext();
            if (context != null) {
                MasterKey masterKey = new MasterKey.Builder(context)
                        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                        .build();

                SharedPreferences sharedPreferences = EncryptedSharedPreferences.create(
                        context, "user_session", masterKey,
                        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM);

                sharedPreferences.edit().clear().apply();

                Intent intent = new Intent(getActivity(), LoginActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(intent);
            }
        } catch (GeneralSecurityException | IOException e) {
            Log.e(TAG, "Error during session clearing", e);
            startActivity(new Intent(getActivity(), LoginActivity.class));
            if (getActivity() != null) getActivity().finishAffinity();
        }
    }
}
