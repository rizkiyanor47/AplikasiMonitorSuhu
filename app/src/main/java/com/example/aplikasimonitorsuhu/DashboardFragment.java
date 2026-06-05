package com.example.aplikasimonitorsuhu;

import android.content.Context;
import android.content.SharedPreferences;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Vibrator;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
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

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class DashboardFragment extends Fragment {

    private TextView tvTimer, tvStatus, tvFanStatus, tvLongestTime;
    private TextView tvRecentDate, tvRecentDuration, tvRecentTemp;
    private TextView tvProfileInitial;
    private CardView cardStatus;
    private Button btnStart;
    
    private final String DB_URL = "https://tofumonitor-default-rtdb.asia-southeast1.firebasedatabase.app/";
    private DatabaseReference dbMonitoring;
    private DatabaseReference dbUserHistory;
    private ValueEventListener monitoringListener;
    
    private boolean isProcessing = false;
    private double currentSuhu = 0.0;
    private long startTime = 0L;
    private boolean hasBeenHotDuringSession = false;
    private long lastAlertTime = 0;

    private final Handler timerHandler = new Handler(Looper.getMainLooper());
    private final Runnable timerRunnable = new Runnable() {
        @Override
        public void run() {
            if (isProcessing && startTime > 0) {
                updateTimerUI();
                timerHandler.postDelayed(this, 1000);
            }
        }
    };

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_dashboard, container, false);

        tvTimer = view.findViewById(R.id.tv_timer_val);
        tvStatus = view.findViewById(R.id.tv_status_text);
        tvFanStatus = view.findViewById(R.id.tv_fan_status);
        tvLongestTime = view.findViewById(R.id.tv_longest_value);
        cardStatus = view.findViewById(R.id.card_status);
        tvRecentDate = view.findViewById(R.id.tv_recent_date);
        tvRecentDuration = view.findViewById(R.id.tv_recent_history_detail);
        tvRecentTemp = view.findViewById(R.id.tv_recent_temp_val);
        btnStart = view.findViewById(R.id.btn_start_process);
        tvProfileInitial = view.findViewById(R.id.tv_profile_initial);

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user != null && user.getEmail() != null) {
            String email = user.getEmail();
            tvProfileInitial.setText(email.length() >= 2 ? email.substring(0, 2).toUpperCase() : email.substring(0, 1).toUpperCase());
            dbUserHistory = FirebaseDatabase.getInstance(DB_URL).getReference("users").child(user.getUid()).child("history");
        }

        dbMonitoring = FirebaseDatabase.getInstance(DB_URL).getReference("monitoring");
        
        startMonitoring();
        fetchHistoryData();

        btnStart.setOnClickListener(v -> toggleProcessing());

        return view;
    }

    private void updateTimerUI() {
        if (startTime <= 0 || tvTimer == null) return;
        long diff = System.currentTimeMillis() - startTime;
        if (diff < 0) diff = 0;
        int totalSecs = (int) (diff / 1000);
        int mins = totalSecs / 60;
        int secs = totalSecs % 60;
        tvTimer.setText(String.format(Locale.getDefault(), "%02d:%02d", mins, secs));
    }

    private void toggleProcessing() {
        if (!isProcessing) {
            startTime = System.currentTimeMillis();
            isProcessing = true;
            hasBeenHotDuringSession = (currentSuhu > 30);
            
            tvTimer.setText("00:00");
            timerHandler.removeCallbacks(timerRunnable);
            timerHandler.post(timerRunnable);

            Map<String, Object> updates = new HashMap<>();
            updates.put("is_processing", true);
            updates.put("start_time", startTime);
            updates.put("command", "START");
            updates.put("timer", "00:00");
            updates.put("has_been_hot", hasBeenHotDuringSession);
            dbMonitoring.updateChildren(updates);
            
            Toast.makeText(getContext(), "Pendinginan Dimulai", Toast.LENGTH_SHORT).show();
        } else {
            saveHistoryAndStop("Manual");
        }
    }

    private void saveHistoryAndStop(String method) {
        if (!isProcessing) return;
        long finalDiff = System.currentTimeMillis() - startTime;
        int totalSecs = Math.max(0, (int) (finalDiff / 1000));
        String durasiFinal = String.format(Locale.getDefault(), "%02d:%02d", totalSecs / 60, totalSecs % 60);

        isProcessing = false;
        timerHandler.removeCallbacks(timerRunnable);
        
        String tanggal = new SimpleDateFormat("dd MMMM yyyy", Locale.getDefault()).format(new Date());
        String suhuAkhir = String.format(Locale.getDefault(), "%.1f", currentSuhu);

        Map<String, Object> stopUpdates = new HashMap<>();
        stopUpdates.put("is_processing", false);
        stopUpdates.put("start_time", 0);
        stopUpdates.put("command", "STOP");
        stopUpdates.put("timer", durasiFinal);
        stopUpdates.put("has_been_hot", false);
        dbMonitoring.updateChildren(stopUpdates);

        if (totalSecs > 0 && dbUserHistory != null) {
            HistoryModel newHistory = new HistoryModel(tanggal, durasiFinal, suhuAkhir, System.currentTimeMillis());
            dbUserHistory.push().setValue(newHistory);
            Toast.makeText(getContext(), "Proses Selesai & Tersimpan", Toast.LENGTH_SHORT).show();
        }
        
        tvTimer.setText(durasiFinal);
        startTime = 0;
    }

    private void startMonitoring() {
        monitoringListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (!snapshot.exists() || !isAdded()) return;

                Boolean processing = snapshot.child("is_processing").getValue(Boolean.class);
                Long st = snapshot.child("start_time").getValue(Long.class);

                if (processing != null && processing && st != null && st > 0) {
                    startTime = st;
                    isProcessing = true;
                    btnStart.setText("BERHENTI");
                    updateTimerUI();
                    timerHandler.removeCallbacks(timerRunnable);
                    timerHandler.post(timerRunnable);
                } else {
                    isProcessing = false;
                    startTime = 0;
                    timerHandler.removeCallbacks(timerRunnable);
                    btnStart.setText("MULAI PROSES PENDINGINAN");
                    tvTimer.setText("00:00");
                }

                Object suhuObj = snapshot.child("suhu").getValue();
                if (suhuObj != null) {
                    try {
                        currentSuhu = Double.parseDouble(suhuObj.toString());
                        updateUIBySuhu(currentSuhu);
                    } catch (Exception ignored) {}
                }
                
                Object fanVal = snapshot.child("fan").getValue();
                if (fanVal != null && tvFanStatus != null) {
                    tvFanStatus.setText(fanVal.toString().toUpperCase());
                }
            }

            private void updateUIBySuhu(double suhu) {
                if (suhu > 30) {
                    tvStatus.setText("SUHU PANAS");
                    cardStatus.setCardBackgroundColor(getResources().getColor(R.color.status_danger));
                    triggerSecurityAlert();
                    if (isProcessing && !hasBeenHotDuringSession) {
                        hasBeenHotDuringSession = true;
                        dbMonitoring.child("has_been_hot").setValue(true);
                    }
                } else {
                    tvStatus.setText(getString(R.string.status_aman));
                    cardStatus.setCardBackgroundColor(getResources().getColor(R.color.status_safe));
                    if (isProcessing && hasBeenHotDuringSession) saveHistoryAndStop("Otomatis");
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        };
        dbMonitoring.addValueEventListener(monitoringListener);
    }

    private void triggerSecurityAlert() {
        if (getContext() == null || System.currentTimeMillis() - lastAlertTime < 5000) return;
        SharedPreferences sp = getContext().getSharedPreferences("app_settings", Context.MODE_PRIVATE);
        if (sp.getBoolean("use_vibrate", true)) {
            Vibrator v = (Vibrator) getContext().getSystemService(Context.VIBRATOR_SERVICE);
            if (v != null) v.vibrate(500);
        }
        if (sp.getBoolean("use_sound", true)) {
            try {
                Uri notification = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
                Ringtone r = RingtoneManager.getRingtone(getContext(), notification);
                r.play();
            } catch (Exception ignored) {}
        }
        lastAlertTime = System.currentTimeMillis();
    }

    private void fetchHistoryData() {
        if (dbUserHistory == null) return;
        dbUserHistory.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (!snapshot.exists() || !isAdded()) {
                    tvLongestTime.setText("00:00");
                    return;
                }
                long maxSecs = -1;
                String maxStr = "00:00";
                HistoryModel latest = null;
                long latestTs = -1;

                for (DataSnapshot child : snapshot.getChildren()) {
                    HistoryModel model = child.getValue(HistoryModel.class);
                    if (model == null) continue;
                    if (model.timestamp > latestTs) { latestTs = model.timestamp; latest = model; }
                    long secs = durationToSeconds(model.getDurasi());
                    if (secs > maxSecs) { maxSecs = secs; maxStr = model.getDurasi(); }
                }

                if (latest != null) {
                    tvRecentDate.setText(latest.getTanggal());
                    tvRecentDuration.setText(latest.getDurasi());
                    tvRecentTemp.setText(latest.getSuhuAkhirString());
                }
                tvLongestTime.setText(maxStr);
            }
            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    private long durationToSeconds(String dur) {
        try {
            String[] p = dur.split(":");
            if (p.length == 3) return Integer.parseInt(p[0]) * 3600L + Integer.parseInt(p[1]) * 60L + Integer.parseInt(p[2]);
            if (p.length == 2) return Integer.parseInt(p[0]) * 60L + Integer.parseInt(p[1]);
        } catch (Exception e) {}
        return 0;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (dbMonitoring != null && monitoringListener != null) {
            dbMonitoring.removeEventListener(monitoringListener);
        }
        timerHandler.removeCallbacks(timerRunnable);
    }
}
