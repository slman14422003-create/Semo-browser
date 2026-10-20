# سجل المهام والتعديلات

## 2026-10-20 (2) - in-app mini player, audio-only, next/prev, YouTube status bar
- yt/YtAudio.kt (new): audio-only engine (NewPipe audio stream + MediaPlayer, runs on Work.media), autoplays the first related video when a track ends. Feeds YtMedia so the notification/lock screen work.
- yt/YtMiniBar.kt (new): in-app mini player bar (thumbnail, title, play/pause, next, close, progress). AppActivity reserves space for it (WebView is padded up) and tapping it reopens the video at the same second.
- YtMedia.pageChanged: in an installed app, leaving a video page while playing hands the sound over to YtAudio instead of stopping. Browser tabs keep the old behaviour.
- App options sheet: new "Audio only" row on video pages (hands over and goes to the YouTube home page).
- Next/previous: new `next`/`prev` commands in yt.js (__novaYtCtl), notification action + MediaSession skip actions.
- Status bar: YtWeb now samples the page colour (PageTint) and AppActivity falls back to YouTube's own header colour; options handle is a short hint, then invisible.
- Not compiled in the session; run a Gradle build first.

## 2026-10-20 - thread lanes, web-app manifest, cosmetic filter, YouTube warm-up
- core/Work.kt (new): named pools net / disk (single thread) / cpu / media + coroutine dispatchers (Work.netD...) + Work.ui. All 14 ad-hoc Thread() calls (MainActivity, AppActivity, WebEngine, Updater, Library, YtMedia, YtDownload mux) moved to a lane. Library read+save now share the single disk lane (no more two writers racing on library.tmp). NetImage loads on Work.netD (bounded) instead of Dispatchers.IO.
- pwa/PwaManifest.kt (new): on install, reads the site's Web App Manifest in the background (4.5s deadline, https only, size caps): short_name/name, best PNG icon (192px, saved in filesDir/pwa), orientation (default only if the user has not chosen), theme_color (stored as pwa_t_<host>). Pwa.togglePin pins after the fetch and falls back to the favicon. Removing the app deletes the stored icon.
- assets/cosmetic.js + Perf.installCosmetic: generic ad-container hiding at document start (only when Prefs.blockAds; skipped on YouTube/Google/Cloudflare/GitHub).
- YtWeb.create: preconnect to i.ytimg.com and yt3.ggpht.com.
- Not compiled in the session; run a Gradle build first. tools/project_check.py passes.

## 2026-10-17 (2) - auto reload on reconnect, installed-app bottom gap
- core/NetRetry.kt: on network available, reloads only tabs whose load failed (BrowserTab.failed set when the error page is shown; cleared by the next real navigation). Wired in MainActivity and AppActivity (start in onStart, stop in onStop). YtWeb and WebFactory mark failures.
- AppActivity: with gesture navigation the bottom system-bar inset was reserved as an empty strip below the page (the gap in the screenshot, worse after rotation). Now only top/sides are padded with gesture nav; 3-button nav keeps the bottom padding.
- Not compiled in the session.

## 2026-10-17 - rotation gap fix, installed-app features, motion/perf
- Rotation gap (YouTube): `yt.js` gets `__novaFit` (re-measures fixed bars/player, restores scroll, fires resize; also on orientationchange). `YtWeb.create` adds a layout-change listener (debounced 160ms) so ANY real size change notifies the page; `YtWeb.forceRemeasure` toggles the WebView height by 1px then back. `MainActivity` also refits non-YouTube pages after rotation and uses a crossfade rotation animation.
- Installed apps (`AppActivity`): handle at the top edge opens `AppOptionsSheet` (reload/share/open in Nova, per-app screen orientation, keep screen on, full screen, pull-to-refresh, remove app). Settings are per host via `AppPrefs` (nova.conf keys pwa_o_/pwa_k_/pwa_i_/pwa_r_). Pull-to-refresh added for non-YouTube apps. Launcher long-press shows the 4 newest installed apps (dynamic shortcuts, `Pwa.publishShortcuts`). Open/close activity transitions.
- Motion/perf: page switch no longer scales the live WebView (fade only); bottom bar slides with the emphasized curve; StartPage sections enter in a stagger (graphicsLayer only); rounder icons/search; app splash exit scales out.
- Not compiled in the session; run a Gradle build first. `tools/project_check.py` passes.

