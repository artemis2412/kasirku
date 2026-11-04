(() => {
  "use strict";
  const PANEL_ID="ks-cloud-check-panel";
  const esc=value=>String(value??"").replace(/[&<>"']/g,c=>({"&":"&amp;","<":"&lt;",">":"&gt;",'"':"&quot;","'":"&#39;"}[c]));
  function localSessionUser(){
    try{
      const id=localStorage.getItem("ksecurity_session");
      const users=JSON.parse(localStorage.getItem("ksecurity_users")||"[]");
      return users.find(x=>String(x.id)===String(id)&&x.active!==false)||null;
    }catch{return null}
  }
  function isOwner(){
    const active=localSessionUser();
    return window.KASIRKU_LOCAL_ROLE==="owner"||active?.role==="owner";
  }
  function currentPage(){return document.getElementById("page-title")?.textContent?.trim()||""}
  function panelMarkup(){
    return '<section id="'+PANEL_ID+'" style="margin:18px 0;padding:18px;border:1px solid #d9e8dd;border-radius:18px;background:linear-gradient(145deg,#fff,#f6faf7);color:#203b2d;box-shadow:0 8px 24px #123b2610">'
      +'<div style="display:flex;align-items:center;gap:11px"><div style="width:42px;height:42px;display:grid;place-items:center;border-radius:13px;background:#e5f3e9;color:#17764d;font-size:21px">☁</div><div style="min-width:0;flex:1"><div style="font-size:10px;font-weight:800;letter-spacing:1.8px;color:#23845a">KONEKSI & PEMETAAN</div><h3 style="margin:3px 0 0;font-size:18px">Status Cloud KASIRKU</h3></div><span id="ks-cloud-dot" style="width:10px;height:10px;border-radius:50%;background:#b9c5bd"></span></div>'
      +'<div id="ks-cloud-state" role="status" style="margin-top:14px;padding:12px;border-radius:12px;background:#f1f6f2;color:#52675a;font-size:13px;line-height:1.55">Memeriksa koneksi Cloud…</div>'
      +'<div id="ks-cloud-store" style="margin-top:12px"></div>'
      +'<div style="margin-top:14px;padding-top:13px;border-top:1px solid #e5eee7"><div style="font-size:13px;font-weight:800;margin-bottom:8px">Pemetaan cabang perangkat ini</div><div id="ks-cloud-mappings" style="display:grid;gap:8px"><span style="color:#718078;font-size:12px">Memuat pemetaan…</span></div></div>'
      +'<div style="margin-top:14px;padding-top:13px;border-top:1px solid #e5eee7"><div style="font-size:13px;font-weight:800;margin-bottom:8px">Undang Pegawai Cloud</div><form id="ks-cloud-invite-form" style="display:grid;gap:9px"><input name="email" type="email" required placeholder="Email pegawai" style="box-sizing:border-box;width:100%;padding:12px;border:1px solid #dce6de;border-radius:10px;font:inherit"><select name="branch" required style="width:100%;padding:12px;border:1px solid #dce6de;border-radius:10px;background:white;font:inherit"><option value="">Pilih cabang Cloud</option></select><select name="role" required style="width:100%;padding:12px;border:1px solid #dce6de;border-radius:10px;background:white;font:inherit"><option value="cashier">Kasir</option><option value="admin">Admin</option></select><button type="submit" style="min-height:43px;border:0;border-radius:11px;background:#e4f2e8;color:#176b46;font:700 13px system-ui">Kirim Undangan</button><div id="ks-cloud-invite-message" role="status" style="font-size:12px;line-height:1.5;color:#718078"></div></form><p style="margin:8px 0 0;color:#829087;font-size:10px;line-height:1.45">Undangan hanya mendukung Admin dan Kasir. Email harus dikonfirmasi oleh pegawai.</p></div>'
      +'<button id="ks-cloud-refresh" type="button" style="width:100%;min-height:44px;margin-top:15px;border:0;border-radius:12px;background:linear-gradient(135deg,#188b59,#116b46);color:white;font:700 13px system-ui">↻ Periksa ulang Cloud</button>'
      +'<p style="margin:10px 0 0;color:#829087;font-size:11px;line-height:1.5">Panel ini menampilkan status Cloud, pemetaan cabang, dan undangan pegawai. Transaksi belum disinkronkan otomatis.</p></section>';
  }
  function renderStatus(){
    const state=window.KASIRKU_CLOUD?.getState?.()||{};
    const mappings=window.KASIRKU_CLOUD?.getLocalBranchMappings?.()||{mappings:[],localBranches:[]};
    const status=document.getElementById("ks-cloud-state"),store=document.getElementById("ks-cloud-store"),mapBox=document.getElementById("ks-cloud-mappings"),dot=document.getElementById("ks-cloud-dot");
    if(!status||!store||!mapBox)return;
    if(!state.ready){status.textContent="Menunggu koneksi Cloud siap…";dot.style.background="#d5a43b"}
    else if(state.error){status.textContent="Cloud belum dapat diperiksa: "+state.error;dot.style.background="#c44b45"}
    else if(!state.authenticated){status.textContent="Belum login Cloud pada perangkat ini. Buka mode login Cloud dan masuk dengan akun Owner.";dot.style.background="#d5a43b"}
    else if(!state.store){status.textContent="Login Cloud terdeteksi, tetapi keanggotaan toko aktif belum ditemukan.";dot.style.background="#d5a43b"}
    else{status.textContent="Akun Cloud aktif dan toko berhasil dikenali.";dot.style.background="#168b58"}
    const branchSelect=document.querySelector("#ks-cloud-invite-form select[name=branch]");
    if(branchSelect){
      const previous=branchSelect.value;
      branchSelect.innerHTML='<option value="">Pilih cabang Cloud</option>'+(state.branches||[]).map(b=>'<option value="'+esc(b.id)+'">'+esc(b.name)+'</option>').join("");
      if((state.branches||[]).some(b=>b.id===previous))branchSelect.value=previous;
      const form=document.getElementById("ks-cloud-invite-form");
      if(form&&!form.dataset.bound){
        form.dataset.bound="1";
        form.addEventListener("submit",async event=>{
          event.preventDefault();
          const button=form.querySelector('button[type=submit]'),msg=document.getElementById("ks-cloud-invite-message");
          button.disabled=true;button.textContent="Mengirim…";msg.textContent="";
          try{
            const result=await window.KASIRKU_CLOUD.inviteStaff(form.elements.email.value,form.elements.role.value,form.elements.branch.value);
            msg.style.color="#168153";
            msg.textContent=result.existing_user?"Email sudah punya akun Cloud. Minta pegawai masuk dengan email itu untuk menerima undangan.":"Undangan berhasil dikirim ke "+(result.email||form.elements.email.value)+".";
            form.elements.email.value="";
          }catch(error){msg.style.color="#b42318";msg.textContent=error.message||"Undangan gagal dikirim."}
          finally{button.disabled=false;button.textContent="Kirim Undangan"}
        });
      }
    }
    const cloudRole=String(state.store?.role||"").toLowerCase();
    const localUser=localSessionUser();
    const localRole=String(localUser?.role||window.KASIRKU_LOCAL_ROLE||"").toLowerCase();
    const greetingRole=localRole==="owner"?"Owner":localRole==="admin"?"Admin":localRole==="supervisor"?"Supervisor":localRole==="cashier"?"Kasir":(cloudRole==="owner"?"Owner":cloudRole==="admin"?"Admin":"Pegawai");
    const greetingName=localUser?.name||state.user?.name||state.user?.email?.split("@")[0]||"Pengguna";
    store.innerHTML=state.store?'<div style="padding:14px;border:1px solid #cfe5d5;border-radius:14px;background:linear-gradient(135deg,#f1faf3,#fff)"><div style="font-size:17px;font-weight:850;color:#176b46">Halo '+greetingRole+', '+esc(greetingName)+' 👋</div><div style="margin-top:7px;font-size:10px;color:#829087;letter-spacing:1px">TOKO CLOUD</div><b style="display:block;margin-top:3px;font-size:14px">'+esc(state.store.name||"Toko terhubung")+'</b><div style="margin-top:4px;color:#718078;font-size:11px">Peran: '+esc(state.store.role||"-")+' · '+(state.branches||[]).length+' cabang dapat diakses</div></div>':'<div style="padding:12px;border:1px solid #e5eee7;border-radius:12px;background:#fff;color:#718078;font-size:13px">Halo! Menunggu akun Cloud terhubung…</div>';
    const locals=mappings.localBranches||[],saved=mappings.mappings||[];
    if(!locals.length){mapBox.innerHTML='<span style="color:#b06a25;font-size:12px">Daftar cabang lokal belum terbaca. Muat ulang aplikasi setelah login PIN.</span>';return}
    mapBox.innerHTML=locals.map(local=>{
      const match=saved.find(m=>m.localBranchId===local.id);
      const remote=match?(state.branches||[]).find(b=>b.id===match.cloudBranchId):null;
      const label=match?(remote?remote.name:match.cloudBranchName):"Belum dipasangkan";
      return '<div style="padding:10px 11px;border:1px solid '+(match?'#cfe5d5':'#e6ebe7')+';border-radius:11px;background:'+(match?'#f4faf5':'#fff')+'"><div style="display:flex;align-items:center;justify-content:space-between;gap:8px"><b style="font-size:13px">'+esc(local.name)+'</b><span style="font-size:10px;font-weight:800;color:'+(match?'#168153':'#8b968e')+'">'+(match?'TERHUBUNG':'BELUM DIPASANG')+'</span></div><div style="margin-top:5px;color:#718078;font-size:12px">↳ Cloud: '+esc(label)+'</div></div>';
    }).join("");
  }
  function mount(){
    if(!isOwner()||currentPage()!=="Pengaturan")return;
    const host=document.getElementById("view");
    if(!host||host.querySelector("#"+PANEL_ID))return;
    host.appendChild(document.createRange().createContextualFragment(panelMarkup()));
    document.getElementById("ks-cloud-refresh").addEventListener("click",async()=>{
      const button=document.getElementById("ks-cloud-refresh");button.disabled=true;button.textContent="Memeriksa…";
      try{await window.KASIRKU_CLOUD?.refresh?.()}finally{button.disabled=false;button.textContent="↻ Periksa ulang Cloud";renderStatus()}
    });
    renderStatus();
  }
  window.KASIRKU_CLOUD_PANEL_MOUNT=mount;
  window.addEventListener("kasirku:cloud-context",renderStatus);
  window.addEventListener("kasirku:page-ready",()=>{mount();renderStatus()});
  const observer=new MutationObserver(()=>{mount()});
  observer.observe(document.body,{childList:true,subtree:true,characterData:true});
  window.addEventListener("load",()=>{mount();renderStatus()});
  setTimeout(()=>{mount();renderStatus()},800);
})();