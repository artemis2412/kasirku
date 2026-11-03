
// ======================================
// KASIRKU V2 — DASHBOARD + KELOLA MENU
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
let editingId = null;
let searchText = "";
let categoryFilter = "Semua";

// Penyimpanan menu di perangkat
function loadMenus() {
  try {
    return JSON.parse(localStorage.getItem("kasirku_menus")) || [];
  } catch {
    return [];
  }
}

let menus = loadMenus();

function saveMenus() {
  localStorage.setItem("kasirku_menus", JSON.stringify(menus));
}

// Format Rupiah
const money = n =>
  new Intl.NumberFormat("id-ID", {
    style: "currency",
    currency: "IDR",
    maximumFractionDigits: 0
  }).format(n);

// Pengaman teks HTML
function safe(text) {
  return String(text).replace(/[&<>"']/g, char => ({
    "&": "&amp;",
    "<": "&lt;",
    ">": "&gt;",
    '"': "&quot;",
    "'": "&#039;"
  })[char]);
}

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

// Dashboard
function dashboard() {
  const available = menus.filter(menu => menu.available).length;

  return `
    <div class="welcome">
      <div class="welcome-copy">
        <h2>Selamat datang di KASIRKU 👋</h2>
        <p class="sub">Ringkasan aktivitas usaha hari ini.</p>
        <button class="primary" data-go="Kasir">
          ＋ Transaksi Baru
        </button>
      </div>
    </div>

    <div class="stats">
      <div class="card">
        <div class="label">Penjualan Hari Ini</div>
        <div class="value">${money(0)}</div>
        <div class="hint">Belum ada transaksi</div>
      </div>

      <div class="card">
        <div class="label">Total Pesanan</div>
        <div class="value">0</div>
        <div class="hint">Pesanan hari ini</div>
      </div>

      <div class="card">
        <div class="label">Meja Terisi</div>
        <div class="value">0 / 0</div>
        <div class="hint">Belum ada data meja</div>
      </div>

      <div class="card">
        <div class="label">Menu Tersedia</div>
        <div class="value">${available}</div>
        <div class="hint">${menus.length} menu terdaftar</div>
      </div>
    </div>

    <h3 class="heading">Aktivitas penjualan · Hari ini</h3>

    <div class="grid">
      <div class="panel">
        <div class="panel-title">
          <b>📊 Grafik Penjualan</b>
          <span>Hari Ini</span>
        </div>
        <div class="chart">
          Grafik akan tampil setelah transaksi pertama dicatat.
        </div>
      </div>

      <div class="panel">
        <div class="panel-title">
          <b>▤ Pesanan Terbaru</b>
          <span data-go="Pesanan">Lihat Semua →</span>
        </div>
        <div class="empty-orders">
          <span class="big">▤</span>
          <b>Belum ada pesanan</b>
          <small>Pesanan yang masuk akan tampil di sini.</small>
        </div>
      </div>
    </div>

    <h3 class="quick-title">⚡ Akses Cepat</h3>

    <div class="quickgrid">
      <button class="quick" data-go="Kasir">
        <span class="qicon">🧾</span>
        <b>Kasir</b>
        <small>Buat transaksi penjualan</small>
      </button>

      <button class="quick" data-go="Menu">
        <span class="qicon">🍽️</span>
        <b>Menu</b>
        <small>Kelola makanan dan minuman</small>
      </button>

      <button class="quick" data-go="Meja">
        <span class="qicon">🪑</span>
        <b>Meja</b>
        <small>Atur status meja pelanggan</small>
      </button>

      <button class="quick" data-go="Dapur">
        <span class="qicon">👨‍🍳</span>
        <b>Dapur</b>
        <small>Pantau pesanan</small>
      </button>
    </div>

    <div class="note">
      <b>Catatan:</b> Dashboard masih dalam tahap pengembangan.
      Transaksi, database online, akun pegawai, dan printer
      belum diaktifkan.
    </div>
  `;
}