## 2026-10-16 - smart assistant, WebView support, smoothness audit
- New: summarize.js + SmartAssist.kt (on-device page summary, Ask AI via Google AI Mode), SmartCalc.java (address-bar calculator), NovaWebView.kt (selection toolbar actions), WebBoost.kt (slow-network image deferral), Strings.kt + res/values(-ar)/strings.xml (resource strings that follow Prefs.lang).
- Menus: Summarize page and Ask AI in the page menu; Close duplicate tabs in the tab overview menu.
- Manifest: PROCESS_TEXT filter (select text in any app, open with Nova). Prefs.smart toggle in Settings.
- Audit fixes: Suggest.local allocation and scan cap, Library filtering via derivedStateOf, stable LazyRow keys, earlier status-bar tint (onPageCommitVisible).
- tools/project_check.py: checks R.string parity (values vs values-ar), Arabic-free new code files, PROCESS_TEXT filter.
- Not compiled in the session; run a Gradle build first.

## 2026-10-14 — البصمة (EFF) + مكتبات دعم الصفحات واللغات + استقرار
- `assets/privacy.js` (إعادة كتابة): توحيد `platform`/`maxTouchPoints`/`hardwareConcurrency`، أبعاد الشاشة وdevicePixelRatio إلى قيم شائعة، إخفاء الخطوط غير الشائعة (measureText وقياس عناصر الاختبار)، وWebGL: حدود المعاملات والامتدادات وprecision وvendor/renderer بقيم شائعة، وزمن الصوت. `toString()` للدوال المعدّلة يُظهر الأصلية. فحص صفحة التحقق يخفّ بعد 5 ثوانٍ.
- `assets/polyfills.js` (جديد): Array.at/findLast/toSorted…، Object.groupBy/hasOwn، Set.union…، Promise.withResolvers/any، structuredClone، AbortSignal.timeout/any، crypto.randomUUID، URL.canParse… (تعمل فقط عند غياب الميزة).
- `assets/langfix.js` (جديد): يضبط `lang` للصفحات التي بلا lang من نص الصفحة (الصينية المبسّطة/التقليدية، اليابانية، الكورية، الهندية، التايلندية، العبرية، الفارسية، الأردية، الروسية…) فيختار المحرك نموذج الحروف الصحيح.
- `browser/WebCompat.kt`: حقن polyfills ثم webcompat ثم langfix. `core/Settings.kt`: 13 لغة جديدة لـ«لغة المواقع» في نهاية القائمة. `res/xml/locales_config.xml` وlocaleFilters: 23 لغة.
- `MainActivity.kt`: `onLowMemory` يحرّر تبويبات الخلفية كـ onTrimMemory. `proguard-rules.pro`: حذف Log.v/d من الإصدار.

## 2026-10-13 — إصلاح النافذة المنبثقة (PiP) لمشغّل يوتيوب فقط
- `assets/yt.js`: إعادة كتابة `__novaPip` (وسم بخاصية بدل class، إعادة تطبيق دورية، إلغاء خصائص الآباء، اختيار أفضل عنصر فيديو)، وإرسال `vw/vh` مع حالة التشغيل.
- `yt/YtMedia.kt`: حفظ أبعاد الفيديو (`vw/vh`) وتصفيرها عند الإيقاف.
- `yt/YtWeb.kt`: `onPip` يفعّل حماية التشغيل قبل نمط المنبثقة.
- `MainActivity.kt`: نسبة المنبثقة من أبعاد الفيديو + تحديثها عند تغيّرها؛ فشل الدخول يُبلَّغ بدل الصمت؛ إصلاح التقاط «إغلاق المنبثقة» خطأً بعد التوسيع؛ الدخول التلقائي لا يشترط التشغيل الجاري؛ خلفية سوداء أثناء المنبثقة؛ زر منبثق عائم لا يعتمد على وضع التطبيق.
- `pwa/SiteBar.kt`: `PipGlyph` صارت `internal` لاستعمالها في الزر العائم.
- `core/I18n.kt`: ترجمتان إنجليزيتان لرسالتي الفشل.
- لم يُمسّ أي كود خاص بالمتصفح العام؛ كل التعديلات محصورة بمشغّل يوتيوب والمنبثقة.

