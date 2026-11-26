/*
 * Comixium page script. Injected at document start into comix.ws / comix.to pages only.
 *  - installs the app stylesheet (ad hiding, native-chrome adjustments)
 *  - neutralises the site's popunder (an empty full-screen <a target=_blank> on <body>)
 *  - reports page state (url, title, login, reader, open dialog, theme colours) to the app
 *  - exposes a few helpers the app calls (open site settings / login, close dialogs, scroll)
 * The CSS placeholder below is replaced by the app with the contents of comixium.css.
 */
(function () {
  'use strict';
  if (window.__comixium) return;
  var CSS = __COMIXIUM_CSS__;
  var native = window.ComixiumNative;

  function post(obj) {
    try { if (native) native.postMessage(JSON.stringify(obj)); } catch (e) { /* bridge gone */ }
  }

  // At document start there may be no <html> yet; everything below copes with that.
  function addStyle() {
    if (document.getElementById('comixium-style')) return;
    var parent = document.head || document.documentElement;
    if (!parent) return;
    var s = document.createElement('style');
    s.id = 'comixium-style';
    s.textContent = CSS;
    parent.appendChild(s);
  }
  addStyle();

  function hostOf(href) {
    try { return new URL(href, location.href).host.toLowerCase(); } catch (e) { return ''; }
  }
  function isSiteHost(h) {
    return /(^|\.)comix\.(ws|to)$/.test(h);
  }

  // --- Popunder / clickjacking guard -------------------------------------------------------
  function isTrap(el) {
    if (!el || el.nodeType !== 1 || el.tagName !== 'A') return false;
    if ((el.textContent || '').trim() || el.children.length) return false;
    var st = el.getAttribute('style') || '';
    var fixed = /position\s*:\s*fixed/i.test(st) ||
      (el.isConnected && window.getComputedStyle(el).position === 'fixed');
    if (!fixed) return false;
    var href = el.getAttribute('href') || '';
    return el.target === '_blank' || (/^https?:/i.test(href) && !isSiteHost(hostOf(href)));
  }
  var trapsRemoved = 0;
  function zap(el) {
    if (el.parentNode) el.parentNode.removeChild(el);
    trapsRemoved++;
  }
  document.addEventListener('click', function (e) {
    var a = e.target && e.target.closest ? e.target.closest('a') : null;
    if (a && isTrap(a)) {
      e.preventDefault();
      e.stopImmediatePropagation();
      zap(a);
    }
  }, true);
  // --- In-memory downloads ------------------------------------------------------------------
  // Pages build some files in memory (e.g. a library backup) and "download" them through a
  // blob: or data: link, which the app can't fetch. Keep the blobs behind object URLs so they
  // can be handed over even if the page revokes the URL right after clicking.
  var blobs = {};
  var origCreate = URL.createObjectURL;
  URL.createObjectURL = function (obj) {
    var u = origCreate.apply(URL, arguments);
    try { if (obj instanceof Blob && !/^image\//.test(obj.type)) blobs[u] = obj; } catch (e) { /* ignore */ }
    return u;
  };
  var origRevoke = URL.revokeObjectURL;
  URL.revokeObjectURL = function (u) {
    setTimeout(function () { delete blobs[u]; }, 60000);
    return origRevoke.apply(URL, arguments);
  };
  function sendBlob(blob, name) {
    var fr = new FileReader();
    fr.onload = function () {
      var s = String(fr.result);
      post({ t: 'download', name: name, mime: blob.type || 'application/octet-stream', data: s.slice(s.indexOf(',') + 1) });
    };
    fr.readAsDataURL(blob);
  }
  function handleDownload(a) {
    var href = a.href || '';
    if (!a.hasAttribute('download') || !/^(blob|data):/i.test(href)) return false;
    var name = a.getAttribute('download') || 'download';
    if (blobs[href]) sendBlob(blobs[href], name);
    else fetch(href).then(function (r) { return r.blob(); }).then(function (b) { sendBlob(b, name); });
    return true;
  }
  var origAnchorClick = HTMLAnchorElement.prototype.click;
  HTMLAnchorElement.prototype.click = function () {
    if (handleDownload(this)) return;
    return origAnchorClick.apply(this, arguments);
  };
  document.addEventListener('click', function (e) {
    var a = e.target && e.target.closest ? e.target.closest('a[download]') : null;
    if (a && handleDownload(a)) { e.preventDefault(); e.stopImmediatePropagation(); }
  }, true);

  // --- Chapter reader: touch controls ---------------------------------------------------------
  // The site's reader already toggles its controls on a double tap and turns pages on edge taps in
  // paged mode. On top of that: edge taps scroll a long strip, pinch zoom works on chapter pages,
  // and its keyboard-shortcut help is replaced by the app's touch-controls sheet.
  // Mirrors the app's settings (pushed with setPrefs). Some are applied here (taps, zoom, controls
  // layout, mobile layer); the rest (volume keys, full screen...) are only shown in the settings sheet.
  var prefs = {
    tapEdges: true, tapPaged: true, tapLayout: 'sides', tapZone: 'medium', invertTaps: false, controlsTap: 'double',
    pinchZoom: true, layout: 'rail', x: 'right', y: 'top', size: 'normal',
    volumeKeys: true, volumeInvert: false, immersive: true, keepOn: true, longPress: true, controlsHide: '',
    mobile: true, announce: true, share: true
  };
  var DEFAULTS = { layout: 'rail', x: 'right', y: 'top', size: 'normal' };
  var PREFS_KEY = 'comixium.reader';
  try { var saved = JSON.parse(localStorage.getItem(PREFS_KEY) || 'null'); if (saved) setPrefsFrom(saved); } catch (e) { /* none */ }

  function setPrefsFrom(p) {
    if (p.layout === 'minimal') { // older "minimal" layout = rail with only the chapter switcher and settings
      p.layout = 'rail';
      if (p.controlsHide === undefined) p.controlsHide = 'home,series,comments,chapters,autoscroll';
    }
    if (typeof p.volume === 'string' && p.volumeKeys === undefined) { // older single "volume" setting
      p.volumeKeys = p.volume !== 'off';
      p.volumeInvert = p.volume === 'inverted';
    }
    // Turning announcements back on also brings back the ones dismissed one by one.
    if ((p.announce === true && prefs.announce === false) || p.clearDismissed) saveDismissed([]);
    for (var k in p) {
      if (!Object.prototype.hasOwnProperty.call(prefs, k)) continue;
      prefs[k] = typeof prefs[k] === 'boolean' ? !!p[k] : String(p[k]);
    }
  }

  // Prefs live on <html> as data-cx-* attributes (see comixium.css): reader controls
  // layout/position/size, the mobile optimizations switch, and which page this is.
  function pageKey() {
    var path = location.pathname.replace(/\/+$/, '') || '/';
    if (path === '/user') return 'user-' + (new URLSearchParams(location.search).get('tab') || 'titles');
    if (path === '/' || path === '/home') return 'home';
    return path.split('/')[1] || 'home';
  }
  function setAttr(root, name, value) {
    if (value === null) { if (root.hasAttribute(name)) root.removeAttribute(name); }
    else if (root.getAttribute(name) !== value) root.setAttribute(name, value);
  }
  function applyControls() {
    var root = document.documentElement;
    if (!root) return;
    setAttr(root, 'data-cx-mobile', prefs.mobile ? '' : null);
    setAttr(root, 'data-cx-hide', prefs.controlsHide ? prefs.controlsHide.split(',').join(' ') : null);
    setAttr(root, 'data-cx-page', pageKey());
    [['layout', 'data-cx-layout'], ['x', 'data-cx-x'], ['y', 'data-cx-y'], ['size', 'data-cx-size']].forEach(function (a) {
      var v = prefs[a[0]];
      if (v === DEFAULTS[a[0]]) { if (root.hasAttribute(a[1])) root.removeAttribute(a[1]); }
      else if (root.getAttribute(a[1]) !== v) root.setAttribute(a[1], v);
    });
  }

  function isReader() {
    return !!(document.body && document.body.classList.contains('rpage-body'));
  }

  /*
   * One step forward (dir=1) or back (dir=-1) in the chapter reader, through the reader's own
   * keyboard shortcuts so its settings apply: ArrowDown/ArrowUp scroll a long strip by the
   * user's scroll distance; s/w turn pages in paged mode regardless of reading direction.
   */
  function readerStep(dir) {
    var r = document.querySelector('.rpage');
    if (!r) return false;
    var paged = !r.classList.contains('rpage--long-strip');
    var key = paged ? (dir > 0 ? 's' : 'w') : (dir > 0 ? 'ArrowDown' : 'ArrowUp');
    var code = paged ? (dir > 0 ? 'KeyS' : 'KeyW') : key;
    window.dispatchEvent(new KeyboardEvent('keydown', { key: key, code: code, bubbles: true, cancelable: true }));
    return true;
  }

  // Reader taps. The app handles every one-finger tap on the page itself so each behaviour can be
  // configured: edge zones (sides or top/bottom, three sizes, invertible) turn the page in paged
  // mode or scroll a screen in long strip, and the middle shows/hides the reader controls with a
  // double tap, a single tap, or not at all. The tap is kept from the reader's own handler, but its
  // page-turn library (which tracks touches on document) still gets the touch end.
  var tap = null, touches = 0, lastCenterTap = 0, redispatching = false;
  var ZONES = { small: 0.2, medium: 0.25, large: 0.35 };
  function onPage(t) {
    return t && t.closest && t.closest('.rpage-main') && !t.closest('button, a, input, textarea, select, label');
  }
  function toggleControls() {
    window.dispatchEvent(new KeyboardEvent('keydown', { key: 'x', code: 'KeyX', bubbles: true, cancelable: true }));
  }
  function centerTap() {
    if (prefs.controlsTap === 'off') return;
    if (prefs.controlsTap === 'single') { toggleControls(); return; }
    var now = Date.now();
    if (now - lastCenterTap < 320) { lastCenterTap = 0; toggleControls(); } else lastCenterTap = now;
  }
  function tapZone(e, paged, r) {
    var vv = window.visualViewport;
    var fx = vv ? (e.clientX - vv.offsetLeft) / vv.width : e.clientX / window.innerWidth;
    var fy = vv ? (e.clientY - vv.offsetTop) / vv.height : e.clientY / window.innerHeight;
    var z = ZONES[prefs.tapZone] || 0.25;
    var zone;
    if (prefs.tapLayout === 'vertical') {
      zone = fy < z ? -1 : fy > 1 - z ? 1 : 0;
    } else {
      zone = fx < z ? -1 : fx > 1 - z ? 1 : 0;
      if (paged && r.classList.contains('rpage--rtl')) zone = -zone; // right-to-left books: left is next
    }
    return prefs.invertTaps ? -zone : zone;
  }
  window.addEventListener('pointerdown', function (e) {
    if (redispatching || e.pointerType !== 'touch') return;
    touches++;
    tap = touches === 1 && onPage(e.target) ? { x: e.clientX, y: e.clientY, t: Date.now(), id: e.pointerId } : null;
  }, true);
  window.addEventListener('pointercancel', function (e) {
    if (e.pointerType === 'touch') touches = Math.max(0, touches - 1);
    tap = null;
  }, true);
  window.addEventListener('pointerup', function (e) {
    if (redispatching || e.pointerType !== 'touch') return;
    touches = Math.max(0, touches - 1);
    var t = tap;
    tap = null;
    if (!t || e.pointerId !== t.id) return;
    if (Math.abs(e.clientX - t.x) > 10 || Math.abs(e.clientY - t.y) > 10 || Date.now() - t.t > 350) return;
    var r = document.querySelector('.rpage');
    if (!r) return;
    e.stopPropagation();
    redispatching = true;
    try {
      document.dispatchEvent(new PointerEvent('pointerup', {
        pointerId: e.pointerId, pointerType: 'touch', isPrimary: e.isPrimary,
        clientX: e.clientX, clientY: e.clientY, screenX: e.screenX, screenY: e.screenY, button: e.button
      }));
    } finally { redispatching = false; }
    var paged = !r.classList.contains('rpage--long-strip');
    var zone = tapZone(e, paged, r);
    if (zone !== 0 && (paged ? prefs.tapPaged : prefs.tapEdges)) { readerStep(zone); return; }
    centerTap();
  }, true);

  // --- One settings sheet: the app's reader options inside the site's own settings panel --------
  // The reader's gear opens the site's settings (a bottom sheet with mobile optimizations); these
  // sections are appended to it in the same markup, so there is one place for every reader option.
  // Changes apply here at once and are sent to the app, which stores them and syncs other pages.
  // Laid out like the app's own settings: switches for on/off options, segmented choices, and chips
  // for the buttons shown. Rows: [type, pref key, label, hint or options, only-when].
  var SECTIONS = [
    ['Tapping', [
      ['switch', 'tapPaged', 'Tap to turn pages', 'Paged mode: tap the edges'],
      ['switch', 'tapEdges', 'Tap to scroll', 'Long strip: tap the edges to move a screen'],
      ['choice', 'tapLayout', 'Tap zones', [['sides', 'Left & right'], ['vertical', 'Top & bottom']], 'taps'],
      ['choice', 'tapZone', 'Tap zone size', [['small', 'Small'], ['medium', 'Medium'], ['large', 'Large']], 'taps'],
      ['switch', 'invertTaps', 'Invert tap zones', 'Left / top goes forward', 'taps'],
      ['choice', 'controlsTap', 'Show controls with', [['double', 'Double tap'], ['single', 'Single tap'], ['off', 'Off']]]
    ]],
    ['Screen & keys', [
      ['switch', 'immersive', 'Full screen', 'Hide the status and navigation bars'],
      ['switch', 'keepOn', 'Keep screen on', 'While a chapter is open'],
      ['switch', 'volumeKeys', 'Volume keys turn pages', 'Uses your scroll distance'],
      ['switch', 'volumeInvert', 'Invert volume keys', 'Volume up goes forward', 'volume'],
      ['switch', 'pinchZoom', 'Pinch to zoom', 'Zoom into a page with two fingers'],
      ['switch', 'longPress', 'Long-press image menu', 'Save, share or copy a page']
    ]],
    ['Controls', [
      ['choice', 'layout', 'Style', [['rail', 'Rail'], ['bar', 'Bar']]],
      ['checks', 'controlsHide', 'Buttons shown', 'The settings button always stays'],
      ['choice', 'y', 'Position', [['top', 'Top'], ['bottom', 'Bottom']]],
      ['choice', 'x', 'Side', [['left', 'Left'], ['center', 'Center'], ['right', 'Right']], 'rail'],
      ['choice', 'size', 'Size', [['normal', 'Normal'], ['large', 'Large']]]
    ]]
  ];
  // Which reader control buttons to show (the settings button always stays).
  var CONTROL_ITEMS = [
    ['chapter', 'Chapter switcher'], ['home', 'Home'], ['series', 'Series page'],
    ['comments', 'Comments'], ['chapters', 'Chapter list'], ['autoscroll', 'Auto-scroll'], ['download', 'Download']
  ];
  var GESTURES_HINT = 'Swipe to turn pages or scroll, pinch to zoom, tap or drag the progress bar to jump, ' +
    'and use Back to leave the chapter. Tap zones and the controls tap work as set above.';
  var CHECK_SVG = '<svg viewBox="0 0 24 24" width="14" height="14" aria-hidden="true"><path d="M20 6 9 17l-5-5" fill="none" ' +
    'stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"/></svg>';
  function hiddenControls() {
    return prefs.controlsHide ? prefs.controlsHide.split(',') : [];
  }
  // Rows that only matter while another option is on are greyed out otherwise.
  function rowEnabled(when) {
    if (when === 'taps') return prefs.tapPaged || prefs.tapEdges;
    if (when === 'volume') return prefs.volumeKeys;
    if (when === 'rail') return prefs.layout !== 'bar'; // the bar always spans the width
    return true;
  }
  function esc(t) { return String(t).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/"/g, '&quot;'); }
  function renderRow(r) {
    var type = r[0], key = r[1];
    if (type === 'switch') {
      return '<div class="cx-row cx-row--switch" role="switch" tabindex="0" data-key="' + key + '" data-when="' + (r[4] || '') + '">' +
        '<span class="cx-row__text"><span class="cx-row__label">' + esc(r[2]) + '</span>' +
        '<span class="cx-row__hint">' + esc(r[3]) + '</span></span><span class="cx-switch" aria-hidden="true"></span></div>';
    }
    var out = '<div class="cx-row cx-row--choice" data-when="' + (r[4] || '') + '"><span class="cx-row__text">' +
      '<span class="cx-row__label">' + esc(r[2]) + '</span>' + (type === 'checks' ? '<span class="cx-row__hint">' + esc(r[3]) + '</span>' : '') + '</span>';
    if (type === 'checks') {
      out += '<div class="cx-chips">';
      for (var i = 0; i < CONTROL_ITEMS.length; i++) {
        out += '<span class="cx-chip" role="checkbox" tabindex="0" data-check="' + CONTROL_ITEMS[i][0] + '">' + CHECK_SVG + esc(CONTROL_ITEMS[i][1]) + '</span>';
      }
      return out + '</div></div>';
    }
    out += '<div class="cx-seg" role="radiogroup" data-key="' + key + '">';
    for (var j = 0; j < r[3].length; j++) {
      out += '<span class="cx-seg__opt" role="radio" tabindex="0" data-v="' + r[3][j][0] + '">' + esc(r[3][j][1]) + '</span>';
    }
    return out + '</div></div>';
  }
  function renderSettings() {
    var out = '';
    for (var i = 0; i < SECTIONS.length; i++) {
      out += '<section class="rpage-settings__group cx-group"><h3 class="rpage-settings__title mono">' + SECTIONS[i][0] + '</h3><div class="cx-card">';
      for (var j = 0; j < SECTIONS[i][1].length; j++) out += renderRow(SECTIONS[i][1][j]);
      out += '</div></section>';
    }
    return out + '<p class="rpage-settings__hint">' + GESTURES_HINT + '</p>';
  }
  // Rows are drawn once and then updated in place, so switches animate instead of being redrawn.
  function syncSettings(box) {
    var i, el;
    var rows = box.querySelectorAll('.cx-row');
    for (i = 0; i < rows.length; i++) rows[i].classList.toggle('is-disabled', !rowEnabled(rows[i].getAttribute('data-when')));
    var switches = box.querySelectorAll('.cx-row--switch');
    for (i = 0; i < switches.length; i++) {
      el = switches[i];
      setAttr(el, 'aria-checked', prefs[el.getAttribute('data-key')] ? 'true' : 'false');
    }
    var opts = box.querySelectorAll('.cx-seg__opt');
    for (i = 0; i < opts.length; i++) {
      el = opts[i];
      var on = String(prefs[el.parentElement.getAttribute('data-key')]) === el.getAttribute('data-v');
      el.classList.toggle('is-on', on);
      setAttr(el, 'aria-checked', on ? 'true' : 'false');
    }
    var hidden = hiddenControls();
    var chips = box.querySelectorAll('.cx-chip');
    for (i = 0; i < chips.length; i++) {
      el = chips[i];
      var shown = hidden.indexOf(el.getAttribute('data-check')) < 0;
      el.classList.toggle('is-on', shown);
      setAttr(el, 'aria-checked', shown ? 'true' : 'false');
    }
  }
  function changePref(key, value) {
    var change = {};
    change[key] = value;
    window.__comixium.setPrefs(change);
    post({ t: 'pref', key: key, value: value });
  }
  function onSettingClick(e) {
    var t = e.target;
    if (!t || !t.closest) return;
    var row = t.closest('.cx-row');
    if (!row || row.classList.contains('is-disabled')) return;
    var chip = t.closest('.cx-chip'), opt = t.closest('.cx-seg__opt');
    if (chip) {
      var hidden = hiddenControls(), id = chip.getAttribute('data-check'), at = hidden.indexOf(id);
      if (at >= 0) hidden.splice(at, 1); else hidden.push(id);
      changePref('controlsHide', hidden.join(','));
    } else if (opt) {
      var raw = opt.getAttribute('data-v');
      changePref(opt.parentElement.getAttribute('data-key'), raw === 'true' ? true : raw === 'false' ? false : raw);
    } else if (row.classList.contains('cx-row--switch')) {
      var key = row.getAttribute('data-key');
      changePref(key, !prefs[key]);
    } else {
      return;
    }
    e.preventDefault();
    e.stopPropagation();
  }
  function readerSettingsSections() {
    var body = document.querySelector('.rpage-settings__body');
    if (!body) return;
    var box = body.querySelector(':scope > #cx-reader-settings');
    if (!box) {
      box = document.createElement('div');
      box.id = 'cx-reader-settings';
      box.addEventListener('click', onSettingClick);
      body.appendChild(box);
    }
    if (!box.firstChild) box.innerHTML = renderSettings();
    syncSettings(box);
    // The site's own "Hide controls: single / double tap" no longer applies (the app handles
    // those taps; see "Show controls with"), so hide it rather than show two settings for one thing.
    var titles = body.querySelectorAll(':scope > .rpage-settings__group > .rpage-settings__title');
    for (var i = 0; i < titles.length; i++) {
      if (/^hide controls$/i.test(titles[i].textContent.trim())) titles[i].parentElement.classList.add('cx-hidden');
    }
  }

  // --- Bottom sheets and the comments drawer: swipe down to close (mobile optimizations) -------
  var drag = null;
  function sheetFor(t) {
    if (!prefs.mobile || !t || !t.closest) return null;
    return t.closest('.minidlg__panel') || t.closest('.rpage-cmpanel.is-open');
  }
  function scrollerIn(el, sheet) {
    for (var n = el; n && n !== sheet.parentElement; n = n.parentElement) {
      if (n.scrollHeight > n.clientHeight + 1) {
        var oy = window.getComputedStyle(n).overflowY;
        if (oy === 'auto' || oy === 'scroll') return n;
      }
    }
    return null;
  }
  document.addEventListener('touchstart', function (e) {
    var sheet = e.touches.length === 1 ? sheetFor(e.target) : null;
    drag = sheet ? { sheet: sheet, x0: e.touches[0].clientX, y0: e.touches[0].clientY, t0: Date.now(), dy: 0,
      active: false, done: false, scroller: scrollerIn(e.target, sheet) } : null;
  }, { passive: true });
  document.addEventListener('touchmove', function (e) {
    if (!drag || drag.done) return;
    var t = e.touches[0];
    var dy = t.clientY - drag.y0, dx = t.clientX - drag.x0;
    if (!drag.active) {
      if (Math.abs(dy) < 8 && Math.abs(dx) < 8) return;
      // Only a downward, mostly vertical pull with nothing scrolled above it drags the sheet.
      if (dy < 0 || Math.abs(dx) > Math.abs(dy) || (drag.scroller && drag.scroller.scrollTop > 0)) { drag.done = true; return; }
      drag.active = true;
      drag.y0 = t.clientY;
      drag.t0 = Date.now();
      drag.sheet.style.transition = 'none';
      dy = 0;
    }
    drag.dy = Math.max(0, dy);
    drag.sheet.style.transform = 'translateY(' + drag.dy + 'px)';
    e.preventDefault();
  }, { passive: false });
  document.addEventListener('touchend', function () {
    var d = drag;
    drag = null;
    if (!d || !d.active) return;
    var h = d.sheet.getBoundingClientRect().height || 1;
    var speed = d.dy / Math.max(1, Date.now() - d.t0);
    d.sheet.style.transition = 'transform .18s cubic-bezier(.2,.8,.2,1)';
    if (d.dy > h * 0.25 || speed > 0.7) {
      d.sheet.style.transform = 'translateY(100%)';
      setTimeout(function () {
        window.__comixium.closeDialog();
        setTimeout(function () { d.sheet.style.transform = ''; d.sheet.style.transition = ''; }, 300);
      }, 170);
    } else {
      d.sheet.style.transform = '';
      setTimeout(function () { d.sheet.style.transition = ''; }, 200);
    }
  }, { passive: true });

  // --- Reading position guard (long strip) -----------------------------------------------------
  // Keeps the page you're reading in place if the reader moves without you touching it (content
  // loading or changing size, something taking focus). It stays out of the way while you scroll or
  // fling, right after a chapter opens (so the site can restore your place), and during auto-scroll.
  var lastInput = 0, chapterOpenedAt = 0, guardAnchor = null, guardUrl = '';
  ['touchstart', 'touchmove', 'touchend', 'pointerdown', 'wheel', 'keydown'].forEach(function (type) {
    window.addEventListener(type, function () { lastInput = Date.now(); }, { capture: true, passive: true });
  });
  function guardActive() {
    var r = document.querySelector('.rpage.rpage--long-strip');
    if (!r) return false;
    if (location.pathname !== guardUrl) { guardUrl = location.pathname; chapterOpenedAt = Date.now(); guardAnchor = null; }
    if (Date.now() - chapterOpenedAt < 4000 || Date.now() - lastInput < 2500) return false;
    return !document.querySelector('.rpage-floatctl button[aria-pressed="true"]'); // auto-scroll playing
  }
  function pageAtCenter() {
    var el = document.elementFromPoint(window.innerWidth / 2, window.innerHeight / 2);
    return el && el.closest ? el.closest('.rpage-page') : null;
  }
  function saveAnchor() {
    var pg = pageAtCenter();
    guardAnchor = pg ? { el: pg, top: pg.getBoundingClientRect().top } : null;
  }
  function keepPosition() {
    if (!guardActive()) { saveAnchor(); return; }
    if (!guardAnchor || !guardAnchor.el.isConnected) { saveAnchor(); return; }
    var moved = guardAnchor.el.getBoundingClientRect().top - guardAnchor.top;
    if (Math.abs(moved) > 2) window.scrollBy(0, moved);
  }
  var guardFrame = 0;
  window.addEventListener('scroll', function () {
    if (guardFrame) return;
    guardFrame = requestAnimationFrame(function () { guardFrame = 0; keepPosition(); });
  }, { passive: true });
  if (window.ResizeObserver) {
    var sizeWatch = new ResizeObserver(function () { keepPosition(); });
    setInterval(function () {
      var main = document.querySelector('.rpage-main');
      if (main && !main.__cxWatched) { main.__cxWatched = true; sizeWatch.observe(main); }
    }, 1000);
  }

  // Pinch zoom: the site locks zoom with its viewport tag; relax it on chapter pages only.
  var originalViewport = null;
  var ZOOMABLE = 'width=device-width, initial-scale=1, minimum-scale=1, maximum-scale=5, user-scalable=yes, viewport-fit=cover';
  function applyZoom() {
    var m = document.querySelector('meta[name="viewport"]');
    if (!m) return;
    if (originalViewport === null) originalViewport = m.getAttribute('content') || '';
    var want = isReader() && prefs.pinchZoom ? ZOOMABLE : originalViewport;
    if (m.getAttribute('content') !== want) m.setAttribute('content', want);
  }

  // --- Mobile optimizations (see the html[data-cx-mobile] rules in comixium.css) ---------------
  // Hover styles stick after a tap on touch screens: move the site's :hover rules under
  // @media (hover: hover) so they only apply with a real pointer. Runs once per stylesheet; turning
  // the option off reloads the page, which restores the originals.
  function fixHover() {
    if (!prefs.mobile) return;
    for (var i = 0; i < document.styleSheets.length; i++) {
      var sheet = document.styleSheets[i];
      if (sheet.__cxHover) continue;
      var rules;
      try { rules = sheet.cssRules; } catch (e) { continue; } // cross-origin (fonts)
      if (!rules) continue;
      sheet.__cxHover = true;
      moveHoverRules(sheet, rules);
    }
  }
  function moveHoverRules(parent, rules) {
    for (var j = rules.length - 1; j >= 0; j--) {
      var r = rules[j];
      if (r.type === 1 && r.selectorText && r.selectorText.indexOf(':hover') >= 0) {
        var text = r.cssText;
        try {
          parent.deleteRule(j);
          parent.insertRule('@media (hover: hover) { ' + text + ' }', j);
        } catch (e) { /* leave it */ }
      } else if (r.type === 4 && r.cssRules && !/hover/.test(r.conditionText || '')) {
        moveHoverRules(r, r.cssRules);
      }
    }
  }

  // Sideways-scrolling chip rows: mark which edge has more, for the fade in comixium.css.
  function markScrollers() {
    if (!prefs.mobile) return;
    var els = document.querySelectorAll('.useg--scroll, .utabs');
    for (var i = 0; i < els.length; i++) {
      var el = els[i];
      if (!el.__cxScroll) {
        el.__cxScroll = true;
        el.addEventListener('scroll', function () { markScroller(this); }, { passive: true });
      }
      markScroller(el);
    }
  }
  function markScroller(el) {
    var more = el.scrollWidth - el.clientWidth > 2;
    var start = more && el.scrollLeft > 2;
    var end = more && el.scrollLeft < el.scrollWidth - el.clientWidth - 2;
    setAttr(el, 'data-cx-more', start && end ? 'both' : start ? 'start' : end ? 'end' : null);
  }

  // --- Series page header: keep badges and stats beside the cover only if they fit ------------
  function fitSeriesHeader() {
    var hero = document.querySelector('.mpage__hero-inner');
    if (!hero) return;
    if (!prefs.mobile) { hero.classList.remove('cx-head-stacked'); return; }
    var poster = hero.querySelector('.mpage__poster');
    var parts = ['.mpage__title-row', '.mpage__badges', '.mpage__byline'].map(function (sel) { return hero.querySelector(sel); });
    if (!poster || !parts[0]) return;
    hero.classList.remove('cx-head-stacked'); // measure the side-by-side layout
    var bottom = 0;
    for (var i = 0; i < parts.length; i++) if (parts[i]) bottom = Math.max(bottom, parts[i].getBoundingClientRect().bottom);
    if (bottom > poster.getBoundingClientRect().bottom + 2) hero.classList.add('cx-head-stacked');
  }
  window.addEventListener('resize', function () { fitSeriesHeader(); });

  // --- Home page: dismissible announcement and share bar -------------------------------------
  var DISMISSED_KEY = 'comixium.dismissed';
  function loadDismissed() {
    try { return JSON.parse(localStorage.getItem(DISMISSED_KEY) || '[]'); } catch (e) { return []; }
  }
  function saveDismissed(list) {
    try { localStorage.setItem(DISMISSED_KEY, JSON.stringify(list.slice(-50))); } catch (e) { /* private mode */ }
  }
  function textKey(t) {
    var h = 5381;
    for (var i = 0; i < t.length; i++) h = ((h << 5) + h + t.charCodeAt(i)) | 0;
    return 'a' + (h >>> 0).toString(36);
  }
  function addClose(el, label, onClose) {
    el.classList.add('cx-dismissible');
    if (el.querySelector(':scope > .cx-close')) return;
    var b = document.createElement('button');
    b.type = 'button';
    b.className = 'cx-close';
    b.setAttribute('aria-label', label);
    b.textContent = '\u00d7';
    b.addEventListener('click', function (e) { e.preventDefault(); e.stopPropagation(); onClose(); });
    el.appendChild(b);
  }
  function homeBlocks() {
    if (pageKey() !== 'home') return;
    var dismissed = loadDismissed();
    var notes = document.querySelectorAll('aside.announce');
    for (var i = 0; i < notes.length; i++) {
      (function (a) {
        // textContent, not innerText: innerText changes once the element is hidden.
        var key = textKey((a.textContent || '').replace(/\u00d7/g, '').replace(/\s+/g, ' ').trim());
        a.classList.toggle('cx-hidden', !prefs.announce || dismissed.indexOf(key) >= 0);
        addClose(a, 'Dismiss announcement', function () {
          var list = loadDismissed();
          list.push(key);
          saveDismissed(list);
          a.classList.add('cx-hidden');
        });
      })(notes[i]);
    }
    var shares = document.querySelectorAll('.share-block');
    for (var j = 0; j < shares.length; j++) {
      shares[j].classList.toggle('cx-hidden', !prefs.share);
      addClose(shares[j], 'Hide share bar', function () {
        prefs.share = false;
        try { localStorage.setItem(PREFS_KEY, JSON.stringify(prefs)); } catch (e) { /* private mode */ }
        homeBlocks();
        post({ t: 'pref', key: 'share', value: false });
      });
    }
  }

  // --- Downloads: a button on each chapter row of a series page, and one in the reader ----------
  // The app owns the downloads; it sends each chapter's state with setDownloads (id -> state).
  var dlStates = {};
  var DL_SVG = '<svg viewBox="0 0 24 24" width="18" height="18" aria-hidden="true"><path d="M12 4v11m0 0-4.5-4.5M12 15l4.5-4.5M5 19.5h14" ' +
    'fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/></svg>';
  var DL_DONE_SVG = '<svg viewBox="0 0 24 24" width="18" height="18" aria-hidden="true"><path d="m6 12.5 4 4 8-9M5 19.5h14" ' +
    'fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/></svg>';
  var DL_LABELS = {
    done: 'Downloaded', queued: 'Download waiting', preparing: 'Preparing download', active: 'Downloading',
    failed: 'Download failed, tap for options'
  };
  function chapterIdOf(href) {
    var m = /\/(\d+)-chapter-[^/?#]+\/?$/.exec(String(href || '').split(/[?#]/)[0]);
    return m ? m[1] : '';
  }
  function paintDownload(btn, id) {
    var st = dlStates[id] || '';
    var kind = st.indexOf('active:') === 0 ? 'active' : st;
    setAttr(btn, 'data-state', kind || null);
    btn.style.setProperty('--cx-p', kind === 'active' ? st.slice(7) : '0');
    btn.setAttribute('aria-label', DL_LABELS[kind] || 'Download chapter');
    var icon = kind === 'done' ? 'done' : 'dl';
    if (btn.getAttribute('data-icon') !== icon) {
      btn.setAttribute('data-icon', icon);
      btn.innerHTML = icon === 'done' ? DL_DONE_SVG : DL_SVG;
    }
  }
  function makeDownloadButton(cls, onTap) {
    var b = document.createElement('button');
    b.type = 'button';
    b.className = cls;
    b.addEventListener('click', function (e) { e.preventDefault(); e.stopPropagation(); onTap(); });
    return b;
  }
  function chapterRows() {
    if (pageKey() !== 'title') return;
    var rows = document.querySelectorAll('.mchap-item');
    for (var i = 0; i < rows.length; i++) {
      var row = rows[i];
      var a = row.querySelector('a.mchap-row__primary[href*="-chapter-"]');
      if (!a) continue;
      var btn = row.querySelector(':scope > .cx-dl');
      if (!btn) {
        btn = makeDownloadButton('cx-dl', (function (row) {
          return function () {
            // Read the row at tap time: the list reuses rows when paging.
            var link = row.querySelector('a.mchap-row__primary[href*="-chapter-"]');
            if (!link) return;
            var ch = row.querySelector('.mchap-row__ch');
            var group = row.querySelector('.mchap-row__group span');
            var title = document.querySelector('.mpage__title');
            var cover = document.querySelector('.mpage__poster img');
            post({
              t: 'download', path: link.getAttribute('href'),
              label: ch ? ch.textContent.trim() : '', group: group ? group.textContent.trim() : '',
              series: title ? title.textContent.trim() : '', cover: cover ? (cover.currentSrc || cover.src || '') : ''
            });
          };
        })(row));
        row.insertBefore(btn, row.querySelector(':scope > .mchap-bookmark'));
      }
      paintDownload(btn, chapterIdOf(a.getAttribute('href')));
    }
  }
  function readerDownload() {
    var col = document.body && document.body.classList.contains('rpage-body') && document.querySelector('.rpage-floatctl__col');
    if (!col) return;
    var btn = col.querySelector('.cx-dl-reader');
    if (!btn) {
      btn = makeDownloadButton('rpage-floatctl__btn cx-dl cx-dl-reader', function () {
        post({ t: 'download', path: location.pathname, label: '', group: '', series: '', cover: '' });
      });
      col.insertBefore(btn, col.querySelector('[aria-label="Settings"]'));
    }
    paintDownload(btn, chapterIdOf(location.pathname));
  }

  // A series page's own Download button: the app asks what to download (all, unread, how many,
  // which groups). The page's group filter knows every group; it's opened out of sight to read them.
  function seriesGroups() {
    return new Promise(function (resolve) {
      var wrap = document.querySelector('.mpage__group');
      var trigger = wrap && wrap.querySelector(':scope > button');
      function read() {
        return [].map.call(wrap.querySelectorAll('.fdrop__item'), function (i) {
          var spans = i.querySelectorAll('span');
          return (spans[spans.length - 1] || i).textContent.trim();
        }).filter(function (t) { return t && !/^all groups$/i.test(t); });
      }
      if (!trigger) return resolve([]);
      if (wrap.classList.contains('is-open')) return resolve(read());
      setAttr(document.documentElement, 'data-cx-peek', '');
      trigger.click();
      var tries = 0;
      (function poll() {
        var got = read();
        if (got.length || ++tries > 40) {
          if (wrap.classList.contains('is-open')) trigger.click();
          setTimeout(function () { setAttr(document.documentElement, 'data-cx-peek', null); }, 80);
          return resolve(got);
        }
        setTimeout(poll, 50);
      })();
    });
  }
  function offerSeriesDownload() {
    var title = document.querySelector('.mpage__title');
    var cover = document.querySelector('.mpage__poster img');
    var read = document.querySelector('.mpage__read');
    var m = read && /(\d+(?:\.\d+)?)/.exec(read.textContent || '');
    seriesGroups().then(function (groups) {
      if (!groups.length) {
        [].forEach.call(document.querySelectorAll('.mchap-row__group span'), function (g) {
          var t = g.textContent.trim();
          if (t && groups.indexOf(t) < 0) groups.push(t);
        });
      }
      post({
        t: 'downloadMany', path: location.pathname,
        series: title ? title.textContent.trim() : '', cover: cover ? (cover.currentSrc || cover.src || '') : '',
        groups: groups, current: m ? m[1] : ''
      });
    });
  }
  function seriesDownload() {
    var cta = document.querySelector('.mpage__cta');
    if (!cta || cta.querySelector('.cx-dl-series')) return;
    var b = makeDownloadButton('mpage__cta-icon cx-dl-series', offerSeriesDownload);
    b.setAttribute('aria-label', 'Download chapters');
    b.title = 'Download';
    b.innerHTML = DL_SVG;
    var more = cta.querySelector('[aria-label="More actions"]');
    if (more && more.parentNode) more.parentNode.insertBefore(b, more); else cta.appendChild(b);
  }

  // Reaching the end of a chapter (the reader's "next chapter" block comes into view) counts as
  // having read it: the app can delete a downloaded chapter afterwards (a setting).
  var endTarget = null, endObserver = null, endSent = '', chapterSince = 0, chapterPath = '';
  function watchReadEnd() {
    if (!isReader()) return;
    if (chapterPath !== location.pathname) { chapterPath = location.pathname; chapterSince = Date.now(); }
    var el = document.querySelector('.rpage-chap-ending');
    if (!el || el === endTarget || !window.IntersectionObserver) return;
    endTarget = el;
    if (endObserver) endObserver.disconnect();
    endObserver = new IntersectionObserver(function (entries) {
      entries.forEach(function (e) {
        // Not while the chapter is still laying out (the block can flash into view at first).
        if (!e.isIntersecting || endSent === location.pathname || Date.now() - chapterSince < 4000) return;
        if (!document.querySelector('.rpage-page')) return;
        endSent = location.pathname;
        post({ t: 'readEnd', path: location.pathname });
      });
    }, { threshold: 0.1 });
    endObserver.observe(el);
  }

  // --- State reporting ----------------------------------------------------------------------
  var VARS = {
    bg: '--bg', bg2: '--bg-2', surface: '--surface', surface2: '--surface-2', surface3: '--surface-3',
    textEmphasis: '--text-emphasis', text: '--text', text2: '--text-2', text3: '--text-3', text4: '--text-4',
    accent: '--accent', accent2: '--accent-2', accentInk: '--accent-ink', danger: '--danger',
    readerBg: '--reader-bg'
  };
  // Reading 15 custom properties forces a style pass, so do it only when <html> changes
  // (theme switches set data-theme / inline variables there), not on every DOM update.
  var themeCache = null;
  function theme() {
    if (themeCache) return themeCache;
    var cs = window.getComputedStyle(document.documentElement);
    var out = {};
    for (var k in VARS) out[k] = cs.getPropertyValue(VARS[k]).trim();
    if (out.bg) themeCache = out;
    return out;
  }
  function auth() {
    if (document.querySelector('.topnav .usermenu')) return 'in';
    if (document.querySelector('.topnav .login-btn')) return 'out';
    return '';
  }
  // Anything Back should close before leaving the page: dialogs, sheets, menus, dropdowns, panels.
  var OVERLAYS = [
    '.auth', '.settings-dialog', '.minidlg', '[role="dialog"][aria-modal="true"]',
    '[role="menu"]', '[role="listbox"]', '.fdrop__pop', '.nav-menu__pop', '.browse-pop',
    '.usermenu__pop', '.notifpop', '.search-pop', '.rpage-cmpanel.is-open'
  ].join(', ');
  function isShown(el) {
    if (!el.isConnected || !el.getClientRects().length) return false;
    var cs = window.getComputedStyle(el);
    return cs.display !== 'none' && cs.visibility !== 'hidden' && cs.opacity !== '0';
  }
  function openOverlay() {
    var els = document.querySelectorAll(OVERLAYS);
    for (var i = els.length - 1; i >= 0; i--) if (isShown(els[i])) return els[i]; // topmost last
    return null;
  }
  function dialogOpen() {
    return !!openOverlay();
  }
  // A view inside the site's settings (Content preferences, Custom theme) has its own back button;
  // its title goes to the app's top bar and Back returns to the main settings view.
  function settingsSubpage() {
    var d = document.querySelector('.settings-dialog');
    if (!d || !d.querySelector('.settings-dialog__back')) return '';
    var h = d.querySelector('.settings-dialog__title');
    return h ? h.textContent.trim() : '';
  }
  function snapshot() {
    var b = document.body;
    return {
      t: 'state',
      url: location.href,
      title: document.title || '',
      reader: !!(b && b.classList.contains('rpage-body')),
      auth: auth(),
      dialog: dialogOpen(),
      sub: settingsSubpage(),
      tone: document.documentElement.getAttribute('data-tone') || '',
      theme: theme(),
      traps: trapsRemoved
    };
  }
  var last = '';
  function report() {
    if (!document.documentElement) return;
    applyZoom();
    applyControls();
    fixHover();
    markScrollers();
    homeBlocks();
    readerSettingsSections();
    fitSeriesHeader();
    chapterRows();
    readerDownload();
    seriesDownload();
    watchReadEnd();
    var s = snapshot();
    var key = JSON.stringify(s);
    if (key !== last) { last = key; post(s); }
  }
  // Report when the page is idle (not mid-scroll or mid-animation), at most ~4 times a second.
  var timer = 0;
  var idle = window.requestIdleCallback || function (cb) { return setTimeout(cb, 1); };
  function schedule() {
    if (timer) return;
    timer = setTimeout(function () {
      idle(function () { timer = 0; report(); }, { timeout: 500 });
    }, 250);
  }

  ['pushState', 'replaceState'].forEach(function (m) {
    var orig = history[m];
    history[m] = function () {
      var r = orig.apply(this, arguments);
      schedule();
      return r;
    };
  });
  window.addEventListener('popstate', schedule);

  // Popunder links and dialogs are added anywhere in the tree; theme/reader flags live on <html>/<body>.
  var attrObserver = new MutationObserver(function (records) {
    for (var i = 0; i < records.length; i++) if (records[i].target === document.documentElement) themeCache = null;
    schedule();
  });
  var watchedRoot = null, watchedBody = null;
  function watchRoots() {
    var root = document.documentElement, body = document.body;
    if (root && root !== watchedRoot) {
      watchedRoot = root;
      applyControls();
      attrObserver.observe(root, { attributes: true, attributeFilter: ['data-theme', 'data-tone', 'style'] });
      addStyle();
    }
    if (body && body !== watchedBody) {
      watchedBody = body;
      attrObserver.observe(body, { attributes: true, attributeFilter: ['class'] });
    }
  }
  new MutationObserver(function (records) {
    var urgent = false;
    for (var i = 0; i < records.length; i++) {
      var added = records[i].addedNodes;
      for (var j = 0; j < added.length; j++) {
        var n = added[j];
        if (n.nodeType !== 1) continue;
        if (n.tagName === 'A' && isTrap(n)) zap(n);
        else if (n.tagName === 'HTML' || n.tagName === 'HEAD' || n.tagName === 'BODY') watchRoots();
        else if (n.matches(OVERLAYS) || n.querySelector(OVERLAYS)) urgent = true;
      }
      var removed = records[i].removedNodes;
      for (var k = 0; k < removed.length && !urgent; k++) {
        var m = removed[k];
        if (m.nodeType === 1 && (m.matches(OVERLAYS) || m.querySelector(OVERLAYS))) urgent = true;
      }
    }
    // Popups opening or closing are reported right away, so Back knows about them immediately.
    if (urgent) { readerSettingsSections(); setTimeout(report, 0); }
    schedule();
  }).observe(document, { childList: true, subtree: true });
  watchRoots();

  function onReady() {
    watchRoots();
    addStyle();
    var traps = document.querySelectorAll('body > a');
    for (var i = 0; i < traps.length; i++) if (isTrap(traps[i])) zap(traps[i]);
    report();
  }
  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', onReady);
  else onReady();
  window.addEventListener('load', schedule);

  // --- Helpers the app calls via evaluateJavascript -----------------------------------------
  function click(sel) {
    var el = document.querySelector(sel);
    if (el) { el.click(); return true; }
    return false;
  }
  window.__comixium = {
    v: 1,
    report: function () { last = ''; report(); },
    openSettings: function () { return click('.topnav .settings button'); },
    openLogin: function () { return click('.topnav .login-btn'); },
    openRegister: function () {
      if (!click('.topnav .login-btn')) return false;
      setTimeout(function () {
        var links = document.querySelectorAll('.auth__foot .auth__link, .auth .auth__link');
        for (var i = 0; i < links.length; i++) {
          if (/create an account/i.test(links[i].textContent)) { links[i].click(); break; }
        }
      }, 200);
      return true;
    },
    openUserMenu: function () { return click('.topnav .usermenu button, .topnav .usermenu__btn'); },
    signOut: function () {
      if (!click('.topnav .usermenu__item--signout')) {
        click('.topnav .usermenu button, .topnav .usermenu__btn');
        setTimeout(function () { click('.usermenu__item--signout'); }, 250);
      }
      return true;
    },
    // Closes the topmost popup: its own close button, else Escape, else a press outside it.
    closeDialog: function () {
      var o = openOverlay();
      if (!o) return false;
      var sub = o.querySelector('.settings-dialog__back');
      if (sub) { sub.click(); return true; }
      var btn = o.querySelector('.auth__close, .settings-dialog__close, .rpage-modal__close, [aria-label^="Close"], [aria-label^="close"]');
      if (btn) { btn.click(); return true; }
      if (o.matches('.rpage-cmpanel')) {
        var toggle = document.querySelector('.rpage-floatctl button[aria-label="Toggle comments"]');
        if (toggle) { toggle.click(); return true; }
      }
      if (o.matches('.minidlg')) { o.click(); return true; } // its backdrop closes it
      var esc = { key: 'Escape', code: 'Escape', keyCode: 27, bubbles: true, cancelable: true };
      (document.activeElement || document.body).dispatchEvent(new KeyboardEvent('keydown', esc));
      setTimeout(function () {
        if (openOverlay() !== o) return;
        // Popovers that close on a press outside them.
        document.body.dispatchEvent(new PointerEvent('pointerdown', { bubbles: true }));
        document.body.dispatchEvent(new MouseEvent('mousedown', { bubbles: true }));
        document.body.dispatchEvent(new MouseEvent('click', { bubbles: true }));
      }, 60);
      return true;
    },
    readerStep: readerStep,
    setPrefs: function (p) {
      setPrefsFrom(p);
      try { localStorage.setItem(PREFS_KEY, JSON.stringify(prefs)); } catch (e) { /* private mode */ }
      applyZoom();
      applyControls();
      fixHover();
      markScrollers();
      homeBlocks();
      readerSettingsSections();
      fitSeriesHeader();
      return true;
    },
    setDownloads: function (map) {
      dlStates = map || {};
      chapterRows();
      readerDownload();
      return true;
    },
    scrollByPage: function (dir) {
      window.scrollBy({ top: dir * window.innerHeight * 0.85, behavior: 'smooth' });
      return true;
    },
    scrollY: function () { return window.scrollY || document.documentElement.scrollTop || 0; }
  };
})();
