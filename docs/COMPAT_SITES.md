# Compatibility checklist

Open each site in the browser, sign in where it applies, and note failures. If a site only works after "Fix this site", add its domain to `SiteRules.trustedSuffixes` and extend `SiteRulesTest`.

## Developer consoles
- console.firebase.google.com (sign in, open a project, Firestore data tab)
- dash.cloudflare.com (sign in with Turnstile, DNS tab, Workers tab)
- github.com, gitlab.com, vercel.com, app.netlify.com, supabase.com, dashboard.render.com
- console.cloud.google.com, console.aws.amazon.com, portal.azure.com
- stackblitz.com, codesandbox.io, codepen.io, replit.com

## Hosted apps and login popups
- A site using Firebase signInWithPopup (Google, GitHub)
- A site behind Cloudflare Access
- Stripe Checkout test page, PayPal sandbox
- Auth0 and Okta demo apps, Microsoft login, Sign in with Apple

## Challenge and bot checks
- Any page with Cloudflare Turnstile, hCaptcha, reCAPTCHA v2 and v3

## Language and script
- ar.wikipedia.org, he.wikipedia.org, fa.wikipedia.org, ur.wikipedia.org
- ja.wikipedia.org, ko.wikipedia.org, zh.wikipedia.org (simplified and traditional)
- hi, bn, ta, te, kn, ml, th, ka, hy, km, my, uk, bg, sr wikipedia
- A page with no lang attribute in each script above

## Maps and canvas
- maps.google.com, openstreetmap.org (Leaflet tiles), figma.com, excalidraw.com

## Self-signed and internal
- A site with an expired certificate (continue-anyway flow, once per host per session)

## YouTube
- studio.youtube.com: upload a file, camera permission prompt
- m.youtube.com: watch, rotate, fullscreen, PiP, background audio
