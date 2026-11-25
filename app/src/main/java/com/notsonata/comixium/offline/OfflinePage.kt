package com.notsonata.comixium.offline

/**
 * The offline reader's page: a downloaded chapter's images in a long strip, served from the app
 * (see [com.notsonata.comixium.ui.OfflineReader]). The WebView does the hard parts: very tall
 * strips, pinch zoom, decoding only what's near the screen. Pure Kotlin, unit-tested.
 */
object OfflinePage {
    /** Where the offline reader's pages are served (WebViewAssetLoader's app-only domain). */
    const val ORIGIN = "https://appassets.androidplatform.net"
    const val PREFIX = "/offline/"

    fun url(link: ChapterLink): String = "$ORIGIN$PREFIX${link.seriesSlug}/${link.chapterId}/index.html"

    /** (series slug, chapter id, file) from a path under [PREFIX], or null if it isn't one. */
    fun parsePath(path: String): Triple<String, String, String>? {
        val m = PATH.matchEntire(path) ?: return null
        return Triple(m.groupValues[1], m.groupValues[2], m.groupValues[3])
    }

    private val PATH = Regex("^([A-Za-z0-9][A-Za-z0-9._-]*)/(\\d+)/([A-Za-z0-9][A-Za-z0-9._-]*)$")

    /**
     * [files]: page file names in order. [sizes]: their pixel sizes, if known, so the page lays
     * out before images load and [startPage] (1-based) can be scrolled to right away.
     */
    fun html(files: List<String>, sizes: List<Pair<Int, Int>>, background: String, startPage: Int, endText: String, nextLabel: String?): String {
        val imgs = files.mapIndexed { i, name ->
            val size = sizes.getOrNull(i)?.takeIf { it.first > 0 && it.second > 0 }
            val dims = if (size != null) " width=\"${size.first}\" height=\"${size.second}\"" else ""
            "<img src=\"${attr(name)}\"$dims loading=\"lazy\" decoding=\"async\" alt=\"Page ${i + 1}\">"
        }.joinToString("\n")
        val next = if (nextLabel != null) "<button type=\"button\" data-go=\"next\">Next: ${text(nextLabel)}</button>" else ""
        return """<!doctype html>
<html><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1, minimum-scale=1, maximum-scale=5">
<style>
html,body{margin:0;padding:0;background:${attr(background)};color:#9aa5a9;font:14px/1.4 system-ui,sans-serif;-webkit-tap-highlight-color:transparent}
img{display:block;width:100%;height:auto;max-width:1000px;margin:0 auto}
.end{padding:36px 16px 56px;text-align:center}
.end button{font:inherit;color:inherit;background:rgba(127,127,127,.18);border:0;border-radius:10px;padding:12px 18px;margin:12px 6px 0}
</style></head><body>
$imgs
<div class="end">${text(endText)}<br>$next</div>
<script>
(function () {
  var native = window.ComixiumOffline;
  function post(o) { try { if (native) native.postMessage(JSON.stringify(o)); } catch (e) {} }
  var imgs = document.images, last = 0, timer = 0;
  function current() {
    var y = innerHeight * 0.3;
    for (var i = 0; i < imgs.length; i++) if (imgs[i].getBoundingClientRect().bottom > y) return i + 1;
    return imgs.length;
  }
  addEventListener('scroll', function () {
    if (timer) return;
    timer = setTimeout(function () {
      timer = 0;
      var p = current();
      if (p !== last) { last = p; post({ t: 'page', page: p, total: imgs.length }); }
    }, 200);
  }, { passive: true });
  document.addEventListener('click', function (e) {
    var b = e.target.closest && e.target.closest('[data-go]');
    post(b ? { t: 'go', to: b.getAttribute('data-go') } : { t: 'tap' });
  });
  var start = ${startPage.coerceAtLeast(1)};
  if (start > 1 && imgs[start - 1]) imgs[start - 1].scrollIntoView();
  post({ t: 'page', page: current(), total: imgs.length });
})();
</script>
</body></html>"""
    }

    private fun text(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
    private fun attr(s: String) = text(s).replace("\"", "&quot;")
}
