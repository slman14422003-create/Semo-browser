package com.nova.browser

import android.webkit.WebView
import java.util.Collections

object SiteRules {
    val trustedSuffixes = arrayOf(
        "firebase.google.com", "firebaseapp.com", "web.app", "firebaseio.com", "cloud.google.com", "console.cloud.google.com",
        "cloudflare.com", "cloudflareaccess.com", "pages.dev", "workers.dev", "cloudflarestorage.com",
        "github.com", "github.dev", "gitlab.com", "bitbucket.org", "vercel.com", "vercel.app", "netlify.com", "netlify.app",
        "supabase.com", "supabase.co", "render.com", "onrender.com", "heroku.com", "herokuapp.com", "railway.app", "fly.io",
        "digitalocean.com", "amazon.com", "aws.amazon.com", "amazonaws.com", "azure.com", "azurewebsites.net", "microsoft.com",
        "microsoftonline.com", "live.com", "office.com", "atlassian.net", "atlassian.com", "slack.com", "notion.so", "figma.com",
        "stripe.com", "paypal.com", "auth0.com", "okta.com", "apple.com", "facebook.com", "linkedin.com", "x.com", "twitter.com",
        "discord.com", "openai.com", "chatgpt.com", "claude.ai", "anthropic.com", "huggingface.co", "expo.dev", "mongodb.com",
        "neon.tech", "sentry.io", "datadoghq.com", "codepen.io", "jsfiddle.net", "stackblitz.com", "codesandbox.io", "replit.com",
        "glitch.com", "glitch.me", "google.com", "googleusercontent.com", "gstatic.com"
    )

    val authSuffixes = arrayOf(
        "firebaseapp.com", "accounts.google.com", "apis.google.com", "challenges.cloudflare.com", "hcaptcha.com", "recaptcha.net",
        "login.microsoftonline.com", "login.live.com", "appleid.apple.com", "auth0.com", "okta.com", "cloudflareaccess.com",
        "js.stripe.com", "paypal.com", "arkoselabs.com", "geetest.com", "friendlycaptcha.com", "mtcaptcha.com"
    )

    private val trusted = HostMatcher(emptyArray(), trustedSuffixes)
    private val auth = HostMatcher(emptyArray(), authSuffixes)
    private val sslOk: MutableSet<String> = Collections.synchronizedSet(HashSet())
    private val sslAsked: MutableSet<String> = Collections.synchronizedSet(HashSet())

    fun fixed(host: String?): Boolean = !host.isNullOrBlank() && SiteStore.get(host.lowercase().removePrefix("www.")).fix == 1

    fun relaxed(host: String?): Boolean = !host.isNullOrBlank() && (fixed(host) || trusted.matches(host))

    fun thirdPartyCookies(host: String?): Boolean = relaxed(host)

    fun authHost(host: String?): Boolean = !host.isNullOrBlank() && auth.matches(host)

    fun allowSsl(host: String) { sslOk.add(host.lowercase()) }

    fun sslAllowed(host: String): Boolean = sslOk.contains(host.lowercase())

    fun shouldAskSsl(host: String): Boolean = sslAsked.add(host.lowercase())

    private val jsonCache: String by lazy { trustedSuffixes.joinToString(",", "[", "]") { "\"$it\"" } }

    fun json(): String = jsonCache

    fun ensureFlag(v: WebView, host: String) {
        if (!fixed(host)) return
        runCatching { v.evaluateJavascript("try{localStorage.setItem('__novaFix','1')}catch(e){}", null) }
    }

    fun setFlag(v: WebView?, on: Boolean) {
        val js = if (on) "try{localStorage.setItem('__novaFix','1')}catch(e){}" else "try{localStorage.removeItem('__novaFix')}catch(e){}"
        runCatching { v?.evaluateJavascript(js, null) }
    }
}
