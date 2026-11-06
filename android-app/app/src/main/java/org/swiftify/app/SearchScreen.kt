package org.swiftify.app

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject
import kotlin.math.roundToInt

data class GenreItem(
    val name: String,
    val symbol: String,
    val startColor: Color,
    val endColor: Color
)

val featuredGenres = listOf(
    GenreItem("Pop", "🎵", Color(0xFFFF375F), Color(0xFFFF9F0A)),
    GenreItem("Hip-Hop", "🎧", Color(0xFF5E5CE6), Color(0xFFBF5AF2)),
    GenreItem("Electronic", "⚡", Color(0xFF64D2FF), Color(0xFF30D158)),
    GenreItem("Alternative", "🎸", Color(0xFFBF5AF2), Color(0xFFFF375F)),
    GenreItem("R&B", "🎙️", Color(0xFFFF453A), Color(0xFFFF9F0A)),
    GenreItem("Indie", "✨", Color(0xFF63E6E2), Color(0xFF40C8E0)),
    GenreItem("Dance", "🔥", Color(0xFF0A84FF), Color(0xFF5E5CE6)),
    GenreItem("Chill", "🌙", Color(0xFF40C8E0), Color(0xFF0A84FF))
)

@Composable
fun SearchScreen(
    accessToken: String?,
    allTracks: List<TrackItem>,
    currentTrackIndex: Int,
    livePlayingTrack: TrackItem?,
    isPlaying: Boolean,
    onTrackSelect: (TrackItem) -> Unit,
    onAddToQueue: ((TrackItem) -> Unit)? = null,
    onArtistSelect: (ArtistItem) -> Unit,
    onAlbumSelect: ((TrackItem) -> Unit)? = null,
    onPlaylistSelect: (PlaylistItem) -> Unit
) {
    var query by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("All") }
    val categories = listOf("All", "Songs", "Artists", "Playlists")

    val searchResultsTracks = remember { mutableStateListOf<TrackItem>() }
    val searchResultsArtists = remember { mutableStateListOf<ArtistItem>() }
    val searchResultsPlaylists = remember { mutableStateListOf<PlaylistItem>() }
    var isSearching by remember { mutableStateOf(false) }

    val coroutineScope = rememberCoroutineScope()
    var searchJob by remember { mutableStateOf<Job?>(null) }

    fun doSearch(q: String) {
        val trimmed = q.trim()
        if (trimmed.isEmpty()) {
            searchResultsTracks.clear()
            searchResultsArtists.clear()
            searchResultsPlaylists.clear()
            isSearching = false
            return
        }

        searchJob?.cancel()
        searchJob = coroutineScope.launch {
            delay(300) // debounce
            isSearching = true

            // Fetch tracks
            SpotifyBridge.searchTracks(trimmed) { okTracks, jsonTracks ->
                if (okTracks && jsonTracks.isNotEmpty()) {
                    try {
                        val obj = JSONObject(jsonTracks)
                        val tracksArr = obj.optJSONArray("tracks")
                        if (tracksArr != null) {
                            val list = mutableListOf<TrackItem>()
                            for (i in 0 until tracksArr.length()) {
                                val item = tracksArr.getJSONObject(i)
                                val name = item.optString("name")
                                val uri = item.optString("uri")
                                val duration = item.optLong("duration_ms")
                                val albumObj = item.optJSONObject("album")
                                val albumName = albumObj?.optString("name") ?: ""
                                val images = albumObj?.optJSONArray("images")
                                val artUrl = if (images != null && images.length() > 0) {
                                    images.getJSONObject(0).optString("url")
                                } else ""
                                val artistsArr = item.optJSONArray("artists")
                                val artistName = if (artistsArr != null && artistsArr.length() > 0) {
                                    (0 until artistsArr.length()).joinToString(", ") { j ->
                                        artistsArr.getJSONObject(j).optString("name")
                                    }
                                } else "Unknown"
                                list.add(TrackItem(name, artistName, albumName, artUrl, duration, uri))
                            }
                            searchResultsTracks.clear()
                            searchResultsTracks.addAll(list)
                        }
                    } catch (_: Exception) {}
                }

                // Fetch artists
                SpotifyBridge.searchArtists(trimmed) { okArtists, jsonArtists ->
                    if (okArtists && jsonArtists.isNotEmpty()) {
                        try {
                            val obj = JSONObject(jsonArtists)
                            val artArr = obj.optJSONArray("artists")
                            if (artArr != null) {
                                val aList = mutableListOf<ArtistItem>()
                                for (i in 0 until artArr.length()) {
                                    val item = artArr.getJSONObject(i)
                                    aList.add(
                                        ArtistItem(
                                            name = item.optString("name"),
                                            artworkUrl = item.optString("artwork_url"),
                                            uri = item.optString("uri")
                                        )
                                    )
                                }
                                searchResultsArtists.clear()
                                searchResultsArtists.addAll(aList)
                            }
                        } catch (_: Exception) {}
                    }
                    isSearching = false
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF13100E))
            .padding(horizontal = 16.dp)
    ) {
        Spacer(modifier = Modifier.height(14.dp))

        // Search Bar with Swiftify styling
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(Color(0xFF221C1A))
                .padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("🔍", fontSize = 16.sp)
                Spacer(modifier = Modifier.width(10.dp))
                BasicTextField(
                    value = query,
                    onValueChange = {
                        query = it
                        doSearch(it)
                    },
                    textStyle = TextStyle(
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium
                    ),
                    cursorBrush = SolidColor(Color(0xFFF2A385)),
                    modifier = Modifier.weight(1f),
                    decorationBox = { innerTextField ->
                        if (query.isEmpty()) {
                            Text(
                                text = "Artists, songs, or playlists...",
                                color = Color(0xFF7A706A),
                                fontSize = 15.sp
                            )
                        }
                        innerTextField()
                    }
                )
                if (query.isNotEmpty()) {
                    Text(
                        text = "✕",
                        color = Color(0xFFA59B94),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clickable {
                                query = ""
                                doSearch("")
                            }
                            .padding(4.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Categories Chips
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(categories) { category ->
                val isSelected = (selectedCategory == category)
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(if (isSelected) Color(0xFFF2A385) else Color(0xFF221C1A))
                        .clickable { selectedCategory = category }
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = category,
                        color = if (isSelected) Color(0xFF13100E) else Color(0xFFCDC2BB),
                        fontSize = 13.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Body: Results or Browse Genres
        if (query.isEmpty()) {
            Text(
                text = "Browse all",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(12.dp))

            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 110.dp)
            ) {
                items(featuredGenres) { genre ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(96.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(
                                Brush.linearGradient(
                                    listOf(genre.startColor, genre.endColor)
                                )
                            )
                            .clickable {
                                query = genre.name
                                doSearch(genre.name)
                            }
                            .padding(12.dp)
                    ) {
                        Text(
                            text = genre.name,
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.align(Alignment.TopStart)
                        )
                        Text(
                            text = genre.symbol,
                            fontSize = 32.sp,
                            modifier = Modifier.align(Alignment.BottomEnd)
                        )
                    }
                }
            }
        } else {
            if (isSearching) {
                Box(modifier = Modifier.fillMaxWidth().padding(top = 32.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Color(0xFFF2A385), strokeWidth = 2.5.dp)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 110.dp)
                ) {
                    val trimmed = query.trim().lowercase()
                    val topArtist = searchResultsArtists.firstOrNull { it.name.lowercase().contains(trimmed) }
                    val isArtistTopResult = (topArtist != null && (topArtist.name.lowercase() == trimmed || topArtist.name.lowercase().startsWith(trimmed)))

                    // Top Result Card (if All is selected)
                    if (selectedCategory == "All") {
                        if (isArtistTopResult && topArtist != null) {
                            item {
                                Text("Top result", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 10.dp))
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(18.dp))
                                        .background(Color(0xFF221C1A))
                                        .clickable { onArtistSelect(topArtist) }
                                        .padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    AsyncImage(
                                        model = topArtist.artworkUrl,
                                        contentDescription = topArtist.name,
                                        modifier = Modifier.size(70.dp).clip(CircleShape),
                                        contentScale = ContentScale.Crop
                                    )
                                    Spacer(modifier = Modifier.width(16.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(topArtist.name, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                                        Text("ARTIST", color = Color(0xFFF2A385), fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp))
                                    }
                                }
                                Spacer(modifier = Modifier.height(14.dp))
                            }
                        } else if (searchResultsTracks.isNotEmpty()) {
                            val topSong = searchResultsTracks.first()
                            item {
                                Text("Top result", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 10.dp))
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(18.dp))
                                        .background(Color(0xFF221C1A))
                                        .clickable { onTrackSelect(topSong) }
                                        .padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    AsyncImage(
                                        model = topSong.artworkUrl,
                                        contentDescription = topSong.title,
                                        modifier = Modifier.size(70.dp).clip(RoundedCornerShape(12.dp)),
                                        contentScale = ContentScale.Crop
                                    )
                                    Spacer(modifier = Modifier.width(16.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(topSong.title, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.clickable { onAlbumSelect?.invoke(topSong) })
                                        Text(topSong.artist, color = Color(0xFFA59B94), fontSize = 14.sp, modifier = Modifier.padding(top = 2.dp))
                                        Text("SONG", color = Color(0xFFF2A385), fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp))
                                    }
                                }
                                Spacer(modifier = Modifier.height(14.dp))
                            }
                        }
                    }

                    // Songs Section
                    if (selectedCategory in listOf("All", "Songs") && searchResultsTracks.isNotEmpty()) {
                        item {
                            Text(
                                "Songs",
                                color = Color.White,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(vertical = 10.dp)
                            )
                        }
                        val tracksToShow = if (selectedCategory == "All") searchResultsTracks.take(5) else searchResultsTracks
                        items(tracksToShow) { track ->
                            val isCurrent = (livePlayingTrack != null && livePlayingTrack.uri == track.uri)
                            val context = LocalContext.current
                            var offsetX by remember { mutableStateOf(0f) }

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (isCurrent) Color(0x33F2A385) else Color.Transparent)
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
                                        .clickable { onTrackSelect(track) }
                                        .padding(vertical = 8.dp, horizontal = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(modifier = Modifier.size(46.dp)) {
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
                                                Text("🔊", fontSize = 12.sp)
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
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.clickable { onAlbumSelect?.invoke(track) }
                                        )
                                        Text(
                                            text = "${track.artist} • ${track.album}",
                                            color = Color(0xFF9E948E),
                                            fontSize = 13.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    Text(
                                        text = formatTime(track.durationMs),
                                        color = Color(0xFF7A706A),
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }
                    }

                    // Artists Section
                    if (selectedCategory in listOf("All", "Artists") && searchResultsArtists.isNotEmpty()) {
                        item {
                            Text(
                                "Artists",
                                color = Color.White,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(top = 16.dp, bottom = 10.dp)
                            )
                        }
                        items(searchResultsArtists) { artist ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { onArtistSelect(artist) }
                                    .padding(vertical = 8.dp, horizontal = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                AsyncImage(
                                    model = artist.artworkUrl,
                                    contentDescription = artist.name,
                                    modifier = Modifier
                                        .size(46.dp)
                                        .clip(CircleShape),
                                    contentScale = ContentScale.Crop
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = artist.name,
                                        color = Color.White,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = "Artist",
                                        color = Color(0xFF9E948E),
                                        fontSize = 13.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
