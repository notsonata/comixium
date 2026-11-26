package com.notsonata.comixium.offline

import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit

/** Download settings (More → Downloads). */
class DownloadPrefs(private val prefs: SharedPreferences) {
    /** Save each chapter's comments too, so they open offline. */
    var comments by mutableStateOf(prefs.getBoolean(KEY_COMMENTS, true))
        private set
    /** Delete a downloaded chapter once it's been read to the end (when leaving it). */
    var deleteAfterRead by mutableStateOf(prefs.getBoolean(KEY_DELETE_READ, false))
        private set
    /** Only download on Wi-Fi (or another unmetered connection). */
    var wifiOnly by mutableStateOf(prefs.getBoolean(KEY_WIFI, false))
        private set

    fun updateComments(on: Boolean) { comments = on; prefs.edit { putBoolean(KEY_COMMENTS, on) } }
    fun updateDeleteAfterRead(on: Boolean) { deleteAfterRead = on; prefs.edit { putBoolean(KEY_DELETE_READ, on) } }
    fun updateWifiOnly(on: Boolean) { wifiOnly = on; prefs.edit { putBoolean(KEY_WIFI, on) } }

    private companion object {
        const val KEY_COMMENTS = "download_comments"
        const val KEY_DELETE_READ = "download_delete_after_read"
        const val KEY_WIFI = "download_wifi_only"
    }
}
