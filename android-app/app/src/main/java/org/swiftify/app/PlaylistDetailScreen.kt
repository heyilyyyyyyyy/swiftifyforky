package org.swiftify.app
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import android.content.Intent
import android.content.ClipboardManager
import android.content.ClipData

enum class PlaylistSortMode(val label: String) {
    DEFAULT("Default order"),
    TITLE("Title (A-Z)"),
    ARTIST("Artist (A-Z)"),
    DURATION("Duration")
}

fun formatPlaylistDuration(ms: Long): String {
    val totalSeconds = ms / 1000L
    val hours = totalSeconds / 3600L
    val minutes = (totalSeconds % 3600L) / 60L
    return if (hours > 0) "${hours} hr ${minutes} min" else "${minutes} min"
}

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
    onArtistClick: ((String) -> Unit)? = null,
    userPlaylists: List<PlaylistItem> = emptyList(),
    onDeletePlaylist: ((PlaylistItem) -> Unit)? = null,
    onAddTrackToPlaylist: ((PlaylistItem, TrackItem) -> Unit)? = null,
    onRemoveTrackFromPlaylist: ((PlaylistItem, TrackItem) -> Unit)? = null
) {
    var isShuffle by remember { mutableStateOf(false) }
    val playlistTracks = remember { mutableStateListOf<TrackItem>() }
    var playlistCoverUrl by remember(playlist.uri) { mutableStateOf(playlist.coverUrl) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var reloadTrigger by remember { mutableIntStateOf(0) }
    val context = androidx.compose.ui.platform.LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val prefs = remember { context.getSharedPreferences("swiftify_prefs", android.content.Context.MODE_PRIVATE) }
    val savedTrackUris = remember {
        val cached = prefs.getStringSet("saved_track_uris", emptySet()) ?: emptySet()
        mutableStateListOf<String>().apply { addAll(cached) }
    }
    var showDeleteConfirmation by remember { mutableStateOf(false) }
    var targetTrackForAddToPlaylist by remember { mutableStateOf<TrackItem?>(null) }

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

    var searchQuery by remember { mutableStateOf("") }
    var sortMode by remember { mutableStateOf(PlaylistSortMode.DEFAULT) }
    var isSortMenuOpen by remember { mutableStateOf(false) }
    var isMoreMenuOpen by remember { mutableStateOf(false) }

    val totalDurationMs = remember(playlistTracks.size) {
        playlistTracks.sumOf { it.durationMs }
    }

    val displayTracks = remember(isShuffle, playlistTracks.size, sortMode, searchQuery) {
        var list = playlistTracks.toList()
        if (searchQuery.isNotBlank()) {
            val q = searchQuery.trim()
            list = list.filter {
                it.title.contains(q, ignoreCase = true) ||
                it.artist.contains(q, ignoreCase = true) ||
                it.album.contains(q, ignoreCase = true)
            }
        }
        when (sortMode) {
            PlaylistSortMode.TITLE -> list = list.sortedBy { it.title.lowercase() }
            PlaylistSortMode.ARTIST -> list = list.sortedBy { it.artist.lowercase() }
            PlaylistSortMode.DURATION -> list = list.sortedBy { it.durationMs }
            PlaylistSortMode.DEFAULT -> {}
        }
        if (isShuffle) list.shuffled() else list
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF13100E))
    ) {
        // Top Bar matching Swiftify Desktop:
        // [ StyledBackButton ] [ 🔍 Find in Playlist (Search Pill) ] [ Share ] [ Sort ] [ More ]
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            StyledBackButton(onBack = onBack)

            Spacer(modifier = Modifier.width(10.dp))

            // Search pill: "Find in Playlist"
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(38.dp)
                    .clip(RoundedCornerShape(19.dp))
                    .background(Color(0xFF221C1A))
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Search,
                        contentDescription = "Search",
                        tint = Color(0xFF8E847E),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(modifier = Modifier.weight(1f)) {
                        if (searchQuery.isEmpty()) {
                            Text(
                                text = "Find in Playlist",
                                color = Color(0xFF8E847E),
                                fontSize = 13.sp
                            )
                        }
                        BasicTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            textStyle = TextStyle(color = Color.White, fontSize = 13.sp),
                            singleLine = true,
                            cursorBrush = SolidColor(Color(0xFFF2A385)),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    if (searchQuery.isNotEmpty()) {
                        IconButton(
                            onClick = { searchQuery = "" },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = "Clear",
                                tint = Color(0xFFA59B94),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.width(6.dp))

            // Share Action
            IconButton(
                onClick = {
                    val cleanId = playlist.uri.removePrefix("spotify:playlist:").removePrefix("spotify:user:").trim()
                    val shareUrl = if (playlist.uri.startsWith("spotify:playlist:")) {
                        "https://open.spotify.com/playlist/$cleanId"
                    } else {
                        "https://open.spotify.com"
                    }
                    val sendIntent = Intent().apply {
                        action = Intent.ACTION_SEND
                        putExtra(Intent.EXTRA_TEXT, "Listen to ${playlist.name} on Spotify: $shareUrl")
                        type = "text/plain"
                    }
                    context.startActivity(Intent.createChooser(sendIntent, "Share Playlist"))
                },
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Share,
                    contentDescription = "Share",
                    tint = Color(0xFFD6CBC5),
                    modifier = Modifier.size(20.dp)
                )
            }

            // Sort Menu Action
            Box {
                IconButton(
                    onClick = { isSortMenuOpen = true },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Sort,
                        contentDescription = "Sort",
                        tint = if (sortMode != PlaylistSortMode.DEFAULT) Color(0xFFF2A385) else Color(0xFFD6CBC5),
                        modifier = Modifier.size(20.dp)
                    )
                }

                DropdownMenu(
                    expanded = isSortMenuOpen,
                    onDismissRequest = { isSortMenuOpen = false },
                    modifier = Modifier.background(Color(0xFF221C1A))
                ) {
                    PlaylistSortMode.values().forEach { mode ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = mode.label,
                                    color = if (sortMode == mode) Color(0xFFF2A385) else Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = if (sortMode == mode) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            onClick = {
                                sortMode = mode
                                isSortMenuOpen = false
                            }
                        )
                    }
                }
            }

            // More Options Action
            Box {
                IconButton(
                    onClick = { isMoreMenuOpen = true },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.MoreVert,
                        contentDescription = "More",
                        tint = Color(0xFFD6CBC5),
                        modifier = Modifier.size(20.dp)
                    )
                }

                DropdownMenu(
                    expanded = isMoreMenuOpen,
                    onDismissRequest = { isMoreMenuOpen = false },
                    modifier = Modifier.background(Color(0xFF221C1A))
                ) {
                    DropdownMenuItem(
                        text = { Text("Copy Spotify URI", color = Color.White, fontSize = 13.sp) },
                        onClick = {
                            isMoreMenuOpen = false
                            val cm = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as? ClipboardManager
                            val clip = ClipData.newPlainText("Spotify URI", playlist.uri)
                            cm?.setPrimaryClip(clip)
                            Toast.makeText(context, "Copied Spotify URI", Toast.LENGTH_SHORT).show()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Refresh Playlist", color = Color.White, fontSize = 13.sp) },
                        onClick = {
                            isMoreMenuOpen = false
                            reloadTrigger++
                        }
                    )
                    if (playlist.uri.startsWith("spotify:playlist:") && onDeletePlaylist != null) {
                        DropdownMenuItem(
                            text = { Text("Delete Playlist", color = Color(0xFFFF6B6B), fontSize = 13.sp) },
                            onClick = {
                                isMoreMenuOpen = false
                                showDeleteConfirmation = true
                            }
                        )
                    }
                }
            }
        }

        if (showDeleteConfirmation) {
            AlertDialog(
                onDismissRequest = { showDeleteConfirmation = false },
                title = { Text("Delete Playlist", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp) },
                text = { Text("Are you sure you want to delete '${playlist.name}'? This cannot be undone.", color = Color(0xFFA59B94), fontSize = 14.sp) },
                containerColor = Color(0xFF221C1A),
                confirmButton = {
                    Button(
                        onClick = {
                            showDeleteConfirmation = false
                            onDeletePlaylist?.invoke(playlist)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF4D4D)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Delete", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteConfirmation = false }) {
                        Text("Cancel", color = Color(0xFFA59B94))
                    }
                }
            )
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(bottom = 110.dp)
        ) {
            // Header matching Swiftify Desktop
            item {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(190.dp)
                            .shadow(24.dp, RoundedCornerShape(26.dp), spotColor = Color(0x66F2A385))
                            .clip(RoundedCornerShape(26.dp))
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

                    Spacer(modifier = Modifier.height(14.dp))

                    // Pill Badge: ≡ PLAYLIST
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0x33F2A385))
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Rounded.QueueMusic,
                                contentDescription = null,
                                tint = Color(0xFFF2A385),
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = "PLAYLIST",
                                color = Color(0xFFF2A385),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = playlist.name,
                        color = Color.White,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    val ownerName = playlist.owner.ifEmpty { playlist.subtitle.ifEmpty { "Swiftify" } }
                    val durationStr = formatPlaylistDuration(totalDurationMs)
                    val factsStr = "$ownerName  •  ${playlistTracks.size} songs  •  $durationStr"

                    Text(
                        text = factsStr,
                        color = Color(0xFF9E948E),
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(18.dp))

                    // Wavy Scalloped Play & Shuffle Buttons (Swiftify Desktop Style, No Heart Icon)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        WavyPillButton(
                            text = "Play",
                            icon = Icons.Rounded.PlayArrow,
                            backgroundColor = Color(0xFFF2A385),
                            contentColor = Color(0xFF13100E),
                            enabled = displayTracks.isNotEmpty(),
                            onClick = { onPlayAll(displayTracks) }
                        )

                        Spacer(modifier = Modifier.width(14.dp))

                        WavyPillButton(
                            text = "Shuffle",
                            icon = Icons.Rounded.Shuffle,
                            backgroundColor = Color(0xFF332722),
                            contentColor = Color(0xFFF2A385),
                            enabled = displayTracks.isNotEmpty(),
                            onClick = {
                                isShuffle = true
                                onPlayAll(displayTracks.shuffled())
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(18.dp))

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
                            text = if (searchQuery.isNotBlank()) "No results found for “$searchQuery”" else "This playlist is empty",
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
                val offsetX = remember { Animatable(0f) }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0xFF231C1A))
                ) {
                    // Background revealed when swiping right
                    if (offsetX.value > 10f) {
                        Row(
                            modifier = Modifier
                                .matchParentSize()
                                .background(Color(0xFF2B201C))
                                .padding(start = 16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.QueueMusic,
                                contentDescription = null,
                                tint = Color(0xFFF2A385),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (offsetX.value > 110f) "Add to Queue" else "Swipe to queue",
                                color = Color(0xFFF2A385),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // Foreground song card (opaque background so text never overlaps!)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .offset { IntOffset(offsetX.value.roundToInt(), 0) }
                            .shadow(if (offsetX.value > 5f) 8.dp else 0.dp, RoundedCornerShape(14.dp))
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (isCurrent) Color(0xFF281E1B) else Color(0xFF13100E))
                            .clickable {
                                if (offsetX.value == 0f) {
                                    onTrackSelect(track, displayTracks)
                                }
                            }
                            .draggable(
                                orientation = Orientation.Horizontal,
                                state = rememberDraggableState { delta ->
                                    coroutineScope.launch {
                                        offsetX.snapTo((offsetX.value + delta).coerceIn(0f, 240f))
                                    }
                                },
                                onDragStopped = {
                                    if (offsetX.value > 110f) {
                                        onAddToQueue?.invoke(track)
                                        Toast.makeText(context, "Added '${track.title}' to queue", Toast.LENGTH_SHORT).show()
                                    }
                                    coroutineScope.launch {
                                        offsetX.animateTo(0f, spring(dampingRatio = 0.7f, stiffness = 600f))
                                    }
                                }
                            )
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

                    // Star button (Save / Liked Track)
                    val isTrackSaved = savedTrackUris.contains(track.uri)
                    IconButton(
                        onClick = {
                            val next = !isTrackSaved
                            if (next) savedTrackUris.add(track.uri) else savedTrackUris.remove(track.uri)
                            coroutineScope.launch {
                                SpotifyApi.setTrackSaved(context, track.uri, next)
                            }
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = if (isTrackSaved) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                            contentDescription = "Save",
                            tint = if (isTrackSaved) Color(0xFFF2A385) else Color(0xFF6E635E),
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    // Track more options dropdown
                    Box {
                        var isTrackMenuOpen by remember { mutableStateOf(false) }
                        IconButton(
                            onClick = { isTrackMenuOpen = true },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.MoreVert,
                                contentDescription = "Track options",
                                tint = Color(0xFF9E948E),
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        DropdownMenu(
                            expanded = isTrackMenuOpen,
                            onDismissRequest = { isTrackMenuOpen = false },
                            modifier = Modifier.background(Color(0xFF221C1A))
                        ) {
                            DropdownMenuItem(
                                text = { Text("Add to Queue", color = Color.White, fontSize = 13.sp) },
                                onClick = {
                                    isTrackMenuOpen = false
                                    onAddToQueue?.invoke(track)
                                    Toast.makeText(context, "Added '${track.title}' to queue", Toast.LENGTH_SHORT).show()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Add to Playlist...", color = Color.White, fontSize = 13.sp) },
                                onClick = {
                                    isTrackMenuOpen = false
                                    targetTrackForAddToPlaylist = track
                                }
                            )
                            if (playlist.uri.startsWith("spotify:playlist:") && onRemoveTrackFromPlaylist != null) {
                                DropdownMenuItem(
                                    text = { Text("Remove from this Playlist", color = Color(0xFFFF6B6B), fontSize = 13.sp) },
                                    onClick = {
                                        isTrackMenuOpen = false
                                        playlistTracks.remove(track)
                                        onRemoveTrackFromPlaylist.invoke(playlist, track)
                                    }
                                )
                            }
                            DropdownMenuItem(
                                text = { Text("Share Song", color = Color.White, fontSize = 13.sp) },
                                onClick = {
                                    isTrackMenuOpen = false
                                    val cleanTrackId = track.uri.removePrefix("spotify:track:").trim()
                                    val sendIntent = Intent().apply {
                                        action = Intent.ACTION_SEND
                                        putExtra(Intent.EXTRA_TEXT, "Listen to '${track.title}' by ${track.artist}: https://open.spotify.com/track/$cleanTrackId")
                                        type = "text/plain"
                                    }
                                    context.startActivity(Intent.createChooser(sendIntent, "Share Track"))
                                }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(4.dp))

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

    if (targetTrackForAddToPlaylist != null) {
        val target = targetTrackForAddToPlaylist!!
        AddToPlaylistBottomSheet(
            track = target,
            userPlaylists = userPlaylists,
            onDismiss = { targetTrackForAddToPlaylist = null },
            onPlaylistSelect = { selectedPlaylist ->
                targetTrackForAddToPlaylist = null
                onAddTrackToPlaylist?.invoke(selectedPlaylist, target)
            }
        )
    }
}
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddToPlaylistBottomSheet(
    track: TrackItem,
    userPlaylists: List<PlaylistItem>,
    onDismiss: () -> Unit,
    onPlaylistSelect: (PlaylistItem) -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1D1715),
        dragHandle = { BottomSheetDefaults.DragHandle(color = Color(0xFF6E625D)) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 10.dp)
        ) {
            Text(
                text = "Add to playlist",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = track.title,
                color = Color(0xFFA59B94),
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(16.dp))

            if (userPlaylists.isEmpty()) {
                Text(
                    text = "No user playlists found",
                    color = Color(0xFF887D77),
                    fontSize = 14.sp,
                    modifier = Modifier.padding(vertical = 20.dp)
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 350.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(userPlaylists.size) { idx ->
                        val p = userPlaylists[idx]
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFF261F1D))
                                .clickable {
                                    onPlaylistSelect(p)
                                }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFF332A27)),
                                contentAlignment = Alignment.Center
                            ) {
                                if (p.artworkUrl.isNotEmpty()) {
                                    AsyncImage(
                                        model = p.artworkUrl,
                                        contentDescription = null,
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Rounded.MusicNote,
                                        contentDescription = null,
                                        tint = Color(0xFFF2A385),
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = p.name,
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = p.owner,
                                    color = Color(0xFF9E948E),
                                    fontSize = 12.sp,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