// Tampilan halaman Menu
function menuPage() {
  const categories = ["Semua", "Makanan", "Minuman", "Snack", "Lainnya"];

  const filtered = menus.filter(menu => {
    const matchSearch = menu.name.toLowerCase().includes(searchText.toLowerCase());
    const matchCategory = categoryFilter === "Semua" ||
      menu.category === categoryFilter;

    return matchSearch && matchCategory;
  });

  return `
    <style>
      .menu-toolbar {
        display:flex;
        flex-wrap:wrap;
        gap:10px;
        margin:18px 0;
      }

      .menu-input {
        width:100%;
        padding:12px;
        border:1px solid #dce5de;
        border-radius:10px;
        font:inherit;
        background:white;
        color:#20352a;
      }

      .menu-search {
        flex:1;
        min-width:180px;
      }

      .menu-add {
        background:#168451;
        color:white;
        border:0;
        border-radius:10px;
        padding:12px 18px;
        font-weight:700;
      }

      .menu-categories {
        display:flex;
        gap:8px;
        overflow-x:auto;
        padding-bottom:8px;
        margin-bottom:15px;
      }

      .menu-cat {
        flex-shrink:0;
        border:1px solid #dce5de;
        background:white;
        color:#53665a;
        border-radius:30px;
        padding:9px 15px;
      }

      .menu-cat.active {
        background:#e8f4ec;
        border-color:#168451;
        color:#168451;
        font-weight:700;
      }

      .menu-list {
        display:grid;
        grid-template-columns:repeat(2,minmax(0,1fr));
        gap:12px;
      }

      .menu-item {
        background:white;
        border:1px solid #e2eae4;
        border-radius:14px;
        padding:15px;
        min-width:0;
      }

      .menu-item h3 {
        margin:0 0 5px;
        font-size:16px;
        overflow-wrap:anywhere;
      }

      .menu-price {
        font-weight:800;
        color:#168451;
        font-size:17px;
        margin:8px 0;
      }

      .menu-tag {
        display:inline-block;
        font-size:11px;
        padding:5px 9px;
        background:#edf4ef;
        border-radius:20px;
        color:#52675a;
      }

      .menu-status {
        font-size:12px;
        margin:9px 0;
      }

      .menu-actions {
        display:flex;
        flex-wrap:wrap;
        gap:7px;
        margin-top:12px;
      }

      .menu-actions button {
        border:1px solid #dce5de;
        background:white;
        border-radius:8px;
        padding:8px 10px;
        font-size:12px;
      }

      .menu-actions .delete {
        color:#bd3939;
      }

      .menu-form {
        background:white;
        border:1px solid #dfe8e1;
        border-radius:15px;
        padding:17px;
        margin:15px 0;
      }

      .menu-form label {
        display:block;
        font-size:13px;
        font-weight:650;
        margin:12px 0 6px;
      }

      .menu-form button {
        margin-top:14px;
      }

      .menu-empty {
        background:white;
        padding:35px 15px;
        text-align:center;
        border:1px dashed #cbd9ce;
        border-radius:14px;
        color:#718078;
        grid-column:1/-1;
      }

      @media(max-width:650px) {
        .menu-list {
          grid-template-columns:1fr;
        }

        .menu-add {
          width:100%;
        }
      }
    </style>

    <div class="panel">
      <div class="panel-title">
        <b>🍽️ Kelola Menu</b>
        <span>${menus.length} menu</span>
      </div>

      <p style="color:#75837a;font-size:13px">
        Tambahkan dan atur makanan serta minuman yang dijual.
      </p>

      <div class="menu-toolbar">
        <div class="menu-search">
          <input
            class="menu-input"
            id="menuSearch"
            placeholder="Cari nama menu..."
            value="${safe(searchText)}"
          >
        </div>

        <button class="menu-add" data-action="add">
          ＋ Tambah Menu
        </button>
      </div>

      <div class="menu-categories">
        ${categories.map(category => `
          <button
            class="menu-cat ${categoryFilter === category ? "active" : ""}"
            data-category="${category}"
          >
            ${category}
          </button>
        `).join("")}
      </div>

      <div id="menuFormArea">
        ${editingId !== null ? menuForm() : ""}
      </div>

      <div class="menu-list">
        ${filtered.length ? filtered.map(menu => `
          <div class="menu-item">
            <span class="menu-tag">${safe(menu.category)}</span>
            <h3>${safe(menu.name)}</h3>
            <div class="menu-price">${money(menu.price)}</div>

            <div class="menu-status" style="color:${menu.available ? "#168451" : "#c03939"}">
              ● ${menu.available ? "Tersedia" : "Habis"}
            </div>

            <div class="menu-actions">
              <button data-action="edit" data-id="${menu.id}">
                ✏️ Edit
              </button>

              <button data-action="toggle" data-id="${menu.id}">
                ${menu.available ? "Jadikan Habis" : "Aktifkan"}
              </button>

              <button class="delete" data-action="delete" data-id="${menu.id}">
                🗑 Hapus
              </button>
            </div>
          </div>
        `).join("") : `
          <div class="menu-empty">
            <div style="font-size:35px">🍽️</div>
            <b>Belum ada menu</b>
            <p>Tekan Tambah Menu untuk memasukkan menu pertama.</p>
          </div>
        `}
      </div>
    </div>
  `;
}

// Form tambah/edit
function menuForm() {
  const existing = menus.find(menu => menu.id === editingId);

  const name = existing ? existing.name : "";
  const price = existing ? existing.price : "";
  const category = existing ? existing.category : "Makanan";

  return `
    <form class="menu-form" id="menuForm">
      <h3>${existing ? "Edit Menu" : "Tambah Menu Baru"}</h3>

      <label>Nama Menu</label>
      <input
        class="menu-input"
        name="name"
        value="${safe(name)}"
        placeholder="Contoh: Nasi Goreng"
        required
      >

      <label>Kategori</label>
      <select class="menu-input" name="category">
        ${["Makanan", "Minuman", "Snack", "Lainnya"].map(item => `
          <option value="${item}" ${category === item ? "selected" : ""}>
            ${item}
          </option>
        `).join("")}
      </select>

      <label>Harga Jual (Rp)</label>
      <input
        class="menu-input"
        name="price"
        type="number"
        min="0"
        step="500"
        value="${price}"
        placeholder="Contoh: 15000"
        required
      >

      <div style="display:flex;gap:8px;flex-wrap:wrap">
        <button class="menu-add" type="submit">
          Simpan Menu
        </button>

        <button type="button" data-action="cancel">
          Batal
        </button>
      </div>
    </form>
  `;
}

