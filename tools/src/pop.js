(function () {
  try {
    if (!/^https?:$/.test(location.protocol)) return;
    if (/(^|\.)(youtube\.com|google\.[a-z.]+|googlevideo\.com)$/.test(location.hostname)) return;

    function trusted() {
      try {
        var T = __TRUSTED__, h = String(location.hostname).toLowerCase();
        for (var i = 0; i < T.length; i++) if (h === T[i] || h.slice(-T[i].length - 1) === '.' + T[i]) return true;
        return localStorage.getItem('__novaFix') === '1';
      } catch (e) { return false; }
    }
    if (trusted()) return;

    var CONSENT = '#onetrust-consent-sdk,#onetrust-banner-sdk,#CybotCookiebotDialog,#CybotCookiebotDialogBodyUnderlay,.cc-window,.cc-banner,' +
      '#cookie-law-info-bar,#cookieconsent,.cookie-consent,#cookie-consent,.cookie-banner,#cookie-banner,#gdpr-cookie-notice,.qc-cmp2-container,' +
      '#didomi-host,.fc-consent-root,#usercentrics-root,.osano-cm-window,#cmpbox,#cmpbox2,#iubenda-cs-banner,.klaro,.truste_overlay,' +
      '[id^="sp_message_container"],[id^="CookieBoxSaveButton"]';
    try {
      var psh = new CSSStyleSheet();
      psh.replaceSync(CONSENT + '{display:none!important}');
      document.adoptedStyleSheets = document.adoptedStyleSheets.concat([psh]);
    } catch (e) { }

    function unlock() {
      try {
        if (!document.querySelector(CONSENT)) return;
        [document.documentElement, document.body].forEach(function (el) {
          if (el && getComputedStyle(el).overflowY === 'hidden') el.style.setProperty('overflow', 'auto', 'important');
        });
      } catch (e) { }
    }
    document.addEventListener('DOMContentLoaded', function () { unlock(); setTimeout(unlock, 800); });
    window.addEventListener('load', function () { setTimeout(unlock, 400); setTimeout(unlock, 2500); });
  } catch (e) { }
})();
