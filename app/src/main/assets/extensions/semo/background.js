const hosts = new Set();
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
  const p = h.split(".");
  return p.length <= 2 ? h : p.slice(-2).join(".");
}

browser.webRequest.onBeforeRequest.addListener(
  d => {
    try {
      if (d.type === "main_frame") return {};
      const host = new URL(d.url).hostname.toLowerCase();
      if (!listed(host)) return {};
      const doc = d.documentUrl || d.originUrl;
      if (doc) {
        const dh = new URL(doc).hostname.toLowerCase();
        if (baseOf(dh) === baseOf(host)) return {};
      }
      return { cancel: true };
    } catch (e) {
      return {};
    }
  },
  { urls: ["http://*/*", "https://*/*"] },
  ["blocking"]
);

let nativePort = null;
let nativeRetry = 0;
let ytCfg = null;

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
    } else if (m.type === "fill") {
      browser.tabs.sendMessage(m.tab, { pwFill: true, host: m.host, u: m.u, p: m.p }, { frameId: 0 }).catch(() => {});
    } else if (m.type === "ytcfg") {
      ytCfg = m.cfg || null;
      if (Array.isArray(m.tabs)) {
        for (const id of m.tabs) {
          browser.tabs.sendMessage(id, { ytcfg: ytCfg }, { frameId: 0 }).catch(() => {});
        }
      }
    } else if (m.type === "exec") {
      if (m.file !== "summarize.js" && m.file !== "reader.js") {
        nativeReply(p, { type: "execres", id: m.id, result: null });
      } else {
        browser.tabs
          .executeScript(m.tab, { file: m.file, runAt: "document_idle" })
          .then(r => nativeReply(p, { type: "execres", id: m.id, result: r && r.length ? r[r.length - 1] : null }))
          .catch(() => nativeReply(p, { type: "execres", id: m.id, result: null }));
      }
    } else if (m.type === "ytcall") {
      browser.tabs
        .sendMessage(m.tab, { ytcall: true, fn: m.fn, args: m.args }, { frameId: 0 })
        .catch(() => {});
    }
  });
  nativeRetry = 0;
  nativeReply(p, { type: "hello", ua: navigator.userAgent });
}

nativeConnect();

browser.runtime.onMessage.addListener((m, sender) => {
  if (!m || typeof m !== "object") return;
  if (m.ytcfg) return Promise.resolve(ytCfg);
  if (!sender || !sender.tab || sender.frameId !== 0 || !nativePort) return;
  if (m.page && typeof m.page === "object") {
    nativeReply(nativePort, { type: "page", tab: sender.tab.id, url: sender.url, o: m.page });
  } else if (m.pw) {
    nativeReply(nativePort, { type: "pw", tab: sender.tab.id, url: sender.url, o: m.pw });
  } else if (typeof m.yt === "string" && m.yt.length <= 6000) {
    nativeReply(nativePort, { type: "yt", tab: sender.tab.id, url: sender.url, s: m.yt });
  }
});

const YT_MATCHES = ["https://m.youtube.com/*", "https://www.youtube.com/*", "https://youtube.com/*"];
const YT_ID = "semo-yt-main";

async function registerYtMain() {
  try {
    if (!browser.scripting || !browser.scripting.registerContentScripts) return;
    try {
      await browser.scripting.unregisterContentScripts({ ids: [YT_ID] });
    } catch (e) {}
    await browser.scripting.registerContentScripts([
      {
        id: YT_ID,
        matches: YT_MATCHES,
        js: ["yt-main.js"],
        runAt: "document_start",
        world: "MAIN",
        allFrames: false,
        persistAcrossSessions: false
      }
    ]);
  } catch (e) {}
}

registerYtMain();
