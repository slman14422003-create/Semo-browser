try{(function(){
(function () {
  try {
    if (!/^https?:$/.test(location.protocol)) return;
    if (/(^|\.)(youtube\.com|google\.[a-z.]+|googlevideo\.com)$/.test(location.hostname)) return;

    var cn = navigator.connection || {};
    var slow = !!cn.saveData || /(^|-)2g$/.test(cn.effectiveType || '');

    var seen = {}, count = 0, timer = 0, target = null;
    var SKIP_FILE = /\.(zip|apk|pdf|exe|mp4|mp3|mkv|rar|7z|iso|dmg|torrent)$/i;
    var SKIP_PATH = /(logout|signout|sign-out|log-out|delete|remove|unsubscribe|checkout|cart|payment|\/pay\b|confirm|activate|verify|download|install)/i;
    var noPrefetch = false;
    try {
      var T = ["firebase.google.com", "firebaseapp.com", "web.app", "firebaseio.com", "cloud.google.com", "console.cloud.google.com", "cloudflare.com", "cloudflareaccess.com", "pages.dev", "workers.dev", "cloudflarestorage.com", "github.com", "github.dev", "gitlab.com", "bitbucket.org", "vercel.com", "vercel.app", "netlify.com", "netlify.app", "supabase.com", "supabase.co", "render.com", "onrender.com", "heroku.com", "herokuapp.com", "railway.app", "fly.io", "digitalocean.com", "amazon.com", "aws.amazon.com", "amazonaws.com", "azure.com", "azurewebsites.net", "microsoft.com", "microsoftonline.com", "live.com", "office.com", "atlassian.net", "atlassian.com", "slack.com", "notion.so", "figma.com", "stripe.com", "paypal.com", "auth0.com", "okta.com", "apple.com", "facebook.com", "linkedin.com", "x.com", "twitter.com", "discord.com", "openai.com", "chatgpt.com", "claude.ai", "anthropic.com", "huggingface.co", "expo.dev", "mongodb.com", "neon.tech", "sentry.io", "datadoghq.com", "codepen.io", "jsfiddle.net", "stackblitz.com", "codesandbox.io", "replit.com", "glitch.com", "glitch.me", "google.com", "googleusercontent.com", "gstatic.com"], h = String(location.hostname).toLowerCase();
      for (var ti = 0; ti < T.length; ti++) if (h === T[ti] || h.slice(-T[ti].length - 1) === '.' + T[ti]) noPrefetch = true;
    } catch (e) { }

    function prefetch(a) {
      try {
        if (slow || noPrefetch || count >= 12 || !a || !a.href) return;
        var u = new URL(a.href, location.href);
        if (u.origin !== location.origin) return;
        if (u.href.split('#')[0] === location.href.split('#')[0]) return;
        if (a.target && a.target !== '_self') return;
        if (a.hasAttribute('download') || SKIP_FILE.test(u.pathname) || SKIP_PATH.test(u.pathname + u.search)) return;
        if (seen[u.href]) return;
        seen[u.href] = 1;
        count++;
        var l = document.createElement('link');
        l.rel = 'prefetch';
        l.href = u.href;
        l.as = 'document';
        (document.head || document.documentElement).appendChild(l);
      } catch (e) { }
    }

    document.addEventListener('touchstart', function (e) {
      var t = e.target;
      var a = t && t.closest ? t.closest('a[href]') : null;
      if (!a) return;
      target = a;
      clearTimeout(timer);
      timer = setTimeout(function () { prefetch(target); }, 90);
    }, { passive: true, capture: true });
    ['touchmove', 'touchend', 'touchcancel'].forEach(function (n) {
      document.addEventListener(n, function () { clearTimeout(timer); }, { passive: true, capture: true });
    });

    if ('IntersectionObserver' in window) {
      var paused = new WeakSet();
      var watched = new WeakSet();
      var io = new IntersectionObserver(function (es) {
        es.forEach(function (en) {
          var v = en.target;
          try {
            if (!en.isIntersecting) {
              if (!v.paused && v.muted && !v.controls && !v.ended) {
                paused.add(v);
                v.pause();
              }
            } else if (paused.has(v)) {
              paused.delete(v);
              var p = v.play();
              if (p && p.catch) p.catch(function () { });
            }
          } catch (e) { }
        });
      }, { rootMargin: '150px' });

      var watch = function () {
        try {
          var l = document.getElementsByTagName('video');
          for (var i = 0; i < l.length && i < 40; i++) {
            if (!watched.has(l[i])) {
              watched.add(l[i]);
              io.observe(l[i]);
            }
          }
        } catch (e) { }
      };
      document.addEventListener('DOMContentLoaded', watch);
      window.addEventListener('load', function () { setTimeout(watch, 400); setTimeout(watch, 3000); });
    }
  } catch (e) { }
})();

}).call(window)}catch(e){}
