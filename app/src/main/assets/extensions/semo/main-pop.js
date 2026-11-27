try{(function(){
(function () {
  try {
    if (!/^https?:$/.test(location.protocol)) return;
    if (/(^|\.)(youtube\.com|google\.[a-z.]+|googlevideo\.com)$/.test(location.hostname)) return;

    function trusted() {
      try {
        var T = ["firebase.google.com", "firebaseapp.com", "web.app", "firebaseio.com", "cloud.google.com", "console.cloud.google.com", "cloudflare.com", "cloudflareaccess.com", "pages.dev", "workers.dev", "cloudflarestorage.com", "github.com", "github.dev", "gitlab.com", "bitbucket.org", "vercel.com", "vercel.app", "netlify.com", "netlify.app", "supabase.com", "supabase.co", "render.com", "onrender.com", "heroku.com", "herokuapp.com", "railway.app", "fly.io", "digitalocean.com", "amazon.com", "aws.amazon.com", "amazonaws.com", "azure.com", "azurewebsites.net", "microsoft.com", "microsoftonline.com", "live.com", "office.com", "atlassian.net", "atlassian.com", "slack.com", "notion.so", "figma.com", "stripe.com", "paypal.com", "auth0.com", "okta.com", "apple.com", "facebook.com", "linkedin.com", "x.com", "twitter.com", "discord.com", "openai.com", "chatgpt.com", "claude.ai", "anthropic.com", "huggingface.co", "expo.dev", "mongodb.com", "neon.tech", "sentry.io", "datadoghq.com", "codepen.io", "jsfiddle.net", "stackblitz.com", "codesandbox.io", "replit.com", "glitch.com", "glitch.me", "google.com", "googleusercontent.com", "gstatic.com"], h = String(location.hostname).toLowerCase();
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

}).call(window)}catch(e){}
