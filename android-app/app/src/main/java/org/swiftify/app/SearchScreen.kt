package org.swiftify.app

import android.widget.Toast
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.ui.draw.shadow
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

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.ui.graphics.vector.ImageVector

data class GenreItem(
    val name: String,
    val icon: ImageVector,
    val startColor: Color,
    val endColor: Color
)

val featuredGenres = listOf(
    GenreItem("Pop", Icons.Rounded.MusicNote, Color(0xFFD6336C), Color(0xFFF76707)),
    GenreItem("Hip-Hop", Icons.Rounded.Headphones, Color(0xFF5C5FDC), Color(0xFFA259FF)),
    GenreItem("Electronic", Icons.Rounded.Bolt, Color(0xFF20C997), Color(0xFF0CA678)),
    GenreItem("Alternative", Icons.Rounded.GraphicEq, Color(0xFFA259FF), Color(0xFFD6336C)),
    GenreItem("R&B", Icons.Rounded.Mic, Color(0xFFE03131), Color(0xFFF76707)),
    GenreItem("Indie", Icons.Rounded.Star, Color(0xFF15AABF), Color(0xFF1C7ED6)),
    GenreItem("Dance", Icons.Rounded.LocalFireDepartment, Color(0xFF1C7ED6), Color(0xFF5C5FDC)),
    GenreItem("Chill", Icons.Rounded.Nightlight, Color(0xFF15AABF), Color(0xFF1C7ED6))
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
    val currentContext = LocalContext.current

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

            // Fetch playlists via Spotify Web API
            launch {
                try {
                    val pList = SpotifyApi.searchPlaylists(currentContext, trimmed)
                    if (query.trim() == trimmed) {
                        searchResultsPlaylists.clear()
                        searchResultsPlaylists.addAll(pList)
                    }
                } catch (_: Exception) {}
            }

            // Fetch tracks
            SpotifyBridge.searchTracks(trimmed) { okTracks, jsonTracks ->
                if (query.trim() == trimmed && okTracks && jsonTracks.isNotEmpty()) {
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
                                val albumUri = albumObj?.optString("uri") ?: ""
                                val images = albumObj?.optJSONArray("images")
                                val artUrl = getBestArtworkUrl(images)
                                val artistsArr = item.optJSONArray("artists")
                                val artistUri = if (artistsArr != null && artistsArr.length() > 0) artistsArr.getJSONObject(0).optString("uri") else ""
                                val artistName = if (artistsArr != null && artistsArr.length() > 0) {
                                    (0 until artistsArr.length()).joinToString(", ") { j ->
                                        artistsArr.getJSONObject(j).optString("name")
                                    }
                                } else "Unknown"
                                list.add(TrackItem(name, artistName, albumName, artUrl, duration, uri, albumUri, artistUri))
                            }
                            searchResultsTracks.clear()
                            searchResultsTracks.addAll(list)
                        }
                    } catch (_: Exception) {}
                }

                // Fetch artists
                SpotifyBridge.searchArtists(trimmed) { okArtists, jsonArtists ->
                    if (query.trim() == trimmed && okArtists && jsonArtists.isNotEmpty()) {
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
                Icon(
                    imageVector = Icons.Rounded.Search,
                    contentDescription = null,
                    tint = Color(0xFFF2A385),
                    modifier = Modifier.size(20.dp)
                )
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
                        Icon(
                            imageVector = genre.icon,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.85f),
                            modifier = Modifier
                                .size(32.dp)
                                .align(Alignment.BottomEnd)
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
                                        Text(topSong.title, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
                            val offsetX = remember { Animatable(0f) }

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(Color(0xFF231C1A))
                            ) {
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

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .offset { IntOffset(offsetX.value.roundToInt(), 0) }
                                        .shadow(if (offsetX.value > 5f) 8.dp else 0.dp, RoundedCornerShape(14.dp))
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(if (isCurrent) Color(0xFF281E1B) else Color(0xFF13100E))
                                        .clickable {
                                            if (offsetX.value == 0f) {
                                                onTrackSelect(track)
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

                    // Playlists Section
                    if (selectedCategory in listOf("All", "Playlists") && searchResultsPlaylists.isNotEmpty()) {
                        item {
                            Text(
                                "Playlists",
                                color = Color.White,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(top = 16.dp, bottom = 10.dp)
                            )
                        }
                        items(searchResultsPlaylists) { playlist ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { onPlaylistSelect(playlist) }
                                    .padding(vertical = 8.dp, horizontal = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                AsyncImage(
                                    model = playlist.artworkUrl,
                                    contentDescription = playlist.name,
                                    modifier = Modifier
                                        .size(46.dp)
                                        .clip(RoundedCornerShape(8.dp)),
                                    contentScale = ContentScale.Crop
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = playlist.name,
                                        color = Color.White,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "Playlist • ${playlist.owner}",
                                        color = Color(0xFF9E948E),
                                        fontSize = 13.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }

                    // Empty State
                    if (searchResultsTracks.isEmpty() && searchResultsArtists.isEmpty() && searchResultsPlaylists.isEmpty() && !isSearching) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 60.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = "No results found for \"$query\"",
                                        color = Color.White,
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = "Please check spelling or try another search",
                                        color = Color(0xFFA59B94),
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
