# Aturan ProGuard untuk AplikasiMonitorSuhu

# 1. Menjaga class-class Android dasar
-keep class android.support.** { *; }
-keep class androidx.** { *; }
-dontwarn androidx.**

# 2. Menjaga class Material Components agar tidak crash saat rendering
-keep class com.google.android.material.** { *; }
-dontwarn com.google.android.material.**

# 3. Menjaga class Security Crypto (SANGAT PENTING)
# Agar algoritma enkripsi tidak di-obfuscate dan menyebabkan error
-keep class androidx.security.crypto.** { *; }
-dontwarn androidx.security.crypto.**

# 4. Menjaga class-class Activity dan Fragment Anda
-keep class com.example.aplikasimonitorsuhu.** { *; }

# 5. Aturan Obfuscation tambahan
-optimizationpasses 5
-allowaccessmodification
-dontpreverify

# 6. Menghapus log debug dari versi release untuk keamanan tambahan
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
}

# 7. Mengabaikan warning tentang class yang hilang (Solusi untuk error R8)
-ignorewarnings
-dontwarn **
