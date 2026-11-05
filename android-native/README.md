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

## Import produk lama ke Room

`LegacyProductImporter` menyalin katalog dari `kasirku_local.db` setelah aplikasi memberikan `storeId` dan `branchId` dari konteks akun/cabang yang sudah terverifikasi.

- ID produk Room dibuat deterministik dari ID lama dan toko, sehingga proses dapat diulang tanpa menggandakan produk.
- Kategori, harga jual, foto URI, stok cabang, dan barcode unik ikut disalin.
- Barcode duplikat diabaikan saat insert agar tidak menggagalkan seluruh impor.
- Harga modal sengaja tidak disalin ke tabel katalog Room; nilai aslinya tetap berada di database lama untuk penanganan khusus Owner pada tahap berikutnya.
- Hasil impor mengembalikan jumlah sumber, berhasil, sudah pernah diimpor, dan barcode yang dilewati untuk pemeriksaan.
- Proses ini belum dipanggil otomatis dari UI. Database lama tidak dihapus dan layar produk lama belum dialihkan.

## Konteks akun, toko, dan cabang

Android Native wajib menggunakan identitas Cloud yang diverifikasi, bukan menganggap sesi PIN lokal sebagai bukti akses server.

Kontrak data yang sudah dipakai modul Cloud Web:
- Supabase Auth menyediakan identitas pengguna melalui auth.getUser().
- store_memberships menentukan toko aktif dan peran (owner, admin, cashier). Untuk tahap awal, akun dengan lebih dari satu keanggotaan aktif ditahan sampai pemilihan toko tersedia.
- stores memvalidasi bahwa data toko dapat diakses.
- branches menyediakan cabang aktif milik toko.
- branch_memberships membatasi cabang untuk Admin/Kasir; Owner dapat melihat cabang aktif toko.
- Peran PIN lokal hanya boleh dicocokkan sebagai pemeriksaan tambahan, tidak boleh menggantikan membership Cloud.

Android sudah memiliki model VerifiedTenantContext dan TenantContextProvider sebagai kontrak. Belum ada provider autentikasi yang mengisinya, sehingga UI katalog Room belum boleh memakai ID toko/cabang buatan atau mengimpor data secara otomatis. Langkah integrasi berikutnya adalah menambahkan Supabase Auth Android, mengambil membership dan cabang melalui RLS, lalu baru menyediakan konteks terverifikasi ke repository.

Izin jaringan Android (INTERNET) sudah ditambahkan untuk kebutuhan koneksi Supabase. Kunci publishable harus disediakan melalui konfigurasi build/runtime; jangan menanam service-role key atau secret di APK.

## Login Cloud Android

- Android Native menggunakan Supabase Auth melalui SDK Kotlin (`auth-kt`) dan PostgREST (`postgrest-kt`).
- Layar Akun menyediakan login email/kata sandi, pemulihan sesi, daftar toko yang dapat diakses, dan pilihan cabang aktif.
- Data toko/cabang diambil setelah login dari `store_memberships`, `stores`, `branches`, dan `branch_memberships`; pembatasan server tetap mengikuti Row Level Security (RLS).
- Akun dengan beberapa toko dapat memilih toko. Admin/Kasir hanya melihat cabang yang ditetapkan pada `branch_memberships`.
- Kunci yang dipakai adalah publishable key (bukan service-role/secret). Untuk rilis produksi, pindahkan nilai konfigurasi build ke secret CI atau konfigurasi lokal yang tidak dikomit.
- Pemilihan toko/cabang menghasilkan `VerifiedTenantContext`. Konteks ini belum otomatis mengalihkan layar Produk dari database lama ke Room; integrasi katalog dilakukan setelah proses login dan pemilihan cabang diuji pada perangkat.
