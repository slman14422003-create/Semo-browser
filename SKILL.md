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

## Perf pass (lag on GitHub, APKMirror, Cloudflare pages)
- tools/src/frames.js replaces polyfills.js, webcompat.js, shield.js; gen-ext.py emits main-frames.js and main-frames-ns.js (cfg.shield off), minified by tools/jsmin.py
- ES polyfills run only when a feature probe fails (none missing on Gecko 157); sub-frames skip the iframe fixer and touchstart listeners
- top-frame MAIN scripts grouped into bundles: main-core, main-page-<mask> (fit, lazy, boost, pop, heavy), main-misc-<mask> (ads, zoom, copy, draft), main-dark-1/2; at most 5 registrations (was up to 17)
- per-host bypass: tools/src/bypass.txt (host group[,group]) becomes excludeMatches per group
- background.js: blocking webRequest removed (permissions too); ads via static DNR ruleset rules-ads.json generated from tools/src/blocklist.txt; dataSaver image block via session rule; hello reports dnr availability
- one MutationObserver per world: tools/src/hub.js (MAIN bundles and isolated hub.js); page.js, pw.js, zoom, dark, webcompat subscribe; single idle flush
- page.js touchstart: cached ancestor scroll check, uncached chain resolved at idle; no getComputedStyle walk in the handler
- dark.js: no full-DOM querySelectorAll; added nodes queue plus capture-phase image load listener
- NovaGeckoView.onTouchEvent: detail result only on ACTION_DOWN, super path for the rest
- PageSampler: single capture after load, only when the extension sent no colour, two pixel rows read
- PageBars: wired from MainActivity; clipping now max minus hidden (visible bar height, as Fenix does); GeckoView no longer shrunk by the bar when dynamic toolbar is on
- PerfAb (debug only): per-group script switches, FINGERPRINTING flag, dynamic toolbar; Settings section perfab
- app/build.gradle.kts: preBuild runs tools/gen-ext.py when python3 exists

## Next
- Build with gradle and fix any compile errors
- Measure with the A/B section on GitHub and APKMirror (JankStats tag NovaJank)
- Decide whether to drive barCollapse from tab.barIsCompact (target is constant 0f in MainActivity)
- Move dp values into UiLayout.kt file by file, starting with Bars.kt
- Replace the Downloader sleep poll with a latch or condition

## Glass design system (this pass; static checks only, no gradle or device)
Plan: policy and math as Android-free Java with tests, one Compose glass layer, then apply to chrome, then settings and accent.

Done
- core/GlassPolicy.java (+GlassPolicyTest): FULL/LITE/FLAT; effective level is FLAT when Adaptive.level >= 1, Battery Saver (Adaptive.saver, now observable state) or Prefs.smoothAnim is off; unset pick = LITE, FLAT on weak devices
- ui/Glass.kt: Glass.level, GlassStyles (Pill, Bar, Sheet, Dialog), GlassSource + Modifier.glassSource (records Compose content once into a GraphicsLayer), Modifier.glass (live blur + saturation + 3/5-tap AGSL lens on Compose sources; cached snapshot for the page; flat tint fallback). Shaders, paths, brushes, effects are built in drawWithCache and the blurred layer is re-recorded only when position, size or level changes
- GeckoView is never sampled per frame: PageSampler takes the single existing post-load capture and perf/GlassSnap.kt turns it into a 48px blurred bitmap (BoxBlur.java +test) kept in BrowserTab.glassSnap; MainActivity publishes it as Glass.page and NovaTheme exposes it only to MainActivity as LocalGlassPage. No snapshot or FLAT = opaque flat tint
- Applied to: address pill (AddressSlot), EditCard and compact pill via liquidGlass, MenuSheet, tab switcher bottom bar (live source = tab grid), NovaDialog
- ui/GlowProgress.kt: glowProgress modifier (line + glowing head at FULL, shared RadialGlow shader, RTL aware) on the address pill
- ui/NavBloom.kt: NavBloomState/bloomHost/bloomAnchor (replaces Glow.kt; glides on a spring, dims and stretches mid-glide, lit pill, selected key while page menu is open), tabBloom on the current tab card, LensPill (leading/trailing edge springs) in GlassSegments
- ui/Accent.kt, AccentSheet.kt, core/AccentTone.java (+test): 6 palette accents + custom colour (Compose picker: hue slider, saturation/brightness square, quick picks, hex field), tone-corrected per light/dark; Prefs.accentColor, key accentc
- ui/ThemeMode.kt: single dark/light resolver used by NovaTheme, GeckoSettings.appIsDark, novaDialog; BarTint untouched and barIos still uses accented()
- Motion: GLIDE/BLOOM_HOLD/PROGRESS tokens, ms(), millis(), loop(), glideLead/glideTrail/bloom/rubber; Shimmer, Spinner, Overscroll no longer build raw tween/spring
- Haptics is now one object (tap, tick, confirm, heavy, glide, reject, toggle); rememberHaptics() returns Haptics.On
- Settings: new Appearance section (theme, accent, status bar, bottom bar, Effects) and Effects page (Glass quality, note when auto-lowered); strings in values and values-ar
- dp: new values are semantic in UiLayout.kt; every literal dp in touched files (Bars, Tabs, MenuSheet, Dialogs, Theme, Segmented, Shimmer, Spinner, Overscroll, Settings, MainActivity, Utils, new files) moved to UiLayout.dpN (value-named, generated)

