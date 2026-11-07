package org.swiftify.app
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.ui.platform.LocalContext
import android.widget.Toast
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import org.json.JSONObject
import kotlinx.coroutines.launch

@Composable
fun PlaylistDetailScreen(
    playlist: PlaylistItem,
    tracks: List<TrackItem>,
    livePlayingTrack: TrackItem?,
    isPlaying: Boolean,
    onBack: () -> Unit,
    onTrackSelect: (TrackItem, List<TrackItem>) -> Unit,
    onPlayAll: (List<TrackItem>) -> Unit,
    onAddToQueue: ((TrackItem) -> Unit)? = null,
    onTrackTitleClick: ((TrackItem) -> Unit)? = null,
    onArtistClick: ((String) -> Unit)? = null
) {
    var isShuffle by remember { mutableStateOf(false) }
    val playlistTracks = remember { mutableStateListOf<TrackItem>() }
    var playlistCoverUrl by remember(playlist.uri) { mutableStateOf(playlist.coverUrl) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var reloadTrigger by remember { mutableIntStateOf(0) }
    val context = androidx.compose.ui.platform.LocalContext.current

    // Fetch playlist tracks from SpotifyBridge or Web API fallback
    LaunchedEffect(playlist.uri, reloadTrigger) {
        if (playlist.uri.isNotEmpty()) {
            isLoading = true
            errorMessage = null
            if (playlist.name == "Favourite Songs" || playlist.uri == "spotify:collection") {
                SpotifyBridge.fetchCollectionTracks { ok, json ->
                    var parsedCount = 0
                    if (ok && json.isNotEmpty()) {
                        try {
                            val obj = JSONObject(json)
                            val items = obj.optJSONArray("tracks")
                            if (items != null) {
                                val loaded = mutableListOf<TrackItem>()
                                for (i in 0 until items.length()) {
                                    val item = items.getJSONObject(i)
                                    val uri = item.optString("uri")
                                    val name = item.optString("name")
                                    val duration = item.optLong("duration_ms")
                                    val albumObj = item.optJSONObject("album")
                                    val albumName = albumObj?.optString("name") ?: ""
                                    val images = albumObj?.optJSONArray("images")
                                    val artUrl = getBestArtworkUrl(images)
                                    val artistsArr = item.optJSONArray("artists")
                                    val artistName = if (artistsArr != null && artistsArr.length() > 0) {
                                        artistsArr.getJSONObject(0).optString("name")
                                    } else "Unknown"
                                    val albumUri = albumObj?.optString("uri") ?: ""
                                    val artistUri = if (artistsArr != null && artistsArr.length() > 0) artistsArr.getJSONObject(0).optString("uri") else ""
                                    loaded.add(TrackItem(name, artistName, albumName, artUrl, duration, uri, albumUri, artistUri))
                                }
                                if (loaded.isNotEmpty()) {
                                    parsedCount = loaded.size
                                    playlistTracks.clear()
                                    playlistTracks.addAll(loaded)
                                    val likedUris = loaded.map { it.uri }.filter { it.isNotEmpty() }.toSet()
                                    val prefs = context.getSharedPreferences("swiftify_prefs", android.content.Context.MODE_PRIVATE)
                                    prefs.edit().putStringSet("saved_track_uris", likedUris).apply()
                                }
                            }
                        } catch (_: Exception) {}
                    }
                    if (parsedCount == 0) {
                        // Web API fallback with auto-token refresh
                        kotlinx.coroutines.MainScope().launch {
                            val webTracks = SpotifyApi.fetchSavedTracks(context, offset = 0, limit = 50)
                            isLoading = false
                            if (webTracks.isNotEmpty()) {
                                playlistTracks.clear()
                                playlistTracks.addAll(webTracks)
                                errorMessage = null
                            } else if (!ok) {
                                errorMessage = "Unable to load Favourite Songs"
                            }
                        }
                    } else {
                        isLoading = false
                    }
                }
            } else {
                SpotifyBridge.fetchPlaylist(playlist.uri) { ok, json ->
                    var parsedCount = 0
                    if (ok && json.isNotEmpty()) {
                        try {
                            val obj = JSONObject(json)
                            val items = obj.optJSONArray("items")
                            val resolvedCover = obj.optString("cover_url", "")
                            val fallbackCoverFromFirstTrack = if (resolvedCover.isEmpty()) {
                                val firstItem = items?.optJSONObject(0)
                                val trackObj = firstItem?.optJSONObject("item") ?: firstItem
                                val alb = trackObj?.optJSONObject("album")
                                val imgs = alb?.optJSONArray("images")
                                getBestArtworkUrl(imgs)
                            } else ""

                            val finalFoundCover = if (resolvedCover.isNotEmpty()) resolvedCover else fallbackCoverFromFirstTrack
                            if (finalFoundCover.isNotEmpty() && playlistCoverUrl.isEmpty()) {
                                playlistCoverUrl = finalFoundCover
                            }
                            if (items != null) {
                                val loaded = mutableListOf<TrackItem>()
                                for (i in 0 until items.length()) {
                                    val itemObj = items.getJSONObject(i)
                                    val item = itemObj.optJSONObject("item") ?: itemObj
                                    val uri = item.optString("uri")
                                    val name = item.optString("name")
                                    val duration = item.optLong("duration_ms")
                                    val albumObj = item.optJSONObject("album")
                                    val albumName = albumObj?.optString("name") ?: ""
                                    val images = albumObj?.optJSONArray("images")
                                    val artUrl = getBestArtworkUrl(images)
                                    val artistsArr = item.optJSONArray("artists")
                                    val artistName = if (artistsArr != null && artistsArr.length() > 0) {
                                        artistsArr.getJSONObject(0).optString("name")
                                    } else "Unknown"
                                    val albumUri = albumObj?.optString("uri") ?: ""
                                    val artistUri = if (artistsArr != null && artistsArr.length() > 0) artistsArr.getJSONObject(0).optString("uri") else ""
                                    loaded.add(TrackItem(name, artistName, albumName, artUrl, duration, uri, albumUri, artistUri))
                                }
                                if (loaded.isNotEmpty()) {
                                    parsedCount = loaded.size
                                    playlistTracks.clear()
                                    playlistTracks.addAll(loaded)
                                }
                            }
                        } catch (_: Exception) {}
                    }
                    if (parsedCount == 0) {
                        // Web API fallback
                        kotlinx.coroutines.MainScope().launch {
                            val webTracks = SpotifyApi.fetchPlaylistTracks(context, playlist.uri, offset = 0, limit = 50)
                            isLoading = false
                            if (webTracks.isNotEmpty()) {
                                playlistTracks.clear()
                                playlistTracks.addAll(webTracks)
                                errorMessage = null
                            } else if (!ok) {
                                errorMessage = "Unable to load playlist tracks"
                            }
                        }
                    } else {
                        isLoading = false
                    }
                }
            }
        } else {
            isLoading = false
        }
    }

    val displayTracks = remember(isShuffle, playlistTracks.size) {
        val base = playlistTracks.toList()
        if (isShuffle) base.shuffled() else base
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF13100E))
    ) {
        // Top Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF221C1A))
            ) {
                Text("←", color = Color.White, fontSize = 20.sp)
            }
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = playlist.name,
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(bottom = 110.dp)
        ) {
            // Header
            item {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(200.dp)
                            .shadow(20.dp, RoundedCornerShape(24.dp), spotColor = Color(0x66F2A385))
                            .clip(RoundedCornerShape(24.dp))
                            .background(Color(0xFF201B19)),
                        contentAlignment = Alignment.Center
                    ) {
                        val finalCover = if (playlistCoverUrl.isNotEmpty()) playlistCoverUrl else playlist.coverUrl
                        if (finalCover.isNotEmpty()) {
                            AsyncImage(
                                model = finalCover,
                                contentDescription = playlist.name,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Icon(
                                imageVector = androidx.compose.material.icons.Icons.Rounded.QueueMusic,
                                contentDescription = null,
                                tint = Color(0xFFF2A385),
                                modifier = Modifier.size(64.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    Text(
                        text = playlist.name,
                        color = Color.White,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = if (playlistTracks.isNotEmpty()) "${playlist.subtitle} • ${playlistTracks.size} tracks" else playlist.subtitle.ifEmpty { "Swiftify Collection" },
                        color = Color(0xFF9E948E),
                        fontSize = 14.sp
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Action Controls
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // Play All Pill
                            Button(
                                onClick = { onPlayAll(displayTracks) },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFFF2A385),
                                    contentColor = Color(0xFF13100E)
                                ),
                                shape = RoundedCornerShape(24.dp),
                                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.PlayArrow,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Play All", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            // Shuffle Toggle
                            IconButton(
                                onClick = { isShuffle = !isShuffle },
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(if (isShuffle) Color(0x33F2A385) else Color(0xFF221C1A))
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Shuffle,
                                    contentDescription = "Shuffle",
                                    tint = if (isShuffle) Color(0xFFF2A385) else Color(0x88FFFFFF),
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }

                        IconButton(
                            onClick = {},
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF221C1A))
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.FavoriteBorder,
                                contentDescription = "Favorite",
                                tint = Color(0xFFF2A385),
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    WavyLine(
                        modifier = Modifier.fillMaxWidth(),
                        color = Color(0x33F2A385),
                        wavelength = 18f,
                        amplitude = 2.5f
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    if (isLoading) {
                        CircularProgressIndicator(
                            color = Color(0xFFF2A385),
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(24.dp).padding(vertical = 4.dp)
                        )
                    }
                }
            }

            // Track list, loading, or empty/error states
            if (isLoading && displayTracks.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(48.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = Color(0xFFF2A385), strokeWidth = 3.dp)
                    }
                }
            } else if (errorMessage != null && displayTracks.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(40.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = errorMessage ?: "Failed to load tracks",
                            color = Color(0xFFA59B94),
                            fontSize = 14.sp,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        Button(
                            onClick = { reloadTrigger++ },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2C221E)),
                            shape = RoundedCornerShape(18.dp)
                        ) {
                            Text("Retry", color = Color(0xFFF2A385), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            } else if (!isLoading && displayTracks.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(48.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "This playlist is empty",
                            color = Color(0xFF8A807A),
                            fontSize = 14.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                itemsIndexed(displayTracks) { index, track ->
                val isCurrent = (livePlayingTrack != null && livePlayingTrack.uri == track.uri)
                val context = LocalContext.current
                var offsetX by remember { mutableStateOf(0f) }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isCurrent) Color(0x33F2A385) else Color.Transparent)
                ) {
                    // Background hint when swiping right
                    if (offsetX > 20f) {
                        Row(
                            modifier = Modifier
                                .matchParentSize()
                                .background(Color(0xFF2C221E))
                                .padding(start = 16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("➕ Add to Queue", color = Color(0xFFF2A385), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .offset { IntOffset(offsetX.roundToInt(), 0) }
                            .draggable(
                                orientation = Orientation.Horizontal,
                                state = rememberDraggableState { delta ->
                                    // only allow swiping to the right
                                    offsetX = (offsetX + delta).coerceIn(0f, 250f)
                                },
                                onDragStopped = {
                                    if (offsetX > 120f) {
                                        onAddToQueue?.invoke(track)
                                        Toast.makeText(context, "Added '${track.title}' to queue", Toast.LENGTH_SHORT).show()
                                    }
                                    offsetX = 0f
                                }
                            )
                            .clickable { onTrackSelect(track, displayTracks) }
                            .padding(vertical = 10.dp, horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                    Text(
                        text = "${index + 1}",
                        color = if (isCurrent) Color(0xFFF2A385) else Color(0xFF7A706A),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.width(28.dp)
                    )

                    Box(modifier = Modifier.size(44.dp)) {
                        AsyncImage(
                            model = track.artworkUrl,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(8.dp)),
                            contentScale = ContentScale.Crop
                        )
                        if (isCurrent && isPlaying) {
                            Box(
                                modifier = Modifier.fillMaxSize().background(Color(0x77000000)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.VolumeUp,
                                    contentDescription = null,
                                    tint = Color(0xFFF2A385),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = track.title,
                            color = if (isCurrent) Color(0xFFF2A385) else Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = track.artist,
                            color = Color(0xFF9E948E),
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Text(
                        text = formatTime(track.durationMs),
                        color = Color(0xFF7A706A),
                        fontSize = 13.sp
                    )
                }
            }
        }
    }
}
}
}
