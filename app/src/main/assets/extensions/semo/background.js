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
