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

  // --- State reporting ----------------------------------------------------------------------
  var VARS = {
    bg: '--bg', bg2: '--bg-2', surface: '--surface', surface2: '--surface-2', surface3: '--surface-3',
    textEmphasis: '--text-emphasis', text: '--text', text2: '--text-2', text3: '--text-3', text4: '--text-4',
    accent: '--accent', accent2: '--accent-2', accentInk: '--accent-ink', danger: '--danger',
    readerBg: '--reader-bg'
  };
  function theme() {
    var cs = window.getComputedStyle(document.documentElement);
    var out = {};
    for (var k in VARS) out[k] = cs.getPropertyValue(VARS[k]).trim();
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
    var s = snapshot();
    var key = JSON.stringify(s);
    if (key !== last) { last = key; post(s); }
  }
  var timer = 0;
  function schedule() {
    if (timer) return;
    timer = setTimeout(function () { timer = 0; report(); }, 150);
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
  var attrObserver = new MutationObserver(schedule);
  var watchedRoot = null, watchedBody = null;
  function watchRoots() {
    var root = document.documentElement, body = document.body;
    if (root && root !== watchedRoot) {
      watchedRoot = root;
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
    /*
     * One step forward (dir=1) or back (dir=-1) in the chapter reader, through the reader's own
     * keyboard shortcuts so its settings apply: ArrowDown/ArrowUp scroll a long strip by the
     * user's scroll distance; s/w turn pages in paged mode regardless of reading direction.
     */
    readerStep: function (dir) {
      var r = document.querySelector('.rpage');
      if (!r) return false;
      var paged = !r.classList.contains('rpage--long-strip');
      var key = paged ? (dir > 0 ? 's' : 'w') : (dir > 0 ? 'ArrowDown' : 'ArrowUp');
      var code = paged ? (dir > 0 ? 'KeyS' : 'KeyW') : key;
      window.dispatchEvent(new KeyboardEvent('keydown', { key: key, code: code, bubbles: true, cancelable: true }));
      return true;
    },
    scrollByPage: function (dir) {
      window.scrollBy({ top: dir * window.innerHeight * 0.85, behavior: 'smooth' });
      return true;
    },
    scrollY: function () { return window.scrollY || document.documentElement.scrollTop || 0; }
  };
})();
