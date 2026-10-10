package com.benjaminbini.wallswap

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridItemScope
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage

private val TileShape = RoundedCornerShape(24.dp)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    albums: List<Album>,
    current: Map<Screen, Photo?>,
    queued: Map<Screen, Boolean>,
    checking: Boolean,
    lastError: String?,
    batteryUnrestricted: Boolean,
    snackbar: SnackbarHostState,
    onOpenAlbum: (Album) -> Unit,
    onAddAlbum: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onNext: (Screen) -> Unit,
    onSave: (Screen, Photo) -> Unit,
    onBan: (Screen, Photo) -> Unit,
    onDismissError: () -> Unit,
    onAllowBattery: () -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    var adding by remember { mutableStateOf(false) }
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text("WallSwap") },
                actions = {
                    IconButton(onClick = onOpenSettings) { Icon(Icons.Filled.Settings, contentDescription = "Settings") }
                },
                scrollBehavior = scrollBehavior,
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Adaptive(150.dp),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 24.dp,
            ),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            if (lastError != null) {
                fullWidth { Banner("Last change failed", lastError, "Dismiss", MaterialTheme.colorScheme.errorContainer, onDismissError) }
            }
            if (!batteryUnrestricted) {
                fullWidth {
                    Banner(
                        title = "Keep auto-change running",
                        text = "Samsung puts apps to sleep. Allow WallSwap to run unrestricted.",
                        action = "Allow",
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        onAction = onAllowBattery,
                    )
                }
            }
            fullWidth { SectionTitle("On your screens", null) }
            fullWidth {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Screen.entries.forEach { screen ->
                        val isQueued = queued.getValue(screen)
                        NowTile(
                            screen = screen,
                            photo = current[screen],
                            queued = isQueued,
                            changing = isQueued && checking,
                            onNext = { onNext(screen) },
                            onSave = { onSave(screen, it) },
                            onBan = { onBan(screen, it) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
            fullWidth {
                SectionTitle(
                    "Albums",
                    if (albums.any { it.enabled }) {
                        "Open an album to pick a wallpaper. Albums marked Random feed the automatic changes."
                    } else {
                        "Open an album to pick a wallpaper. No album is marked Random, so automatic changes use all of Unsplash."
                    },
                )
            }
            items(albums, key = { it.id }) { album -> AlbumCard(album, onClick = { onOpenAlbum(album) }) }
            item { AddAlbumCard(onClick = { adding = true }) }
        }
    }
    if (adding) {
        AddAlbumDialog(
            onDismiss = { adding = false },
            onAdd = {
                adding = false
                onAddAlbum(it)
            },
        )
    }
}

private fun LazyGridScope.fullWidth(content: @Composable LazyGridItemScope.() -> Unit) =
    item(span = { GridItemSpan(maxLineSpan) }, content = content)

@Composable
private fun SectionTitle(title: String, subtitle: String?) {
    Column(Modifier.padding(top = 12.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        if (subtitle != null) {
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun Banner(title: String, text: String, action: String, color: Color, onAction: () -> Unit) {
    Card(shape = TileShape, colors = CardDefaults.cardColors(containerColor = color)) {
        Row(Modifier.padding(start = 20.dp, end = 8.dp, top = 12.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(text, style = MaterialTheme.typography.bodySmall)
            }
            TextButton(onClick = onAction) { Text(action) }
        }
    }
}

@Composable
private fun NowTile(
    screen: Screen,
    photo: Photo?,
    queued: Boolean,
    changing: Boolean,
    onNext: () -> Unit,
    onSave: (Photo) -> Unit,
    onBan: (Photo) -> Unit,
    modifier: Modifier,
) {
    var menu by remember { mutableStateOf(false) }
    val status = when {
        changing -> "Changing…"
        queued -> "Queued until the phone is ${screen.inUseWhen}"
        photo != null -> "by ${photo.photographer}"
        else -> "No photo yet"
    }
    Card(modifier, shape = TileShape) {
        Box(Modifier.aspectRatio(0.8f)) {
            if (photo != null) {
                AsyncImage(
                    model = Unsplash.imageUrl(photo.rawUrl, 600),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant))
            }
            BottomScrim(Modifier.align(Alignment.BottomCenter))
            Column(Modifier.align(Alignment.BottomStart).padding(start = 14.dp, end = 60.dp, bottom = 14.dp)) {
                Text(screen.title, style = MaterialTheme.typography.titleSmall, color = Color.White)
                Text(
                    status,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.8f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            FilledTonalIconButton(
                onClick = onNext,
                enabled = !changing,
                modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp),
            ) {
                if (changing) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Filled.Refresh, contentDescription = "New photo on the ${screen.title.lowercase()}")
                }
            }
            if (photo != null) {
                Box(Modifier.align(Alignment.TopEnd).padding(4.dp)) {
                    IconButton(
                        onClick = { menu = true },
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = Color.Black.copy(alpha = 0.3f),
                            contentColor = Color.White,
                        ),
                    ) { Icon(Icons.Filled.MoreVert, contentDescription = "More") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("Save to gallery") }, onClick = {
                            menu = false
                            onSave(photo)
                        })
                        DropdownMenuItem(text = { Text("Never show again") }, onClick = {
                            menu = false
                            onBan(photo)
                        })
                    }
                }
            }
        }
    }
}

@Composable
private fun AlbumCard(album: Album, onClick: () -> Unit) {
    Card(onClick = onClick, shape = TileShape) {
        Box(Modifier.aspectRatio(0.8f)) {
            AsyncImage(
                model = Unsplash.imageUrl(album.coverUrl, 500),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant),
            )
            BottomScrim(Modifier.align(Alignment.BottomCenter))
            Column(Modifier.align(Alignment.BottomStart).padding(14.dp)) {
                Text(
                    album.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "${album.curator} · ${album.totalPhotos} photos",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.8f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (album.enabled) RandomBadge(Modifier.align(Alignment.TopStart).padding(10.dp))
        }
    }
}

@Composable
private fun RandomBadge(modifier: Modifier) {
    Surface(
        modifier,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    ) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(4.dp))
            Text("Random", style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun AddAlbumCard(onClick: () -> Unit) {
    OutlinedCard(onClick = onClick, shape = TileShape) {
        Box(Modifier.aspectRatio(0.8f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(32.dp))
                Text("Add album", style = MaterialTheme.typography.titleSmall)
            }
        }
    }
}

@Composable
private fun AddAlbumDialog(onDismiss: () -> Unit, onAdd: (String) -> Unit) {
    var link by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add album") },
        text = {
            OutlinedTextField(
                value = link,
                onValueChange = { link = it },
                label = { Text("Unsplash collection link") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = { TextButton(onClick = { onAdd(link) }, enabled = link.isNotBlank()) { Text("Add") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
