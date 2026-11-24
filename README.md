# Comixium

A native Android app for [comix.ws](https://comix.ws) (mirror: comix.to).

Pages are rendered by the real site inside the app, so the look matches the site exactly and
nothing breaks when the site changes its private API. Everything around the pages is native:
bottom tabs, top bar, search, back gestures, pull-to-refresh, a full-screen reader, sharing,
and built-in ad blocking.

## Install

Download `comixium.apk` from the [latest release](../../releases/latest) and open it on your
phone (allow installs from your browser/files app when Android asks). New builds install over
old ones; your login and settings are kept.

## Ad blocking

comix's ads are mostly first-party: an invisible full-screen link that opens a popunder on your
first tap, plus promo banners. The injected script removes the popunder link and the stylesheet
hides the banners. Third-party ad and tracker hosts are blocked at the network level. External
sites only open (in a Custom Tab) when you actually tapped a link; scripted redirects are dropped.

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
| `.github/workflows/android.yml` | CI: tests, signed APK, rolling `latest` release on `main` |

## Third-party

Poppins and JetBrains Mono are bundled under the SIL Open Font License; see `third_party/fonts/`.
The ad/tracker host list is StevenBlack/hosts (MIT); see `third_party/adblock/`.
