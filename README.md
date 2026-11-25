<p align="center">
  <picture>
    <source media="(prefers-color-scheme: dark)" srcset="docs/images/logo-dark.svg">
    <img alt="Comixium" src="docs/images/logo-light.svg" width="380">
  </picture>
</p>

<p align="center">
  <b>comix as a real Android app.</b><br>
  The site you know and love, with native navigation, a better reader and no ads.
</p>

<p align="center">
  <a href="https://github.com/notsonata/comixium/releases/latest"><img alt="Download the APK" src="https://img.shields.io/badge/Download-APK-66E8FA?style=for-the-badge&logo=android&logoColor=66E8FA&labelColor=22282A"></a>
</p>

<p align="center">
  <img alt="Android 8.0+" src="https://img.shields.io/badge/Android-8.0%2B-3DDC84?logo=android&logoColor=white">
  <img alt="Kotlin" src="https://img.shields.io/badge/Kotlin-Jetpack%20Compose-7F52FF?logo=kotlin&logoColor=white">
  <img alt="No ads" src="https://img.shields.io/badge/ads-blocked-22282A?labelColor=66E8FA">
  <img alt="MIT License" src="https://img.shields.io/badge/license-MIT-22282A?labelColor=66E8FA">
</p>

| Home | Series | Reader | Search | More |
| :---: | :---: | :---: | :---: | :---: |
| <img src="docs/images/screen-home.png" width="160" alt="Home tab"> | <img src="docs/images/screen-series.png" width="160" alt="Series page"> | <img src="docs/images/screen-reader.png" width="160" alt="Full screen reader"> | <img src="docs/images/screen-search.png" width="160" alt="Search"> | <img src="docs/images/screen-more.png" width="160" alt="More tab"> |

## Why Comixium

[Mihon](https://mihon.app) (and Tachiyomi before it) is a great reader for pulling manga from many sites into one library. Comixium does one thing: it's comix, as an app.

- **Your comix account is your library.** Follows, reading history, collections and ratings are the ones on the site, so they match comix in your browser and on any other device. Mihon keeps its own library on the phone, separate from your comix account.
- **All of comix, not just the chapters.** Comments, ratings, collections, groups, community pages, notifications and the site's AniList and MangaBaka sync all work, because they're the site's own pages.
- **Nothing to install or wait on.** Mihon reads each site through an extension that pulls chapters out of its pages; when the site changes, the extension can stop working until someone updates it. Comixium shows comix's own pages, so new site features show up right away and reading keeps working. Only the app's own additions (tap zones, ad clean-up, layout fixes) can go stale, and a daily check flags that.
- **The reader you already know**, with app extras on top: tap zones, volume keys, full screen, movable controls.
- **No ads**, with nothing to set up.

Pick Mihon instead if you want one library across many sites, chapters downloaded for offline reading, or a library that doesn't need an account. Comixium needs a connection and reads from comix only.

## Features

**Feels like an app, not a website**

- Bottom tabs for Home, Browse, Library, History and More, each remembering where you were
- Native top bar, search with recent searches, and Back that goes where you expect (popups close first)
- Pull to refresh, proper offline and site-down screens
- Switches to comix.to automatically when comix.ws is down

**Reads better**

- Full screen chapters that keep the screen awake
- Double tap to show the controls, tap the edges to turn pages or scroll, pinch to zoom
- Tapping is fully adjustable: zones on the sides or top and bottom, three sizes, inverted, or off
- Volume keys turn pages, normal or inverted
- Reader controls can be moved, resized and trimmed: rail or bar; top or bottom; left, center or right; tick which buttons show
- Every reader option in one settings sheet, opened from the gear while reading

**No ads**

- Removes the popunder that opens on the first tap, and the promo banners
- Blocks known ad and tracker hosts
- Other sites only open when a link to them is actually tapped

**Looks like comix**

- Every page is drawn by comix itself, so it looks and works like the site
- App colours follow the theme picked in the site's settings
- Mobile optimizations tidy up the spots where the site still acts like a desktop page

**Handy extras**

- Share any series; long-press an image to save or share it
- Downloads and library backups work
- comix links from other apps can open in Comixium (on Android 12 and up, turn on its links under App info → Open by default)
- Hide the home page announcements and share bar

## Install

1. Download `comixium-<version>.apk` from the [latest release](https://github.com/notsonata/comixium/releases/latest).
2. Open it on your phone and allow installs from your browser or files app when Android asks.
3. Log in with your comix email and password.

**Updating:** install the newer APK over the old one. Login and settings are kept.

**Pre-releases:** newer builds may appear on the [releases page](https://github.com/notsonata/comixium/releases) as pre-releases before they've been tested on a phone. The latest release is always the newest tested one.

**Coming from a version before 1.7.0:** uninstall it first. 1.7.0 is signed with a new key, so Android won't install it over older versions. You'll need to log in again.

**Checking a download:** every APK from 1.7.0 on is signed with the certificate whose SHA-256 is `ba6780acf4966f6daa0e9829b6233eb8ac5ba153e66a3d58edb8237f7df281e9`. Android refuses updates signed with any other key.

Requires Android 8.0 or newer.

## FAQ

<details>
<summary><b>Is this an official comix app?</b></summary>
<br>
No. Comixium is an unofficial app. All content comes from comix.ws, shown by the site itself.
</details>

<details>
<summary><b>Why can't I sign in with Google?</b></summary>
<br>
Google blocks its sign-in inside apps that display web pages, so the button is hidden. Use your comix email and password instead.
</details>

<details>
<summary><b>comix.ws is down. Does the app still work?</b></summary>
<br>
Yes. It retries on comix.to automatically for the rest of the session. The two domains keep separate logins, so you may need to log in again there. The domain can also be switched by hand in More → App settings.
</details>

<details>
<summary><b>Something looks off after a site update</b></summary>
<br>
The app hooks into parts of the site, and a daily check flags when comix changes them, so fixes usually follow quickly. In the meantime, turning off <b>Mobile optimizations</b> in More → App settings shows the site's own layout.
</details>

<details>
<summary><b>Where are the settings?</b></summary>
<br>
While reading, the gear in the reader controls opens every reader option in one sheet: tapping, volume keys, zoom, full screen, controls layout and the site's own reader settings. More → App settings has the same reader options plus home page blocks, mobile optimizations, the back animation and its speed, ad blocking, domain and cache. The site's general settings (theme, content filter) open from More → Theme & content filter.
</details>

<details>
<summary><b>Does the app collect any data?</b></summary>
<br>
No. There is no analytics or tracking of its own. It only talks to comix.ws / comix.to and the image servers they use, plus any site opened from a tapped link, and it blocks known trackers.
</details>

## What's new

See [Releases](https://github.com/notsonata/comixium/releases) or [CHANGELOG.md](CHANGELOG.md).

## Building from source

See [docs/DEVELOPMENT.md](docs/DEVELOPMENT.md) for how the app works, building, tests, the daily site check and the release process.

## Credits

- Content: [comix.ws](https://comix.ws). Comixium is not affiliated with comix.
- Fonts: Poppins and JetBrains Mono, SIL Open Font License
- Icons: drawn from [Lucide](https://lucide.dev), ISC License
- Ad and tracker list: [StevenBlack/hosts](https://github.com/StevenBlack/hosts), MIT License

Licence texts are in [`third_party/`](third_party).

## License

Comixium's code is released under the [MIT License](LICENSE). The comix name, logo and site content belong to their owners and aren't covered by it.
