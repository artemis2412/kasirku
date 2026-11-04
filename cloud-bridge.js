(() => {
  "use strict";
  // Read-only bridge: exposes verified Supabase identity and allowed branches.
  // It does not change PIN login, local data, or active branch selection.
  const cfg = window.KASIRKU_SUPABASE_CONFIG;
  const state = { ready:false, authenticated:false, user:null, memberships:[], store:null, branches:[], error:null };
  window.KASIRKU_CLOUD = {
    getState: () => ({...state, memberships:[...state.memberships], branches:[...state.branches]}),
    refresh: refresh,
    loadCatalog: loadCatalog,
    syncCatalog: syncCatalog,
    inviteStaff: inviteStaff,
    getLocalBranchMappings: getLocalBranchMappings
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
  const LOCAL_BRANCH_MAP_KEY = "kasirku_cloud_branch_map_v1";
  function readLocalBranches(){
    try{
      const rows=JSON.parse(localStorage.getItem("ksecurity_branches")||"[]");
      return Array.isArray(rows)?rows.filter(x=>x&&x.id&&x.name):[];
    }catch{return []}
  }
  function getLocalBranchMappings(){
    try{
      const saved=JSON.parse(localStorage.getItem(LOCAL_BRANCH_MAP_KEY)||"{}");
      return {storeId:state.store?.id||saved.storeId||null,mappings:Array.isArray(saved.mappings)?saved.mappings:[],localBranches:readLocalBranches().map(b=>({id:b.id,name:b.name,active:b.active!==false}))};
    }catch{return {storeId:state.store?.id||null,mappings:[],localBranches:readLocalBranches()}}
  }
  function saveApprovedInitialMapping(){
    if(!state.store?.id||!state.branches.length)return;
    const locals=readLocalBranches(),cloud=state.branches;
    let saved={storeId:state.store.id,mappings:[]};
    try{saved=JSON.parse(localStorage.getItem(LOCAL_BRANCH_MAP_KEY)||"{}")}catch{}
    if(saved.storeId!==state.store.id)saved={storeId:state.store.id,mappings:[]};
    if(!Array.isArray(saved.mappings))saved.mappings=[];
    const normalize=value=>String(value||"").trim().toLocaleLowerCase("id-ID");
    const pairs=[];
    // Preserve the previously owner-approved legacy mapping.
    const legacyLocal=locals.filter(b=>normalize(b.name)==="roti cafe kuwe in");
    const legacyCloud=cloud.filter(b=>normalize(b.name)==="cabang satu");
    if(legacyLocal.length===1&&legacyCloud.length===1)pairs.push([legacyLocal[0],legacyCloud[0]]);
    // Exact, unique name matches are safe for the newly created branches.
    for(const local of locals){
      const matches=cloud.filter(remote=>normalize(remote.name)===normalize(local.name));
      if(matches.length===1)pairs.push([local,matches[0]]);
    }
    let changed=false;
    for(const [local,remote] of pairs){
      const conflict=saved.mappings.find(m=>
        (m.localBranchId===local.id&&m.cloudBranchId!==remote.id)||
        (m.cloudBranchId===remote.id&&m.localBranchId!==local.id)
      );
      if(conflict)continue;
      const exists=saved.mappings.some(m=>m.localBranchId===local.id&&m.cloudBranchId===remote.id&&m.storeId===state.store.id);
      if(exists)continue;
      saved.mappings.push({
        localBranchId:local.id,localBranchName:local.name,
        cloudBranchId:remote.id,cloudBranchName:remote.name,
        storeId:state.store.id,approvedBy:"owner-confirmed",createdAt:new Date().toISOString()
      });
      changed=true;
    }
    if(changed)localStorage.setItem(LOCAL_BRANCH_MAP_KEY,JSON.stringify(saved));
  }
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
  async function syncCatalog(){
    if(!client||!state.authenticated||!state.store)throw new Error("Login Cloud Owner belum siap");
    const localBranchId=window.KASIRKU_BRANCH_STORAGE?.activeBranch?.()||localStorage.getItem("ksecurity_active_branch")||"main";
    const mapping=(getLocalBranchMappings().mappings||[]).find(x=>String(x.localBranchId)===String(localBranchId)&&x.storeId===state.store.id);
    if(!mapping)throw new Error("Cabang lokal aktif belum dipasangkan ke Cloud.");
    const branch=state.branches.find(x=>x.id===mapping.cloudBranchId);
    if(!branch)throw new Error("Cabang Cloud pasangan tidak tersedia untuk akun ini.");
    let localMenus,localCategories;
    try{localMenus=JSON.parse(localStorage.getItem("kasirku_menus")||"[]");localCategories=JSON.parse(localStorage.getItem("kasirku_categories")||"[]")}catch{throw new Error("Data menu/kategori lokal tidak dapat dibaca.")}
    if(!Array.isArray(localMenus)||!Array.isArray(localCategories))throw new Error("Format data menu lokal tidak valid.");
    const menuMap=new Map();
    for(const item of localMenus){
      if(!item||item.id==null||!String(item.name||"").trim())throw new Error("Ada menu tanpa ID atau nama. Sinkronisasi dihentikan.");
      const id=String(item.id);if(menuMap.has(id))throw new Error("ID menu lokal ganda: "+id);
      const price=Number(item.price),stock=Number(item.stock??0);
      if(!Number.isFinite(price)||price<0||!Number.isFinite(stock)||stock<0)throw new Error("Harga/stok tidak valid: "+item.name);
      menuMap.set(id,item);
    }
    const categoryNames=[...new Set([...localCategories,...localMenus.map(x=>String(x.category||"Lainnya"))].map(x=>String(x||"").trim()).filter(Boolean))];
    const oldCats=await client.from("menu_categories").select("id,local_id,name").eq("store_id",state.store.id).eq("branch_id",branch.id);
    if(oldCats.error)throw oldCats.error;
    const catMap=new Map((oldCats.data||[]).filter(x=>x.local_id).map(x=>[x.local_id,x]));
    const catIds=new Map();let categoriesCreated=0,categoriesUpdated=0;
    for(const name of categoryNames){
      const localId="category:"+name.toLocaleLowerCase("id-ID"),old=catMap.get(localId);
      if(old){catIds.set(name,old.id);if(old.name!==name){const u=await client.from("menu_categories").update({name}).eq("id",old.id);if(u.error)throw u.error;categoriesUpdated++}}
      else{const ins=await client.from("menu_categories").insert({store_id:state.store.id,branch_id:branch.id,local_id:localId,name}).select("id").single();if(ins.error)throw ins.error;catIds.set(name,ins.data.id);categoriesCreated++}
    }
    const oldProducts=await client.from("products").select("id,local_id").eq("store_id",state.store.id).eq("branch_id",branch.id);
    if(oldProducts.error)throw oldProducts.error;
    const productMap=new Map((oldProducts.data||[]).filter(x=>x.local_id).map(x=>[String(x.local_id),x]));
    let productsCreated=0,productsUpdated=0;
    for(const [localId,item] of menuMap){
      const category=String(item.category||"Lainnya").trim()||"Lainnya";
      const payload={store_id:state.store.id,branch_id:branch.id,category_id:catIds.get(category)||null,local_id:localId,name:String(item.name).trim(),price:Number(item.price),stock_unlimited:item.stockUnlimited!==false,stock_quantity:item.stockUnlimited===false?Number(item.stock||0):0,is_active:true,updated_at:new Date().toISOString()};
      const old=productMap.get(localId);
      const result=old?await client.from("products").update(payload).eq("id",old.id):await client.from("products").insert(payload);
      if(result.error)throw result.error;if(old)productsUpdated++;else productsCreated++;
    }
    return {branch:{id:branch.id,name:branch.name},categoriesCreated,categoriesUpdated,productsCreated,productsUpdated,totalLocalProducts:menuMap.size,totalLocalCategories:categoryNames.length};
  }
  async function inviteStaff(email,role,branchId){
    if(!client||!state.authenticated||!state.store)throw new Error("Login Cloud belum siap");
    if(!["owner","admin"].includes(String(state.store.role).toLowerCase()))throw new Error("Hanya Owner/Admin yang dapat mengundang pegawai");
    email=String(email||"").trim().toLowerCase();
    if(!email||!["admin","cashier"].includes(role)||!state.branches.some(b=>b.id===branchId))throw new Error("Data undangan tidak valid");
    const {data:invitation,error:inviteError}=await client.rpc("create_staff_invitation",{
      p_store_id:state.store.id,p_branch_id:branchId,p_email:email,p_role:role
    });
    if(inviteError)throw inviteError;
    const {data:sent,error:sendError}=await client.functions.invoke("kasirku-send-staff-invite",{
      body:{email,store_id:state.store.id,branch_id:branchId}
    });
    if(sendError)throw sendError;
    if(sent?.error)throw new Error(sent.error);
    return {...(invitation||{}),...(sent||{})};
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
      state.user=session?.user?{id:session.user.id,email:session.user.email,name:String(session.user.user_metadata?.display_name||session.user.user_metadata?.full_name||session.user.user_metadata?.name||session.user.email?.split("@")[0]||"Pengguna").trim()}:null;
      state.memberships=[];state.store=null;state.branches=[];state.error=null;
      if(session?.user){
        const {data:memberships,error}=await client.from("store_memberships")
          .select("store_id,role,is_active").eq("user_id",session.user.id).eq("is_active",true);
        if(error)throw error;
        state.memberships=memberships||[];
        if(state.memberships.length){
          const m=state.memberships[0];
          const {data:storeRow,error:storeError}=await client.from("stores")
            .select("id,name").eq("id",m.store_id).maybeSingle();
          if(storeError)throw storeError;
          state.store={id:m.store_id,name:storeRow?.name||"Toko terhubung",role:m.role};
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
          saveApprovedInitialMapping();
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