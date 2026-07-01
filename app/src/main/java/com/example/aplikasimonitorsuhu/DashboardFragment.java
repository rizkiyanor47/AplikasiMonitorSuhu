package com.example.aplikasimonitorsuhu;

import android.Manifest;
import android.app.AlertDialog;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

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
    
    private final String DB_URL = "https://tofumonitor-default-rtdb.asia-southeast1.firebasedatabase.app";
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
    private long lastSeenTimestamp = 0;

    private float firebaseBatasPanas = 35.0f;
    private float firebaseBatasAman = 30.0f;

    private Ringtone activeRingtone;
    private Vibrator activeVibrator;

    private static final String CHANNEL_ID = "tofu_alert_channel";
    private static final int PERMISSION_REQUEST_CODE = 101;

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

    private final Handler connectionHandler = new Handler(Looper.getMainLooper());
    private final Runnable connectionRunnable = new Runnable() {
        @Override
        public void run() {
            checkConnectionHealth();
            connectionHandler.postDelayed(this, 5000);
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

        createNotificationChannel();
        checkAndRequestPermissions();

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
        connectionHandler.post(connectionRunnable);

        View.OnClickListener toHistoryListener = v -> {
            if (getActivity() != null) {
                BottomNavigationView bottomNav = getActivity().findViewById(R.id.bottom_nav);
                if (bottomNav != null) bottomNav.setSelectedItemId(R.id.nav_history);
            }
        };

        view.findViewById(R.id.btn_see_all).setOnClickListener(toHistoryListener);
        view.findViewById(R.id.btn_to_history).setOnClickListener(toHistoryListener);

        btnProfileNav.setOnClickListener(v -> {
            if (getActivity() != null) {
                BottomNavigationView bottomNav = getActivity().findViewById(R.id.bottom_nav);
                if (bottomNav != null) bottomNav.setSelectedItemId(R.id.nav_profile);
            }
        });

        return view;
    }

    private void checkConnectionHealth() {
        if (lastSeenTimestamp == 0 || !isAdded()) return;
        long diff = Math.abs(System.currentTimeMillis() - lastSeenTimestamp);
        if (diff > 15000) {
            tvStatus.setText("ALAT OFFLINE (" + currentSuhu + "°C)");
            cardStatus.setCardBackgroundColor(getResources().getColor(android.R.color.darker_gray));
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "Alert Suhu", NotificationManager.IMPORTANCE_HIGH);
            NotificationManager manager = (NotificationManager) requireContext().getSystemService(Context.NOTIFICATION_SERVICE);
            if (manager != null) manager.createNotificationChannel(channel);
        }
    }

    private void checkAndRequestPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (requireContext().checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, PERMISSION_REQUEST_CODE);
            }
        }
    }

    private void startMonitoring() {
        monitoringListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (!snapshot.exists() || !isAdded()) return;

                try {
                    if (snapshot.hasChild("batas_panas")) firebaseBatasPanas = Float.parseFloat(snapshot.child("batas_panas").getValue().toString());
                    if (snapshot.hasChild("batas_aman")) firebaseBatasAman = Float.parseFloat(snapshot.child("batas_aman").getValue().toString());
                } catch (Exception ignored) {}

                Object fanVal = snapshot.child("fan").getValue();
                if (fanVal != null) {
                    currentFan = fanVal.toString().toUpperCase();
                    tvFanStatus.setText(currentFan);
                    tvFanStatus.setTextColor(getResources().getColor(("AKTIF".equals(currentFan) || "HIDUP".equals(currentFan)) ? R.color.status_danger : R.color.tofu_brown));
                }

                Object suhuObj = snapshot.child("suhu").getValue();
                Object lastSeenObj = snapshot.child("last_seen").getValue();
                
                if (lastSeenObj instanceof Number) {
                    lastSeenTimestamp = ((Number) lastSeenObj).longValue();
                }

                if (suhuObj != null) {
                    currentSuhu = Double.parseDouble(suhuObj.toString());
                    long diff = Math.abs(System.currentTimeMillis() - lastSeenTimestamp);
                    if (diff < 15000) {
                        updateUIOnly(currentSuhu);
                        if (isSessionLoaded) handleSessionLogic(currentSuhu);
                    }
                }
            }
            @Override public void onCancelled(@NonNull DatabaseError error) {}
        };
        dbMonitoring.addValueEventListener(monitoringListener);
    }

    private void updateUIOnly(double suhu) {
        if (!isAdded()) return;
        if (suhu >= firebaseBatasPanas) {
            tvStatus.setText(String.format(Locale.getDefault(), "PANAS! (%.1f\u00b0C)", suhu));
            cardStatus.setCardBackgroundColor(getResources().getColor(R.color.status_danger));
        } else if (suhu >= firebaseBatasAman) {
            tvStatus.setText(String.format(Locale.getDefault(), "PENDINGINAN... (%.1f\u00b0C)", suhu));
            cardStatus.setCardBackgroundColor(getResources().getColor(R.color.status_warning));
        } else {
            tvStatus.setText(String.format(Locale.getDefault(), "AMAN (%.1f\u00b0C)", suhu));
            cardStatus.setCardBackgroundColor(getResources().getColor(R.color.status_safe));
        }
    }

    private void handleSessionLogic(double suhu) {
        if (suhu >= firebaseBatasAman) {
            if (!isProcessing) startSession(suhu >= firebaseBatasPanas);
            else if (suhu >= firebaseBatasPanas && !hasBeenHotDuringSession) {
                hasBeenHotDuringSession = true;
                dbSession.child("has_been_hot").setValue(true);
            }
        } else if (isProcessing && !isAlertShowing) {
            triggerSafeAlert();
        }
    }

    private void startSessionMonitoring() {
        sessionListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (!isAdded()) return;
                Boolean processing = snapshot.child("is_processing").getValue(Boolean.class);
                Long st = snapshot.child("start_time").getValue(Long.class);
                hasBeenHotDuringSession = Boolean.TRUE.equals(snapshot.child("has_been_hot").getValue(Boolean.class));

                if (Boolean.TRUE.equals(processing) && st != null && st > 0) {
                    isProcessing = true;
                    startTime = st;
                    timerHandler.removeCallbacks(timerRunnable);
                    timerHandler.post(timerRunnable);
                } else {
                    isProcessing = false;
                    startTime = 0;
                    timerHandler.removeCallbacks(timerRunnable);
                }
                isSessionLoaded = true;
            }
            @Override public void onCancelled(@NonNull DatabaseError error) { isSessionLoaded = true; }
        };
        dbSession.addValueEventListener(sessionListener);
    }

    private void startSession(boolean hot) {
        startTime = System.currentTimeMillis();
        isProcessing = true;
        hasBeenHotDuringSession = hot;
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
    }

    private void triggerSafeAlert() {
        if (!isAdded() || isAlertShowing) return;
        isAlertShowing = true;
        
        NotificationCompat.Builder builder = new NotificationCompat.Builder(requireContext(), CHANNEL_ID)
                .setSmallIcon(R.drawable.suhutahu)
                .setContentTitle("Suhu Aman - Siap Kemas!")
                .setContentText("Suhu saat ini " + currentSuhu + "°C.")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true);
        NotificationManager nm = (NotificationManager) requireContext().getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) nm.notify(1, builder.build());

        SharedPreferences sp = requireContext().getSharedPreferences("app_settings", Context.MODE_PRIVATE);
        if (sp.getBoolean("use_sound", true)) {
            try {
                Uri uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
                activeRingtone = RingtoneManager.getRingtone(getContext(), uri);
                if (activeRingtone != null) { 
                    activeRingtone.play(); 
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) activeRingtone.setLooping(true); 
                }
            } catch (Exception ignored) {}
        }
        if (sp.getBoolean("use_vibrate", true)) {
            activeVibrator = (Vibrator) requireContext().getSystemService(Context.VIBRATOR_SERVICE);
            if (activeVibrator != null) activeVibrator.vibrate(VibrationEffect.createWaveform(new long[]{0, 1000, 500, 1000}, 0));
        }

        new AlertDialog.Builder(getContext())
                .setTitle("Siap Kemas!")
                .setMessage("Tahu sudah dingin (" + currentSuhu + "°C). Sesi berakhir, tekan OK untuk simpan riwayat.")
                .setCancelable(false)
                .setPositiveButton("OK", (dialog, which) -> {
                    saveHistoryAndStop();
                    stopAlerts();
                    isAlertShowing = false;
                }).show();
    }

    private void stopAlerts() {
        if (activeRingtone != null && activeRingtone.isPlaying()) activeRingtone.stop();
        if (activeVibrator != null) activeVibrator.cancel();
    }

    private void updateTimerUI() {
        if (startTime <= 0 || tvTimer == null || !isAdded()) return;
        long diff = System.currentTimeMillis() - startTime;
        int totalSecs = (int) (diff / 1000);
        tvTimer.setText(String.format(Locale.getDefault(), "%02d:%02d", totalSecs / 60, totalSecs % 60));
    }

    private void fetchHistoryData() {
        if (dbUserHistory == null) return;
        dbUserHistory.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (!snapshot.exists() || !isAdded()) return;
                long maxSecs = -1; String maxStr = "00:00"; HistoryModel latest = null; long latestTs = -1;
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
            @Override public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    private long durationToSeconds(String dur) {
        try { String[] p = dur.split(":"); return Integer.parseInt(p[0]) * 60L + Integer.parseInt(p[1]); }
        catch (Exception e) { return 0; }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        stopAlerts();
        connectionHandler.removeCallbacks(connectionRunnable);
        timerHandler.removeCallbacks(timerRunnable);
    }
}