Not verified
- nothing compiled; lens/blur look and RTL feel need a device. Check first: layer.record usage in Glass.kt, ModalBottomSheet with dragHandle = null, bloom positions in RTL
- dp literals remain in files not touched (IosKit 35, StartPage, PanelKit, others)

## MeeTube UI port to Compose (partial; reference: meetube-ui-ref.zip)
Plan: pure policy classes in Java with tests, Compose layers on top, then wire into Tabs.kt, StartPage.kt, MainActivity.kt, PullArc.kt, menus. Rules: no comments, no README, dp only in ui/UiLayout.kt, every animation through Adaptive.ms or Motion.enabled.

Done
- core/BarHidePolicy.java (+test): scroll direction hide/show, separate hide and show slop, lock after toggle, top always shows
- core/MagnetPolicy.java (+test): detach/attach hysteresis, pull factor, snapPull, flingCloses, hoverTarget
- core/PullHaptics.java (+test): threshold at pull fraction > 1, min gap between clicks
- the three tests were run with a stub JUnit through the JDK compiler module: 18 passed
- ui/UiLayout.kt: constants for magnet, swipe, reorder, bar hide, zoom, frost, shimmer, overscroll, launch hold, menu height; BarGeometry.hiddenPx takes hide and maxPx; menuMaxHeight()
- ui/Haptics.kt: threshold(view, engaged), also on Haptics.On
- ui/Motion.kt: bounce(), magnet(), drop() springs
- ui/Shimmer.kt rewritten: ShimmerClock (one Choreographer clock, runs only while a host is shown), one shared band brush, Modifier.shimmerHost() (window-coordinate band, SrcAtop), SkeletonRows, SkeletonTiles, SkeletonThumb, SkeletonReveal. Not compiled

Done (pass 2, static only, nothing compiled: no kotlinc, gradle or SDK was available)
- BrowserTab: noteScroll feeds BarHidePolicy into barHidden; revealBar(); resetScroll resets both; setCollapsed(on) no longer scroll driven
- MainActivity: barHide = animateFloatAsState(Motion.timed(BAR_HIDE_MS)), no hiding while editing, finding, typing, tabs open, no page, or dynamic toolbar off; translationY on the bottom bar; PageBars.set(max, hiddenPx(density, collapse, finding, hide, max)); revealBar on tab or blocked change
- PullArc.kt: PullHaptics on ACTION_MOVE with pull fraction, Haptics.threshold, reset on DOWN/UP/CANCEL; arc and PageRules untouched
- Overscroll.kt: RubberBand with relative limit (OVERSCROLL_MAX_SHARE of height), stretch from the pulled edge, Motion.bounce, fling share OVERSCROLL_FLING; rememberRubberBand applied to StartPage, MenuSheet, AccentSheet, NovaChoiceDialog, PageMenu (PageMenu column wrapped in heightIn(menuMaxHeight) + verticalScroll)
- StartPage: favorites tiles inside SkeletonReveal(Library.ready)
- SplashGate: drawn, veilDone, LaunchVeil (nova_bg, fades after revealed, blocks touches, failsafe reveal after 2x LAUNCH_HOLD_CAP_MS); MainActivity keep condition !ready || (!drawn && elapsed since ready < LAUNCH_HOLD_CAP_MS)

