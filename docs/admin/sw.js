// Пульт как приложение (2.13): открывается и без интернета — показывается последняя загруженная версия.
// Сначала всегда сеть, кэш — только запасной. Запросы к GitHub API не кэшируются (там токен и свежие данные).
const CACHE = 'apex-pult-v1'
const SHELL = ['./', 'icon.svg', 'icon-192.png', 'manifest.webmanifest']
self.addEventListener('install', e => { e.waitUntil(caches.open(CACHE).then(c => c.addAll(SHELL)).catch(() => {})); self.skipWaiting() })
self.addEventListener('activate', e => { e.waitUntil(caches.keys().then(k => Promise.all(k.filter(x => x !== CACHE).map(x => caches.delete(x)))).then(() => self.clients.claim())) })
self.addEventListener('fetch', e => {
  const r = e.request, u = new URL(r.url)
  if (r.method !== 'GET' || u.origin !== location.origin || !u.pathname.includes('/admin/')) return
  e.respondWith(fetch(r).then(res => {
    if (res.ok) { const copy = res.clone(); caches.open(CACHE).then(c => c.put(r.mode === 'navigate' ? './' : r, copy)) }
    return res
  }).catch(() => caches.match(r.mode === 'navigate' ? './' : r).then(x => x || caches.match('./'))))
})
