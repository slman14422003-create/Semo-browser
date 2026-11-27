(function () {
  try {
    if (!/Android/i.test(navigator.userAgent)) return;
    if (/(^|\.)(youtube\.com|google\.[a-z.]+|googlevideo\.com)$/.test(location.hostname)) return;

    function trusted() {
      try {
        var T = __TRUSTED__, h = String(location.hostname).toLowerCase();
        for (var i = 0; i < T.length; i++) if (h === T[i] || h.slice(-T[i].length - 1) === '.' + T[i]) return true;
        return localStorage.getItem('__novaFix') === '1';
      } catch (e) { return false; }
    }

    var css =
      'html{-webkit-text-size-adjust:100%;text-size-adjust:100%;-webkit-tap-highlight-color:transparent}' +
      'a,button,summary,label,input,select,[role=button]{touch-action:manipulation}' +
      'img{image-orientation:from-image}' +
      ':focus-visible{outline:2px solid #4c8dff;outline-offset:2px;border-radius:4px}' +
      '::selection{background:rgba(76,141,255,.35)}' +
      'html{scrollbar-width:thin;overscroll-behavior-x:none}' +
      '@media (prefers-reduced-motion:reduce){*{scroll-behavior:auto!important}}';

    if (!trusted()) {
      try {
        var sh = new CSSStyleSheet();
        sh.replaceSync(css);
        document.adoptedStyleSheets = document.adoptedStyleSheets.concat([sh]);
      } catch (e) { }

      var guard = function () {
        try {
          var m = document.querySelector('meta[name=viewport]');
          if (!m || !/device-width/i.test(m.content)) return;
          var de = document.documentElement;
          if (de.scrollWidth > window.innerWidth * 1.02) de.style.setProperty('overflow-x', 'hidden');
        } catch (e) { }
      };
      window.addEventListener('load', function () { setTimeout(guard, 400); });
    }

    var seen = new WeakSet();
    function decode() {
      try {
        var l = document.images, n = Math.min(l.length, 300);
        for (var i = 0; i < n; i++) {
          if (seen.has(l[i])) continue;
          seen.add(l[i]);
          if (!l[i].hasAttribute('decoding')) l[i].decoding = 'async';
        }
      } catch (e) { }
    }
    var ric = window.requestIdleCallback || function (f) { return setTimeout(f, 60); };
    document.addEventListener('DOMContentLoaded', function () { setTimeout(function () { ric(decode, { timeout: 800 }); }, 50); });
    window.addEventListener('load', function () { setTimeout(function () { ric(decode, { timeout: 800 }); }, 300); });
  } catch (e) { }
})();
