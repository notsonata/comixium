# Changelog

Each section becomes the notes of the GitHub release for that version.

## 1.0.0

First release. I turned comix.ws into a proper Android app: the site still draws every page, so it looks exactly like it does in the browser, but everything around the pages is native.

- **Navigation**: bottom tabs for Home, Browse, Library, History and More, each keeping its own page and back history. A native top bar replaces the site header, with back, the page title, search, site settings and log in.
- **Search**: native search box with my recent searches; results open in Browse.
- **More**: my account pages, the site's browse shortcuts, the site's theme and content settings, and the app's own settings.
- **Ad blocking**: I strip the site's popunder (an invisible full-screen link that opened an ad on the first tap) and its promo banners, block third-party ad and tracker hosts, and drop redirects to other sites that I didn't tap.
- **Reader**: chapters open full screen, keep the screen awake, and the volume keys turn pages through the reader's own shortcuts, so my scroll distance and reading direction still apply.
- **Theme**: the app's colours follow whatever theme I pick in the site's settings.
- **Sharing and files**: a share button on series pages, long-press on any image to save, share or copy it, working file uploads and downloads (including the library backup), and comix links from other apps open here.
- **Reliability**: if comix.ws is unreachable, the app retries on comix.to for that session; otherwise it shows a proper offline / site-down screen.

Logging in works with email and password. I hid Google sign-in because Google blocks it inside apps.
