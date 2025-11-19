// explanation: New small wrapper API that uses the existing flatNotify (from notify/script.js)
// Provides window.notify.success/error/info and shows any queued notification stored in localStorage.lastNotify
(function(){
  // simple helper to instantiate flatNotify when available
  function getNotifier() {
    try {
      if (!document.body) return null; // not yet ready
      if (typeof window.flatNotify === 'function') {
        return window.flatNotify();
      }
    } catch (e) {
      console.debug('getNotifier failed', e);
    }
    return null;
  }

  // simple dedupe to avoid spamming the same notify repeatedly
  var _lastNotify = { msg: null, time: 0 };

  function show(type, message, opts) {
    opts = opts || {};
    try {
      var now = Date.now();
      var normalized = (message || '').toString().trim();
      if (normalized && _lastNotify.msg === normalized && (now - _lastNotify.time) < (opts.throttleMs || 2000)) {
        // suppress duplicate within throttle window
        return;
      }
      _lastNotify.msg = normalized;
      _lastNotify.time = now;
    } catch (e) { /* ignore throttle errors */ }
    // if flatNotify exists, use it; else fallback to alert
    const n = getNotifier();
    if (n) {
      try {
        if (type === 'success') n.success(message, opts.dismissIn);
        else if (type === 'error') n.error(message, opts.dismissIn);
        else n.alert(message, opts.dismissIn);
        return;
      } catch (e) { /* fallback to alert below */ }
    }
    // fallback
    if (type === 'error') alert('错误: ' + message);
    else alert(message);
  }

  window.notify = {
    success: function(msg, opts){ show('success', msg, opts); },
    error: function(msg, opts){ show('error', msg, opts); },
    info: function(msg, opts){ show('info', msg, opts); }
  };

  // On DOMContentLoaded show any queued notification stored in localStorage under 'lastNotify'
  document.addEventListener('DOMContentLoaded', function(){
    try {
      const raw = localStorage.getItem('lastNotify');
      if (!raw) return;
      const obj = JSON.parse(raw);
      if (obj && obj.message) {
        // small timeout to allow CSS/scripts to finish loading
        setTimeout(function(){
          try { if (obj.type === 'success') window.notify.success(obj.message);
          else if (obj.type === 'error') window.notify.error(obj.message);
          else window.notify.info(obj.message); } catch (e) { try { alert(obj.message); } catch(e2){} }
          localStorage.removeItem('lastNotify');
        }, 120);
      }
    } catch (e) { console.debug('notify: failed to show queued notify', e); }
  });
})();
