/* KASIRKU V3 - Warung & Cafe
   Dashboard, Menu, Kasir, Pesanan, 15 Meja
*/

const items = [
  ["Dashboard", "⌂"], ["Kasir", "▣"], ["Pesanan", "☷"],
  ["Meja", "▦"], ["Menu", "☕"], ["Dapur", "♨"],
  ["Laporan", "▤"], ["Pengaturan", "⚙"]
];

let current = "Dashboard";
let menus = readData("kasirku_menus", []);
let orders = readData("kasirku_orders", []);
let tables = readData("kasirku_tables", null);
let cart = [];
let orderType = "Dine In";
let selectedTable = 1;
let menuSearch = "";
let cashierSearch = "";
let menuCategory = "Semua";
let showMenuForm = false;
let editingMenuId = null;

function readData(key, fallback) {
  try {
    const value = JSON.parse(localStorage.getItem(key));
    return value === null ? fallback : value;
  } catch (e) { return fallback; }
}

function saveData(key, value) {
  localStorage.setItem(key, JSON.stringify(value));
}

function makeTables() {
  return Array.from({ length: 15 }, (_, i) => ({
    id: i + 1,
    name: `Meja ${String(i + 1).padStart(2, "0")}`
  }));
}

if (!Array.isArray(menus)) menus = [];
if (!Array.isArray(orders)) orders = [];

if (!Array.isArray(tables) || !tables.length) {
  tables = makeTables();
  saveData("kasirku_tables", tables);
}

function money(n) {
  return new Intl.NumberFormat("id-ID", {
    style: "currency",
    currency: "IDR",
    maximumFractionDigits: 0
  }).format(Number(n) || 0);
}

function safe(value) {
  return String(value ?? "").replace(/[&<>"']/g, c => ({
    "&": "&amp;",
    "<": "&lt;",
    ">": "&gt;",
    '"': "&quot;",
    "'": "&#39;"
  })[c]);
}

function today() {
  const d = new Date();
  return `${d.getFullYear()}-${String(d.getMonth()+1).padStart(2,"0")}-${String(d.getDate()).padStart(2,"0")}`;
}

function todayOrders() {
  return orders.filter(o => o.date === today());
}

function todaySales() {
  return todayOrders()
    .filter(o => o.paymentStatus === "Lunas")
    .reduce((sum, o) => sum + Number(o.total || 0), 0);
}

function activeOrder(tableId) {
  return orders.find(o =>
    o.type === "Dine In" &&
    Number(o.tableId) === Number(tableId) &&
    o.paymentStatus !== "Lunas" &&
    o.status !== "Dibatalkan"
  );
}

function styles() {
  return `<style>
  .v3-wrap{display:grid;gap:14px}
  .v3-head{display:flex;justify-content:space-between;align-items:center;gap:10px;flex-wrap:wrap}
  .v3-panel,.v3-card{background:#fff;border:1px solid #e1e9e3;border-radius:14px;padding:14px;min-width:0}
  .v3-grid{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:10px}
  .v3-two{display:grid;grid-template-columns:1.2fr .8fr;gap:12px;align-items:start}
  .v3-btn{border:0;border-radius:9px;padding:10px 13px;background:#168451;color:#fff;font-weight:700}
  .v3-btn.alt{background:#edf4ef;color:#28533c;border:1px solid #d7e5da}
  .v3-btn.red{background:#fff0ef;color:#b33b32}
  .v3-btn:disabled{opacity:.45}
  .v3-input,.v3-select{width:100%;padding:11px;border:1px solid #dce5de;border-radius:9px;background:white;color:#20352a;font:inherit}
  .v3-muted{color:#718078;font-size:12px}
  .v3-price{color:#168451;font-weight:800}
  .v3-line{display:flex;justify-content:space-between;align-items:center;gap:8px;padding:9px 0;border-bottom:1px solid #edf1ed}
  .v3-pill{display:inline-block;border-radius:30px;padding:5px 9px;font-size:11px;font-weight:800;background:#e7f5eb;color:#168451}
  .v3-pill.busy{background:#fff0d9;color:#9b6417}
  .v3-pill.wait{background:#eaf2ff;color:#315f9c}
  .v3-table{border:1px solid #dfe9e1;border-radius:13px;padding:13px;background:#f5faf6}
  .v3-table.occupied{background:#fff6e9;border-color:#f0d7b4}
  .v3-table strong{display:block;margin:8px 0;font-size:16px}
  .v3-actions{display:flex;gap:6px;flex-wrap:wrap;margin-top:10px}
  .v3-empty{padding:24px;text-align:center;color:#718078;border:1px dashed #cbd9ce;border-radius:12px}
  .v3-form{display:grid;grid-template-columns:1fr 1fr;gap:10px}
  .v3-field label{display:block;font-size:12px;font-weight:700;margin-bottom:5px}
  .v3-field.full{grid-column:1/-1}
  .v3-qty{display:flex;align-items:center;gap:7px}
  .v3-qty button{border:1px solid #dce5de;background:#fff;border-radius:7px;padding:4px 9px}
  @media(max-width:800px){.v3-two{grid-template-columns:1fr}.v3-grid{grid-template-columns:repeat(2,minmax(0,1fr))}}
  @media(max-width:420px){.v3-form{grid-template-columns:1fr}}
  </style>`;
}

