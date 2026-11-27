(function () {
  if (window.top !== window) return;
  var last = { c: null, i: null, n: null, b: null };
  var EDITOR =
    '.monaco-editor,.cm-editor,.CodeMirror,.ace_editor,.view-lines,.xterm,.CodeMirror-scroll,[contenteditable=""],[contenteditable="true"],textarea,canvas,iframe,[role="textbox"],[data-nova-noptr]';
  var timer = 0;

  function send(o) {
    try {
      browser.runtime.sendMessage({ page: o }).catch(function () {});
    } catch (e) {}
  }

  function usable(c) {
    return c && c !== "transparent" && c !== "rgba(0, 0, 0, 0)" ? c : null;
  }

  var cvs = null;
  // يحوّل أي صيغة لون (oklch, color(), lab…) إلى rgb عبر canvas؛ عند الفشل يعيد النص كما هو
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

  // أول meta theme-color يطابق media الحالية (نسخة الداكن/الفاتح)، ثم أي meta بدون media
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
      var el = document.elementFromPoint(Math.max(1, window.innerWidth / 2), 1);
      while (el) {
        var c = usable(getComputedStyle(el).backgroundColor);
        if (c) return c;
        el = el.parentElement;
      }
    } catch (e) {}
    return null;
  }

  function botBg() {
    try {
      var h = Math.max(1, window.innerHeight - 3);
      var xs = [0.1, 0.3, 0.5, 0.7, 0.9];
      var cnt = {};
      for (var i = 0; i < xs.length; i++) {
        var el = document.elementFromPoint(Math.max(1, window.innerWidth * xs[i]), h);
        while (el) {
          var tg = el.tagName;
          if (tg !== "IMG" && tg !== "VIDEO" && tg !== "CANVAS" && tg !== "svg" && tg !== "SVG") {
            var c = usable(getComputedStyle(el).backgroundColor);
            if (c) {
              cnt[c] = (cnt[c] || 0) + 1;
              break;
            }
          }
          el = el.parentElement;
        }
      }
      var best = null;
      var bn = 0;
      for (var k in cnt) if (cnt[k] > bn) { bn = cnt[k]; best = k; }
      return best;
    } catch (e) {}
    return null;
  }

  function tint() {
    try {
      var c = themeColor();
      if (!c) c = topBg();
      if (!c) {
        var h = getComputedStyle(document.documentElement).backgroundColor;
        var b = document.body ? getComputedStyle(document.body).backgroundColor : null;
        c = usable(b) || usable(h);
      }
      if (!c) {
        var cs = (getComputedStyle(document.documentElement).colorScheme || "").toLowerCase();
        c = cs.indexOf("dark") >= 0 && cs.indexOf("light") < 0 ? "#121212" : "#ffffff";
      }
      c = norm(c);
      if (c !== last.c) {
        last.c = c;
        send({ t: "tint", c: c });
      }
      var bb = norm(botBg()) || c;
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

  function soon() {
    clearTimeout(timer);
    timer = setTimeout(run, 250);
  }

  function blocked(t) {
    try {
      for (var e = t; e && e.nodeType === 1; e = e.parentElement) {
        if (e.matches && e.matches(EDITOR)) return true;
        if (e === document.body || e === document.documentElement) return false;
        var cs = getComputedStyle(e);
        if (/(auto|scroll)/.test(cs.overflowY) && e.scrollHeight > e.clientHeight + 4) return true;
      }
    } catch (x) {}
    return false;
  }

  document.addEventListener(
    "touchstart",
    function (ev) {
      var v = blocked(ev.target);
      if (v !== last.n) {
        last.n = v;
        send({ t: "noPull", v: v });
      }
    },
    { capture: true, passive: true }
  );

  if (document.readyState === "loading") document.addEventListener("DOMContentLoaded", run, { once: true });
  else run();
  window.addEventListener("load", soon, { once: true });
  setTimeout(tint, 900);
  setTimeout(tint, 2600);
  // مواقع كثيرة تبدّل الداكن/الفاتح عبر class أو data-theme على html/body (لا يظهر في <head>)
  try {
    var themeAttrs = ["class", "style", "data-theme", "data-mode", "data-color-mode", "data-bs-theme"];
    var watchEl = function (el) {
      if (el) new MutationObserver(soon).observe(el, { attributes: true, attributeFilter: themeAttrs });
    };
    watchEl(document.documentElement);
    if (document.body) watchEl(document.body);
    else document.addEventListener("DOMContentLoaded", function () { watchEl(document.body); }, { once: true });
  } catch (e) {}
  try {
    var mq = window.matchMedia("(prefers-color-scheme: dark)");
    if (mq.addEventListener) mq.addEventListener("change", soon);
  } catch (e) {}
  var scrollTimer = 0;
  window.addEventListener(
    "scroll",
    function () {
      clearTimeout(scrollTimer);
      scrollTimer = setTimeout(tint, 300);
    },
    { passive: true }
  );
  try {
    new MutationObserver(soon).observe(document.head || document.documentElement, {
      childList: true,
      subtree: true,
      attributes: true,
      attributeFilter: ["content", "href", "rel", "name"]
    });
  } catch (e) {}
})();

