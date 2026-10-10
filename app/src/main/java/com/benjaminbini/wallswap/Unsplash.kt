package com.benjaminbini.wallswap

import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

object Unsplash {
    private const val API = "https://api.unsplash.com"

    /** Required by the Unsplash guidelines on every link back to Unsplash. */
    private const val UTM = "utm_source=wallswap&utm_medium=referral"

    /** The API maximum. */
    const val PAGE_SIZE = 30

    /** Random portrait photos from the given collections, or from all of Unsplash when empty. */
    fun randomPhotos(collectionIds: List<String>): List<Photo> {
        var query = "count=10&orientation=portrait&content_filter=high"
        if (collectionIds.isNotEmpty()) query += "&collections=" + collectionIds.joinToString(",")
        return parsePhotos(JSONArray(api("$API/photos/random?$query")))
    }

    /** One page of an album's portrait photos. */
    fun albumPhotos(albumId: String, page: Int): List<Photo> =
        parsePhotos(JSONArray(api("$API/collections/$albumId/photos?page=$page&per_page=$PAGE_SIZE&orientation=portrait")))

    /** The album with this ID, enabled as a random source. */
    fun album(albumId: String): Album {
        val json = JSONObject(api("$API/collections/$albumId"))
        return Album(
            id = albumId,
            title = json.getString("title"),
            curator = json.getJSONObject("user").getString("name"),
            coverUrl = json.getJSONObject("cover_photo").getJSONObject("urls").getString("raw"),
            totalPhotos = json.getInt("total_photos"),
            enabled = true,
        )
    }

    /** Accepts a collection link (https://unsplash.com/collections/<id>/<name>) or a bare ID. */
    fun parseCollectionId(input: String): String {
        val trimmed = input.trim()
        val id = Regex("collections/([^/?#]+)").find(trimmed)?.groupValues?.get(1) ?: trimmed
        require(id.matches(Regex("[A-Za-z0-9_-]+"))) { "Not an Unsplash collection link: $input" }
        return id
    }

    /** Unsplash requires reporting each photo that is used as a download. */
    fun trackDownload(photo: Photo) {
        api(photo.downloadLocation)
    }

    /** JPEG cropped by Unsplash's image service to exactly [width] x [height]. */
    fun downloadImage(photo: Photo, width: Int, height: Int): ByteArray =
        request("${photo.rawUrl}&w=$width&h=$height&fit=crop&crop=entropy&fm=jpg&q=90", authorized = false)

    /** Display URL of an image resized by Unsplash's image service to [width] pixels wide. */
    fun imageUrl(rawUrl: String, width: Int) = "$rawUrl&w=$width&fit=max&fm=jpg&q=75"

    private fun parsePhotos(array: JSONArray): List<Photo> = (0 until array.length()).map {
        val json = array.getJSONObject(it)
        val user = json.getJSONObject("user")
        val links = json.getJSONObject("links")
        Photo(
            id = json.getString("id"),
            photographer = user.getString("name"),
            photographerUrl = user.getJSONObject("links").getString("html") + "?$UTM",
            pageUrl = links.getString("html") + "?$UTM",
            rawUrl = json.getJSONObject("urls").getString("raw"),
            downloadLocation = links.getString("download_location"),
        )
    }

    private fun api(url: String): String = String(request(url, authorized = true))

    private fun request(url: String, authorized: Boolean): ByteArray {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 30_000
        connection.readTimeout = 30_000
        if (authorized) {
            connection.setRequestProperty("Authorization", "Client-ID ${BuildConfig.UNSPLASH_ACCESS_KEY}")
            connection.setRequestProperty("Accept-Version", "v1")
        }
        try {
            val code = connection.responseCode
            if (code != HttpURLConnection.HTTP_OK) {
                val body = connection.errorStream?.bufferedReader()?.readText().orEmpty()
                throw IOException("Unsplash returned HTTP $code ${body.take(200)}")
            }
            return connection.inputStream.readBytes()
        } finally {
            connection.disconnect()
        }
    }
}
