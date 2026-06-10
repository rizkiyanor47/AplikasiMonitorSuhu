package com.example.aplikasimonitorsuhu;

import android.app.AlertDialog;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
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
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.cardview.widget.CardView;
import androidx.core.app.NotificationCompat;
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
    private FrameLayout btnProfileNav;
    
    private final String DB_URL = "https://tofumonitor-default-rtdb.asia-southeast1.firebasedatabase.app/";
    private DatabaseReference dbMonitoring;
    private DatabaseReference dbSession;
    private DatabaseReference dbUserHistory;
    
    private ValueEventListener monitoringListener;
    private ValueEventListener sessionListener;
    
    private boolean isProcessing = false;
    private double currentSuhu = -1.0; 
    private String currentFan = "MATI";
    private long startTime = 0L;
    private boolean hasBeenHotDuringSession = false;
    private boolean isAlertShowing = false;
    private boolean isSessionLoaded = false;

    private Ringtone activeRingtone;
    private Vibrator activeVibrator;

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

        btnProfileNav.setOnClickListener(v -> {
            if (getActivity() != null) {
                BottomNavigationView bottomNav = getActivity().findViewById(R.id.bottom_nav);
                if (bottomNav != null) bottomNav.setSelectedItemId(R.id.nav_profile);
            }
        });

        return view;
    }

    private void updateTimerUI() {
        if (startTime <= 0 || tvTimer == null || !isAdded()) return;
        long diff = System.currentTimeMillis() - startTime;
        if (diff < 0) diff = 0;
        int totalSecs = (int) (diff / 1000);
        int mins = totalSecs / 60;
        int secs = totalSecs % 60;
        tvTimer.setText(String.format(Locale.getDefault(), "%02d:%02d", mins, secs));
    }

    private void startSession(boolean hot) {
        startTime = System.currentTimeMillis();
        isProcessing = true;
        hasBeenHotDuringSession = hot;
        
        tvTimer.setText("00:00");
        timerHandler.removeCallbacks(timerRunnable);
        timerHandler.post(timerRunnable);

        Map<String, Object> updates = new HashMap<>();
        updates.put("is_processing", true);
        updates.put("start_time", startTime);
        updates.put("has_been_hot", hasBeenHotDuringSession);
        dbSession.updateChildren(updates);
    }

    private void saveHistoryAndStop() {
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
        startTime = 0;
        hasBeenHotDuringSession = false;
    }

    private void startSessionMonitoring() {
        sessionListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (!isAdded()) return;

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
                    if (beenHot != null) hasBeenHotDuringSession = beenHot;
                } else {
                    isProcessing = false;
                    startTime = 0;
                    hasBeenHotDuringSession = false;
                    timerHandler.removeCallbacks(timerRunnable);
                }
                isSessionLoaded = true;
                if (currentSuhu != -1.0) updateUIBySuhu(currentSuhu);
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
                    if (tvFanStatus != null) {
                        tvFanStatus.setText(currentFan.toUpperCase());
                        if ("AKTIF".equalsIgnoreCase(currentFan) || "HIDUP".equalsIgnoreCase(currentFan)) {
                            tvFanStatus.setTextColor(getResources().getColor(R.color.status_danger));
                        } else {
                            tvFanStatus.setTextColor(getResources().getColor(R.color.tofu_brown));
                        }
                    }
                }

                Object suhuObj = snapshot.child("suhu").getValue();
                if (suhuObj != null) {
                    try {
                        currentSuhu = Double.parseDouble(suhuObj.toString());
                        if (isSessionLoaded) {
                            updateUIBySuhu(currentSuhu);
                        }
                    } catch (Exception ignored) {}
                }
            }
            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        };
        dbMonitoring.addValueEventListener(monitoringListener);
    }

    private void updateUIBySuhu(double suhu) {
        if (!isAdded() || suhu == -1.0) return;

        Context context = getContext();
        if (context == null) return;
        
        SharedPreferences sp = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE);
        float batasPanas = sp.getFloat("batas_panas", 35.0f);
        float batasAman = sp.getFloat("batas_aman", 30.0f);

        if (suhu > batasPanas) {
            tvStatus.setText(String.format(Locale.getDefault(), "PANAS! (%.1f°C)", suhu));
            cardStatus.setCardBackgroundColor(getResources().getColor(R.color.status_danger));
            
            if (!isProcessing) {
                startSession(true);
            } else if (!hasBeenHotDuringSession) {
                hasBeenHotDuringSession = true;
                dbSession.child("has_been_hot").setValue(true);
            }
        } else if (suhu >= batasAman) {
            tvStatus.setText(String.format(Locale.getDefault(), "PROSES PENDINGINAN... (%.1f°C)", suhu));
            cardStatus.setCardBackgroundColor(getResources().getColor(R.color.status_warning));
            
            if (!isProcessing) {
                startSession(true); 
            } else if (!hasBeenHotDuringSession) {
                hasBeenHotDuringSession = true;
                dbSession.child("has_been_hot").setValue(true);
            }
        } else {
            // STATUS AMAN (< 30)
            if (isProcessing && hasBeenHotDuringSession) {
                tvStatus.setText(String.format(Locale.getDefault(), "SIAP KEMAS! (%.1f°C)", suhu));
                cardStatus.setCardBackgroundColor(getResources().getColor(R.color.status_safe));
                
                if (!isAlertShowing) {
                    triggerSafeAlert(); 
                }
            } else {
                tvStatus.setText(String.format(Locale.getDefault(), "AMAN (%.1f°C)", suhu));
                cardStatus.setCardBackgroundColor(getResources().getColor(R.color.status_safe));
                if (!isProcessing) tvTimer.setText("00:00");
            }
        }
    }

    private void triggerSafeAlert() {
        if (!isAdded() || isAlertShowing) return;
        Context context = getContext();
        if (context == null) return;
        
        isAlertShowing = true;
        
        SharedPreferences sp = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE);
        boolean useSound = sp.getBoolean("use_sound", true);
        boolean useVibrate = sp.getBoolean("use_vibrate", true);

        // Getaran
        if (useVibrate) {
            activeVibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
            if (activeVibrator != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    activeVibrator.vibrate(VibrationEffect.createWaveform(new long[]{0, 500, 200, 500, 200, 500}, 0)); // 0 for repeat
                } else {
                    activeVibrator.vibrate(new long[]{0, 500, 200, 500, 200, 500}, 0);
                }
            }
        }

        // Suara
        if (useSound) {
            try {
                Uri soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
                if (soundUri == null) soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE);
                activeRingtone = RingtoneManager.getRingtone(context, soundUri);
                if (activeRingtone != null) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        activeRingtone.setLooping(true);
                    }
                    activeRingtone.play();
                }
            } catch (Exception ignored) {}
        }

        // Notifikasi Sistem
        NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        String channelId = "tofu_safe_alert";
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(channelId, "Tofu Safe Alert", NotificationManager.IMPORTANCE_HIGH);
            if (notificationManager != null) notificationManager.createNotificationChannel(channel);
        }
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, channelId)
                .setSmallIcon(R.drawable.suhutahu)
                .setContentTitle("Suhu Aman - Siap Kemas!")
                .setContentText("Suhu tahu saat ini " + currentSuhu + "°C.")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true);
        if (notificationManager != null) notificationManager.notify(101, builder.build());

        // Popup Dialog
        new AlertDialog.Builder(context)
                .setTitle("Suhu Aman")
                .setMessage("Tahu sudah dingin (" + currentSuhu + "°C). Siap untuk dikemas!")
                .setCancelable(false)
                .setPositiveButton("OK", (dialog, which) -> {
                    stopAlerts();
                    saveHistoryAndStop();
                    isAlertShowing = false;
                })
                .show();
    }

    private void stopAlerts() {
        if (activeRingtone != null && activeRingtone.isPlaying()) {
            activeRingtone.stop();
            activeRingtone = null;
        }
        if (activeVibrator != null) {
            activeVibrator.cancel();
            activeVibrator = null;
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        stopAlerts();
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
