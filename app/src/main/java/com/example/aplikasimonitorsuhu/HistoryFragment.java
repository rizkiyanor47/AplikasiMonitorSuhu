package com.example.aplikasimonitorsuhu;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class HistoryFragment extends Fragment {

    private RecyclerView rvHistory;
    private HistoryAdapter adapter;
    private List<HistoryModel> historyList;
    private DatabaseReference dbRef;
    private final String DB_URL = "https://tofumonitor-default-rtdb.asia-southeast1.firebasedatabase.app/";

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_history, container, false);

        // 1. Inisialisasi RecyclerView
        rvHistory = view.findViewById(R.id.rv_history);
        rvHistory.setLayoutManager(new LinearLayoutManager(getContext()));
        historyList = new ArrayList<>();
        adapter = new HistoryAdapter(historyList);
        rvHistory.setAdapter(adapter);

        // 2. Tampilkan Inisial Nama di Pojok Kanan Atas
        TextView tvProfileInitial = view.findViewById(R.id.tv_profile_initial);
        View btnProfileNav = view.findViewById(R.id.btn_profile_nav);
        
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user != null && user.getEmail() != null) {
            String email = user.getEmail();
            String initial = email.substring(0, Math.min(email.length(), 2)).toUpperCase();
            if (tvProfileInitial != null) tvProfileInitial.setText(initial);
        }

        // Klik profil di halaman riwayat akan pindah ke Pengaturan
        if (btnProfileNav != null) {
            btnProfileNav.setOnClickListener(v -> {
                if (getActivity() != null) {
                    BottomNavigationView nav = getActivity().findViewById(R.id.bottom_nav);
                    if (nav != null) nav.setSelectedItemId(R.id.nav_settings);
                }
            });
        }

        // 3. Ambil data dari Firebase
        dbRef = FirebaseDatabase.getInstance(DB_URL).getReference("history");
        getHistoryData();

        return view;
    }

    private void getHistoryData() {
        dbRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                historyList.clear();
                if (snapshot.exists()) {
                    for (DataSnapshot item : snapshot.getChildren()) {
                        String tgl = "-";
                        String dur = "-";
                        Object suhu = "-";

                        for (DataSnapshot detail : item.getChildren()) {
                            String key = detail.getKey();
                            if (key != null) {
                                String cleanKey = key.trim().toLowerCase();
                                if (cleanKey.equals("tanggal")) tgl = String.valueOf(detail.getValue());
                                if (cleanKey.equals("durasi")) dur = String.valueOf(detail.getValue());
                                if (cleanKey.equals("suhu_akhir") || cleanKey.equals("suhu")) {
                                    suhu = detail.getValue();
                                }
                            }
                        }
                        historyList.add(new HistoryModel(tgl, dur, suhu));
                    }
                    Collections.reverse(historyList);
                    adapter.notifyDataSetChanged();
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                if (isAdded()) {
                    Toast.makeText(getContext(), "Maaf, gagal memuat riwayat.", Toast.LENGTH_SHORT).show();
                }
            }
        });
    }
}
