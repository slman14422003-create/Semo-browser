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

## Tab navigation
- Back handlers in MainActivity are ordered by registration (last registered wins). Tab-level back (goBackTab) sits above the start-page reset and below find, editing, tab switcher and fullscreen handlers.
- Open page-initiated tabs with openChild so back returns to the opener; use openInNewTab for tabs from other apps.

## Script injection
- Use ScriptBundle.add(wv, js) for document-start scripts that apply to every origin; never call addDocumentStartJavaScript directly for those. Scripts needing a specific origin list or a removable handler (Pwa, YtHub, SafariMode) register directly.
- All assets/*.js must pass `node --check` (project_check does it).

## Downloads
- download/FileKind.kt resolves names and sniffs content; Downloader.smartName runs at start and after the probe, Downloader.finalizeName runs in markDone, Downloader.repairOld renames old .bin entries at init.
- If a type is still wrong, add its magic bytes to FileKind.sniff and a case to FileKindTest.
- blob: and data: downloads are still refused in MainActivity and AppActivity.

## When a site breaks
1. Reproduce, then try "Fix this site". If that works, find which layer by turning protections off one at a time.
2. If the site is a developer or login platform, add its suffix to `trustedSuffixes` (and `authSuffixes` if it embeds auth iframes) and extend SiteRulesTest.
3. Add the site to docs/COMPAT_SITES.md.

## Rules for this project
- No comments and no Arabic text inside code; UI strings go to res/values and res/values-ar.
- No README files. Update this skill and docs/TASKS.md after each task.
- Run `python3 tools/project_check.py`, then a Gradle build (last passes were never compiled).

## Performance ideas not done
- Generate a real baseline profile on a device (current one is a wildcard).
- Pre-warm one WebView at startup for the next new tab.
- Smaller thumbnails and capture only when switching tabs.
- Replace yt.js and rotation timers with events after on-device testing.
- blob: and data: downloads through a JS bridge.

## Open items
- YouTube rotation/fullscreen heal timers are still timer-based; unify after on-device testing.
- yt.js selectors are hard-coded; move to remote rules via the brain channel.
- Google sign-in inside WebView is unverified on device; fallback is a Custom Tab with a separate session.
- Optional second engine (GeckoView) not started.
