// Daily check that comix still has everything Comixium hooks into.
// Loads the live site in Chromium at phone size with the app's injected page script (the same
// script and stylesheet the app ships) and checks each dependency. Prints a Markdown report;
// exits 1 if any check fails on a page that loaded, 2 if the site couldn't be reached at all.
// GitHub's runners sometimes get Cloudflare's "checking your browser" page: the script waits for it
// to clear and retries; checks on pages that stay behind it are reported as skipped, not failed.
//
// usage: node scripts/site-check/check.mjs [--base https://comix.ws]
import { chromium } from 'playwright';
import { readFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const root = join(dirname(fileURLToPath(import.meta.url)), '..', '..');
const inject = join(root, 'app/src/main/assets/inject');
const BASE = (process.argv.includes('--base') ? process.argv[process.argv.indexOf('--base') + 1] : 'https://comix.ws').replace(/\/$/, '');
const pageScript = readFileSync(join(inject, 'comixium.js'), 'utf8')
  .replace('__COMIXIUM_CSS__', JSON.stringify(readFileSync(join(inject, 'comixium.css'), 'utf8')));

const results = [];
let section = '';
let blockedSection = false;
const check = (name, ok, detail = '', affects = '') =>
  results.push({ section, name, ok: !!ok, skipped: blockedSection, detail, affects });
const wait = ms => new Promise(r => setTimeout(r, ms));

const browser = await chromium.launch();
const ctx = await browser.newContext({
  userAgent: 'Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0 Mobile Safari/537.36',
  viewport: { width: 412, height: 915 }, isMobile: true, hasTouch: true,
});
await ctx.addInitScript({ content: 'window.__msgs=[];window.ComixiumNative={postMessage:function(s){window.__msgs.push(JSON.parse(s));}};' + pageScript });
const page = await ctx.newPage();
const errors = [];
page.on('pageerror', e => { if (/comixium/i.test(e.stack || '')) errors.push(e.message); });
const lastState = () => page.evaluate(() => [...window.__msgs].reverse().find(m => m.t === 'state') || null);

const challenged = () => page.evaluate(() =>
  /__cf_chl/.test(location.href) ||
  /just a moment|attention required|verify(ing)? you are human|checking your browser/i
    .test(document.title + ' ' + ((document.body && document.body.innerText) || '').slice(0, 400)) ||
  !!document.querySelector('#challenge-form, #challenge-running, iframe[src*="challenges.cloudflare.com"]:not(.auth iframe)'),
).catch(() => true);

/**
 * Loads a page, waiting out Cloudflare challenges. Returns true if the real page loaded.
 * Bounded so a runner stuck behind challenges can't run for long: two attempts per page, and once
 * the overall budget is spent, the remaining pages are skipped.
 */
const started = Date.now();
const BUDGET_MS = 9 * 60 * 1000;
async function open(path) {
  for (let attempt = 0; attempt < 2 && Date.now() - started < BUDGET_MS; attempt++) {
    await page.goto(BASE + path, { waitUntil: 'domcontentloaded', timeout: 30000 }).catch(() => {});
    for (let i = 0; i < 15 && await challenged(); i++) await wait(1000); // challenges usually clear in a few seconds
    if (!(await challenged())) {
      await page.waitForLoadState('networkidle', { timeout: 15000 }).catch(() => {});
      await wait(1500);
      blockedSection = false;
      return true;
    }
    await wait(5000);
  }
  blockedSection = true; // checks in this section are reported as skipped
  return false;
}

try {
  // --- Home -----------------------------------------------------------------------------------
  section = 'Home';
  await open('/');
  check('App page script runs', await page.evaluate(() => !!window.__comixium), errors.join('; '), 'everything below');
  const diag = await page.evaluate(() => JSON.stringify({
    url: location.href, title: document.title,
    top: [...(document.querySelector('#app-root') || document.body).children].slice(0, 6).map(e => e.tagName + '.' + e.className).join(' '),
    css: document.styleSheets.length, bg: getComputedStyle(document.documentElement).getPropertyValue('--bg'),
    text: document.body.innerText.replace(/\s+/g, ' ').slice(0, 160),
  }));
  check('Header `.topnav`', await page.$('.topnav'), diag, 'native top bar replaces it; site header would show twice');
  check('Header hidden by app stylesheet', await page.evaluate(() => getComputedStyle(document.querySelector('.topnav') || document.body).visibility === 'hidden'), '', 'double header');
  check('Log in button `.topnav .login-btn`', await page.$('.topnav .login-btn'), '', 'native Log in button, logged-in detection, Library/History prompt');
  check('Settings button `.topnav .settings button`', await page.$('.topnav .settings button'), '', 'More → Theme & content filter');
  const st = await lastState();
  check('Page state reaches the app', st, '', 'titles, login state, theme sync');
  check('Theme variables (--bg, --accent, --text)', st && st.theme && st.theme.bg && st.theme.accent && st.theme.text, st ? JSON.stringify({ bg: st.theme.bg, accent: st.theme.accent }) : '', 'native colours follow the site theme');
  check('Logged-out state detected', st && st.auth === 'out', st ? `auth=${st.auth}` : '', 'Library/History log-in prompt');
  await wait(3500); // the site adds its popunder link every 3 s
  check('No full-screen popunder link left on the page', await page.evaluate(() => ![...document.querySelectorAll('body > a')].some(a => !a.textContent.trim() && getComputedStyle(a).position === 'fixed' && getComputedStyle(a).display !== 'none')), '', 'ad blocking');
  check('Promo banner hidden', await page.evaluate(() => ![...document.querySelectorAll('.home-banner, a[href*="utm_source=comix"]')].some(e => e.offsetParent !== null)), '', 'ad blocking');

  section = 'Mobile layer';
  check('Mobile optimizations active (`data-cx-mobile`)', await page.evaluate(() => document.documentElement.hasAttribute('data-cx-mobile')), '', 'mobile optimizations');
  check('Site hover rules moved under (hover: hover)', await page.evaluate(() => { let bare = 0; const walk = (rules, inHover) => { for (const r of rules) { if (r.type === 1 && r.selectorText && r.selectorText.includes(':hover') && !inHover) bare++; else if (r.cssRules) walk(r.cssRules, inHover || /hover/.test(r.conditionText || '')); } }; for (const s of document.styleSheets) { try { walk(s.cssRules, false); } catch (e) {} } return bare === 0; }), '', 'no sticky hover after taps');
  check('Footer `footer.footer` hidden', await page.evaluate(() => { const f = document.querySelector('footer.footer'); return !!f && f.offsetParent === null; }), '', 'footer hidden in the app');
  check('Announcements `aside.announce` get a close button', await page.$('aside.announce .cx-close'), '', 'dismissible announcements');
  check('Share bar `.share-block` gets a close button', await page.$('.share-block .cx-close'), '', 'dismissible share bar');
  check('Carousel arrows `.section__controls .nav-btn` hidden', await page.evaluate(() => { const b = document.querySelector('.section__controls .nav-btn:not(.nav-btn--kebab)'); return !!b && b.offsetParent === null; }), '', 'cleaner home carousels');

  section = 'Dialogs';
  await page.evaluate(() => window.__comixium.openLogin()); await wait(800);
  check('Log in dialog opens (`.auth`)', await page.$('.auth'), '', 'native Log in button');
  check('Google sign-in hidden (`.auth__socials`)', await page.evaluate(() => { const s = document.querySelector('.auth__socials'); return !s || s.offsetParent === null; }), '', 'Google button would show and fail');
  check('Dialog reported as open', (await lastState())?.dialog === true, '', 'Back closes dialogs');
  await page.evaluate(() => window.__comixium.closeDialog()); await wait(600);
  check('Log in dialog closes (`.auth__close`)', !(await page.$('.auth')), '', 'Back closes dialogs');
  await page.evaluate(() => window.__comixium.openRegister()); await wait(900);
  check('Create-account link (`.auth__link`)', await page.evaluate(() => /create your account/i.test((document.querySelector('.auth__title') || {}).textContent || '')), '', 'More → Create an account');
  await page.evaluate(() => window.__comixium.closeDialog()); await wait(500);
  await page.evaluate(() => window.__comixium.openSettings()); await wait(700);
  check('Site settings dialog (`.settings-dialog`)', await page.$('.settings-dialog'), '', 'More → Theme & content filter');
  check('Site settings fill the page (`.settings-dialog__panel`)', await page.evaluate(() => { const p = document.querySelector('.settings-dialog__panel'); if (!p) return false; const r = p.getBoundingClientRect(); return Math.abs(r.top) < 2 && Math.abs(r.height - innerHeight) < 2; }), '', 'Theme & content filter page');
  await page.evaluate(() => document.querySelector('.settings-advanced-row')?.click()); await wait(700);
  check('Content preferences view has a back button (`.settings-dialog__back`)', (await lastState())?.sub === 'Content preferences', String((await lastState())?.sub), 'Content preferences title in the top bar, Back to the main settings');
  await page.evaluate(() => window.__comixium.closeDialog()); await wait(600);
  check('Back from Content preferences returns to the main settings', await page.$('.settings-dialog .settings-advanced-row'), '', 'Back inside Theme & content filter');
  await page.evaluate(() => window.__comixium.closeDialog()); await wait(500);

  // --- Series page ----------------------------------------------------------------------------
  section = 'Series page';
  const titleHref = await page.evaluate(() => (document.querySelector('a[href^="/title/"]:not([href*="-chapter-"])') || {}).getAttribute?.('href'));
  check('Series links look like `/title/<slug>`', titleHref && /^\/title\/[^/]+\/?$/.test(titleHref), titleHref || 'none found', 'series detection (share button)');
  if (titleHref) {
    await open(titleHref);
    check('Series header parts (`.mpage__hero-inner`, `.mpage__poster`, `.mpage__title-row`)', await page.$('.mpage__hero-inner .mpage__poster') && await page.$('.mpage__hero-inner .mpage__title-row'), '', 'title beside the cover');
    check('Title sits beside the cover', await page.evaluate(() => { const p = document.querySelector('.mpage__poster'), t = document.querySelector('.mpage__title-row'); if (!p || !t) return false; const a = p.getBoundingClientRect(), b = t.getBoundingClientRect(); return b.left >= a.right - 1 && b.top < a.bottom; }), '', 'title beside the cover');
    const chapterHref = await page.evaluate(() => (document.querySelector('a[href*="-chapter-"]') || {}).getAttribute?.('href'));
    check('Chapter links look like `/title/<slug>/<id>-chapter-<n>`', chapterHref && /^\/title\/[^/]+\/\d+-chapter-[^/?#]+\/?$/.test(chapterHref), chapterHref || 'none found', 'reader mode, Back treating chapters as one page');

  }

  // --- Reader -----------------------------------------------------------------------------------
  // Uses the chapter found above, or a known one if the series page was behind a challenge.
  section = 'Reader';
  {
    const chapterHref = (await page.evaluate(() => (document.querySelector('a[href*="-chapter-"]') || {}).getAttribute?.('href')).catch(() => null))
      || '/title/9nej6-return-of-the-blossoming-blade/11578620-chapter-185';
    {
      await open(chapterHref);
      check('Reader page marker `body.rpage-body`', await page.evaluate(() => document.body.classList.contains('rpage-body')), '', 'reader mode');
      check('Reader reported to the app', (await lastState())?.reader === true, '', 'full screen, keep screen on, volume keys');
      check('Reader layout `.rpage` + `.rpage-main`', await page.$('.rpage .rpage-main'), '', 'edge taps');
      check('Floating controls `.rpage-floatctl__row` / `__col`', await page.$('.rpage-floatctl .rpage-floatctl__row') && await page.$('.rpage-floatctl .rpage-floatctl__col'), '', 'controls layout/position/size options');
      const labels = await page.evaluate(() => [...document.querySelectorAll('.rpage-floatctl button')].map(b => b.getAttribute('aria-label')));
      check('Help and Settings buttons (aria-label)', labels.includes('Help') && labels.includes('Settings'), labels.join(', '), 'touch-controls sheet, minimal layout');
      check('Progress bar `.rpage-progress--*`', await page.$('[class*="rpage-progress--"]'), '', 'controls keep clear of the progress bar');
      const longStrip = await page.evaluate(() => document.querySelector('.rpage')?.classList.contains('rpage--long-strip'));
      check('Default mode is long strip (`.rpage--long-strip`)', longStrip, '', 'edge taps, volume keys');
      if (longStrip) {
        const y0 = await page.evaluate(() => scrollY);
        await page.evaluate(() => window.__comixium.readerStep(1)); await wait(900);
        check('Reader scrolls on ArrowDown (volume keys)', (await page.evaluate(() => scrollY)) > y0, '', 'volume keys');
        const y1 = await page.evaluate(() => scrollY);
        await page.touchscreen.tap(395, 500); await wait(900);
        check('Right-edge tap scrolls (long strip)', (await page.evaluate(() => scrollY)) > y1, '', 'edge taps');
      }
      check('Help button hidden (settings live behind the gear)', await page.evaluate(() => { const b = document.querySelector('.rpage-floatctl button[aria-label="Help"]'); return !!b && !b.getClientRects().length; }), '', 'one settings button');
      const h0 = await page.evaluate(() => document.querySelector('.rpage').classList.contains('rpage--chrome-hidden'));
      await page.touchscreen.tap(206, 500); await wait(120); await page.touchscreen.tap(206, 500); await wait(600);
      check('Double tap toggles controls (reader `x` shortcut)', (await page.evaluate(() => document.querySelector('.rpage').classList.contains('rpage--chrome-hidden'))) !== h0, '', 'show/hide controls');
      if (await page.evaluate(() => document.querySelector('.rpage').classList.contains('rpage--chrome-hidden'))) {
        await page.touchscreen.tap(206, 500); await wait(120); await page.touchscreen.tap(206, 500); await wait(600);
      }
      await page.evaluate(() => document.querySelector('.rpage-floatctl button[aria-label="Settings"]')?.click()); await wait(700);
      check('Reader settings open as a bottom sheet (`.minidlg`)', await page.evaluate(() => { const p = document.querySelector('.minidlg__panel'); return !!p && Math.abs(p.getBoundingClientRect().bottom - innerHeight) < 2; }), '', 'reader panels as bottom sheets');
      check('App options inside the reader settings sheet', await page.evaluate(() => document.querySelectorAll('.rpage-settings__body #cx-reader-settings .cx-row').length > 10), '', 'reader settings (taps, volume, controls)');
      check('Reader settings sheet reported as a popup', (await lastState())?.dialog === true, '', 'Back closes popups first');
      await page.evaluate(() => window.__comixium.closeDialog()); await wait(600);
      check('Back helper closes the settings sheet', !(await page.$('.minidlg')), '', 'Back closes popups first');
      await page.evaluate(() => document.querySelector('.rpage-floatctl button[aria-label="Toggle comments"]')?.click()); await wait(800);
      check('Comments panel opens as a bottom drawer (`.rpage-cmpanel`)', await page.evaluate(() => { const c = document.querySelector('.rpage-cmpanel.is-open'); if (!c) return false; const r = c.getBoundingClientRect(); return Math.abs(r.bottom - innerHeight) < 2 && Math.abs(r.width - innerWidth) < 2; }), '', 'comments drawer');
      await page.evaluate(() => window.__comixium.closeDialog()); await wait(600);
      check('Pinch zoom enabled on chapters', await page.evaluate(() => /user-scalable=yes/.test(document.querySelector('meta[name=viewport]').content)), '', 'pinch zoom');
    }
  }

  // --- Other routes ---------------------------------------------------------------------------
  section = 'Routes';
  await open('/genres');
  check('Breadcrumbs `*__crumbs` hidden', await page.evaluate(() => { const c = document.querySelector('[class$="__crumbs"], [class*="__crumbs "]'); return !!c && c.offsetParent === null; }), '', 'breadcrumbs hidden in the app');
  await open('/browse?q=solo');
  check('`/browse?q=` searches', await page.evaluate(() => [...document.querySelectorAll('input')].some(i => /solo/i.test(i.value))), '', 'native search');
  await open('/user?tab=titles');
  check('Logged out `/user` redirects home', /\/home\/?$|\/$/.test(new URL(page.url()).pathname), page.url(), 'Library/History log-in prompt');
} catch (e) {
  check('Check script ran to the end', false, String(e && e.stack || e));
} finally {
  await browser.close();
}

const failed = results.filter(r => !r.ok && !r.skipped);
const skipped = results.filter(r => r.skipped);
const reachable = results.some(r => !r.skipped);
const status = failed.length ? `${failed.length} problem${failed.length > 1 ? 's' : ''}`
  : !reachable ? 'site unreachable (Cloudflare challenge)'
  : skipped.length ? `no problems (${skipped.length} checks skipped behind a Cloudflare challenge)` : 'all good';
const lines = [`# Site check: ${status}`, '', `Checked ${BASE} on ${new Date().toISOString().slice(0, 10)}.`, ''];
if (failed.length) {
  lines.push('## Broken', '', '| Area | Check | Affects | Detail |', '| --- | --- | --- | --- |');
  failed.forEach(r => lines.push(`| ${r.section} | ${r.name} | ${r.affects} | ${String(r.detail).replace(/\|/g, '\\|').slice(0, 200)} |`));
  lines.push('');
}
lines.push('## All checks', '');
results.forEach(r => lines.push(`- ${r.skipped ? '⏭️' : r.ok ? '✅' : '❌'} ${r.section}: ${r.name}${r.skipped ? ' (skipped: Cloudflare challenge)' : ''}`));
console.log(lines.join('\n'));
// On GitHub Actions, also surface each failure as an annotation on the run / pull request.
if (process.env.GITHUB_ACTIONS) {
  const esc = t => String(t).replace(/%/g, '%25').replace(/\r/g, '').replace(/\n/g, ' ');
  failed.forEach(r => console.error(`::error title=Site check: ${esc(r.section)}::${esc(r.name)}. Affects: ${esc(r.affects)}. ${esc(r.detail).slice(0, 300)}`));
  if (skipped.length) console.error(`::warning title=Site check::${skipped.length} checks skipped behind a Cloudflare challenge: ${esc([...new Set(skipped.map(r => r.section))].join(', '))}`);
}
process.exit(failed.length ? 1 : reachable ? 0 : 2);
