
/* =====================================
   KASIRKU V2 - DASHBOARD, MENU & KASIR
===================================== */

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
let menus = readData("kasirku_menus", []);
let orders = readData("kasirku_orders", []);
let cart = [];
let orderType = "Dine In";
let menuSearch = "";
let menuCategory = "Semua";
let cashierCategory = "Semua";
let cashierSearch = "";

function readData(key, fallback) {
  try {
    const data = JSON.parse(localStorage.getItem(key));
    return Array.isArray(data) ? data : fallback;
  } catch {
    return fallback;
  }
}

function saveMenus() {
  localStorage.setItem("kasirku_menus", JSON.stringify(menus));
}

function saveOrders() {
  localStorage.setItem("kasirku_orders", JSON.stringify(orders));
}

function money(value) {
  return new Intl.NumberFormat("id-ID", {
    style: "currency",
    currency: "IDR",
    maximumFractionDigits: 0
  }).format(value || 0);
}

function safe(value) {
  return String(value ?? "").replace(/[&<>"']/g, char => ({
    "&": "&amp;",
    "<": "&lt;",
    ">": "&gt;",
    '"': "&quot;",
    "'": "&#039;"
  })[char]);
}

function todayOrders() {
  const today = new Date().toLocaleDateString("en-CA");
  return orders.filter(order => order.date === today);
}

function todaySales() {
  return todayOrders().reduce((sum, order) => sum + order.total, 0);
}

/* NAVIGASI */

function navButton(item) {
  return `
    <button class="navbtn ${current === item[0] ? "active" : ""}"
      data-go="${item[0]}">
      <span>${item[1]}</span>
      <span>${item[0]}</span>
    </button>
  `;
}

/* DASHBOARD */

