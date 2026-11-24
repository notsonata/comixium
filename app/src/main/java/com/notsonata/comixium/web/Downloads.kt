package com.notsonata.comixium.web

import android.app.DownloadManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.webkit.CookieManager
import android.webkit.MimeTypeMap
import android.webkit.URLUtil
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** Saving and sharing files the site hands us: downloads, generated backups, page images. */
object Downloads {
    /** Folder under the cache dir for images handed to the share sheet; cleared with the cache. */
    const val SHARED_DIR = "shared"


    /** Regular http(s) downloads go to the system download manager, with the site's cookies. */
    fun enqueue(context: Context, url: String, userAgent: String?, contentDisposition: String?, mime: String?) {
        val name = URLUtil.guessFileName(url, contentDisposition, mime)
        val request = DownloadManager.Request(Uri.parse(url)).apply {
            setMimeType(mime)
            CookieManager.getInstance().getCookie(url)?.let { addRequestHeader("Cookie", it) }
            userAgent?.let { addRequestHeader("User-Agent", it) }
            addRequestHeader("Referer", "https://${Site.hostOf(url) ?: Site.DOMAINS[0]}/")
            setTitle(name)
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, name)
        }
        runCatching {
            context.getSystemService(DownloadManager::class.java).enqueue(request)
            toast(context, "Downloading $name")
        }.onFailure { toast(context, "Couldn't start the download") }
    }

    /** Files the page builds in memory (blob:/data: links, e.g. a library backup). */
    fun saveBytes(context: Context, name: String, mime: String, bytes: ByteArray) {
        val safeName = name.replace(Regex("[\\\\/:*?\"<>|]"), "_").ifBlank { "download" }
        val saved = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, safeName)
                    put(MediaStore.Downloads.MIME_TYPE, mime)
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }
                val resolver = context.contentResolver
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: error("insert failed")
                resolver.openOutputStream(uri)!!.use { it.write(bytes) }
                values.clear()
                values.put(MediaStore.Downloads.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
                "Saved to Downloads: $safeName"
            } else {
                // Pre-Android 10 needs a storage permission for public Downloads; offer it via the share sheet instead.
                val file = cacheFile(context, safeName).apply { writeBytes(bytes) }
                share(context, file, mime, "Save $safeName")
                null
            }
        }.getOrElse { "Couldn't save $safeName" }
        saved?.let { toast(context, it) }
    }

    /** Fetches an image the page shows, with the headers the CDN expects. Call off the main thread. */
    fun fetch(url: String, userAgent: String?): Pair<ByteArray, String> {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 15_000
            conn.readTimeout = 30_000
            conn.setRequestProperty("Referer", "https://${Site.DOMAINS[0]}/")
            userAgent?.let { conn.setRequestProperty("User-Agent", it) }
            CookieManager.getInstance().getCookie(url)?.let { conn.setRequestProperty("Cookie", it) }
            if (conn.responseCode !in 200..299) error("HTTP ${conn.responseCode}")
            val mime = conn.contentType?.substringBefore(';')?.trim()?.takeIf { it.startsWith("image/") } ?: "image/jpeg"
            return conn.inputStream.use { it.readBytes() } to mime
        } finally {
            conn.disconnect()
        }
    }

    fun saveImage(context: Context, bytes: ByteArray, mime: String, baseName: String): Boolean = runCatching {
        val name = "$baseName.${extension(mime)}"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, name)
                put(MediaStore.Images.Media.MIME_TYPE, mime)
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Comixium")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: error("insert failed")
            resolver.openOutputStream(uri)!!.use { it.write(bytes) }
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            true
        } else {
            false
        }
    }.getOrDefault(false)

    fun shareImage(context: Context, bytes: ByteArray, mime: String, baseName: String) {
        val file = cacheFile(context, "$baseName.${extension(mime)}").apply { writeBytes(bytes) }
        share(context, file, mime, "Share image")
    }

    fun shareText(context: Context, subject: String?, text: String) {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            subject?.let { putExtra(Intent.EXTRA_SUBJECT, it) }
            putExtra(Intent.EXTRA_TEXT, text)
        }
        context.startActivity(Intent.createChooser(send, null))
    }

    private fun share(context: Context, file: File, mime: String, title: String) {
        val uri = FileProvider.getUriForFile(context, context.packageName + ".files", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, title))
    }

    private fun cacheFile(context: Context, name: String): File {
        val dir = File(context.cacheDir, SHARED_DIR).apply { mkdirs() }
        dir.listFiles()?.filter { System.currentTimeMillis() - it.lastModified() > 24 * 3600_000L }?.forEach { it.delete() }
        return File(dir, name)
    }

    private fun extension(mime: String) = MimeTypeMap.getSingleton().getExtensionFromMimeType(mime) ?: "jpg"

    private fun toast(context: Context, text: String) = Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
}
