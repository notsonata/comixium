# Changelog

Each section becomes the notes of the GitHub release for that version.

## 1.8.0

Google sign-in:

- Continue with Google is back: Google's sign-in opens in your default browser and returns you to the app signed in
- On Android 12 and up, allow Comixium to open comix.to links once; the app shows the way to the setting
- Google sign-in lives on comix.to, so the app switches to comix.to when you use it
- Google sign-in only opens in a browser; with no default browser set, pick one from a list of browsers
- If the browser keeps the sign-in and shows "Invalid OAuth state", share that page to Comixium to finish
- Share any comix link to Comixium to open it in the app

Crash reports:

- If Comixium closes unexpectedly, the next launch shows what went wrong, to copy or share; the report stays on your phone unless you share it

## 1.7.1

Reader settings:

- Redesigned: switches for on/off options, compact selectors for choices
- Grouped into Tapping, Screen & keys, and Controls
- Options that don't apply right now are greyed out, like tap zone size with tapping off
- Pick the control buttons shown with chips

## 1.7.0

New signing key:

- Uninstall Comixium before installing this version; Android won't install it over older ones
- Log in again afterwards, and set your app settings again

Theme & content filter:

- Opens as a full page instead of a bottom sheet
- Content preferences opens as its own page; Back returns to theme and content filter

## 1.6.0

Series page (mobile optimizations):

- Title sits next to the cover, with tags, rank and score below it
- Tags, rank and score move under the cover when the title is long

Top bar:

- Settings gear removed; theme and content filter live in More
- No search icon on Browse, which has its own search box

Theme & content filter:

- Opens as a bottom sheet that closes with a swipe down or Back

More:

- Shorter: account, browse shortcuts in a grid, settings and about
- App options moved to their own App settings page
- Notifications and Backup left the account list; they're in the top bar and on the profile page

App settings:

- Back animation can be turned off
- Back animation speed: fast, normal or slow
- Clear cache shows how much space the cache uses and how much was freed

## 1.5.0

Reader:

- Chapter list and settings sheets close by swiping down; no close buttons
- Comments open as a pull-out drawer from the bottom
- Controls bar stays centred when the progress bar is on the side
- Choose which control buttons to show with checkboxes
- Reading position stays put when something on the page loads or changes size

## 1.4.0

Reader settings:

- One settings button: the reader's gear opens a single sheet with every reader option
- Help button removed; its touch info is at the bottom of the settings sheet

Tapping:

- Tap to turn pages (paged) and tap to scroll (long strip) can each be turned off
- Tap zones on the left and right, or at the top and bottom
- Small, medium or large tap zones
- Inverted tap zones
- Show controls with a double tap, a single tap, or not at all

More reader options:

- Inverted volume keys
- Long-press image menu can be turned off

Navigation:

- Back closes open popups first: reader settings, chapter list, comments, menus and dropdowns
- No back gesture animation when Back only closes a popup
- No sideways stretch when a swipe reaches the edge of the page

## 1.3.0

Smoother:

- Back gesture follows the finger and settles faster
- Page titles crossfade instead of popping
- Loading bar animates smoothly
- Background tabs pause while hidden
- Removed always-running logo animations that cost scrolling smoothness
- Lighter page monitoring while scrolling

Mobile optimizations (on by default, More → Settings):

- Library folders shown as chips instead of a cut-off scrolling row
- All library sort options visible
- Library and History skip the profile card
- Profile stats fit the screen
- Popular groups counts no longer cut off
- Reader settings, chapter list and small dialogs open as bottom sheets
- No stuck hover highlights after a tap
- No scrollbars, site footer or breadcrumbs
- Home carousels without arrow buttons (swipe instead)
- Chip rows that scroll sideways fade at the edge to show there's more

Home page:

- Close button on announcements and the share bar
- Toggles for both in More → Home page

## 1.2.0

Navigation:

- Back from a series page opened from the reader returns to where the series was opened from
- Back leaves the reader in one step instead of going through every chapter read
- Back gesture shows a preview animation on Android 14+

Reader controls:

- Layout options: rail, bar or minimal
- Position options: top or bottom, left, center or right
- Large size option for bigger touch targets
- Controls stay clear of the progress bar
- Set from the reader's help button or More → Reader controls

## 1.1.0

New look:

- Now uses original comix logo for branding

Reader:

- Touch controls guide replaces the keyboard shortcuts help
- Double tap to show or hide reader controls
- Tap the left or right edge to scroll in long strip mode
- Pinch to zoom on chapter pages
- Edge taps and pinch zoom can be turned off in More → Reader

## 1.0.0

First release:

- comix.ws as a native Android app, with pages rendered by the site itself

Navigation:

- Bottom tabs for Home, Browse, Library, History and More
- Native top bar with back, page title, search, site settings and log in
- Native search with recent searches

Ad blocking:

- Removes the popunder and promo banners
- Blocks third-party ad and tracker hosts
- Blocks redirects to other sites that weren't tapped

Reader:

- Full screen, keeps the screen on
- Volume keys turn pages

Other:

- App colours follow the site theme
- Share button on series pages
- Long-press images to save, share or copy them
- File uploads and downloads, including library backups
- Opens comix.ws and comix.to links from other apps
- Falls back to comix.to when comix.ws is down
- Email and password log in (Google sign-in doesn't work inside apps)
