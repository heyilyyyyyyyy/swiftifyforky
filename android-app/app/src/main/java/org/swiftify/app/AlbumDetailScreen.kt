package org.swiftify.app

import android.content.Context
import android.widget.Toast
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

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF13100E))
            .padding(horizontal = 20.dp)
    ) {
        // Back Button & Header
        item {
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                StyledBackButton(onBack = onBack)
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "Album",
                    color = Color(0xFFA59B94),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium
                )
            }
            Spacer(modifier = Modifier.height(20.dp))
        }

        // Album Hero Card
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(
                        Brush.verticalGradient(
                            listOf(Color(0xFF2A201C), Color(0xFF1B1513))
                        )
                    )
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(200.dp)
                        .shadow(22.dp, RoundedCornerShape(24.dp), spotColor = Color(0x66F2A385))
                        .clip(RoundedCornerShape(24.dp))
                        .background(Color(0xFF221C1A))
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
                    color = Color(0xFFF5EFEA),
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                if (artistName.isNotEmpty()) {
                    Text(
                        text = artistName,
                        color = Color(0xFFF2A385),
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
                    color = Color(0xFFA59B94),
                    fontSize = 13.sp,
                    modifier = Modifier.padding(top = 4.dp)
                )

                Spacer(modifier = Modifier.height(20.dp))

                WavyPillButton(
                    text = "Play All",
                    icon = Icons.Rounded.PlayArrow,
                    backgroundColor = Color(0xFFF2A385),
                    contentColor = Color(0xFF13100E),
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
            item {
                Box(
                    modifier = Modifier.fillMaxWidth().height(160.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = Color(0xFFF2A385), strokeWidth = 3.dp)
                }
            }
        } else if (errorMessage != null && albumTracks.isEmpty()) {
            item {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = errorMessage ?: "No tracks in album",
                        color = Color(0xFFA59B94),
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            itemsIndexed(albumTracks) { idx, track ->
                val isCurrent = livePlayingTrack?.uri == track.uri
                val context = LocalContext.current
                var offsetX by remember { mutableStateOf(0f) }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isCurrent) Color(0x33F2A385) else Color.Transparent)
                ) {
                    // Swipe hint
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
                            .clickable { onTrackSelect(track, albumTracks) }
                            .padding(horizontal = 8.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${idx + 1}",
                            color = if (isCurrent) Color(0xFFF2A385) else Color(0xFF756A64),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.width(28.dp)
                        )

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = track.title,
                                color = if (isCurrent) Color(0xFFF2A385) else Color(0xFFF5EFEA),
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = track.artist,
                                color = Color(0xFFA59B94),
                                fontSize = 13.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        if (isCurrent && isPlaying) {
                            Icon(
                                imageVector = Icons.Rounded.VolumeUp,
                                contentDescription = null,
                                tint = Color(0xFFF2A385),
                                modifier = Modifier.size(16.dp)
                            )
                        } else {
                            val min = track.durationMs / 60000
                            val sec = (track.durationMs % 60000) / 1000
                            Text(
                                text = String.format("%d:%02d", min, sec),
                                color = Color(0xFF756A64),
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(130.dp))
        }
    }
}
