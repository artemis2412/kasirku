
// ======================================
// KASIRKU V2 — PREMIUM DASHBOARD
// ======================================

const items = [
  ["Dashboard", "⌂"],
  ["Kasir", "▣"],
  ["Pesanan", "☷"],
  ["Meja", "▦"],
  ["Menu", "☕"],
  ["Dapur", "♨"],
  ["Laporan", "▤"],
  ["Pengaturan", "⚙"]
];

let current = "Dashboard";

// Format Rupiah
const money = n =>
  new Intl.NumberFormat("id-ID", {
    style: "currency",
    currency: "IDR",
    maximumFractionDigits: 0
  }).format(n);


// Navigasi
function navButton(item) {
  return `
    <button
      class="navbtn ${current === item[0] ? "active" : ""}"
      data-go="${item[0]}"
    >
      <span>${item[1]}</span>
      <span>${item[0]}</span>
    </button>
  `;
}


// Dashboard utama
function dashboard() {
  return `

    <div class="welcome">
      <div class="welcome-copy">

        <h2>
          Selamat datang di KASIRKU 👋
        </h2>

        <p class="sub">
          Ringkasan aktivitas usaha hari ini.
        </p>

        <button class="primary" data-go="Kasir">
          ＋ Transaksi Baru
        </button>

      </div>
    </div>


    <div class="stats">

      <div class="card" data-symbol="↗">
        <div class="label">
          Penjualan Hari Ini
        </div>

        <div class="value">
          ${money(0)}
        </div>

        <div class="hint">
          Belum ada transaksi
        </div>
      </div>


      <div class="card" data-symbol="▣">
        <div class="label">
          Total Pesanan
        </div>

        <div class="value">0</div>

        <div class="hint">
          Pesanan hari ini
        </div>
      </div>


      <div class="card" data-symbol="▦">
        <div class="label">
          Meja Terisi
        </div>

        <div class="value">0 / 0</div>

        <div class="hint">
          Belum ada data meja
        </div>
      </div>


      <div class="card" data-symbol="♨">
        <div class="label">
          Menu Tersedia
        </div>

        <div class="value">0</div>

        <div class="hint">
          Tambahkan menu pertama
        </div>
      </div>

    </div>


    <div class="grid" style="margin-top:18px">

      <div class="panel">

        <div class="panel-title">
          <b>📊 Grafik Penjualan</b>
          <span>Hari Ini⌄</span>
        </div>

        <div class="chart">

          <div class="chart-inner">

            <span class="chart-icon">
              ▂ ▄ ▆ ▇
            </span>

            Grafik akan tampil setelah
            transaksi pertama dicatat.

          </div>

        </div>

      </div>


      <div class="panel">

        <div class="panel-title">

          <b>▤ Pesanan Terbaru</b>

          <span data-go="Pesanan">
            Lihat Semua →
          </span>

        </div>

        <div class="empty-orders">

          <span class="big">▤</span>

          <b>Belum ada pesanan</b>

          <small>
            Pesanan yang masuk akan tampil di sini.
          </small>

        </div>

      </div>

    </div>


    <h3 class="quick-title">
      ⚡ Akses Cepat
    </h3>


    <div class="quickgrid">

      <button class="quick" data-go="Kasir">

        <span class="qicon">🧾</span>

        <b>Kasir</b>

        <small>
          Buat transaksi penjualan
        </small>

      </button>


      <button class="quick" data-go="Menu">

        <span class="qicon">🍽️</span>

        <b>Menu</b>

        <small>
          Kelola makanan dan minuman
        </small>

      </button>


      <button class="quick" data-go="Meja">

        <span class="qicon">🪑</span>

        <b>Meja</b>

        <small>
          Atur status meja pelanggan
        </small>

      </button>


      <button class="quick" data-go="Dapur">

        <span class="qicon">👨‍🍳</span>

        <b>Dapur</b>

        <small>
          Pantau dan proses pesanan
        </small>

      </button>

    </div>


    <div class="note">

      <b>Catatan tahap pengembangan:</b>

      Dashboard ini masih menggunakan data kosong.
      Fitur transaksi, database, sinkronisasi cabang,
      akun pegawai, dan printer akan dibangun bertahap.

    </div>

  `;
}


// Deskripsi setiap halaman
const descriptions = {

  Kasir:
    "Buat pesanan, pilih menu, dan proses pembayaran.",

  Pesanan:
    "Pantau pesanan makan di tempat dan dibawa pulang.",

  Meja:
    "Kelola meja kosong, terisi, dan menunggu pembayaran.",

  Menu:
    "Kelola nama menu, kategori, harga, foto, dan ketersediaan.",

  Dapur:
    "Pantau pesanan baru, sedang dibuat, dan siap disajikan.",

  Laporan:
    "Lihat omzet, transaksi, dan performa setiap cabang.",

  Pengaturan:
    "Atur profil usaha, cabang, pengguna, dan aplikasi."

};


// Menampilkan halaman
function draw() {

  document.getElementById("title").textContent =
    current;


  // Navigasi desktop
  document.getElementById("nav").innerHTML =
    items.map(navButton).join("");


  // Navigasi bawah HP
  document.getElementById("bottom").innerHTML =
    items.slice(0, 6).map(navButton).join("");


  // Konten halaman
  if (current === "Dashboard") {

    document.getElementById("view").innerHTML =
      dashboard();

  } else {

    document.getElementById("view").innerHTML = `

      <div class="placeholder">

        <h2>${current}</h2>

        <p>
          ${descriptions[current]}
        </p>

        <p>
          Bagian ini akan kita bangun berikutnya.
        </p>

        <button
          class="primary"
          data-go="Dashboard"
        >
          Kembali ke Dashboard
        </button>

      </div>

    `;

  }


  // Semua tombol navigasi
  document.querySelectorAll("[data-go]").forEach(button => {

    button.addEventListener("click", () => {

      current = button.dataset.go;

      draw();

      document
        .getElementById("sidebar")
        .classList.remove("open");

    });

  });

}


// Tanggal hari ini
document.getElementById("date").textContent =

  new Intl.DateTimeFormat("id-ID", {

    weekday: "short",
    day: "numeric",
    month: "short",
    year: "numeric"

  }).format(new Date());


// Tombol menu HP
document.getElementById("menu").onclick = () => {

  document
    .getElementById("sidebar")
    .classList.toggle("open");

};


// Pilihan cabang
document.getElementById("branch").onclick = () => {

  alert(
    "Pengaturan cabang akan dibuat pada tahap berikutnya."
  );

};


// Jalankan aplikasi
draw();
