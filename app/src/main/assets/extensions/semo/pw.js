(function () {
  if (window.top !== window || window.__semoPw) return;
  window.__semoPw = 1;
  var last = '', lastT = 0, announced = false, tm = null;

  function vis(e) {
    try {
      var r = e.getBoundingClientRect(), cs = getComputedStyle(e);
      return r.width > 0 && r.height > 0 && cs.visibility !== 'hidden' && cs.display !== 'none';
    } catch (x) { return false; }
  }
  function pwFields() {
    return Array.prototype.filter.call(document.querySelectorAll('input[type=password]'), vis);
  }
  function nameFields() {
    return Array.prototype.filter.call(document.querySelectorAll('input[autocomplete~=username]'), vis);
  }
  var textSel = 'input:not([type]),input[type=text],input[type=email],input[type=tel],input[type=url]';
  var deny = /(otp|one-time|verif|code|card|cc-|cvv|cvc|search|query|captcha|token|coupon|promo|zip|postal|\bpin\b)/i;
  var allow = /(user|login|log-in|signin|sign-in|email|e-mail|mail|account|acct|identifier|uname|phone|mobile)/i;
  function labelText(el) {
    var t = '';
    try {
      var l = el.labels || [];
      for (var i = 0; i < l.length; i++) t += ' ' + (l[i].textContent || '');
    } catch (x) {}
    return t;
  }
  function isUserCandidate(el) {
    var ac = String(el.getAttribute('autocomplete') || '').toLowerCase();
    if (/(^|\s)one-time-code(\s|$)/.test(ac) || /(^|\s)cc-/.test(ac)) return false;
    if (/(^|\s)(username|email)(\s|$)/.test(ac)) return true;
    var type = String(el.getAttribute('type') || 'text').toLowerCase();
    var meta = [ac, el.name, el.id, el.getAttribute('aria-label'), el.getAttribute('placeholder'), labelText(el)].join(' ');
    if (deny.test(meta)) return false;
    if (type === 'email') return true;
    return allow.test(meta);
  }
  function nearestBefore(pw, list) {
    var best = null;
    for (var i = 0; i < list.length; i++) {
      if (list[i].compareDocumentPosition(pw) & 4) best = list[i];
    }
    return best;
  }
  function userField(pw) {
    var scope = pw.form || document;
    var c = Array.prototype.slice.call(scope.querySelectorAll(textSel)).filter(vis).filter(isUserCandidate);
    return nearestBefore(pw, c);
  }
  function fillUserField(pw) {
    var scope = pw.form || document;
    var c = Array.prototype.slice.call(scope.querySelectorAll(textSel)).filter(vis);
    return nearestBefore(pw, c);
  }
  function send(t, o) {
    try {
      o.t = t;
      browser.runtime.sendMessage({ pw: o }).catch(function () {});
    } catch (e) {}
  }
  function capture() {
    var f = pwFields();
    for (var i = 0; i < f.length; i++) {
      var p = f[i];
      if (!p.value) continue;
      var u = userField(p), uv = u ? u.value : '';
      var key = uv + '\u0000' + p.value, now = Date.now();
      if (key === last && now - lastT < 4000) return;
      last = key; lastT = now;
      send('save', { u: uv, p: p.value });
      return;
    }
  }
  var skip = /(show|hide|reveal|eye|visib|toggle)/i;
  document.addEventListener('submit', function (e) {
    if (!e.isTrusted) return;
    capture();
  }, true);
  document.addEventListener('click', function (e) {
    if (!e.isTrusted) return;
    var b = e.target && e.target.closest && e.target.closest('button,input[type=submit],input[type=button],[role=button],a');
    if (!b) return;
    var meta = (b.getAttribute('aria-label') || '') + ' ' + (b.className || '') + ' ' + (b.id || '') + ' ' + (b.title || '');
    if (skip.test(meta)) return;
    setTimeout(capture, 0);
  }, true);
  document.addEventListener('keydown', function (e) {
    if (!e.isTrusted) return;
    if (e.key === 'Enter' && e.target && e.target.type === 'password') capture();
  }, true);

  function announce() {
    var f = pwFields();
    if (!f.length) f = nameFields();
    if (f.length && !announced) { announced = true; send('form', {}); }
    else if (!f.length) announced = false;
  }
  try {
    new MutationObserver(function () { clearTimeout(tm); tm = setTimeout(announce, 300); })
      .observe(document.documentElement, { childList: true, subtree: true });
  } catch (e) {}
  announce();
  setTimeout(announce, 800);

  function setValue(el, v) {
    var d = Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value');
    d.set.call(el, v);
    el.dispatchEvent(new Event('input', { bubbles: true }));
    el.dispatchEvent(new Event('change', { bubbles: true }));
  }
  function fill(u, p) {
    var f = pwFields(), pw = f[0] || null, us = null;
    if (pw) us = fillUserField(pw); else us = nameFields()[0] || null;
    if (!pw && !us) return false;
    if (us && u) setValue(us, u);
    if (pw && p) setValue(pw, p);
    return true;
  }
  function normHost(h) {
    return String(h || '').trim().toLowerCase().replace(/^www\./, '');
  }
  browser.runtime.onMessage.addListener(function (m) {
    if (!m || !m.pwFill) return;
    var h = location.hostname;
    if (location.protocol !== 'https:' && h !== 'localhost') return Promise.resolve(false);
    if (normHost(h) !== normHost(m.host)) return Promise.resolve(false);
    return Promise.resolve(fill(m.u, m.p));
  });
})();
