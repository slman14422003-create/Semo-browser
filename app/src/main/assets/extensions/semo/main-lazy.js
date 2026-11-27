try{(function(){
(function () {
  try {
    if (!/Android/i.test(navigator.userAgent)) return;
    if (/(^|\.)(youtube\.com|google\.[a-z.]+|googlevideo\.com)$/.test(location.hostname)) return;

    var seen = new WeakSet();
    function imgs() {
      try {
        var l = document.images, n = Math.min(l.length, 300), vh = window.innerHeight * 2;
        var todo = [], defer = [], i;
        for (i = 0; i < n; i++) {
          if (seen.has(l[i])) continue;
          seen.add(l[i]);
          todo.push(l[i]);
        }
        for (i = 0; i < todo.length; i++) {
          var m = todo[i];
          if (!m.hasAttribute('loading') && !m.complete && m.getBoundingClientRect().top > vh) defer.push(m);
        }
        for (i = 0; i < todo.length; i++) if (!todo[i].hasAttribute('decoding')) todo[i].decoding = 'async';
        for (i = 0; i < defer.length; i++) defer[i].loading = 'lazy';
      } catch (e) { }
    }
    var ric = window.requestIdleCallback || function (f) { return setTimeout(f, 60); };
    document.addEventListener('DOMContentLoaded', function () { setTimeout(function () { ric(imgs, { timeout: 800 }); }, 50); });
    window.addEventListener('load', function () { setTimeout(function () { ric(imgs, { timeout: 800 }); }, 300); });
  } catch (e) { }
})();

}).call(window)}catch(e){}
