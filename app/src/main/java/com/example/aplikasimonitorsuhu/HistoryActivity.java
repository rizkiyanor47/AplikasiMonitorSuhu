package com.example.aplikasimonitorsuhu;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.bottomnavigation.BottomNavigationView;

import java.util.ArrayList;
import java.util.List;

public class HistoryActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_history);

        BottomNavigationView bottomNav = findViewById(R.id.bottom_nav);
        bottomNav.setSelectedItemId(R.id.nav_history);

        ListView lvHistory = findViewById(R.id.lv_history);
        List<HistoryRecord> dummyData = getDummyData();
        HistoryAdapter adapter = new HistoryAdapter(dummyData);
        lvHistory.setAdapter(adapter);

        bottomNav.setOnItemSelectedListener(item -> {
            int itemId = item.getItemId();
            if (itemId == R.id.nav_history) {
                return true;
            }

            Intent intent = null;
            if (itemId == R.id.nav_dashboard) {
                intent = new Intent(this, MainActivity.class);
            } else if (itemId == R.id.nav_settings) {
                intent = new Intent(this, SettingsActivity.class);
            } else if (itemId == R.id.nav_profile) {
                intent = new Intent(this, ProfileActivity.class);
            }

            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                startActivity(intent);
                overridePendingTransition(0, 0);
                return true;
            }
            return false;
        });
    }

    private List<HistoryRecord> getDummyData() {
        List<HistoryRecord> data = new ArrayList<>();
        data.add(new HistoryRecord("30 Apr 2026", "15 min", "85°C", "Completed"));
        data.add(new HistoryRecord("29 Apr 2026", "20 min", "92°C", "Completed"));
        data.add(new HistoryRecord("28 Apr 2026", "18 min", "78°C", "Completed"));
        data.add(new HistoryRecord("27 Apr 2026", "25 min", "98°C", "Benchmark"));
        data.add(new HistoryRecord("26 Apr 2026", "12 min", "65°C", "Completed"));
        data.add(new HistoryRecord("25 Apr 2026", "22 min", "88°C", "Completed"));
        data.add(new HistoryRecord("24 Apr 2026", "15 min", "72°C", "Completed"));
        data.add(new HistoryRecord("23 Apr 2026", "30 min", "100°C", "Benchmark"));
        data.add(new HistoryRecord("22 Apr 2026", "18 min", "55°C", "Completed"));
        data.add(new HistoryRecord("21 Apr 2026", "14 min", "45°C", "Completed"));
        return data;
    }

    static class HistoryRecord {
        String date, duration, temp, status;
        HistoryRecord(String d, String dur, String t, String s) {
            this.date = d;
            this.duration = dur;
            this.temp = t;
            this.status = s;
        }
    }

    class HistoryAdapter extends BaseAdapter {
        List<HistoryRecord> items;
        HistoryAdapter(List<HistoryRecord> items) { this.items = items; }
        @Override public int getCount() { return items.size(); }
        @Override public Object getItem(int i) { return items.get(i); }
        @Override public long getItemId(int i) { return i; }
        @Override public View getView(int i, View view, ViewGroup viewGroup) {
            if (view == null) {
                view = LayoutInflater.from(HistoryActivity.this).inflate(R.layout.item_history, viewGroup, false);
            }
            HistoryRecord item = items.get(i);
            ((TextView) view.findViewById(R.id.tv_item_date)).setText(item.date);
            ((TextView) view.findViewById(R.id.tv_item_duration)).setText(item.duration);
            ((TextView) view.findViewById(R.id.tv_item_temp)).setText(item.temp);
            ((TextView) view.findViewById(R.id.tv_item_status)).setText(item.status);
            return view;
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        BottomNavigationView bottomNav = findViewById(R.id.bottom_nav);
        if (bottomNav != null) {
            bottomNav.setSelectedItemId(R.id.nav_history);
        }
    }
}