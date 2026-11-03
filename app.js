const items = [
  ["Dashboard", "⌂"],
  ["Kasir", "▣"],
  ["Pesanan", "☷"],
  ["Meja", "▦"],
  ["Menu", "☕"],
  ["Dapur", "♨"],
  ["Laporan", "▤"],
  ["Pengaturan", "⚙"],
];
let current = "Dashboard";
const money = (n) =>
  new Intl.NumberFormat("id-ID", {
    style: "currency",
    currency: "IDR",
    maximumFractionDigits: 0,
  }).format(n);
function button(x) {
  return `<button class="navbtn ${current === x[0] ? "active" : ""}" data-go="${ x[0] }">${x[1]}　${x[0]}</button>`;
}
function draw() {
  document.getElementById("title").textContent = current;
  document.getElementById("nav").innerHTML = items.map(button).join("");
  document.getElementById("bottom").innerHTML = items
    .slice(0, 5)
    .map(button)
    .join("");
  let html;
  if (current === "Dashboard") {
    html = `<div class="welcome"><div><h2>Selamat datang di KASIRKU 👋</h2><p class="sub">Ringkasan aktivitas usaha hari ini.</p></div><button class="primary" data-go="Kasir">＋ Transaksi Baru</button></div><div class="stats"><div class="card"><div class="label">Penjualan Hari Ini</div><div class="value">${money( 0 )}</div><div class="hint">Belum ada transaksi</div></div><div class="card"><div class="label">Total Pesanan</div><div class="value">0</div><div class="hint">Pesanan hari ini</div></div><div class="card"><div class="label">Meja Terisi</div><div class="value">0 / 0</div><div class="hint">Belum ada data meja</div></div><div class="card"><div class="label">Menu Tersedia</div><div class="value">0</div><div class="hint">Tambahkan menu pertama</div></div></div><h3 class="heading">Aktivitas penjualan · Hari ini</h3><div class="grid"><div class="panel"><b>Grafik penjualan</b><div class="chart">Grafik akan tampil setelah transaksi pertama dicatat.</div></div><div class="panel"><b>Akses cepat</b><div class="quickgrid"><button class="quick" data-go="Kasir">🧾<b>Buka Kasir</b><small>Buat transaksi</small></button><button class="quick" data-go="Menu">🍽️<b>Kelola Menu</b><small>Makanan & minuman</small></button><button class="quick" data-go="Meja">🪑<b>Atur Meja</b><small>Status meja</small></button><button class="quick" data-go="Dapur">👨‍🍳<b>Layar Dapur</b><small>Pantau pesanan</small></button></div></div></div><div class="note"><b>Catatan:</b> Ini kerangka dashboard. Data masih kosong; transaksi, database, akun pegawai, sinkronisasi cabang, dan printer belum diaktifkan.</div>`;
  } else {
    const d = {
      Kasir: "Buat pesanan, pilih menu, dan proses pembayaran.",
      Pesanan: "Pantau pesanan makan di tempat dan dibawa pulang.",
      Meja: "Kelola meja kosong, terisi, dan menunggu pembayaran.",
      Menu: "Kelola nama menu, kategori, harga, foto, dan ketersediaan.",
      Dapur: "Pantau pesanan baru, sedang dibuat, dan siap disajikan.",
      Laporan: "Lihat omzet, transaksi, dan performa setiap cabang.",
      Pengaturan: "Atur profil usaha, cabang, pengguna, dan aplikasi.",
    };
    html = `<div class="placeholder"><h2>${current}</h2><p>${d[current]}</p><p>Bagian ini akan kita bangun pada tahap berikutnya.</p><button class="primary" data-go="Dashboard">Kembali ke Dashboard</button></div>`;
  }
  document.getElementById("view").innerHTML = html;
  document.querySelectorAll("[data-go]").forEach(
    (b) =>
      (b.onclick = () => {
        current = b.dataset.go;
        draw();
      })
  );
}
document.getElementById("date").textContent = new Intl.DateTimeFormat("id-ID", {
  weekday: "short",
  day: "numeric",
  month: "short",
  year: "numeric",
}).format(new Date());
document.getElementById("menu").onclick = () =>
  document.getElementById("sidebar").classList.toggle("open");
document.getElementById("branch").onclick = () =>
  alert("Pengaturan cabang akan dibuat pada tahap berikutnya.");
draw();
