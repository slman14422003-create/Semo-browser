# Semo-browser working notes

## Architecture facts
- Engine: GeckoView (USE_GECKO = true in engine/GeckoBridge.kt). The WebView stack (browser/WebFactory, WebSupport, WebCompat, Shield, assets/*.js at assets root) is not used at runtime.
- Page scripts for Gecko live in assets/extensions/semo (page.js sends the noPull flag).
- Downloads: Downloader (http/https, probe + segments), BlobDownload (data/blob via WebView, WebView only), StreamDownload (Gecko blob/data responses).

## Done in this task
- engine/EngineTab.kt: ContentDelegate.onContextMenu forwarded to EngineListener.onContextMenu.
- engine/GeckoBridge.kt: long-press menu for image, video, audio and links (download, open in new tab, copy, share). Hard-coded English strings replaced with L() keys that already exist in I18n.
- engine/NovaGeckoView.kt (new): reads PanZoomController scrollable directions per touch; pull-to-refresh is blocked unless the element under the finger cannot scroll up.
- download/Downloader.kt: probe retries without Range header when the server answers 4xx to the range request.

## Open items
- Pin geckoview version in app/build.gradle.kts (currently 157.+); use the version printed in the last build log.
- blob: images from long-press are not downloadable on Gecko (no native access to the blob).
- Remove the dead WebView stack only after a successful build, in small steps.
- Verify on device: long-press on image, image inside link, pull-down on pages with inner scrolling lists, iframes.