function navButton(item) {
  return `<button class="navbtn ${current === item[0] ? "active" : ""}" data-go="${item[0]}">
    <span>${item[1]}</span><span>${item[0]}</span>
  </button>`;
}

function dashboard() {
  const occupied = tables.filter(t => activeOrder(t.id)).length;

  return `${styles()}
  <div class="v3-wrap">
    <div class="v3-panel">
      <h2>Selamat datang di KASIRKU 👋</h2>
      <p class="v3-muted">Ringkasan aktivitas warung & cafe hari ini.</p>
      <button class="v3-btn" data-go="Kasir">＋ Transaksi Baru</button>
    </div>

    <div class="stats">
      <div class="card">
        <div class="label">Penjualan Hari Ini</div>
        <div class="value">${money(todaySales())}</div>
        <div class="hint">Transaksi lunas</div>
      </div>
      <div class="card">
        <div class="label">Pesanan Hari Ini</div>
        <div class="value">${todayOrders().length}</div>
        <div class="hint">Semua pesanan</div>
      </div>
      <div class="card">
        <div class="label">Meja Terisi</div>
        <div class="value">${occupied}/${tables.length}</div>
        <div class="hint">Meja aktif</div>
      </div>
      <div class="card">
        <div class="label">Menu Tersedia</div>
        <div class="value">${menus.filter(m=>m.available!==false).length}</div>
        <div class="hint">${menus.length} menu terdaftar</div>
      </div>
    </div>

    <div class="v3-two">
      <div class="v3-panel">
        <h3>Aktivitas Hari Ini</h3>
        <div class="v3-line"><span>Penjualan lunas</span><b>${money(todaySales())}</b></div>
        <div class="v3-line"><span>Jumlah pesanan</span><b>${todayOrders().length}</b></div>
      </div>

      <div class="v3-panel">
        <div class="v3-head">
          <h3>Pesanan Terbaru</h3>
          <button class="v3-btn alt" data-go="Pesanan">Lihat</button>
        </div>
        ${orders.length
          ? [...orders].slice(-4).reverse().map(o =>
            `<div class="v3-line"><span>${safe(o.number)}</span><b>${money(o.total)}</b></div>`
          ).join("")
          : `<div class="v3-empty">Belum ada transaksi.</div>`
        }
      </div>
    </div>

    <div class="note">
      Data tersimpan di perangkat ini. Sinkronisasi online dan antar-cabang belum aktif.
    </div>
  </div>`;
}
/* =========================
   MANAJEMEN MENU
========================= */

