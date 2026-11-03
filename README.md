# KASIRKU V1 — Prototipe Android-friendly

Ini adalah prototipe PWA lokal untuk membantu menguji alur kasir toko kelontong.

## Fitur saat ini
- Dashboard omzet dan transaksi hari ini
- Daftar barang dan tambah/edit barang
- Kolom barcode/kode barang (input manual pada V1)
- Keranjang dan pembayaran tunai/transfer
- Hitung kembalian
- Riwayat transaksi
- Pemilihan nama cabang untuk simulasi
- Penyimpanan lokal browser
- Ekspor cadangan JSON
- Service worker untuk cache/offline setelah aplikasi pertama kali dibuka melalui HTTPS

## Batasan penting
- Belum ada login dan keamanan multi-pengguna.
- Pemilihan cabang masih simulasi; stok belum terpisah per cabang.
- Belum ada Supabase/cloud sync.
- Barcode kamera/Bluetooth belum diaktifkan.
- Printer Bluetooth belum diintegrasikan.
- Data lokal dapat hilang jika data browser dihapus.
- Jangan gunakan sebagai satu-satunya sistem pencatatan transaksi usaha.

## Menjalankan di Android
1. Ekstrak ZIP ini.
2. Unggah isi folder ke layanan hosting statis yang menyediakan HTTPS (misalnya GitHub Pages atau Netlify).
3. Buka alamat HTTPS tersebut di Chrome Android.
4. Pilih menu Chrome > Tambahkan ke layar utama / Install app.
5. Buka sekali saat online agar file aplikasi tersimpan untuk penggunaan offline.

Untuk pengembangan melalui HP, gunakan editor kode berbasis browser atau GitHub web editor. Untuk tahap APK native, proyek perlu dimigrasikan/dibangun dengan Flutter dan toolchain Android.

## Tahap pengembangan berikutnya
1. Validasi harga, stok, dan transaksi.
2. Tambahkan struktur stok per cabang.
3. Integrasikan Supabase Auth, PostgreSQL, RLS, dan fungsi transaksi atomik.
4. Tambahkan antrean sinkronisasi offline yang idempotent.
5. Tambahkan pemindai barcode dan printer ESC/POS Bluetooth.
6. Uji pemulihan backup dan konflik stok.
