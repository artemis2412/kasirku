(() => {
"use strict";
const KEYS = new Set(["kasirku_menus","kasirku_orders","kasirku_tables","kasirku_categories","kasirku_cash_audit","kasirku_cash_entries","kasirku_cash_closures","kasirku_store_name","kasirku_store_logo"]);
const nativeGet=Storage.prototype.getItem,nativeSet=Storage.prototype.setItem,nativeRemove=Storage.prototype.removeItem;
const activeId=()=>{try{const id=nativeGet.call(localStorage,"ksecurity_active_branch");return /^[a-zA-Z0-9_-]{1,80}$/.test(id||"")?id:"main"}catch{return "main"}};
const scopedKey=key=>{const k=String(key);return KEYS.has(k)?"kasirku_branch_"+activeId()+"__"+k:k};
try{const marker="ksecurity_branch_storage_v1";if(nativeGet.call(localStorage,marker)!=="done"){const id=activeId();for(const key of KEYS){const scoped="kasirku_branch_"+id+"__"+key;if(nativeGet.call(localStorage,scoped)===null){const legacy=nativeGet.call(localStorage,key);if(legacy!==null)nativeSet.call(localStorage,scoped,legacy)}}nativeSet.call(localStorage,marker,"done")}}catch(error){console.error("Migrasi penyimpanan cabang ditunda:",error)}
Storage.prototype.getItem=function(key){return nativeGet.call(this,scopedKey(key))};
Storage.prototype.setItem=function(key,value){return nativeSet.call(this,scopedKey(key),value)};
Storage.prototype.removeItem=function(key){return nativeRemove.call(this,scopedKey(key))};
window.KASIRKU_BRANCH_STORAGE={version:1,activeBranch:activeId,keyFor:scopedKey,isScopedKey:key=>String(key).startsWith("kasirku_branch_")};
})();