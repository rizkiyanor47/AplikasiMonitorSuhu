package com.example.aplikasimonitorsuhu;

import com.google.firebase.database.IgnoreExtraProperties;
import java.util.Locale;

@IgnoreExtraProperties
public class HistoryModel {
    public String tanggal;
    public String durasi;
    public Object suhu_akhir;
    public long timestamp;
    public int durasi_detik;

    public HistoryModel() { }

    public HistoryModel(String tanggal, String durasi, Object suhu_akhir, long timestamp) {
        this.tanggal = tanggal;
        this.durasi = durasi;
        this.suhu_akhir = suhu_akhir;
        this.timestamp = timestamp;
    }

    public HistoryModel(String tanggal, String durasi, Object suhu_akhir, long timestamp, int durasi_detik) {
        this.tanggal = tanggal;
        this.durasi = durasi;
        this.suhu_akhir = suhu_akhir;
        this.timestamp = timestamp;
        this.durasi_detik = durasi_detik;
    }

    public String getTanggal() {
        return (tanggal != null && !tanggal.equals("null")) ? tanggal : "-";
    }

    public String getDurasi() {
        return (durasi != null && !durasi.equals("null")) ? durasi : "-";
    }

    public String getSuhuAkhirString() {
        if (suhu_akhir == null) return "-";
        try {
            double suhu = Double.parseDouble(String.valueOf(suhu_akhir));
            return String.format(Locale.US, "%.1f°C", suhu);
        } catch (Exception e) {
            String val = String.valueOf(suhu_akhir).trim();
            if (val.isEmpty() || val.equals("null") || val.equals("-")) return "-";
            return val + "°C";
        }
    }

    public int getDurasiDetik() {
        if (durasi_detik > 0) return durasi_detik;
        if (durasi != null && durasi.contains(":")) {
            try {
                String[] parts = durasi.split(":");
                if (parts.length == 2) {
                    return (Integer.parseInt(parts[0]) * 60) + Integer.parseInt(parts[1]);
                }
            } catch (Exception ignored) {}
        }
        return 0;
    }
}
