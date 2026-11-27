const hosts = new Set();
// إعدادات التطبيق (Prefs) تصل عبر المنفذ الأصلي وتُحفظ ليعمل الحقن فور بدء التشغيل
const DEFAULT_CFG = { shield: true, ads: true, fit: true, lazy: true, boost: true, pop: true, zoom: true, copy: true, draft: true, dataSaver: false, dark: 0 };
let cfg = Object.assign({}, DEFAULT_CFG);
fetch(browser.runtime.getURL("blocklist.txt"))
  .then(r => r.text())
  .then(t => {
    for (const line of t.split("\n")) {
      const s = line.trim().toLowerCase();
      if (s && s[0] !== "#") hosts.add(s);
    }
  })
  .catch(() => {});

function listed(h) {
  while (h) {
    if (hosts.has(h)) return true;
    const i = h.indexOf(".");
    if (i < 0) return false;
    h = h.slice(i + 1);
  }
  return false;
}

function baseOf(h) {
  try { return self.__semoPsl.registrable(h); } catch (e) { return h; }
}

const verdict = new Map();

function hostFast(u) {
  const a = u.indexOf("//");
  if (a < 0) return "";
  let b = u.indexOf("/", a + 2);
  if (b < 0) b = u.length;
  let h = u.slice(a + 2, b);
  const at = h.lastIndexOf("@");
  if (at >= 0) h = h.slice(at + 1);
  const c = h.indexOf(":");
  if (c >= 0) h = h.slice(0, c);
  return h.toLowerCase();
}

function adListener(d) {
  try {
    if (!cfg.ads) return {};
    const host = hostFast(d.url);
    if (!host) return {};
    const doc = d.documentUrl || d.originUrl || "";
    const key = (doc ? hostFast(doc) : "") + ">" + host;
    let v = verdict.get(key);
    if (v === undefined) {
      v = false;
      if (listed(host)) {
        const dh = doc ? hostFast(doc) : "";
        v = !(dh && baseOf(dh) === baseOf(host));
      }
      if (verdict.size > 4000) verdict.clear();
      verdict.set(key, v);
    }
    return v ? { cancel: true } : {};
  } catch (e) {
    return {};
  }
}

function imageListener() {
  return { cancel: true };
}

const WEB_FILTER = ["http://*/*", "https://*/*"];

browser.webRequest.onBeforeRequest.addListener(
  adListener,
  { urls: WEB_FILTER, types: ["script", "sub_frame", "xmlhttprequest", "ping", "beacon"] },
  ["blocking"]
);

function syncImageBlock() {
  try {
    const on = browser.webRequest.onBeforeRequest.hasListener(imageListener);
    if (cfg.dataSaver && !on) {
      browser.webRequest.onBeforeRequest.addListener(imageListener, { urls: WEB_FILTER, types: ["image", "imageset"] }, ["blocking"]);
    } else if (!cfg.dataSaver && on) {
      browser.webRequest.onBeforeRequest.removeListener(imageListener);
    }
  } catch (e) {}
}

let nativePort = null;
let nativeRetry = 0;
let applyChain = Promise.resolve();

function nativeSchedule() {
  nativeRetry = Math.min(nativeRetry + 1, 10);
  setTimeout(nativeConnect, 500 * nativeRetry);
}

function nativeReply(p, msg) {
  try {
    p.postMessage(msg);
  } catch (e) {}
}

