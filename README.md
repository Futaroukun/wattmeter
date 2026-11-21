# Wattmeter

Aplikasi Android native untuk memantau pengisian daya baterai (charging) secara real time dengan akurasi tinggi, visualisasi canvas, dan status diagnostic perangkat.

## Fitur Utama
- **Real-Time Wattmeter**: Kalkulasi daya aktif $W = |I| \times V$ dengan moving average smoothing.
- **Hardware Fallback Chain**: `BATTERY_PROPERTY_CURRENT_NOW` -> `CURRENT_AVERAGE` -> sysfs nodes (`/sys/class/power_supply/battery/current_now`, `batt_current_now`, `bms/current_now`).
- **Deteksi Satuan Otomatis**: Otomatis mendeteksi $\mu\text{A}$ vs $\text{mA}$, disertai opsi multiplier manual.
- **Inversi Tanda Arus**: Toggle untuk chipset/OEM yang melaporkan arus pengisian bertanda negatif.
- **Panel Diagnostik**: Analisis sumber kernel ketika arus terdeteksi 0 mA.
- **Foreground Monitoring Service**: Notifikasi status bar real-time tanpa delay dengan unit metric yang dapat disesuaikan.
- **Visualisasi Canvas**: Arc Gauge interaktif dan Time-series Graph real time (Watt, mA, °C).
- **Riwayat Sesi Room**: Pencatatan riwayat sesi lengkap dengan ekspor data ke file CSV.
- **Sistem Alarm**: Notifikasi audio dan getaran untuk baterai penuh, batas persentase, dan suhu berlebih.
- **Glance AppWidget & Quick Settings Tile**: Pemantauan cepat langsung dari homescreen dan notification shade.
- **Tema**: Material You (Dynamic Color), Light, Dark, dan Pure Black OLED.
- **Privasi Total**: 100% tanpa iklan, tanpa izin internet, tanpa tracking.

## Tech Stack
- Kotlin 2.0.21
- Jetpack Compose (BOM 2024.10.01) & Material 3
- Room 2.6.1 (KSP)
- Jetpack DataStore Preferences
- Jetpack Glance AppWidget
- Target SDK 35 (Android 15), Min SDK 26 (Android 8.0)

## Build via GitHub Actions
Repository ini dilengkapi alur kerja GitHub Actions di `.github/workflows/build.yml`. Setiap push ke branch `main` atau `master` (atau melalui trigger manual `workflow_dispatch`) akan otomatis mengompilasi dan mengunggah APK Debug dan APK Release sebagai artifact yang dapat diunduh langsung tanpa Android Studio.

## Penandatanganan Rilis
Jika ingin menggunakan keystore pribadi di GitHub Actions, isi GitHub Secrets berikut: `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`.

```bash
keytool -genkeypair -v -keystore release.jks -alias wattmeter -keyalg RSA -keysize 2048 -validity 10000
base64 -w 0 release.jks
```

Untuk build lokal, set `KEYSTORE_FILE`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, dan `KEY_PASSWORD` sebagai environment variable atau di `~/.gradle/gradle.properties`. Tanpa GitHub Secrets, alur kerja otomatis menggunakan release keystore yang terpasang di repository sehingga APK update dapat langsung di-install tanpa uninstall.
