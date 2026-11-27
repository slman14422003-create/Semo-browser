try{(function(){
(function () {
  try {
    if (window.top !== window) return;
    if (!/^https?:$/.test(location.protocol)) return;
    if (/(^|\.)(youtube\.com|youtu\.be|googlevideo\.com|google\.[a-z.]+)$/.test(location.hostname)) return;

    function editable(t) {
      try {
        return !!(t && t.closest && t.closest('input,textarea,select,[contenteditable=""],[contenteditable="true"],[contenteditable="plaintext-only"]'));
      } catch (e) { return false; }
    }

    window.addEventListener('selectstart', function (e) {
      try { if (!editable(e.target)) e.stopImmediatePropagation(); } catch (x) { }
    }, true);
    window.addEventListener('copy', function (e) {
      try {
        if (!editable(e.target) && String(window.getSelection()).length > 0) e.stopImmediatePropagation();
      } catch (x) { }
    }, true);
    var applied = false;
    function unlock() {
      try {
        if (applied || !document.body) return;
        var cs = getComputedStyle(document.body);
        if ((cs.userSelect || cs.webkitUserSelect) !== 'none') return;
        applied = true;
        var sh = new CSSStyleSheet();
        sh.replaceSync('html *:not(input):not(textarea):not(select):not(button):not([role=button]):not(summary){-webkit-user-select:text!important;user-select:text!important}');
        document.adoptedStyleSheets = document.adoptedStyleSheets.concat([sh]);
      } catch (e) { }
    }
    if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', unlock, { once: true });
    else unlock();
    window.addEventListener('load', function () { setTimeout(unlock, 500); }, { once: true });
  } catch (e) { }
})();

}).call(window)}catch(e){}
