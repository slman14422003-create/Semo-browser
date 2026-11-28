(function () {
  if (window.top !== window) return;
  var last = { c: null, i: null, n: null, b: null };
  var EDITOR =
    '.monaco-editor,.cm-editor,.CodeMirror,.ace_editor,.view-lines,.xterm,.CodeMirror-scroll,[contenteditable=""],[contenteditable="true"],textarea,canvas,iframe,[role="textbox"],[data-nova-noptr]';
  var timer = 0;
  var idle = function (f) {
    return window.requestIdleCallback ? window.requestIdleCallback(f, { timeout: 600 }) : setTimeout(f, 150);
  };

  function send(o) {
    try {
      browser.runtime.sendMessage({ page: o }).catch(function () {});
    } catch (e) {}
  }

  var cvs = null;
  // Convert any CSS color syntax to rgb() through a canvas; return the input when conversion fails
  function norm(c) {
    try {
      if (!c || /^(#|rgb)/i.test(c)) return c;
      if (!cvs) cvs = document.createElement("canvas");
      cvs.width = cvs.height = 1;
      var x = cvs.getContext("2d", { willReadFrequently: true });
      x.clearRect(0, 0, 1, 1);
      x.fillStyle = "#010203";
      x.fillStyle = c;
      x.fillRect(0, 0, 1, 1);
      var d = x.getImageData(0, 0, 1, 1).data;
      if (d[3] < 128) return c;
      return "rgb(" + d[0] + ", " + d[1] + ", " + d[2] + ")";
    } catch (e) {}
    return c;
  }

  function rgba(c) {
    var m = /rgba?\(\s*([\d.]+)[,\s]+([\d.]+)[,\s]+([\d.]+)(?:\s*[,/]\s*([\d.]+)(%?))?/i.exec(c || "");
    if (!m) return null;
    var a = m[4] === undefined ? 1 : parseFloat(m[4]) / (m[5] ? 100 : 1);
    return [parseFloat(m[1]), parseFloat(m[2]), parseFloat(m[3]), a];
  }

  function lum(r, g, b) {
    function f(v) {
      v /= 255;
      return v <= 0.03928 ? v / 12.92 : Math.pow((v + 0.055) / 1.055, 2.4);
    }
    return 0.2126 * f(r) + 0.7152 * f(g) + 0.0722 * f(b);
  }

  function cl(v) {
    return Math.max(0, Math.min(255, Math.round(v)));
  }

  function forced() {
    return document.documentElement && document.documentElement.hasAttribute("data-semo-dark");
  }

  function canvasBase() {
    try {
      var cs = (getComputedStyle(document.documentElement).colorScheme || "").toLowerCase();
      if (cs.indexOf("dark") >= 0 && window.matchMedia("(prefers-color-scheme: dark)").matches) return [18, 18, 18];
    } catch (e) {}
    return [255, 255, 255];
  }

  // Composite the translucent background layers from the element up to the root over the canvas color
  function effBg(el) {
    var layers = [];
    var acc = 0;
    while (el && el.nodeType === 1) {
      var p = rgba(getComputedStyle(el).backgroundColor);
      if (p && p[3] > 0.01) {
        layers.push(p);
        acc = acc + (1 - acc) * p[3];
        if (acc >= 0.99) break;
      }
      el = el.parentElement;
    }
    var out = canvasBase();
    for (var i = layers.length - 1; i >= 0; i--) {
      var l = layers[i];
      out = [out[0] * (1 - l[3]) + l[0] * l[3], out[1] * (1 - l[3]) + l[1] * l[3], out[2] * (1 - l[3]) + l[2] * l[3]];
    }
    return out;
  }

  // What the user actually sees when the page is inverted by the force-dark filter
  function seen(rgb, el) {
    if (!rgb || !forced()) return rgb;
    try { if (el && el.closest && el.closest("[data-semo-keep]")) return rgb; } catch (e) {}
    var r = 255 - rgb[0], g = 255 - rgb[1], b = 255 - rgb[2];
    return [
      cl(-0.574 * r + 1.43 * g + 0.144 * b),
      cl(0.426 * r + 0.43 * g + 0.144 * b),
      cl(0.426 * r + 1.43 * g - 0.856 * b)
    ];
  }

  function str(rgb) {
    return rgb ? "rgb(" + cl(rgb[0]) + ", " + cl(rgb[1]) + ", " + cl(rgb[2]) + ")" : null;
  }

  // First meta theme-color that matches the current media (dark/light variant), then any meta without media
  function themeColor() {
    try {
      var ms = document.querySelectorAll("meta[name=theme-color]");
      var plain = null;
      for (var i = 0; i < ms.length; i++) {
        var md = ms[i].getAttribute("media");
        var v = ms[i].content;
        if (!v) continue;
        if (!md) { if (!plain) plain = v; continue; }
        if (window.matchMedia(md).matches) return v;
      }
      return plain;
    } catch (e) {}
    return null;
  }

  function topBg() {
    try {
      var el = document.elementFromPoint(Math.max(1, window.innerWidth / 2), 1) || document.body || document.documentElement;
      return seen(effBg(el), el);
    } catch (e) {}
    return null;
  }

  function botBg() {
    try {
      var h = Math.max(1, window.innerHeight - 3);
      var xs = [0.1, 0.3, 0.5, 0.7, 0.9];
      var cnt = {};
      var val = {};
      for (var i = 0; i < xs.length; i++) {
        var el = document.elementFromPoint(Math.max(1, window.innerWidth * xs[i]), h);
        while (el && el.nodeType === 1) {
          var tg = el.tagName;
          if (tg !== "IMG" && tg !== "VIDEO" && tg !== "CANVAS" && tg !== "svg" && tg !== "SVG") break;
          el = el.parentElement;
        }
        if (!el) continue;
        var c = seen(effBg(el), el);
        var k = str(c);
        cnt[k] = (cnt[k] || 0) + 1;
        val[k] = k;
      }
      var best = null;
      var bn = 0;
      for (var key in cnt) if (cnt[key] > bn) { bn = cnt[key]; best = key; }
      return best;
    } catch (e) {}
    return null;
  }

  function tint() {
    try {
      var real = topBg();
      var c = null;
      var tc = forced() ? null : themeColor();
      if (tc) {
        var tp = rgba(norm(tc));
        var rp = rgba(str(real));
        // A declared theme color that disagrees strongly with what the page really shows loses
        if (tp && rp) {
          var dl = Math.abs(lum(tp[0], tp[1], tp[2]) - lum(rp[0], rp[1], rp[2]));
          c = dl > 0.45 ? str(real) : tc;
        } else c = tc;
      }
      if (!c) c = str(real);
      if (!c) c = "#ffffff";
      c = norm(c);
      if (c !== last.c) {
        last.c = c;
        send({ t: "tint", c: c });
      }
      var bb = botBg() || c;
      if (bb !== last.b) {
        last.b = bb;
        send({ t: "bgb", c: bb });
      }
    } catch (e) {}
  }

  function icon() {
    try {
      var best = null;
      var bs = -1;
      var l = document.querySelectorAll('link[rel~="icon"],link[rel="apple-touch-icon"],link[rel="apple-touch-icon-precomposed"]');
      for (var i = 0; i < l.length; i++) {
        var s = 0;
        var sz = l[i].getAttribute("sizes");
        if (sz) {
          var p = /(\d+)x/.exec(sz);
          if (p) s = parseInt(p[1], 10);
        }
        if ((l[i].getAttribute("rel") || "").indexOf("apple") >= 0) s += 1;
        if (s > bs) {
          bs = s;
          best = l[i];
        }
      }
      var u = best && best.href ? best.href : location.origin + "/favicon.ico";
      if (u !== last.i) {
        last.i = u;
        send({ t: "icon", u: u });
      }
    } catch (e) {}
  }

  function run() {
    tint();
    icon();
  }

  var lastRun = 0;
  var retryTimer = 0;
  function gated() {
    var now = Date.now();
    var gap = now - lastRun;
    if (gap < 900) {
      clearTimeout(retryTimer);
      retryTimer = setTimeout(gated, 900 - gap);
      return;
    }
    lastRun = now;
    idle(run);
  }

  function soon() {
    clearTimeout(timer);
    timer = setTimeout(gated, 300);
  }

  var scrollCache = new WeakMap();
  var SCROLL_TTL = 3000;
  var chainTimer = 0;
  var lastTarget = null;

  function scrollable(e, now) {
    var c = scrollCache.get(e);
    return c && now - c.t < SCROLL_TTL ? c.v : null;
  }

  function cachedBlocked(t) {
    var now = Date.now(), unknown = false, depth = 0;
    for (var e = t; e && e.nodeType === 1 && depth < 24; e = e.parentElement, depth++) {
      if (e.matches(EDITOR)) return { v: true, unknown: false };
      if (e === document.body || e === document.documentElement) break;
      var c = scrollable(e, now);
      if (c === null) unknown = true;
      else if (c) return { v: true, unknown: false };
    }
    return { v: false, unknown: unknown };
  }

  function resolveChain() {
    chainTimer = 0;
    var t = lastTarget;
    lastTarget = null;
    if (!t) return;
    var now = Date.now(), depth = 0, hit = false;
    try {
      for (var e = t; e && e.nodeType === 1 && depth < 24; e = e.parentElement, depth++) {
        if (e === document.body || e === document.documentElement) break;
        var c = scrollable(e, now);
        if (c === null) {
          var cs = getComputedStyle(e);
          c = /(auto|scroll)/.test(cs.overflowY) && e.scrollHeight > e.clientHeight + 4;
          scrollCache.set(e, { v: c, t: now });
        }
        if (c) { hit = true; break; }
      }
    } catch (x) {}
    if (hit && !last.n) {
      last.n = true;
      send({ t: "noPull", v: true });
    }
  }

  document.addEventListener(
    "touchstart",
    function (ev) {
      var r = cachedBlocked(ev.target);
      if (r.v !== last.n) {
        last.n = r.v;
        send({ t: "noPull", v: r.v });
      }
      if (!r.v && r.unknown) {
        lastTarget = ev.target;
        if (!chainTimer) chainTimer = idle(resolveChain);
      }
    },
    { capture: true, passive: true }
  );

  if (document.readyState === "loading") document.addEventListener("DOMContentLoaded", gated, { once: true });
  else gated();
  window.addEventListener("load", soon, { once: true });
  setTimeout(gated, 1200);
  setTimeout(gated, 3000);
  try {
    var hub = semoHub();
    hub.on(hub.TH | hub.VP, gated, 900);
  } catch (e) {}
  try {
    var mq = window.matchMedia("(prefers-color-scheme: dark)");
    if (mq.addEventListener) mq.addEventListener("change", soon);
  } catch (e) {}
  document.addEventListener("semo-tint", soon);
  var scrollTimer = 0;
  window.addEventListener(
    "scroll",
    function () {
      clearTimeout(scrollTimer);
      scrollTimer = setTimeout(gated, 500);
    },
    { passive: true }
  );
})();
