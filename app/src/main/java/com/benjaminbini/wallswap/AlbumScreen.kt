package com.benjaminbini.wallswap

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Start loading the next page when the grid is this close to its last photo. */
private const val PREFETCH_DISTANCE = 6

/** An album's photos, loaded page by page and kept while the app runs so coming back is instant. */
class AlbumPhotos(private val albumId: String) {
    val photos = mutableStateListOf<Photo>()
    val gridState = LazyGridState()
    var loading by mutableStateOf(false)
        private set
    var endReached by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    private var nextPage = 1

    suspend fun loadMore() {
        if (loading || endReached) return
        loading = true
        error = null
        try {
            val page = withContext(Dispatchers.IO) { Unsplash.albumPhotos(albumId, nextPage) }
            val known = photos.mapTo(HashSet()) { it.id }
            photos += page.filter { it.id !in known }
            nextPage++
            endReached = page.size < Unsplash.PAGE_SIZE
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error = e.message ?: e.javaClass.simpleName
        } finally {
            loading = false
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumScreen(
    album: Album,
    photos: AlbumPhotos,
    onBack: () -> Unit,
    onSourceChange: (Boolean) -> Unit,
    onRemove: () -> Unit,
    onOpenPhoto: (Photo) -> Unit,
) {
    val scope = rememberCoroutineScope()
    LaunchedEffect(photos) {
        snapshotFlow { photos.photos.size to (photos.gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1) }
            .collect { (size, lastVisible) -> if (lastVisible >= size - PREFETCH_DISTANCE) photos.loadMore() }
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(album.title)
                        Text(
                            "${album.curator} · ${album.totalPhotos} photos",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
                actions = {
                    IconButton(onClick = onRemove) { Icon(Icons.Filled.Delete, contentDescription = "Remove album") }
                },
            )
        },
    ) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Adaptive(110.dp),
            state = photos.gridState,
            contentPadding = PaddingValues(
                start = 12.dp,
                end = 12.dp,
                top = padding.calculateTopPadding() + 4.dp,
                bottom = padding.calculateBottomPadding() + 24.dp,
            ),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Card(shape = RoundedCornerShape(20.dp), modifier = Modifier.padding(bottom = 6.dp)) {
                    ListItem(
                        headlineContent = { Text("Random source") },
                        supportingContent = { Text("Use this album for automatic changes and the refresh buttons") },
                        trailingContent = { Switch(checked = album.enabled, onCheckedChange = onSourceChange) },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    )
                }
            }
            items(photos.photos, key = { it.id }) { photo ->
                AsyncImage(
                    model = Unsplash.imageUrl(photo.rawUrl, 400),
                    contentDescription = "Photo by ${photo.photographer}",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .aspectRatio(9f / 16f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable { onOpenPhoto(photo) },
                )
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                    val error = photos.error
                    when {
                        photos.loading -> CircularProgressIndicator()
                        error != null -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                            TextButton(onClick = { scope.launch { photos.loadMore() } }) { Text("Retry") }
                        }
                        photos.endReached && photos.photos.isEmpty() -> Text("No portrait photos in this album")
                    }
                }
            }
        }
    }
}
