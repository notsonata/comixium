package com.notsonata.comixium

import android.content.Context
import android.os.Build
import android.webkit.WebView
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter

/**
 * Keeps the last crash on the phone, so the next launch can offer it to the user to copy or share.
 * Nothing leaves the phone unless the user shares it.
 */
object CrashLog {
    private const val FILE = "last-crash.txt"
    private const val MAX_CHARS = 12_000

    /** Records uncaught exceptions on any thread, then lets Android handle the crash as usual. */
    fun install(context: Context) {
        val file = File(context.filesDir, FILE)
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching { file.writeText(report(thread, error)) }
            previous?.uncaughtException(thread, error)
        }
    }

    /** The last crash's report, or null; [clear] it once the user has seen it. */
    fun pending(context: Context): String? =
        runCatching { File(context.filesDir, FILE).takeIf { it.isFile }?.readText() }.getOrNull()?.takeIf { it.isNotBlank() }

    fun clear(context: Context) {
        runCatching { File(context.filesDir, FILE).delete() }
    }

    private fun report(thread: Thread, error: Throwable): String {
        val trace = StringWriter().also { error.printStackTrace(PrintWriter(it)) }.toString()
        val webView = runCatching { WebView.getCurrentWebViewPackage()?.versionName }.getOrNull() ?: "unknown"
        val head = "Comixium ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) · Android ${Build.VERSION.RELEASE} " +
            "(API ${Build.VERSION.SDK_INT}) · WebView $webView\nThread: ${thread.name}\n\n"
        return (head + trace).take(MAX_CHARS)
    }
}