function menuForm() {
  const m = menus.find(x => String(x.id) === String(editingMenuId));

  return `
    <div class="v3-panel">
      <h3>${m ? "Edit Menu" : "Tambah Menu"}</h3>

      <form id="menu-form" class="v3-form">
        <div class="v3-field">
          <label>Nama menu</label>
          <input class="v3-input" name="name" required
            value="${safe(m?.name || "")}">
        </div>

        <div class="v3-field">
          <label>Kategori</label>
          <input class="v3-input" name="category"
            value="${safe(m?.category || "Makanan")}">
        </div>

        <div class="v3-field">
          <label>Harga (Rp)</label>
          <input class="v3-input" name="price"
            type="number" min="0" required
            value="${safe(m?.price ?? "")}">
        </div>

        <div class="v3-field">
          <label>Status</label>
          <select class="v3-select" name="available">
            <option value="yes" ${m?.available !== false ? "selected" : ""}>Tersedia</option>
            <option value="no" ${m?.available === false ? "selected" : ""}>Habis</option>
          </select>
        </div>

        <div class="v3-field full v3-actions">
          <button class="v3-btn">Simpan</button>
          <button class="v3-btn alt" type="button" data-action="cancel-menu">Batal</button>
        </div>
      </form>
    </div>
  `;
}

function menuPage() {
  const cats = ["Semua", ...new Set(
    menus.map(m => m.category || "Lainnya")
  )];

  const filtered = menus.filter(m =>
    (m.name || "").toLowerCase().includes(menuSearch.toLowerCase()) &&
    (menuCategory === "Semua" ||
      (m.category || "Lainnya") === menuCategory)
  );

  return `${styles()}
    <div class="v3-wrap">

      <div class="v3-head">
        <div>
          <h2>Daftar Menu</h2>
          <div class="v3-muted">${menus.length} menu</div>
        </div>
        <button class="v3-btn" data-action="new-menu">
          ＋ Tambah Menu
        </button>
      </div>

      <input class="v3-input" id="menu-search"
        placeholder="Cari menu..." value="${safe(menuSearch)}">

      <div class="v3-actions">
        ${cats.map(c => `
          <button class="v3-btn ${menuCategory === c ? "" : "alt"}"
            data-category="${safe(c)}">${safe(c)}</button>
        `).join("")}
      </div>

      ${showMenuForm ? menuForm() : ""}

      <div class="v3-grid">
        ${filtered.length ? filtered.map(m => `
          <div class="v3-card">
            <span class="v3-pill ${m.available === false ? "busy" : ""}">
              ${m.available === false ? "Habis" : "Tersedia"}
            </span>

            <h3>${safe(m.name)}</h3>
            <div class="v3-muted">${safe(m.category || "Lainnya")}</div>
            <div class="v3-price">${money(m.price)}</div>

            <div class="v3-actions">
              <button class="v3-btn alt"
                data-action="edit-menu" data-id="${m.id}">Edit</button>

              <button class="v3-btn alt"
                data-action="toggle-menu" data-id="${m.id}">
                ${m.available === false ? "Aktifkan" : "Habis"}
              </button>

              <button class="v3-btn red"
                data-action="delete-menu" data-id="${m.id}">Hapus</button>
            </div>
          </div>
        `).join("") : `
          <div class="v3-empty" style="grid-column:1/-1">
            Belum ada menu. Tekan Tambah Menu.
          </div>
        `}
      </div>
    </div>
  `;
}


/* =========================
   HALAMAN KASIR
========================= */

