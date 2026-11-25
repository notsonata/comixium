/*
 * Comixium chapter collector. Injected at document start into the hidden page that prepares a
 * chapter download, never into pages the user sees. The site's own reader works out each page's
 * image address; this script makes it do so for every page and reports them to the app:
 *  - shows the chapter as a long strip, without changing the saved reader settings
 *  - keeps the visit out of reading progress (the local copy and the upload)
 *  - scrolls page by page; the reader fills in a page's <img> as it comes near
 * The app answers image requests in this page with a 1x1 placeholder, so nothing heavy loads.
 */
(function () {
  'use strict';
  if (window.__cxCollector) return;
  window.__cxCollector = true;
  var native = window.ComixiumCollector;
  function post(o) {
    try { if (native) native.postMessage(JSON.stringify(o)); } catch (e) { /* bridge gone */ }
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
  // The app also drops non-GET requests to the account API; these cover fetch and beacons.
  var PROGRESS = /\/api\/v1\/user\//;
  var realFetch = window.fetch;
  if (realFetch) {
    window.fetch = function (input, init) {
      var url = typeof input === 'string' ? input : (input && input.url) || '';
      var method = (init && init.method) || (input && input.method) || 'GET';
      if (PROGRESS.test(url) && String(method).toUpperCase() !== 'GET') {
        return Promise.resolve(new Response('{"status":"ok","result":null}', { status: 200, headers: { 'Content-Type': 'application/json' } }));
      }
      return realFetch.apply(this, arguments);
    };
  }
  if (navigator.sendBeacon) {
    var beacon = navigator.sendBeacon.bind(navigator);
    navigator.sendBeacon = function (url, data) { return PROGRESS.test(String(url)) ? true : beacon(url, data); };
  }

  // --- Series details from the page's embedded data (title and cover) -------------------------
  var parse = JSON.parse, series = null;
  JSON.parse = function (t, r) {
    var v = parse.call(this, t, r);
    try {
      if (!series && v && v.page === 'read' && v.queries) {
        for (var key in v.queries) {
          var q = v.queries[key];
          if (q && q.title && q.poster) { series = { title: q.title, cover: q.poster.large || q.poster.medium || '' }; break; }
        }
        if (series) JSON.parse = parse;
      }
    } catch (e) { /* not ours */ }
    return v;
  };

  // --- Collect every page's image -----------------------------------------------------------
  function sleep(ms) { return new Promise(function (r) { setTimeout(r, ms); }); }
  function srcOf(page) {
    var img = page.querySelector('img');
    var s = img && img.src;
    return s && /^https?:/.test(s) ? s : null;
  }

  async function collect() {
    var list = [];
    for (var i = 0; i < 300; i++) {
      list = document.querySelectorAll('.rpage-page');
      if (list.length) break;
      await sleep(100);
    }
    if (!list.length) {
      post({ type: 'failed', why: document.querySelector('.wafcap__frame, iframe[src*="challenge"]') ? 'check' : 'noreader' });
      return;
    }
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
      for (var j = 0; j < total; j++) {
        if (!urls[j]) urls[j] = srcOf(pages[j]);
        if (urls[j]) n++;
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
    post({
      type: done === total ? 'pages' : 'failed',
      why: done === total ? '' : 'missing',
      urls: urls, found: done, total: total,
      title: document.title || '',
      series: series ? series.title : '',
      cover: series && series.cover ? new URL(series.cover, location.href).href : ''
    });
  }

  collect().catch(function (e) { post({ type: 'failed', why: 'script', detail: String(e && e.message || e) }); });
})();
