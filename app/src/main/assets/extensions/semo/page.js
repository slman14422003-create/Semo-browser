(function () {
  if (window.top !== window) return;
  var last = { c: null, i: null, n: null };
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

  function tint() {
    try {
      var m = document.querySelector("meta[name=theme-color]");
      var c = m && m.content;
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
      if (c !== last.c) {
        last.c = c;
        send({ t: "tint", c: c });
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
        if (/(auto|scroll)/.test(cs.overflowY) && e.scrollHeight > e.clientHeight + 4 && e.scrollTop > 0) return true;
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
  try {
    new MutationObserver(soon).observe(document.head || document.documentElement, {
      childList: true,
      subtree: true,
      attributes: true,
      attributeFilter: ["content", "href", "rel", "name"]
    });
  } catch (e) {}
})();

(function () {
  try {
    if (window.top !== window) return;
    var done = false;
    function pad() {
      try {
        if (done || !document.body) return;
        if (document.documentElement.scrollHeight > window.innerHeight * 1.25) {
          var d = document.createElement("div");
          d.setAttribute("data-semo-pad", "1");
          d.style.cssText = "height:92px;width:1px;pointer-events:none;flex:none";
          document.body.appendChild(d);
          done = true;
        }
      } catch (e) {}
    }
    window.addEventListener("load", function () {
      setTimeout(pad, 600);
      setTimeout(pad, 2500);
    });
  } catch (e) {}
})();
