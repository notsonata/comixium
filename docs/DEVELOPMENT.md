# Developing Comixium

## How it works

comix has no public API, and its private one is signed and encrypted by code that changes with
every deploy. So Comixium doesn't use it. Each tab is a WebView showing the real site, and
everything around the pages is native (Kotlin, Jetpack Compose):

- **Shell** (`app/src/main/java/.../ui`): bottom tabs, top bar, search, More and Settings screens, reader
  sheets, predictive back. Each tab keeps its own WebView and history.
- **Web engine** (`app/src/main/java/.../web`): tab state, link policy (external links open in a
  Custom Tab only when tapped), ad blocker, comix.ws → comix.to fallback, page-level Back
  (`BackStack`), downloads and file pickers.
- **Page script** (`app/src/main/assets/inject/comixium.js` + `comixium.css`), injected at document
  start into comix pages only:
  - hides the site header (the app clicks its hidden buttons for login and settings);
  - removes ads;
  - reports page state (URL, title, login, reader, theme colours) to the app;
  - adds reader touch controls and control layouts;
  - applies the mobile optimizations (`html[data-cx-mobile]`).

### Ad blocking

comix's ads are mostly first-party: an empty full-screen link its own bundle adds to `<body>`
(a popunder on the first tap) and promo banners. The page script removes the link and the
stylesheet hides the banners. Third-party ad and tracker hosts are blocked at the network level
using `assets/adblock/hosts.txt` (StevenBlack/hosts). Image requests are never blocked, because
chapter pages come from rotating CDN hosts.

## Build

Requirements: JDK 17+ and the Android SDK (`ANDROID_HOME`, or `sdk.dir` in `local.properties`).

```bash
./gradlew assembleRelease
```

The APK lands in `app/build/outputs/apk/release/`. Without the release key it comes out unsigned
(`app-release-unsigned.apk`); debug builds (`assembleDebug`) use the standard debug key.

## Google sign-in

Google refuses OAuth inside WebViews, so the flow is split (see `web/GoogleSignIn.kt`):

1. The site's button opens `/api/v1/auth/google/redirect`. comix sets its sign-in cookie and
   redirects to Google with a `redirect_uri` on a comix domain (currently always comix.to).
2. If that started on another domain, the tab restarts it on the return domain, so the cookie and
   the return match. Google's page then goes to the default browser.
3. Google sends the browser to `https://comix.to/api/v1/auth/google/callback?...`. Android hands
   the link to the app (on Android 12+ only once the user allows comix.to under Open by default;
   the app checks first and links to the setting). The app switches to that domain and loads the
   link in the Home tab, whose cookie jar has step 1's cookie, so comix signs the app in.

If the browser keeps the callback (no handoff), comix rejects it there with "Invalid OAuth state"
because the browser lacks step 1's cookie. comix checks the state before exchanging the code, so
the code is still unused: sharing that page to the app (ACTION_SEND) or Chrome's "Open in Comixium"
finishes the sign-in. The app shows how when the user comes back from the browser without it.

The daily site check covers the button, its start URL and the `redirect_uri`.

## Signing

The release key is never committed. `app/signing/release-cert.sha256` (public) is the SHA-256 of
its certificate; CI checks every release APK against it before publishing.

- **Locally:** put `release.jks` and `release.properties` in `app/signing/` (both gitignored).
- **CI:** the `SIGNING_BUNDLE` Actions secret is a base64 `.tar.gz` of those two files, unpacked
  into `app/signing/` before the build. Pull requests from forks don't get secrets, so they build
  unsigned and never publish. A push to `main` without a signed APK fails instead of publishing.

To make the secret from the two files:

```bash
tar -czf - -C app/signing release.jks release.properties | base64 -w0
```

Keep a copy of the key somewhere safe: GitHub can't show a secret again, and without the key
no update can install over the current release. Until 1.7.0 the key was committed to the repo,
so that old key is retired; never sign with it again.

## Tests

```bash
./gradlew testDebugUnitTest
```

JVM unit tests cover URL handling, Back, the ad blocker, link policy and page messages.
Robolectric loads the real packaged assets. Roborazzi renders the native screens to
`app/screenshots/`; regenerate them with:

```bash
./gradlew recordRoborazziDebug
```

## Site check

The app depends on parts of the site: header buttons, dialog and reader class names, the chapter
URL format, reader keyboard shortcuts and theme variables. `.github/workflows/site-check.yml`
runs daily:

- it loads the live site at phone size with the app's page script and checks each of these hooks;
- if one breaks, it opens an issue naming what changed and which feature it affects, and closes
  the issue once the check passes again;
- pages that GitHub's runners can't load past a Cloudflare challenge count as skipped, not failed.

Run it locally:

```bash
cd scripts/site-check && npm install && npx playwright install chromium && node check.mjs
```

## Releases

Every merge to `main` builds the signed APK and publishes a GitHub release named after the app
version: tag `v1.3.0`, title "Comixium 1.3.0", asset `comixium-1.3.0.apk`.

- **Version:** set `appVersion` in `app/build.gradle.kts`. `versionCode` is derived from it.
- **Notes:** the release notes are that version's section of [`CHANGELOG.md`](../CHANGELOG.md).
  Earlier releases are re-synced from the same file. CI fails if the current version has no
  section.
- **Same version:** a merge that doesn't bump the version rebuilds that version's release.

To ship a new version, bump `appVersion` and add a `## x.y.z` section to the changelog.
Changelog style: short headed bullet lists, no first person.

## Project layout

| Path | What it is |
| --- | --- |
| `app/src/main/java/.../ui` | Compose shell: tabs, top bar, search, More, Settings, reader sheets |
| `app/src/main/java/.../ui/theme` | Site design tokens (colours, Poppins, JetBrains Mono) |
| `app/src/main/java/.../web` | Web engine: tabs, Back, links, ad blocker, prefs, downloads |
| `app/src/main/assets/inject` | Page script and stylesheet injected into comix pages |
| `app/src/main/assets/adblock` | Ad and tracker host list |
| `app/signing` | Release key certificate fingerprint; the key itself stays out of the repo |
| `art/` | Logo source |
| `docs/` | README images and this guide |
| `scripts/` | Release notes extraction, site check |
| `.github/workflows` | Build and release (`android.yml`), daily site check (`site-check.yml`) |
| `third_party/` | Licences for bundled fonts, icons and the host list |
