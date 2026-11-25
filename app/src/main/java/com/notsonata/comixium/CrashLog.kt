package com.notsonata.comixium

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.os.Build
import android.webkit.WebView
import androidx.core.content.edit
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.DateFormat
import java.util.Date

/**
 * Keeps the last crash on the phone, so the next launch can offer it to the user to copy or share.
 * Nothing leaves the phone unless the user shares it.
 *
 * Two sources: an uncaught exception handler (Kotlin/Java crashes, with the full stack trace), and
 * Android's record of how the app last closed (Android 11+), which also covers crashes in native
 * code such as the WebView, and freezes ("isn't responding").
 */
object CrashLog {
    private const val FILE = "last-crash.txt"
    private const val PREFS = "crash-log"
    private const val SEEN_EXIT = "seenExit"
    private const val MAX_CHARS = 12_000
    /** Older system records aren't worth showing (e.g. the first launch after installing this). */
    private const val MAX_AGE_MS = 3L * 24 * 60 * 60 * 1000

    /** Records uncaught exceptions on any thread, then lets Android handle the crash as usual. */
    fun install(context: Context) {
        val file = File(context.filesDir, FILE)
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching { file.writeText(header() + "Thread: ${thread.name}\n\n" + trace(error)) }
            previous?.uncaughtException(thread, error)
        }
    }

    /** The last crash's report, or null. Each system record is offered once; [clear] the file once seen. */
    fun pending(context: Context): String? {
        val own = runCatching { File(context.filesDir, FILE).takeIf { it.isFile }?.readText() }.getOrNull()?.takeIf { it.isNotBlank() }
        val system = systemReport(context, skipJavaCrash = own != null)
        return when {
            own != null && system != null -> (own + "\n\n" + system).take(MAX_CHARS)
            else -> (own ?: system)?.take(MAX_CHARS)
        }
    }

    fun clear(context: Context) {
        runCatching { File(context.filesDir, FILE).delete() }
    }

    private fun systemReport(context: Context, skipJavaCrash: Boolean): String? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val seen = prefs.getLong(SEEN_EXIT, 0L)
        val exits = runCatching {
            context.getSystemService(ActivityManager::class.java)?.getHistoricalProcessExitReasons(null, 0, 5)
        }.getOrNull().orEmpty()
        val newest = exits.maxOfOrNull { it.timestamp } ?: return null
        prefs.edit { putLong(SEEN_EXIT, maxOf(seen, newest)) }
        val wanted = setOf(
            ApplicationExitInfo.REASON_CRASH_NATIVE,
            ApplicationExitInfo.REASON_ANR,
            ApplicationExitInfo.REASON_INITIALIZATION_FAILURE,
        ) + if (skipJavaCrash) emptySet() else setOf(ApplicationExitInfo.REASON_CRASH)
        val exit = exits.filter { it.timestamp > seen && System.currentTimeMillis() - it.timestamp < MAX_AGE_MS && it.reason in wanted }
            .maxByOrNull { it.timestamp } ?: return null
        val kind = when (exit.reason) {
            ApplicationExitInfo.REASON_CRASH_NATIVE -> "crash in native code"
            ApplicationExitInfo.REASON_ANR -> "not responding"
            ApplicationExitInfo.REASON_INITIALIZATION_FAILURE -> "failed to start"
            else -> "crash"
        }
        val where = if (exit.importance <= ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND) "on screen" else "in the background"
        val details = buildString {
            append(header())
            append("Android's record: $kind, $where, ")
            append(DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.MEDIUM).format(Date(exit.timestamp)))
            append("\nProcess: ${exit.processName}")
            exit.description?.takeIf { it.isNotBlank() }?.let { append("\nDescription: $it") }
            traceOf(exit)?.let { append("\n\n").append(it) }
        }
        return details
    }

    /** The freeze's thread dump, or the readable parts of a native crash's record (it's binary). */
    private fun traceOf(exit: ApplicationExitInfo): String? = runCatching {
        val bytes = exit.traceInputStream?.use { it.readBytes() } ?: return null
        if (exit.reason == ApplicationExitInfo.REASON_ANR) {
            val text = String(bytes)
            val main = text.indexOf("\"main\"").takeIf { it >= 0 } ?: 0
            text.substring(main).lineSequence().take(60).joinToString("\n")
        } else {
            // Printable runs: the abort message, library and function names of the backtrace.
            Regex("[\\x20-\\x7e]{6,}").findAll(String(bytes, Charsets.ISO_8859_1)).map { it.value }
                .distinct().take(80).joinToString("\n")
        }
    }.getOrNull()

    private fun header(): String {
        val webView = runCatching { WebView.getCurrentWebViewPackage()?.let { "${it.packageName} ${it.versionName}" } }.getOrNull() ?: "unknown"
        return "Comixium ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) · Android ${Build.VERSION.RELEASE} " +
            "(API ${Build.VERSION.SDK_INT}) · WebView $webView\n"
    }

    private fun trace(error: Throwable): String = StringWriter().also { error.printStackTrace(PrintWriter(it)) }.toString()
}
