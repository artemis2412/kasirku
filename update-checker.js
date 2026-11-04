(function () {
  "use strict";
  const CURRENT_VERSION = "1.0.0";
  const RELEASES_API = "https://api.github.com/repos/artemis2412/kasirku/releases/latest";
  const RELEASES_PAGE = "https://github.com/artemis2412/kasirku/releases/latest";
  const STORAGE_KEY = "kasirku_update_notice_dismissed";
  function compareVersions(a, b) {
    const aa = String(a).replace(/^v/i, "").split(".").map(Number);
    const bb = String(b).replace(/^v/i, "").split(".").map(Number);
    for (let i = 0; i < 3; i++) {
      const x = aa[i] || 0, y = bb[i] || 0;
      if (x !== y) return x > y ? 1 : -1;
    }
    return 0;
  }
  function showNotice(version, url) {
    if (localStorage.getItem(STORAGE_KEY) === version) return;
    if (document.getElementById("kasirku-update-notice")) return;
    const style = document.createElement("style");
    style.textContent = "#kasirku-update-notice{position:fixed;z-index:99999;left:14px;right:14px;bottom:calc(76px + env(safe-area-inset-bottom));max-width:520px;margin:auto;background:#103d2c;color:#fff;padding:15px 16px;border-radius:14px;box-shadow:0 8px 28px #0003;font:14px/1.45 system-ui,sans-serif}#kasirku-update-notice strong{display:block;font-size:15px;margin-bottom:4px}#kasirku-update-notice p{margin:0 0 12px;color:#e5f2e9}#kasirku-update-notice .actions{display:flex;gap:9px;align-items:center}#kasirku-update-notice a{background:#fff;color:#103d2c;text-decoration:none;font-weight:700;padding:9px 13px;border-radius:9px}#kasirku-update-notice button{border:0;background:transparent;color:#fff;padding:9px;cursor:pointer}";
    document.head.appendChild(style);
    const box = document.createElement("div");
    box.id = "kasirku-update-notice";
    box.setAttribute("role", "status");
    const title = document.createElement("strong");
    title.textContent = "Pembaruan KASIRKU tersedia";
    const message = document.createElement("p");
    message.textContent = "Versi " + version + " sudah tersedia. Perbarui aplikasi untuk mendapatkan versi terbaru.";
    const actions = document.createElement("div");
    actions.className = "actions";
    const link = document.createElement("a");
    link.href = url || RELEASES_PAGE;
    link.target = "_blank";
    link.rel = "noopener";
    link.textContent = "Lihat pembaruan";
    const close = document.createElement("button");
    close.type = "button";
    close.textContent = "Nanti";
    close.addEventListener("click", function () {
      localStorage.setItem(STORAGE_KEY, version);
      box.remove();
    });
    actions.append(link, close);
    box.append(title, message, actions);
    document.body.appendChild(box);
  }
  async function check() {
    if (!navigator.onLine) return;
    try {
      const response = await fetch(RELEASES_API, {
        cache: "no-store",
        headers: { Accept: "application/vnd.github+json" }
      });
      if (!response.ok) return;
      const release = await response.json();
      const latest = String(release.tag_name || "").replace(/^v/i, "");
      if (!latest || compareVersions(latest, CURRENT_VERSION) <= 0) return;
      const apk = (release.assets || []).find(function (asset) {
        return /\.apk$/i.test(asset.name || "");
      });
      showNotice(latest, apk ? apk.browser_download_url : (release.html_url || RELEASES_PAGE));
    } catch (error) {
      console.info("KASIRKU: pemeriksaan pembaruan belum berhasil.", error);
    }
  }
  window.addEventListener("load", check);
  window.addEventListener("online", check);
})();
