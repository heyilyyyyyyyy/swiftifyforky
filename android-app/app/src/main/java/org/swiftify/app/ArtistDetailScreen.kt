package org.swiftify.app

import android.content.Context
import android.util.Log
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
import androidx.compose.ui.platform.LocalDensity
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
    val coroutineScope = rememberCoroutineScope()
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

                    val albumUri = albumObj?.optString("uri") ?: ""
                    if (title.isNotEmpty() && uri.isNotEmpty()) {
                        parsedTracks.add(
                            TrackItem(
                                title = title,
                                artist = artistNames,
                                album = albumName,
                                artworkUrl = artworkUrl,
                                durationMs = durationMs,
                                uri = uri,
                                albumUri = albumUri,
                                artistUri = artist.uri
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

    val keyedTracks = remember(visibleTracks.toList()) {
        val occurrences = HashMap<String, Int>(visibleTracks.size)
        visibleTracks.map { track ->
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
            .padding(horizontal = 16.dp)
    ) {
        // Back Button & Header
        item(key = "artist_header", contentType = "header") {
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                StyledBackButton(onBack = onBack)
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "Artist",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium
                )
            }
            Spacer(modifier = Modifier.height(20.dp))
        }

        // Hero Header
        item(key = "artist_hero", contentType = "hero") {
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
                SquiggleAvatar(
                    imageUrl = artistAvatar,
                    contentDescription = artist.name,
                    modifier = Modifier.size(150.dp),
                    ringColor = MaterialTheme.colorScheme.primary,
                    waves = 20,
                    amplitude = 1.4.dp,
                    strokeWidth = 2.4.dp,
                    gap = 4.2.dp
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = artist.name,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(6.dp))

                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .padding(horizontal = 10.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = "ARTIST",
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                }

                Spacer(modifier = Modifier.height(18.dp))

                WavyPillButton(
                    text = "Play Top Songs",
                    icon = Icons.Rounded.PlayArrow,
                    backgroundColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    enabled = topTracks.isNotEmpty(),
                    onClick = {
                        if (topTracks.isNotEmpty()) {
                            onPlayAll(topTracks)
                        }
                    }
                )
            }

            Spacer(modifier = Modifier.height(28.dp))
        }

        // Section Title: Popular Songs
        item(key = "popular_title", contentType = "section_title") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Popular Songs",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                WavyLine(
                    modifier = Modifier.weight(1f).padding(start = 14.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f),
                    wavelength = 18f,
                    amplitude = 2.5f
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
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
        } else if (errorMessage != null && topTracks.isEmpty()) {
            item(key = "error", contentType = "status") {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = errorMessage ?: "No tracks available",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            // Tracks (5 or 10)
            itemsIndexed(
                items = keyedTracks,
                key = { _, indexed -> indexed.key },
                contentType = { _, _ -> "track_row" }
            ) { idx, indexed ->
                val track = indexed.track
                val isCurrent = livePlayingTrack?.uri == track.uri
                var dragDistance by remember(indexed.key) { mutableFloatStateOf(0f) }
                var offsetX by remember(indexed.key) { mutableFloatStateOf(0f) }
                val swipeArmed by remember { derivedStateOf { offsetX > 100f } }
                val density = LocalDensity.current
                val swipeThresholdPx = remember(density) { with(density) { 28.dp.toPx() } }

                Box(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (offsetX > 15f) {
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
                                    offsetX > 15f -> MaterialTheme.colorScheme.background
                                    else -> Color.Transparent
                                }
                            )
                            .clickable {
                                if (offsetX == 0f) {
                                    onTrackSelect(track, topTracks)
                                }
                            }
                            .draggable(
                                orientation = Orientation.Horizontal,
                                state = rememberDraggableState { delta ->
                                    dragDistance = (dragDistance + delta).coerceAtLeast(0f)
                                    offsetX = if (dragDistance > swipeThresholdPx) {
                                        (dragDistance - swipeThresholdPx).coerceIn(0f, 220f)
                                    } else {
                                        0f
                                    }
                                },
                                onDragStopped = {
                                    if (swipeArmed) {
                                        onAddToQueue?.invoke(track)
                                        Toast.makeText(context, "Added '${track.title}' to queue", Toast.LENGTH_SHORT).show()
                                    }
                                    dragDistance = 0f
                                    coroutineScope.launch {
                                        if (offsetX > 0f) {
                                            animate(
                                                initialValue = offsetX,
                                                targetValue = 0f,
                                                animationSpec = spring(dampingRatio = 0.8f, stiffness = 800f)
                                            ) { value, _ -> offsetX = value }
                                        }
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
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            modifier = Modifier.width(30.dp)
                        )

                        AsyncImage(
                            model = track.artworkUrl,
                            contentDescription = null,
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(7.dp)),
                            contentScale = ContentScale.Crop
                        )

                        Spacer(modifier = Modifier.width(14.dp))

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
                                text = track.album.ifEmpty { track.artist },
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
                                fontSize = 13.sp,
                                textAlign = androidx.compose.ui.text.style.TextAlign.End,
                                modifier = Modifier.width(44.dp)
                            )
                        }
                    }
                }
            }

            // More / Less button
            if (topTracks.size > 5) {
                item(key = "more_button", contentType = "action") {
                    TextButton(
                        onClick = { showAllTracks = !showAllTracks },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                    ) {
                        Text(
                            text = if (showAllTracks) "Show Less ↑" else "More (10 Songs) ↓",
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                }
            }
        }

        // Section Title: Discography & Filter Chips
        if (releases.isNotEmpty()) {
            item(key = "discography_title", contentType = "section_title") {
                Spacer(modifier = Modifier.height(28.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Discography",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                    WavyLine(
                        modifier = Modifier.weight(1f).padding(start = 14.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f),
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
                                .clip(CircleShape)
                                .background(if (isSel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh)
                                .clickable { selectedFilter = chip }
                                .padding(horizontal = 14.dp, vertical = 7.dp)
                        ) {
                            Text(
                                text = chip,
                                color = if (isSel) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 13.sp,
                                fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Horizontal Carousel or List of Releases
            item(key = "discography_row", contentType = "horizontal_list") {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(
                        items = filteredReleases,
                        key = { it.uri.ifEmpty { "${it.name}#${it.year}" } },
                        contentType = { "release_card" }
                    ) { rel ->
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
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
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
                                color = MaterialTheme.colorScheme.onSurface,
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
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 12.sp,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }

        item(key = "bottom_spacer", contentType = "spacer") {
            Spacer(modifier = Modifier.height(110.dp))
        }
    }
}