function cashierPage() {
  const available = menus.filter(m =>
    m.available !== false &&
    (m.name || "").toLowerCase().includes(cashierSearch.toLowerCase())
  );

  const total = cart.reduce(
    (sum, x) => sum + x.price * x.qty, 0
  );

  return `${styles()}
    <div class="v3-wrap">
      <h2>Kasir</h2>

      <div class="v3-actions">
        <button class="v3-btn ${orderType === "Dine In" ? "" : "alt"}"
          data-action="dine">🍽️ Dine In</button>

        <button class="v3-btn ${orderType === "Takeaway" ? "" : "alt"}"
          data-action="take">🥡 Takeaway</button>

        ${orderType === "Dine In" ? `
          <select class="v3-select" id="selected-table"
            style="max-width:180px">
            ${tables.map(t => `
              <option value="${t.id}"
                ${Number(selectedTable) === Number(t.id) ? "selected" : ""}
                ${activeOrder(t.id) ? "disabled" : ""}>
                ${safe(t.name)}${activeOrder(t.id) ? " (Terisi)" : ""}
              </option>
            `).join("")}
          </select>
        ` : ""}
      </div>

      <div class="v3-two">

        <div class="v3-panel">
          <h3>Pilih Menu</h3>

          <input class="v3-input" id="cashier-search"
            placeholder="Cari makanan/minuman..."
            value="${safe(cashierSearch)}">

          <div class="v3-grid" style="margin-top:12px">
            ${available.length ? available.map(m => `
              <button class="v3-card" style="text-align:left"
                data-action="add-cart" data-id="${m.id}">
                <b>${safe(m.name)}</b>
                <div class="v3-muted">${safe(m.category || "Lainnya")}</div>
                <div class="v3-price">${money(m.price)}</div>
                <span class="v3-muted">＋ Tambahkan</span>
              </button>
            `).join("") : `
              <div class="v3-empty" style="grid-column:1/-1">
                ${menus.length
                  ? "Menu tidak ditemukan atau tidak tersedia."
                  : "Tambahkan menu terlebih dahulu."}
              </div>
            `}
          </div>
        </div>

        <div class="v3-panel">
          <h3>🧺 Keranjang</h3>

          ${cart.length ? cart.map(x => `
            <div class="v3-line">
              <div>
                <b>${safe(x.name)}</b>
                <div class="v3-muted">
                  ${money(x.price)} × ${x.qty}
                </div>
              </div>

              <div class="v3-qty">
                <button data-action="minus" data-id="${x.id}">−</button>
                <b>${x.qty}</b>
                <button data-action="plus" data-id="${x.id}">＋</button>
              </div>
            </div>
          `).join("") : `
            <div class="v3-empty">Keranjang masih kosong.</div>
          `}

          <div class="v3-line">
            <b>Total</b>
            <b class="v3-price">${money(total)}</b>
          </div>

          <button class="v3-btn" style="width:100%;margin-top:12px"
            data-action="checkout" ${cart.length ? "" : "disabled"}>
            ${orderType === "Dine In"
              ? "Kirim Pesanan ke Meja"
              : "Bayar & Simpan"}
          </button>
        </div>

      </div>
    </div>
  `;
}


/* =========================
   PROSES KERANJANG
========================= */

function makeOrder(status, paymentStatus, tableId) {
  const total = cart.reduce(
    (sum, x) => sum + x.price * x.qty, 0
  );

  const d = new Date();

  return {
    id: Date.now(),
    number: `KSR-${today().replaceAll("-", "")}-${String(orders.length + 1).padStart(3, "0")}`,
    date: today(),
    time: d.toLocaleTimeString("id-ID", {
      hour: "2-digit",
      minute: "2-digit"
    }),
    type: orderType,
    table: tableId ? String(tableId) : "",
    tableId: tableId,
    items: cart.map(x => ({ ...x })),
    total,
    received: 0,
    change: 0,
    status,
    paymentStatus
  };
}

function checkout() {
  if (!cart.length) return;

  if (orderType === "Dine In") {
    if (activeOrder(selectedTable)) {
      alert("Meja sedang terisi. Pilih meja lain.");
      return;
    }

    const t = tables.find(
      x => Number(x.id) === Number(selectedTable)
    );

    if (!t) {
      alert("Pilih meja terlebih dahulu.");
      return;
    }

    orders.push(
      makeOrder("Menunggu pembayaran", "Belum Lunas", selectedTable)
    );

    saveData("kasirku_orders", orders);
    cart = [];
    current = "Pesanan";

    alert(`Pesanan dikirim ke ${t.name}. Pembayaran dilakukan saat pelanggan selesai.`);
    draw();
    return;
  }

  const total = cart.reduce(
    (sum, x) => sum + x.price * x.qty, 0
  );

  const received = Number(prompt(
    `Total ${money(total)}\nUang diterima (Rp):`,
    String(total)
  ));

  if (!Number.isFinite(received) || received < total) {
    alert("Uang diterima kurang atau tidak valid.");
    return;
  }

  const order = makeOrder("Menunggu", "Lunas", null);
  order.received = received;
  order.change = received - total;

  orders.push(order);
  saveData("kasirku_orders", orders);
  cart = [];
  current = "Pesanan";

  alert(`Transaksi berhasil.\nKembalian: ${money(order.change)}`);
  draw();
}
/* =========================
   RIWAYAT PESANAN
========================= */