function dashboard() {
  const daily = todayOrders();
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
        <div class="value">${money(todaySales())}</div>
        <div class="hint">Total transaksi hari ini</div>
      </div>

      <div class="card">
        <div class="label">Total Pesanan</div>
        <div class="value">${daily.length}</div>
        <div class="hint">Pesanan hari ini</div>
      </div>

      <div class="card">
        <div class="label">Meja Terisi</div>
        <div class="value">-</div>
        <div class="hint">Fitur meja belum aktif</div>
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
        <div class="panel-title"><b>📊 Penjualan Hari Ini</b></div>
        <div class="chart">
          ${daily.length
            ? `${daily.length} transaksi tercatat<br><b>${money(todaySales())}</b>`
            : "Grafik akan tersedia setelah transaksi pertama."}
        </div>
      </div>

      <div class="panel">
        <div class="panel-title">
          <b>▤ Pesanan Terbaru</b>
          <span data-go="Pesanan">Lihat Semua →</span>
        </div>
        <div class="empty-orders">
          ${daily.length
            ? daily.slice(-3).reverse().map(order => `
              <div style="width:100%;padding:7px;border-bottom:1px solid #eee;text-align:left">
                <b>${safe(order.number)}</b><br>
                <small>${money(order.total)}</small>
              </div>
            `).join("")
            : `<span class="big">▤</span><b>Belum ada pesanan</b>`}
        </div>
      </div>
    </div>

    <h3 class="quick-title">⚡ Akses Cepat</h3>

    <div class="quickgrid">
      <button class="quick" data-go="Kasir">
        <span class="qicon">🧾</span>
        <b>Kasir</b><small>Buat transaksi</small>
      </button>
      <button class="quick" data-go="Menu">
        <span class="qicon">🍽️</span>
        <b>Menu</b><small>Kelola makanan</small>
      </button>
      <button class="quick" data-go="Meja">
        <span class="qicon">🪑</span>
        <b>Meja</b><small>Atur meja pelanggan</small>
      </button>
      <button class="quick" data-go="Dapur">
        <span class="qicon">👨‍🍳</span>
        <b>Dapur</b><small>Pantau pesanan</small>
      </button>
    </div>

    <div class="note">
      Transaksi saat ini disimpan secara lokal di perangkat ini.
      Sinkronisasi online dan antar-cabang belum aktif.
    </div>
  `;
}

/* STYLE TAMBAHAN MENU DAN KASIR */

function featureStyles() {
  return `
    <style>
      .feature-toolbar {
        display:flex;gap:9px;flex-wrap:wrap;margin:15px 0;
      }
      .feature-input {
        width:100%;padding:12px;border:1px solid #dce5de;
        border-radius:10px;font:inherit;background:white;color:#20352a;
      }
      .feature-search {flex:1;min-width:160px}
      .feature-btn {
        background:#168451;color:white;border:0;border-radius:10px;
        padding:11px 15px;font-weight:700;
      }
      .feature-cats {
        display:flex;gap:8px;overflow-x:auto;margin:12px 0;
        padding-bottom:7px;
      }
      .feature-cat {
        flex-shrink:0;border:1px solid #dce5de;background:white;
        color:#53665a;border-radius:30px;padding:9px 14px;
      }
      .feature-cat.active {
        background:#e8f4ec;border-color:#168451;color:#168451;font-weight:700;
      }
      .feature-list {
        display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:11px;
      }
      .feature-item {
        background:white;border:1px solid #e2eae4;border-radius:13px;
        padding:14px;min-width:0;
      }
      .feature-item h3 {margin:5px 0;font-size:15px;overflow-wrap:anywhere}
      .feature-price {font-size:16px;font-weight:800;color:#168451;margin:7px 0}
      .feature-tag {
        font-size:11px;background:#edf4ef;padding:5px 9px;
        border-radius:20px;color:#52675a;display:inline-block;
      }
      .feature-actions {display:flex;flex-wrap:wrap;gap:6px;margin-top:10px}
      .feature-actions button {
        border:1px solid #dce5de;background:white;border-radius:8px;
        padding:8px 10px;font-size:12px;
      }
      .feature-form {
        background:white;border:1px solid #dfe8e1;border-radius:14px;
        padding:16px;margin:14px 0;
      }
      .feature-form label {display:block;font-size:13px;font-weight:650;margin:11px 0 5px}
      .feature-empty {
        grid-column:1/-1;background:white;padding:30px;text-align:center;
        border:1px dashed #cbd9ce;border-radius:13px;color:#718078;
      }
      .cash-layout {
        display:grid;grid-template-columns:minmax(0,1.2fr) minmax(280px,.8fr);
        gap:14px;align-items:start;
      }
      .cash-panel {
        background:white;border:1px solid #e2eae4;border-radius:15px;
        padding:15px;min-width:0;
      }
      .cash-menu-grid {
        display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:9px;
      }
      .cash-menu {
        border:1px solid #e1e9e3;background:#fff;border-radius:12px;
        padding:12px;text-align:left;min-width:0;
      }
      .cash-menu:disabled {opacity:.45}
      .cash-menu b {display:block;overflow-wrap:anywhere;margin:5px 0}
      .cash-menu small {color:#168451;font-weight:750}
      .cart-line {
        display:flex;justify-content:space-between;align-items:center;
        gap:8px;padding:11px 0;border-bottom:1px solid #edf1ed;
      }
      .cart-line-info {min-width:0}
      .cart-line-info b {display:block;overflow-wrap:anywhere}
      .cart-qty {display:flex;align-items:center;gap:7px;flex-shrink:0}
      .cart-qty button {
        width:29px;height:29px;border:1px solid #dce5de;background:white;
        border-radius:8px;font-weight:800;
      }
      .cart-total {
        display:flex;justify-content:space-between;font-weight:800;
        font-size:18px;padding:14px 0;
      }
      .type-buttons {display:flex;gap:8px;margin:10px 0}
      .type-buttons button {
        flex:1;padding:11px 7px;border:1px solid #dce5de;
        background:white;border-radius:10px;
      }
      .type-buttons button.active {
        background:#e8f4ec;border-color:#168451;color:#168451;font-weight:750;
      }
      .cash-checkout {width:100%;padding:13px;margin-top:12px}
      .order-card {
        background:white;border:1px solid #e2eae4;border-radius:13px;
        padding:14px;margin:10px 0;
      }
      .order-head {display:flex;justify-content:space-between;gap:10px;flex-wrap:wrap}
      .order-card p {margin:6px 0;color:#68786e;font-size:13px}
      @media(max-width:750px) {
        .cash-layout {grid-template-columns:1fr}
      }
      @media(max-width:650px) {
        .feature-list {grid-template-columns:1fr}
        .cash-menu-grid {grid-template-columns:repeat(2,minmax(0,1fr))}
      }
    </style>
  `;
}

/* HALAMAN MENU */

let editingId = null;

function menuForm() {
  const menu = menus.find(item => item.id === editingId);
  const name = menu ? menu.name : "";
  const price = menu ? menu.price : "";
  const category = menu ? menu.category : "Makanan";

  return `
    <form class="feature-form" id="menuForm">
      <h3>${menu ? "Edit Menu" : "Tambah Menu Baru"}</h3>
      <label>Nama Menu</label>
      <input class="feature-input" name="name" value="${safe(name)}"
        placeholder="Contoh: Nasi Goreng" required>
      <label>Kategori</label>
      <select class="feature-input" name="category">
        ${["Makanan","Minuman","Snack","Lainnya"].map(cat =>
          `<option ${category === cat ? "selected" : ""}>${cat}</option>`
        ).join("")}
      </select>
      <label>Harga Jual (Rp)</label>
      <input class="feature-input" name="price" type="number" min="0"
        value="${price}" placeholder="15000" required>
      <div class="feature-actions">
        <button class="feature-btn" type="submit">Simpan Menu</button>
        <button type="button" data-action="cancel-edit">Batal</button>
      </div>
    </form>
  `;
}

function menuPage() {
  const categories = ["Semua","Makanan","Minuman","Snack","Lainnya"];

  const filtered = menus.filter(menu =>
    menu.name.toLowerCase().includes(menuSearch.toLowerCase()) &&
    (menuCategory === "Semua" || menu.category === menuCategory)
  );

  return `
    ${featureStyles()}
    <div class="panel">
      <div class="panel-title">
        <b>🍽️ Kelola Menu</b><span>${menus.length} menu</span>
      </div>
      <p style="color:#75837a;font-size:13px">
        Atur makanan dan minuman yang dijual.
      </p>
      <div class="feature-toolbar">
        <div class="feature-search">
          <input class="feature-input" id="menuSearch"
            placeholder="Cari nama menu..." value="${safe(menuSearch)}">
        </div>
        <button class="feature-btn" data-action="add-menu">＋ Tambah Menu</button>
      </div>
      <div class="feature-cats">
        ${categories.map(cat => `
          <button class="feature-cat ${menuCategory === cat ? "active" : ""}"
            data-menu-category="${cat}">${cat}</button>
        `).join("")}
      </div>
      <div id="menuFormArea">${editingId !== null ? menuForm() : ""}</div>
      <div class="feature-list">
        ${filtered.length ? filtered.map(menu => `
          <div class="feature-item">
            <span class="feature-tag">${safe(menu.category)}</span>
            <h3>${safe(menu.name)}</h3>
            <div class="feature-price">${money(menu.price)}</div>
            <small style="color:${menu.available ? "#168451" : "#c03939"}">
              ● ${menu.available ? "Tersedia" : "Habis"}
            </small>
            <div class="feature-actions">
              <button data-action="edit-menu" data-id="${menu.id}">✏️ Edit</button>
              <button data-action="toggle-menu" data-id="${menu.id}">
                ${menu.available ? "Jadikan Habis" : "Aktifkan"}
              </button>
              <button data-action="delete-menu" data-id="${menu.id}">🗑 Hapus</button>
            </div>
          </div>
        `).join("") : `
          <div class="feature-empty">
            <div style="font-size:32px">🍽️</div>
            <b>Belum ada menu</b>
            <p>Tambahkan menu pertama kamu.</p>
          </div>
        `}
      </div>
    </div>
  `;
}

/* HALAMAN KASIR */

function cartTotal() {
  return cart.reduce((sum, item) => sum + item.price * item.qty, 0);
}

function cashierPage() {
  const categories = ["Semua","Makanan","Minuman","Snack","Lainnya"];

  const availableMenus = menus.filter(menu =>
    menu.available &&
    menu.name.toLowerCase().includes(cashierSearch.toLowerCase()) &&
    (cashierCategory === "Semua" || menu.category === cashierCategory)
  );

  return `
    ${featureStyles()}
    <div class="cash-layout">
      <div class="cash-panel">
        <div class="panel-title"><b>🍽️ Pilih Menu</b></div>
        <input class="feature-input" id="cashierSearch"
          placeholder="Cari makanan atau minuman..."
          value="${safe(cashierSearch)}">
        <div class="feature-cats">
          ${categories.map(cat => `
            <button class="feature-cat ${cashierCategory === cat ? "active" : ""}"
              data-cash-category="${cat}">${cat}</button>
          `).join("")}
        </div>
        <div class="cash-menu-grid">
          ${availableMenus.length ? availableMenus.map(menu => `
            <button class="cash-menu" data-action="add-cart" data-id="${menu.id}">
              <span class="feature-tag">${safe(menu.category)}</span>
              <b>${safe(menu.name)}</b>
              <small>${money(menu.price)}</small>
            </button>
          `).join("") : `
            <div class="feature-empty">
              Belum ada menu tersedia.<br>
              Tambahkan menu melalui halaman Menu.
            </div>
          `}
        </div>
      </div>

      <div class="cash-panel">
        <div class="panel-title">
          <b>🛒 Keranjang Pesanan</b>
          <span>${cart.reduce((sum,item) => sum + item.qty,0)} item</span>
        </div>

        ${cart.length ? cart.map(item => `
          <div class="cart-line">
            <div class="cart-line-info">
              <b>${safe(item.name)}</b>
              <small>${money(item.price)} × ${item.qty}</small>
            </div>
            <div class="cart-qty">
              <button data-action="minus-cart" data-id="${item.id}">−</button>
              <b>${item.qty}</b>
              <button data-action="plus-cart" data-id="${item.id}">+</button>
            </div>
          </div>
        `).join("") : `
          <div class="feature-empty">Keranjang masih kosong.</div>
        `}

        <div class="cart-total">
          <span>Total</span><span>${money(cartTotal())}</span>
        </div>

        <div style="font-weight:700">Jenis Pesanan</div>
        <div class="type-buttons">
          <button class="${orderType === "Dine In" ? "active" : ""}"
            data-order-type="Dine In">🍽️ Makan di Tempat</button>
          <button class="${orderType === "Takeaway" ? "active" : ""}"
            data-order-type="Takeaway">🥡 Dibawa Pulang</button>
        </div>

        ${orderType === "Dine In" ? `
          <label style="display:block;font-size:13px;margin:10px 0 5px">
            Nomor Meja
          </label>
          <input class="feature-input" id="tableNumber"
            placeholder="Contoh: 3">
        ` : ""}

        <label style="display:block;font-size:13px;margin:12px 0 5px">
          Uang Diterima (Rp)
        </label>
        <input class="feature-input" id="cashReceived"
          type="number" min="0" placeholder="Masukkan uang pembayaran">

        <div style="margin-top:12px;color:#68786e;font-size:13px">
          Kembalian:
          <b id="changeAmount" style="color:#168451">${money(0)}</b>
        </div>

        <button class="feature-btn cash-checkout"
          data-action="checkout" ${cart.length ? "" : "disabled"}>
          ✓ Selesaikan Pembayaran
        </button>
      </div>
    </div>
  `;
}

/* HALAMAN PESANAN */

function ordersPage() {
  return `
    ${featureStyles()}
    <div class="panel">
      <div class="panel-title">
        <b>▤ Riwayat Pesanan</b>
        <span>${orders.length} transaksi</span>
      </div>
      ${orders.length ? [...orders].reverse().map(order => `
        <div class="order-card">
          <div class="order-head">
            <b>${safe(order.number)}</b>
            <b style="color:#168451">${money(order.total)}</b>
          </div>
          <p>${safe(order.date)} · ${safe(order.time)}</p>
          <p>${safe(order.type)}${order.table ? " · Meja " + safe(order.table) : ""}</p>
          <p>${order.items.map(item =>
            `${safe(item.name)} × ${item.qty}`
          ).join(", ")}</p>
          <p>Uang diterima: ${money(order.received)} · Kembalian: ${money(order.change)}</p>
        </div>
      `).join("") : `
        <div class="feature-empty">Belum ada transaksi tersimpan.</div>
      `}
    </div>
  `;
}

/* HALAMAN LAIN */

const descriptions = {
  Meja: "Pengaturan meja pelanggan akan kita bangun berikutnya.",
  Dapur: "Tampilan pesanan untuk staf dapur akan kita bangun berikutnya.",
  Laporan: "Laporan penjualan dan ringkasan usaha akan kita bangun berikutnya.",
  Pengaturan: "Pengaturan usaha, cabang, dan pegawai akan kita bangun berikutnya."
};

/* RENDER */

function draw() {
  document.getElementById("title").textContent = current;
  document.getElementById("nav").innerHTML = items.map(navButton).join("");
  document.getElementById("bottom").innerHTML =
    items.slice(0,6).map(navButton).join("");

  const view = document.getElementById("view");

  if (current === "Dashboard") view.innerHTML = dashboard();
  else if (current === "Menu") view.innerHTML = menuPage();
  else if (current === "Kasir") view.innerHTML = cashierPage();
  else if (current === "Pesanan") view.innerHTML = ordersPage();
  else view.innerHTML = `
    <div class="placeholder">
      <h2>${current}</h2>
      <p>${descriptions[current] || "Halaman dalam pengembangan."}</p>
      <button class="primary" data-go="Dashboard">Kembali ke Dashboard</button>
    </div>
  `;

  // Navigasi halaman
  document.querySelectorAll("[data-go]").forEach(button => {
    button.addEventListener("click", () => {
      current = button.dataset.go;
      draw();
      document.getElementById("sidebar").classList.remove("open");
    });
  });

  // Aksi umum
  document.querySelectorAll("[data-action]").forEach(button => {
    button.addEventListener("click", () => {
      const action = button.dataset.action;
      const id = Number(button.dataset.id);

      if (action === "add-menu") {
        editingId = -1;
        draw();
        document.getElementById("menuForm").scrollIntoView({
          behavior:"smooth", block:"center"
        });
      }

      if (action === "edit-menu") {
        editingId = id;
        draw();
        document.getElementById("menuForm").scrollIntoView({
          behavior:"smooth", block:"center"
        });
      }

      if (action === "cancel-edit") {
        editingId = null;
        draw();
      }

      if (action === "toggle-menu") {
        const menu = menus.find(item => item.id === id);
        if (menu) {
          menu.available = !menu.available;
          saveMenus();
          draw();
        }
      }

      if (action === "delete-menu") {
        const menu = menus.find(item => item.id === id);
        if (menu && confirm(`Hapus menu "${menu.name}"?`)) {
          menus = menus.filter(item => item.id !== id);
          saveMenus();
          draw();
        }
      }

      if (action === "add-cart") {
        const menu = menus.find(item => item.id === id && item.available);
        if (menu) {
          const existing = cart.find(item => item.id === id);
          if (existing) existing.qty++;
          else cart.push({
            id: menu.id,
            name: menu.name,
            price: Number(menu.price),
            qty: 1
          });
          draw();
        }
      }

      if (action === "plus-cart" || action === "minus-cart") {
        const item = cart.find(item => item.id === id);
        if (item) {
          item.qty += action === "plus-cart" ? 1 : -1;
          if (item.qty <= 0) {
            cart = cart.filter(item => item.id !== id);
          }
          draw();
        }
      }

      if (action === "checkout") completeCheckout();
    });
  });

  // Form menu
  const menuFormElement = document.getElementById("menuForm");

  if (menuFormElement) {
    menuFormElement.addEventListener("submit", event => {
      event.preventDefault();

      const data = new FormData(menuFormElement);
      const name = String(data.get("name")).trim();
      const category = data.get("category");
      const price = Number(data.get("price"));

      if (!name || !Number.isFinite(price) || price < 0) {
        alert("Periksa nama dan harga menu.");
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
      menuSearch = "";
      menuCategory = "Semua";
      draw();
      alert("Menu berhasil disimpan.");
    });
  }

  // Pencarian menu
  const menuSearchElement = document.getElementById("menuSearch");
  if (menuSearchElement) {
    menuSearchElement.addEventListener("input", event => {
      menuSearch = event.target.value;
      const position = event.target.selectionStart;
      draw();
      const next = document.getElementById("menuSearch");
      next.focus();
      next.setSelectionRange(position, position);
    });
  }

  // Kategori menu
  document.querySelectorAll("[data-menu-category]").forEach(button => {
    button.addEventListener("click", () => {
      menuCategory = button.dataset.menuCategory;
      draw();
    });
  });

  // Pencarian kasir
  const cashierSearchElement = document.getElementById("cashierSearch");
  if (cashierSearchElement) {
    cashierSearchElement.addEventListener("input", event => {
      cashierSearch = event.target.value;
      const position = event.target.selectionStart;
      draw();
      const next = document.getElementById("cashierSearch");
      next.focus();
      next.setSelectionRange(position, position);
    });
  }

  // Kategori kasir
  document.querySelectorAll("[data-cash-category]").forEach(button => {
    button.addEventListener("click", () => {
      cashierCategory = button.dataset.cashCategory;
      draw();
    });
  });

  // Jenis pesanan
  document.querySelectorAll("[data-order-type]").forEach(button => {
    button.addEventListener("click", () => {
      orderType = button.dataset.orderType;
      draw();
    });
  });

  // Hitung kembalian langsung
  const cashInput = document.getElementById("cashReceived");
  if (cashInput) {
    cashInput.addEventListener("input", () => {
      const received = Number(cashInput.value);
      const change = received - cartTotal();
      const output = document.getElementById("changeAmount");
      output.textContent = money(Math.max(0, change));
    });
  }
}

/* PROSES PEMBAYARAN */

function completeCheckout() {
  if (!cart.length) {
    alert("Keranjang masih kosong.");
    return;
  }

  const total = cartTotal();
  const received = Number(document.getElementById("cashReceived")?.value || 0);
  const table = document.getElementById("tableNumber")?.value.trim() || "";

  if (orderType === "Dine In" && !table) {
    alert("Masukkan nomor meja terlebih dahulu.");
    return;
  }

  if (!Number.isFinite(received) || received < total) {
    alert("Uang pembayaran belum mencukupi.");
    return;
  }

  const now = new Date();
  const date = now.toLocaleDateString("en-CA");
  const time = now.toLocaleTimeString("id-ID", {
    hour:"2-digit", minute:"2-digit"
  });

  const order = {
    id: Date.now(),
    number: "ORD-" + now.getTime().toString().slice(-6),
    date,
    time,
    type: orderType,
    table: orderType === "Dine In" ? table : "",
    items: cart.map(item => ({...item})),
    total,
    received,
    change: received - total
  };

  orders.push(order);
  saveOrders();

  cart = [];
  orderType = "Dine In";
  cashierSearch = "";
  cashierCategory = "Semua";

  alert(
    "Transaksi berhasil disimpan!\n\n" +
    "Total: " + money(total) + "\n" +
    "Diterima: " + money(received) + "\n" +
    "Kembalian: " + money(order.change)
  );

  current = "Pesanan";
  draw();
}

/* TANGGAL DAN TOMBOL */

document.getElementById("date").textContent =
  new Intl.DateTimeFormat("id-ID", {
    weekday:"short",
    day:"numeric",
    month:"short",
    year:"numeric"
  }).format(new Date());

document.getElementById("menu").onclick = () => {
  document.getElementById("sidebar").classList.toggle("open");
};

document.getElementById("branch").onclick = () => {
  alert("Pengaturan cabang akan dibuat pada tahap berikutnya.");
};

draw();
