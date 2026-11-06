(() => {
  "use strict";
  const state = { tab: "bahan", ctx: null, items: [], recipes: [], recipeItems: [], products: [], menuItems: [], batches: [], movements: [], loading: false };
  const $ = (s, root=document) => root.querySelector(s);
  const esc = v => String(v ?? "").replace(/[&<>"']/g, c => ({ "&":"&amp;","<":"&lt;",">":"&gt;",'"':"&quot;","'":"&#39;" }[c]));
  const num = v => Number(v || 0);
  const fmt = v => new Intl.NumberFormat("id-ID", { maximumFractionDigits: 3 }).format(num(v));
  const money = v => new Intl.NumberFormat("id-ID", { style:"currency", currency:"IDR", maximumFractionDigits:0 }).format(num(v));
  const toast = (message, bad=false) => {
    const el = $("#production-message");
    if (el) { el.textContent = message; el.className = "production-message " + (bad ? "is-error" : "is-ok"); }
  };
  function activeBranchId() {
    try { return window.KASIRKU_BRANCH_STORAGE?.activeBranch?.() || null; } catch { return null; }
  }
  async function getContext() {
    if (!window.KASIRKU_CLOUD?.refresh || !window.KASIRKU_CLOUD?.getScopedClient) throw new Error("Modul Cloud belum siap.");
    const cloud = await window.KASIRKU_CLOUD.refresh();
    if (!cloud.authenticated || !cloud.linked || !cloud.store?.id) throw new Error(cloud.error || "Hubungkan akun Cloud terlebih dahulu melalui Pengaturan Cloud.");
    const branches = Array.isArray(cloud.branches) ? cloud.branches : [];
    if (!branches.length) throw new Error("Akun ini belum memiliki cabang yang diizinkan.");
    const saved = sessionStorage.getItem("kasirku_production_branch");
    const localId = activeBranchId();
    const mappings = window.KASIRKU_CLOUD.getLocalBranchMappings?.()?.mappings || [];
    const mapped = mappings.find(x => String(x.localBranchId) === String(localId))?.cloudBranchId;
    const branch = branches.find(x => x.id === saved) || branches.find(x => x.id === mapped) || branches[0];
    sessionStorage.setItem("kasirku_production_branch", branch.id);
    return { client: await window.KASIRKU_CLOUD.getScopedClient(), storeId: cloud.store.id, storeName: cloud.store.name, role: String(cloud.store.role || "").toLowerCase(), userId: cloud.user?.id, branches, branch };
  }
  async function loadData() {
    if (state.loading) return;
    state.loading = true;
    try {
      state.ctx = await getContext();
      const c = state.ctx.client, store = state.ctx.storeId, branch = state.ctx.branch.id;
      const [items, recipes, recipeItems, products, menuItems, batches, movements] = await Promise.all([
        c.from("inventory_items").select("*").eq("store_id", store).eq("branch_id", branch).eq("is_active", true).order("name"),
        c.from("production_recipes").select("*").eq("store_id", store).eq("branch_id", branch).eq("is_active", true).order("name"),
        c.from("production_recipe_items").select("*").eq("store_id", store).eq("branch_id", branch),
        c.from("products").select("id,name,price").eq("store_id", store).eq("branch_id", branch).eq("is_active", true).order("name"),
        c.from("menu_recipe_items").select("*").eq("store_id", store).eq("branch_id", branch),
        c.from("production_batches").select("*").eq("store_id", store).eq("branch_id", branch).order("completed_at", {ascending:false}).limit(20),
        c.from("inventory_movements").select("*").eq("store_id", store).eq("branch_id", branch).order("created_at", {ascending:false}).limit(30)
      ]);
      for (const result of [items,recipes,recipeItems,products,menuItems,batches,movements]) if (result.error) throw result.error;
      state.items=items.data||[]; state.recipes=recipes.data||[]; state.recipeItems=recipeItems.data||[];
      state.products=products.data||[]; state.menuItems=menuItems.data||[]; state.batches=batches.data||[]; state.movements=movements.data||[];
      renderContent();
    } catch (error) {
      const el=$("#production-content");
      if (el) el.innerHTML='<div class="production-empty production-error">'+esc(error?.message || "Gagal memuat data produksi.")+'</div>';
    } finally { state.loading=false; }
  }
  function itemOptions(filter) {
    return state.items.filter(x => !filter || x.item_type === filter).map(x => '<option value="'+esc(x.id)+'">'+esc(x.name)+' · '+esc(x.base_unit)+'</option>').join("");
  }
  function branchOptions() {
    return state.ctx.branches.map(b=>'<option value="'+esc(b.id)+'" '+(b.id===state.ctx.branch.id?'selected':'')+'>'+esc(b.name)+'</option>').join("");
  }
  function shell() {
    return '<div class="production-page"><div class="production-heading"><div><span class="production-kicker">OPERASIONAL DAPUR</span><h2>Produksi &amp; Resep</h2><p>Kelola bahan mentah, hasil olahan, resep, dan proses produksi.</p></div><span class="production-branch-label">Cabang aktif</span><select id="production-branch" class="field" aria-label="Cabang aktif"><option>Memuat cabang…</option></select></div><div id="production-message" class="production-message" role="status"></div><div class="production-tabs">'+[
      ["bahan","Bahan baku"],["resep","Resep produksi"],["produksi","Catat produksi"],["menu","Resep menu"],["riwayat","Riwayat"]
    ].map(([id,label])=>'<button type="button" class="production-tab '+(state.tab===id?'active':'')+'" data-production-tab="'+id+'">'+label+'</button>').join("")+'</div><div id="production-content"><div class="production-empty">Memuat data cabang…</div></div></div>';
  }
  function headerStats() {
    const raw=state.items.filter(x=>x.item_type==="raw").length, prep=state.items.filter(x=>x.item_type==="prepared").length;
    const low=state.items.filter(x=>num(x.on_hand)<=num(x.minimum_stock)).length;
    return '<div class="production-stats"><div><small>Bahan mentah</small><strong>'+raw+'</strong></div><div><small>Bahan siap pakai</small><strong>'+prep+'</strong></div><div><small>Stok perlu dicek</small><strong>'+low+'</strong></div></div>';
  }
  function bahanTab() {
    const can = ["owner","admin","supervisor"].includes(state.ctx.role);
    return headerStats()+(can?'<section class="production-card"><h3>Tambah bahan</h3><form id="production-item-form" class="production-form-grid"><label>Nama bahan<input name="name" class="field" required maxlength="120" placeholder="Contoh: Ayam mentah"></label><label>Jenis<select name="item_type" class="field"><option value="raw">Bahan mentah</option><option value="prepared">Bahan siap pakai</option></select></label><label>Satuan dasar<input name="base_unit" class="field" required maxlength="24" placeholder="kg, gram, liter, ekor, porsi"></label><label>Stok minimum<input name="minimum_stock" class="field" type="number" min="0" step="0.001" value="0"></label><button class="btn production-primary" type="submit">Simpan bahan</button></form></section>':'')+
      '<section class="production-card"><h3>Daftar bahan <span>'+state.items.length+'</span></h3>'+(state.items.length?'<div class="production-list">'+state.items.map(item=>'<article class="production-item"><div class="production-item-main"><b>'+esc(item.name)+'</b><small>'+ (item.item_type==="raw"?"Bahan mentah":"Siap pakai") +' · '+esc(item.base_unit)+'</small></div><div class="production-item-stock"><strong>'+fmt(item.on_hand)+'</strong><small>Min. '+fmt(item.minimum_stock)+'</small></div>'+(can?'<button class="btn btn-light" data-stock-item="'+esc(item.id)+'">Atur stok</button>':'')+'</article>').join("")+'</div>':'<div class="production-empty">Belum ada bahan. Tambahkan bahan mentah dan bahan siap pakai terlebih dahulu.</div>')+'</section>'+
      (can?'<section class="production-card"><h3>Tambah / kurangi stok</h3><form id="production-stock-form" class="production-form-grid"><label>Bahan<select name="item_id" class="field" required><option value="">Pilih bahan</option>'+itemOptions("")+'</select></label><label>Jenis perubahan<select name="movement_type" class="field"><option value="purchase">Stok masuk / pembelian</option><option value="waste">Bahan terbuang</option><option value="adjustment">Penyesuaian stok</option></select></label><label>Jumlah<input name="quantity" class="field" type="number" step="0.001" min="0.001" required placeholder="Jumlah"></label><label>Biaya per satuan (Rp)<input name="unit_cost" class="field" type="number" min="0" step="1" value="0"></label><label class="production-wide">Catatan<input name="note" class="field" maxlength="250" placeholder="Opsional"></label><button class="btn production-primary" type="submit">Simpan perubahan stok</button></form><p class="production-hint">Untuk bahan terbuang, jumlah akan otomatis dicatat sebagai pengurangan stok.</p></section>':'');
  }
  function recipeTab() {
    const can=["owner","admin","supervisor"].includes(state.ctx.role);
    const prepared=state.items.filter(x=>x.item_type==="prepared");
    return (can?'<section class="production-card"><h3>Buat resep produksi</h3><form id="production-recipe-form" class="production-form-grid"><input type="hidden" name="recipe_id"><label>Nama resep<input name="name" class="field" required maxlength="120" placeholder="Contoh: Ayam marinasi"></label><label>Hasil produksi<select name="output_item_id" class="field" required><option value="">Pilih bahan siap pakai</option>'+itemOptions("prepared")+'</select></label><label>Hasil standar<input name="yield_quantity" class="field" type="number" min="0.001" step="0.001" required value="10"></label><label>Satuan hasil<small class="production-hint">Mengikuti satuan bahan hasil produksi.</small></label><div class="production-wide production-ingredient-list" id="production-ingredients"><div class="production-ingredient-row" data-ingredient-row><select name="ingredient_id" class="field" required><option value="">Pilih bahan</option>'+itemOptions("")+'</select><input name="ingredient_quantity" class="field" type="number" min="0.001" step="0.001" required placeholder="Takaran"><button type="button" class="production-remove-row" aria-label="Hapus bahan" disabled>×</button></div></div><button type="button" class="btn btn-light" data-add-ingredient="production-ingredients">＋ Tambah bahan</button><label class="production-wide">Catatan<input name="notes" class="field" maxlength="250" placeholder="Opsional"></label><button class="btn production-primary" type="submit">Simpan resep</button></form></section>':'')+
      '<section class="production-card"><h3>Resep tersimpan</h3>'+(state.recipes.length?'<div class="production-list">'+state.recipes.map(r=>{const out=state.items.find(x=>x.id===r.output_item_id);const ingredients=state.recipeItems.filter(x=>x.recipe_id===r.id);return '<article class="production-recipe-card"><div><b>'+esc(r.name)+'</b><small>Hasil: '+esc(out?.name||"Bahan tidak ditemukan")+' · '+fmt(r.yield_quantity)+' '+esc(out?.base_unit||"")+'</small><p>'+ingredients.map(x=>{const item=state.items.find(i=>i.id===x.inventory_item_id);return esc(item?.name||"Bahan")+' '+fmt(x.quantity)}).join(" · ")+'</p></div></article>'}).join("")+'</div>':'<div class="production-empty">Belum ada resep produksi.</div>')+'</section>';
  }
  function productionTab() {
    const can=["owner","admin","supervisor"].includes(state.ctx.role);
    if(!can) return '<div class="production-empty">Akses pencatatan produksi hanya tersedia untuk Owner, Admin, dan Supervisor.</div>';
    return '<section class="production-card"><h3>Catat hasil produksi</h3><p class="production-hint">Pilih resep dan jumlah hasil aktual. Sistem menghitung kebutuhan bahan berdasarkan takaran resep, lalu mengurangi bahan mentah dan menambah stok hasil olahan dalam satu transaksi.</p><form id="production-batch-form" class="production-form-grid"><label>Resep produksi<select name="recipe_id" class="field" required><option value="">Pilih resep</option>'+state.recipes.map(r=>'<option value="'+esc(r.id)+'">'+esc(r.name)+'</option>').join("")+'</select></label><label>Jumlah hasil aktual<input name="output_quantity" class="field" type="number" min="0.001" step="0.001" required placeholder="Contoh: 20"></label><label class="production-wide">Catatan produksi<input name="notes" class="field" maxlength="250" placeholder="Contoh: Produksi pagi"></label><button class="btn production-primary" type="submit" '+(!state.recipes.length?'disabled':'')+'>Konfirmasi produksi</button></form></section><section class="production-card"><h3>Stok hasil olahan</h3>'+(state.items.filter(x=>x.item_type==="prepared").length?'<div class="production-list">'+state.items.filter(x=>x.item_type==="prepared").map(x=>'<article class="production-item"><div class="production-item-main"><b>'+esc(x.name)+'</b><small>Biaya rata-rata '+money(x.average_unit_cost)+' / '+esc(x.base_unit)+'</small></div><div class="production-item-stock"><strong>'+fmt(x.on_hand)+'</strong><small>'+esc(x.base_unit)+'</small></div></article>').join("")+'</div>':'<div class="production-empty">Belum ada bahan siap pakai.</div>')+'</section>';
  }
  function menuTab() {
    const can=["owner","admin","supervisor"].includes(state.ctx.role);
    return (can?'<section class="production-card"><h3>Hubungkan resep ke menu jualan</h3><form id="menu-recipe-form" class="production-form-grid"><label class="production-wide">Menu<select name="product_id" class="field" required><option value="">Pilih menu</option>'+state.products.map(p=>'<option value="'+esc(p.id)+'">'+esc(p.name)+'</option>').join("")+'</select></label><div class="production-wide production-ingredient-list" id="menu-ingredients"><div class="production-ingredient-row" data-ingredient-row><select name="ingredient_id" class="field" required><option value="">Pilih bahan</option>'+itemOptions("")+'</select><input name="ingredient_quantity" class="field" type="number" min="0.001" step="0.001" required placeholder="Takaran per menu"><button type="button" class="production-remove-row" aria-label="Hapus bahan" disabled>×</button></div></div><button type="button" class="btn btn-light" data-add-ingredient="menu-ingredients">＋ Tambah bahan</button><button class="btn production-primary" type="submit">Simpan resep menu</button></form></section>':'')+
      '<section class="production-card"><h3>Komposisi menu</h3>'+(state.menuItems.length?'<div class="production-list">'+state.menuItems.map(x=>{const p=state.products.find(z=>z.id===x.product_id),i=state.items.find(z=>z.id===x.inventory_item_id);return '<article class="production-item"><div class="production-item-main"><b>'+esc(p?.name||"Menu")+'</b><small>'+esc(i?.name||"Bahan")+' · '+fmt(x.quantity_per_menu)+' '+esc(i?.base_unit||"")+' per menu</small></div></article>'}).join("")+'</div>':'<div class="production-empty">Belum ada resep menu yang terhubung.</div>')+'</section>';
  }
  function historyTab() {
    return '<section class="production-card"><h3>Riwayat produksi</h3>'+(state.batches.length?'<div class="production-list">'+state.batches.map(b=>{const r=state.recipes.find(x=>x.id===b.recipe_id),o=state.items.find(x=>x.id===b.output_item_id);return '<article class="production-item"><div class="production-item-main"><b>'+esc(r?.name||"Produksi")+'</b><small>'+new Date(b.completed_at).toLocaleString("id-ID")+'</small></div><div class="production-item-stock"><strong>+'+fmt(b.actual_output_quantity)+'</strong><small>'+esc(o?.base_unit||"")+'</small></div></article>'}).join("")+'</div>':'<div class="production-empty">Belum ada aktivitas produksi.</div>')+'</section><section class="production-card"><h3>Pergerakan stok terbaru</h3>'+(state.movements.length?'<div class="production-list">'+state.movements.map(m=>{const i=state.items.find(x=>x.id===m.inventory_item_id);return '<article class="production-item"><div class="production-item-main"><b>'+esc(i?.name||"Bahan")+'</b><small>'+esc(m.movement_type.replaceAll("_"," "))+' · '+new Date(m.created_at).toLocaleString("id-ID")+'</small></div><div class="production-item-stock"><strong class="'+(num(m.quantity_delta)<0?'is-negative':'is-positive')+'">'+(num(m.quantity_delta)>0?"+":"")+fmt(m.quantity_delta)+'</strong></div></article>'}).join("")+'</div>':'<div class="production-empty">Belum ada pergerakan stok.</div>')+'</section>';
  }
  function renderContent() {
    const el=$("#production-content"); if(!el || !state.ctx) return;
    const tab=state.tab;
    el.innerHTML=tab==="bahan"?bahanTab():tab==="resep"?recipeTab():tab==="produksi"?productionTab():tab==="menu"?menuTab():historyTab();
    const branch=$("#production-branch"); if(branch && state.ctx) branch.innerHTML=branchOptions();
  }
  function render() {
    const html=shell();
    setTimeout(loadData,0);
    return html;
  }
  async function refresh() { state.loading=false; await loadData(); }
  document.addEventListener("click", async e => {
    const tab=e.target.closest("[data-production-tab]");
    if(tab){state.tab=tab.dataset.productionTab;document.querySelectorAll("[data-production-tab]").forEach(x=>x.classList.toggle("active",x===tab));renderContent();return;}
    const add=e.target.closest("[data-add-ingredient]");
    if(add){const list=$("#"+add.dataset.addIngredient);if(!list)return;const row=list.querySelector("[data-ingredient-row]");const clone=row.cloneNode(true);clone.querySelectorAll("input").forEach(x=>x.value="");clone.querySelectorAll("select").forEach(x=>x.selectedIndex=0);clone.querySelector("button").disabled=false;list.appendChild(clone);return;}
    const remove=e.target.closest(".production-remove-row");
    if(remove){const list=remove.closest(".production-ingredient-list");if(list.querySelectorAll("[data-ingredient-row]").length>1)remove.closest("[data-ingredient-row]").remove();return;}
    const stock=e.target.closest("[data-stock-item]");
    if(stock){state.tab="bahan";renderContent();const form=$("#production-stock-form");if(form)form.elements.item_id.value=stock.dataset.stockItem;form?.scrollIntoView({behavior:"smooth",block:"center"});}
  });
  document.addEventListener("change", e => {
    if(e.target.id==="production-branch" && state.ctx?.branches.some(x=>x.id===e.target.value)){sessionStorage.setItem("kasirku_production_branch",e.target.value);state.loading=false;loadData();}
  });
  document.addEventListener("submit", async e => {
    const form=e.target;
    if(!form.matches("#production-item-form,#production-stock-form,#production-recipe-form,#production-batch-form,#menu-recipe-form"))return;
    e.preventDefault();
    if(!state.ctx){toast("Konteks toko belum siap.",true);return;}
    const c=state.ctx.client, store=state.ctx.storeId, branch=state.ctx.branch.id, f=form.elements;
    const button=form.querySelector('button[type="submit"]');if(button){button.disabled=true;button.dataset.oldText=button.textContent;button.textContent="Menyimpan…";}
    try {
      if(form.id==="production-item-form"){
        const {error}=await c.from("inventory_items").insert({store_id:store,branch_id:branch,name:f.name.value.trim(),item_type:f.item_type.value,base_unit:f.base_unit.value.trim(),minimum_stock:num(f.minimum_stock.value),created_by:state.ctx.userId});
        if(error)throw error;
        toast("Bahan berhasil ditambahkan.");
      } else if(form.id==="production-stock-form"){
        let qty=num(f.quantity.value);const type=f.movement_type.value;if(type==="waste")qty=-qty;
        const {error}=await c.rpc("adjust_inventory_stock",{p_store_id:store,p_branch_id:branch,p_item_id:f.item_id.value,p_quantity_delta:qty,p_movement_type:type,p_unit_cost:num(f.unit_cost.value),p_note:f.note.value.trim(),p_idempotency_key:crypto.randomUUID()});
        if(error)throw error;
        toast("Perubahan stok berhasil dicatat.");
      } else if(form.id==="production-recipe-form"){
        const ingredients=[...form.querySelectorAll("[data-ingredient-row]")].map(row=>({inventory_item_id:row.querySelector('[name="ingredient_id"]').value,quantity:num(row.querySelector('[name="ingredient_quantity"]').value)}));
        const {error}=await c.rpc("save_production_recipe",{p_store_id:store,p_branch_id:branch,p_recipe_id:f.recipe_id.value||null,p_name:f.name.value.trim(),p_output_item_id:f.output_item_id.value,p_yield_quantity:num(f.yield_quantity.value),p_notes:f.notes.value.trim(),p_items:ingredients});
        if(error)throw error;
        toast("Resep produksi berhasil disimpan.");
      } else if(form.id==="production-batch-form"){
        const {error}=await c.rpc("complete_production_batch",{p_store_id:store,p_branch_id:branch,p_recipe_id:f.recipe_id.value,p_output_quantity:num(f.output_quantity.value),p_actual_inputs:[],p_notes:f.notes.value.trim()});
        if(error)throw error;
        toast("Produksi berhasil dicatat. Stok bahan dan hasil olahan sudah diperbarui.");
      } else if(form.id==="menu-recipe-form"){
        const ingredients=[...form.querySelectorAll("[data-ingredient-row]")].map(row=>({inventory_item_id:row.querySelector('[name="ingredient_id"]').value,quantity_per_menu:num(row.querySelector('[name="ingredient_quantity"]').value)}));
        const {error}=await c.rpc("replace_menu_recipe",{p_store_id:store,p_branch_id:branch,p_product_id:f.product_id.value,p_items:ingredients});
        if(error)throw error;
        toast("Resep menu berhasil disimpan.");
      }
      form.reset();await loadData();
    } catch(error) { toast(error?.message||"Gagal menyimpan data.",true); }
    finally {if(button){button.disabled=false;button.textContent=button.dataset.oldText||"Simpan";}}
  });

  const OUTBOX_KEY="kasirku_web_inventory_outbox_v1";
  let flushingOutbox=false;
  function readOutbox(){try{const value=JSON.parse(localStorage.getItem(OUTBOX_KEY)||"[]");return Array.isArray(value)?value:[]}catch{return []}}
  function writeOutbox(rows){try{localStorage.setItem(OUTBOX_KEY,JSON.stringify(rows.slice(-500)));return true}catch{return false}}
  async function sendWebOrder(entry){
    const cloud=await window.KASIRKU_CLOUD.refresh();
    if(!cloud.authenticated||!cloud.linked||!cloud.store?.id)throw new Error(cloud.error||"Akun Cloud belum terhubung.");
    const mappings=window.KASIRKU_CLOUD.getLocalBranchMappings?.()?.mappings||[];
    const mapping=mappings.find(x=>String(x.localBranchId)===String(entry.localBranchId));
    const branch=(mapping&&cloud.branches.find(x=>x.id===mapping.cloudBranchId))||(cloud.branches.length===1?cloud.branches[0]:null);
    if(!branch)throw new Error("Cabang Web belum dipasangkan dengan cabang Cloud. Cocokkan cabang terlebih dahulu.");
    const client=await window.KASIRKU_CLOUD.getScopedClient();
    const {error}=await client.rpc("consume_web_order_inventory",{
      p_store_id:cloud.store.id,p_branch_id:branch.id,p_order_local_id:String(entry.orderId),p_items:entry.items
    });
    if(error)throw error;
    return true;
  }
  async function syncPaidOrder(order){
    if(!order?.id||!Array.isArray(order.items)||!order.items.length)return {queued:false,skipped:true};
    const localBranchId=window.KASIRKU_BRANCH_STORAGE?.activeBranch?.()||"main";
    const entry={orderId:String(order.id),localBranchId,items:order.items.map(x=>({product_local_id:String(x.id??""),quantity:num(x.qty||1)})).filter(x=>x.product_local_id&&x.quantity>0)};
    if(!entry.items.length)return {queued:false,skipped:true};
    try{await sendWebOrder(entry);return {queued:false}}
    catch(error){
      const queue=readOutbox();
      if(!queue.some(x=>x.orderId===entry.orderId&&x.localBranchId===entry.localBranchId)){queue.push({...entry,lastError:String(error?.message||error),queuedAt:new Date().toISOString()});writeOutbox(queue)}
      return {queued:true,error:String(error?.message||error)};
    }
  }
  async function flushOutbox(){
    if(flushingOutbox||!navigator.onLine)return;
    const queue=readOutbox();if(!queue.length)return;
    flushingOutbox=true;
    try{
      const remaining=[];
      for(const entry of queue){
        try{await sendWebOrder(entry)}
        catch(error){remaining.push({...entry,lastError:String(error?.message||error)})}
      }
      writeOutbox(remaining);
    }finally{flushingOutbox=false}
  }
  window.addEventListener("online",()=>{void flushOutbox()});
  window.addEventListener("kasirku:cloud-context",()=>{if(!flushingOutbox)void flushOutbox()});
  setTimeout(()=>{void flushOutbox()},1200);
\n  window.KASIRKU_PRODUCTION={render,refresh,syncPaidOrder,flushOutbox};
})();