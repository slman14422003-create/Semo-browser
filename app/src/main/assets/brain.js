(function () {
  'use strict';
  if (window.__novaBrain || window.top !== window) return;
  window.__novaBrain = 1;
  var count = 0, lastKey = '';
  function q(s) { try { return document.querySelector(s); } catch (e) { return null; } }
  function qa(s) { try { return document.querySelectorAll(s).length; } catch (e) { return 0; } }
  function meta(n) { var m = q('meta[property="' + n + '"],meta[name="' + n + '"]'); return m ? (m.content || '') : ''; }
  function types() {
    var out = '', l = document.querySelectorAll('script[type="application/ld+json"]');
    for (var i = 0; i < l.length && i < 6; i++) {
      var m = (l[i].textContent || '').match(/"@type"\s*:\s*"[A-Za-z]+"/g);
      if (m) out += m.join(' ');
    }
    return out;
  }
  function stack() {
    var w = window;
    try {
      if (w.ytcfg && w.ytcfg.get) { var cv = w.ytcfg.get('INNERTUBE_CLIENT_VERSION'); if (cv) return ['youtube', String(cv)]; }
      if (w.__NEXT_DATA__) return ['next', (w.next && w.next.version) || ''];
      if (w.__NUXT__) return ['nuxt', ''];
      if (w.Vue && w.Vue.version) return ['vue', w.Vue.version];
      var ng = q('[ng-version]'); if (ng) return ['angular', ng.getAttribute('ng-version') || ''];
      var h = w.__REACT_DEVTOOLS_GLOBAL_HOOK__;
      if (h && h.renderers && h.renderers.size) { var r = h.renderers.values().next().value; return ['react', (r && r.version) || '']; }
      if (w.React && w.React.version) return ['react', w.React.version];
      if (w.Shopify) return ['shopify', ''];
      var g = meta('generator');
      if (g) { var m = g.match(/^([A-Za-z ]+?)\s*([\d.]+)?$/); if (m) return [m[1].toLowerCase().trim(), m[2] || '']; }
      if (w.jQuery && w.jQuery.fn) return ['jquery', w.jQuery.fn.jquery || ''];
      if (q('#root,#app,#__next,app-root')) return ['spa', ''];
    } catch (e) {}
    return ['', ''];
  }
  function classify() {
    var ct = (document.contentType || '').toLowerCase();
    if (ct && ct.indexOf('html') < 0) {
      if (ct.indexOf('json') >= 0) return 'json';
      if (ct.indexOf('svg') >= 0) return 'file';
      if (ct.indexOf('xml') >= 0) return 'xml';
      if (ct.indexOf('text/') === 0) return 'text';
      return 'file';
    }
    var og = meta('og:type').toLowerCase(), t = types(), p = location.pathname.toLowerCase(), h = location.hostname;
    var body = document.body, txt = body ? (body.innerText || '').length : 0;
    var s = { video: 0, news: 0, shop: 0, search: 0, docs: 0, code: 0, forum: 0, social: 0, app: 0, map: 0 };
    if (og.indexOf('video') === 0 || /VideoObject/.test(t)) s.video += 3;
    if (qa('video') && qa('article') < 2) s.video += 1;
    if (og === 'article' || /Article|BlogPosting/.test(t)) s.news += 3;
    if (qa('article p') > 3 || qa('p') > 12) s.news += 1;
    if (og.indexOf('product') >= 0 || /Product|Offer/.test(t) || meta('product:price:amount')) s.shop += 3;
    if (q('[class*="add-to-cart" i],[id*="add-to-cart" i],button[name="add"]')) s.shop += 2;
    if (/\/(search|results)\b/.test(p) || /[?&](q|query|search|s)=/.test(location.search)) s.search += 2;
    if (qa('pre code') > 2 || qa('pre') > 3 || /(github|gitlab|stackoverflow)\./.test(h)) s.code += 2;
    if (/\/(docs?|documentation|guide|reference|manual|wiki)\b/.test(p) || /^(docs|developer|developers)\./.test(h)) s.docs += 2;
    if (/DiscussionForumPosting|QAPage/.test(t) || /\/(forum|thread|topic|questions)\b/.test(p)) s.forum += 2;
    if (og === 'profile' || qa('[role=feed]') > 0 || qa('[role=article]') > 4) s.social += 2;
    if (body && txt < 800 && qa('script') > 4 && q('#root,#app,#__next,[ng-app],app-root')) s.app += 2;
    var c = q('canvas');
    if (c && c.clientWidth >= innerWidth * 0.9 && c.clientHeight >= innerHeight * 0.5 && txt < 400) s.map += 2;
    var best = 'generic', bv = 1;
    for (var k in s) if (s[k] > bv) { best = k; bv = s[k]; }
    return best;
  }
  function origins() {
    var m = {}, self = location.origin;
    try {
      performance.getEntriesByType('resource').forEach(function (e) {
        try { var o = new URL(e.name).origin; if (o !== self && o.indexOf('https://') === 0) m[o] = (m[o] || 0) + 1; } catch (x) {}
      });
    } catch (e) {}
    return Object.keys(m).sort(function (a, b) { return m[b] - m[a]; }).slice(0, 4);
  }
  function report() {
    if (!window.NovaBrain || count > 24) return;
    var key = location.pathname + location.search;
    if (key === lastKey) return;
    lastKey = key; count++;
    var st = stack(), de = document.documentElement;
    try {
      window.NovaBrain.postMessage(JSON.stringify({
        k: classify(), fw: st[0], v: st[1],
        rtl: (de.dir || getComputedStyle(de).direction) === 'rtl' ? 1 : 0,
        n: document.getElementsByTagName('*').length,
        o: origins()
      }));
    } catch (e) {}
  }
  function arm() { setTimeout(report, 700); setTimeout(function () { lastKey = ''; report(); }, 2600); }
  if (document.readyState === 'complete') arm(); else window.addEventListener('load', arm);
  setInterval(function () { if (!document.hidden && location.pathname + location.search !== lastKey) setTimeout(report, 500); }, 1500);
})();
