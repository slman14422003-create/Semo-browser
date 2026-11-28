const DEFAULT_CFG = { shield: true, ads: true, fit: true, lazy: true, boost: true, pop: true, zoom: true, copy: true, draft: true, dataSaver: false, dark: 0, off: [] };
let cfg = Object.assign({}, DEFAULT_CFG);

const DNR = browser.declarativeNetRequest || null;
const ADS_RULESET = "ads";
const IMG_RULE = 1;
const WEB_FILTER = ["http://*/*", "https://*/*"];
const IDS = ["semo-frames", "semo-core", "semo-page", "semo-misc", "semo-dark"];
const bypass = new Map();

function off(group) {
  return Array.isArray(cfg.off) && cfg.off.indexOf(group) >= 0;
}

const bypassReady = fetch(browser.runtime.getURL("bypass.txt"))
  .then(r => r.text())
  .then(t => {
    for (const line of t.split("\n")) {
      const s = line.trim().toLowerCase();
      if (!s || s[0] === "#") continue;
      const parts = s.split(/\s+/);
      if (parts.length < 2) continue;
      bypass.set(parts[0], new Set(parts[1].split(",").map(x => x.trim()).filter(Boolean)));
    }
  })
  .catch(() => {});

function excludeFor(group) {
  const out = [];
  for (const [host, groups] of bypass) {
    if (groups.has(group) || groups.has("all")) out.push("*://*." + host + "/*");
  }
  return out;
}

function syncNet() {
  if (!DNR) return;
  try {
    const wantAds = cfg.ads && !off("net");
    DNR.updateEnabledRulesets(wantAds ? { enableRulesetIds: [ADS_RULESET] } : { disableRulesetIds: [ADS_RULESET] }).catch(() => {});
    const add = cfg.dataSaver
      ? [{ id: IMG_RULE, priority: 1, action: { type: "block" }, condition: { urlFilter: "|http", resourceTypes: ["image"] } }]
      : [];
    DNR.updateSessionRules({ removeRuleIds: [IMG_RULE], addRules: add }).catch(() => {});
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
      syncNet();
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
  nativeReply(p, { type: "hello", ua: navigator.userAgent, dnr: !!DNR });
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

function scriptList() {
  const m = ["http://*/*", "https://*/*"];
  const list = [];
  const add = (id, group, js, allFrames) => {
    const ex = excludeFor(group);
    const o = { id, matches: m, js: [js], runAt: "document_start", world: "MAIN", allFrames, persistAcrossSessions: false };
    if (ex.length) o.excludeMatches = ex;
    list.push(o);
  };
  if (!off("frames")) add("semo-frames", "frames", cfg.shield ? "main-frames.js" : "main-frames-ns.js", true);
  if (!off("core")) add("semo-core", "core", "main-core.js", false);
  const page = (cfg.fit ? 1 : 0) | (cfg.lazy ? 2 : 0) | (cfg.boost ? 4 : 0) | (cfg.pop ? 8 : 0);
  if (page && !off("page")) add("semo-page", "page", "main-page-" + page + ".js", false);
  const misc = (cfg.ads ? 1 : 0) | (cfg.zoom ? 2 : 0) | (cfg.copy ? 4 : 0) | (cfg.draft ? 8 : 0);
  if (misc && !off("misc")) add("semo-misc", "misc", "main-misc-" + misc + ".js", false);
  if ((cfg.dark === 1 || cfg.dark === 2) && !off("dark")) add("semo-dark", "dark", "main-dark-" + cfg.dark + ".js", false);
  return list;
}

async function applyMainNow() {
  try {
    if (!browser.scripting || !browser.scripting.registerContentScripts) return;
    await bypassReady;
    try {
      const cur = await browser.scripting.getRegisteredContentScripts();
      const ids = cur.map(s => s.id).filter(id => IDS.indexOf(id) >= 0);
      if (ids.length) await browser.scripting.unregisterContentScripts({ ids });
    } catch (e) {}
    const list = scriptList();
    if (list.length) await browser.scripting.registerContentScripts(list);
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
    .then(() => { syncNet(); applyMain(); });
} catch (e) {
  syncNet();
  applyMain();
}
