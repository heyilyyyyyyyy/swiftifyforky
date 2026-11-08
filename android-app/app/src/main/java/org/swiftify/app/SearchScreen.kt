package org.swiftify.app

import android.widget.Toast
import androidx.compose.animation.core.animate
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
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

data class SearchHistoryEntry(
    val type: String, // "track", "artist", "playlist"
    val title: String,
    val subtitle: String,
    val artworkUrl: String,
    val uri: String,
    val album: String = "",
    val durationMs: Long = 0L,
    val albumUri: String = "",
    val artistUri: String = ""
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
    val prefs = remember { currentContext.getSharedPreferences("swiftify_prefs", android.content.Context.MODE_PRIVATE) }
    val searchHistory = remember { mutableStateListOf<SearchHistoryEntry>() }

    fun loadHistory() {
        val raw = prefs.getString("search_history_items", null) ?: return
        try {
            val arr = org.json.JSONArray(raw)
            val list = mutableListOf<SearchHistoryEntry>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(
                    SearchHistoryEntry(
                        type = obj.optString("type", "track"),
                        title = obj.optString("title"),
                        subtitle = obj.optString("subtitle"),
                        artworkUrl = obj.optString("artworkUrl"),
                        uri = obj.optString("uri"),
                        album = obj.optString("album"),
                        durationMs = obj.optLong("durationMs"),
                        albumUri = obj.optString("albumUri"),
                        artistUri = obj.optString("artistUri")
                    )
                )
            }
            searchHistory.clear()
            searchHistory.addAll(list)
        } catch (_: Exception) {}
    }

    LaunchedEffect(Unit) {
        loadHistory()
    }

    fun persistHistory() {
        val arr = org.json.JSONArray()
        searchHistory.take(30).forEach { item ->
            val obj = org.json.JSONObject()
            obj.put("type", item.type)
            obj.put("title", item.title)
            obj.put("subtitle", item.subtitle)
            obj.put("artworkUrl", item.artworkUrl)
            obj.put("uri", item.uri)
            obj.put("album", item.album)
            obj.put("durationMs", item.durationMs)
            obj.put("albumUri", item.albumUri)
            obj.put("artistUri", item.artistUri)
            arr.put(obj)
        }
        prefs.edit().putString("search_history_items", arr.toString()).apply()
    }

    fun addToHistory(entry: SearchHistoryEntry) {
        searchHistory.removeAll { it.uri == entry.uri || (it.title == entry.title && it.type == entry.type) }
        searchHistory.add(0, entry)
        if (searchHistory.size > 30) {
            searchHistory.removeRange(30, searchHistory.size)
        }
        persistHistory()
    }

    fun removeFromHistory(entry: SearchHistoryEntry) {
        searchHistory.remove(entry)
        persistHistory()
    }

    fun clearHistory() {
        searchHistory.clear()
        prefs.edit().remove("search_history_items").apply()
    }

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
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp)
    ) {
        Spacer(modifier = Modifier.height(14.dp))

        // Search Bar with Swiftify styling (Height 48.dp, radius 17.dp, onSurfaceVariant icon, primary cursor)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .clip(RoundedCornerShape(17.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .padding(horizontal = 14.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Rounded.Search,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
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
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    modifier = Modifier.weight(1f),
                    decorationBox = { innerTextField ->
                        if (query.isEmpty()) {
                            Text(
                                text = "Artists, songs, or playlists...",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 16.sp
                            )
                        }
                        innerTextField()
                    }
                )
                if (query.isNotEmpty()) {
                    Text(
                        text = "✕",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                        .clip(CircleShape)
                        .background(if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh)
                        .clickable { selectedCategory = category }
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = category,
                        color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Body: Results or Search History
        if (query.isEmpty()) {
            val filteredHistory = remember(searchHistory.size, selectedCategory, searchHistory.toList()) {
                when (selectedCategory) {
                    "Songs" -> searchHistory.filter { it.type == "track" }
                    "Artists" -> searchHistory.filter { it.type == "artist" }
                    "Playlists" -> searchHistory.filter { it.type == "playlist" }
                    else -> searchHistory.toList()
                }
            }

            if (filteredHistory.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.search_recent_title),
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    TextButton(
                        onClick = { clearHistory() },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.search_recent_clear),
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 110.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(
                        items = filteredHistory,
                        key = { "${it.type}_${it.uri}_${it.title}" }
                    ) { item ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    addToHistory(item)
                                    when (item.type) {
                                        "track" -> onTrackSelect(
                                            TrackItem(
                                                title = item.title,
                                                artist = item.subtitle.substringBefore(" •"),
                                                album = item.album,
                                                artworkUrl = item.artworkUrl,
                                                durationMs = item.durationMs,
                                                uri = item.uri,
                                                albumUri = item.albumUri,
                                                artistUri = item.artistUri
                                            )
                                        )
                                        "artist" -> onArtistSelect(
                                            ArtistItem(
                                                name = item.title,
                                                artworkUrl = item.artworkUrl,
                                                uri = item.uri
                                            )
                                        )
                                        "playlist" -> onPlaylistSelect(
                                            PlaylistItem(
                                                name = item.title,
                                                owner = item.subtitle.substringAfter("Playlist • "),
                                                artworkUrl = item.artworkUrl,
                                                uri = item.uri
                                            )
                                        )
                                    }
                                }
                                .padding(vertical = 6.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (item.type == "artist") {
                                AsyncImage(
                                    model = item.artworkUrl,
                                    contentDescription = item.title,
                                    modifier = Modifier.size(46.dp).clip(CircleShape),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                AsyncImage(
                                    model = item.artworkUrl,
                                    contentDescription = item.title,
                                    modifier = Modifier.size(46.dp).clip(RoundedCornerShape(8.dp)),
                                    contentScale = ContentScale.Crop
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = item.title,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = item.subtitle,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 13.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            IconButton(
                                onClick = { removeFromHistory(item) },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Close,
                                    contentDescription = "Remove",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(horizontal = 32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.History,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
                            modifier = Modifier.size(56.dp)
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = stringResource(R.string.search_empty_history_title),
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.search_empty_history_desc),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        } else {
            if (isSearching) {
                Box(modifier = Modifier.fillMaxWidth().padding(top = 32.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary, strokeWidth = 2.5.dp)
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
                            item(key = "top_result_artist_${topArtist.uri}", contentType = "top_result") {
                                Text(stringResource(R.string.search_top_result), color = MaterialTheme.colorScheme.onSurface, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 10.dp))
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(17.dp))
                                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                                        .clickable {
                                            addToHistory(
                                                SearchHistoryEntry(
                                                    type = "artist",
                                                    title = topArtist.name,
                                                    subtitle = "Artist",
                                                    artworkUrl = topArtist.artworkUrl,
                                                    uri = topArtist.uri
                                                )
                                            )
                                            onArtistSelect(topArtist)
                                        }
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
                                        Text(topArtist.name, color = MaterialTheme.colorScheme.onSurface, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Box(
                                            modifier = Modifier
                                                .clip(CircleShape)
                                                .background(MaterialTheme.colorScheme.primaryContainer)
                                                .padding(horizontal = 8.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                "ARTIST",
                                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                letterSpacing = 1.sp
                                            )
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.height(14.dp))
                            }
                        } else if (searchResultsTracks.isNotEmpty()) {
                            val topSong = searchResultsTracks.first()
                            item(key = "top_result_song_${topSong.uri}", contentType = "top_result") {
                                Text(stringResource(R.string.search_top_result), color = MaterialTheme.colorScheme.onSurface, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 10.dp))
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(17.dp))
                                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                                        .clickable {
                                            addToHistory(
                                                SearchHistoryEntry(
                                                    type = "track",
                                                    title = topSong.title,
                                                    subtitle = "${topSong.artist} • Song",
                                                    artworkUrl = topSong.artworkUrl,
                                                    uri = topSong.uri,
                                                    album = topSong.album,
                                                    durationMs = topSong.durationMs,
                                                    albumUri = topSong.albumUri,
                                                    artistUri = topSong.artistUri
                                                )
                                            )
                                            onTrackSelect(topSong)
                                        }
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
                                        Text(topSong.title, color = MaterialTheme.colorScheme.onSurface, fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(topSong.artist, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp, modifier = Modifier.padding(top = 2.dp))
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Box(
                                            modifier = Modifier
                                                .clip(CircleShape)
                                                .background(MaterialTheme.colorScheme.primaryContainer)
                                                .padding(horizontal = 8.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                "SONG",
                                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                letterSpacing = 1.sp
                                            )
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.height(14.dp))
                            }
                        }
                    }

                    // Songs Section
                    if (selectedCategory in listOf("All", "Songs") && searchResultsTracks.isNotEmpty()) {
                        item(key = "songs_header", contentType = "header") {
                            Text(
                                "Songs",
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(vertical = 10.dp)
                            )
                        }
                        val tracksToShow = if (selectedCategory == "All") searchResultsTracks.take(5) else searchResultsTracks
                        items(
                            items = tracksToShow,
                            key = { it.uri.ifEmpty { "${it.title}\u0000${it.artist}" } },
                            contentType = { "song_row" }
                        ) { track ->
                            val isCurrent = (livePlayingTrack != null && livePlayingTrack.uri == track.uri)
                            var dragDistance by remember(track.uri) { mutableFloatStateOf(0f) }
                            var offsetX by remember(track.uri) { mutableFloatStateOf(0f) }
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
                                        .semantics(mergeDescendants = true) {}
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
                                                addToHistory(
                                                    SearchHistoryEntry(
                                                        type = "track",
                                                        title = track.title,
                                                        subtitle = "${track.artist} • Song",
                                                        artworkUrl = track.artworkUrl,
                                                        uri = track.uri,
                                                        album = track.album,
                                                        durationMs = track.durationMs,
                                                        albumUri = track.albumUri,
                                                        artistUri = track.artistUri
                                                    )
                                                )
                                                onTrackSelect(track)
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
                                                    Toast.makeText(currentContext, currentContext.getString(R.string.toast_added_to_queue, track.title), Toast.LENGTH_SHORT).show()
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
                                        .padding(vertical = 8.dp, horizontal = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(modifier = Modifier.size(44.dp)) {
                                        AsyncImage(
                                            model = track.artworkUrl,
                                            contentDescription = null,
                                            modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(7.dp)),
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
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
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
                                            text = "${track.artist} • ${track.album}",
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontSize = 13.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    Text(
                                        text = formatTime(track.durationMs),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 12.sp,
                                        textAlign = androidx.compose.ui.text.style.TextAlign.End,
                                        modifier = Modifier.width(44.dp)
                                    )
                                }
                            }
                        }
                    }

                    // Artists Section
                    if (selectedCategory in listOf("All", "Artists") && searchResultsArtists.isNotEmpty()) {
                        item(key = "artists_header", contentType = "header") {
                            Text(
                                "Artists",
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(top = 16.dp, bottom = 10.dp)
                            )
                        }
                        items(
                            items = searchResultsArtists,
                            key = { it.uri.ifEmpty { it.name } },
                            contentType = { "artist_row" }
                        ) { artist ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable {
                                        addToHistory(
                                            SearchHistoryEntry(
                                                type = "artist",
                                                title = artist.name,
                                                subtitle = "Artist",
                                                artworkUrl = artist.artworkUrl,
                                                uri = artist.uri
                                            )
                                        )
                                        onArtistSelect(artist)
                                    }
                                    .padding(vertical = 8.dp, horizontal = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                AsyncImage(
                                    model = artist.artworkUrl,
                                    contentDescription = artist.name,
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(CircleShape),
                                    contentScale = ContentScale.Crop
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = artist.name,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = "Artist",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 13.sp
                                    )
                                }
                            }
                        }
                    }

                    // Playlists Section
                    if (selectedCategory in listOf("All", "Playlists") && searchResultsPlaylists.isNotEmpty()) {
                        item(key = "playlists_header", contentType = "header") {
                            Text(
                                "Playlists",
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(top = 16.dp, bottom = 10.dp)
                            )
                        }
                        items(
                            items = searchResultsPlaylists,
                            key = { it.uri.ifEmpty { it.name } },
                            contentType = { "playlist_row" }
                        ) { playlist ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable {
                                        addToHistory(
                                            SearchHistoryEntry(
                                                type = "playlist",
                                                title = playlist.name,
                                                subtitle = "Playlist • ${playlist.owner}",
                                                artworkUrl = playlist.artworkUrl,
                                                uri = playlist.uri
                                            )
                                        )
                                        onPlaylistSelect(playlist)
                                    }
                                    .padding(vertical = 8.dp, horizontal = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                AsyncImage(
                                    model = playlist.artworkUrl,
                                    contentDescription = playlist.name,
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(RoundedCornerShape(7.dp)),
                                    contentScale = ContentScale.Crop
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = playlist.name,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "Playlist • ${playlist.owner}",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                        item(key = "empty_state", contentType = "status") {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 60.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = "No results found for \"$query\"",
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = "Please check spelling or try another search",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
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
