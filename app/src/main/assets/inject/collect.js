/*
 * Comixium chapter collector. Injected at document start into the hidden page that prepares
 * downloads, never into pages the user sees. On load it says hello; the app answers with the job:
 *  - chapter: make the site's own reader find every page's image address (it fills in a page's
 *    <img> as it comes near), and save what the reader needs to open the chapter offline: the page,
 *    its scripts, styles and fonts, the chapter data and (if wanted) the comments
 *  - peek:    only read the chapter's number, group and the number its Next button names
 *  - find:    on a series page, search the chapter list for one number (every group's version)
 *  - first:   on a series page, list the oldest chapters
 * While it works, this visit doesn't count as reading: the saved reader settings and progress are
 * left alone, and the app drops the progress upload. The app answers image requests in this page
 * with a 1x1 placeholder, so nothing heavy loads.
 */
(function () {
  'use strict';
  if (window.__cxCollector) return;
  window.__cxCollector = true;
  var native = window.ComixiumCollector;
  function post(o) {
    try { if (native) native.postMessage(JSON.stringify(o)); } catch (e) { /* bridge gone */ }
  }

  // --- The job, from the app ---------------------------------------------------------------
  var job = null, waiting = [];
  function setJob(o) {
    if (job) return;
    job = o || {};
    waiting.splice(0).forEach(function (f) { f(job); });
  }
  if (native) {
    native.onmessage = function (e) { try { setJob(JSON.parse(e.data)); } catch (x) { /* not ours */ } };
  }
  function getJob() {
    return new Promise(function (resolve) {
      if (job) return resolve(job);
      waiting.push(resolve);
      // An app too old to answer: collect the chapter as before.
      setTimeout(function () { setJob({ mode: 'chapter', comments: false, have: [] }); }, 5000);
    });
  }

  // --- This visit doesn't count as reading ----------------------------------------------------
  var proto = Storage.prototype;
  var getItem = proto.getItem, setItem = proto.setItem, removeItem = proto.removeItem;
  var SETTINGS = /settings_v2$/;
  var KEEP_OUT = /settings_v2$|reader\.progress/;
  function isLocal(s) { try { return s === window.localStorage; } catch (e) { return false; } }
  proto.getItem = function (k) {
    var v = getItem.call(this, k);
    if (!isLocal(this) || !SETTINGS.test(String(k))) return v;
    try {
      var o = v ? JSON.parse(v) : { state: {}, version: 2 };
      o.state = Object.assign({}, o.state, { readingDirection: 'ttb', layout: 'long-strip' });
      return JSON.stringify(o);
    } catch (e) { return v; }
  };
  proto.setItem = function (k, v) {
    if (isLocal(this) && KEEP_OUT.test(String(k))) return;
    return setItem.call(this, k, v);
  };
  proto.removeItem = function (k) {
    if (isLocal(this) && KEEP_OUT.test(String(k))) return;
    return removeItem.call(this, k);
  };

  // Nothing else of the page's is wrapped: the site treats wrapped fetch, XMLHttpRequest and
  // JSON.parse as signs of a bot. Progress uploads are dropped by the app (see PageCollector), and
  // the API answers the reader needs offline are asked for again once the reader has them.

  // --- Helpers ------------------------------------------------------------------------------
  function sleep(ms) { return new Promise(function (r) { setTimeout(r, ms); }); }
  function challenged() { return !!document.querySelector('.wafcap__frame, iframe[src*="challenge"]'); }
  async function waitFor(sel, ms) {
    for (var t = 0; t < ms; t += 100) {
      var el = document.querySelector(sel);
      if (el) return el;
      await sleep(100);
    }
    return null;
  }
  function srcOf(page) {
    var img = page.querySelector('img');
    var s = img && img.src;
    return s && /^https?:/.test(s) ? s : null;
  }
  /** Addresses the page has requested so far (its own requests, as the browser logged them). */
  function requested(re) {
    var out = [];
    try {
      performance.getEntriesByType('resource').forEach(function (e) { if (re.test(e.name) && out.indexOf(e.name) < 0) out.push(e.name); });
    } catch (e) { /* no timing */ }
    return out;
  }
  /** The site's answer for [url] again, as the reader got it (the x-enc header says how to read it). */
  async function answer(url) {
    try {
      var r = await fetch(url, { credentials: 'include', headers: { 'Accept': 'application/json', 'X-Requested-With': 'XMLHttpRequest' } });
      if (!r.ok) return null;
      return { url: url, type: r.headers.get('content-type') || '', enc: r.headers.get('x-enc') || '', body: await r.text() };
    } catch (e) { return null; }
  }
  /** Series title and cover from the data the page was built from. */
  function seriesInfo() {
    try {
      var data = JSON.parse(document.getElementById('initial-data').textContent);
      var q = data && data.queries;
      for (var key in q) {
        if (q[key] && q[key].title && q[key].poster) {
          var p = q[key].poster;
          return { title: q[key].title, cover: new URL(p.large || p.medium || p.small || '', location.href).href };
        }
      }
    } catch (e) { /* not there */ }
    return { title: '', cover: '' };
  }
  function numberIn(text) {
    var m = /(\d+(?:\.\d+)?)/.exec(String(text || ''));
    return m ? m[1] : '';
  }

  /** The chapter's number and group, and the number the Next button goes to ('' at the end). */
  function readerInfo() {
    var number = '', group = '', next = '';
    var meta = document.querySelector('.rpage-chap-ending__meta');
    if (meta) {
      number = numberIn((meta.querySelector('span') || {}).textContent);
      var g = meta.querySelector('strong');
      if (g) group = g.textContent.trim();
    }
    if (!number) { var m = /-chapter-([^/?#]+)/.exec(location.pathname); if (m) number = m[1]; }
    var btns = document.querySelectorAll('.rpage-chap-ending__nav-btn');
    for (var i = 0; i < btns.length; i++) {
      var t = btns[i].textContent.replace(/\s+/g, ' ').trim();
      if (/^next\b/i.test(t) && !btns[i].disabled && btns[i].getAttribute('aria-disabled') !== 'true') next = numberIn(t);
    }
    return { number: number, group: group, next: next };
  }

  // --- The page's scripts, styles and fonts ---------------------------------------------------
  function assetUrls(extra) {
    var set = {};
    function add(u) {
      try {
        var x = new URL(u, location.href);
        if (x.protocol !== 'https:') return;
        var site = x.origin === location.origin;
        var ok = (site && /^\/assets\/.+\.(?:js|mjs|css|woff2?|ttf|otf)$/.test(x.pathname)) ||
          (site && /^\/images\//.test(x.pathname)) ||
          x.hostname === 'fonts.googleapis.com' || x.hostname === 'fonts.gstatic.com';
        if (ok) set[x.href.split('#')[0]] = 1;
      } catch (e) { /* not an address */ }
    }
    try { performance.getEntriesByType('resource').forEach(function (e) { if (!/\/images\//.test(e.name)) add(e.name); }); } catch (e) { /* no timing */ }
    var els = document.querySelectorAll('script[src], link[href]');
    for (var i = 0; i < els.length; i++) {
      var el = els[i];
      if (el.tagName === 'LINK' && !/stylesheet|modulepreload|preload/i.test(el.getAttribute('rel') || '')) continue;
      add(el.src || el.href);
    }
    (extra || []).forEach(add);
    return Object.keys(set);
  }
  function base64(buf) {
    var bytes = new Uint8Array(buf), s = '';
    for (var i = 0; i < bytes.length; i += 0x8000) s += String.fromCharCode.apply(null, bytes.subarray(i, i + 0x8000));
    return btoa(s);
  }
  /** The Latin font files a Google Fonts stylesheet names (other alphabets fall back to the phone's fonts). */
  async function fontFiles(cssUrl) {
    try {
      var css = await (await fetch(cssUrl, { credentials: 'omit', cache: 'force-cache' })).text();
      var out = [], re = /\/\* latin \*\/\s*@font-face\s*\{[^}]*?url\((https:\/\/fonts\.gstatic\.com\/[^)\s]+)\)/g, m;
      while ((m = re.exec(css))) out.push(m[1]);
      return out;
    } catch (e) { return []; }
  }
  async function sendAsset(url) {
    try {
      var same = new URL(url).origin === location.origin;
      var r = await fetch(url, { credentials: same ? 'same-origin' : 'omit', cache: 'force-cache' });
      if (!r.ok) return false;
      var type = r.headers.get('content-type') || '';
      post({ type: 'asset', url: url, ct: type, data: base64(await r.arrayBuffer()) });
      return true;
    } catch (e) { return false; }
  }

  // --- Jobs ---------------------------------------------------------------------------------
  async function waitForReader() {
    var list = [];
    for (var i = 0; i < 300; i++) {
      list = document.querySelectorAll('.rpage-page');
      if (list.length) return list;
      await sleep(100);
    }
    post({ type: 'failed', why: challenged() ? 'check' : 'noreader' });
    return null;
  }

  async function peek() {
    if (!(await waitForReader())) return;
    await waitFor('.rpage-chap-ending__meta', 10000);
    var info = readerInfo();
    post({ type: 'info', number: info.number, group: info.group, next: info.next, title: document.title || '' });
  }

  async function collect(j) {
    var list = await waitForReader();
    if (!list) return;
    // Pages may still be added for a moment; wait until the count holds.
    for (var s = 0; s < 20; s++) {
      await sleep(150);
      var again = document.querySelectorAll('.rpage-page');
      if (again.length === list.length) break;
      list = again;
    }
    var pages = Array.prototype.slice.call(list);
    var total = pages.length;
    var urls = [];
    for (var u = 0; u < total; u++) urls.push(null);
    function sweep() {
      var n = 0;
      for (var j2 = 0; j2 < total; j2++) {
        if (!urls[j2]) urls[j2] = srcOf(pages[j2]);
        if (urls[j2]) n++;
      }
      return n;
    }
    var lastPost = 0;
    post({ type: 'progress', found: sweep(), total: total });
    for (var pass = 0; pass < 2 && sweep() < total; pass++) {
      for (var k = 0; k < total; k++) {
        if (urls[k] || (urls[k] = srcOf(pages[k]))) continue;
        pages[k].scrollIntoView({ block: 'start' });
        for (var w = 0; w < 60 && !srcOf(pages[k]); w++) await sleep(50);
        var found = sweep();
        if (Date.now() - lastPost > 300) { lastPost = Date.now(); post({ type: 'progress', found: found, total: total }); }
      }
      if (sweep() < total) { window.scrollTo(0, 0); await sleep(400); }
    }
    var done = sweep();
    if (done < total) {
      post({ type: 'failed', why: 'missing', found: done, total: total });
      return;
    }
    // The comments load when the reader reaches the end of the chapter.
    var extra = [];
    if (j.comments) {
      window.scrollTo(0, document.documentElement.scrollHeight);
      for (var c = 0; c < 80 && !requested(/\/api\/v1\/threads\/\d+\/comments/).length; c++) {
        if (c % 10 === 9) window.scrollTo(0, document.documentElement.scrollHeight);
        await sleep(100);
      }
      await sleep(600);
      var imgs = document.querySelectorAll('.rpage-strip-comments img, [class*="comment"] img');
      for (var q = 0; q < imgs.length; q++) {
        var src = imgs[q].getAttribute('src');
        if (src && !/^data:/.test(src)) extra.push(src);
      }
    }
    await waitFor('.rpage-chap-ending__meta', 3000);
    var info = readerInfo();
    var about = seriesInfo();
    post({ type: 'progress', found: total, total: total, saving: true });

    // The page itself, as the site sends it (its scripts set the reader up from it).
    var html = '';
    try {
      var res = await fetch(location.href, { credentials: 'include', cache: 'force-cache' });
      if (res.ok) html = await res.text();
    } catch (e) { /* offline or blocked */ }
    if (!/<script[^>]*id="initial-data"/.test(html)) {
      post({ type: 'failed', why: 'nopage' });
      return;
    }
    var all = assetUrls(extra);
    for (var f = 0, n0 = all.length; f < n0; f++) {
      if (/^https:\/\/fonts\.googleapis\.com\//.test(all[f])) {
        (await fontFiles(all[f])).forEach(function (u) { if (all.indexOf(u) < 0) all.push(u); });
      }
    }
    var have = {};
    (j.have || []).forEach(function (h) { have[h] = 1; });
    var kept = [];
    for (var a = 0; a < all.length; a++) {
      if (have[all[a]] || await sendAsset(all[a])) kept.push(all[a]);
    }
    var wanted = requested(j.comments ? /\/api\/v1\/(chapters\/\d+|threads\/)/ : /\/api\/v1\/chapters\/\d+/);
    var gotChapter = false;
    for (var n = 0; n < wanted.length; n++) {
      var x = await answer(wanted[n]);
      if (!x) continue;
      if (/\/chapters\/\d+/.test(x.url)) gotChapter = true;
      post({ type: 'answer', url: x.url, ct: x.type, enc: x.enc, body: x.body });
    }
    if (!gotChapter) {
      post({ type: 'failed', why: 'nodata' });
      return;
    }
    post({ type: 'document', html: html });
    post({
      type: 'pages',
      urls: urls, found: done, total: total,
      assets: kept,
      number: info.number, group: info.group, next: info.next,
      title: document.title || '',
      series: about.title,
      cover: about.cover
    });
  }

  /** The rows of a series page's chapter list: address, number and group. */
  function rows() {
    var out = [];
    var items = document.querySelectorAll('.mchap-item');
    for (var i = 0; i < items.length; i++) {
      var a = items[i].querySelector('a.mchap-row__primary[href*="-chapter-"]');
      if (!a) continue;
      var g = items[i].querySelector('.mchap-row__group span');
      var href = a.getAttribute('href');
      var m = /-chapter-([^/?#]+)/.exec(href);
      out.push({ path: href.split(/[?#]/)[0], number: m ? m[1] : numberIn((items[i].querySelector('.mchap-row__ch') || {}).textContent), group: g ? g.textContent.trim() : '' });
    }
    return out;
  }
  function sameNumber(a, b) {
    var x = parseFloat(a), y = parseFloat(b);
    return !isNaN(x) && !isNaN(y) ? x === y : String(a) === String(b);
  }

  async function listRows(j) {
    var list = null;
    for (var i = 0; i < 300 && !list; i++) {
      list = document.querySelector('.mchap-list');
      if (!list) await sleep(100);
    }
    if (!list) { post({ type: 'failed', why: challenged() ? 'check' : 'noreader' }); return; }
    await waitFor('.mchap-item, .mchap-foot', 10000);
    var before = JSON.stringify(rows());
    if (j.mode === 'first') {
      var sort = document.querySelector('.mchap-toolbar__sort .useg__btn');
      if (sort && /desc/i.test(sort.title || '')) {
        sort.click();
        for (var s = 0; s < 100 && JSON.stringify(rows()) === before; s++) await sleep(100);
      }
      post({ type: 'rows', rows: rows() });
      return;
    }
    // One number: the series page's chapter search lists every group's version of it.
    var input = document.querySelector('.mchap-search input');
    if (!input) { post({ type: 'failed', why: 'nosearch' }); return; }
    var setter = Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value').set;
    setter.call(input, String(j.number));
    input.dispatchEvent(new Event('input', { bubbles: true }));
    var got = [];
    for (var w = 0; w < 120; w++) {
      await sleep(100);
      got = rows();
      var changed = JSON.stringify(got) !== before;
      if (changed && got.length && got.every(function (r) { return sameNumber(r.number, j.number); })) break;
      if (changed && !got.length && w > 15) break;
    }
    post({ type: 'rows', rows: got.filter(function (r) { return sameNumber(r.number, j.number); }) });
  }

  getJob().then(function (j) {
    var run = j.mode === 'find' || j.mode === 'first' ? listRows : j.mode === 'peek' ? peek : collect;
    return run(j);
  }).catch(function (e) { post({ type: 'failed', why: 'script', detail: String(e && e.message || e) }); });
  post({ type: 'hello', path: location.pathname });
})();
