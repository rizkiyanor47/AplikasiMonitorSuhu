package com.example.aplikasimonitorsuhu;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
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
import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;

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
    private DatabaseReference sessionRef;
    private ValueEventListener sessionListener;
    private String localSessionId = "";
    private Spinner spinnerFilter;
    private final String DB_URL = "https://tofumonitor-default-rtdb.asia-southeast1.firebasedatabase.app/";

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_history, container, false);

        rvHistory = view.findViewById(R.id.rv_history);
        rvHistory.setLayoutManager(new LinearLayoutManager(getContext()));
        historyList = new ArrayList<>();
        fullHistoryList = new ArrayList<>();
        adapter = new HistoryAdapter(historyList);
        rvHistory.setAdapter(adapter);

        spinnerFilter = view.findViewById(R.id.spinner_filter);
        setupFilterSpinner();

        TextView tvProfileInitial = view.findViewById(R.id.tv_profile_initial);
        View btnProfileNav = view.findViewById(R.id.btn_profile_nav);
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        
        if (user != null) {
            if (user.getEmail() != null) {
                String email = user.getEmail();
                String initial = email.length() >= 2 ? email.substring(0, 2).toUpperCase() : email.substring(0, 1).toUpperCase();
                if (tvProfileInitial != null) tvProfileInitial.setText(initial);
            }

            loadLocalSession();
            dbRef = FirebaseDatabase.getInstance(DB_URL).getReference("users").child(user.getUid()).child("history");
            getHistoryData();
            checkSessionSecurity(user.getUid());
        }

        if (btnProfileNav != null) {
            btnProfileNav.setOnClickListener(v -> {
                if (getActivity() != null) {
                    BottomNavigationView nav = getActivity().findViewById(R.id.bottom_nav);
                    if (nav != null) nav.setSelectedItemId(R.id.nav_profile);
                }
            });
        }

        return view;
    }

    private void loadLocalSession() {
        try {
            MasterKey masterKey = new MasterKey.Builder(requireContext()).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build();
            SharedPreferences sp = EncryptedSharedPreferences.create(requireContext(), "user_session", masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM);
            localSessionId = sp.getString("session_id", "");
        } catch (Exception e) {
            Log.e("HistoryFragment", "Gagal membaca sesi lokal", e);
        }
    }

    private void checkSessionSecurity(String userId) {
        sessionRef = FirebaseDatabase.getInstance(DB_URL).getReference("users").child(userId).child("current_session_id");
        sessionListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    String remoteSessionId = snapshot.getValue(String.class);
                    if (remoteSessionId != null && !remoteSessionId.equals(localSessionId) && !localSessionId.isEmpty()) {
                        if (getActivity() instanceof MainActivity) {
                            ((MainActivity) getActivity()).handleMultiLogin();
                        }
                    }
                }
            }
            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        };
        sessionRef.addValueEventListener(sessionListener);
    }

    private void setupFilterSpinner() {
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
        if (dbRef == null) return;
        dbRef.orderByChild("timestamp").addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                fullHistoryList.clear();
                if (snapshot.exists()) {
                    for (DataSnapshot item : snapshot.getChildren()) {
                        HistoryModel model = item.getValue(HistoryModel.class);
                        if (model != null) {
                            if (model.timestamp == 0) {
                                Date d = parseDate(model.getTanggal());
                                if (d != null) model.timestamp = d.getTime();
                            }
                            fullHistoryList.add(model);
                        }
                    }
                    Collections.reverse(fullHistoryList);
                    if (isAdded()) applyFilter(spinnerFilter.getSelectedItem().toString());
                } else {
                    historyList.clear();
                    adapter.notifyDataSetChanged();
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
            Calendar cal = Calendar.getInstance();
            cal.set(Calendar.HOUR_OF_DAY, 0);
            cal.set(Calendar.MINUTE, 0);
            cal.set(Calendar.SECOND, 0);
            cal.set(Calendar.MILLISECOND, 0);
            long startOfToday = cal.getTimeInMillis();

            Calendar calWeek = Calendar.getInstance();
            calWeek.add(Calendar.DAY_OF_YEAR, -7);
            long sevenDaysAgo = calWeek.getTimeInMillis();

            Calendar calMonth = Calendar.getInstance();
            calMonth.add(Calendar.MONTH, -1);
            long thirtyDaysAgo = calMonth.getTimeInMillis();

            for (HistoryModel item : fullHistoryList) {
                long itemTs = item.timestamp;
                if (itemTs == 0) continue;

                if (criteria.equals(getString(R.string.filter_hari))) {
                    if (itemTs >= startOfToday) historyList.add(item);
                } else if (criteria.equals(getString(R.string.filter_minggu))) {
                    if (itemTs >= sevenDaysAgo) historyList.add(item);
                } else if (criteria.equals(getString(R.string.filter_bulan))) {
                    if (itemTs >= thirtyDaysAgo) historyList.add(item);
                }
            }
        }
        adapter.notifyDataSetChanged();
    }

    private Date parseDate(String dateStr) {
        if (dateStr == null || dateStr.isEmpty() || dateStr.equals("-")) return null;
        String[] formats = {"dd MMMM yyyy", "dd-MM-yyyy", "yyyy-MM-dd", "dd/MM/yyyy"};
        for (String format : formats) {
            try {
                SimpleDateFormat sdf = new SimpleDateFormat(format, Locale.getDefault());
                return sdf.parse(dateStr);
            } catch (ParseException ignored) {}
        }
        return null;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (sessionRef != null && sessionListener != null) {
            sessionRef.removeEventListener(sessionListener);
        }
    }
}
