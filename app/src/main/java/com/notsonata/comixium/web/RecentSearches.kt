package com.notsonata.comixium.web

import android.content.SharedPreferences
import androidx.compose.runtime.mutableStateListOf
import androidx.core.content.edit
import org.json.JSONArray

/** Last few searches, newest first, kept on the device. */
class RecentSearches(private val prefs: SharedPreferences) {
    val items = mutableStateListOf<String>().apply {
        runCatching {
            val a = JSONArray(prefs.getString(KEY, "[]"))
            for (i in 0 until a.length()) add(a.getString(i))
        }
    }

    fun add(query: String) {
        val q = query.trim()
        if (q.isEmpty()) return
        items.removeAll { it.equals(q, ignoreCase = true) }
        items.add(0, q)
        while (items.size > MAX) items.removeAt(items.lastIndex)
        save()
    }

    fun remove(query: String) {
        items.remove(query)
        save()
    }

    fun clear() {
        items.clear()
        save()
    }

    private fun save() = prefs.edit { putString(KEY, JSONArray(items.toList()).toString()) }

    companion object {
        private const val KEY = "recent_searches"
        private const val MAX = 12
    }
}
