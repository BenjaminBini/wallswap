package com.benjaminbini.wallswap

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

enum class Screen(val title: String, val inUseWhen: String) {
    COVER("Cover screen", "folded"),
    INTERIOR("Interior screen", "open"),
}

/** An Unsplash collection the photos are picked from. */
data class Album(val id: String, val title: String, val enabled: Boolean)

data class Photo(
    val id: String,
    val photographer: String,
    val photographerUrl: String,
    val pageUrl: String,
)

class Prefs(context: Context) {
    val sp: SharedPreferences = context.getSharedPreferences("wallswap", Context.MODE_PRIVATE)

    /** 0 means manual changes only. */
    var intervalMinutes: Int
        get() = sp.getInt("interval_minutes", 60)
        set(value) = sp.edit().putInt("interval_minutes", value).apply()

    fun autoChange(screen: Screen): Boolean = sp.getBoolean("auto_${screen.name}", true)

    fun setAutoChange(screen: Screen, enabled: Boolean) =
        sp.edit().putBoolean("auto_${screen.name}", enabled).apply()

    /** A new photo was requested for the screen; it is set as soon as the screen is in use. */
    fun isQueued(screen: Screen): Boolean = sp.getBoolean("queued_${screen.name}", false)

    fun queue(screen: Screen) = sp.edit().putBoolean("queued_${screen.name}", true).apply()

    fun lastChanged(screen: Screen): Long = sp.getLong("last_${screen.name}", 0)

    fun photo(screen: Screen): Photo? = sp.getString("photo_${screen.name}", null)?.let {
        val json = JSONObject(it)
        Photo(
            id = json.getString("id"),
            photographer = json.getString("photographer"),
            photographerUrl = json.getString("photographerUrl"),
            pageUrl = json.getString("pageUrl"),
        )
    }

    fun recordChange(screen: Screen, photo: Photo) {
        val json = JSONObject()
            .put("id", photo.id)
            .put("photographer", photo.photographer)
            .put("photographerUrl", photo.photographerUrl)
            .put("pageUrl", photo.pageUrl)
        sp.edit()
            .putString("photo_${screen.name}", json.toString())
            .putLong("last_${screen.name}", System.currentTimeMillis())
            .putBoolean("queued_${screen.name}", false)
            .remove("last_error")
            .apply()
    }

    var albums: List<Album>
        get() {
            val array = JSONArray(sp.getString("albums", "[]"))
            return (0 until array.length()).map {
                val json = array.getJSONObject(it)
                Album(json.getString("id"), json.getString("title"), json.getBoolean("enabled"))
            }
        }
        set(value) {
            val array = JSONArray()
            value.forEach {
                array.put(JSONObject().put("id", it.id).put("title", it.title).put("enabled", it.enabled))
            }
            sp.edit().putString("albums", array.toString()).apply()
        }

    val banned: Set<String>
        get() = sp.getStringSet("banned", emptySet())!!

    fun ban(photoId: String) = sp.edit().putStringSet("banned", banned + photoId).apply()

    var lastError: String?
        get() = sp.getString("last_error", null)
        set(value) = sp.edit().putString("last_error", value).apply()
}
