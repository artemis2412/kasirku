(() => {
  "use strict";
  function showSyncConfirm(count,branchName){
    return new Promise(resolve=>{
      const overlay=document.createElement("div");
      overlay.style.cssText="position:fixed;inset:0;z-index:10050;display:flex;align-items:center;justify-content:center;padding:20px;background:rgba(8,29,20,.58);backdrop-filter:blur(4px)";
      const modal=document.createElement("div");
      modal.setAttribute("role","dialog");modal.setAttribute("aria-modal","true");modal.setAttribute("aria-labelledby","ks-sync-title");
      modal.style.cssText="width:100%;max-width:420px;background:#fff;border:1px solid #e1ebe4;border-radius:22px;padding:22px;color:#203b2d;box-shadow:0 24px 70px rgba(0,0,0,.25);font-family:system-ui,-apple-system,sans-serif";
      const heading=document.createElement("div");heading.style.cssText="display:flex;align-items:center;gap:12px;margin-bottom:16px";
      const icon=document.createElement("div");icon.textContent="☁";icon.style.cssText="width:44px;height:44px;flex:0 0 44px;display:grid;place-items:center;border-radius:14px;background:#e7f4eb;color:#168153;font-size:23px";
      const title=document.createElement("div");title.id="ks-sync-title";title.textContent="Sinkronkan katalog?";title.style.cssText="font-size:19px;font-weight:800";heading.append(icon,title);
      const summary=document.createElement("p");summary.textContent="Kirim "+count+" menu dari cabang lokal aktif ke "+branchName+"?";summary.style.cssText="margin:0 0 12px;font-size:15px;line-height:1.55;font-weight:650";
      const detail=document.createElement("div");detail.textContent="Yang dikirim hanya kategori, nama produk, harga, dan stok. Pesanan, transaksi, serta catatan kas tidak ikut dikirim.";detail.style.cssText="padding:13px 14px;border-radius:13px;background:#f3f7f4;color:#607267;font-size:13px;line-height:1.55";
      const safe=document.createElement("p");safe.textContent="Produk dengan ID lokal yang sama akan diperbarui, bukan digandakan.";safe.style.cssText="margin:12px 0 20px;color:#718078;font-size:12px;line-height:1.5";
      const actions=document.createElement("div");actions.style.cssText="display:flex;gap:10px;justify-content:flex-end";
      const cancel=document.createElement("button");cancel.type="button";cancel.textContent="Batal";cancel.style.cssText="min-height:46px;padding:0 20px;border:1px solid #dce7df;border-radius:12px;background:#fff;color:#416252;font-size:14px;font-weight:700";
      const confirm=document.createElement("button");confirm.type="button";confirm.textContent="Ya, Sinkronkan";confirm.style.cssText="min-height:46px;padding:0 18px;border:0;border-radius:12px;background:linear-gradient(135deg,#168b59,#116b46);color:#fff;font-size:14px;font-weight:750";
      actions.append(cancel,confirm);modal.append(heading,summary,detail,safe,actions);overlay.append(modal);document.body.append(overlay);
      const finish=value=>{overlay.remove();document.removeEventListener("keydown",onKey);resolve(value)};
      const onKey=e=>{if(e.key==="Escape")finish(false)};
      cancel.addEventListener("click",()=>finish(false));confirm.addEventListener("click",()=>finish(true));
      overlay.addEventListener("click",e=>{if(e.target===overlay)finish(false)});document.addEventListener("keydown",onKey);confirm.focus();
    });
  }
  document.addEventListener("click", async event => {
    const button=event.target.closest('[data-action="sync-local-catalog"]');
    if(!button)return;
    event.preventDefault();event.stopPropagation();event.stopImmediatePropagation();
    const menus=(()=>{try{return JSON.parse(localStorage.getItem("kasirku_menus")||"[]")}catch{return []}})();
    if(!Array.isArray(menus)){alert("Data menu lokal tidak valid.");return}
    const c=window.KASIRKU_CLOUD?.getState?.();
    const branchName=c?.branches?.[0]?.name||"cabang Cloud yang dipasangkan";
    const ok=await showSyncConfirm(menus.length,branchName);
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