function ordersPage() {
  return `${styles()}
    <div class="v3-wrap">
      <div class="v3-head">
        <div>
          <h2>Riwayat Pesanan</h2>
          <div class="v3-muted">${orders.length} pesanan</div>
        </div>
      </div>

      ${orders.length ? [...orders].reverse().map(o => {
        const unpaid =
          o.paymentStatus !== "Lunas" &&
          o.status !== "Dibatalkan";

        return `
          <div class="v3-panel">
            <div class="v3-head">
              <b>${safe(o.number)}</b>
              <b>${money(o.total)}</b>
            </div>

            <div class="v3-muted">
              ${safe(o.date)} · ${safe(o.time || "")}
            </div>

            <div class="v3-line">
              <span>
                ${safe(o.type)}
                ${o.table ? " · Meja " + safe(o.table) : ""}
              </span>

              <span class="v3-pill ${unpaid ? "wait" : ""}">
                ${unpaid ? "Belum Lunas" : safe(o.paymentStatus || "Lunas")}
              </span>
            </div>

            <div class="v3-muted">
              ${(o.items || []).map(x =>
                `${safe(x.name)} × ${x.qty}`
              ).join(", ")}
            </div>

            ${unpaid ? `
              <div class="v3-actions">
                <button class="v3-btn"
                  data-action="pay" data-id="${o.id}">
                  Proses Pembayaran
                </button>

                <button class="v3-btn red"
                  data-action="cancel" data-id="${o.id}">
                  Batalkan
                </button>
              </div>
            ` : `
              <div class="v3-muted">
                Status: ${safe(o.status || "Selesai")}
              </div>
            `}
          </div>
        `;
      }).join("") : `
        <div class="v3-empty">Belum ada pesanan.</div>
      `}
    </div>
  `;
}


/* =========================
   PEMBAYARAN PESANAN
========================= */

function payOrder(id) {
  const order = orders.find(
    x => String(x.id) === String(id)
  );

  if (!order) return;

  const received = Number(prompt(
    `Tagihan ${money(order.total)}\nUang diterima (Rp):`,
    String(order.total)
  ));

  if (!Number.isFinite(received) ||
      received < Number(order.total)) {
    alert("Uang diterima kurang atau tidak valid.");
    return;
  }

  order.received = received;
  order.change = received - order.total;
  order.paymentStatus = "Lunas";

  saveData("kasirku_orders", orders);

  alert(
    `Pembayaran berhasil.\nKembalian: ${money(order.change)}`
  );

  draw();
}


/* =========================
   MANAJEMEN MEJA
========================= */

