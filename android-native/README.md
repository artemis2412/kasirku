# Kasirku Native

Aplikasi Android Native terpisah dari aplikasi web Kasirku.

## Teknologi awal
- Kotlin
- Jetpack Compose
- Android Gradle Plugin
- Android SDK 35
- Java 17

## Prinsip arsitektur
- Folder web dan konfigurasi Capacitor yang sudah ada tidak diubah.
- Aplikasi Native dibangun di folder android-native/.
- Integrasi ke backend/API Kasirku dilakukan setelah fondasi aplikasi dan alur lokal diuji.
- Fitur transaksi offline, database lokal, akun multi-toko, sinkronisasi, dan printer Bluetooth akan ditambahkan bertahap.

## Build
Build otomatis dijalankan melalui GitHub Actions workflow:
`.github/workflows/build-native-android.yml`

Output awal adalah APK debug untuk pengujian internal, bukan APK rilis Play Store.
