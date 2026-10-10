package com.benjaminbini.wallswap

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.work.WorkManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private sealed interface Route {
    data object Home : Route
    data object Settings : Route
    data class AlbumDetail(val albumId: String) : Route
    data class PhotoDetail(val photo: Photo) : Route
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val prefs = Prefs(this)
        HingeAccessService.start(this)
        Schedule.update(this)
        setContent {
            val colors = if (isSystemInDarkTheme()) dynamicDarkColorScheme(this) else dynamicLightColorScheme(this)
            MaterialTheme(colorScheme = colors) {
                WallSwapApp(prefs)
            }
        }
    }
}

@Composable
private fun WallSwapApp(prefs: Prefs) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val backStack = remember { mutableStateListOf<Route>(Route.Home) }
    val albumPhotos = remember { mutableMapOf<String, AlbumPhotos>() }

    rememberPrefsVersion(prefs) // Recompose whenever a preference changes, e.g. after a background change.
    val albums = prefs.albums
    val current = Screen.entries.associateWith(prefs::photo)
    val queued = Screen.entries.associateWith(prefs::isQueued)
    val auto = Screen.entries.associateWith(prefs::autoChange)
    val batteryUnrestricted = rememberBatteryUnrestricted()
    val checks by remember {
        WorkManager.getInstance(context).getWorkInfosForUniqueWorkFlow(Schedule.CHECK_NOW_WORK)
    }.collectAsStateWithLifecycle(emptyList())
    val checking = checks.any { !it.state.isFinished }

    val navigate = { route: Route -> backStack.add(route) }
    val back = { backStack.removeAt(backStack.lastIndex) }
    BackHandler(enabled = backStack.size > 1) { back() }

    /** Runs [action], then shows [success] or the error in a snackbar. */
    fun run(success: String?, action: suspend () -> Unit) = scope.launch {
        val message = try {
            action()
            success
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            e.message ?: e.javaClass.simpleName
        }
        if (message != null) snackbar.showSnackbar(message)
    }

    AnimatedContent(
        targetState = backStack.last(),
        transitionSpec = { fadeIn() togetherWith fadeOut() },
        label = "route",
    ) { route ->
        when (route) {
            Route.Home -> HomeScreen(
                albums = albums,
                current = current,
                queued = queued,
                checking = checking,
                lastError = prefs.lastError,
                batteryUnrestricted = batteryUnrestricted,
                snackbar = snackbar,
                onOpenAlbum = { navigate(Route.AlbumDetail(it.id)) },
                onAddAlbum = { link ->
                    run("Album added") {
                        val album = withContext(Dispatchers.IO) { Unsplash.album(Unsplash.parseCollectionId(link)) }
                        prefs.albums = prefs.albums.filter { it.id != album.id } + album
                    }
                },
                onOpenSettings = { navigate(Route.Settings) },
                onNext = { Schedule.requestChange(context, it, photo = null) },
                onSave = { screen, photo -> run("Saved to Pictures/WallSwap") { saveToGallery(context, screen, photo) } },
                onBan = { screen, photo ->
                    prefs.ban(photo.id)
                    Schedule.requestChange(context, screen, photo = null)
                },
                onDismissError = { prefs.lastError = null },
                onAllowBattery = { requestBatteryExemption(context) },
            )

            is Route.AlbumDetail -> {
                // Null for a moment while the screen fades out after the album was removed.
                val album = albums.firstOrNull { it.id == route.albumId }
                if (album != null) {
                    AlbumScreen(
                        album = album,
                        photos = albumPhotos.getOrPut(album.id) { AlbumPhotos(album.id) },
                        onBack = { back() },
                        onSourceChange = { enabled ->
                            prefs.albums = albums.map { if (it.id == album.id) it.copy(enabled = enabled) else it }
                        },
                        onRemove = {
                            back()
                            prefs.albums = albums - album
                        },
                        onOpenPhoto = { navigate(Route.PhotoDetail(it)) },
                    )
                }
            }

            is Route.PhotoDetail -> PhotoScreen(
                photo = route.photo,
                snackbar = snackbar,
                onBack = { back() },
                onSetWallpaper = { screens ->
                    run(success = null) {
                        val active = activeScreen(context)
                        screens.forEach { Schedule.requestChange(context, it, route.photo) }
                        snackbar.showSnackbar(
                            screens.joinToString("\n") {
                                if (it == active) "${it.title}: setting it now" else "${it.title}: set once the phone is ${it.inUseWhen}"
                            },
                        )
                    }
                },
            )

            Route.Settings -> SettingsScreen(
                interval = prefs.intervalMinutes,
                auto = auto,
                batteryUnrestricted = batteryUnrestricted,
                onBack = { back() },
                onIntervalChange = {
                    prefs.intervalMinutes = it
                    Schedule.update(context)
                },
                onAutoChange = { screen, enabled ->
                    prefs.setAutoChange(screen, enabled)
                    Schedule.update(context)
                },
                onAllowBattery = { requestBatteryExemption(context) },
            )
        }
    }
}

private fun requestBatteryExemption(context: Context) {
    val uri = Uri.parse("package:${context.packageName}")
    context.startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, uri))
}

@Composable
private fun rememberPrefsVersion(prefs: Prefs): Int {
    var version by remember { mutableIntStateOf(0) }
    DisposableEffect(prefs) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> version++ }
        prefs.sp.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.sp.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    return version
}

@Composable
private fun rememberBatteryUnrestricted(): Boolean {
    val context = LocalContext.current
    val power = context.getSystemService(PowerManager::class.java)
    var unrestricted by remember { mutableStateOf(power.isIgnoringBatteryOptimizations(context.packageName)) }
    LifecycleResumeEffect(Unit) {
        unrestricted = power.isIgnoringBatteryOptimizations(context.packageName)
        onPauseOrDispose { }
    }
    return unrestricted
}