function tablesPage() {
  const occupied = tables.filter(
    t => activeOrder(t.id)
  ).length;

  return `${styles()}

    <style>
      .table-grid {
        display: grid;
        grid-template-columns: repeat(3, 1fr);
        gap: 12px;
      }

      .table-tile {
        border: 1px solid #dce9df;
        background: #f0f8f2;
        border-radius: 15px;
        padding: 15px 10px;
        text-align: center;
        min-width: 0;
      }

      .table-tile.occupied {
        background: #fff5e8;
        border-color: #efd4aa;
      }

      .table-icon {
        font-size: 28px;
        margin: 5px 0;
      }

      .table-name {
        font-size: 15px;
        font-weight: 800;
        margin: 5px 0;
      }

      .table-tile .v3-btn {
        width: 100%;
        margin-top: 10px;
        padding: 8px 5px;
        font-size: 12px;
      }

      @media(max-width: 420px) {
        .table-grid {
          grid-template-columns: repeat(2, 1fr);
          gap: 9px;
        }
      }
    </style>

    <div class="v3-wrap">

      <div class="v3-head">
        <div>
          <h2>Manajemen Meja</h2>
          <div class="v3-muted">
            Pantau aktivitas meja pelanggan
          </div>
        </div>

        <span class="v3-pill">
          ${tables.length} Meja
        </span>
      </div>

      <div class="v3-actions">
        <span class="v3-pill">
          Kosong: ${tables.length - occupied}
        </span>

        <span class="v3-pill busy">
          Terisi: ${occupied}
        </span>
      </div>

      <div class="table-grid">

        ${tables.map(t => {
          const order = activeOrder(t.id);

          return `
            <div class="table-tile ${order ? "occupied" : ""}">

              <span class="v3-pill ${order ? "busy" : ""}">
                ${order ? "Terisi" : "Kosong"}
              </span>

              <div class="table-icon">🪑</div>

              <div class="table-name">
                ${safe(t.name)}
              </div>

              ${order ? `
                <div class="v3-muted">
                  ${money(order.total)}
                </div>

                <button class="v3-btn"
                  data-action="pay"
                  data-id="${order.id}">
                  Bayar
                </button>

              ` : `
                <div class="v3-muted">
                  Siap digunakan
                </div>

                <button class="v3-btn alt"
                  data-action="choose-table"
                  data-id="${t.id}">
                  Pilih Meja
                </button>
              `}

            </div>
          `;
        }).join("")}

      </div>
    </div>
  `;
}


/* =========================
   DAPUR, LAPORAN, PENGATURAN
========================= */

function otherPage(page) {

  if (page === "Dapur") {
  const active = orders.filter(o =>
    o.status !== "Selesai" &&
    o.status !== "Dibatalkan"
  );

  return `${styles()}
    <h2>Dapur</h2>
    <p class="v3-muted">
      Pesanan yang sedang berjalan.
    </p>

    ${active.length
      ? active.map(o => `
        <div class="v3-panel">
          <div class="v3-head">
            <b>${safe(o.number)}</b>
            <span class="v3-pill">
              ${safe(o.status)}
            </span>
          </div>

          <p>
            ${safe(o.type)}
            ${o.table ? " · Meja " + safe(o.table) : ""}
          </p>

          <p>
            ${(o.items || []).map(x =>
              `${safe(x.name)} × ${x.qty}`
            ).join(", ")}
          </p>

          <div class="v3-actions">
            ${(o.status === "Menunggu" || o.status === "Menunggu pembayaran") ? `
              <button class="v3-btn"
                data-action="kitchen-process"
                data-id="${o.id}">
                Mulai Proses
              </button>
            ` : ""}

            ${o.status === "Diproses" ? `
              <button class="v3-btn"
                data-action="kitchen-ready"
                data-id="${o.id}">
                Pesanan Siap
              </button>
            ` : ""}

            ${o.status === "Siap" ? `
              <button class="v3-btn" data-action="kitchen-finish" data-id="${o.id}">Tandai Selesai</button>
            ` : ""}
          </div>
        </div>
      `).join("")
      : `<div class="v3-empty">
          Belum ada pesanan aktif.
        </div>`
    }
  `;
}

  
if (page === "Laporan") {
  return `${styles()}
    <div class="v3-wrap">
      <div class="v3-head">
        <div>
          <h2>Laporan Penjualan</h2>
          <div class="v3-muted">
            Ringkasan transaksi hari ini
          </div>
        </div>
      </div>

      <div class="v3-panel">
        <div class="v3-muted">
          Penjualan lunas hari ini
        </div>

        <h2>${money(todaySales())}</h2>

        <p>
          ${todayOrders().filter(
            o => o.paymentStatus === "Lunas"
          ).length} transaksi lunas
        </p>
      </div>

      <div class="v3-panel">
        <div class="v3-muted">
          Total pesanan hari ini
        </div>

        <h2>${todayOrders().length}</h2>
        <p>Pesanan tercatat hari ini</p>
      </div>

      <div class="v3-panel">
        <div class="v3-muted">
          Pesanan belum lunas
        </div>

        <h2>${todayOrders().filter(
          o => o.paymentStatus !== "Lunas"
        ).length}</h2>
      </div>
    </div>
  `;
}

  
  if (page === "Pengaturan") {
    return `${styles()}
      <div class="v3-wrap">
        <h2>Pengaturan</h2>
        <div class="v3-panel">
          <h3>Cadangan Data</h3>
          <p class="v3-muted">Simpan salinan menu, pesanan, dan meja.</p>
          <button class="v3-btn" data-action="backup">Unduh Cadangan Data</button>
        </div>
        <div class="v3-panel">
          <h3>Penyimpanan</h3>
          <p class="v3-muted">Data KASIRKU tersimpan di perangkat ini. Jangan hapus data situs atau penyimpanan browser agar transaksi tidak hilang.</p>
        </div>
      </div>
    `;
  }

  return `${styles()}<div class="v3-empty">Halaman tidak ditemukan.</div>`;
}

