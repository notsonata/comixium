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
const collectScript = readFileSync(join(inject, 'collect.js'), 'utf8');

const results = [];
let section = '';
let blockedSection = false;
const check = (name, ok, detail = '', affects = '') =>
  results.push({ section, name, ok: !!ok, skipped: blockedSection, detail, affects });
const wait = ms => new Promise(r => setTimeout(r, ms));
/** Polls fn until it returns something truthy or the time is up; returns the last value. */
async function until(fn, ms = 8000) {
  const end = Date.now() + ms;
  let v = null;
  while (!(v = await fn().catch(() => null)) && Date.now() < end) await wait(250);
  return v;
}
// Cloudflare sometimes lets a page load but challenges the requests it makes for its data (lists,
// genres). Checks that need that data are reported as skipped, not failed, once that happened.
let dataChallenged = false;
const checkData = (name, ok, detail = '', affects = '') =>
  results.push({ section, name, ok: !!ok, skipped: blockedSection || (!ok && dataChallenged), detail, affects });

const browser = await chromium.launch();
const ctx = await browser.newContext({
  userAgent: 'Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0 Mobile Safari/537.36',
  viewport: { width: 412, height: 915 }, isMobile: true, hasTouch: true,
});
await ctx.addInitScript({ content: 'window.__msgs=[];window.ComixiumNative={postMessage:function(s){window.__msgs.push(JSON.parse(s));}};' + pageScript });
const page = await ctx.newPage();
const errors = [];
page.on('response', r => {
  const h = r.headers();
  if (h['cf-mitigated'] === 'challenge' || ([403, 503].includes(r.status()) && /cloudflare/i.test(h.server || '') && ['xhr', 'fetch'].includes(r.request().resourceType()))) dataChallenged = true;
});
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
  await until(() => page.$('.settings-advanced-row'));
  await page.evaluate(() => document.querySelector('.settings-advanced-row')?.click());
  const sub = await until(async () => (await lastState())?.sub === 'Content preferences' && 'Content preferences');
  checkData('Content preferences view has a back button (`.settings-dialog__back`)', sub, String((await lastState())?.sub), 'Content preferences title in the top bar, Back to the main settings');
  await page.evaluate(() => window.__comixium.closeDialog());
  checkData('Back from Content preferences returns to the main settings', sub && await until(() => page.$('.settings-dialog .settings-advanced-row')), '', 'Back inside Theme & content filter');
  await page.evaluate(() => window.__comixium.closeDialog()); await wait(500);

  // --- Series page ----------------------------------------------------------------------------
  section = 'Series page';
  const titleHref = await until(() => page.evaluate(() => (document.querySelector('a[href^="/title/"]:not([href*="-chapter-"])') || {}).getAttribute?.('href')), 10000);
  checkData('Series links look like `/title/<slug>`', titleHref && /^\/title\/[^/]+\/?$/.test(titleHref), titleHref || 'none found', 'series detection (share button)');
  if (titleHref) {
    await open(titleHref);
    check('Series header parts (`.mpage__hero-inner`, `.mpage__poster`, `.mpage__title-row`)', await page.$('.mpage__hero-inner .mpage__poster') && await page.$('.mpage__hero-inner .mpage__title-row'), '', 'title beside the cover');
    check('Title sits beside the cover', await page.evaluate(() => { const p = document.querySelector('.mpage__poster'), t = document.querySelector('.mpage__title-row'); if (!p || !t) return false; const a = p.getBoundingClientRect(), b = t.getBoundingClientRect(); return b.left >= a.right - 1 && b.top < a.bottom; }), '', 'title beside the cover');
    const chapterHref = await page.evaluate(() => (document.querySelector('a[href*="-chapter-"]') || {}).getAttribute?.('href'));
    checkData('Chapter links look like `/title/<slug>/<id>-chapter-<n>`', chapterHref && /^\/title\/[^/]+\/\d+-chapter-[^/?#]+\/?$/.test(chapterHref), chapterHref || 'none found', 'reader mode, Back treating chapters as one page');
    checkData('Chapter rows (`.mchap-item` > `.mchap-row__primary`, `.mchap-bookmark`)', await page.$('.mchap-item a.mchap-row__primary[href*="-chapter-"]') && await page.$('.mchap-item > .mchap-bookmark'), '', 'download buttons on chapter rows');
    checkData('Download button on each chapter row', await until(() => page.evaluate(() => document.querySelectorAll('.mchap-item > .cx-dl').length > 0)), '', 'downloads');
    await page.evaluate(() => { window.__msgs.length = 0; document.querySelector('.mchap-item > .cx-dl')?.click(); });
    const dl = await until(() => page.evaluate(() => window.__msgs.find(m => m.t === 'download') || null), 2000);
    checkData('Download tap names the chapter, series and cover', dl && /-chapter-/.test(dl.path) && dl.label && dl.series && dl.cover,
      dl ? JSON.stringify({ path: dl.path, label: dl.label, group: dl.group, series: dl.series }) : 'no message', 'downloads (labels, cover)');
    checkData('Chapter search box and sort (`.mchap-search input`, `.mchap-toolbar__sort`)', await page.$('.mchap-search input') && await page.$('.mchap-toolbar__sort .useg__btn'), '', 'series downloads');
    check('Download button beside Read (`.mpage__cta`)', await until(() => page.$('.mpage__cta .cx-dl-series')), '', 'series downloads');
    await page.evaluate(() => { window.__msgs.length = 0; document.querySelector('.mpage__cta .cx-dl-series')?.click(); });
    const many = await until(() => page.evaluate(() => window.__msgs.find(m => m.t === 'downloadMany') || null), 4000);
    checkData('Series Download lists the groups (`.mpage__group` menu)', many && many.groups && many.groups.length > 0 && many.series,
      many ? JSON.stringify({ groups: many.groups.slice(0, 4), current: many.current, series: many.series }) : 'no message', 'series downloads (scanlators)');
    check('Group menu closed again after reading it', !(await page.$('.mpage__group.is-open')), '', 'series downloads');

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
      check('Download button among the reader controls', await page.$('.rpage-floatctl__col .cx-dl-reader'), '', 'download from the reader');
      check('Chapter title reads "<series> · <chapter>"', await page.evaluate(() => / · \S/.test(document.title)), await page.evaluate(() => document.title), 'download labels');
      const ending = await page.evaluate(() => ({
        group: (document.querySelector('.rpage-chap-ending__meta strong') || {}).textContent || '',
        nav: [...document.querySelectorAll('.rpage-chap-ending__nav-btn')].map(b => b.textContent.replace(/\s+/g, ' ').trim()),
      }));
      // "Next · Ch. 186"; on the newest chapter just "Next" (no number: the series ends there).
      check('Chapter ending names the group and the chapters around it (`.rpage-chap-ending`)', ending.group && ending.nav.some(t => /^Next\b/.test(t)) && ending.nav.every(t => /^(Prev|Next)\b/.test(t)), JSON.stringify(ending), 'series downloads, "read to the end"');
    }

    // Downloads run collect.js in a page of their own, with every image answered by a placeholder
    // (as the app does), then open the saved chapter with the network cut, as the app does offline.
    section = 'Downloads';
    const UA = 'Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0 Mobile Safari/537.36';
    const pixel = Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNkYAAAAAYAAjCB0C8AAAAASUVORK5CYII=', 'base64');
    /** Runs one collector job on [path]; returns its messages and the chapter-data request flags. */
    async function collect(path, job) {
      const c = await browser.newContext({ userAgent: UA, viewport: { width: 412, height: 915 }, isMobile: true, hasTouch: true });
      // The app answers the hello with the job; only site pages get the script (not about:blank).
      await c.addInitScript({ content: `if (/comix\\.(ws|to)$/.test(location.hostname)) { window.__got = []; window.ComixiumCollector = { onmessage: null, postMessage: function (s) {
        var m = JSON.parse(s); window.__got.push(m);
        if (m.type === 'hello') setTimeout(function () { window.ComixiumCollector.onmessage({ data: ${JSON.stringify(JSON.stringify(job))} }); }, 0); } }; ${collectScript} }` });
      await c.route('**/*', r => {
        const q = r.request();
        if (q.resourceType() === 'image') return r.fulfill({ status: 200, contentType: 'image/png', body: pixel });
        // The app drops progress uploads.
        if (/\/api\/v1\/user\//.test(q.url()) && q.method() !== 'GET') return r.fulfill({ status: 200, contentType: 'application/json', body: '{"status":"ok","result":null}' });
        return r.continue();
      });
      const flags = [];
      c.on('request', q => { const m = /\/api\/v1\/chapters\/\d+\?(.*)/.exec(q.url()); if (m) flags.push(m[1]); });
      const p = await c.newPage();
      await p.goto(BASE + path, { waitUntil: 'domcontentloaded', timeout: 30000 }).catch(() => {});
      const end = await until(() => p.evaluate(() => window.__got && window.__got.find(m => ['pages', 'failed', 'rows', 'info'].includes(m.type)) || null), 200000);
      const msgs = await p.evaluate(() => window.__got || []).catch(() => []);
      await c.close();
      if (end && end.why === 'check') dataChallenged = true;
      return { end, msgs, flags };
    }

    // Series downloads look numbers up with the series page's chapter search (one page of results, never paging).
    const series = chapterHref.replace(/\/[^/]+$/, '');
    const first = await collect(series, { mode: 'first', have: [] });
    const firstRows = first.end && first.end.type === 'rows' ? first.end.rows : [];
    checkData('Oldest-first list gives the first chapter', firstRows.length && Math.min(...firstRows.map(r => parseFloat(r.number))) <= 1, firstRows.slice(0, 3).map(r => r.number).join(', '), 'series downloads ("All")');
    const find = await collect(series, { mode: 'find', number: '2', have: [] });
    const rows = find.end && find.end.type === 'rows' ? find.end.rows : [];
    checkData('Chapter search finds every version of a number', rows.length && rows.every(r => r.number === '2' && /-chapter-2$/.test(r.path)), rows.map(r => r.group).join(', ') || JSON.stringify(find.end), 'series downloads');

    // An early chapter, so it has a next one.
    const saveHref = rows[0]?.path || chapterHref;
    // Series downloads look at each chapter they queue, to learn the number after it.
    const peek = await collect(saveHref, { mode: 'peek', have: [] });
    checkData('A quick look at a chapter reads its number and the next one', peek.end && peek.end.type === 'info' && peek.end.number === '2' && parseFloat(peek.end.next) > 2,
      peek.end ? JSON.stringify(peek.end) : 'no answer', 'series downloads (finding the next chapter)');
    const { end, msgs, flags } = await collect(saveHref, { mode: 'chapter', comments: true, have: [] });
    const ok = end && end.type === 'pages' && end.found === end.total && end.total > 0 && end.urls.every(u => /^https:\/\//.test(u));
    checkData('Every page image found by scrolling the reader', ok, end ? `${end.type} ${end.why || ''} ${end.found}/${end.total}` : 'no answer', 'downloads');
    checkData('Series title and cover found for downloads', end && end.series && /^https:\/\//.test(end.cover || ''), end ? JSON.stringify({ series: end.series, cover: (end.cover || '').slice(0, 60) }) : '', 'downloads (series name, cover)');
    checkData('Chapter number, group and next chapter read from the reader', end && end.number && end.group && end.next, end ? JSON.stringify({ number: end.number, group: end.group, next: end.next }) : '', 'series downloads (one version per chapter)');
    checkData("The collector doesn't trip the site's tamper flags (v=0)", flags.length && flags.every(f => /(^|&)v=0(&|$)/.test(f)), flags.map(f => f.replace(/&_=.*/, '')).join(' | '), 'downloads (not flagged as a bot)');
    const answers = msgs.filter(m => m.type === 'answer');
    const doc = msgs.find(m => m.type === 'document');
    const chapterAnswer = answers.find(a => /\/api\/v1\/chapters\/\d+/.test(a.url));
    checkData('Chapter page, chapter data (with x-enc) and comments saved', doc && /id="initial-data"/.test(doc.html) && chapterAnswer && chapterAnswer.enc && answers.some(a => /\/threads\/\d+\/comments/.test(a.url)),
      `${answers.length} answers, enc ${chapterAnswer ? chapterAnswer.enc : '-'}, page ${doc ? doc.html.length : 0} chars`, 'offline reading in the site\'s reader, offline comments');
    if (ok) {
      const res = await fetch(end.urls[0], { headers: { 'User-Agent': 'Mozilla/5.0 (Linux; Android 14)' } }).catch(e => ({ ok: false, status: String(e) }));
      check('Page images download without cookies or a referrer', res.ok && /^image\//.test(res.headers.get('content-type') || ''), String(res.status), 'downloads');
    }
    if (ok && doc && chapterAnswer) {
      // Offline: only what was saved, answered the way OfflineServer does.
      const key = u => /(^|\.)comix\.(ws|to)$/.test(u.hostname) ? u.pathname + u.search : u.href;
      const assets = new Map(msgs.filter(m => m.type === 'asset').map(m => [key(new URL(m.url)), m]));
      const images = new Set(end.urls.map(u => new URL(u).pathname));
      let hits = 0;
      const oc = await browser.newContext({ userAgent: UA, viewport: { width: 412, height: 915 }, isMobile: true, hasTouch: true });
      await oc.addInitScript({ content: 'window.__msgs=[];window.ComixiumNative={postMessage:function(s){window.__msgs.push(JSON.parse(s));}};' + pageScript });
      await oc.route('**/*', r => {
        const q = r.request(), u = new URL(q.url());
        if (u.pathname.startsWith('/hi/')) { if (images.has(u.pathname)) { hits++; return r.fulfill({ status: 200, contentType: 'image/png', body: pixel }); } return r.abort('internetdisconnected'); }
        if (q.isNavigationRequest()) return r.fulfill({ status: 200, contentType: 'text/html; charset=utf-8', body: doc.html });
        const asset = assets.get(key(u));
        if (asset) return r.fulfill({ status: 200, headers: { 'access-control-allow-origin': '*' }, contentType: asset.ct, body: Buffer.from(asset.data, 'base64') });
        if (/\/api\/v1\/chapters\/\d+/.test(u.pathname)) return r.fulfill({ status: 200, headers: { 'x-enc': chapterAnswer.enc }, contentType: chapterAnswer.type, body: chapterAnswer.body });
        return r.abort('internetdisconnected');
      });
      const op = await oc.newPage();
      await op.goto(BASE + saveHref, { waitUntil: 'domcontentloaded', timeout: 30000 }).catch(() => {});
      const shown = await until(() => op.evaluate(() => document.querySelectorAll('.rpage-page img[src^="http"]').length || null), 15000);
      check("A saved chapter opens offline in the site's reader", shown && hits > 0, `${shown || 0} pages shown, ${hits} images from the saved list`, 'offline reading');
      check('Reader controls on the offline chapter', await until(() => op.$('.rpage-floatctl__col .cx-dl-reader'), 6000), '', 'offline reading (same reader UI)');
      await oc.close();

      // Signed in, the reader tracks the page you're on only once the account's state for the chapter
      // answers; offline the app answers it with a blank one (OfflineServer.BLANK_STATE), tells the
      // page it's offline, and the page script hides the site's "Http Error" toasts. A made-up user
      // in local storage is enough for the site to act signed in; nothing reaches an account.
      const sc = await browser.newContext({ userAgent: UA, viewport: { width: 412, height: 915 }, isMobile: true, hasTouch: true });
      await sc.addInitScript({ content: `try { localStorage.getItem('auth') || localStorage.setItem('auth', JSON.stringify({ state: { user: { id: 1, hashId: 'check', username: 'check', displayName: 'check' } }, version: 0 })); } catch (e) {}` });
      await sc.addInitScript({ content: 'window.ComixiumNative={postMessage:function(){}};' + pageScript +
        ';window.__toasts=[];new MutationObserver(function(){document.querySelectorAll(".toast__text").forEach(function(t){if(t.__seen)return;t.__seen=1;' +
        'setTimeout(function(){var b=t.closest(".toast");window.__toasts.push({text:t.textContent,shown:!!b&&getComputedStyle(b).display!=="none"});},0);});}).observe(document,{subtree:true,childList:true});' +
        'document.addEventListener("DOMContentLoaded",function(){window.__comixium.setOffline(true);});' });
      let stateAsked = 0;
      await sc.route('**/*', r => {
        const q = r.request(), u = new URL(q.url());
        if (u.pathname.startsWith('/hi/')) return images.has(u.pathname) ? r.fulfill({ status: 200, contentType: 'image/png', body: pixel }) : r.abort('internetdisconnected');
        if (q.isNavigationRequest()) return r.fulfill({ status: 200, contentType: 'text/html; charset=utf-8', body: doc.html });
        const asset = assets.get(key(u));
        if (asset) return r.fulfill({ status: 200, headers: { 'access-control-allow-origin': '*' }, contentType: asset.ct, body: Buffer.from(asset.data, 'base64') });
        if (/\/api\/v1\/chapters\/\d+/.test(u.pathname)) return r.fulfill({ status: 200, headers: { 'x-enc': chapterAnswer.enc }, contentType: chapterAnswer.type, body: chapterAnswer.body });
        if (/^\/api\/v1\/user\/chapters\/\d+\/state$/.test(u.pathname) && q.method() === 'GET') { stateAsked++; return r.fulfill({ status: 200, contentType: 'application/json', body: '{"status":"ok","result":{"liked":false}}' }); }
        return r.abort('internetdisconnected');
      });
      const sp = await sc.newPage();
      await sp.goto(BASE + saveHref, { waitUntil: 'domcontentloaded', timeout: 30000 }).catch(() => {});
      await until(() => sp.evaluate(() => document.querySelectorAll('.rpage-page img[src^="http"]').length || null), 15000);
      await sp.mouse.move(200, 400);
      const segs = () => sp.evaluate(() => { const s = [...document.querySelectorAll('.rpage-progress__seg')]; return { total: s.length, at: s.findIndex(x => x.classList.contains('is-active')) }; });
      for (let i = 0; i < 80 && (await segs()).at < (await segs()).total - 1; i++) { await sp.mouse.wheel(0, 3000); await wait(120); }
      const bar = await segs();
      check('Signed in and offline, the reader tracks the page once its account state answers (`/api/v1/user/chapters/<id>/state`)',
        stateAsked > 0 && bar.total > 1 && bar.at === bar.total - 1, `state asked ${stateAsked}x, at page ${bar.at + 1} of ${bar.total}`, 'reading progress in downloaded chapters offline');
      const toasts = await sp.evaluate(() => window.__toasts || []);
      const netErrors = toasts.filter(t => /^\s*http error\s*$/i.test(t.text));
      check('"Http Error" toasts hidden while offline', netErrors.length > 0 && netErrors.every(t => !t.shown),
        `${netErrors.length} "Http Error", ${netErrors.filter(t => t.shown).length} shown; others: ${toasts.filter(t => !netErrors.includes(t)).map(t => t.text).join(', ') || 'none'}`, 'no Http Error messages offline');
      await sc.close();
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
