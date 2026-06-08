package com.example.aplikasimonitorsuhu;

import android.content.Context;
import android.content.SharedPreferences;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
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
    private FrameLayout btnProfileNav;
    
    private final String DB_URL = "https://tofumonitor-default-rtdb.asia-southeast1.firebasedatabase.app/";
    private DatabaseReference dbMonitoring;
    private DatabaseReference dbSession;
    private DatabaseReference dbUserHistory;
    
    private ValueEventListener monitoringListener;
    private ValueEventListener sessionListener;
    
    private boolean isProcessing = false;
    private double currentSuhu = 0.0;
    private String currentFan = "MATI";
    private long startTime = 0L;
    private boolean hasBeenHotDuringSession = false;
    private long lastAlertTime = 0;
    private long lastSafeAlertTime = 0;

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
        btnProfileNav = view.findViewById(R.id.btn_profile_nav);

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user != null && user.getEmail() != null) {
            String email = user.getEmail();
            tvProfileInitial.setText(email.length() >= 2 ? email.substring(0, 2).toUpperCase() : email.substring(0, 1).toUpperCase());
            dbUserHistory = FirebaseDatabase.getInstance(DB_URL).getReference("users").child(user.getUid()).child("history");
        }

        dbMonitoring = FirebaseDatabase.getInstance(DB_URL).getReference("monitoring");
        dbSession = FirebaseDatabase.getInstance(DB_URL).getReference("monitoring_session");
        
        startMonitoring();
        startSessionMonitoring();
        fetchHistoryData();

        btnStart.setOnClickListener(v -> toggleProcessing());

        btnProfileNav.setOnClickListener(v -> {
            if (getActivity() != null) {
                BottomNavigationView bottomNav = getActivity().findViewById(R.id.bottom_nav);
                if (bottomNav != null) bottomNav.setSelectedItemId(R.id.nav_profile);
            }
        });

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
            hasBeenHotDuringSession = (currentSuhu >= 30);
            lastSafeAlertTime = 0; 
            
            tvTimer.setText("00:00");
            btnStart.setText("BERHENTI");
            timerHandler.removeCallbacks(timerRunnable);
            timerHandler.post(timerRunnable);

            Map<String, Object> updates = new HashMap<>();
            updates.put("is_processing", true);
            updates.put("start_time", startTime);
            updates.put("has_been_hot", hasBeenHotDuringSession);
            dbSession.updateChildren(updates);
            
            updateUIBySuhu(currentSuhu);
            Toast.makeText(getContext(), "Pemantauan Dimulai...", Toast.LENGTH_SHORT).show();
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
        stopUpdates.put("has_been_hot", false);
        dbSession.updateChildren(stopUpdates);

        if (totalSecs > 0 && dbUserHistory != null) {
            HistoryModel newHistory = new HistoryModel(tanggal, durasiFinal, suhuAkhir, System.currentTimeMillis());
            dbUserHistory.push().setValue(newHistory);
        }
        
        tvTimer.setText(durasiFinal);
        btnStart.setText("MULAI PROSES PENDINGINAN");
        startTime = 0;
        hasBeenHotDuringSession = false;
    }

    private void startSessionMonitoring() {
        sessionListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (!isAdded()) return;

                if (!snapshot.exists()) {
                    isProcessing = false;
                    startTime = 0;
                    if (btnStart != null) btnStart.setText("MULAI PROSES PENDINGINAN");
                    return;
                }

                Boolean processing = snapshot.child("is_processing").getValue(Boolean.class);
                Long st = snapshot.child("start_time").getValue(Long.class);
                Boolean beenHot = snapshot.child("has_been_hot").getValue(Boolean.class);

                if (processing != null && processing) {
                    isProcessing = true;
                    if (st != null && st > 0) {
                        startTime = st;
                        updateTimerUI();
                        timerHandler.removeCallbacks(timerRunnable);
                        timerHandler.post(timerRunnable);
                    }
                    if (btnStart != null) btnStart.setText("BERHENTI");
                    if (beenHot != null) hasBeenHotDuringSession = beenHot;
                    updateUIBySuhu(currentSuhu);
                } else {
                    isProcessing = false;
                    startTime = 0;
                    hasBeenHotDuringSession = false;
                    timerHandler.removeCallbacks(timerRunnable);
                    if (btnStart != null) btnStart.setText("MULAI PROSES PENDINGINAN");
                }
            }
            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        };
        dbSession.addValueEventListener(sessionListener);
    }

    private void startMonitoring() {
        monitoringListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (!snapshot.exists() || !isAdded()) return;

                Object fanVal = snapshot.child("fan").getValue();
                if (fanVal != null) {
                    currentFan = fanVal.toString();
                }

                Object suhuObj = snapshot.child("suhu").getValue();
                if (suhuObj != null) {
                    try {
                        currentSuhu = Double.parseDouble(suhuObj.toString());
                        updateUIBySuhu(currentSuhu);
                    } catch (Exception ignored) {}
                }
            }
            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        };
        dbMonitoring.addValueEventListener(monitoringListener);
    }

    private void updateUIBySuhu(double suhu) {
        if (!isAdded()) return;

        // 1. Sinkronisasi Tampilan Status Kipas (ESP32 mengirim "AKTIF" atau "MATI")
        if (tvFanStatus != null) {
            tvFanStatus.setText(currentFan.toUpperCase());
            if ("AKTIF".equalsIgnoreCase(currentFan) || "HIDUP".equalsIgnoreCase(currentFan)) {
                tvFanStatus.setTextColor(getResources().getColor(R.color.status_danger));
            } else {
                tvFanStatus.setTextColor(getResources().getColor(R.color.tofu_brown));
            }
        }

        // 2. Logika Status, Warna, dan Alarm
        if (suhu > 35) {
            // STATUS PANAS (MERAH)
            tvStatus.setText(String.format(Locale.getDefault(), "PANAS! (%.1f°C)", suhu));
            cardStatus.setCardBackgroundColor(getResources().getColor(R.color.status_danger));
            
            triggerAlert(true); // Peringatan Panas
            
            if (!isProcessing) {
                autoStartSession(true);
            } else if (!hasBeenHotDuringSession) {
                hasBeenHotDuringSession = true;
                dbSession.child("has_been_hot").setValue(true);
            }

        } else if (suhu >= 30) {
            // STATUS PENDINGINAN (KUNING)
            tvStatus.setText(String.format(Locale.getDefault(), "PROSES PENDINGINAN... (%.1f°C)", suhu));
            cardStatus.setCardBackgroundColor(getResources().getColor(R.color.status_warning));
            
            if (isProcessing) {
                triggerAlert(true); 
            } else {
                autoStartSession(false);
            }

        } else {
            // STATUS AMAN (< 30) (HIJAU)
            if (isProcessing && hasBeenHotDuringSession) {
                tvStatus.setText(String.format(Locale.getDefault(), "SIAP KEMAS! (%.1f°C)", suhu));
                cardStatus.setCardBackgroundColor(getResources().getColor(R.color.status_safe));
                
                triggerAlert(false); // Alert Aman
                saveHistoryAndStop("Otomatis");
            } else {
                tvStatus.setText(String.format(Locale.getDefault(), "AMAN (%.1f°C)", suhu));
                cardStatus.setCardBackgroundColor(getResources().getColor(R.color.status_safe));
                
                if (!isProcessing) {
                    tvTimer.setText("00:00");
                }
            }
        }
    }

    private void autoStartSession(boolean hot) {
        startTime = System.currentTimeMillis();
        isProcessing = true;
        hasBeenHotDuringSession = hot;

        Map<String, Object> updates = new HashMap<>();
        updates.put("is_processing", true);
        updates.put("start_time", startTime);
        updates.put("has_been_hot", hot);
        dbSession.updateChildren(updates);

        timerHandler.removeCallbacks(timerRunnable);
        timerHandler.post(timerRunnable);
        if (btnStart != null) btnStart.setText("BERHENTI");
    }

    private void triggerAlert(boolean isDanger) {
        if (!isAdded()) return;
        Context context = getContext();
        if (context == null) return;
        
        long now = System.currentTimeMillis();
        if (isDanger) {
            if (now - lastAlertTime < 10000) return;
            lastAlertTime = now;
        } else {
            if (now - lastSafeAlertTime < 15000) return;
            lastSafeAlertTime = now;
        }
        
        SharedPreferences sp = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE);
        boolean useSound = sp.getBoolean("use_sound", true);
        boolean useVibrate = sp.getBoolean("use_vibrate", true);

        if (useVibrate) {
            Vibrator v = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
            if (v != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    v.vibrate(VibrationEffect.createWaveform(new long[]{0, 600, 200, 600}, -1));
                } else {
                    v.vibrate(new long[]{0, 600, 200, 600}, -1);
                }
            }
        }

        if (useSound) {
            try {
                Uri soundUri = RingtoneManager.getDefaultUri(isDanger ? RingtoneManager.TYPE_NOTIFICATION : RingtoneManager.TYPE_ALARM);
                if (soundUri == null) soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE);
                Ringtone r = RingtoneManager.getRingtone(context, soundUri);
                if (r != null) r.play();
            } catch (Exception ignored) {}
        }
        
        Toast.makeText(context, isDanger ? "PERINGATAN: SUHU PANAS!" : "SUHU AMAN: SIAP DIKEMAS!", Toast.LENGTH_LONG).show();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (dbMonitoring != null && monitoringListener != null) dbMonitoring.removeEventListener(monitoringListener);
        if (dbSession != null && sessionListener != null) dbSession.removeEventListener(sessionListener);
        timerHandler.removeCallbacks(timerRunnable);
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
}
