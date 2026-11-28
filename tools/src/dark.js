(function () {
  if (window.top !== window) return;
  var FORCE = !!(document.documentElement && document.documentElement.hasAttribute("data-semo-force"));
  var ATTR = "data-semo-dark";
  var STYLE_ID = "__semo_dark_css";
  var applied = false;

  var CSS =
    "html[" + ATTR + "]{filter:invert(1) hue-rotate(180deg)!important;background-color:#fff!important}" +
    "html[" + ATTR + "] img[data-semo-keep],html[" + ATTR + "] video,html[" + ATTR + "] picture[data-semo-keep]," +
    "html[" + ATTR + "] canvas,html[" + ATTR + "] [data-semo-keep]{filter:invert(1) hue-rotate(180deg)!important}" +
    "html[" + ATTR + "] [data-semo-keep] img,html[" + ATTR + "] [data-semo-keep] video{filter:none!important}";

  function isHtml() {
    var t = (document.contentType || "").toLowerCase();
    return t === "text/html" || t === "application/xhtml+xml" || t === "";
  }

  function lum(r, g, b) {
    function f(v) {
      v /= 255;
      return v <= 0.03928 ? v / 12.92 : Math.pow((v + 0.055) / 1.055, 2.4);
    }
    return 0.2126 * f(r) + 0.7152 * f(g) + 0.0722 * f(b);
  }

  function parse(c) {
    var m = /rgba?\(\s*([\d.]+)[,\s]+([\d.]+)[,\s]+([\d.]+)(?:\s*[,/]\s*([\d.]+)(%?))?/i.exec(c || "");
    if (!m) return null;
    var a = m[4] === undefined ? 1 : parseFloat(m[4]) / (m[5] ? 100 : 1);
    return [parseFloat(m[1]), parseFloat(m[2]), parseFloat(m[3]), a];
  }

  function pageIsLight() {
    var de = document.documentElement;
    var body = document.body;
    var layers = [];
    if (body) layers.push(parse(getComputedStyle(body).backgroundColor));
    layers.push(parse(getComputedStyle(de).backgroundColor));
    for (var i = 0; i < layers.length; i++) {
      var p = layers[i];
      if (p && p[3] > 0.5) return lum(p[0], p[1], p[2]) > 0.35;
    }
    var scheme = (getComputedStyle(de).colorScheme || "").toLowerCase();
    var mq = window.matchMedia && matchMedia("(prefers-color-scheme: dark)").matches;
    if (mq && scheme.indexOf("dark") >= 0) return false;
    var fg = parse(getComputedStyle(body || de).color);
    if (fg && lum(fg[0], fg[1], fg[2]) > 0.6) return false;
    return true;
  }

  function ensureStyle() {
    if (document.getElementById(STYLE_ID)) return;
    var s = document.createElement("style");
    s.id = STYLE_ID;
    s.textContent = CSS;
    (document.head || document.documentElement).appendChild(s);
  }

  var MIN = 9000, BG = '[style*="background-image"]';
  var hub = semoHub();

  function markImg(im) {
    try {
      if (im.hasAttribute("data-semo-keep")) return;
      var w = im.naturalWidth || im.width, h = im.naturalHeight || im.height;
      if (w * h >= MIN) im.setAttribute("data-semo-keep", "1");
    } catch (e) {}
  }

  function markBg(el) {
    try {
      if (el.hasAttribute("data-semo-keep") || el.childElementCount !== 0 || (el.textContent || "").trim()) return;
      var r = el.getBoundingClientRect();
      if (r.width * r.height >= MIN) el.setAttribute("data-semo-keep", "1");
    } catch (e) {}
  }

  function visit(n) {
    var t = n.nodeName;
    if (t === "IMG") markImg(n);
    else if (t === "PICTURE") n.setAttribute("data-semo-keep", "1");
    else if (n.hasAttribute("style") && (n.getAttribute("style") || "").indexOf("background-image") >= 0) markBg(n);
    if (!n.firstElementChild) return;
    var l = n.querySelectorAll("img,picture," + BG), i, m;
    for (i = 0; i < l.length && i < 200; i++) {
      m = l[i];
      if (m.nodeName === "IMG") markImg(m);
      else if (m.nodeName === "PICTURE") m.setAttribute("data-semo-keep", "1");
      else markBg(m);
    }
  }

  function drain() {
    if (!applied) return;
    var q = hub.q, t0 = Date.now(), n;
    while (q.length && Date.now() - t0 < 6) {
      n = q.shift();
      if (n && n.isConnected) visit(n);
    }
    if (q.length) hub.poke(drain);
  }

  function markMedia() {
    try {
      var imgs = document.images;
      for (var i = 0; i < imgs.length && i < 400; i++) markImg(imgs[i]);
      var pics = document.getElementsByTagName("picture");
      for (var j = 0; j < pics.length && j < 100; j++) pics[j].setAttribute("data-semo-keep", "1");
    } catch (e) {}
    drain();
  }

  function on() {
    if (applied) { drain(); return; }
    applied = true;
    ensureStyle();
    document.documentElement.setAttribute(ATTR, "1");
    markMedia();
  }

  function off() {
    if (!applied) return;
    applied = false;
    document.documentElement.removeAttribute(ATTR);
  }

  function decide() {
    try {
      if (!document.documentElement || !isHtml()) return;
      if (FORCE) { on(); return; }
      if (pageIsLight()) on(); else off();
    } catch (e) {}
  }

  hub.track(true);
  hub.on(hub.TH, decide, 200);
  hub.on(hub.CH, drain, 400);
  document.addEventListener("load", function (e) {
    if (applied && e.target && e.target.nodeName === "IMG") markImg(e.target);
  }, true);
  if (FORCE) decide();
  if (document.readyState === "loading") document.addEventListener("DOMContentLoaded", decide, { once: true });
  else decide();
  window.addEventListener("load", function () { decide(); setTimeout(markMedia, 600); }, { once: true });
  setTimeout(decide, 1200);
})();
