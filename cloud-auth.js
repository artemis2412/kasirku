(() => {
"use strict";

// Preview only. This screen is intentionally isolated from the live POS
// until cloud identity, branch mapping, and local-data migration are tested.
const cfg = window.KASIRKU_SUPABASE_CONFIG;
let cloudAuthClient=null;
const CLOUD_AUTH_PREFIX="kasirku_cloud_auth_v1:";
function cloudAuthStorage(){
  const legacyId=(()=>{try{return sessionStorage.getItem("ksecurity_session")||localStorage.getItem("ksecurity_session")}catch{return null}})();
  const legacyPrefix=legacyId?"kasirku_supabase_auth_v1:"+encodeURIComponent(String(legacyId))+":":null;
  return {
    getItem:async key=>{const current=localStorage.getItem(CLOUD_AUTH_PREFIX+key);if(current!==null)return current;return legacyPrefix?localStorage.getItem(legacyPrefix+key):null},
    setItem:async(key,value)=>localStorage.setItem(CLOUD_AUTH_PREFIX+key,value),
    removeItem:async key=>{localStorage.removeItem(CLOUD_AUTH_PREFIX+key);if(legacyPrefix)localStorage.removeItem(legacyPrefix+key)}
  };
}
async function getIsolatedCloudClient(){
  await loadSdk();
  if(!cloudAuthClient){
    cloudAuthClient=window.supabase.createClient(cfg.url,cfg.publishableKey,{
      auth:{storage:cloudAuthStorage(),persistSession:true,autoRefreshToken:true,detectSessionInUrl:true}
    });
  }
  return cloudAuthClient;
}
function ensureCloudBridge(){
  if(typeof window.KASIRKU_CLOUD?.refresh==="function")return Promise.resolve();
  return new Promise((resolve,reject)=>{
    const script=document.createElement("script");
    script.src="./cloud-bridge.js?v=identity-isolation-6";
    script.onload=()=>{
      if(typeof window.KASIRKU_CLOUD?.refresh==="function")resolve();
      else reject(new Error("Modul keamanan Cloud belum termuat. Muat ulang halaman beberapa saat lagi."));
    };
    script.onerror=()=>reject(new Error("Gagal memuat modul keamanan Cloud. Periksa koneksi lalu muat ulang."));
    document.head.appendChild(script);
  });
}
if (!cfg || !cfg.url || !cfg.publishableKey) {
  console.error("KASIRKU Supabase config is missing.");
  return;
}
const layer = document.createElement("div");
layer.id = "ks-cloud-auth";
layer.innerHTML = `
  <style>
    #ks-cloud-auth{position:fixed;inset:0;z-index:100000;background:radial-gradient(ellipse at 20% 15%,#285c43 0,transparent 38%),linear-gradient(145deg,#10271e,#174b36 55%,#0d3024);display:grid;place-items:center;padding:18px;overflow:auto;font-family:system-ui,-apple-system,sans-serif;color:#20352a}
    #ks-cloud-auth *{box-sizing:border-box}
    .kca-card{width:min(100%,430px);background:#fff;border:1px solid #ffffff80;border-radius:25px;padding:26px 23px 22px;box-shadow:0 24px 70px #0004}
    .kca-brand{text-align:center;margin-bottom:22px}.kca-logo{width:62px;height:62px;border-radius:19px;margin:0 auto 13px;background:linear-gradient(145deg,#e3f3e8,#b8ddc5);display:grid;place-items:center;color:#176c48;font-size:27px;font-weight:900;box-shadow:0 8px 22px #176c4820}
    .kca-brand small{display:block;color:#7d9485;font-size:10px;letter-spacing:3px;font-weight:800}.kca-brand h1{margin:6px 0 4px;font-size:27px;letter-spacing:1px;color:#203b2d}.kca-brand p{margin:0;color:#7b8981;font-size:13px}
    .kca-tabs{display:grid;grid-template-columns:1fr 1fr;gap:5px;padding:4px;background:#f0f5f1;border-radius:12px;margin:0 0 18px}
    .kca-tabs button{border:0;border-radius:9px;padding:11px;background:transparent;color:#63776a;font:inherit;font-size:13px;font-weight:750}
    .kca-tabs button.active{background:#fff;color:#176c48;box-shadow:0 2px 8px #193b2812}
    .kca-field{display:block;margin:12px 0 0;color:#40564a;font-size:12px;font-weight:750}
    .kca-field input{display:block;width:100%;height:49px;margin-top:6px;padding:0 13px;border:1px solid #dce6de;border-radius:12px;background:#f9fbf9;color:#20352a;font:inherit;font-size:16px;outline:none}
    .kca-field input:focus{border-color:#218658;box-shadow:0 0 0 3px #2186581c}
    .kca-primary{width:100%;min-height:50px;margin-top:18px;border:0;border-radius:13px;background:linear-gradient(135deg,#188b59,#116b46);color:white;font:inherit;font-weight:800;font-size:15px}
    .kca-primary:disabled{opacity:.6}
    .kca-message{min-height:20px;margin:12px 0 0;text-align:center;color:#64766b;font-size:12px;line-height:1.5}
    .kca-message.error{color:#b42318}.kca-message.success{color:#137347}
    .kca-note{margin-top:17px;padding:11px 12px;border-radius:11px;background:#f3f7f4;color:#75857a;font-size:11px;line-height:1.5}
    .kca-hidden{display:none!important}
    .kca-preview-tag{margin-top:16px;text-align:center;color:#9aaa9f;font-size:10px;letter-spacing:1px}
    @media(max-width:360px){.kca-card{padding:22px 17px}.kca-brand h1{font-size:24px}}
  </style>
  <section class="kca-card" aria-label="Akun KASIRKU">
    <header class="kca-brand">
      <div class="kca-logo">K</div><small>SISTEM KASIR MODERN</small>
      <h1>KASIRKU</h1><p id="kca-subtitle">Masuk ke akun tokomu</p>
    </header>
    <div class="kca-tabs">
      <button type="button" class="active" data-tab="login">Masuk</button>
      <button type="button" data-tab="register">Registrasi Toko</button>
    </div>
    <form id="kca-form">
      <div id="kca-name-wrap" class="kca-hidden">
        <label class="kca-field">Nama pemilik<input name="displayName" autocomplete="name" maxlength="80" placeholder="Nama kamu"></label>
        <label class="kca-field">Nama toko<input name="storeName" maxlength="100" placeholder="Contoh: Warung Berkah"></label>
        <label class="kca-field">Cabang pertama<input name="branchName" maxlength="100" value="Cabang Utama"></label>
      </div>
      <label class="kca-field">Email<input name="email" type="email" required autocomplete="email" placeholder="nama@email.com"></label>
      <label class="kca-field">Kata sandi<input name="password" type="password" required minlength="8" autocomplete="current-password" placeholder="Minimal 8 karakter"></label>
      <button class="kca-primary" id="kca-submit" type="submit">Masuk</button>
      <button type="button" id="kca-forgot" style="display:block;margin:12px auto 0;border:0;background:none;color:#16734b;font:inherit;font-size:12px;font-weight:750">Lupa kata sandi?</button>
      <div id="kca-recovery-wrap" class="kca-hidden">
        <label class="kca-field">Kata sandi baru<input name="newPassword" type="password" minlength="8" autocomplete="new-password" placeholder="Minimal 8 karakter"></label>
        <label class="kca-field">Ulangi kata sandi<input name="confirmPassword" type="password" minlength="8" autocomplete="new-password" placeholder="Ulangi kata sandi"></label>
        <button type="button" id="kca-save-password" class="kca-primary">Simpan kata sandi & aktifkan akun</button>
      </div>
    </form>
    <p id="kca-message" class="kca-message" role="status" aria-live="polite"></p>
    <div class="kca-note">Ini masih pratinjau login cloud. Data transaksi lokal KASIRKU tidak disentuh dan belum dipindahkan ke cloud.</div>
    <div class="kca-preview-tag">KASIRKU • CLOUD AUTH PREVIEW</div>
  </section>`;
document.body.appendChild(layer);

function loadSdk() {
  return new Promise((resolve,reject) => {
    if (window.supabase?.createClient) return resolve();
    const script=document.createElement("script");
    script.src="https://cdn.jsdelivr.net/npm/@supabase/supabase-js@2.57.4/dist/umd/supabase.min.js";
    script.onload=resolve; script.onerror=()=>reject(new Error("Gagal memuat pustaka login. Periksa koneksi internet."));
    document.head.appendChild(script);
  });
}
const form=layer.querySelector("#kca-form");
const nameWrap=layer.querySelector("#kca-name-wrap");
const submit=layer.querySelector("#kca-submit");
const message=layer.querySelector("#kca-message");
const forgot=layer.querySelector("#kca-forgot");
const recoveryWrap=layer.querySelector("#kca-recovery-wrap");
const savePasswordButton=layer.querySelector("#kca-save-password");
let mode="login",client=null,storeSetupInProgress=false;
const inviteMode=new URLSearchParams(location.search).has("accept-staff-invite");
function say(text,type=""){message.textContent=text;message.className="kca-message "+type}
function returnToDashboard(){
  window.dispatchEvent(new CustomEvent("kasirku:cloud-context"));
  const url=new URL(window.location.href);
  url.searchParams.delete("cloud-auth-preview");
  url.searchParams.delete("accept-staff-invite");
  history.replaceState({},document.title,url.pathname+url.search+url.hash);
  layer.remove();
}
layer.querySelectorAll("[data-tab]").forEach(btn=>btn.addEventListener("click",()=>{
  mode=btn.dataset.tab;
  layer.querySelectorAll("[data-tab]").forEach(b=>b.classList.toggle("active",b===btn));
  nameWrap.classList.toggle("kca-hidden",mode!=="register");
  form.elements.password.autocomplete=mode==="login"?"current-password":"new-password";
  submit.textContent=mode==="login"?"Masuk":"Registrasi Sekarang";
  layer.querySelector("#kca-subtitle").textContent=mode==="login"?"Masuk ke akun tokomu":"Daftarkan Toko Anda";
  forgot.style.display=mode==="login"?"block":"none";
  recoveryWrap.classList.add("kca-hidden");
  say("");
}));
forgot.addEventListener("click",async()=>{
  const email=String(form.elements.email.value||"").trim().toLowerCase();
  if(!email){say("Masukkan email akun terlebih dahulu.","error");form.elements.email.focus();return;}
  forgot.disabled=true; say("Mengirim kode pemulihan…");
  try{
    if(!client){await loadSdk();client=await getIsolatedCloudClient();}
    const {error}=await client.auth.resetPasswordForEmail(email,{redirectTo:window.location.origin+window.location.pathname+"?cloud-auth-preview"});
    if(error) throw error;
    say("Jika email terdaftar, tautan pemulihan akan dikirim. Buka email ini dari perangkat yang sama, lalu buat kata sandi baru.","success");
  }catch(error){say(error.message||"Gagal mengirim kode pemulihan.","error");}
  finally{forgot.disabled=false;}
});
savePasswordButton.addEventListener("click",async()=>{
  const newPassword=String(form.elements.newPassword.value||"");
  const confirmPassword=String(form.elements.confirmPassword.value||"");
  if(newPassword.length<8){say("Kata sandi baru minimal 8 karakter.","error");return;}
  if(newPassword!==confirmPassword){say("Ulangi kata sandi harus sama.","error");return;}
  savePasswordButton.disabled=true;
  try{
    if(!client)client=await getIsolatedCloudClient();
    const {error}=await client.auth.updateUser({password:newPassword});
    if(error) throw error;
    if(inviteMode){
      const {data:accepted,error:acceptError}=await client.rpc("accept_staff_invitation");
      if(acceptError) throw acceptError;
      if(accepted?.status!=="accepted") throw new Error("Kata sandi tersimpan, tetapi undangan belum diterima. Hubungi Owner.");
      const {data:memberships,error:membershipError}=await client.from("store_memberships").select("store_id,role,is_active").eq("user_id",session.user.id).eq("is_active",true);
      if(membershipError)throw membershipError;
      await showPinEnrollment(session.user,memberships||[]);
    }else{
      recoveryWrap.classList.add("kca-hidden");
      say("Kata sandi berhasil diperbarui. Silakan masuk menggunakan kata sandi baru.","success");
      await client.auth.signOut();
    }
  }catch(error){say(error.message||"Gagal memperbarui kata sandi.","error");}
  finally{savePasswordButton.disabled=false;}
});
async function showPinEnrollment(cloudUser,memberships){
  if(!window.KASIRKU_AUTH?.enrollVerifiedCloudUser)throw new Error("Modul keamanan PIN belum siap. Muat ulang halaman.");
  if(!Array.isArray(memberships)||memberships.length!==1)throw new Error("Akun ini memiliki lebih dari satu toko aktif. Pengaitan otomatis ditahan.");
  const role=String(memberships[0].role||"").toLowerCase();
  if(!["owner","admin","cashier"].includes(role))throw new Error("Peran akun Cloud belum didukung untuk aktivasi perangkat.");
  const email=String(cloudUser.email||"").toLowerCase();
  const displayName=String(cloudUser.user_metadata?.display_name||cloudUser.user_metadata?.full_name||email.split("@")[0]||"Pengguna").trim();
  layer.querySelector(".kca-tabs")?.classList.add("kca-hidden");
  form.classList.add("kca-hidden");
  recoveryWrap.classList.add("kca-hidden");
  layer.querySelector("#kca-subtitle").textContent="Aktifkan perangkat ini";
  layer.querySelector(".kca-note").textContent="Akun Cloud sudah terverifikasi. Buat PIN 6 angka untuk membuka KASIRKU di perangkat ini.";
  layer.querySelector(".kca-preview-tag").textContent="VERIFIKASI CLOUD BERHASIL";
  const panel=document.createElement("form");
  panel.id="kca-pin-enroll";
  panel.innerHTML='<label class="kca-field">PIN perangkat (6 angka)<input name="pin" type="password" inputmode="numeric" maxlength="6" minlength="6" autocomplete="new-password" required></label><label class="kca-field">Ulangi PIN perangkat<input name="confirm" type="password" inputmode="numeric" maxlength="6" minlength="6" autocomplete="new-password" required></label><button class="kca-primary" type="submit">Aktifkan KASIRKU</button>';
  form.after(panel);
  const oldUsers=(window.KASIRKU_AUTH.getLocalUsers()||[]).filter(u=>u.active!==false&&u.role===role);
  if(oldUsers.length){
    const legacy=document.createElement("form");
    legacy.id="kca-link-old-pin";
    legacy.style.cssText="margin-top:16px;padding-top:15px;border-top:1px solid #e5ece7";
    legacy.innerHTML='<p style="margin:0 0 8px;color:#52675a;font-size:12px;font-weight:750">Sudah punya akun PIN di perangkat ini?</p><label class="kca-field">Pilih akun lokal<select name="localId" required>'+oldUsers.map(u=>'<option value="'+String(u.id).replace(/&/g,"&amp;").replace(/"/g,"&quot;")+'">'+String(u.name).replace(/[&<>"]/g,c=>({"&":"&amp;","<":"&lt;",">":"&gt;",'"':"&quot;"}[c]))+'</option>').join("")+'</select></label><label class="kca-field">PIN lama<input name="oldPin" type="password" inputmode="numeric" maxlength="6" minlength="6" required></label><button class="kca-primary" type="submit" style="background:#eaf3ed;color:#176c48;border:1px solid #d7e8dc">Hubungkan akun lama</button>';
    panel.after(legacy);
    legacy.addEventListener("submit",async event=>{
      event.preventDefault();
      const button=legacy.querySelector("button");button.disabled=true;
      try{
        await window.KASIRKU_AUTH.linkExistingLocalUser({localId:legacy.elements.localId.value,userId:cloudUser.id,email,role,pin:legacy.elements.oldPin.value});
        say("Akun lokal lama berhasil ditautkan. Membuka KASIRKU…","success");
        setTimeout(returnToDashboard,700);
      }catch(error){say(error.message||"Gagal menghubungkan akun lokal lama.","error");button.disabled=false;}
    });
  }
  say("Akun "+email+" terverifikasi sebagai "+role.toUpperCase()+". Buat PIN perangkat baru atau hubungkan PIN lama.","success");
  panel.addEventListener("submit",async event=>{
    event.preventDefault();
    const pin=String(panel.elements.pin.value||""),confirm=String(panel.elements.confirm.value||"");
    if(!/^\d{6}$/.test(pin)){say("PIN harus tepat 6 angka.","error");return;}
    if(pin!==confirm){say("Ulangi PIN harus sama.","error");return;}
    const button=panel.querySelector("button");button.disabled=true;say("Mengaktifkan perangkat…");
    try{
      await window.KASIRKU_AUTH.enrollVerifiedCloudUser({userId:cloudUser.id,email,role,name:displayName,pin});
      say("Perangkat berhasil diaktifkan. Membuka KASIRKU…","success");
      setTimeout(returnToDashboard,700);
    }catch(error){say(error.message||"Gagal mengaktifkan PIN perangkat.","error");button.disabled=false;}
  });
}
async function finishPendingStore(session){
  if(!session?.user||storeSetupInProgress)return;
  let pending=null;
  try{pending=JSON.parse(sessionStorage.getItem("kca_pending_store")||"null")}catch{}
  if(!pending){
    const meta=session.user.user_metadata||{};
    if(meta.pending_store_name&&meta.pending_branch_name&&meta.display_name){
      pending={storeName:String(meta.pending_store_name),branchName:String(meta.pending_branch_name),displayName:String(meta.display_name)};
    }
  }
  if(!pending)return;
  storeSetupInProgress=true;
  try{
    const {data:membership,error:membershipError}=await client.from("store_memberships")
      .select("store_id").eq("user_id",session.user.id).eq("is_active",true).limit(1);
    if(membershipError)throw membershipError;
    if(membership?.length){
      sessionStorage.removeItem("kca_pending_store");
      const {data:existingMemberships,error:existingError}=await client.from("store_memberships").select("store_id,role,is_active").eq("user_id",session.user.id).eq("is_active",true);
      if(existingError)throw existingError;
      const {error:metadataError}=await client.auth.updateUser({data:{pending_store_name:null,pending_branch_name:null}});
      if(metadataError)throw metadataError;
      await showPinEnrollment(session.user,existingMemberships||[]);
      return;
    }
    const {error}=await client.rpc("create_my_store",{
      p_store_name:pending.storeName,p_branch_name:pending.branchName,p_display_name:pending.displayName
    });
    if(error)throw error;
    sessionStorage.removeItem("kca_pending_store");
    const {data:memberships,error:membershipError2}=await client.from("store_memberships").select("store_id,role,is_active").eq("user_id",session.user.id).eq("is_active",true);
    if(membershipError2)throw membershipError2;
    const {error:metadataError}=await client.auth.updateUser({data:{pending_store_name:null,pending_branch_name:null}});
    if(metadataError)throw metadataError;
    await showPinEnrollment(session.user,memberships||[]);
  }catch(error){console.error("KASIRKU store setup:",error);say(error.message||"Konfirmasi berhasil, tetapi pembuatan toko belum selesai. Silakan masuk kembali.","error");}
  finally{storeSetupInProgress=false;}
}
form.addEventListener("submit",async event=>{
  event.preventDefault();
  submit.disabled=true; say("Memproses…");
  const fd=new FormData(form);
  const email=String(fd.get("email")||"").trim().toLowerCase();
  const password=String(fd.get("password")||"");
  try{
    if(!client){
      await loadSdk();
      client=await getIsolatedCloudClient();
    }
    if(mode==="login"){
      const {data,error}=await client.auth.signInWithPassword({email,password});
      if(error) throw error;
      let {data:memberships,error:membershipError}=await client.from("store_memberships")
        .select("store_id,role,is_active").eq("user_id",data.user.id).eq("is_active",true);
      if(membershipError) throw membershipError;
      if(!memberships?.length){
        const {data:accepted,error:acceptError}=await client.rpc("accept_staff_invitation");
        if(!acceptError&&accepted?.status==="accepted"){
          const result=await client.from("store_memberships").select("store_id,role,is_active")
            .eq("user_id",data.user.id).eq("is_active",true);
          if(result.error)throw result.error;
          memberships=result.data||[];
        }
      }
      if(!memberships?.length){
        const pending=JSON.parse(sessionStorage.getItem("kca_pending_store")||"null");
        if(pending){
          const {error:storeError}=await client.rpc("create_my_store",{
            p_store_name:pending.storeName,p_branch_name:pending.branchName,p_display_name:pending.displayName
          });
          if(storeError)throw storeError;
          sessionStorage.removeItem("kca_pending_store");
          const result=await client.from("store_memberships").select("store_id,role,is_active")
            .eq("user_id",data.user.id).eq("is_active",true);
          if(result.error)throw result.error;
          memberships=result.data||[];
        }
      }
      if(!memberships?.length)throw new Error("Akun berhasil masuk, tetapi belum memiliki toko atau undangan pegawai yang aktif.");
      await showPinEnrollment(data.user,memberships);
    }else{
      const displayName=String(fd.get("displayName")||"").trim();
      const storeName=String(fd.get("storeName")||"").trim();
      const branchName=String(fd.get("branchName")||"").trim();
      if(displayName.length<2||storeName.length<2||branchName.length<2) throw new Error("Isi nama pemilik, nama toko, dan nama cabang (minimal 2 karakter).");
      sessionStorage.setItem("kca_pending_store",JSON.stringify({displayName,storeName,branchName}));
      const {data,error}=await client.auth.signUp({
        email,password,
        options:{
          data:{display_name:displayName,pending_store_name:storeName,pending_branch_name:branchName},
          emailRedirectTo: window.location.origin + window.location.pathname + "?cloud-auth-preview"
        }
      });
      if(error) throw error;
      if(!data.session){
        say("Akun dibuat. Buka tautan konfirmasi dari email di perangkat ini agar pendaftaran toko dapat diselesaikan.","success");
      }else{
        await finishPendingStore(data.session);
      }
    }
  }catch(error){
    console.error("KASIRKU cloud auth:",error);
    say(error.message||"Terjadi kesalahan. Coba lagi.","error");
  }finally{submit.disabled=false}
});
ensureCloudBridge().then(()=>loadSdk()).then(async()=>{
  client=await getIsolatedCloudClient();
  const showInviteSetup=(session)=>{
    if(!inviteMode||!session?.user)return;
    layer.querySelectorAll(".kca-tabs").forEach(x=>x.classList.add("kca-hidden"));
    nameWrap.classList.add("kca-hidden");
    form.querySelectorAll(".kca-field").forEach(x=>{if(!x.closest("#kca-recovery-wrap"))x.classList.add("kca-hidden")});
    submit.classList.add("kca-hidden");
    forgot.classList.add("kca-hidden");
    recoveryWrap.classList.remove("kca-hidden");
    layer.querySelector("#kca-subtitle").textContent="Aktifkan akun pegawai";
    say("Undangan terverifikasi. Buat kata sandi pribadi untuk akun KASIRKU.","success");
  };
  client.auth.onAuthStateChange((event,session)=>{
    if(inviteMode&&(event==="SIGNED_IN"||event==="INITIAL_SESSION")&&session?.user) showInviteSetup(session);
    if(event==="PASSWORD_RECOVERY"&&!inviteMode){
      recoveryWrap.classList.remove("kca-hidden");
      layer.querySelector("#kca-subtitle").textContent="Buat kata sandi baru";
      say("Tautan pemulihan valid. Masukkan kata sandi baru.","success");
    }
    if((event==="SIGNED_IN"||event==="INITIAL_SESSION")&&!inviteMode) setTimeout(()=>finishPendingStore(session),0);
  });
  const {data:{session}}=await client.auth.getSession();
  if(inviteMode){
    if(session?.user) showInviteSetup(session);
    else say("Buka tautan undangan terbaru dari email untuk membuat kata sandi akun pegawai.","error");
  }else if(session) await finishPendingStore(session);
}).catch(error=>say(error.message,"error"));
})();