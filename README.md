# Comixium

A native Android app for [comix.ws](https://comix.ws) (mirror: comix.to).

Pages are rendered by the real site inside the app, so the look matches the site exactly and
nothing breaks when the site changes its private API. Everything around the pages is native:
bottom tabs, top bar, search, back gestures, pull-to-refresh, a full-screen reader, sharing,
and built-in ad blocking.

## What's native

| | |
| --- | --- |
| Bottom tabs | Home, Browse, Library, History, More. Each keeps its own page and back stack; tapping the active tab again scrolls to top or returns to its start page. |
| Top bar | Replaces the site header: back, page title, search, site settings, log in (or notifications and profile when logged in). |
| Search | Native field with recent searches; results open in the Browse tab (`/browse?q=`). |
| More | Account pages, the site's Browse menu shortcuts, the site's theme/content settings, ad blocking toggle, domain switch, cache. |
| Library / History | Show a native log-in prompt when you're logged out (the site redirects those pages). |
| Reader | Chapter pages go full screen (bars hidden, swipe to peek) and keep the screen on. Touch controls: double tap the middle to show/hide the reader controls, tap the edges to turn pages (paged) or scroll a screen (long strip), pinch to zoom, volume keys to step through the chapter. The reader's help button opens a "Touch controls" sheet with all of this and its toggles. The reader's floating controls can be laid out as a rail, a full-width bar or a minimal pill, placed top/bottom and left/center/right, in normal or large size. |
| Sharing & files | Share button on series pages; long-press any image to save (Pictures/Comixium), share or copy its link; file pickers and downloads (including the site's in-memory backup export) work. |
| Links | comix.ws / comix.to links from other apps can open in Comixium (pick it in the "Open with" chooser). |
| Mobile optimizations | On by default (More → Settings): touch-friendly fixes to the site's layout, such as Library folders as chips, all sort options visible, bottom-sheet dialogs, no sticky hover or desktop scrollbars, no footer or breadcrumbs. Off shows the site's own mobile layout. |
| Home page | Announcements and the share bar have a ✕; More → Home page brings them back. |
| Theme | Native colours follow whatever theme you pick in the site's settings (Main, Dark, Light, Custom). |

Screenshots of the native parts (rendered on the JVM by Roborazzi) live in `app/screenshots/`;
regenerate them with `./gradlew recordRoborazziDebug`.

## Install

Download `comixium-<version>.apk` from the [latest release](../../releases/latest) and open it on your
phone (allow installs from your browser/files app when Android asks). New builds install over
old ones; your login and settings are kept.

## Ad blocking

comix's ads are mostly first-party: an invisible full-screen link that opens a popunder on your
first tap, plus promo banners. The injected script removes the popunder link and the stylesheet
hides the banners. Third-party ad and tracker hosts are blocked at the network level. External
sites only open (in a Custom Tab) when you actually tapped a link; scripted redirects are dropped.

## Versions

Each merge to `main` publishes a GitHub release named after the app version (tag `v1.0.0`,
title "Comixium 1.0.0", asset `comixium-1.0.0.apk`). Its notes are that version's section of
`CHANGELOG.md`; earlier releases are re-synced from the same file, so editing an old entry
updates its release too. To ship a new version, bump `appVersion` in `app/build.gradle.kts`
(`versionCode` follows from it) and add a matching `## x.y.z` section to the changelog; CI
fails without one. A merge that doesn't bump the version rebuilds that version's release.

## Site check

The app hooks into a few parts of the site (header buttons, dialog and reader class names,
chapter URL format, reader keyboard shortcuts, theme variables). `.github/workflows/site-check.yml`
runs every day: it loads the live site at phone size with the app's injected script and checks
each hook. If something changed, it opens an issue listing what broke and which app feature it
affects (GitHub emails you), and closes it once the check passes again. Run it locally with:

```bash
cd scripts/site-check && npm install && npx playwright install chromium && node check.mjs
```

## Build

Requirements: JDK 17+ and the Android SDK (`ANDROID_HOME` set, or `sdk.dir` in `local.properties`).

```bash
./gradlew assembleRelease
```

The APK lands in `app/build/outputs/apk/release/`. It is signed with the key in `app/signing/`,
which is committed deliberately: this is a single-user, sideloaded app and a stable key is what
lets updates install in place.

## Project layout

| Path | What it is |
| --- | --- |
| `app/src/main/java/.../ui/theme` | Site design tokens (colours, Poppins / JetBrains Mono) as a Compose theme |
| `app/src/main/java/.../web` | Web engine: tabs, link policy, ad blocker, domain fallback, page-state bridge |
| `app/src/main/assets/inject` | Script + stylesheet injected into site pages (ad removal, state reporting, helpers) |
| `app/src/main/assets/adblock` | Bundled tracker/ad host list (StevenBlack unified hosts, MIT) |
| `app/signing` | Release keystore and its properties |
| `.github/workflows/android.yml` | CI: tests, signed APK, versioned release on every merge to `main` |

## Third-party

Poppins and JetBrains Mono are bundled under the SIL Open Font License; see `third_party/fonts/`.
The ad/tracker host list is StevenBlack/hosts (MIT); see `third_party/adblock/`.
