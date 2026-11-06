# Kasirku Native

Aplikasi Android Native terpisah dari aplikasi web Kasirku. Keduanya berada dalam satu repository, tetapi memiliki codebase masing-masing dan memakai backend/API yang sama.

## Teknologi
- Kotlin
- Jetpack Compose
- Room Database
- Supabase Auth dan PostgREST
- WorkManager untuk sinkronisasi latar belakang
- Android SDK 35
- Java 17

## Prinsip arsitektur
- Folder web dan konfigurasi Capacitor yang sudah ada tidak diubah.
- Aplikasi Native berada di folder `android-native/`.
- Web dan Android memakai backend serta kontrak data yang sama.
- Android menggunakan pola local-first: checkout disimpan secara lokal, lalu dimasukkan ke antrean sinkronisasi yang tahan aplikasi ditutup.
- Harga modal tidak disimpan pada tabel katalog biasa; akses biaya dipisahkan untuk kebutuhan Owner.
- Uang disimpan sebagai Long rupiah bulat dan stok sebagai Int.

## Database lokal
Room menggunakan file `kasirku_room.db` dengan tabel katalog:
- `catalog_products`: master produk tingkat toko.
- `catalog_categories`: kategori tingkat toko.
- `branch_product_prices`: harga khusus cabang.
- `branch_inventory`: stok integer per cabang.
- `product_barcodes`: beberapa barcode untuk satu produk.

Checkout lokal memvalidasi tenant, produk, harga, pembayaran, dan stok dalam satu transaksi Room atomik. Penjualan serta item penjualan disimpan bersama antrean outbox untuk sinkronisasi.

Database lama `kasirku_local.db` dan layar produk yang sedang menggunakannya sengaja belum dialihkan. Pemindahan data dilakukan terpisah agar data produk lama tidak hilang.

## Akun, toko, dan cabang
- Android Native menggunakan Supabase Auth untuk identitas pengguna dan memulihkan sesi.
- Layar Akun menyediakan login email/kata sandi, pemilihan toko, dan pemilihan cabang.
- Membership toko dan cabang diverifikasi melalui data Cloud yang dilindungi Row Level Security (RLS).
- `VerifiedTenantContext` hanya diberikan setelah konteks akun, toko, dan cabang berhasil diverifikasi.
- Peran PIN lokal tidak menggantikan membership Cloud.
- Checkout lokal dan akses data dibatasi ke konteks toko/cabang yang aktif.

Kunci publishable digunakan untuk koneksi aplikasi. Jangan pernah menanam service-role key atau secret di APK. Untuk rilis produksi, nilai konfigurasi build harus disimpan melalui secret CI atau konfigurasi lokal yang tidak dikomit.

## Sinkronisasi
- Penjualan lokal masuk ke antrean outbox yang persisten.
- WorkManager menjalankan sinkronisasi saat jaringan tersedia dan menjadwalkan pemeriksaan berkala.
- Worker memulihkan sesi Supabase dan memverifikasi kembali konteks toko/cabang sebelum mengirim transaksi.
- RPC server memvalidasi identitas, tenant, perhitungan transaksi, dan idempotensi sebelum menyimpan transaksi secara atomik.
- Zona waktu cabang memakai ID IANA yang dikonfigurasi Owner/Admin. Jika belum tersedia, aplikasi memberi peringatan fallback zona waktu perangkat dan sinkronisasi ditahan.

Sinkronisasi membutuhkan koneksi internet. Perubahan skema/migrasi Supabase yang disiapkan pada branch pengembangan belum diterapkan ke project produksi.

## Pesanan dan printer
- Menu Pesanan berfokus pada transaksi harian berdasarkan tengah malam zona waktu cabang.
- Arsip transaksi 30 hari dapat diekspor sebagai CSV.
- Aplikasi mendukung printer thermal Bluetooth klasik SPP dengan format ESC/POS, pemilihan printer tersimpan, uji cetak, dan cetak ulang struk.
- Kegagalan cetak tidak membatalkan transaksi yang sudah tersimpan.
- Lebar struk saat ini ditujukan untuk 32 kolom (umumnya printer 58 mm). Kompatibilitas model printer, codepage, dan printer 80 mm tetap perlu diuji secara fisik.

## Import produk lama ke Room
`LegacyProductImporter` menyalin katalog dari `kasirku_local.db` setelah aplikasi memberikan `storeId` dan `branchId` dari konteks akun/cabang yang sudah terverifikasi.

- ID produk Room dibuat deterministik dari ID lama dan toko, sehingga proses dapat diulang tanpa menggandakan produk.
- Kategori, harga jual, foto URI, stok cabang, dan barcode unik ikut disalin.
- Barcode duplikat diabaikan saat insert agar tidak menggagalkan seluruh impor.
- Harga modal sengaja tidak disalin ke tabel katalog Room.
- Hasil impor mengembalikan jumlah sumber, berhasil, sudah pernah diimpor, dan barcode yang dilewati untuk pemeriksaan.
- Proses ini belum dipanggil otomatis dari UI. Database lama tidak dihapus dan layar produk lama belum dialihkan ke Room.

## Build dan status pengujian
Build otomatis dijalankan melalui GitHub Actions workflow:
`.github/workflows/build-native-android.yml`

Output saat ini adalah APK debug untuk pengujian internal, bukan APK rilis Play Store. Unit test dan build APK dijalankan di CI; Room instrumentation test juga disiapkan melalui Android Emulator. Sebelum rilis, lakukan pengujian perangkat nyata untuk alur login, checkout, sinkronisasi, dan printer thermal.

### Harga jual per cabang
Harga jual cabang bersifat opsional. Jika harga jual khusus cabang kosong, aplikasi menggunakan harga dasar produk sebagai harga jual. Jika harga khusus diisi, harga cabang tersebut yang dipakai saat checkout. Harga nominal pada formulir produk ditampilkan dengan pemisah ribuan Indonesia; nilai disimpan sebagai angka.