Done (pass 3, static only, nothing compiled: no gradle, kotlinc, SDK or network)
- Hand review of Shimmer.kt, Overscroll.kt (RubberBand node), LaunchVeil in SplashGate.kt, PageMenu scroll wrapper: imports, signatures and modifier order checked, no error found, still not compiled
- ui/UiLayout.kt: FROST_EXIT_SHARE, ZOOM_CARD_R
- ui/Frost.kt: FrostCache (RenderEffect per step, FROST_STEPS steps, rebuilt only when the FROST_BLUR pixel radius changes), Modifier.frost(clarity) (alpha plus cached blur, no blur when Adaptive.blurOk is false), FrostPresence (updateTransition, animateFloat, enter FROST_MS decelerate, exit FROST_MS * FROST_EXIT_SHARE accelerate, content removed when settled hidden), FrostGhostState/FrostGhost (old tab thumb frosting away over the new page)
- ui/TabZoom.kt: TabZoom (page coordinates, card coordinates by tab id, active run, hiding id), ZoomRun, LocalTabZoom, TabZoomGhost. Opening: ghost starts at the page rect, waits up to ZOOM_WAIT_FRAMES for the card, target card rect read live every frame. Closing: ghost starts at the card rect, target is the page rect. Corner radius 0 <-> ZOOM_CARD_R, fade ZOOM_FADE_MS at the end. Skipped when Motion.enabled is false, no thumb, blank tab, or page/card not found. Image follows tab.thumb live
- Tabs.kt: thumb box registers its coordinates (onGloballyPositioned), detaches on dispose, alpha 0 while its tab is the zoom target
- MainActivity: TabSwitcher in FrostPresence with LocalTabZoom provided; openTabs calls zoom.open; onSelect calls zoom.close for the picked tab; page box registers attachPage; FrostGhost inside the page box; onSwitch shows the old tab thumb as ghost when the index changes; TabZoomGhost above the switcher
- tools/project_check.py: new section 2.991 (ported files: no comments, no dp literals, no raw tween/spring, MainActivity wiring present)

Check first when a compiler is available
- verticalScroll(state, overscrollEffect = x) and LazyColumn(overscrollEffect = x) exist in the Compose version of BOM 2025.06.01? Not verified (used by Settings.kt, StartPage.kt, MenuSheet.kt, Dialogs.kt, AccentSheet.kt, Library.kt, Downloads.kt). If missing: bump the BOM or use Modifier.overscroll + scrollable
- Frost.kt: BlurEffect(r, r, TileMode.Clamp) constructor, Transition.animateFloat inside FrostPresence
- TabZoom.kt: localBoundingBoxOf(this, false) on the root coordinates, lerp(Rect, Rect, Float), ghost landing on the card while the grid pop-in is still running
- Shimmer.kt, Overscroll.kt node (LayoutModifierNode), LaunchVeil, PageMenu scroll wrapper

Done (pass 4, static only, nothing compiled: no gradle, kotlinc, SDK or network)
- ui/MagneticDrag.kt: MagneticDrag(scope, haptic, detachPx, attachPx, pull) over MagnetPolicy. position = finger * factor + gap; on detach/attach the gap is set to (previous position - new goal) synchronously and closed with animate(...Motion.magnet()) so the card never trails the finger; Haptics.threshold on both events; finish() stops the catch-up
- ui/TabReorder.kt: TabReorder (lift, drag, hover, edge scroll, drop) and tabCardGestures (one gesture per card)
  - tabCardGestures: wait up to the long press timeout. Horizontal slop first = swipe (consumes), vertical slop first = nothing consumed (grid scrolls), timeout = lift. Lift then drag until up; up without moving past touch slop = menu. The lift phase is in try/finally so a card disposed mid-drag still ends the reorder
  - displacement = snapPull(distance to slot, SNAP_RADIUS_SHARE * card width, SNAP_STRENGTH) applied to (finger - grab - slot) plus a gap that is closed with Motion.magnet() after each swap (computed from the target slot before the move, so no one-frame jump)
  - hover per frame: MagnetPolicy.hoverTarget on the dragged card centre with SWAP_INSET_SHARE; the same target must hold for Motion.millis(SWAP_GAP_MS) before onMove fires
  - edge scroll per frame: EDGE_SCROLL_ZONE_DP from the top and from the end of the content area (viewport minus afterContentPadding), speed EDGE_SCROLL_DP_S scaled by depth into the zone, via grid.scrollBy
  - lift: LIFT_SCALE and LIFT_SHADOW follow a lift Animatable (Motion.drop); on release the card settles from its last displacement with Motion.drop