## 2026-10-13 — سلاسة الواجهة والتنقل + توفير البطارية والمعالج
- `perf/Smooth.kt` (Adaptive): البطارية ≤15% وغير موصولة بالشاحن تُدخل وضع التخفيف (أنيميشن أقصر، 60Hz، عدد تبويبات حيّة أقل) تلقائياً؛ يُقرأ الشحن ببث لاصق ويُحدَّث عند BATTERY_LOW/OKAY وتوصيل/فصل الشاحن.
- `MainActivity.kt`: تحرير الـ WebView للتبويبات الخلفية الخاملة (15 دقيقة، و5 عند التخفيف) مع حفظ السجل والتمرير؛ لا يمسّ المثبّتة ولا قيد التحميل ولا يوتيوب الجاري. وقت الاستخدام يُسجَّل عند مغادرة التبويب. حكم «منع السحب للتحديث» يُحفظ لكل رابط بدل تحليل الرابط مع كل حركة لمس أثناء التمرير.
- `download/DownloadService.kt`: القفل الجزئي (WakeLock) كان يُمسك 6 ساعات متواصلة؛ صار يُمسك فقط أثناء تنزيل فعلي ويُحرَّر عند الإيقاف المؤقت/الانتهاء. تحديث الإشعار كل 5ث بدل 1ث والشاشة مطفأة.
- `browser/Popups.kt`: مراقبة عنوان النافذة المنبثقة كل 1000ms بدل 400ms.
- `assets/yt.js`: قياس موضع الترجمة الدوري كل 1ث بدل 0.7ث ويتوقف مع إيقاف الفيديو.
- ملاحظة: ميزة تحرير التبويبات الخاملة قد توقف صوتاً يعمل في تبويب خلفي لموقع غير يوتيوب بعد المهلة؛ المهلة قابلة للتعديل في `MainActivity.kt` (المتغيّر `idle`).

## 2026-10-13 — كاش الصفحات + إصلاح تنزيل apk باسم .bin
- السبب: `URLUtil.guessFileName` يستبدل امتداد الملف بامتداد نوع المحتوى إن اختلفا؛ الخوادم تعيد apk بنوع octet-stream فيصير `.bin`، وتسجيل MediaStore بنوع عام قد يُلحق `.bin` أيضاً.
- `download/Downloader.kt`: دالتان `smartName` و`smartMime`: الاسم الحقيقي مقدَّم على نوع المحتوى، والبحث عن اسم apk داخل الرابط (`?file=x.apk`) للسكربتات والأنواع العامة، ونوع apk الصحيح (`application/vnd.android.package-archive`) يُسجَّل في MediaStore ويُستعمل عند الفتح (يفتح المثبّت). وإن أضاف النظام `.bin` يُعاد الاسم المطلوب. الملفات المنزَّلة سابقاً باسم `.bin` لا تتغيّر (تُعاد تسميتها يدوياً أو تُنزَّل من جديد).
- `core/Settings.kt`: «مسح الكاش عند كل تشغيل» صار معطّلاً افتراضياً (كان يمحو كاش HTTP وكود الصفحات والرسوميات مع كل فتح فتبدأ كل صفحة باردة). من فعّله يدوياً يبقى مفعّلاً.
- `MainActivity.kt`: عند الإيقاف يُقاس حجم الكاش في خيط خلفي (كل 10 دقائق كحدّ أقصى)، وعند التشغيل التالي يُنظَّف فقط إن تجاوز 300MB (`CacheCleaner.cleanLarge`) مع إبقاء كاش الشيفرة والرسوميات.
- `browser/WebSupport.kt`: `cacheFirst` (تحميل واحد يفضّل الكاش) و`prepareLoad` (بلا اتصال تُفتح الصفحة من الكاش). تُستعمل لاستعادة التبويبات المحرَّرة (غير يوتيوب) فتعود فوراً، ولكل تحميل يبدؤه التطبيق.

