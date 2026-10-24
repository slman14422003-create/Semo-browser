---
name: nova-site-compat
description: How site compatibility works in Semo/Nova browser (SiteRules, fix-this-site, cookies, popups, SSL exception, language) and what to touch when a site breaks.
---

# Site compatibility

## Where things live
- browser/SiteRules.kt: trusted suffix list, auth host list, SSL exceptions, fix flag helpers, JSON for injected scripts.
- core/SiteStore.kt: per-site `fix` (key `f`), toggled from the page menu.
- assets/privacy.js (+ `Prefs.fpStrict`), smooth.js, cosmetic.js: all read `__TRUSTED__` and localStorage `__novaFix` and skip themselves.
- browser/WebCompat.applyCookies: single place that decides third-party cookies. BrowserTab.thirdOn is set by WebFactory.shouldInterceptRequest when a sub-request hits an auth host.
- polyfills.js: Notification stub, showOpenFilePicker, window.open feature hint for OAuth URLs.
- perf/Smooth.kt (Perf.intercept) and security/Shield.kt: blocking; both bail out for fixed hosts and soften for relaxed hosts.

## When a site breaks
1. Reproduce, then try "Fix this site". If that works, find which layer by turning protections off one at a time.
2. If the site is a developer or login platform, add its suffix to `trustedSuffixes` (and `authSuffixes` if it embeds auth iframes) and extend SiteRulesTest.
3. Add the site to docs/COMPAT_SITES.md.

## Rules for this project
- No comments and no Arabic text inside code; UI strings go to res/values and res/values-ar.
- No README files. Update this skill and docs/TASKS.md after each task.
- Run `python3 tools/project_check.py`, then a Gradle build (last passes were never compiled).

## Open items
- YouTube rotation/fullscreen heal timers are still timer-based; unify after on-device testing.
- yt.js selectors are hard-coded; move to remote rules via the brain channel.
- Google sign-in inside WebView is unverified on device; fallback is a Custom Tab with a separate session.
- Optional second engine (GeckoView) not started.
