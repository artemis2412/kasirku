(() => {
"use strict";
const MARKER="kasirku_cloud_session_active";
const PREFIX="kasirku_supabase_cloud_auth_v1:";
let client=null;
function loadSdk(){
 return new Promise((resolve,reject)=>{
  if(window.supabase?.createClient)return resolve();
  const script=document.createElement("script");
  script.src="https://cdn.jsdelivr.net/npm/@supabase/supabase-js@2.57.4/dist/umd/supabase.min.js";
  script.onload=resolve;
  script.onerror=()=>reject(new Error("Pustaka Cloud gagal dimuat."));
  document.head.appendChild(script);
 });
}
async function getClient(){
 await loadSdk();
 const cfg=window.KASIRKU_SUPABASE_CONFIG||window.KASIRKU_SUPABASE;
 if(!cfg?.url||!(cfg.publishableKey||cfg.anonKey))throw new Error("Konfigurasi Cloud tidak ditemukan.");
 const storage={getItem:async k=>localStorage.getItem(PREFIX+k),setItem:async(k,v)=>localStorage.setItem(PREFIX+k,v),removeItem:async k=>localStorage.removeItem(PREFIX+k)};
 return window.supabase.createClient(cfg.url,cfg.publishableKey||cfg.anonKey,{auth:{storage,persistSession:true,autoRefreshToken:true,detectSessionInUrl:false}});
}
async function validate(c){
 const {data:{user},error:userError}=await c.auth.getUser();
 if(userError)throw userError;
 if(!user)throw new Error("Sesi Cloud sudah berakhir.");
 const {data:memberships,error}=await c.from("store_memberships").select("store_id,role,is_active").eq("user_id",user.id).eq("is_active",true);
 if(error)throw error;
 if(!memberships?.length)throw new Error("Keanggotaan toko aktif tidak ditemukan.");
 if(memberships.length!==1)throw new Error("Akun memiliki lebih dari satu toko aktif; pemilihan toko diperlukan.");
 const membership=memberships[0],role=String(membership.role||"").toLowerCase();
 if(!["owner","admin","cashier"].includes(role))throw new Error("Peran Cloud tidak valid.");
 const {data:store,error:storeError}=await c.from("stores").select("id,name").eq("id",membership.store_id).maybeSingle();
 if(storeError)throw storeError;
 if(!store)throw new Error("Toko tidak ditemukan atau akses ditolak.");
 const {data:branches,error:branchError}=await c.from("branches").select("id,name,is_active").eq("store_id",membership.store_id).eq("is_active",true);
 if(branchError)throw branchError;
 let allowed=branches||[];
 if(role!=="owner"){
  const {data:assignments,error:assignmentError}=await c.from("branch_memberships").select("branch_id").eq("store_id",membership.store_id).eq("user_id",user.id);
  if(assignmentError)throw assignmentError;
  const ids=new Set((assignments||[]).map(x=>x.branch_id));
  allowed=allowed.filter(x=>ids.has(x.id));
 }
 if(!allowed.length)throw new Error("Akun belum memiliki cabang aktif yang diizinkan.");
 return {id:user.id,email:user.email||"",name:String(user.user_metadata?.display_name||user.user_metadata?.full_name||user.email?.split("@")[0]||"Pengguna"),role,branchIds:allowed.map(x=>x.id),store,branches:allowed};
}
async function signOut(){
 try{if(!client)client=await getClient();await client.auth.signOut()}catch(e){console.warn("Gagal mengakhiri sesi Cloud:",e)}
 localStorage.removeItem(MARKER);
}
window.KASIRKU_CLOUD_SESSION={signOut};
async function restore(){
 if(localStorage.getItem(MARKER)!=="1")return;
 try{
  client=await getClient();
  const identity=await validate(client);
  if(!window.KASIRKU_AUTH?.startCloudSession)throw new Error("Modul autentikasi belum siap.");
  window.KASIRKU_AUTH.startCloudSession(identity);
 }catch(error){
  console.warn("Pemulihan sesi Cloud gagal:",error);
  await signOut();
  window.KASIRKU_AUTH?.showWelcome?.();
 }
}
if(document.readyState==="loading")document.addEventListener("DOMContentLoaded",restore,{once:true});else restore();
})();