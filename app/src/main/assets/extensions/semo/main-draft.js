try{(function(){
(function () {
  try {
    if (window.top !== window) return;
    if (!/^https?:$/.test(location.protocol)) return;
    if (/(^|\.)(youtube\.com|youtu\.be|googlevideo\.com|google\.[a-z.]+)$/.test(location.hostname)) return;

    var PFX = 'nvd1:', MAX = 30, TTL = 1800000, MINLEN = 15;
    var SKIP = /pass|pwd|otp|card|cvv|cvc|secret|token|pin\b/i;
    var ls;
    try { ls = window.localStorage; } catch (e) { return; }
    if (!ls) return;
    var tracked = [];
    var setter = Object.getOwnPropertyDescriptor(HTMLTextAreaElement.prototype, 'value');

    function eligible(t) {
      if (!t || t.tagName !== 'TEXTAREA' || t.readOnly || t.disabled) return false;
      var ac = (t.getAttribute('autocomplete') || '').toLowerCase();
      if (ac === 'off' || ac === 'one-time-code' || ac.indexOf('cc-') === 0) return false;
      return !SKIP.test((t.id || '') + ' ' + (t.name || ''));
    }
    function keyFor(t) {
      var id = t.id || t.name;
      if (!id) id = '#' + Array.prototype.indexOf.call(document.getElementsByTagName('textarea'), t);
      return PFX + location.origin + location.pathname + '|' + String(id).slice(0, 60);
    }
    function prune() {
      try {
        var keys = [], now = Date.now();
        for (var i = 0; i < ls.length; i++) {
          var k = ls.key(i);
          if (k && k.indexOf(PFX) === 0) keys.push(k);
        }
        var live = [];
        keys.forEach(function (k) {
          try {
            var o = JSON.parse(ls.getItem(k));
            if (!o || now - o.t > TTL) ls.removeItem(k); else live.push([k, o.t]);
          } catch (e) { ls.removeItem(k); }
        });
        live.sort(function (a, b) { return a[1] - b[1]; });
        while (live.length > MAX) ls.removeItem(live.shift()[0]);
      } catch (e) { }
    }
    function sync(t) {
      try {
        if (!t.isConnected) return;
        var k = keyFor(t), v = t.value || '';
        if (v.length < MINLEN) { if (ls.getItem(k) !== null) ls.removeItem(k); return; }
        var cur = ls.getItem(k);
        if (cur) { try { if (JSON.parse(cur).v === v) return; } catch (e) { } }
        ls.setItem(k, JSON.stringify({ v: v, t: Date.now() }));
      } catch (e) { }
    }
    function track(t) {
      if (tracked.indexOf(t) < 0 && tracked.length < 20) tracked.push(t);
    }
    var timer = 0;
    document.addEventListener('input', function (e) {
      var t = e.target;
      if (!eligible(t)) return;
      track(t);
      clearTimeout(timer);
      timer = setTimeout(function () { sync(t); }, 700);
    }, true);
    document.addEventListener('submit', function (e) {
      try {
        var f = e.target, l = f.getElementsByTagName('textarea');
        for (var i = 0; i < l.length; i++) ls.removeItem(keyFor(l[i]));
      } catch (x) { }
    }, true);
    setInterval(function () {
      if (!tracked.length || document.hidden) return;
      tracked = tracked.filter(function (t) { return t.isConnected; });
      for (var i = 0; i < tracked.length; i++) sync(tracked[i]);
    }, 1500);
    function flush() { for (var i = 0; i < tracked.length; i++) sync(tracked[i]); }
    window.addEventListener('pagehide', flush);
    document.addEventListener('visibilitychange', function () { if (document.hidden) flush(); });

    function restore() {
      try {
        var l = document.getElementsByTagName('textarea'), now = Date.now();
        for (var i = 0; i < l.length; i++) {
          var t = l[i];
          if (!eligible(t) || t.value) continue;
          var raw = ls.getItem(keyFor(t));
          if (!raw) continue;
          var o = JSON.parse(raw);
          if (!o || !o.v || now - o.t > TTL) continue;
          if (setter && setter.set) setter.set.call(t, o.v); else t.value = o.v;
          t.dispatchEvent(new Event('input', { bubbles: true }));
          track(t);
        }
      } catch (e) { }
    }
    function start() {
      prune();
      setTimeout(restore, 700);
      setTimeout(restore, 2500);
    }
    if (document.readyState === 'complete') start();
    else window.addEventListener('load', start, { once: true });
  } catch (e) { }
})();

}).call(window)}catch(e){}
