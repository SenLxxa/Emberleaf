// Lagerfeuer-App offline startbar halten: die App-Dateien kommen aus dem Netz und zur Not aus dem Speicher.
// Anfragen an den Server (/v1/...) gehen immer direkt durch.
const CACHE = "lagerfeuer-app-1";
const SHELL = ["./", "./app.js", "./app.css", "./manifest.webmanifest", "./icon-192.png", "./favicon.png"];

self.addEventListener("install", (event) => {
  event.waitUntil(caches.open(CACHE).then((cache) => cache.addAll(SHELL)).then(() => self.skipWaiting()));
});

self.addEventListener("activate", (event) => {
  event.waitUntil(caches.keys()
    .then((keys) => Promise.all(keys.filter((k) => k !== CACHE).map((k) => caches.delete(k))))
    .then(() => self.clients.claim()));
});

self.addEventListener("fetch", (event) => {
  const url = new URL(event.request.url);
  if (event.request.method !== "GET" || url.origin !== self.location.origin || !url.pathname.startsWith("/app/")) return;
  event.respondWith(fetch(event.request).then((response) => {
    if (response.ok) {
      const copy = response.clone();
      caches.open(CACHE).then((cache) => cache.put(event.request, copy));
    }
    return response;
  }).catch(() => caches.match(event.request).then((hit) => hit || caches.match("./"))));
});
