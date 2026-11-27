try{(function(){
(function () {
  if (window.top !== window) return;
  var FORCE = !!(document.documentElement && document.documentElement.hasAttribute("data-semo-force"));
  var ATTR = "data-semo-dark";
  var STYLE_ID = "__semo_dark_css";
  var applied = false;
  var timer = 0;

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

  function markMedia() {
    try {
      var imgs = document.images;
      for (var i = 0; i < imgs.length && i < 400; i++) {
        var im = imgs[i];
        if (im.hasAttribute("data-semo-keep")) continue;
        var w = im.naturalWidth || im.width, h = im.naturalHeight || im.height;
        if (w * h >= 9000) im.setAttribute("data-semo-keep", "1");
      }
      var pics = document.querySelectorAll("picture:not([data-semo-keep])");
      for (var j = 0; j < pics.length && j < 100; j++) pics[j].setAttribute("data-semo-keep", "1");
      var cand = document.querySelectorAll('[style*="background-image"]:not([data-semo-keep])');
      for (var k = 0; k < cand.length && k < 200; k++) {
        var el = cand[k];
        if (el.childElementCount === 0 && !(el.textContent || "").trim()) {
          var r = el.getBoundingClientRect();
          if (r.width * r.height >= 9000) el.setAttribute("data-semo-keep", "1");
        }
      }
    } catch (e) {}
  }

  function on() {
    if (applied) { markMedia(); return; }
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

  function soon() {
    clearTimeout(timer);
    timer = setTimeout(decide, 200);
  }

  if (FORCE) decide();
  if (document.readyState === "loading") document.addEventListener("DOMContentLoaded", decide, { once: true });
  else decide();
  window.addEventListener("load", function () { decide(); setTimeout(markMedia, 600); }, { once: true });
  setTimeout(decide, 1200);
  try {
    var attrs = ["class", "data-theme", "data-mode", "data-color-mode", "data-bs-theme", "style"];
    var watch = function (el) {
      if (el) new MutationObserver(soon).observe(el, { attributes: true, attributeFilter: attrs });
    };
    watch(document.documentElement);
    document.addEventListener("DOMContentLoaded", function () { watch(document.body); }, { once: true });
    var mt = 0;
    new MutationObserver(function () {
      if (!applied || mt) return;
      mt = setTimeout(function () { mt = 0; markMedia(); }, 4000);
    }).observe(document.documentElement, { childList: true, subtree: true });
  } catch (e) {}
})();

}).call(window)}catch(e){}
