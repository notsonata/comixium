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
  var prefs = {
    tapEdges: true, pinchZoom: true, layout: 'rail', x: 'right', y: 'top', size: 'normal',
    mobile: true, announce: true, share: true
  };
  var DEFAULTS = { layout: 'rail', x: 'right', y: 'top', size: 'normal' };
  var PREFS_KEY = 'comixium.reader';
  try { var saved = JSON.parse(localStorage.getItem(PREFS_KEY) || 'null'); if (saved) setPrefsFrom(saved); } catch (e) { /* none */ }

  function setPrefsFrom(p) {
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

  // Edge taps in long-strip mode. Handled in the capture phase and stopped there, so quick
  // repeated edge taps scroll repeatedly instead of reaching the reader as a double tap.
  var tap = null, touches = 0;
  function onPage(t) {
    return t && t.closest && t.closest('.rpage-main') && !t.closest('button, a, input, textarea, select, label');
  }
  window.addEventListener('pointerdown', function (e) {
    if (e.pointerType !== 'touch') return;
    touches++;
    tap = touches === 1 && onPage(e.target) ? { x: e.clientX, y: e.clientY, t: Date.now(), id: e.pointerId } : null;
  }, true);
  window.addEventListener('pointercancel', function (e) {
    if (e.pointerType === 'touch') touches = Math.max(0, touches - 1);
    tap = null;
  }, true);
  window.addEventListener('pointerup', function (e) {
    if (e.pointerType !== 'touch') return;
    touches = Math.max(0, touches - 1);
    var t = tap;
    tap = null;
    if (!t || e.pointerId !== t.id || !prefs.tapEdges) return;
    if (Math.abs(e.clientX - t.x) > 10 || Math.abs(e.clientY - t.y) > 10 || Date.now() - t.t > 350) return;
    var r = document.querySelector('.rpage');
    if (!r || !r.classList.contains('rpage--long-strip')) return; // paged: the reader's own edge taps apply
    var vv = window.visualViewport;
    var f = vv ? (e.clientX - vv.offsetLeft) / vv.width : e.clientX / window.innerWidth;
    var dir = f < 0.25 ? -1 : f > 0.75 ? 1 : 0;
    if (!dir) return;
    e.stopPropagation();
    readerStep(dir);
  }, true);

  // The reader's help button lists keyboard shortcuts; show the app's touch-controls sheet instead.
  document.addEventListener('click', function (e) {
    var b = e.target && e.target.closest ? e.target.closest('.rpage-floatctl button') : null;
    if (!b) return;
    var label = (b.getAttribute('aria-label') || b.getAttribute('title') || '').trim();
    if (!/^help$/i.test(label)) return;
    e.preventDefault();
    e.stopImmediatePropagation();
    post({ t: 'readerHelp' });
  }, true);

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
  function dialogOpen() {
    return !!document.querySelector('.auth, .settings-dialog, [role="dialog"][aria-modal="true"]');
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
    for (var i = 0; i < records.length; i++) {
      var added = records[i].addedNodes;
      for (var j = 0; j < added.length; j++) {
        var n = added[j];
        if (n.nodeType !== 1) continue;
        if (n.tagName === 'A' && isTrap(n)) zap(n);
        else if (n.tagName === 'HTML' || n.tagName === 'HEAD' || n.tagName === 'BODY') watchRoots();
      }
    }
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
    closeDialog: function () {
      if (click('.auth__close') || click('.settings-dialog__close')) return true;
      var d = dialogOpen();
      document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', code: 'Escape', keyCode: 27, bubbles: true }));
      return d;
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
      return true;
    },
    scrollByPage: function (dir) {
      window.scrollBy({ top: dir * window.innerHeight * 0.85, behavior: 'smooth' });
      return true;
    },
    scrollY: function () { return window.scrollY || document.documentElement.scrollTop || 0; }
  };
})();
