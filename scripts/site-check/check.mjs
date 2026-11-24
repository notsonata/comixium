// Daily check that comix still has everything Comixium hooks into.
// Loads the live site in Chromium at phone size with the app's injected page script (the same
// script and stylesheet the app ships) and checks each dependency. Prints a Markdown report;
// exits 1 if any check fails, 2 if the site couldn't be reached at all (e.g. a Cloudflare wall).
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
const check = (name, ok, detail = '', affects = '') => results.push({ section, name, ok: !!ok, detail, affects });
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

async function open(path) {
  const res = await page.goto(BASE + path, { waitUntil: 'networkidle', timeout: 60000 }).catch(e => ({ error: e }));
  await wait(1500);
  const blocked = await page.evaluate(() => /Just a moment|cf-browser-verification|challenge-platform\/h\//.test(document.title + document.body.innerText.slice(0, 500))).catch(() => true);
  return { res, blocked };
}

try {
  // --- Home -----------------------------------------------------------------------------------
  section = 'Home';
  const home = await open('/');
  if (home.res?.error || home.blocked) {
    console.log(`# Site check: could not load ${BASE}\n\n${home.blocked ? 'Blocked by a Cloudflare challenge.' : String(home.res.error)}`);
    process.exit(2);
  }
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
  check('Settings button `.topnav .settings button`', await page.$('.topnav .settings button'), '', 'native site-settings button');
  const st = await lastState();
  check('Page state reaches the app', st, '', 'titles, login state, theme sync');
  check('Theme variables (--bg, --accent, --text)', st && st.theme && st.theme.bg && st.theme.accent && st.theme.text, st ? JSON.stringify({ bg: st.theme.bg, accent: st.theme.accent }) : '', 'native colours follow the site theme');
  check('Logged-out state detected', st && st.auth === 'out', st ? `auth=${st.auth}` : '', 'Library/History log-in prompt');
  await wait(3500); // the site adds its popunder link every 3 s
  check('No full-screen popunder link left on the page', await page.evaluate(() => ![...document.querySelectorAll('body > a')].some(a => !a.textContent.trim() && getComputedStyle(a).position === 'fixed' && getComputedStyle(a).display !== 'none')), '', 'ad blocking');
  check('Promo banner hidden', await page.evaluate(() => ![...document.querySelectorAll('.home-banner, a[href*="utm_source=comix"]')].some(e => e.offsetParent !== null)), '', 'ad blocking');

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
  check('Site settings dialog (`.settings-dialog`)', await page.$('.settings-dialog'), '', 'native site-settings button');
  await page.evaluate(() => window.__comixium.closeDialog()); await wait(500);

  // --- Series page ----------------------------------------------------------------------------
  section = 'Series page';
  const titleHref = await page.evaluate(() => (document.querySelector('a[href^="/title/"]:not([href*="-chapter-"])') || {}).getAttribute?.('href'));
  check('Series links look like `/title/<slug>`', titleHref && /^\/title\/[^/]+\/?$/.test(titleHref), titleHref || 'none found', 'series detection (share button)');
  if (titleHref) {
    await open(titleHref);
    const chapterHref = await page.evaluate(() => (document.querySelector('a[href*="-chapter-"]') || {}).getAttribute?.('href'));
    check('Chapter links look like `/title/<slug>/<id>-chapter-<n>`', chapterHref && /^\/title\/[^/]+\/\d+-chapter-[^/?#]+\/?$/.test(chapterHref), chapterHref || 'none found', 'reader mode, Back treating chapters as one page');

    // --- Reader -------------------------------------------------------------------------------
    section = 'Reader';
    if (chapterHref) {
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
      await page.evaluate(() => { const b = [...document.querySelectorAll('.rpage-floatctl button')].find(b => b.getAttribute('aria-label') === 'Help'); b && b.click(); });
      await wait(500);
      check('Help opens the app sheet, not the shortcut list', await page.evaluate(() => window.__msgs.some(m => m.t === 'readerHelp') && !/Keyboard shortcuts/i.test(document.body.innerText)), '', 'touch-controls sheet');
      check('Pinch zoom enabled on chapters', await page.evaluate(() => /user-scalable=yes/.test(document.querySelector('meta[name=viewport]').content)), '', 'pinch zoom');
    }
  }

  // --- Other routes ---------------------------------------------------------------------------
  section = 'Routes';
  await open('/browse?q=solo');
  check('`/browse?q=` searches', await page.evaluate(() => [...document.querySelectorAll('input')].some(i => /solo/i.test(i.value))), '', 'native search');
  const lib = await page.goto(BASE + '/user?tab=titles', { waitUntil: 'domcontentloaded' });
  check('Logged out `/user` redirects home', /\/home\/?$|\/$/.test(new URL(page.url()).pathname), page.url(), 'Library/History log-in prompt');
} catch (e) {
  check('Check script ran to the end', false, String(e && e.stack || e));
} finally {
  await browser.close();
}

const failed = results.filter(r => !r.ok);
const lines = [`# Site check: ${failed.length ? `${failed.length} problem${failed.length > 1 ? 's' : ''}` : 'all good'}`, '', `Checked ${BASE} on ${new Date().toISOString().slice(0, 10)}.`, ''];
if (failed.length) {
  lines.push('## Broken', '', '| Area | Check | Affects | Detail |', '| --- | --- | --- | --- |');
  failed.forEach(r => lines.push(`| ${r.section} | ${r.name} | ${r.affects} | ${String(r.detail).replace(/\|/g, '\\|').slice(0, 200)} |`));
  lines.push('');
}
lines.push('## All checks', '');
results.forEach(r => lines.push(`- ${r.ok ? '✅' : '❌'} ${r.section}: ${r.name}`));
console.log(lines.join('\n'));
// On GitHub Actions, also surface each failure as an annotation on the run / pull request.
if (process.env.GITHUB_ACTIONS) {
  const esc = t => String(t).replace(/%/g, '%25').replace(/\r/g, '').replace(/\n/g, ' ');
  failed.forEach(r => console.error(`::error title=Site check: ${esc(r.section)}::${esc(r.name)}. Affects: ${esc(r.affects)}. ${esc(r.detail).slice(0, 300)}`));
}
process.exit(failed.length ? 1 : 0);
