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

    class Candidate(val photo: Photo, val rawUrl: String, val downloadLocation: String)

    /** Random portrait photos from the given collections, or from all of Unsplash when empty. */
    fun randomPhotos(collectionIds: List<String>): List<Candidate> {
        var query = "count=10&orientation=portrait&content_filter=high"
        if (collectionIds.isNotEmpty()) query += "&collections=" + collectionIds.joinToString(",")
        val array = JSONArray(api("$API/photos/random?$query"))
        return (0 until array.length()).map { parseCandidate(array.getJSONObject(it)) }
    }

    fun collectionTitle(collectionId: String): String =
        JSONObject(api("$API/collections/$collectionId")).getString("title")

    /** Accepts a collection link (https://unsplash.com/collections/<id>/<name>) or a bare ID. */
    fun parseCollectionId(input: String): String {
        val trimmed = input.trim()
        val id = Regex("collections/([^/?#]+)").find(trimmed)?.groupValues?.get(1) ?: trimmed
        require(id.matches(Regex("[A-Za-z0-9_-]+"))) { "Not an Unsplash collection link: $input" }
        return id
    }

    /** Unsplash requires reporting each photo that is used as a download. */
    fun trackDownload(downloadLocation: String) {
        api(downloadLocation)
    }

    /** JPEG cropped by Unsplash's image service to exactly [width] x [height]. */
    fun downloadImage(rawUrl: String, width: Int, height: Int): ByteArray =
        request("$rawUrl&w=$width&h=$height&fit=crop&crop=entropy&fm=jpg&q=90", authorized = false)

    private fun parseCandidate(json: JSONObject): Candidate {
        val user = json.getJSONObject("user")
        val links = json.getJSONObject("links")
        return Candidate(
            photo = Photo(
                id = json.getString("id"),
                photographer = user.getString("name"),
                photographerUrl = user.getJSONObject("links").getString("html") + "?$UTM",
                pageUrl = links.getString("html") + "?$UTM",
            ),
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
