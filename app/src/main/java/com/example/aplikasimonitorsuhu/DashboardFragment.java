package com.example.aplikasimonitorsuhu;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.cardview.widget.CardView;
import androidx.fragment.app.Fragment;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

public class DashboardFragment extends Fragment {

    private TextView tvTimer, tvStatus, tvFanStatus, tvLongestTime;
    private TextView tvRecentDate, tvRecentDuration, tvRecentTemp;
    private TextView tvProfileInitial;
    private CardView cardStatus;
    private View btnToHistory, btnSeeAll, btnProfileNav;
    
    private final String DB_URL = "https://tofumonitor-default-rtdb.asia-southeast1.firebasedatabase.app/";
    private DatabaseReference dbRoot;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_dashboard, container, false);

        // 1. Inisialisasi View
        tvTimer = view.findViewById(R.id.tv_timer_val);
        tvStatus = view.findViewById(R.id.tv_status_text);
        tvFanStatus = view.findViewById(R.id.tv_fan_status);
        tvLongestTime = view.findViewById(R.id.tv_longest_value);
        cardStatus = view.findViewById(R.id.card_status);
        tvRecentDate = view.findViewById(R.id.tv_recent_date);
        tvRecentDuration = view.findViewById(R.id.tv_recent_history_detail);
        tvRecentTemp = view.findViewById(R.id.tv_recent_temp_val);
        btnToHistory = view.findViewById(R.id.btn_to_history);
        btnSeeAll = view.findViewById(R.id.btn_see_all);
        
        // Foto profil (lingkaran inisial)
        btnProfileNav = view.findViewById(R.id.btn_profile_nav);
        tvProfileInitial = view.findViewById(R.id.tv_profile_initial);

        // 2. Set Inisial User (2 Huruf)
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user != null && user.getEmail() != null) {
            String email = user.getEmail();
            if (email.length() >= 2) {
                tvProfileInitial.setText(email.substring(0, 2).toUpperCase());
            } else {
                tvProfileInitial.setText(email.substring(0, 1).toUpperCase());
            }
        }

        // 3. Logika Klik Foto Profil (PINDAH KE PROFIL)
        if (btnProfileNav != null) {
            btnProfileNav.setOnClickListener(v -> {
                if (getActivity() != null) {
                    BottomNavigationView nav = getActivity().findViewById(R.id.bottom_nav);
                    if (nav != null) {
                        // Memaksa BottomNav pindah ke menu Profil
                        nav.setSelectedItemId(R.id.nav_profile);
                    }
                }
            });
        }

        // 4. Inisialisasi Database
        try {
            dbRoot = FirebaseDatabase.getInstance(DB_URL).getReference();
            startMonitoring();
            fetchHistorySummary();
        } catch (Exception e) {
            Toast.makeText(getContext(), "Gagal menyambung ke database.", Toast.LENGTH_LONG).show();
        }

        // 5. Navigasi ke History
        View.OnClickListener toHistoryListener = v -> {
            if (getActivity() != null) {
                BottomNavigationView nav = getActivity().findViewById(R.id.bottom_nav);
                if (nav != null) nav.setSelectedItemId(R.id.nav_history);
            }
        };
        if (btnToHistory != null) btnToHistory.setOnClickListener(toHistoryListener);
        if (btnSeeAll != null) btnSeeAll.setOnClickListener(toHistoryListener);

        return view;
    }

    private void startMonitoring() {
        dbRoot.child("monitoring").addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists() && isAdded()) {
                    String timerValue = snapshot.child("timer").getValue(String.class);
                    if (timerValue != null) tvTimer.setText(timerValue);

                    String currentFan = "MATI";
                    Object fanVal = snapshot.child("fan").getValue();
                    if (fanVal != null) {
                        String valStr = fanVal.toString().trim();
                        if (valStr.equalsIgnoreCase("Hidup") || valStr.equalsIgnoreCase("ON") || valStr.equalsIgnoreCase("AKTIF")) {
                            currentFan = "AKTIF";
                        }
                    }
                    tvFanStatus.setText(currentFan);

                    Object suhuObj = snapshot.child("suhu").getValue();
                    if (suhuObj != null) {
                        try {
                            double suhu = Double.parseDouble(suhuObj.toString());
                            updateUIBySuhu(suhu);
                        } catch (Exception e) {
                            tvStatus.setText(suhuObj.toString());
                        }
                    }
                }
            }

            private void updateUIBySuhu(double suhu) {
                if (suhu <= 30) {
                    tvStatus.setText("SIAP KEMAS");
                    cardStatus.setCardBackgroundColor(getResources().getColor(R.color.status_safe));
                } else if (suhu <= 35) {
                    tvStatus.setText("WASPADAI SUHU");
                    cardStatus.setCardBackgroundColor(getResources().getColor(R.color.status_warning));
                } else {
                    tvStatus.setText("SUHU PANAS");
                    cardStatus.setCardBackgroundColor(getResources().getColor(R.color.status_danger));
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                if (isAdded()) Toast.makeText(getContext(), "Koneksi terputus.", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void fetchHistorySummary() {
        dbRoot.child("history").limitToLast(1).addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists() && isAdded()) {
                    for (DataSnapshot child : snapshot.getChildren()) {
                        tvRecentDate.setText(String.valueOf(child.child("tanggal").getValue()));
                        tvRecentDuration.setText(String.valueOf(child.child("durasi").getValue()));
                        Object sAkhir = child.child("suhu_akhir").getValue();
                        if (sAkhir == null) sAkhir = child.child("suhu").getValue();
                        if (sAkhir != null) tvRecentTemp.setText(sAkhir.toString() + "°C");
                    }
                }
            }
            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        });
        
        dbRoot.child("history").addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists() && isAdded()) {
                    String maxDur = "00:00";
                    for (DataSnapshot child : snapshot.getChildren()) {
                        String dur = child.child("durasi").getValue(String.class);
                        if (dur != null && dur.compareTo(maxDur) > 0) maxDur = dur;
                    }
                    tvLongestTime.setText(maxDur);
                }
            }
            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        });
    }
}