function nativeConnect() {
  let p;
  try {
    p = browser.runtime.connectNative("semo");
  } catch (e) {
    nativeSchedule();
    return;
  }
  nativePort = p;
  p.onDisconnect.addListener(() => {
    if (nativePort === p) nativePort = null;
    nativeSchedule();
  });
  p.onMessage.addListener(m => {
    if (!m || typeof m !== "object") return;
    if (m.type === "cookies") {
      browser.cookies
        .getAll({ url: m.url })
        .then(list => nativeReply(p, { type: "cookies", id: m.id, header: list.map(c => c.name + "=" + c.value).join("; ") }))
        .catch(() => nativeReply(p, { type: "cookies", id: m.id, header: "" }));
    } else if (m.type === "cfg") {
      cfg = Object.assign({}, DEFAULT_CFG, m.cfg || {});
      try { browser.storage.local.set({ cfg }).catch(() => {}); } catch (e) {}
      syncImageBlock();
      applyMain();
    } else if (m.type === "fill") {
      browser.tabs.sendMessage(m.tab, { pwFill: true, host: m.host, u: m.u, p: m.p }, { frameId: 0 }).catch(() => {});
    } else if (m.type === "exec") {
      if (m.file !== "summarize.js" && m.file !== "reader.js") {
        nativeReply(p, { type: "execres", id: m.id, result: null });
      } else {
        browser.tabs
          .executeScript(m.tab, { file: m.file, runAt: "document_idle" })
          .then(r => nativeReply(p, { type: "execres", id: m.id, result: r && r.length ? r[r.length - 1] : null }))
          .catch(() => nativeReply(p, { type: "execres", id: m.id, result: null }));
      }
    }
  });
  nativeRetry = 0;
  nativeReply(p, { type: "hello", ua: navigator.userAgent });
}

nativeConnect();

browser.runtime.onMessage.addListener((m, sender) => {
  if (!m || typeof m !== "object") return;
  if (!sender || !sender.tab || sender.frameId !== 0 || !nativePort) return;
  if (m.page && typeof m.page === "object") {
    nativeReply(nativePort, { type: "page", tab: sender.tab.id, url: sender.url, o: m.page });
  } else if (m.pw) {
    nativeReply(nativePort, { type: "pw", tab: sender.tab.id, url: sender.url, incognito: sender.tab.incognito === true, o: m.pw });
  }
});

const MAIN_ID = "semo-main";
const MAIN_TOP_ID = "semo-main-top";

function framesFiles() {
  const js = ["main-polyfills.js", "main-webcompat.js"];
  if (cfg.shield) js.push("main-shield.js");
  return js;
}

function topFiles() {
  const js = ["main-langfix.js", "main-enhance.js", "main-perfboost.js"];
  if (cfg.ads) js.push("main-cosmetic.js");
  if (cfg.fit) js.push("main-fit.js");
  if (cfg.lazy) js.push("main-lazy.js");
  if (cfg.boost) js.push("main-boost.js");
  if (cfg.pop) js.push("main-pop.js");
  if (cfg.fit || cfg.lazy || cfg.boost || cfg.pop) js.push("main-heavy.js");
  if (cfg.zoom) js.push("main-zoom.js");
  if (cfg.copy) js.push("main-copy.js");
  if (cfg.draft) js.push("main-draft.js");
  if (cfg.dark === 2) js.push("main-darkforce.js");
  if (cfg.dark === 1 || cfg.dark === 2) js.push("main-dark.js");
  return js;
}

async function applyMainNow() {
  try {
    if (!browser.scripting || !browser.scripting.registerContentScripts) return;
    try {
      await browser.scripting.unregisterContentScripts({ ids: [MAIN_ID, MAIN_TOP_ID] });
    } catch (e) {}
    const m = ["http://*/*", "https://*/*"];
    await browser.scripting.registerContentScripts([
      { id: MAIN_ID, matches: m, js: framesFiles(), runAt: "document_start", world: "MAIN", allFrames: true, persistAcrossSessions: false },
      { id: MAIN_TOP_ID, matches: m, js: topFiles(), runAt: "document_start", world: "MAIN", allFrames: false, persistAcrossSessions: false }
    ]);
  } catch (e) {}
}

function applyMain() {
  applyChain = applyChain.then(applyMainNow, applyMainNow);
}

try {
  browser.storage.local
    .get("cfg")
    .then(r => {
      if (r && r.cfg) cfg = Object.assign({}, DEFAULT_CFG, r.cfg);
    })
    .catch(() => {})
    .then(() => { syncImageBlock(); applyMain(); });
} catch (e) {
  applyMain();
}
