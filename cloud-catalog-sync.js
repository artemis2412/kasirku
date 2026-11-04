(() => {
  "use strict";
  document.addEventListener("click", async event => {
    const button=event.target.closest('[data-action="sync-local-catalog"]');
    if(!button)return;
    event.preventDefault();event.stopPropagation();event.stopImmediatePropagation();
    const menus=(()=>{try{return JSON.parse(localStorage.getItem("kasirku_menus")||"[]")}catch{return []}})();
    if(!Array.isArray(menus)){alert("Data menu lokal tidak valid.");return}
    const c=window.KASIRKU_CLOUD?.getState?.();
    const branchName=c?.branches?.[0]?.name||"cabang Cloud yang dipasangkan";
    const ok=confirm("Kirim "+menus.length+" menu dari cabang lokal aktif ke "+branchName+"?\n\nYang dikirim hanya kategori, nama produk, harga, dan stok. Pesanan, transaksi, serta catatan kas TIDAK dikirim. Menu Cloud dengan ID lokal yang sama akan diperbarui, bukan digandakan.");
    if(!ok)return;
    button.disabled=true;button.textContent="Mengirim katalog…";
    const status=document.getElementById("cloud-sync-result");if(status)status.textContent="Sinkronisasi katalog sedang berjalan…";
    try{
      const result=await window.KASIRKU_CLOUD.syncCatalog();
      if(window.KASIRKU_REFRESH_CLOUD_CATALOG)await window.KASIRKU_REFRESH_CLOUD_CATALOG();
      const message="Berhasil · "+result.productsCreated+" produk baru, "+result.productsUpdated+" produk diperbarui; "+result.categoriesCreated+" kategori baru, "+result.categoriesUpdated+" kategori diperbarui. Total menu lokal: "+result.totalLocalProducts+". Tidak ada transaksi yang dikirim.";
      const latest=document.getElementById("cloud-sync-result");if(latest)latest.textContent=message;else alert(message);
    }catch(error){
      const message="Sinkronisasi gagal: "+(error?.message||String(error))+". Data lokal tidak diubah.";
      const latest=document.getElementById("cloud-sync-result");if(latest)latest.textContent=message;else alert(message);
    }finally{
      const current=document.querySelector('[data-action="sync-local-catalog"]');if(current){current.disabled=false;current.textContent="↑ Sinkronkan Menu Lokal ke Cloud"}
    }
  },true);
})();