// Halaman lainnya
const descriptions = {
  Kasir: "Buat pesanan, pilih menu, dan proses pembayaran.",
  Pesanan: "Pantau pesanan makan di tempat dan dibawa pulang.",
  Meja: "Kelola meja kosong, terisi, dan menunggu pembayaran.",
  Dapur: "Pantau pesanan baru, sedang dibuat, dan siap disajikan.",
  Laporan: "Lihat omzet, transaksi, dan performa setiap cabang.",
  Pengaturan: "Atur profil usaha, cabang, pengguna, dan aplikasi."
};

// Render halaman
function draw() {
  document.getElementById("title").textContent = current;

  document.getElementById("nav").innerHTML =
    items.map(navButton).join("");

  document.getElementById("bottom").innerHTML =
    items.slice(0, 6).map(navButton).join("");

  if (current === "Dashboard") {
    document.getElementById("view").innerHTML = dashboard();
  } else if (current === "Menu") {
    document.getElementById("view").innerHTML = menuPage();
  } else {
    document.getElementById("view").innerHTML = `
      <div class="placeholder">
        <h2>${current}</h2>
        <p>${descriptions[current]}</p>
        <p>Bagian ini akan kita bangun berikutnya.</p>
        <button class="primary" data-go="Dashboard">
          Kembali ke Dashboard
        </button>
      </div>
    `;
  }

  // Navigasi
  document.querySelectorAll("[data-go]").forEach(button => {
    button.addEventListener("click", () => {
      current = button.dataset.go;
      draw();
      document.getElementById("sidebar").classList.remove("open");
    });
  });

  // Tombol aksi menu
  document.querySelectorAll("[data-action]").forEach(button => {
    button.addEventListener("click", () => {
      const action = button.dataset.action;
      const id = Number(button.dataset.id);

      if (action === "add") {
        editingId = -1;
        draw();
        document.getElementById("menuForm").scrollIntoView({
          behavior: "smooth",
          block: "center"
        });
      }

      if (action === "edit") {
        editingId = id;
        draw();
        document.getElementById("menuForm").scrollIntoView({
          behavior: "smooth",
          block: "center"
        });
      }

      if (action === "cancel") {
        editingId = null;
        draw();
      }

      if (action === "toggle") {
        const menu = menus.find(item => item.id === id);

        if (menu) {
          menu.available = !menu.available;
          saveMenus();
          draw();
        }
      }

      if (action === "delete") {
        const menu = menus.find(item => item.id === id);

        if (menu && confirm(`Hapus menu "${menu.name}"?`)) {
          menus = menus.filter(item => item.id !== id);
          saveMenus();
          draw();
        }
      }
    });
  });

  // Filter kategori
  document.querySelectorAll("[data-category]").forEach(button => {
    button.addEventListener("click", () => {
      categoryFilter = button.dataset.category;
      draw();
    });
  });

  // Pencarian menu
  const search = document.getElementById("menuSearch");

  if (search) {
    search.addEventListener("input", event => {
      const position = event.target.selectionStart;
      searchText = event.target.value;

      draw();

      const newSearch = document.getElementById("menuSearch");
      newSearch.focus();
      newSearch.setSelectionRange(position, position);
    });
  }

  // Simpan form
  const form = document.getElementById("menuForm");

  if (form) {
    form.addEventListener("submit", event => {
      event.preventDefault();

      const formData = new FormData(form);
      const name = String(formData.get("name")).trim();
      const category = formData.get("category");
      const price = Number(formData.get("price"));

      if (!name || !Number.isFinite(price) || price < 0) {
        alert("Periksa kembali nama dan harga menu.");
        return;
      }

      if (editingId === -1) {
        menus.push({
          id: Date.now(),
          name,
          category,
          price,
          available: true
        });
      } else {
        const menu = menus.find(item => item.id === editingId);

        if (menu) {
          menu.name = name;
          menu.category = category;
          menu.price = price;
        }
      }

      saveMenus();
      editingId = null;
      searchText = "";
      categoryFilter = "Semua";
      draw();

      alert("Menu berhasil disimpan!");
    });
  }
}

// Tanggal
document.getElementById("date").textContent =
  new Intl.DateTimeFormat("id-ID", {
    weekday: "short",
    day: "numeric",
    month: "short",
    year: "numeric"
  }).format(new Date());

// Tombol menu HP
document.getElementById("menu").onclick = () => {
  document.getElementById("sidebar").classList.toggle("open");
};

// Pilihan cabang
document.getElementById("branch").onclick = () => {
  alert("Pengaturan cabang akan dibuat pada tahap berikutnya.");
};

// Jalankan aplikasi
draw();
