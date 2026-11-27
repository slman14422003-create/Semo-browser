# Semo-browser skill

## Project
- Android browser, package com.nova.browser, Kotlin + Compose, GeckoView 157, AGP 9.2.1
- Rules: no README additions, raw code without comments in new code, layout numbers belong in ui/UiLayout.kt

## Verified (static only, no gradle or network available)
- tools/project_check.py: no blocking errors
- tools/test/psl.test.js: passes
- bracket balance in all Kotlin and Java files: ok
- manifest classes and R references: ok
- unused i18n entries reported by project_check are warnings, not errors

## Changed
- app/src/main/baseline-prof.txt: removed stale androidx/webkit rule, added core, startup, coroutines, collections, okhttp, okio, geckoview and app class rules

- gradle.properties: android.r8.optimizedResourceShrinking=true (release already had isMinifyEnabled and isShrinkResources)
- extensions/semo/background.js: ad blocking listener no longer receives image requests (types now script, sub_frame, xmlhttprequest, ping, beacon); image blocking is a separate listener registered only when dataSaver is on (syncImageBlock)
- tools/src/boost.js (regenerated via tools/gen-ext.py): no link prefetch on trusted hosts, and download/install paths are skipped
- PullArc.kt: arc redraw invalidates only the arc area instead of the whole layout every frame
- GeckoBridge.kt + UiLayout.REFRESH_TIMEOUT_MS: pull-to-refresh spinner stops after 8s even if the page never reports loading finished
- shield.js and webcompat.js (regenerated): no injection on captcha and challenge hosts (Cloudflare Turnstile, hCaptcha, reCAPTCHA, DataDome, Arkose, GeeTest)

## Findings not changed
- 354 hardcoded dp values outside UiLayout.kt (largest: Bars.kt 52, Tabs.kt 48, MenuSheet.kt 36, IosKit.kt 35); moving them needs a compile to verify
- Downloader.resume polls with Thread.sleep(50) on a pool thread until active segments stop
- IntentAuth.token uses commit() once on first token creation (intended)
- usesCleartextTraffic is true (HTTPS gate handles upgrades)
- duplicate packaging blocks in app/build.gradle.kts (harmless, can be merged)

## Site issues reported
- GitHub and APKMirror lag and partly broken; causes not profiled on device. Next step: Logcat or remote debugging on those two sites

## Notes
- dash.cloudflare.com and similar app hosts already have pull-to-refresh disabled via PageRules.appLike
- the shield.js WebSocket proxy and the polyfills window.open override are detectable by bot checks on the main page of Cloudflare-fronted sites; unverified without a device

## Next
- Build with gradle and fix any compile errors
- Move dp values into UiLayout.kt file by file, starting with Bars.kt
- Replace the Downloader sleep poll with a latch or condition
