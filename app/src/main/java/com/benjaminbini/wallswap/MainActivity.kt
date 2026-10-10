package com.benjaminbini.wallswap

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.work.WorkManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private val INTERVALS = listOf(0, 15, 30, 60, 180, 360, 720, 1440)

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
                Surface(Modifier.fillMaxSize()) {
                    WallSwapApp(prefs)
                }
            }
        }
    }
}

@Composable
private fun WallSwapApp(prefs: Prefs) {
    val context = LocalContext.current
    rememberPrefsVersion(prefs) // Recompose whenever a preference changes, e.g. after a background change.
    val interval = prefs.intervalMinutes
    val albums = prefs.albums
    val lastError = prefs.lastError
    val photos = Screen.entries.associateWith(prefs::photo)
    val auto = Screen.entries.associateWith(prefs::autoChange)
    val queued = Screen.entries.associateWith(prefs::isQueued)
    val batteryUnrestricted = rememberBatteryUnrestricted()

    LazyColumn(
        modifier = Modifier.fillMaxSize().safeDrawingPadding(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item { Text("WallSwap", style = MaterialTheme.typography.headlineMedium) }
        if (lastError != null) {
            item { ErrorCard(lastError, onDismiss = { prefs.lastError = null }) }
        }
        if (!batteryUnrestricted) {
            item { BatteryCard() }
        }
        items(Screen.entries) { screen ->
            ScreenCard(
                screen = screen,
                photo = photos[screen],
                auto = auto.getValue(screen),
                queued = queued.getValue(screen),
                onAutoChange = {
                    prefs.setAutoChange(screen, it)
                    Schedule.update(context)
                },
                onBan = {
                    prefs.ban(it.id)
                    Schedule.requestChange(context, screen)
                },
            )
        }
        item {
            IntervalCard(interval, onChange = {
                prefs.intervalMinutes = it
                Schedule.update(context)
            })
        }
        item { AlbumsCard(albums, onChange = { prefs.albums = it }) }
    }
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

@Composable
private fun ScreenCard(
    screen: Screen,
    photo: Photo?,
    auto: Boolean,
    queued: Boolean,
    onAutoChange: (Boolean) -> Unit,
    onBan: (Photo) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val checks by remember {
        WorkManager.getInstance(context).getWorkInfosForUniqueWorkFlow(Schedule.CHECK_NOW_WORK)
    }.collectAsStateWithLifecycle(emptyList())
    val changing = queued && checks.any { !it.state.isFinished }
    val preview by produceState<ImageBitmap?>(null, photo?.id) {
        value = withContext(Dispatchers.IO) { loadPreview(imageFile(context, screen)) }
    }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(screen.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Text("Auto-change", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(end = 8.dp))
                Switch(checked = auto, onCheckedChange = onAutoChange)
            }
            preview?.let {
                Image(
                    bitmap = it,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().height(180.dp).clip(RoundedCornerShape(12.dp)),
                )
            }
            photo?.let { Credit(it) }
            if (queued && !changing) {
                Text(
                    "Queued: a new photo will be set while the phone is ${screen.inUseWhen}.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { Schedule.requestChange(context, screen) }, enabled = !changing) {
                    Text(if (changing) "Changing…" else "Next")
                }
                photo?.let {
                    OutlinedButton(onClick = {
                        scope.launch {
                            runAndToast(context, "Saved to Pictures/WallSwap") { saveToGallery(context, screen, it) }
                        }
                    }) { Text("Save") }
                    OutlinedButton(onClick = { onBan(it) }, enabled = !changing) { Text("Ban") }
                }
            }
        }
    }
}

@Composable
private fun Credit(photo: Photo) {
    val linkStyle = TextLinkStyles(SpanStyle(textDecoration = TextDecoration.Underline))
    val text = buildAnnotatedString {
        append("Photo by ")
        withLink(LinkAnnotation.Url(photo.photographerUrl, linkStyle)) { append(photo.photographer) }
        append(" on ")
        withLink(LinkAnnotation.Url(photo.pageUrl, linkStyle)) { append("Unsplash") }
    }
    Text(text, style = MaterialTheme.typography.bodyMedium)
}

@Composable
private fun IntervalCard(minutes: Int, onChange: (Int) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Change interval", style = MaterialTheme.typography.titleMedium)
            Text(
                "Each screen gets a new photo while it is in use (cover: phone folded, interior: phone open) once its interval has elapsed.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Box {
                OutlinedButton(onClick = { expanded = true }) { Text(intervalLabel(minutes)) }
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    INTERVALS.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(intervalLabel(option)) },
                            onClick = {
                                expanded = false
                                onChange(option)
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AlbumsCard(albums: List<Album>, onChange: (List<Album>) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var input by remember { mutableStateOf("") }
    var adding by remember { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Albums", style = MaterialTheme.typography.titleMedium)
            if (albums.none { it.enabled }) {
                Text("No album enabled: photos come from all of Unsplash.", style = MaterialTheme.typography.bodyMedium)
            }
            albums.forEach { album ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(
                        checked = album.enabled,
                        onCheckedChange = { enabled -> onChange(albums.map { if (it == album) it.copy(enabled = enabled) else it }) },
                    )
                    Text(album.title, modifier = Modifier.weight(1f).padding(horizontal = 12.dp))
                    IconButton(onClick = { onChange(albums - album) }) {
                        Icon(Icons.Default.Delete, contentDescription = "Remove ${album.title}")
                    }
                }
            }
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                label = { Text("Unsplash collection link") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                enabled = input.isNotBlank() && !adding,
                onClick = {
                    scope.launch {
                        adding = true
                        runAndToast(context, success = null) {
                            val id = Unsplash.parseCollectionId(input)
                            val title = withContext(Dispatchers.IO) { Unsplash.collectionTitle(id) }
                            onChange(albums.filter { it.id != id } + Album(id, title, enabled = true))
                            input = ""
                        }
                        adding = false
                    }
                },
            ) { Text("Add album") }
        }
    }
}

@Composable
private fun BatteryCard() {
    val context = LocalContext.current
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Background changes", style = MaterialTheme.typography.titleMedium)
            Text(
                "Samsung puts apps to sleep. Allow WallSwap to run unrestricted so wallpapers keep changing.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Button(onClick = {
                val uri = Uri.parse("package:${context.packageName}")
                context.startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, uri))
            }) { Text("Allow") }
        }
    }
}

@Composable
private fun ErrorCard(message: String, onDismiss: () -> Unit) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Last change failed", style = MaterialTheme.typography.titleMedium)
            Text(message, style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = onDismiss) { Text("Dismiss") }
        }
    }
}

private fun intervalLabel(minutes: Int): String = when {
    minutes == 0 -> "Manual only"
    minutes < 60 -> "Every $minutes minutes"
    minutes == 60 -> "Every hour"
    minutes < 1440 -> "Every ${minutes / 60} hours"
    else -> "Every day"
}

private fun loadPreview(file: File): ImageBitmap? {
    if (!file.exists()) return null
    val options = BitmapFactory.Options().apply { inSampleSize = 4 }
    return BitmapFactory.decodeFile(file.path, options)?.asImageBitmap()
}

/** Runs [action] and toasts [success], or the error message when it fails. */
private suspend fun runAndToast(context: Context, success: String?, action: suspend () -> Unit) {
    try {
        action()
        success?.let { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Toast.makeText(context, e.message ?: e.javaClass.simpleName, Toast.LENGTH_LONG).show()
    }
}
