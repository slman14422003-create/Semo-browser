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

- engine/NovaGeckoView.kt: TextureView backend so Compose blur (menuBlur) also applies to the page (SurfaceView ignores RenderEffect).
- assets/extensions/semo/page.js: tint() now uses meta theme-color, then the element at the top edge, then html/body background, then white (or dark gray for color-scheme dark) so the status bar never falls back to the app theme.

- download/Downloads.kt: viewUri() maps the MediaStore Downloads URI to the Images/Video/Audio collection URI (same row id) before ACTION_VIEW, used by openFile and the done-notification.

## Open items
- Black viewer when opening a saved data: image (download.jpg) from the app; the file itself is fine in the gallery. Suspected cause: gallery viewer cannot read the Downloads-collection URI; mitigated by viewUri(). Confirm on device.
- Popup menu shows ghost rounded rectangles on its sections; needs a look at the menu content composables.
- Pin geckoview version in app/build.gradle.kts (currently 157.+); use the version printed in the last build log.
- blob: images from long-press are not downloadable on Gecko (no native access to the blob).
- Remove the dead WebView stack only after a successful build, in small steps.
- Verify on device: long-press on image, image inside link, pull-down on pages with inner scrolling lists, iframes.