/* =========================
   CADANGAN DATA
========================= */

function exportBackup() {
  const backup = {
    menus,
    orders,
    tables,
    exportedAt: new Date().toISOString()
  };

  const blob = new Blob(
    [JSON.stringify(backup, null, 2)],
    { type: "application/json" }
  );

  const url = URL.createObjectURL(blob);
  const a = document.createElement("a");

  a.href = url;
  a.download = `kasirku-backup-${today()}.json`;
  a.click();

  URL.revokeObjectURL(url);
}


/* =========================
   TOMBOL DAN AKSI
========================= */

function handleAction(action, id) {

  if (action === "new-menu") {
    showMenuForm = true;
    editingMenuId = null;
    draw();
  }

  if (action === "cancel-menu") {
    showMenuForm = false;
    editingMenuId = null;
    draw();
  }

  if (action === "edit-menu") {
    showMenuForm = true;
    editingMenuId = id;
    draw();
  }

  if (action === "delete-menu") {
    if (confirm("Hapus menu ini?")) {
      menus = menus.filter(
        m => String(m.id) !== String(id)
      );

      saveData("kasirku_menus", menus);
      draw();
    }
  }

  if (action === "toggle-menu") {
    const menu = menus.find(
      m => String(m.id) === String(id)
    );

    if (menu) {
      menu.available = menu.available === false;
      saveData("kasirku_menus", menus);
      draw();
    }
  }

  if (action === "add-cart") {
    const menu = menus.find(
      m => String(m.id) === String(id)
    );

    if (menu && menu.available !== false) {
      const existing = cart.find(
        x => String(x.id) === String(id)
      );

      if (existing) {
        existing.qty++;
      } else {
        cart.push({
          id: menu.id,
          name: menu.name,
          price: Number(menu.price),
          qty: 1
        });
      }

      draw();
    }
  }

  if (action === "plus" || action === "minus") {
    const item = cart.find(
      x => String(x.id) === String(id)
    );

    if (item) {
      item.qty += action === "plus" ? 1 : -1;
    }

    cart = cart.filter(x => x.qty > 0);
    draw();
  }

  if (action === "dine") {
    orderType = "Dine In";
    draw();
  }

  if (action === "take") {
    orderType = "Takeaway";
    draw();
  }

  if (action === "checkout") checkout();

  if (action === "pay") payOrder(id);

  if (action === "cancel") {
    const order = orders.find(
      x => String(x.id) === String(id)
    );

    if (order && confirm("Batalkan pesanan ini?")) {
      order.status = "Dibatalkan";
      order.paymentStatus = "Dibatalkan";

      saveData("kasirku_orders", orders);
      draw();
    }
  }

  if (action === "choose-table") {
    selectedTable = Number(id);
    orderType = "Dine In";
    current = "Kasir";
    draw();
  }

  if (action === "backup") exportBackup();

  if (
    action === "kitchen-process" ||
    action === "kitchen-ready" ||
    action === "kitchen-finish"
  ) {
    const order = orders.find(
      o => String(o.id) === String(id)
    );

    if (order) {
      order.status =
        action === "kitchen-process" ? "Diproses" :
        action === "kitchen-ready" ? "Siap" : "Selesai";

      saveData("kasirku_orders", orders);
      draw();
    }
  }
}

