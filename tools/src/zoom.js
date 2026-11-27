(function () {
  try {
    if (window.top !== window) return;
    if (!/^https?:$/.test(location.protocol)) return;
    if (/(^|\.)(youtube\.com|youtu\.be|googlevideo\.com|google\.[a-z.]+)$/.test(location.hostname)) return;

    var DROP = { 'user-scalable': 1, 'maximum-scale': 1, 'minimum-scale': 1 };
    function fix(m) {
      try {
        var c = m.getAttribute('content') || '';
        var parts = c.split(/[,;]/), keep = [], changed = false;
        for (var i = 0; i < parts.length; i++) {
          var p = parts[i].trim();
          if (!p) continue;
          var k = p.split('=')[0].trim().toLowerCase();
          if (DROP[k]) changed = true; else keep.push(p);
        }
        if (changed) m.setAttribute('content', keep.join(', '));
      } catch (e) { }
    }
    function scan() {
      try {
        var l = document.querySelectorAll('meta[name="viewport" i]');
        for (var i = 0; i < l.length; i++) fix(l[i]);
      } catch (e) { }
    }
    var mo = null;
    function watch() {
      var h = document.head;
      if (!h) return false;
      mo = new MutationObserver(scan);
      mo.observe(h, { childList: true, subtree: true, attributes: true, attributeFilter: ['content', 'name'] });
      return true;
    }
    scan();
    var root = document.documentElement;
    if (root && !watch()) {
      var boot = new MutationObserver(function () {
        if (document.head) { boot.disconnect(); scan(); watch(); }
      });
      boot.observe(root, { childList: true });
    } else if (!root) {
      document.addEventListener('DOMContentLoaded', scan, { once: true });
    }
    window.addEventListener('load', function () {
      setTimeout(function () { if (mo) mo.disconnect(); }, 4000);
    }, { once: true });
  } catch (e) { }
})();
