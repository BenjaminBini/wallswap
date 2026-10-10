package com.benjaminbini.wallswap

import android.app.WallpaperManager
import android.content.ContentValues
import android.content.Context
import android.os.Environment
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Samsung foldables apply a wallpaper to whichever screen is in use, and Android has no public
 * API to address one screen directly. So a screen is only ever changed while it is the active one.
 * The interior screen gets home + lock; the cover screen gets the plain system flag, which is
 * what Samsung routes to the cover screen when the phone is closed.
 */
private val Screen.wallpaperFlags: Int
    get() = when (this) {
        Screen.COVER -> WallpaperManager.FLAG_SYSTEM
        Screen.INTERIOR -> WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK
    }

/** The image currently set on [screen], kept for the preview and for saving it. */
fun imageFile(context: Context, screen: Screen) = File(context.filesDir, "${screen.name}.jpg")

/** Puts a new Unsplash photo on [screen], which must be the active screen. */
suspend fun changeWallpaper(context: Context, prefs: Prefs, screen: Screen) = withContext(Dispatchers.IO) {
    val currentId = prefs.photo(screen)?.id
    val banned = prefs.banned
    val albumIds = prefs.albums.filter { it.enabled }.map { it.id }
    val candidate = Unsplash.randomPhotos(albumIds)
        .firstOrNull { it.photo.id != currentId && it.photo.id !in banned }
        ?: error("Unsplash returned no new photo for the enabled albums")

    val (width, height) = wallpaperSize(context, screen)
    val image = Unsplash.downloadImage(candidate.rawUrl, width, height)

    check(activeScreen(context) == screen) { "The phone was folded or opened during the change" }
    WallpaperManager.getInstance(context).setStream(image.inputStream(), null, true, screen.wallpaperFlags)

    imageFile(context, screen).writeBytes(image)
    prefs.recordChange(screen, candidate.photo)
    Unsplash.trackDownload(candidate.downloadLocation)
}

/** Copies the image of [screen] to Pictures/WallSwap. */
suspend fun saveToGallery(context: Context, screen: Screen, photo: Photo) = withContext(Dispatchers.IO) {
    val values = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, "unsplash-${photo.id}.jpg")
        put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
        put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/WallSwap")
    }
    val resolver = context.contentResolver
    val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
        ?: error("Could not create the image in the gallery")
    resolver.openOutputStream(uri)!!.use { output ->
        imageFile(context, screen).inputStream().use { it.copyTo(output) }
    }
}
