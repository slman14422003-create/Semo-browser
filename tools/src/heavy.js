(function () {
  try {
    if (window.top !== window) return;
    var host = String(location.hostname);
    if (/(^|\.)(youtube\.com|youtu\.be|google\.[a-z.]+)$/.test(host)) return;

    function trusted() {
      try {
        var T = __TRUSTED__, h = host.toLowerCase();
        for (var i = 0; i < T.length; i++) if (h === T[i] || h.slice(-T[i].length - 1) === '.' + T[i]) return true;
        return localStorage.getItem('__novaFix') === '1';
      } catch (e) { return false; }
    }
    if (trusted()) return;

    var applied = false;
    function check() {
      if (applied || !document.body) return;
      var pre = document.getElementsByTagName('pre');
      var heavy = document.getElementsByTagName('*').length > 5000 || pre.length > 20;
      for (var i = 0; !heavy && i < pre.length && i < 20; i++) {
        var t = pre[i].textContent;
        if (t && t.length > 60000) heavy = true;
      }
      if (!heavy) return;
      applied = true;
      var st = document.createElement('style');
      st.textContent = 'pre,code,kbd,samp,.highlight,.blob-code,.blob-code-inner,.CodeMirror,.cm-content,.monaco-editor{text-rendering:optimizeSpeed!important;font-variant-ligatures:none!important;font-kerning:none!important}html{scroll-behavior:auto!important}';
      (document.head || document.documentElement).appendChild(st);
      if (pre.length > 20) {
        for (var j = 3; j < pre.length; j++) {
          var p = pre[j];
          if (p.closest && p.closest('table')) continue;
          p.style.setProperty('content-visibility', 'auto');
          p.style.setProperty('contain-intrinsic-size', 'auto 300px');
        }
      }
    }
    var ric = window.requestIdleCallback || function (f) { return setTimeout(f, 200); };
    function later(ms) { setTimeout(function () { if (!applied) ric(check, { timeout: 1500 }); }, ms); }
    document.addEventListener('DOMContentLoaded', function () { later(1200); });
    window.addEventListener('load', function () { later(1500); later(5000); });
  } catch (e) { }
})();
