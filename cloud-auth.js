(() => {
"use strict";

// Preview only. This screen is intentionally isolated from the live POS
// until cloud identity, branch mapping, and local-data migration are tested.
const cfg = window.KASIRKU_SUPABASE_CONFIG;
let cloudAuthClient=null, cloudAuthLocalId=null;
function getLocalPinSession(){
  try{
    const id=localStorage.getItem("ksecurity_session");
    const users=JSON.parse(localStorage.getItem("ksecurity_users")||"[]");
    const u=Array.isArray(users)?users.find(x=>String(x?.id)===String(id)&&x?.active!==false):null;
    return u?{id:String(u.id),name:String(u.name||""),role:String(u.role||"").toLowerCase()}:null;
  }catch{return null}
}
async function getIsolatedCloudClient(){
  const local=getLocalPinSession();
  if(!local)throw new Error("Masuk dengan PIN KASIRKU terlebih dahulu.");
  await loadSdk();
  if(!cloudAuthClient||cloudAuthLocalId!==local.id){
    const prefix="kasirku_supabase_auth_v1:"+encodeURIComponent(local.id)+":";
    const storage={
      getItem:async key=>localStorage.getItem(prefix+key),
      setItem:async(key,value)=>localStorage.setItem(prefix+key,value),
      removeItem:async key=>localStorage.removeItem(prefix+key)
    };
    cloudAuthClient=window.supabase.createClient(cfg.url,cfg.publishableKey,{
      auth:{storage,persistSession:true,autoRefreshToken:true,detectSessionInUrl:true}
    });
    cloudAuthLocalId=local.id;
  }
  return cloudAuthClient;
}
async function linkLocalCloudIdentity(){
  const local=getLocalPinSession();
  if(!local)throw new Error("Sesi PIN lokal tidak ditemukan.");
  const client=await getIsolatedCloudClient();
  const {data:{session},error:sessionError}=await client.auth.getSession();
  if(sessionError)throw sessionError;
  if(!session?.user)throw new Error("Akun Cloud belum login.");
  const {data:memberships,error}=await client.from("store_memberships")
    .select("store_id,role,is_active").eq("user_id",session.user.id).eq("is_active",true);
  if(error)throw error;
  if(!memberships?.length)throw new Error("Keanggotaan Cloud aktif tidak ditemukan.");
  if(memberships.length!==1)throw new Error("Akun Cloud memiliki lebih dari satu keanggotaan toko. Pengaitan ditahan.");
  const expected=local.role==="owner"?"owner":local.role==="admin"?"admin":"cashier";
  const actual=String(memberships[0].role||"").toLowerCase();
  if(actual!==expected)throw new Error("Peran tidak cocok: PIN lokal "+expected+" tetapi akun Cloud "+actual+". Pengaitan dibatalkan.");
  const linkKey="kasirku_cloud_identity_link_v1:"+encodeURIComponent(local.id);
  const old=(()=>{try{return JSON.parse(localStorage.getItem(linkKey)||"null")}catch{return null}})();
  if(old&&old.userId!==session.user.id)throw new Error("Akun PIN ini sudah ditautkan ke akun Cloud lain.");
  const users=(()=>{try{return JSON.parse(localStorage.getItem("ksecurity_users")||"[]")}catch{return []}})();
  for(const other of users){
    if(!other?.id||String(other.id)===local.id)continue;
    const otherLink=(()=>{try{return JSON.parse(localStorage.getItem("kasirku_cloud_identity_link_v1:"+encodeURIComponent(String(other.id))||"null"))}catch{return null}})();
    if(otherLink?.userId===session.user.id)throw new Error("Akun Cloud ini sudah ditautkan ke akun PIN lain pada perangkat ini.");
  }
  localStorage.setItem(linkKey,JSON.stringify({userId:session.user.id,email:String(session.user.email||"").toLowerCase(),role:actual,linkedAt:new Date().toISOString()}));
  if(window.KASIRKU_CLOUD?.refresh)await window.KASIRKU_CLOUD.refresh();
  return true;
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
      await linkLocalCloudIdentity();
      recoveryWrap.classList.add("kca-hidden");
      say("Akun pegawai aktif! Kata sandi tersimpan dan undangan berhasil diterima.","success");
      layer.querySelectorAll(".kca-tabs,.kca-note").forEach(x=>x.classList.add("kca-hidden"));
    }else{
      recoveryWrap.classList.add("kca-hidden");
      say("Kata sandi berhasil diperbarui. Silakan masuk menggunakan kata sandi baru.","success");
      await client.auth.signOut();
    }
  }catch(error){say(error.message||"Gagal memperbarui kata sandi.","error");}
  finally{savePasswordButton.disabled=false;}
});
async function finishPendingStore(session){
  if(!session?.user||storeSetupInProgress)return;
  const pending=JSON.parse(sessionStorage.getItem("kca_pending_store")||"null");
  if(!pending)return;
  storeSetupInProgress=true;
  try{
    const {data:membership,error:membershipError}=await client.from("store_memberships")
      .select("store_id").eq("user_id",session.user.id).eq("is_active",true).limit(1);
    if(membershipError)throw membershipError;
    if(membership?.length){sessionStorage.removeItem("kca_pending_store");return;}
    const {error}=await client.rpc("create_my_store",{
      p_store_name:pending.storeName,p_branch_name:pending.branchName,p_display_name:pending.displayName
    });
    if(error)throw error;
    await linkLocalCloudIdentity();
    sessionStorage.removeItem("kca_pending_store");
    say("Email berhasil dikonfirmasi, toko dibuat, dan akun PIN terhubung.","success");
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
      const local=getLocalPinSession();
      if(!local)throw new Error("Masuk dengan PIN KASIRKU terlebih dahulu sebelum menghubungkan akun Cloud.");
      const {data,error}=await client.auth.signInWithPassword({email,password});
      if(error) throw error;
      const {data:membership,error:membershipError}=await client.from("store_memberships")
        .select("store_id,role,is_active").eq("user_id",data.user.id).eq("is_active",true).limit(1);
      if(membershipError) throw membershipError;
      if(membership?.length){
        await linkLocalCloudIdentity();
        sessionStorage.removeItem("kca_pending_store");
        say("Login berhasil. Akun Cloud cocok dengan peran PIN perangkat ini.", "success");
      }else{
        const {data:accepted,error:acceptError}=await client.rpc("accept_staff_invitation");
        if(!acceptError&&accepted?.status==="accepted"){
          await linkLocalCloudIdentity();
          say("Undangan diterima. Akun Cloud cocok dengan peran PIN perangkat ini.", "success");
          return;
        }
        const pending=JSON.parse(sessionStorage.getItem("kca_pending_store")||"null");
        if(pending){
          const {error:storeError}=await client.rpc("create_my_store",{
            p_store_name:pending.storeName,p_branch_name:pending.branchName,p_display_name:pending.displayName
          });
          if(storeError) throw storeError;
          await linkLocalCloudIdentity();
          sessionStorage.removeItem("kca_pending_store");
          say("Login, toko, dan pengaitan akun berhasil.", "success");
        }else{
          say("Login berhasil, tetapi akun ini belum memiliki toko. Pendaftaran toko perlu diselesaikan terlebih dahulu.", "success");
        }
      }
    }else{
      const local=window.KASIRKU_CLOUD.getState().localSession;
      if(!local||local.role!=="owner")throw new Error("Registrasi toko hanya dapat dilakukan setelah login PIN Owner.");
      const displayName=String(fd.get("displayName")||"").trim();
      const storeName=String(fd.get("storeName")||"").trim();
      const branchName=String(fd.get("branchName")||"").trim();
      if(displayName.length<2||storeName.length<2||branchName.length<2) throw new Error("Isi nama pemilik, nama toko, dan nama cabang (minimal 2 karakter).");
      sessionStorage.setItem("kca_pending_store",JSON.stringify({displayName,storeName,branchName}));
      const {data,error}=await client.auth.signUp({
        email,password,
        options:{
          data:{display_name:displayName},
          emailRedirectTo: window.location.origin + window.location.pathname + "?cloud-auth-preview"
        }
      });
      if(error) throw error;
      if(!data.session){
        say("Akun dibuat. Buka tautan konfirmasi yang dikirim ke email untuk mengaktifkan akun dan menyelesaikan pendaftaran toko.","success");
      }else{
        const {data:store,error:storeError}=await client.rpc("create_my_store",{
          p_store_name:storeName,p_branch_name:branchName,p_display_name:displayName
        });
        if(storeError) throw storeError;
        await linkLocalCloudIdentity();
        sessionStorage.removeItem("kca_pending_store");
        say("Akun, toko, dan pengaitan Owner berhasil.", "success");
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