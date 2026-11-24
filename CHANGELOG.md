# Changelog

Each section becomes the notes of the GitHub release for that version.

## 1.1.0

- **Touch controls in the reader**: the help button in the reader now opens my own "Touch controls" sheet instead of a list of keyboard shortcuts nobody can use on a phone. It lists every gesture and has the reader options right there.
  - Double tap the middle of the page to show or hide the reader controls.
  - In long-strip mode, tapping the left or right edge now scrolls up or down a screen (paged mode already turned pages that way). Quick repeated edge taps keep scrolling instead of being read as a double tap.
  - Pinch to zoom now works on chapter pages; the rest of the site stays locked like before.
  - Both new behaviours can be switched off in the sheet or in More → Reader.
- **New look**: I made the app's logo out of the comix.ws one. The wordmark now reads COMIXIUM (same letterforms, with a U drawn to match their O), and it follows my site theme colours like the original does. The launcher icon and splash screen use the frame-and-X emblem with a soft glow behind the X, and there's a matching themed icon for Android 13+.

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
