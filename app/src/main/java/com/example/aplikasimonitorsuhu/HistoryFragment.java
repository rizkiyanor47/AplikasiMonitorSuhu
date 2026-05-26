package com.example.aplikasimonitorsuhu;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
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

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class HistoryFragment extends Fragment {

    private RecyclerView rvHistory;
    private HistoryAdapter adapter;
    private List<HistoryModel> historyList; 
    private List<HistoryModel> fullHistoryList; 
    private DatabaseReference dbRef;
    private Spinner spinnerFilter;
    private final String DB_URL = "https://tofumonitor-default-rtdb.asia-southeast1.firebasedatabase.app/";

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_history, container, false);

        // 1. Inisialisasi RecyclerView
        rvHistory = view.findViewById(R.id.rv_history);
        rvHistory.setLayoutManager(new LinearLayoutManager(getContext()));
        historyList = new ArrayList<>();
        fullHistoryList = new ArrayList<>();
        adapter = new HistoryAdapter(historyList);
        rvHistory.setAdapter(adapter);

        // 2. Setup Spinner Filter
        spinnerFilter = view.findViewById(R.id.spinner_filter);
        setupFilterSpinner();

        // 3. Inisial Profil
        TextView tvProfileInitial = view.findViewById(R.id.tv_profile_initial);
        View btnProfileNav = view.findViewById(R.id.btn_profile_nav);
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user != null && user.getEmail() != null) {
            String email = user.getEmail();
            String initial = email.length() >= 2 ? email.substring(0, 2).toUpperCase() : email.substring(0, 1).toUpperCase();
            if (tvProfileInitial != null) tvProfileInitial.setText(initial);
        }

        if (btnProfileNav != null) {
            btnProfileNav.setOnClickListener(v -> {
                if (getActivity() != null) {
                    BottomNavigationView nav = getActivity().findViewById(R.id.bottom_nav);
                    if (nav != null) nav.setSelectedItemId(R.id.nav_profile);
                }
            });
        }

        // 4. Ambil data dari Firebase
        dbRef = FirebaseDatabase.getInstance(DB_URL).getReference("history");
        getHistoryData();

        return view;
    }

    private void setupFilterSpinner() {
        // Label sesuai permintaan: Per Hari, Per Minggu, Per Bulan
        String[] options = {
                getString(R.string.filter_semua), 
                getString(R.string.filter_hari), 
                getString(R.string.filter_minggu), 
                getString(R.string.filter_bulan)
        };
        
        ArrayAdapter<String> spinnerAdapter = new ArrayAdapter<>(requireContext(), android.R.layout.simple_spinner_item, options);
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerFilter.setAdapter(spinnerAdapter);

        spinnerFilter.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                applyFilter(options[position]);
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
    }

    private void getHistoryData() {
        dbRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                fullHistoryList.clear();
                if (snapshot.exists()) {
                    for (DataSnapshot item : snapshot.getChildren()) {
                        String tgl = "-", dur = "-";
                        Object suhu = "-";

                        if (item.hasChild("tanggal")) tgl = String.valueOf(item.child("tanggal").getValue()).trim();
                        if (item.hasChild("durasi")) dur = String.valueOf(item.child("durasi").getValue()).trim();
                        
                        Object sVal = item.child("suhu_akhir").getValue();
                        if (sVal == null) sVal = item.child("suhu").getValue();
                        if (sVal != null) suhu = sVal;

                        fullHistoryList.add(new HistoryModel(tgl, dur, suhu));
                    }
                    Collections.reverse(fullHistoryList);
                    if (isAdded()) applyFilter(spinnerFilter.getSelectedItem().toString());
                }
            }
            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    private void applyFilter(String criteria) {
        historyList.clear();
        
        if (criteria.equals(getString(R.string.filter_semua))) {
            historyList.addAll(fullHistoryList);
        } else {
            long nowMs = System.currentTimeMillis();
            long dayMs = 24 * 60 * 60 * 1000L;

            for (HistoryModel item : fullHistoryList) {
                Date date = parseDate(item.getTanggal());
                if (date != null) {
                    long itemMs = date.getTime();
                    long diff = nowMs - itemMs;

                    if (criteria.equals(getString(R.string.filter_hari))) {
                        // Hanya hari ini
                        if (isToday(date)) historyList.add(item);
                    } else if (criteria.equals(getString(R.string.filter_minggu))) {
                        // 7 hari terakhir
                        if (diff >= 0 && diff <= (7 * dayMs)) historyList.add(item);
                    } else if (criteria.equals(getString(R.string.filter_bulan))) {
                        // 30 hari terakhir
                        if (diff >= 0 && diff <= (30 * dayMs)) historyList.add(item);
                    }
                }
            }
        }
        adapter.notifyDataSetChanged();

        if (historyList.isEmpty() && !criteria.equals(getString(R.string.filter_semua)) && isAdded()) {
            Toast.makeText(getContext(), "Tidak ada riwayat untuk periode ini", Toast.LENGTH_SHORT).show();
        }
    }

    private Date parseDate(String dateStr) {
        if (dateStr == null || dateStr.isEmpty() || dateStr.equals("-")) return null;
        
        // Coba berbagai format umum agar tidak kosong
        String[] formats = {
                "dd-MM-yyyy", "yyyy-MM-dd", "dd/MM/yyyy", "MM/dd/yyyy", 
                "d-M-yyyy", "dd-MMM-yyyy", "d MMMM yyyy"
        };

        for (String format : formats) {
            try {
                SimpleDateFormat sdf = new SimpleDateFormat(format, Locale.getDefault());
                sdf.setLenient(false);
                return sdf.parse(dateStr);
            } catch (ParseException ignored) {}
        }
        return null;
    }

    private boolean isToday(Date date) {
        Calendar c1 = Calendar.getInstance();
        Calendar c2 = Calendar.getInstance();
        c1.setTime(date);
        c2.setTime(new Date());
        return c1.get(Calendar.YEAR) == c2.get(Calendar.YEAR) &&
               c1.get(Calendar.DAY_OF_YEAR) == c2.get(Calendar.DAY_OF_YEAR);
    }
}