- Tabs.kt: thumb slot is SkeletonReveal(ready = blank || thumb != null || !loading) with SkeletonThumb as skeleton; swipe close = MagneticDrag with MAGNET_* (pull MAGNET_PULL), close when detached or MagnetPolicy.flingCloses(velocity, SWIPE_FLING_DP_S, travel, SWIPE_FLING_MIN_DP), exit to SWIPE_EXIT_SHARE * width in SWIPE_OUT_MS then onClose, otherwise Motion.drop back; tilt SWIPE_TILT_DEG and depth SWIPE_DEPTH from the card offset; a grab during the return spring starts from where the card is
- Tabs.kt card modifier order: animateItem (placementSpec = null while lifted, all specs through Motion.soft), zIndex(1 while lifted), pointerInput (outside graphicsLayer so positions are not tilted or scaled), pressScale, graphicsLayer (translation, tilt, depth, lift scale, alpha, shadowElevation), tabBloom. The card no longer uses combinedClickable: tap is tapClickable, long press is handled by tabCardGestures, accessibility long click is a semantics onLongClick that opens the menu
- MainActivity: moveTab(fromId, toId) removes and inserts at the target index (the dragged card takes the target slot), then current = tabs.indexOf(the same BrowserTab object); TabSwitcher(onMove = ...)
- UiLayout: SWIPE_RETURN_WAIT_MS (was a literal 400 in Tabs.kt)
- tools/project_check.py: MagneticDrag.kt and TabReorder.kt join the ported-file rules; Tabs.kt and MainActivity wiring checks added
- REORDER_DETACH_DP, REORDER_ATTACH_DP, REORDER_PULL in UiLayout are not used by this pass (slot attraction uses snapPull). Use or delete them

Check first when a compiler is available
- everything from the pass 3 list, plus:
- TabReorder.kt: AwaitPointerEventScope.withTimeoutOrNull member and drag(pointerId) imports, PointerInputChange.consume() / isConsumed, VelocityTracker.addPointerInputChange, LazyGridState.scrollBy import, layoutInfo.afterContentPadding
- Tabs.kt: animateItem(fadeInSpec, placementSpec, fadeOutSpec) named parameters and nullable placementSpec in the BOM 2025.06.01 foundation, Modifier.zIndex inside LazyVerticalGrid items, graphicsLayer shadowElevation with clip = false
- RTL (the UI is Arabic): LazyGridItemInfo.offset.x must be the physical left of the card. If the reorder drag lands mirrored sideways on a device, that is the cause: convert with viewportSize.width - offset.x - size.width in TabReorder.slot and hover
- On device: swipe feel (72 dp detach click, fling close, tilt and depth), long press then drag (swap dwell, snap pull, edge scroll while the grid is longer than the screen), long press without moving opens the menu, a vertical drag on a card still scrolls the grid, level 2 and smoothAnim off give no animation (cards jump, no springs)

Remaining
1. Build with gradle and fix compile errors (not possible in the last three passes)
2. Optional: scroll the grid to the current tab on open so its card exists for the zoom
3. Optional: pinned tabs could be kept in front when reordering (not enforced, onMove moves any tab)

## Settings, start page, snackbar, sheets, font, storage pass (static checks only, no gradle or device)
Plan: pure Java policies with tests, Compose pieces, then wiring.

Done
- core/TextMatch.java (+test): case, alef, taa marbuta and diacritic insensitive match with original ranges
- ui/SettingsKit.kt: Bilingual lookup (I18n.en, its reverse, listed R.string ids), highlighted search results, jump to row with flash, SettingsHeader, SettingsNote, StartLayoutPage. Add new settings string ids to Bilingual or they match only the current language
- core/Settings.kt: grouped root list, new pages font, startpage, storage; parentOf() for back navigation
- core/StartLayout.java (+test), Prefs.startLayout (ConfStore key startlayout), sections fav, ai, privacy, shortcuts, recent, downloads; last two hidden by default
- core/SnackQueue.java (+test), ui/Snack.kt: Snacks and SnackHost in MainActivity and PanelActivity; toast() now routes to it; Retry on extension failure, Downloads on download start
- ui/NovaSheet.kt: shared handle, radius, scrim, M3 motion; used by MenuSheet, site settings (Bars.kt), download actions, AccentSheet
- ui/AppFonts.kt, Prefs.font: system, serif, condensed, monospace, casual (system families only)
- ui/StoragePage.kt: cache, completed downloads, site data (estimate from filesDir/mozilla) with confirm before clearing
- dp literals in touched files moved to ui/UiLayout.kt
- The three new tests pass with a stub JUnit

Check first when a compiler is available
- LocalActivity (activity-compose 1.10.1), BringIntoViewRequester, FontFamily(Typeface), StorageController.ClearFlags, ModalBottomSheet scrimColor
- On device: site data size path, snackbar above the bottom bar and keyboard, sheet opened from the address bar, RTL layout of the start page editor arrows
- Native AlertDialogs keep the system font

## First compile fixes (GitHub Actions log, release build)
- NovaSheet no longer exposes the experimental SheetState; NovaStatefulSheet is for callers that already opt in
- Glass.kt: import addOutline and drawOutline
- TabReorder.kt: import util.addPointerInputChange
- Not yet rebuilt
