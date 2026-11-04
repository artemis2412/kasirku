(() => {
  "use strict";
  // Read-only bridge: exposes verified Supabase identity and allowed branches.
  // It does not change PIN login, local data, or active branch selection.
  const cfg = window.KASIRKU_SUPABASE_CONFIG;
  const state = { ready:false, authenticated:false, user:null, memberships:[], store:null, branches:[], error:null };
  window.KASIRKU_CLOUD = {
    getState: () => ({...state, memberships:[...state.memberships], branches:[...state.branches]}),
    refresh: refresh,
    loadCatalog: loadCatalog
  };
  function publish(){
    window.dispatchEvent(new CustomEvent("kasirku:cloud-context", {detail:window.KASIRKU_CLOUD.getState()}));
  }
  async function loadSdk(){
    if(window.supabase?.createClient)return;
    await new Promise((resolve,reject)=>{
      const s=document.createElement("script");
      s.src="https://cdn.jsdelivr.net/npm/@supabase/supabase-js@2.57.4/dist/umd/supabase.min.js";
      s.onload=resolve;s.onerror=()=>reject(new Error("SDK Supabase gagal dimuat"));
      document.head.appendChild(s);
    });
  }
  let client=null, running=false;
  async function loadCatalog(branchId){
    if(!client)throw new Error("Koneksi cloud belum siap");
    const branch=state.branches.find(b=>b.id===branchId);
    if(!state.authenticated||!state.store||!branch)throw new Error("Cabang cloud tidak tersedia atau tidak diizinkan");
    const [categoriesResult,productsResult]=await Promise.all([
      client.from("menu_categories").select("id,name,branch_id").eq("store_id",state.store.id).eq("branch_id",branch.id).order("name"),
      client.from("products").select("id,name,price,stock_unlimited,stock_quantity,is_active,category_id,branch_id").eq("store_id",state.store.id).eq("branch_id",branch.id).eq("is_active",true).order("name")
    ]);
    if(categoriesResult.error)throw categoriesResult.error;
    if(productsResult.error)throw productsResult.error;
    return {branch:{id:branch.id,name:branch.name},categories:categoriesResult.data||[],products:productsResult.data||[]};
  }
  async function refresh(){
    if(running)return window.KASIRKU_CLOUD.getState();
    running=true;
    try{
      if(!cfg?.url||!cfg?.publishableKey)throw new Error("Konfigurasi cloud belum tersedia");
      await loadSdk();
      if(!client)client=window.supabase.createClient(cfg.url,cfg.publishableKey,{
        auth:{persistSession:true,autoRefreshToken:true,detectSessionInUrl:true}
      });
      const {data:{session},error:sessionError}=await client.auth.getSession();
      if(sessionError)throw sessionError;
      state.authenticated=!!session?.user;
      state.user=session?.user?{id:session.user.id,email:session.user.email}:null;
      state.memberships=[];state.store=null;state.branches=[];state.error=null;
      if(session?.user){
        const {data:memberships,error}=await client.from("store_memberships")
          .select("store_id,role,is_active").eq("user_id",session.user.id).eq("is_active",true);
        if(error)throw error;
        state.memberships=memberships||[];
        if(state.memberships.length){
          const m=state.memberships[0];
          state.store={id:m.store_id,role:m.role};
          const {data:branches,error:branchError}=await client.from("branches")
            .select("id,name,is_active").eq("store_id",m.store_id).eq("is_active",true).order("created_at");
          if(branchError)throw branchError;
          let allowed=branches||[];
          if(!["owner","admin"].includes(String(m.role).toLowerCase())){
            const {data:assignments,error:assignmentError}=await client.from("branch_memberships")
              .select("branch_id").eq("store_id",m.store_id).eq("user_id",session.user.id);
            if(assignmentError)throw assignmentError;
            const ids=new Set((assignments||[]).map(x=>x.branch_id));
            allowed=allowed.filter(b=>ids.has(b.id));
          }
          state.branches=allowed;
        }
      }
    }catch(error){state.error=error?.message||"Gagal memuat konteks cloud";}
    finally{state.ready=true;running=false;publish();}
    return window.KASIRKU_CLOUD.getState();
  }
  loadSdk().then(()=>{
    client=window.supabase.createClient(cfg.url,cfg.publishableKey,{
      auth:{persistSession:true,autoRefreshToken:true,detectSessionInUrl:true}
    });
    client.auth.onAuthStateChange(()=>{setTimeout(refresh,0);});
    refresh();
  }).catch(error=>{state.ready=true;state.error=error.message;publish();});
})();