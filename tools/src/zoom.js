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
      } catch (e) {}
    }
    function scan() {
      try {
        var l = document.querySelectorAll('meta[name="viewport" i]');
        for (var i = 0; i < l.length; i++) fix(l[i]);
      } catch (e) {}
    }
    var hub = semoHub();
    var sub = hub.on(hub.VP, scan, 150);
    scan();
    window.addEventListener('load', function () {
      scan();
      setTimeout(function () { hub.off(sub); }, 4000);
    }, { once: true });
  } catch (e) {}
})();
