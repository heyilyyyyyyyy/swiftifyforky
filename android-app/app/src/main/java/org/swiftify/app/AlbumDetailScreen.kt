package org.swiftify.app

import android.content.Context
import android.widget.Toast
import kotlinx.coroutines.launch
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import org.json.JSONObject
import kotlin.math.roundToInt

@Composable
fun AlbumDetailScreen(
    albumUri: String,
    initialTitle: String = "Album",
    initialArtworkUrl: String = "",
    livePlayingTrack: TrackItem?,
    isPlaying: Boolean,
    onBack: () -> Unit,
    onTrackSelect: (TrackItem, List<TrackItem>) -> Unit,
    onPlayAll: (List<TrackItem>) -> Unit,
    onAddToQueue: ((TrackItem) -> Unit)? = null,
    onArtistClick: ((name: String, uri: String) -> Unit)? = null
) {
    var albumTitle by remember { mutableStateOf(initialTitle) }
    var artistName by remember { mutableStateOf("") }
    var artistUri by remember { mutableStateOf("") }
    var albumYear by remember { mutableStateOf(0) }
    var artworkUrl by remember { mutableStateOf(initialArtworkUrl) }
    val albumTracks = remember { mutableStateListOf<TrackItem>() }
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(albumUri) {
        isLoading = true
        errorMessage = null
        albumTracks.clear()

        SpotifyBridge.fetchAlbum(albumUri) { ok, payload ->
            isLoading = false
            if (ok && payload.isNotEmpty()) {
                try {
                    val obj = JSONObject(payload)
                    val n = obj.optString("name")
                    if (n.isNotEmpty()) albumTitle = n
                    artistName = obj.optString("artist_name")
                    artistUri = obj.optString("artist_uri")
                    albumYear = obj.optInt("year", 0)
                    val art = obj.optString("artwork_url")
                    if (art.isNotEmpty()) artworkUrl = art

                    val tracksArr = obj.optJSONArray("tracks")
                    if (tracksArr != null) {
                        val parsed = mutableListOf<TrackItem>()
                        for (i in 0 until tracksArr.length()) {
                            val t = tracksArr.getJSONObject(i)
                            val title = t.optString("name")
                            val uri = t.optString("uri")
                            val durationMs = t.optLong("duration_ms", 0L)
                            val albObj = t.optJSONObject("album")
                            val itemArt = if (artworkUrl.isNotEmpty()) artworkUrl else {
                                val imgs = albObj?.optJSONArray("images")
                                getBestArtworkUrl(imgs)
                            }
                            val artArr = t.optJSONArray("artists")
                            val artNames = if (artArr != null && artArr.length() > 0) {
                                (0 until artArr.length()).joinToString(", ") { j ->
                                    artArr.getJSONObject(j).optString("name")
                                }
                            } else artistName

                            val artistUri = if (artArr != null && artArr.length() > 0) artArr.getJSONObject(0).optString("uri") else ""
                            if (title.isNotEmpty() && uri.isNotEmpty()) {
                                parsed.add(
                                    TrackItem(
                                        title = title,
                                        artist = artNames,
                                        album = albumTitle,
                                        artworkUrl = itemArt,
                                        durationMs = durationMs,
                                        uri = uri,
                                        albumUri = albumUri,
                                        artistUri = artistUri
                                    )
                                )
                            }
                        }
                        albumTracks.addAll(parsed)
                    }
                } catch (e: Exception) {
                    errorMessage = "Error parsing album"
                }
            } else {
                errorMessage = payload.ifEmpty { "Failed to load album" }
            }
        }
    }

    val keyedTracks = remember(albumTracks.toList()) {
        val occurrences = HashMap<String, Int>(albumTracks.size)
        albumTracks.map { track ->
            val baseKey = track.uri.ifEmpty { "${track.title}\u0000${track.artist}" }
            val occurrence = occurrences.getOrDefault(baseKey, 0)
            occurrences[baseKey] = occurrence + 1
            IndexedTrack(track, "$baseKey#$occurrence")
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 20.dp)
    ) {
        // Back Button & Header
        item(key = "album_header", contentType = "header") {
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                StyledBackButton(onBack = onBack)
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "Album",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium
                )
            }
            Spacer(modifier = Modifier.height(20.dp))
        }

        // Album Hero Card
        item(key = "album_hero", contentType = "hero") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(23.dp))
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                MaterialTheme.colorScheme.surfaceContainerHighest,
                                MaterialTheme.colorScheme.surfaceContainer
                            )
                        )
                    )
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(176.dp)
                        .shadow(20.dp, RoundedCornerShape(12.dp), spotColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                ) {
                    AsyncImage(
                        model = artworkUrl,
                        contentDescription = albumTitle,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }

                Spacer(modifier = Modifier.height(18.dp))

                Text(
                    text = albumTitle,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                if (artistName.isNotEmpty()) {
                    Text(
                        text = artistName,
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .padding(top = 6.dp)
                            .clickable {
                                if (artistUri.isNotEmpty()) {
                                    onArtistClick?.invoke(artistName, artistUri)
                                }
                            }
                    )
                }

                val metaStr = buildString {
                    if (albumYear > 0) append("$albumYear • ")
                    append("${albumTracks.size} tracks")
                }
                Text(
                    text = metaStr,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(top = 4.dp)
                )

                Spacer(modifier = Modifier.height(20.dp))

                WavyPillButton(
                    text = "Play All",
                    icon = Icons.Rounded.PlayArrow,
                    backgroundColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    enabled = albumTracks.isNotEmpty(),
                    onClick = {
                        if (albumTracks.isNotEmpty()) {
                            onPlayAll(albumTracks)
                        }
                    }
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }

        if (isLoading) {
            item(key = "loading", contentType = "status") {
                Box(
                    modifier = Modifier.fillMaxWidth().height(160.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary, strokeWidth = 3.dp)
                }
            }
        } else if (errorMessage != null && albumTracks.isEmpty()) {
            item(key = "error", contentType = "status") {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = errorMessage ?: "No tracks in album",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            itemsIndexed(
                items = keyedTracks,
                key = { _, indexed -> indexed.key },
                contentType = { _, _ -> "track_row" }
            ) { idx, indexed ->
                val track = indexed.track
                val isCurrent = livePlayingTrack?.uri == track.uri
                var offsetX by remember { mutableFloatStateOf(0f) }
                val swipeArmed by remember { derivedStateOf { offsetX > 110f } }

                Box(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (offsetX > 10f) {
                        Row(
                            modifier = Modifier
                                .matchParentSize()
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                                .padding(start = 16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.QueueMusic,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (swipeArmed) "Add to Queue" else "Swipe to queue",
                                color = MaterialTheme.colorScheme.primary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(64.dp)
                            .offset { IntOffset(offsetX.roundToInt(), 0) }
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                when {
                                    isCurrent -> MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                    offsetX > 10f -> MaterialTheme.colorScheme.background
                                    else -> Color.Transparent
                                }
                            )
                            .clickable {
                                if (offsetX == 0f) {
                                    onTrackSelect(track, albumTracks)
                                }
                            }
                            .draggable(
                                orientation = Orientation.Horizontal,
                                state = rememberDraggableState { delta ->
                                    offsetX = (offsetX + delta).coerceIn(0f, 240f)
                                },
                                onDragStopped = {
                                    if (swipeArmed) {
                                        onAddToQueue?.invoke(track)
                                        Toast.makeText(context, "Added '${track.title}' to queue", Toast.LENGTH_SHORT).show()
                                    }
                                    coroutineScope.launch {
                                        animate(
                                            initialValue = offsetX,
                                            targetValue = 0f,
                                            animationSpec = spring(dampingRatio = 0.7f, stiffness = 600f)
                                        ) { value, _ -> offsetX = value }
                                    }
                                }
                            )
                            .padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${idx + 1}",
                            color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.width(30.dp)
                        )

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = track.title,
                                color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = track.artist,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 13.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        if (isCurrent && isPlaying) {
                            Icon(
                                imageVector = Icons.Rounded.VolumeUp,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                        } else {
                            Text(
                                text = formatTime(track.durationMs),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }
        }

        item(key = "bottom_spacer", contentType = "spacer") {
            Spacer(modifier = Modifier.height(130.dp))
        }
    }
}
