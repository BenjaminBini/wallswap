package com.benjaminbini.wallswap

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

enum class Screen(val title: String, val inUseWhen: String) {
    COVER("Cover screen", "folded"),
    INTERIOR("Interior screen", "open"),
}

/** An Unsplash collection: browsable in the app, and a random wallpaper source when [enabled]. */
data class Album(
    val id: String,
    val title: String,
    val curator: String,
    val coverUrl: String,
    val totalPhotos: Int,
    val enabled: Boolean,
)

/** An Unsplash photo with what the credit line and the wallpaper download need. */
data class Photo(
    val id: String,
    val photographer: String,
    val photographerUrl: String,
    val pageUrl: String,
    val rawUrl: String,
    val downloadLocation: String,
)

/** Albums used until the user changes the list; both checked to serve photos through the API. */
private val DEFAULT_ALBUMS = listOf(
    Album(
        id = "1065976",
        title = "Wallpapers",
        curator = "Unsplash Archive",
        coverUrl = "https://images.unsplash.com/photo-1559583985-c80d8ad9b29f?ixlib=rb-4.1.0",
        totalPhotos = 2685,
        enabled = true,
    ),
    Album(
        id = "941021",
        title = "Wallpapers",
        curator = "Jim Myhrberg",
        coverUrl = "https://images.unsplash.com/photo-1752520316159-741a8d0bde1d?ixlib=rb-4.1.0",
        totalPhotos = 505,
        enabled = true,
    ),
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

    /** A change was requested for the screen; it is applied as soon as the screen is in use. */
    fun isQueued(screen: Screen): Boolean = sp.contains("queue_${screen.name}")

    /** The photo a queued change sets, or null for a random one. */
    fun queuedPhoto(screen: Screen): Photo? =
        sp.getString("queue_${screen.name}", null)?.takeIf { it.isNotEmpty() }?.let { photoFromJson(JSONObject(it)) }

    fun queue(screen: Screen, photo: Photo?) =
        sp.edit().putString("queue_${screen.name}", photo?.let { photoToJson(it).toString() }.orEmpty()).apply()

    fun lastChanged(screen: Screen): Long = sp.getLong("last_${screen.name}", 0)

    fun photo(screen: Screen): Photo? =
        sp.getString("current_${screen.name}", null)?.let { photoFromJson(JSONObject(it)) }

    fun recordChange(screen: Screen, photo: Photo) {
        sp.edit()
            .putString("current_${screen.name}", photoToJson(photo).toString())
            .putLong("last_${screen.name}", System.currentTimeMillis())
            .remove("queue_${screen.name}")
            .remove("last_error")
            .apply()
    }

    var albums: List<Album>
        get() {
            val array = JSONArray(sp.getString("album_list", null) ?: return DEFAULT_ALBUMS)
            return (0 until array.length()).map { albumFromJson(array.getJSONObject(it)) }
        }
        set(value) {
            val array = JSONArray()
            value.forEach { array.put(albumToJson(it)) }
            sp.edit().putString("album_list", array.toString()).apply()
        }

    val banned: Set<String>
        get() = sp.getStringSet("banned", emptySet())!!

    fun ban(photoId: String) = sp.edit().putStringSet("banned", banned + photoId).apply()

    var lastError: String?
        get() = sp.getString("last_error", null)
        set(value) = sp.edit().putString("last_error", value).apply()
}

private fun photoToJson(photo: Photo) = JSONObject()
    .put("id", photo.id)
    .put("photographer", photo.photographer)
    .put("photographerUrl", photo.photographerUrl)
    .put("pageUrl", photo.pageUrl)
    .put("rawUrl", photo.rawUrl)
    .put("downloadLocation", photo.downloadLocation)

private fun photoFromJson(json: JSONObject) = Photo(
    id = json.getString("id"),
    photographer = json.getString("photographer"),
    photographerUrl = json.getString("photographerUrl"),
    pageUrl = json.getString("pageUrl"),
    rawUrl = json.getString("rawUrl"),
    downloadLocation = json.getString("downloadLocation"),
)

private fun albumToJson(album: Album) = JSONObject()
    .put("id", album.id)
    .put("title", album.title)
    .put("curator", album.curator)
    .put("coverUrl", album.coverUrl)
    .put("totalPhotos", album.totalPhotos)
    .put("enabled", album.enabled)

private fun albumFromJson(json: JSONObject) = Album(
    id = json.getString("id"),
    title = json.getString("title"),
    curator = json.getString("curator"),
    coverUrl = json.getString("coverUrl"),
    totalPhotos = json.getInt("totalPhotos"),
    enabled = json.getBoolean("enabled"),
)
