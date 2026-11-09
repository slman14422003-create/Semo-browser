#!/bin/sh
R="$(cd "$(dirname "$0")" && pwd)"
J="$R/app/src/main/java/com/nova/browser"
A="$R/app/src/main/assets"
rm -f "$J/brain/PageBrain.kt" "$J/browser/NovaWebView.kt" "$J/browser/Popups.kt" "$J/browser/SiteRules.kt" "$J/browser/UserAgent.kt" "$J/browser/WebBoost.kt" "$J/browser/WebCompat.kt" "$J/browser/WebEngine.kt" "$J/browser/WebFactory.kt" "$J/browser/WebSupport.kt" "$J/perf/SafariMode.kt" "$J/perf/ScriptBundle.kt" "$J/perf/Speculate.kt" "$J/yt/YtHub.kt" "$J/yt/YtWeb.kt"
rmdir "$J/brain" 2>/dev/null
rm -f "$A/brain.js" "$A/brain-rules.json" "$A/pw.js" "$A/safari.js" "$A/scrollguard.js" "$A/pwa.js" "$A/privacy.js" "$A/blocklist.txt" "$A/extensions/semo/content.js"
rm -f "$R/app/src/test/java/com/nova/browser/SiteRulesTest.java"
