const CACHE_NAME = 'havencraft-cache-v287';
const ASSETS_TO_CACHE = [
  '/',
  '/index.html',
  '/assets/css/style.css',
  '/assets/css/pages.css',
  '/assets/css/profile.css',
  '/assets/css/vote.css',
  '/assets/css/store.css',
  '/assets/css/leaderboard.css',
  '/assets/css/commands.css',
  '/assets/css/discord.css',
  '/assets/css/support.css',
  '/assets/css/contact.css',
  '/assets/js/core.js',
  '/assets/js/navbar.js',
  '/assets/js/ticket-chat.js',
  '/assets/js/components.js',
  '/assets/js/utils.js',
  '/assets/js/leaderboard-core.js',
  '/assets/js/leaderboard.js',
  '/assets/js/full-leaderboard.js',
  '/assets/js/animations.js',
  '/assets/js/home.js',
  '/assets/js/staff.js',
  '/assets/js/gallery.js',
  '/assets/js/profile.js',
  '/assets/js/commands.js',
  '/assets/js/discord.js',
  '/assets/js/support.js',
  '/assets/js/store.js',
  '/store/config.js',
  '/vote/config.js',
  '/vote/vote.js',
  '/vote/cooldown.js',
  '/vote/ui.js',
  '/vote/validation.js',
  '/vote/supabase.js',
  '/assets/images/bkash-logo.svg',
  '/assets/images/nagad-logo.svg',
  '/images/community.webp',
  '/images/sanctuary-hero.webp',
  '/images/survival.webp',
  '/images/gameplay.webp',
  '/logo/icon.svg',
  '/logo/logo.svg',
  '/logo/logo.png',
  '/manifest.json'
];

self.addEventListener('install', (event) => {
  self.skipWaiting();
  event.waitUntil(
    caches.open(CACHE_NAME).then((cache) => {
      const cachePromises = ASSETS_TO_CACHE.map((url) => {
        const request = new Request(url, { cache: 'reload' });
        return fetch(request).then((response) => {
          if (response && response.ok && response.status === 200) {
            return cache.put(url, response);
          }
          return Promise.resolve();
        }).catch(() => Promise.resolve());
      });
      return Promise.all(cachePromises);
    }).catch(err => console.warn('SW Cache Install Error:', err))
  );
});

self.addEventListener('activate', (event) => {
  event.waitUntil(
    caches.keys().then((cacheNames) => {
      return Promise.all(
        cacheNames.filter((name) => name !== CACHE_NAME).map((name) => caches.delete(name))
      );
    }).then(() => self.clients.claim())
  );
});

self.addEventListener('fetch', (event) => {
  if (event.request.method !== 'GET') return;
  if (!event.request.url.startsWith('http')) return;
  if (!event.request.url.startsWith(self.location.origin)) return;
  if (event.request.url.includes('/api/')) return;
  if (event.request.url.includes('/sitemap.xml') || event.request.url.includes('/robots.txt')) return;
  if (event.request.url.includes('/profile/ranks.js') || event.request.url.includes('ranks.js') || event.request.url.includes('/profile?')) return;

  event.respondWith(
    fetch(event.request).then(response => {
      // Only cache valid 200 responses; never cache 500, 404, or partial error pages
      if (response && response.ok && response.status === 200) {
        const resClone = response.clone();
        caches.open(CACHE_NAME).then(cache => cache.put(event.request, resClone));
      }
      return response;
    }).catch(() => {
      return caches.match(event.request, { ignoreSearch: true }).then(cached => {
        if (cached) return cached;
        if (event.request.mode === 'navigate') {
          return caches.match('/', { ignoreSearch: true }).then(rootCached => rootCached || caches.match('/index.html', { ignoreSearch: true }) || Response.error());
        }
        return Response.error();
      });
    })
  );
});

// Handle notification clicks for Vote Reminder
self.addEventListener('notificationclick', (event) => {
  event.notification.close();
  const targetUrl = event.notification.data?.url || '/vote/';

  event.waitUntil(
    clients.matchAll({ type: 'window', includeUncontrolled: true }).then((clientList) => {
      for (const client of clientList) {
        if (client.url.includes('/vote') && 'focus' in client) {
          return client.focus();
        }
      }
      if (clients.openWindow) {
        return clients.openWindow(targetUrl);
      }
    })
  );
});