/* =========================
   SIMPAN FORM MENU
========================= */

function saveMenuForm(event) {
  event.preventDefault();

  const data = new FormData(event.target);

  const record = {
    id: editingMenuId
      ? Number(editingMenuId)
      : Date.now(),

    name: String(data.get("name") || "").trim(),
    category: String(data.get("category") || "Lainnya").trim(),
    price: Number(data.get("price") || 0),
    available: data.get("available") === "yes"
  };

  if (!record.name || record.price < 0) {
    alert("Periksa nama dan harga menu.");
    return;
  }

  if (editingMenuId) {
    const index = menus.findIndex(
      m => String(m.id) === String(editingMenuId)
    );

    if (index >= 0) menus[index] = record;
  } else {
    menus.push(record);
  }

  saveData("kasirku_menus", menus);

  showMenuForm = false;
  editingMenuId = null;

  draw();
}


/* =========================
   EVENT HALAMAN
========================= */

function bindViewEvents() {

  document.querySelectorAll("[data-go]").forEach(button => {
    button.addEventListener("click", () => {
      current = button.dataset.go;

      document.getElementById("sidebar")
        ?.classList.remove("open");

      draw();
    });
  });

  document.querySelectorAll("[data-action]").forEach(button => {
    button.addEventListener("click", () => {
      handleAction(
        button.dataset.action,
        button.dataset.id
      );
    });
  });

  document.querySelectorAll("[data-category]").forEach(button => {
    button.addEventListener("click", () => {
      menuCategory = button.dataset.category;
      draw();
    });
  });

  document.getElementById("menu-form")
    ?.addEventListener("submit", saveMenuForm);

  document.getElementById("selected-table")
    ?.addEventListener("change", event => {
      selectedTable = Number(event.target.value);
    });

  document.getElementById("menu-search")
    ?.addEventListener("input", event => {
      menuSearch = event.target.value;
      const position = event.target.selectionStart;
      draw();

      const input = document.getElementById("menu-search");
      input?.focus();
      input?.setSelectionRange(position, position);
    });

  document.getElementById("cashier-search")
    ?.addEventListener("input", event => {
      cashierSearch = event.target.value;
      const position = event.target.selectionStart;
      draw();

      const input = document.getElementById("cashier-search");
      input?.focus();
      input?.setSelectionRange(position, position);
    });
}


/* =========================
   FUNGSI UTAMA
========================= */

function draw() {
  const title = document.getElementById("title");
  const nav = document.getElementById("nav");
  const bottom = document.getElementById("bottom");
  const view = document.getElementById("view");

  if (!title || !nav || !bottom || !view) return;

  title.textContent = current;

  nav.innerHTML = items.map(navButton).join("");
  bottom.innerHTML = items.slice(0, 6)
    .map(navButton).join("");

  if (current === "Dashboard") {
    view.innerHTML = dashboard();
  } else if (current === "Menu") {
    view.innerHTML = menuPage();
  } else if (current === "Kasir") {
    view.innerHTML = cashierPage();
  } else if (current === "Pesanan") {
    view.innerHTML = ordersPage();
  } else if (current === "Meja") {
    view.innerHTML = tablesPage();
  } else {
    view.innerHTML = otherPage(current);
  }

  bindViewEvents();
}


/* =========================
   TANGGAL DAN MENU SAMPING
========================= */

const dateElement = document.getElementById("date");

if (dateElement) {
  dateElement.textContent =
    new Intl.DateTimeFormat("id-ID", {
      weekday: "short",
      day: "numeric",
      month: "short",
      year: "numeric"
    }).format(new Date());
}

document.getElementById("menu")
  ?.addEventListener("click", () => {
    document.getElementById("sidebar")
      ?.classList.toggle("open");
  });

document.getElementById("branch")
  ?.addEventListener("click", () => {
    alert("Pengaturan cabang akan tersedia pada tahap berikutnya.");
  });


/* MULAI APLIKASI */
draw();