## 2026-10-13 — توافق Cloudflare/صفحات التحقق + الصفحات ذات الأكواد الضخمة + مربع البحث والحوارات
- السبب المرجّح لتعطّل Cloudflare: الحماية من البصمة (`privacy.js`) كانت تضيف ضجيجاً لـ Canvas/WebGL/الصوت وتبدّل `navigator.*` على صفحة التحقق وإطار Turnstile، فيفشل التحقق أو يدور. الآن: لا تدخّل إطلاقاً في نطاقات التحقق (challenges.cloudflare.com وhCaptcha وreCAPTCHA وDataDome…)، وفي صفحة التحقق نفسها تُعاد القيم الحقيقية بعد اكتشافها (`inChallenge`: عنوان الصفحة/`_cf_chl_opt`/سكربتات challenge-platform).
- `security/Shield.kt`: نطاقات التحقق ومسارات `/cdn-cgi/` لا تُحجب. `browser/WebCompat.kt`: عنوان صفحة التحقق يفعّل كوكيز الطرف الثالث مؤقتاً ويرفع حدّ كاشف حلقات إعادة التحميل من 8 إلى 24 ويمهل الصفحة وقتاً أطول، ثم تعود الإعدادات تلقائياً.
- `browser/WebSupport.kt`: عملية العرض غير المستجيبة كانت تُقتل بعد 10 ثوانٍ؛ صارت 30ث (60ث لصفحات التحقق) مع إشعار «الصفحة ثقيلة… يرجى الانتظار» عند 8ث.
- `assets/privacy.js`: الخطافات الثقيلة (measureText وgetImageData وtoDataURL/toBlob وreadPixels) تُطبَّق كاملة على أول استدعاءات ثم على واحد من كل N، فلا تُثقل صفحات الأكواد والمحررات والألعاب؛ وأُضيفت مواقع التطوير (github وgitlab وvscode.dev وstackblitz وcodesandbox وcodepen وreplit وغيرها) للحماية الخفيفة.
- `assets/smooth.js`: «وضع الصفحات الثقيلة» (DOM فوق 5000 عنصر أو أكثر من 20 كتلة `pre` أو كتلة فوق 60 ألف حرف): نص أسرع بلا ligatures/kerning، وتأجيل تخطيط كتل الأكواد البعيدة (`content-visibility`).
- `ui/Bars.kt` (مربع البحث): الحقل داخل كبسولة بنفس شكل شريط العنوان، زر مسح يظهر فقط مع وجود نص، وزر «لصق» عند الفراغ (يقرأ الحافظة عند الضغط فقط)، وبلا حروف كبيرة تلقائية. `core/Suggest.kt`: عند فتح الشريط قبل الكتابة تظهر آخر 6 صفحات مزارة، وحركة سلسة لتغيّر حجم القائمة.
- `core/Settings.kt` (الحوارات): نص سجل الأعطال وسجل الأمان قابل للتحديد والنسخ، وسجل الأعطال بخط ثابت العرض.
- `download/Downloads.kt`: سطر تفاصيل الملف صار «التاريخ • النوع • الموقع» فلا يُقطع التاريخ.
- `core/I18n.kt`: ترجمتان («لصق»، «الصفحة ثقيلة…»).
- لا ضمان بأن كل صفحة Cloudflare ستمرّ: قد يرفض Cloudflare متصفحات WebView بحسب تقييمه، لكن أسباب الفشل التي يتحكم بها التطبيق أُزيلت.
