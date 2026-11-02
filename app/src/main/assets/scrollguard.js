(function () {
  'use strict';
  try {
    if (window.__novaSg) return;
    window.__novaSg = 1;
    if (!window.NovaScroll) return;
    var EDITOR = '.monaco-editor,.cm-editor,.CodeMirror,.ace_editor,.view-lines,.xterm,.CodeMirror-scroll,[contenteditable=""],[contenteditable="true"],textarea,canvas,iframe,[role="textbox"],[data-nova-noptr]';
    var cache = new WeakMap();
    var sent = '';
    function post(v) { if (v !== sent) { sent = v; try { window.NovaScroll.postMessage(v); } catch (x) { } } }
    function root(el) { return el === document.documentElement || el === document.body || el === document.scrollingElement; }
    var ecache = new WeakMap();
    function isEditor(el) {
      var c = ecache.get(el), now = Date.now();
      if (c && now - c.t < 1500) return c.e;
      var e = 0;
      try { e = el.matches && el.matches(EDITOR) ? 1 : 0; } catch (x) { }
      ecache.set(el, { t: now, e: e });
      return e;
    }
    function verdict(el) {
      var c = cache.get(el);
      var now = Date.now();
      if (c && now - c.t < 1500) return c.v;
      var v = 0;
      try {
        var s = getComputedStyle(el);
        var oy = s.overflowY;
        var ob = s.overscrollBehaviorY;
        if (ob === 'contain' || ob === 'none') v = 1;
        else if ((oy === 'auto' || oy === 'scroll' || oy === 'overlay') && el.scrollHeight > el.clientHeight + 1) v = 1;
      } catch (x) { }
      cache.set(el, { t: now, v: v });
      return v;
    }
    function inner(e) {
      try {
        var p = e.composedPath ? e.composedPath() : null;
        if (!p) { var t = e.target, a = []; while (t && a.length < 60) { a.push(t); t = t.parentNode; } p = a; }
        for (var i = 0; i < p.length && i < 80; i++) {
          var el = p[i];
          if (!el || el.nodeType !== 1) continue;
          if (el.scrollTop > 0) return 1;
          if (isEditor(el)) return 1;
          if (root(el)) {
            var cs = getComputedStyle(el);
            if (cs.overscrollBehaviorY === 'contain' || cs.overscrollBehaviorY === 'none') return 1;
            continue;
          }
          if (verdict(el)) return 1;
        }
        var de = document.scrollingElement || document.documentElement;
        if (de && de.scrollHeight <= de.clientHeight + 2) {
          var bs = getComputedStyle(document.body || de);
          if (bs.overflowY === 'hidden' || getComputedStyle(de).overflowY === 'hidden') return 1;
        }
      } catch (x) { }
      return 0;
    }
    document.addEventListener('touchstart', function (e) {
      try {
        if (e.touches && e.touches.length > 1) return;
        post(inner(e) ? '1' : '0');
        sent = '';
      } catch (x) { }
    }, { capture: true, passive: true });
    document.addEventListener('scroll', function (e) {
      try {
        var t = e.target;
        if (t && t.nodeType === 1 && !root(t)) post('1');
      } catch (x) { }
    }, { capture: true, passive: true });
  } catch (e) { }
})();
