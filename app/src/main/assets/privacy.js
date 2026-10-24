(function () {
  'use strict';
  if (window.__nv) return;
  try { Object.defineProperty(window, '__nv', { value: 1 }); } catch (e) {}
  var seed = __SEED__;
  var SITE_LANG = '__LANG__';
  var STRICT = __STRICT__;
  var TRUSTED = __TRUSTED__;
  function safe(f) { try { f(); } catch (e) {} }
  // مواقع التحقق (Cloudflare Turnstile وhCaptcha وreCAPTCHA وDataDome…): أي تعديل في بصمة المتصفح يجعل التحقق يفشل أو يدور بلا نهاية.
  if (/(^|\.)(challenges\.cloudflare\.com|hcaptcha\.com|recaptcha\.net|captcha-delivery\.com|datadome\.co|arkoselabs\.com|funcaptcha\.com|geetest\.com|perimeterx\.net|px-cdn\.net|px-client\.net|friendlycaptcha\.com|mtcaptcha\.com)$/.test(String(location.hostname))) return;
  try {
    var h0 = String(location.hostname).toLowerCase();
    for (var ti = 0; ti < TRUSTED.length; ti++) if (h0 === TRUSTED[ti] || h0.slice(-TRUSTED[ti].length - 1) === '.' + TRUSTED[ti]) return;
    if (localStorage.getItem('__novaFix') === '1') return;
  } catch (e) {}
  function hash(s) { var x = 2166136261; for (var i = 0; i < s.length; i++) { x ^= s.charCodeAt(i); x = Math.imul(x, 16777619); } return x >>> 0; }
  var rv = 0;
  safe(function () { var a = new Uint32Array(2); crypto.getRandomValues(a); rv = a[0] ^ a[1]; });
  var st = (hash(String(location.hostname)) ^ seed ^ rv ^ ((Math.random() * 4294967296) >>> 0)) >>> 0;
  function rnd() { st = (Math.imul(st, 1664525) + 1013904223) >>> 0; return st / 4294967296; }
  var LIGHT = /(^|\.)(google\.[a-z.]+|youtube\.com|gstatic\.com|googleusercontent\.com|ytimg\.com|googlevideo\.com|github\.com|githubusercontent\.com|github\.dev|gitlab\.com|bitbucket\.org|vscode\.dev|stackblitz\.com|codesandbox\.io|codepen\.io|replit\.com|jsfiddle\.net|huggingface\.co|cloudflare\.com|workers\.dev|pages\.dev|vercel\.com|netlify\.app|supabase\.com|notion\.so|figma\.com)$/.test(String(location.hostname));
  var MOBILE = /Android|Mobile/i.test(navigator.userAgent);

  // ---------- إخفاء أثر التعديل: toString() للدوال المعدّلة تُظهر الدالة الأصلية ----------
  var natMap = new WeakMap(), fts = Function.prototype.toString;
  function mask(f, o) { try { if (o) natMap.set(f, o); } catch (e) {} return f; }
  safe(function () {
    var nts = function toString() { var o = natMap.get(this); return fts.call(o || this); };
    natMap.set(nts, fts);
    Function.prototype.toString = nts;
  });
  function hook(proto, name, make) {
    var o = proto && proto[name];
    if (typeof o !== 'function') return;
    proto[name] = mask(make(o), o);
  }

  // صفحة التحقق نفسها (تأتي من نطاق الموقع): تُكتشف ببصمات واضحة في صفحتها، وعندها تُعاد القيم الحقيقية كلها
  var chl = false, chlAt = 0, t0 = Date.now();
  function inChallenge() {
    if (chl) return true;
    var n = Date.now();
    // أول 5 ثوانٍ: فحص كل 300ms (صفحة التحقق تظهر مبكراً)؛ بعدها كل 3 ثوانٍ كي لا تُثقل القراءات المتكررة (devicePixelRatio وغيرها)
    if (chlAt && n - chlAt < (n - t0 < 5000 ? 300 : 3000)) return false;
    chlAt = n;
    try {
      var t = document.title || '';
      if (window._cf_chl_opt || window._cf_chl_ctx || /^(just a moment|checking your browser|attention required|verifying|verify you are human|one more step|security check|ddos-guard|لحظة|جارٍ التحقق)/i.test(t) ||
          document.querySelector('script[src*="challenge-platform"],script[src*="challenges.cloudflare.com"],iframe[src*="challenges.cloudflare.com"],script[src*="hcaptcha.com"],iframe[src*="hcaptcha.com"],script[src*="captcha-delivery.com"],iframe[src*="captcha-delivery.com"]')) chl = true;
    } catch (e) {}
    return chl;
  }
  // خاصية معدّلة تعيد القيمة الحقيقية داخل صفحات التحقق؛ v يمكن أن تكون دالة تحوّل القيمة الحقيقية
  function defN(o, p, v) {
    try {
      var d = Object.getOwnPropertyDescriptor(o, p), og = d && d.get, ov = d && ('value' in d) ? d.value : undefined;
      var gf = function () {
        var real = og ? og.call(this) : ov;
        if (inChallenge()) return real;
        return typeof v === 'function' ? v(real) : v;
      };
      Object.defineProperty(o, p, { get: mask(gf, og), set: d && d.set, enumerable: d ? d.enumerable : true, configurable: true });
    } catch (e) {}
  }
  var cnt = {};
  function tick(k, free, every) { var n = (cnt[k] = (cnt[k] || 0) + 1); return n <= free || (n % every) === 0; }
  function noisePx(a) {
    var n = a.length, flips = 0, first = -1;
    for (var i = 0; i < n; i += 4) {
      if (a[i + 3] === 0) continue;
      if (first < 0) first = i;
      if (rnd() < 0.015) { a[i + ((rnd() * 3) | 0)] ^= 1; flips++; }
    }
    if (!flips && first >= 0) a[first] ^= 1;
  }
  function jitter(a) { var n = Math.min(a.length, 8192); for (var i = 0; i < n; i += 4) { if (rnd() < 0.12) a[i + ((rnd() * 3) | 0)] ^= 1; } }

  // ---------- Navigator ----------
  safe(function () {
    var N = Navigator.prototype;
    defN(N, 'hardwareConcurrency', 8);
    defN(N, 'deviceMemory', 4);
    defN(N, 'doNotTrack', '1');
    defN(N, 'globalPrivacyControl', true);
    defN(N, 'platform', MOBILE ? 'Linux armv81' : 'Linux x86_64');
    defN(N, 'maxTouchPoints', MOBILE ? 5 : 0);
    defN(N, 'vendor', 'Google Inc.');
    defN(N, 'productSub', '20030107');
    if (!LIGHT) { defN(N, 'getBattery', undefined); defN(N, 'connection', undefined); }
    var lang = SITE_LANG || navigator.language || 'en-US';
    if (SITE_LANG) defN(N, 'language', lang);
    defN(N, 'languages', Object.freeze([lang]));
  });
  safe(function () {
    var u = navigator.userAgentData;
    if (u) {
      var maj = ((navigator.userAgent.match(/Chrome\/(\d+)/) || [])[1]) || '120';
      var P = Object.getPrototypeOf(u);
      hook(P, 'getHighEntropyValues', function (oh) {
        return function () {
          if (inChallenge()) return oh.apply(this, arguments);
          var b = this.brands || [];
          return Promise.resolve({
            brands: b, mobile: this.mobile, platform: this.platform, architecture: '', bitness: '', model: '',
            platformVersion: '10.0.0', uaFullVersion: maj + '.0.0.0', wow64: false,
            fullVersionList: b.map(function (x) { return { brand: x.brand, version: maj + '.0.0.0' }; })
          });
        };
      });
    }
  });

  if (LIGHT || !STRICT) return;

  // ---------- الشاشة: قيم موحّدة شائعة بدل أبعاد الجهاز الفريدة ----------
  safe(function () {
    var S = Screen.prototype;
    var rw = screen.width, rh = screen.height;
    var land = rw > rh, sw = Math.min(rw, rh), sh = Math.max(rw, rh);
    var W, H;
    if (MOBILE) {
      var wl = [360, 393, 412, 432], hl = [800, 851, 915, 960, 1024], i;
      W = sw; H = sh;
      for (i = 0; i < wl.length; i++) if (sw <= wl[i]) { W = wl[i]; break; }
      for (i = 0; i < hl.length; i++) if (sh <= hl[i]) { H = hl[i]; break; }
    } else { W = 1080; H = 1920; land = true; }   // وضع سطح المكتب: شاشة 1920×1080 شائعة
    var ww = land ? H : W, hh = land ? W : H;
    defN(S, 'width', ww); defN(S, 'height', hh);
    defN(S, 'availWidth', ww); defN(S, 'availHeight', hh);
    defN(S, 'colorDepth', 24); defN(S, 'pixelDepth', 24);
    if (MOBILE) {
      defN(window, 'devicePixelRatio', function (r) { return Math.max(1, Math.round(r * 2) / 2); });
    } else defN(window, 'devicePixelRatio', 1);
  });

  // ---------- الخطوط: الخطوط غير الشائعة تُعامل كأنها غير مثبّتة (يمنع تعداد خطوط الجهاز) ----------
  var FONT_OK = /^(sans-serif|serif|monospace|cursive|fantasy|system-ui|ui-[a-z-]+|math|emoji|fangsong|-apple-system|blinkmacsystemfont|roboto[\w ]*|noto [\w ]+|droid [\w ]+|arial|arial black|helvetica|helvetica neue|times new roman|times|courier new|courier|georgia|verdana|tahoma|trebuchet ms|comic sans ms|impact|inherit|initial|unset)$/i;
  var webFonts = null, webAt = 0;
  function isWebFont(f) {
    var n = Date.now();
    if (!webFonts || n - webAt > 1500) {
      webFonts = Object.create(null); webAt = n;
      try { document.fonts.forEach(function (ff) { webFonts[String(ff.family).replace(/^["']|["']$/g, '').toLowerCase()] = 1; }); } catch (e) {}
    }
    return !!webFonts[f];
  }
  function famOk(f) { var l = f.toLowerCase(); return FONT_OK.test(l) || isWebFont(l); }
  // يعيد قائمة العائلات بعد حذف غير الشائع منها، أو null إن لم يُحذف شيء
  function stripFam(list) {
    var parts = list.split(','), out = [], bad = false;
    for (var i = 0; i < parts.length; i++) {
      var f = parts[i].trim().replace(/^["']|["']$/g, '');
      if (!f) continue;
      if (famOk(f)) out.push(parts[i].trim()); else bad = true;
    }
    return bad ? (out.length ? out.join(', ') : 'sans-serif') : null;
  }
  var fontCache = Object.create(null), fontCacheN = 0;
  function fixFont(font) {
    var c = fontCache[font];
    if (c !== undefined) return c;
    var r = null, m = /^(.*?[\d.]+(?:px|pt|em|rem|%|vh|vw)(?:\/\S+)?\s+)(.+)$/i.exec(font);
    if (m) { var s = stripFam(m[2]); if (s !== null) r = m[1] + s; }
    if (fontCacheN > 200) { fontCache = Object.create(null); fontCacheN = 0; }
    fontCache[font] = r; fontCacheN++;
    return r;
  }
  safe(function () {
    // عناصر القياس (span خفي بخط مرشّح): offsetWidth/offsetHeight تُقاس بعد حذف الخطوط غير الشائعة
    ['offsetWidth', 'offsetHeight'].forEach(function (p) {
      var d = Object.getOwnPropertyDescriptor(HTMLElement.prototype, p);
      if (!d || !d.get) return;
      var g = d.get;
      var nf = function () {
        var s, ff;
        try { s = this.style; ff = s && s.fontFamily; } catch (e) {}
        if (ff && ff.indexOf(',') > 0 && !inChallenge()) {
          var rep = stripFam(ff);
          if (rep !== null) {
            s.fontFamily = rep;
            try { return g.call(this); } finally { s.fontFamily = ff; }
          }
        }
        return g.call(this);
      };
      Object.defineProperty(HTMLElement.prototype, p, { get: mask(nf, g), set: d.set, enumerable: d.enumerable, configurable: true });
    });
  });

  // ---------- Canvas 2D ----------
  var kind = new WeakMap();
  var getCtx = HTMLCanvasElement.prototype.getContext;
  var gid = CanvasRenderingContext2D.prototype.getImageData;
  var pid = CanvasRenderingContext2D.prototype.putImageData;
  var tdu = HTMLCanvasElement.prototype.toDataURL;
  var tbl = HTMLCanvasElement.prototype.toBlob;
  safe(function () {
    hook(HTMLCanvasElement.prototype, 'getContext', function (o) {
      return function (t) { var c = o.apply(this, arguments); if (c) kind.set(this, String(t)); return c; };
    });
    hook(CanvasRenderingContext2D.prototype, 'getImageData', function (o) {
      return function () { var d = o.apply(this, arguments); if (!inChallenge() && tick('gid', 300, 16)) jitter(d.data); return d; };
    });
  });
  function noisyCopy(cv) {
    try {
      var w = cv.width, h = cv.height;
      if (!w || !h) return null;
      var t = document.createElement('canvas'); t.width = w; t.height = h;
      var c = getCtx.call(t, '2d'); c.drawImage(cv, 0, 0);
      var rw = w, rh = h;
      if (w * h > 600000) { rw = Math.min(w, 512); rh = Math.min(h, 512); }
      var d = gid.call(c, 0, 0, rw, rh); noisePx(d.data); pid.call(c, d, 0, 0);
      return t;
    } catch (e) { return null; }
  }
  function isKnown(cv) { return kind.has(cv); }
  safe(function () {
    hook(HTMLCanvasElement.prototype, 'toDataURL', function (o) {
      return function () { var t = (isKnown(this) && !inChallenge() && tick('cv', 40, 8)) ? noisyCopy(this) : null; return o.apply(t || this, arguments); };
    });
    hook(HTMLCanvasElement.prototype, 'toBlob', function (o) {
      return function () { var t = (isKnown(this) && !inChallenge() && tick('cv', 40, 8)) ? noisyCopy(this) : null; return o.apply(t || this, arguments); };
    });
  });
  safe(function () {
    if (!window.OffscreenCanvas) return;
    var ogid = OffscreenCanvasRenderingContext2D.prototype.getImageData;
    hook(OffscreenCanvasRenderingContext2D.prototype, 'getImageData', function (o) {
      return function () { var d = o.apply(this, arguments); if (!inChallenge() && tick('gid', 300, 16)) jitter(d.data); return d; };
    });
    hook(OffscreenCanvas.prototype, 'convertToBlob', function (cb) {
      return function () {
        try { var c = this.getContext('2d'); if (c && !inChallenge() && tick('cv', 40, 8)) { var d = ogid.call(c, 0, 0, Math.min(this.width, 512), Math.min(this.height, 512)); noisePx(d.data); c.putImageData(d, 0, 0); } } catch (e) {}
        return cb.apply(this, arguments);
      };
    });
  });

  // قياس النص: خطوط غير شائعة تُقاس كأنها غير مثبّتة + تغيير بالغ الصغر في العرض
  safe(function () {
    hook(CanvasRenderingContext2D.prototype, 'measureText', function (mt) {
      return function () {
        if (inChallenge()) return mt.apply(this, arguments);
        var f, rep, r;
        try { f = this.font; rep = fixFont(f); } catch (e) { rep = null; }
        if (rep !== null) {
          this.font = rep;
          try { r = mt.apply(this, arguments); } finally { this.font = f; }
        } else r = mt.apply(this, arguments);
        if (!tick('mt', 600, 64)) return r;
        try { Object.defineProperty(r, 'width', { value: r.width * (1 + (rnd() - 0.5) * 0.0002), configurable: true }); } catch (e) {}
        return r;
      };
    });
  });

  // ---------- WebGL: قيم موحّدة بدل بصمة كرت الشاشة ----------
  var GL_CAP = {
    3379: 8192, 34076: 8192, 34024: 8192, 34921: 16, 36347: 1024, 36348: 30, 36349: 1024,
    35661: 32, 34930: 16, 35660: 16, 32883: 2048, 35071: 2048, 34852: 8, 36063: 8, 36183: 4
  };
  var GL_EXT = /^(ANGLE_instanced_arrays|EXT_blend_minmax|EXT_color_buffer_half_float|EXT_float_blend|EXT_frag_depth|EXT_shader_texture_lod|EXT_texture_filter_anisotropic|OES_element_index_uint|OES_standard_derivatives|OES_texture_float|OES_texture_float_linear|OES_texture_half_float|OES_texture_half_float_linear|OES_vertex_array_object|WEBGL_color_buffer_float|WEBGL_compressed_texture_astc|WEBGL_compressed_texture_etc|WEBGL_compressed_texture_etc1|WEBGL_debug_renderer_info|WEBGL_depth_texture|WEBGL_draw_buffers|WEBGL_lose_context|EXT_color_buffer_float|OES_draw_buffers_indexed|EXT_texture_compression_bptc|EXT_texture_compression_rgtc|EXT_disjoint_timer_query|EXT_disjoint_timer_query_webgl2|KHR_parallel_shader_compile|OVR_multiview2|WEBGL_multi_draw)$/;
  [window.WebGLRenderingContext, window.WebGL2RenderingContext].forEach(function (C) {
    if (!C) return;
    safe(function () {
      hook(C.prototype, 'getParameter', function (gp) {
        return function (p) {
          if (inChallenge()) return gp.apply(this, arguments);
          if (p === 37445) return 'Google Inc. (Qualcomm)';
          if (p === 37446) return 'ANGLE (Qualcomm, Adreno (TM) 640, OpenGL ES 3.2)';
          var r = gp.apply(this, arguments);
          if (typeof r === 'number') { var c = GL_CAP[p]; return c !== undefined && r > c ? c : r; }
          if (p === 3386 && r && r.length === 2) return new Int32Array([Math.min(r[0], 8192), Math.min(r[1], 8192)]);
          if (p === 33901 && r && r.length === 2) return new Float32Array([1, Math.min(r[1], 1023)]);
          if (p === 33902 && r && r.length === 2) return new Float32Array([1, 1]);
          return r;
        };
      });
      hook(C.prototype, 'getSupportedExtensions', function (o) {
        return function () { var r = o.apply(this, arguments); return (r && !inChallenge()) ? r.filter(function (x) { return GL_EXT.test(x); }) : r; };
      });
      hook(C.prototype, 'getExtension', function (o) {
        return function (n) { if (!inChallenge() && !GL_EXT.test(String(n))) return null; return o.apply(this, arguments); };
      });
      hook(C.prototype, 'getShaderPrecisionFormat', function (o) {
        return function (shader, type) {
          var r = o.apply(this, arguments);
          if (!r || inChallenge()) return r;
          // 36336 LOW_FLOAT، 36337 MEDIUM_FLOAT، 36338 HIGH_FLOAT، 36339 LOW_INT، 36340 MEDIUM_INT، 36341 HIGH_INT
          if (type === 36338) return { rangeMin: 127, rangeMax: 127, precision: 23 };
          if (type === 36336 || type === 36337) return { rangeMin: 15, rangeMax: 15, precision: 10 };
          if (type === 36341) return { rangeMin: 31, rangeMax: 30, precision: 0 };
          return { rangeMin: 15, rangeMax: 14, precision: 0 };
        };
      });
      hook(C.prototype, 'readPixels', function (rp) {
        return function () {
          var r = rp.apply(this, arguments);
          if (inChallenge() || !tick('rp', 100, 16)) return r;
          for (var i = 0; i < arguments.length; i++) {
            var a = arguments[i];
            if (a && a.BYTES_PER_ELEMENT === 1 && a.length) { jitter(a); break; }
          }
          return r;
        };
      });
    });
  });

  // ---------- الصوت ----------
  safe(function () {
    var AC = window.AudioContext || window.webkitAudioContext;
    if (AC) { defN(AC.prototype, 'baseLatency', 0.01); defN(AC.prototype, 'outputLatency', 0); }
  });
  safe(function () {
    if (!window.AudioBuffer) return;
    function audioNoise(d) { if (inChallenge()) return; var n = Math.min(d.length, 20000); for (var i = 0; i < n; i += 97) d[i] += (rnd() - 0.5) * 1e-7; }
    var seen = new WeakSet();
    hook(AudioBuffer.prototype, 'getChannelData', function (gcd) {
      return function () { var d = gcd.apply(this, arguments); if (!seen.has(d)) { seen.add(d); audioNoise(d); } return d; };
    });
    hook(AudioBuffer.prototype, 'copyFromChannel', function (cfc) {
      return function (dest) { var r = cfc.apply(this, arguments); audioNoise(dest); return r; };
    });
  });
  safe(function () {
    if (!window.AnalyserNode) return;
    hook(AnalyserNode.prototype, 'getFloatFrequencyData', function (gff) {
      return function (a) { var r = gff.apply(this, arguments); if (inChallenge()) return r; for (var i = 0; i < a.length; i += 7) a[i] += (rnd() - 0.5) * 1e-4; return r; };
    });
    hook(AnalyserNode.prototype, 'getFloatTimeDomainData', function (gft) {
      return function (a) { var r = gft.apply(this, arguments); if (inChallenge()) return r; for (var i = 0; i < a.length; i += 7) a[i] += (rnd() - 0.5) * 1e-7; return r; };
    });
  });
})();
