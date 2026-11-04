// Receives push messages from the server and shows them, even when the AgriCient page is closed.
self.addEventListener('push', (event) => {
  let data = { title: 'AgriCient', body: 'You have a new weather alert.', url: '/#myAlerts' };
  try {
    if (event.data) data = { ...data, ...event.data.json() };
  } catch (e) { /* keep the defaults */ }
  event.waitUntil(self.registration.showNotification(data.title, {
    body: data.body,
    tag: 'agricient-alert',
    data: { url: data.url }
  }));
});

self.addEventListener('notificationclick', (event) => {
  event.notification.close();
  const url = (event.notification.data && event.notification.data.url) || '/';
  event.waitUntil(self.clients.matchAll({ type: 'window' }).then((list) => {
    for (const c of list) {
      if ('focus' in c) { c.navigate(url); return c.focus(); }
    }
    return self.clients.openWindow(url);
  }));
});
