package com.benjaminbini.wallswap

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage

private val TARGETS = listOf(
    "Cover screen" to listOf(Screen.COVER),
    "Interior screen" to listOf(Screen.INTERIOR),
    "Both screens" to Screen.entries,
)

/** Full-screen preview of a photo, with the action to set it as wallpaper. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhotoScreen(
    photo: Photo,
    snackbar: SnackbarHostState,
    onBack: () -> Unit,
    onSetWallpaper: (List<Screen>) -> Unit,
) {
    var choosing by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AsyncImage(
            model = Unsplash.imageUrl(photo.rawUrl, 1080),
            contentDescription = "Photo by ${photo.photographer}",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        Box(
            Modifier
                .fillMaxWidth()
                .height(140.dp)
                .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.5f), Color.Transparent))),
        )
        FilledTonalIconButton(onClick = onBack, modifier = Modifier.statusBarsPadding().padding(12.dp)) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
        }
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.75f))))
                .navigationBarsPadding()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SnackbarHost(snackbar)
            Credit(photo, color = Color.White)
            Button(onClick = { choosing = true }, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                Text("Set as wallpaper")
            }
        }
    }
    if (choosing) {
        ModalBottomSheet(onDismissRequest = { choosing = false }) {
            Text(
                "Set as wallpaper on",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
            TARGETS.forEach { (label, screens) ->
                ListItem(
                    headlineContent = { Text(label) },
                    modifier = Modifier.clickable {
                        choosing = false
                        onSetWallpaper(screens)
                    },
                )
            }
            Box(Modifier.navigationBarsPadding().padding(bottom = 16.dp))
        }
    }
}
