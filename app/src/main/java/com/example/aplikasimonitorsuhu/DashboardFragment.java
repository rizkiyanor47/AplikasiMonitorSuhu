package com.example.aplikasimonitorsuhu;

import android.Manifest;
import android.app.AlertDialog;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.media.AudioAttributes;
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
import com.google.firebase.database.ServerValue;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class DashboardFragment extends Fragment implements MainActivity.EspConnectionListener {

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
    private ValueEventListener historyListener;
    private ValueEventListener longestListener;
    
    private boolean isProcessing = false;
    private double currentSuhu = -1.0; 
    private long startTime = 0L;
    private boolean hasBeenHotDuringSession = false;
    private boolean isAlertShowing = false;
    private boolean isSessionLoaded = false;

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
        if (user != null) {
            String email = user.getEmail();
            if (email != null && !email.isEmpty()) {
                tvProfileInitial.setText(email.length() >= 2 ? email.substring(0, 2).toUpperCase() : email.substring(0, 1).toUpperCase());
            } else {
                tvProfileInitial.setText("U");
            }
            dbUserHistory = FirebaseDatabase.getInstance(DB_URL).getReference("users").child(user.getUid()).child("history");
        }

        dbMonitoring = FirebaseDatabase.getInstance(DB_URL).getReference("monitoring");
        dbSession = FirebaseDatabase.getInstance(DB_URL).getReference("monitoring_session");
        
        startMonitoring();
        startSessionMonitoring();
        fetchHistoryData();

        cardStatus.setOnLongClickListener(v -> {
            sendTestTemperature();
            return true;
        });

        View.OnClickListener toHistoryListener = v -> {
            if (getActivity() != null) {
                BottomNavigationView bottomNav = getActivity().findViewById(R.id.bottom_nav);
                if (bottomNav != null) {
                    bottomNav.setSelectedItemId(R.id.nav_history);
                }
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

    private void sendTestTemperature() {
        double rawSuhu = 30 + (Math.random() * 10);
        final double finalSuhu = Math.round(rawSuhu * 10.0) / 10.0;
        
        Map<String, Object> testData = new HashMap<>();
        testData.put("suhu", finalSuhu);
        testData.put("last_seen", ServerValue.TIMESTAMP);
        
        dbMonitoring.updateChildren(testData).addOnCompleteListener(task -> {
            if (isAdded() && task.isSuccessful()) {
                String msg = String.format(Locale.US, "Simulasi suhu terkirim: %.1f°C", finalSuhu);
                Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show();
            }
        });
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
                if (fanVal != null) tvFanStatus.setText(fanVal.toString().toUpperCase());

                Object suhuObj = snapshot.child("suhu").getValue();
                if (suhuObj != null) {
                    try {
                        currentSuhu = Double.parseDouble(suhuObj.toString());
                        updateUIStatus(currentSuhu);
                        if (isSessionLoaded) handleSessionLogic(currentSuhu);
                    } catch (Exception ignored) {}
                }
            }
            @Override public void onCancelled(@NonNull DatabaseError error) {}
        };
        dbMonitoring.addValueEventListener(monitoringListener);
    }

    private void updateUIStatus(double suhu) {
        if (!isAdded()) return;
        boolean isConnected = true;
        if (getActivity() instanceof MainActivity) isConnected = ((MainActivity) getActivity()).getEspConnectionStatus();

        String suhuStr = String.format(Locale.US, "%.1f", suhu);

        if (!isConnected) {
            tvStatus.setText("OFFLINE - " + suhuStr + "°C");
            cardStatus.setCardBackgroundColor(getResources().getColor(android.R.color.darker_gray));
        } else if (suhu >= firebaseBatasPanas) {
            tvStatus.setText("PANAS: " + suhuStr + "°C");
            cardStatus.setCardBackgroundColor(getResources().getColor(R.color.status_danger));
        } else if (suhu >= firebaseBatasAman) {
            tvStatus.setText("PENDINGINAN: " + suhuStr + "°C");
            cardStatus.setCardBackgroundColor(getResources().getColor(R.color.status_warning));
        } else {
            tvStatus.setText("AMAN: " + suhuStr + "°C");
            cardStatus.setCardBackgroundColor(getResources().getColor(R.color.status_safe));
        }
    }

    private void handleSessionLogic(double suhu) {
        if (suhu > firebaseBatasAman) {
            if (!isProcessing) {
                startSession(suhu >= firebaseBatasPanas);
            } else if (suhu >= firebaseBatasPanas && !hasBeenHotDuringSession) {
                hasBeenHotDuringSession = true;
                dbSession.child("has_been_hot").setValue(true);
            }
        } 
        else if (suhu <= firebaseBatasAman && isProcessing && !isAlertShowing) {
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
                if (currentSuhu != -1.0) updateUIStatus(currentSuhu);
            }
            @Override public void onCancelled(@NonNull DatabaseError error) { isSessionLoaded = true; }
        };
        dbSession.addValueEventListener(sessionListener);
    }

    private void startSession(boolean hot) {
        startTime = System.currentTimeMillis();
        isProcessing = true;
        hasBeenHotDuringSession = hot;
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
        
        Map<String, Object> stopUpdates = new HashMap<>();
        stopUpdates.put("is_processing", false);
        stopUpdates.put("start_time", 0);
        stopUpdates.put("has_been_hot", false);
        dbSession.updateChildren(stopUpdates);

        if (totalSecs > 0 && dbUserHistory != null) {
            String tanggal = new SimpleDateFormat("dd MMMM yyyy", Locale.getDefault()).format(new Date());
            String suhuSimpan = String.format(Locale.US, "%.1f", currentSuhu);
            HistoryModel newHistory = new HistoryModel(tanggal, durasiFinal, suhuSimpan, System.currentTimeMillis(), totalSecs);
            dbUserHistory.push().setValue(newHistory);
        }
        tvTimer.setText(durasiFinal);
        startTime = 0;
    }

    private void triggerSafeAlert() {
        if (!isAdded() || isAlertShowing) return;
        isAlertShowing = true;
        
        String suhuStr = String.format(Locale.US, "%.1f", currentSuhu);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(requireContext(), CHANNEL_ID)
                .setSmallIcon(R.drawable.suhutahu)
                .setContentTitle("Suhu Aman")
                .setContentText("Suhu saat ini: " + suhuStr + "°C. Proses selesai.")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true);
        NotificationManager nm = (NotificationManager) requireContext().getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) nm.notify(1, builder.build());

        SharedPreferences sp = requireContext().getSharedPreferences("app_settings", Context.MODE_PRIVATE);

        if (sp.getBoolean("use_sound", true)) {
            try {
                Uri alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
                if (alarmUri == null) alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE);
                activeRingtone = RingtoneManager.getRingtone(requireContext(), alarmUri);
                if (activeRingtone != null) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        activeRingtone.setAudioAttributes(new AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_ALARM)
                                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                                .build());
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) activeRingtone.setLooping(true);
                    activeRingtone.play();
                }
            } catch (Exception e) { Log.e("Alert", "Gagal putar suara", e); }
        }

        if (sp.getBoolean("use_vibrate", true)) {
            activeVibrator = (Vibrator) requireContext().getSystemService(Context.VIBRATOR_SERVICE);
            if (activeVibrator != null && activeVibrator.hasVibrator()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    activeVibrator.vibrate(VibrationEffect.createWaveform(new long[]{0, 1000, 500}, 0));
                } else {
                    activeVibrator.vibrate(new long[]{0, 1000, 500}, 0);
                }
            }
        }

        new AlertDialog.Builder(requireContext())
                .setTitle("Pendinginan Selesai")
                .setMessage("Suhu sudah aman (" + suhuStr + "°C). Hentikan peringatan dan simpan riwayat?")
                .setCancelable(false)
                .setPositiveButton("Ya", (dialog, which) -> {
                    stopAlertResources();
                    saveHistoryAndStop();
                    isAlertShowing = false;
                })
                .show();
    }

    private void stopAlertResources() {
        if (activeRingtone != null && activeRingtone.isPlaying()) activeRingtone.stop();
        if (activeVibrator != null) activeVibrator.cancel();
    }

    private void updateTimerUI() {
        long diff = System.currentTimeMillis() - startTime;
        int seconds = (int) (diff / 1000);
        tvTimer.setText(String.format(Locale.getDefault(), "%02d:%02d", seconds / 60, seconds % 60));
    }

    private void fetchHistoryData() {
        if (dbUserHistory == null) return;
        
        historyListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (!snapshot.exists() || !isAdded()) return;
                for (DataSnapshot ds : snapshot.getChildren()) {
                    HistoryModel model = ds.getValue(HistoryModel.class);
                    if (model != null) {
                        tvRecentDate.setText(model.getTanggal());
                        tvRecentDuration.setText("Durasi: " + model.getDurasi());
                        tvRecentTemp.setText(model.getSuhuAkhirString());
                    }
                }
            }
            @Override public void onCancelled(@NonNull DatabaseError error) {}
        };
        dbUserHistory.orderByChild("timestamp").limitToLast(1).addValueEventListener(historyListener);

        longestListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (!isAdded()) return;
                if (!snapshot.exists()) {
                    tvLongestTime.setText("-");
                    return;
                }
                
                HistoryModel longestModel = null;
                int maxSecs = -1;
                
                for (DataSnapshot ds : snapshot.getChildren()) {
                    HistoryModel model = ds.getValue(HistoryModel.class);
                    if (model != null) {
                        int currentSecs = model.getDurasiDetik();
                        if (currentSecs > maxSecs) {
                            maxSecs = currentSecs;
                            longestModel = model;
                        }
                    }
                }
                
                if (longestModel != null) {
                    tvLongestTime.setText(longestModel.getDurasi());
                } else {
                    tvLongestTime.setText("-");
                }
            }
            @Override public void onCancelled(@NonNull DatabaseError error) {}
        };
        dbUserHistory.addValueEventListener(longestListener);
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "Tofu Alerts", NotificationManager.IMPORTANCE_HIGH);
            NotificationManager nm = requireContext().getSystemService(NotificationManager.class);
            if (nm != null) nm.createNotificationChannel(channel);
        }
    }

    private void checkAndRequestPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (requireContext().checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, PERMISSION_REQUEST_CODE);
            }
        }
    }

    @Override
    public void onEspConnectionChanged(boolean isConnected) {
        if (isAdded()) updateUIStatus(currentSuhu);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (dbMonitoring != null && monitoringListener != null) dbMonitoring.removeEventListener(monitoringListener);
        if (dbSession != null && sessionListener != null) dbSession.removeEventListener(sessionListener);
        if (dbUserHistory != null) {
            if (historyListener != null) dbUserHistory.removeEventListener(historyListener);
            if (longestListener != null) dbUserHistory.removeEventListener(longestListener);
        }
        timerHandler.removeCallbacks(timerRunnable);
        stopAlertResources();
    }
}
