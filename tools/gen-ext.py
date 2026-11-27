#!/usr/bin/env python3
import json
import re
from pathlib import Path

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

MAIN = [
    "polyfills", "webcompat", "shield",
    "langfix", "enhance", "perfboost", "cosmetic",
    "fit", "lazy", "boost", "pop", "heavy",
    "zoom", "copy", "draft", "darkforce", "dark",
]
KEEP = {"__NEXT_DATA__", "__NUXT__"}

wanted = set()
for name in MAIN:
    text = (SRC / (name + ".js")).read_text(encoding="utf-8").replace("__TRUSTED__", TRUSTED)
    left = [m for m in re.findall(r"__[A-Z]+__", text) if m not in KEEP]
    if left:
        raise SystemExit(name + ".js: unresolved markers " + str(left))
    target = OUT / ("main-" + name + ".js")
    target.write_text("try{(function(){\n" + text + "\n}).call(window)}catch(e){}\n", encoding="utf-8")
    wanted.add(target.name)

for stale in OUT.glob("main-*.js"):
    if stale.name not in wanted:
        stale.unlink()
for stale in OUT.glob("cfg-*.js"):
    stale.unlink()

print("ok:", len(MAIN), "scripts")
