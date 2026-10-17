---
name: nova-browser-smart
description: Log and rules for the smart-assistant, WebView-support and smoothness pass on Nova Browser (package com.nova.browser). Read before touching Suggest.kt, WebFactory.kt, NovaWebView.kt, WebBoost.kt, SmartAssist.kt or summarize.js.
---

# Status

Pass finished without a compiler (none in the session). First step next time: run a Gradle build and fix any compile error, then run `python3 tools/project_check.py`, then verify on device the items under "Verify on device".

# Rules (user preferences)

- Never create README files.
- Code is raw: no comments and no Arabic text inside code files (Kotlin, Java, JS). Arabic UI text lives only in `res/values-ar/strings.xml`; English in `res/values/strings.xml`. Read strings with `S(R.string.name)` (core/Strings.kt follows Prefs.lang, not the system locale).
- Old files keep their `L("...")` strings and the `I18n.en` table; do not migrate them unless asked.
- After each task: update this file and `docs/TASKS.md`.

# What was added

- `assets/summarize.js`: on-device extractive summary (TF-IDF over sentences, title and position boost, near-duplicate filter, keywords, reading time). Language agnostic: no stopword lists, Arabic light stemming through unicode escapes, CJK bigrams. Returns a JSON string, nothing leaves the device.
- `core/SmartAssist.kt`: summary dialog, Ask AI prompt builders (Google AI Mode URL from `AI_MODE_URL`), translate and search URLs for selected text, tab duplicate key.
- `core/SmartCalc.java`: safe recursive-descent calculator for the address bar (no eval). Covered by `SmartCalcTest`. Handles Arabic-Indic digits, x and division signs, thousands commas, percent, right-associative power; rejects dates and plain numbers.
- `browser/NovaWebView.kt`: WebView subclass that adds Ask AI, Translate and Search to the text-selection toolbar (ActionMode.Callback2 wrapper). Used only by `createWebView`; YouTube keeps its own WebView.
- `browser/WebBoost.kt`: slow-connection detection (downstream estimate up to 1500 kbps) that defers images until progress reaches 70% or the page finishes. Skipped when data saver is on.
- Page menu: Summarize page, Ask AI about this page (MenuSheet.PageMenu, wired in MainActivity). Tab overview menu: Close duplicate tabs (keeps pinned, then the current tab, then the first).
- Address bar: instant calculator row (kind 3 in `Sug`), tap copies the result.
- Intent filter `PROCESS_TEXT`: selecting text in any app offers Nova; handled in `DefaultBrowser.urlFrom`.
- Setting: `Prefs.smart` (key `smart`, default on) controls selection actions, menu items, calculator row, duplicate cleanup and image deferral.

# Audit fixes

- `Suggest.local`: no per-entry string concatenation and lowercase; history scan capped at 2500 entries.
- `Library` panel: bookmark, reading-list and history filtering moved to `derivedStateOf`; history rows built once per result.
- `MenuSheet.ShareApps`: stable keys for the LazyRow.
- `WebFactory`: `onPageCommitVisible` samples the page color earlier so the status bar tint changes sooner.

# Verify on device

- Selection toolbar shows the three new items on Android 15 and above and does not break copy/paste handles.
- Summary dialog on an Arabic article, an English article and a page without paragraphs (should toast the no-summary message).
- Slow-network deferral: throttle to 2G, images appear after the page body.
- Library with a few thousand history entries: typing in the search box stays smooth.

# Known gaps and next steps

- No LLM API is bundled; AI actions hand off to Google AI Mode. A direct model call would need a key and a settings screen.
- Selection inside iframes is not read by the toolbar actions (top-frame selection only).
- Downloads panel still re-sorts on every progress tick; wrap in `derivedStateOf` if it shows jank with many tasks.
