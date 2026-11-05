# Kasirku Native

Aplikasi Android Native terpisah dari aplikasi web Kasirku.

## Teknologi
- Kotlin
- Jetpack Compose
- Room Database (fondasi katalog lokal)
- Android Gradle Plugin
- Android SDK 35
- Java 17

## Prinsip arsitektur
- Folder web dan konfigurasi Capacitor yang sudah ada tidak diubah.
- Aplikasi Native dibangun di folder `android-native/`.
- Web dan Android memakai backend serta kontrak data yang sama.
- Android dirancang local-first; transaksi lokal nantinya disinkronkan melalui antrean yang tahan aplikasi ditutup.
- Harga modal tidak disimpan pada tabel katalog biasa. Data biaya akan ditambahkan melalui penyimpanan terpisah dengan akses Owner.
- Uang disimpan sebagai Long rupiah bulat dan stok sebagai Int.

## Database lokal
Room awal menggunakan file `kasirku_room.db` dan tabel katalog:
- `catalog_products`: master produk tingkat toko.
- `catalog_categories`: kategori tingkat toko.
- `branch_product_prices`: harga khusus cabang.
- `branch_inventory`: stok integer per cabang.
- `product_barcodes`: beberapa barcode untuk satu produk.

Database lama `kasirku_local.db` dan layar produk yang sedang menggunakannya sengaja belum diubah. Pemindahan data akan dilakukan pada langkah terpisah setelah proses import dan validasi dibuat, sehingga data produk lama tidak hilang.

## Build
Build otomatis dijalankan melalui GitHub Actions workflow:
`.github/workflows/build-native-android.yml`

Output awal adalah APK debug untuk pengujian internal, bukan APK rilis Play Store.
