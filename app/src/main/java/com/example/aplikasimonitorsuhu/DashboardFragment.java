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

        // Inisialisasi View
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
        btnProfileNav = view.findViewById(R.id.btn_profile_nav);
        tvProfileInitial = view.findViewById(R.id.tv_profile_initial);

        // Set Inisial User dari Email
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user != null && user.getEmail() != null) {
            String email = user.getEmail();
            String initial = email.substring(0, Math.min(email.length(), 2)).toUpperCase();
            if (tvProfileInitial != null) tvProfileInitial.setText(initial);
        }

        try {
            dbRoot = FirebaseDatabase.getInstance(DB_URL).getReference();
            startMonitoring();
            fetchHistorySummary();
        } catch (Exception e) {
            Toast.makeText(getContext(), "Gagal menyambung ke database.", Toast.LENGTH_LONG).show();
        }

        // Navigasi ke History (Sinkron dengan Bottom Nav)
        View.OnClickListener toHistoryListener = v -> {
            if (getActivity() != null) {
                BottomNavigationView nav = getActivity().findViewById(R.id.bottom_nav);
                if (nav != null) nav.setSelectedItemId(R.id.nav_history);
            }
        };
        btnToHistory.setOnClickListener(toHistoryListener);
        btnSeeAll.setOnClickListener(toHistoryListener);

        // Navigasi ke Settings via Klik Profil (Sinkron dengan Bottom Nav)
        if (btnProfileNav != null) {
            btnProfileNav.setOnClickListener(v -> {
                if (getActivity() != null) {
                    BottomNavigationView nav = getActivity().findViewById(R.id.bottom_nav);
                    if (nav != null) nav.setSelectedItemId(R.id.nav_settings);
                }
            });
        }

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
                    for (DataSnapshot child : snapshot.getChildren()) {
                        String key = child.getKey();
                        if (key != null && key.trim().equalsIgnoreCase("fan")) {
                            Object val = child.getValue();
                            if (val != null) {
                                String valStr = val.toString().trim();
                                if (valStr.equalsIgnoreCase("Hidup") || valStr.equalsIgnoreCase("ON")) {
                                    currentFan = "AKTIF";
                                }
                            }
                        }
                    }
                    tvFanStatus.setText(currentFan);

                    Object suhuObj = snapshot.child("suhu").getValue();
                    if (suhuObj != null) {
                        try {
                            double suhu = Double.parseDouble(suhuObj.toString());
                            updateUIBySuhu(suhu);
                            
                            if (suhu > 35 && currentFan.equals("MATI")) {
                                dbRoot.child("monitoring").child("fan").setValue("Hidup");
                            }
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
        dbRoot.child("history").addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists() && isAdded()) {
                    DataSnapshot lastChild = null;
                    String maxDur = "00:00";
                    for (DataSnapshot child : snapshot.getChildren()) {
                        lastChild = child;
                        String dur = child.child("durasi").getValue(String.class);
                        if (dur != null && dur.compareTo(maxDur) > 0) maxDur = dur;
                    }
                    tvLongestTime.setText(maxDur);
                    if (lastChild != null) {
                        tvRecentDate.setText(String.valueOf(lastChild.child("tanggal").getValue()));
                        tvRecentDuration.setText(String.valueOf(lastChild.child("durasi").getValue()));
                        Object sAkhir = lastChild.child("suhu_akhir").getValue();
                        if (sAkhir == null) sAkhir = lastChild.child("suhu").getValue();
                        if (sAkhir != null) tvRecentTemp.setText(sAkhir.toString() + "°C");
                    }
                }
            }
            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        });
    }
}
