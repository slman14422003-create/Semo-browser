#!/usr/bin/env python3
import json
import re
from pathlib import Path

from jsmin import minify

ROOT = Path(__file__).resolve().parent.parent
SRC = ROOT / "tools/src"
OUT = ROOT / "app/src/main/assets/extensions/semo"

TRUSTED = json.dumps([
    "firebase.google.com", "firebaseapp.com", "web.app", "firebaseio.com", "cloud.google.com", "console.cloud.google.com",
    "cloudflare.com", "cloudflareaccess.com", "pages.dev", "workers.dev", "cloudflarestorage.com", "github.com", "github.dev",
    "gitlab.com", "bitbucket.org", "vercel.com", "vercel.app", "netlify.com", "netlify.app", "supabase.com", "supabase.co",
    "render.com", "onrender.com", "heroku.com", "herokuapp.com", "railway.app", "fly.io", "digitalocean.com", "amazon.com",
    "aws.amazon.com", "amazonaws.com", "azure.com", "azurewebsites.net", "microsoft.com", "microsoftonline.com", "live.com",
    "office.com", "atlassian.net", "atlassian.com", "slack.com", "notion.so", "figma.com", "stripe.com", "paypal.com",
    "auth0.com", "okta.com", "apple.com", "facebook.com", "linkedin.com", "x.com", "twitter.com", "discord.com", "openai.com",
    "chatgpt.com", "claude.ai", "anthropic.com", "huggingface.co", "expo.dev", "mongodb.com", "neon.tech", "sentry.io",
    "datadoghq.com", "codepen.io", "jsfiddle.net", "stackblitz.com", "codesandbox.io", "replit.com", "glitch.com", "glitch.me",
    "google.com", "googleusercontent.com", "gstatic.com",
])

KEEP = {"__NEXT_DATA__", "__NUXT__"}
CORE = ["langfix", "enhance", "perfboost"]
PAGE_FLAGS = ["fit", "lazy", "boost", "pop"]
MISC_FLAGS = ["cosmetic", "zoom", "copy", "draft"]
CHUNK = 1000
DNR_TYPES = ["script", "sub_frame", "xmlhttprequest", "ping"]
HOST = re.compile(r"[a-z0-9.-]+\.[a-z]{2,}")


def read(name):
    text = (SRC / (name + ".js")).read_text(encoding="utf-8").replace("__TRUSTED__", TRUSTED)
    return text


def check(name, text):
    left = [m for m in re.findall(r"__[A-Z]+__", text) if m not in KEEP and m != "__SHIELD__"]
    if left:
        raise SystemExit(name + ".js: unresolved markers " + str(left))


def wrap(text):
    return "try{(function(){\n" + text + "\n}).call(window)}catch(e){}\n"


def bundle(names, hub, replace=None):
    parts = [read("hub")] if hub else []
    for name in names:
        text = read(name)
        check(name, text)
        if replace:
            for k, v in replace.items():
                text = text.replace(k, v)
        parts.append(wrap(text))
    return minify("(function(){\n" + "\n".join(parts) + "\n})();\n")


wanted = {}

wanted["main-frames.js"] = bundle(["frames"], True, {"__SHIELD__": "true"})
wanted["main-frames-ns.js"] = bundle(["frames"], True, {"__SHIELD__": "false"})
wanted["main-core.js"] = bundle(CORE, False)

for mask in range(1, 16):
    names = [PAGE_FLAGS[b] for b in range(4) if mask & (1 << b)] + ["heavy"]
    wanted["main-page-%d.js" % mask] = bundle(names, False)
    names = [MISC_FLAGS[b] for b in range(4) if mask & (1 << b)]
    wanted["main-misc-%d.js" % mask] = bundle(names, bool(mask & 2))

wanted["main-dark-1.js"] = bundle(["dark"], True)
wanted["main-dark-2.js"] = bundle(["darkforce", "dark"], True)

wanted["hub.js"] = minify(read("hub"))

doms = []
seen = set()
for line in (SRC / "blocklist.txt").read_text(encoding="utf-8").splitlines():
    d = line.strip().lower()
    if not d or d.startswith("#") or d in seen or not HOST.fullmatch(d):
        continue
    seen.add(d)
    doms.append(d)
rules = []
for i in range(0, len(doms), CHUNK):
    rules.append({
        "id": len(rules) + 1,
        "priority": 1,
        "action": {"type": "block"},
        "condition": {"requestDomains": doms[i : i + CHUNK], "domainType": "thirdParty", "resourceTypes": DNR_TYPES},
    })
wanted["rules-ads.json"] = json.dumps(rules, separators=(",", ":")) + "\n"
wanted["bypass.txt"] = (SRC / "bypass.txt").read_text(encoding="utf-8")

for name, text in wanted.items():
    (OUT / name).write_text(text, encoding="utf-8")

generated = set(wanted) | {"background.js", "manifest.json", "page.js", "pw.js", "psl.js", "reader.js", "summarize.js"}
for stale in OUT.iterdir():
    if stale.is_file() and stale.name not in generated:
        stale.unlink()

print("ok:", len(wanted), "files,", len(doms), "domains,", len(rules), "rules")
