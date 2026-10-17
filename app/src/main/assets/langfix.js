(function () {
  'use strict';
  if (window.__nvl) return;
  try { Object.defineProperty(window, '__nvl', { value: 1 }); } catch (e) {}
  if (window.top !== window) return;
  // الصفحات بلا lang تُعرض بخط/نموذج حروف خاطئ (أشهرها حروف الصينية/اليابانية/الكورية المشتركة). نستنتج اللغة من النص ونضبطها.
  var TRAD = '國學體說這們會從來個時對發經過後實點開問無為與關門車長東機書電話現學';
  var SIMP = '国学体说这们会从来个时对发经过后实点开问无为与关门车长东机书电话现学';
  function detect(t) {
    var c = { ja: 0, ko: 0, han: 0, hi: 0, th: 0, he: 0, ar: 0, cy: 0, el: 0, bn: 0, ta: 0, other: 0 }, n = 0;
    for (var i = 0; i < t.length; i++) {
      var k = t.charCodeAt(i);
      if (k < 0x41 || (k > 0x5A && k < 0x61) || (k > 0x7A && k < 0xC0)) continue;
      n++;
      if (k >= 0x3040 && k <= 0x30FF) c.ja++;
      else if ((k >= 0xAC00 && k <= 0xD7AF) || (k >= 0x1100 && k <= 0x11FF)) c.ko++;
      else if ((k >= 0x4E00 && k <= 0x9FFF) || (k >= 0x3400 && k <= 0x4DBF)) c.han++;
      else if (k >= 0x0900 && k <= 0x097F) c.hi++;
      else if (k >= 0x0E00 && k <= 0x0E7F) c.th++;
      else if (k >= 0x0590 && k <= 0x05FF) c.he++;
      else if ((k >= 0x0600 && k <= 0x06FF) || (k >= 0x0750 && k <= 0x077F)) c.ar++;
      else if (k >= 0x0400 && k <= 0x04FF) c.cy++;
      else if (k >= 0x0370 && k <= 0x03FF) c.el++;
      else if (k >= 0x0980 && k <= 0x09FF) c.bn++;
      else if (k >= 0x0B80 && k <= 0x0BFF) c.ta++;
      else c.other++;
    }
    if (n < 15) return '';
    if (c.ja >= 3 && (c.ja + c.han) / n > 0.25) return 'ja';
    if (c.ko / n > 0.25) return 'ko';
    if (c.han / n > 0.25) {
      var tr = 0, sm = 0;
      for (var j = 0; j < t.length; j++) { if (TRAD.indexOf(t.charAt(j)) >= 0) tr++; if (SIMP.indexOf(t.charAt(j)) >= 0) sm++; }
      return tr > sm ? 'zh-Hant' : 'zh-Hans';
    }
    if (c.ar / n > 0.4) {
      if (/[\u067E\u0686\u0698\u06AF]/.test(t)) return 'fa';
      if (/[\u0679\u0688\u0691\u06BA\u06D2]/.test(t)) return 'ur';
      return 'ar';
    }
    var m = { hi: 'hi', th: 'th', he: 'he', cy: 'ru', el: 'el', bn: 'bn', ta: 'ta' };
    for (var key in m) if (c[key] / n > 0.4) return m[key];
    return '';
  }
  function sample(limit) {
    var out = '', n, c = 0;
    var w = document.createTreeWalker(document.body, NodeFilter.SHOW_TEXT, {
      acceptNode: function (t) {
        var p = t.parentNode, nm = p && p.nodeName;
        return (!p || nm === 'SCRIPT' || nm === 'STYLE' || nm === 'NOSCRIPT' || nm === 'TEMPLATE') ? NodeFilter.FILTER_REJECT : NodeFilter.FILTER_ACCEPT;
      }
    });
    while (out.length < limit && c++ < 400 && (n = w.nextNode())) {
      var s = n.nodeValue && n.nodeValue.trim();
      if (s) out += s + ' ';
    }
    return out.slice(0, limit);
  }
  function metaLang() {
    try {
      var m = document.querySelector('meta[http-equiv="content-language" i],meta[name="language" i],meta[property="og:locale"]');
      var v = m && m.getAttribute('content');
      if (v && /^[A-Za-z]{2,3}([-_][A-Za-z0-9]{2,8})*$/.test(v.trim())) return v.trim().replace('_', '-');
    } catch (e) {}
    return '';
  }
  var tries = 0;
  function run() {
    try {
      var h = document.documentElement;
      if (!h || h.getAttribute('lang') || !document.body) { if (++tries < 3 && h && !h.getAttribute('lang')) setTimeout(run, 1500); return; }
      var code = detect(sample(1500)) || metaLang();
      if (code) h.setAttribute('lang', code);
      else if (++tries < 3) setTimeout(run, 1500);
    } catch (e) {}
  }
  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', run, { once: true }); else run();
})();
