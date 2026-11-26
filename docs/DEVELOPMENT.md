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
  - adds download buttons to chapter rows and the reader controls (`setDownloads` paints their state),
    and a Download button to series pages (it reads the group filter's names out of sight);
  - reports reaching the end of a chapter (`readEnd`), for "Delete after reading".

### Downloads

Chapter images and data come from an encrypted API, so downloads let the site's own reader do the
work, and save enough of the page that the same reader runs offline
(`app/src/main/java/.../offline`):

1. **Collect** (`PageCollector` + `assets/inject/collect.js`): a WebView placed behind the app's
   screens loads the chapter. It has to be attached and drawn, just covered: the reader fills in a
   page's `<img>` only as it scrolls near, which needs frames. The script, injected only there,
   says hello and gets its job from the app (a reply on the same message channel). For a chapter
   it shows a long strip (it overrides the `settings_v2` read without saving it), drops writes to
   `reader.progress*`, scrolls page by page and reports every `.rpage-page img` address. Then it
   reaches the end so the comments load, and sends what the reader needs offline: the page itself
   (fetched again), the scripts, styles and Latin fonts it used, and the chapter data and comment
   threads (asked for again by the addresses the page used; the chapter data keeps its `x-enc`
   header). The app drops non-GET calls to `/api/v1/user/` (progress), so the visit isn't
   recorded as reading. Every image request in this WebView gets a 1x1 placeholder.
   The site reports pages whose `fetch`, `XMLHttpRequest.prototype.open` or `JSON.parse` were
   replaced (`v=2&w=…` on its API calls), so the script wraps none of them; `Storage` isn't
   reported. The site check verifies `v=0`.
2. **Fetch** (`PageFetcher`): the images download directly, three at a time across chapters,
   while the next chapter is prepared. The image hosts need no cookies or referrer.
3. **Store** (`DownloadStore`): `files/downloads/<series>/<chapter id>/` holds the images
   (`001.webp`…), `page.html`, `images.txt` (each page's image path), the API answers and
   `chapter.properties` (label, group, next chapter number, read). Scripts and styles are shared by
   all chapters in `_assets/` and pruned when nothing uses them. Unfinished work resumes on the
   next launch.
4. **Read** (`OfflineServer`, used by every tab's `shouldInterceptRequest`): image paths
   (`/hi/…`, the same on every host and stable over time) and saved scripts are always answered
   from disk. A downloaded chapter's page comes from disk when offline, or always in Library →
   Downloaded (`DOWNLOADED_PAGE` tab, `preferOffline`). Its chapter data comes from disk only for a
   page that came from disk (it has to match that page's scripts) or offline; comments only
   offline. The app's page script runs on it as on any chapter, so the reader UI is the same.
   Offline, the reader's chapter list is empty (paged API, not saved); Prev/Next work between
   downloaded chapters.

**Series downloads** (`SeriesWalk`): the series' chapter list is paged, and the site flags fast
paging without taps, so a series download never pages it. It walks one number at a time: the series
page's chapter search (`number=` on page 1) lists every group's version of a number; the first
preferred group's version is queued (or the number is skipped when none has it), and that chapter's
Next button (`.rpage-chap-ending__nav-btn`, "Next · Ch. 186"; a bare "Next" on the newest chapter)
gives the next number. "All" starts from the oldest-first list; "Unread" from the number on the
series page's Read/Continue button. The `peek` job reads the next number of a chapter that isn't
saved.

**Read to the end**: the page script reports when the reader's chapter ending comes into view; with
"Delete after reading" on, the chapter is deleted once no tab shows it.

Collecting pauses when the app leaves the screen (no frames), and resumes when it's back. The
site check runs `collect.js` against live pages and opens the saved chapter with the network cut.

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
- **Pre-releases:** a version goes out as a pre-release until it's listed in
  [`.github/tested-versions`](../.github/tested-versions), which happens only after that build
  has been tested on a phone. "Latest" (the README's download link) is always the newest listed
  version. Promoting a version is a one-line change to that file; the next merge to `main`
  updates the release. Bump the version for every change to the app, so a tested version never
  picks up untested code.

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
