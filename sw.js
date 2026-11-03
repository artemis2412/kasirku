const CACHE_VERSION="kasirku-v4-mobile-balance-1";
const APP_FILES=["./","./index.html","./style.css","./app.js","./manifest.json"];
self.addEventListener("install",event=>event.waitUntil(caches.open(CACHE_VERSION).then(cache=>cache.addAll(APP_FILES)).then(()=>self.skipWaiting())));
self.addEventListener("activate",event=>event.waitUntil(caches.keys().then(keys=>Promise.all(keys.filter(k=>k.startsWith("kasirku-")&&k!==CACHE_VERSION).map(k=>caches.delete(k)))).then(()=>self.clients.claim())));
self.addEventListener("fetch",event=>{const req=event.request;if(req.method!=="GET")return;const url=new URL(req.url);if(url.origin!==self.location.origin)return;event.respondWith(fetch(req).then(res=>{if(res.ok){const copy=res.clone();caches.open(CACHE_VERSION).then(c=>c.put(req,copy))}return res}).catch(()=>caches.match(req).then(hit=>hit||caches.match("./"))) )});
