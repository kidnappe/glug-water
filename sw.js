/* 喝杯水吧 — Service Worker
 * 策略：网络优先，离线回退到缓存
 * 更新：改 SW_VERSION 值触发新版本部署
 */
var CACHE_NAME = 'drink-water-v6';
var SW_VERSION = 'v6-20261102-settings';

/* install：预缓存核心资源，正确使用 waitUntil */
self.addEventListener('install', function(e) {
  e.waitUntil(
    caches.open(CACHE_NAME).then(function(cache) {
      return cache.addAll([
        './',
        './index.html',
        './manifest.json',
        './icons/Icon-192.png',
        './icons/Icon-512.png',
        './icons/Icon-maskable-192.png',
        './icons/Icon-maskable-512.png',
        './icons/emoji/mood-1F4AA.png',
        './icons/emoji/mood-1F604.png',
        './icons/emoji/mood-1F607.png',
        './icons/emoji/mood-1F60A.png',
        './icons/emoji/mood-1F60C.png',
        './icons/emoji/mood-1F60E.png',
        './icons/emoji/mood-1F60F.png',
        './icons/emoji/mood-1F622.png',
        './icons/emoji/mood-1F624.png',
        './icons/emoji/mood-1F630.png',
        './icons/emoji/mood-1F634.png',
        './icons/emoji/mood-1F914.png',
        './icons/emoji/mood-1F917.png',
        './icons/emoji/mood-1F929.png',
        './icons/emoji/mood-1F92A.png',
        './icons/emoji/mood-1F92F.png',
        './icons/emoji/mood-1F970.png',
        './icons/emoji/mood-1F973.png',
        './icons/emoji/mood-1F976.png',
        './icons/emoji/mood-1F97A.png',
        './icons/emoji/mood-1F9E0.png',
        './icons/emoji/mood-1F605.png',
        './icons/emoji/mood-1F975.png',
        './icons/emoji/mood-1F979.png',
        './icons/emoji/mood-1F92D.png',
        './icons/emoji/mood-1F633.png',
        './icons/emoji/mood-1F62E.png',
        './icons/emoji/mood-1F912.png',
        './icons/emoji/mood-1F915.png',
        './icons/emoji/mood-1F927.png',
        './icons/emoji/mood-1F637.png',
        './icons/emoji/mood-1FAE0.png',
        './icons/emoji/mood-1F636.png',
        './icons/emoji/mood-1F610.png',
        './icons/emoji/mood-1F924.png',
      ]).catch(function() {
        /* 预缓存失败不阻止安装，后续 fetch 会按需缓存 */
      });
    }).then(function() {
      return self.skipWaiting();
    })
  );
});

/* activate：清旧缓存 + 立即接管客户端 */
self.addEventListener('activate', function(e) {
  e.waitUntil(
    Promise.all([
      clients.claim(),
      caches.keys().then(function(keys) {
        return Promise.all(
          keys.filter(function(k) { return k !== CACHE_NAME; })
              .map(function(k) { return caches.delete(k); })
        );
      })
    ])
  );
});

/* fetch：网络优先，离线回退缓存；导航请求兜底 index.html */
self.addEventListener('fetch', function(e) {
  if (e.request.method !== 'GET') return;

  e.respondWith(
    fetch(e.request).then(function(resp) {
      /* 网络成功：缓存副本供离线使用 */
      if (resp && resp.ok && resp.type === 'basic') {
        var clone = resp.clone();
        caches.open(CACHE_NAME).then(function(cache) { cache.put(e.request, clone); });
      }
      return resp;
    }).catch(function() {
      /* 离线：从缓存取 */
      return caches.match(e.request).then(function(cached) {
        if (cached) return cached;
        /* 导航请求兜底：返回缓存的 index.html */
        if (e.request.mode === 'navigate') {
          return caches.match('./index.html').then(function(fallback) {
            return fallback || new Response('离线，请稍后重试', { status: 503, headers: { 'Content-Type': 'text/html; charset=UTF-8' } });
          });
        }
        return new Response('', { status: 503 });
      });
    })
  );
});
