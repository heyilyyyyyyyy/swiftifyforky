package org.swiftify.app

import android.content.Context
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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

data class ReleaseItem(
    val uri: String,
    val name: String,
    val releaseType: String, // "album", "single", "compilation"
    val year: Int,
    val artworkUrl: String,
    val trackCount: Int
)

@Composable
fun ArtistDetailScreen(
    artist: ArtistItem,
    livePlayingTrack: TrackItem?,
    isPlaying: Boolean,
    onBack: () -> Unit,
    onTrackSelect: (TrackItem, List<TrackItem>) -> Unit,
    onPlayAll: (List<TrackItem>) -> Unit,
    onAddToQueue: ((TrackItem) -> Unit)? = null,
    onAlbumSelect: ((albumUri: String, title: String, artworkUrl: String) -> Unit)? = null
) {
    val topTracks = remember { mutableStateListOf<TrackItem>() }
    val releases = remember { mutableStateListOf<ReleaseItem>() }
    var showAllTracks by remember { mutableStateOf(false) }
    var selectedFilter by remember { mutableStateOf("All") } // "All", "Albums", "Singles & EPs"
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var artistAvatar by remember { mutableStateOf(artist.artworkUrl) }

    val context = androidx.compose.ui.platform.LocalContext.current
    val prefs = remember { context.getSharedPreferences("swiftify_prefs", Context.MODE_PRIVATE) }
    val cacheKey = "cached_artist_" + artist.uri

    fun parseArtistPayload(payload: String) {
        try {
            val obj = JSONObject(payload)
            val av = obj.optString("avatar_url")
            if (av.isNotEmpty()) artistAvatar = av

            // Parse top tracks
            val tracksArr = obj.optJSONArray("tracks") ?: obj.optJSONArray("items")
            if (tracksArr != null) {
                val parsedTracks = mutableListOf<TrackItem>()
                for (i in 0 until tracksArr.length()) {
                    val t = tracksArr.getJSONObject(i)
                    val title = t.optString("name")
                    val uri = t.optString("uri")
                    val durationMs = t.optLong("duration_ms", 0L)
                    val albumObj = t.optJSONObject("album")
                    val albumName = albumObj?.optString("name") ?: ""
                    val images = albumObj?.optJSONArray("images")
                    val artworkUrl = getBestArtworkUrl(images).ifEmpty { artistAvatar }

                    val artistsArr = t.optJSONArray("artists")
                    val artistNames = if (artistsArr != null && artistsArr.length() > 0) {
                        (0 until artistsArr.length()).joinToString(", ") { j ->
                            artistsArr.getJSONObject(j).optString("name")
                        }
                    } else artist.name

                    if (title.isNotEmpty() && uri.isNotEmpty()) {
                        parsedTracks.add(
                            TrackItem(
                                title = title,
                                artist = artistNames,
                                album = albumName,
                                artworkUrl = artworkUrl,
                                durationMs = durationMs,
                                uri = uri
                            )
                        )
                    }
                }
                topTracks.clear()
                topTracks.addAll(parsedTracks)
            }

            // Parse releases
            val relArr = obj.optJSONArray("releases")
            if (relArr != null) {
                val parsedReleases = mutableListOf<ReleaseItem>()
                for (i in 0 until relArr.length()) {
                    val r = relArr.getJSONObject(i)
                    parsedReleases.add(
                        ReleaseItem(
                            uri = r.optString("uri"),
                            name = r.optString("name"),
                            releaseType = r.optString("release_type", "album"),
                            year = r.optInt("year", 0),
                            artworkUrl = r.optString("artwork_url"),
                            trackCount = r.optInt("track_count", 0)
                        )
                    )
                }
                releases.clear()
                releases.addAll(parsedReleases)
            }
        } catch (e: Exception) {
            Log.e("ArtistDetail", "Failed to parse artist payload", e)
        }
    }

    LaunchedEffect(artist.uri) {
        errorMessage = null
        val cached = prefs.getString(cacheKey, null)
        if (!cached.isNullOrEmpty()) {
            parseArtistPayload(cached)
            isLoading = false
        } else {
            isLoading = true
            topTracks.clear()
            releases.clear()
        }

        if (artist.uri.isNotEmpty()) {
            SpotifyBridge.fetchArtistTopTracks(artist.uri) { ok, payload ->
                isLoading = false
                if (ok && payload.isNotEmpty()) {
                    parseArtistPayload(payload)
                    prefs.edit().putString(cacheKey, payload).apply()
                } else if (topTracks.isEmpty()) {
                    errorMessage = payload.ifEmpty { "Failed to load artist details" }
                }
            }
        } else {
            isLoading = false
            errorMessage = "No Spotify URI for this artist"
        }
    }

    val visibleTracks = if (showAllTracks) topTracks else topTracks.take(5)
    val filteredReleases = when (selectedFilter) {
        "Albums" -> releases.filter { it.releaseType == "album" }
        "Singles & EPs" -> releases.filter { it.releaseType == "single" || it.releaseType == "ep" }
        else -> releases
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
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF221C1A))
                ) {
                    Text("←", color = Color(0xFFF5EFEA), fontSize = 20.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "Artist",
                    color = Color(0xFFA59B94),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium
                )
            }
            Spacer(modifier = Modifier.height(20.dp))
        }

        // Hero Header
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
                SquiggleAvatar(
                    imageUrl = artistAvatar,
                    contentDescription = artist.name,
                    modifier = Modifier.size(150.dp),
                    ringColor = Color(0xFFF2A385),
                    waves = 20,
                    amplitude = 1.4.dp,
                    strokeWidth = 2.4.dp,
                    gap = 4.2.dp
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = artist.name,
                    color = Color(0xFFF5EFEA),
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )

                Text(
                    text = "ARTIST",
                    color = Color(0xFFF2A385),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.5.sp,
                    modifier = Modifier.padding(top = 4.dp)
                )

                Spacer(modifier = Modifier.height(18.dp))

                Button(
                    onClick = {
                        if (topTracks.isNotEmpty()) {
                            onPlayAll(topTracks)
                        }
                    },
                    enabled = topTracks.isNotEmpty(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFF2A385),
                        contentColor = Color(0xFF221612),
                        disabledContainerColor = Color(0xFF382F2B),
                        disabledContentColor = Color(0xFF6B605A)
                    ),
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier.fillMaxWidth(0.6f).height(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Play Top Songs", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
            }

            Spacer(modifier = Modifier.height(28.dp))
        }

        // Section Title: Popular Songs
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Popular Songs",
                    color = Color(0xFFF5EFEA),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                WavyLine(
                    modifier = Modifier.weight(1f).padding(start = 14.dp),
                    color = Color(0x33F2A385),
                    wavelength = 18f,
                    amplitude = 2.5f
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
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
        } else if (errorMessage != null && topTracks.isEmpty()) {
            item {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = errorMessage ?: "No tracks available",
                        color = Color(0xFFA59B94),
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            // Tracks (5 or 10)
            itemsIndexed(visibleTracks) { idx, track ->
                val isCurrent = livePlayingTrack?.uri == track.uri
                val context = LocalContext.current
                var offsetX by remember { mutableStateOf(0f) }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (isCurrent) Color(0x22F2A385) else Color.Transparent)
                ) {
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
                            .clickable { onTrackSelect(track, topTracks) }
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

                        AsyncImage(
                            model = track.artworkUrl,
                            contentDescription = null,
                            modifier = Modifier
                                .size(46.dp)
                                .clip(RoundedCornerShape(10.dp)),
                            contentScale = ContentScale.Crop
                        )

                        Spacer(modifier = Modifier.width(14.dp))

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
                                text = track.album.ifEmpty { track.artist },
                                color = Color(0xFFA59B94),
                                fontSize = 13.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        if (isCurrent && isPlaying) {
                            Text("🔊", fontSize = 14.sp)
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

            // More / Less button
            if (topTracks.size > 5) {
                item {
                    TextButton(
                        onClick = { showAllTracks = !showAllTracks },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                    ) {
                        Text(
                            text = if (showAllTracks) "Show Less ↑" else "More (10 Songs) ↓",
                            color = Color(0xFFF2A385),
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                }
            }
        }

        // Section Title: Discography & Filter Chips
        if (releases.isNotEmpty()) {
            item {
                Spacer(modifier = Modifier.height(28.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Discography",
                        color = Color(0xFFF5EFEA),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                    WavyLine(
                        modifier = Modifier.weight(1f).padding(start = 14.dp),
                        color = Color(0x33F2A385),
                        wavelength = 18f,
                        amplitude = 2.5f
                    )
                }
                Spacer(modifier = Modifier.height(14.dp))

                // Filter chips
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("All", "Albums", "Singles & EPs").forEach { chip ->
                        val isSel = (selectedFilter == chip)
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(if (isSel) Color(0xFFF2A385) else Color(0xFF221C1A))
                                .clickable { selectedFilter = chip }
                                .padding(horizontal = 14.dp, vertical = 7.dp)
                        ) {
                            Text(
                                text = chip,
                                color = if (isSel) Color(0xFF13100E) else Color(0xFFA59B94),
                                fontSize = 13.sp,
                                fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Horizontal Carousel or List of Releases
            item {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(filteredReleases) { rel ->
                        Column(
                            modifier = Modifier
                                .width(130.dp)
                                .clickable {
                                    onAlbumSelect?.invoke(rel.uri, rel.name, rel.artworkUrl)
                                }
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(130.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(Color(0xFF221C1A))
                            ) {
                                AsyncImage(
                                    model = rel.artworkUrl,
                                    contentDescription = rel.name,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = rel.name,
                                color = Color(0xFFF5EFEA),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            val desc = buildString {
                                if (rel.year > 0) append("${rel.year} • ")
                                append(if (rel.releaseType == "album") "Album" else "Single")
                            }
                            Text(
                                text = desc,
                                color = Color(0xFFA59B94),
                                fontSize = 12.sp,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(110.dp))
        }
    }
}
