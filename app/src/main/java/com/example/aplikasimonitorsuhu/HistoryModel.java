package com.example.aplikasimonitorsuhu;

import com.google.firebase.database.IgnoreExtraProperties;

@IgnoreExtraProperties
public class HistoryModel {
    public String tanggal;
    public String durasi;
    public Object suhu_akhir;
    public long timestamp;

    public HistoryModel() { }

    public HistoryModel(String tanggal, String durasi, Object suhu_akhir, long timestamp) {
        this.tanggal = tanggal;
        this.durasi = durasi;
        this.suhu_akhir = suhu_akhir;
        this.timestamp = timestamp;
    }

    public String getTanggal() {
        return (tanggal != null && !tanggal.equals("null")) ? tanggal : "-";
    }

    public String getDurasi() {
        return (durasi != null && !durasi.equals("null")) ? durasi : "-";
    }

    public String getSuhuAkhirString() {
        if (suhu_akhir == null) return "-";
        String val = String.valueOf(suhu_akhir).trim();
        if (val.isEmpty() || val.equals("null") || val.equals("-")) return "-";
        return val + "°C";
    }
}
