---
name: nova-safari-ui
description: Progress log and rules for the iOS Safari redesign of Nova Browser (Compose, package com.nova.browser). Read before touching any UI file.
---

# Status

Full UI pass done: every screen, color, and layout moved to iOS Safari (iOS 18 look). No compiler was available in the session, so the first thing to do next time is a Gradle build and fixing any compile error.

# Rules

- Never create README files.
- New code carries no comments.
- Every new L("...") string needs an entry in I18n.en. Run `python3 tools/project_check.py` after edits.
- New strings go in res/values and res/values-ar and are read with S(R.string.x); see skills/nova-browser-smart.
- Reuse the shared kit in ui/IosKit.kt; do not reintroduce Material look (no big radii, no tonal buttons, no ripples styling).

# Design tokens (ui/Theme.kt)

- IosLight / IosDark in LocalIos: system colors, label levels, fills, separators, chrome bar, menu, tabsBg, alert.
- Read with `val ios = LocalIos.current`.
- Radii: field 12dp, group 10dp, alert 14dp, sheet top 12dp, app tile 22.5% of size.
- Type: 34 / 28 / 22 / 20 / 17 / 15 / 13 / 12 / 11 sp, letterSpacing 0 (Arabic joining).

# Shared kit (ui/IosKit.kt)

IosIcon (Canvas glyphs: Back, Forward, Disclosure, Share, Book, Tabs, Reload, Close, Plus, Magnifier, Lock, Ellipsis, EllipsisCircle, Check, ShieldFill, ArrowUpLeft, Copy, PlusSquare, Printer, XCircleFill; Back/Forward/Disclosure/ArrowUpLeft auto-mirror in RTL), IosSwitch, IosCell, IosGroup, IosTile, IosDivider, IosNavBar, IosLargeTitle, IosTextButton, IosPillButton, IosButton, ListRow (IosPos via groupShape), IconCircle, hairlineDp.

# Screens

- Browser bar (ui/Bars.kt): bottom address field with aA menu, lock + host, reload/stop, 2dp blue progress along the field bottom; below it a 5-icon toolbar (back, forward, share, bookmarks, tabs). Edit mode shows field + blue Cancel. UiLayout.PILL = 96dp.
- aA page menu and share sheet: ui/MenuSheet.kt (PageMenu, MenuSheet).
- Start page: ui/StartPage.kt (Favorites grid, AI card, Privacy Report, grouped shortcuts).
- Tab overview: browser/Tabs.kt (2-column cards, bottom toolbar: +, count menu, Done).
- Suggestions: core/Suggest.kt. Find bar: ui/Bars.kt.
- Alerts and choice lists: ui/Dialogs.kt (UIAlertController style, 280dp).
- Settings: core/Settings.kt (large title, grouped lists, colored icon tiles, detail + chevron for short values).
- Library, Downloads, Passwords, Updater dialog, YouTube download sheet, PWA SiteBar restyled in place.
- Resources: iOS palette in values/values-night colors, dialog backgrounds 14dp, launcher icon blue gradient with white glyph, error page restyled in core/Utils.kt.

# Round 2 additions

- Status bar tint: BrowserTab.topColor sampled by browser/PageTint.kt (theme-color meta, else body/html background) on page finish and 700ms later; MainActivity paints the status bar strip with it, flips icon contrast by luminance, tap on strip scrolls to top. Start page uses grouped background.
- Blue 12-spoke NovaSpinner now shows in the address field and tab card while loading, in pull-to-refresh and loading rows.
- Long-press back/forward lists history (copyBackForwardList); long-press tabs button gives New Tab / Close Tab.
- Download arrow appears in the address field while downloads are active.
- Tab cards swipe horizontally to close.

# Known gaps and next steps

- Address bar does not collapse on scroll (would need WebView scroll listener and animated insets).
- No zoom transition from page to tab grid (scale + fade used).
- Switch thumb mirrors in RTL by design.
- Verify visually on device: aA menu width, tab card aspect (0.74), glyph stroke weights.
