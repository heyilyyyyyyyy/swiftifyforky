package org.swiftify.app

import java.net.URL
import java.net.HttpURLConnection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import android.content.Context

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import org.json.JSONObject

@Composable
fun ExpandedPlayer(
    track: TrackItem,
    allTracks: List<TrackItem>,
    isPlaying: Boolean,
    progressMs: Long,
    onCollapse: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrev: () -> Unit,
    onSeek: (Long) -> Unit,
    onSelectTrack: (TrackItem) -> Unit,
    onArtistClick: ((name: String) -> Unit)? = null,
    onAlbumClick: (() -> Unit)? = null,
    isShuffle: Boolean = false,
    repeatMode: Int = 0, // 0 = off, 1 = repeat all, 2 = repeat one
    onToggleShuffle: () -> Unit = {},
    onToggleRepeat: () -> Unit = {}
) {
    val context = LocalContext.current
    val palette = rememberTrackPalette(context, track.artworkUrl)

    val prefs = remember { context.getSharedPreferences("swiftify_prefs", Context.MODE_PRIVATE) }
    val coroutineScope = rememberCoroutineScope()
    var isLiked by remember(track.uri) { 
        val cachedLikes = prefs.getStringSet("saved_track_uris", emptySet()) ?: emptySet()
        mutableStateOf(cachedLikes.contains(track.uri))
    }

    // Check live saved status on Spotify Web API
    LaunchedEffect(track.uri) {
        val token = prefs.getString("access_token", null)
        val trackId = track.uri.substringAfterLast(":")
        if (!token.isNullOrEmpty() && trackId.isNotEmpty() && track.uri.startsWith("spotify:track:")) {
            withContext(Dispatchers.IO) {
                try {
                    val conn = URL("https://api.spotify.com/v1/me/tracks/contains?ids=$trackId").openConnection() as HttpURLConnection
                    conn.setRequestProperty("Authorization", "Bearer $token")
                    if (conn.responseCode in 200..299) {
                        val text = conn.inputStream.bufferedReader().readText()
                        val arr = org.json.JSONArray(text)
                        if (arr.length() > 0) {
                            val saved = arr.getBoolean(0)
                            withContext(Dispatchers.Main) {
                                isLiked = saved
                                val currentSet = (prefs.getStringSet("saved_track_uris", emptySet()) ?: emptySet()).toMutableSet()
                                if (saved) currentSet.add(track.uri) else currentSet.remove(track.uri)
                                prefs.edit().putStringSet("saved_track_uris", currentSet).apply()
                            }
                        }
                    }
                    conn.disconnect()
                } catch (e: Exception) {
                    // ignore network errors
                }
            }
        }
    }
    var selectedTab by remember { mutableIntStateOf(0) } // 0: Artwork, 1: Lyrics, 2: Queue

    var lyricsText by remember { mutableStateOf("Loading lyrics...") }
    var lyricsLoadedForUri by remember { mutableStateOf("") }

    // Fetch lyrics when lyrics drawer is opened
    LaunchedEffect(selectedTab, track.uri) {
        if (selectedTab == 1 && lyricsLoadedForUri != track.uri) {
            lyricsText = "Loading lyrics from Spotify..."
            SpotifyBridge.fetchLyrics(track.uri) { ok, payload ->
                if (ok && payload.isNotEmpty()) {
                    try {
                        val obj = JSONObject(payload)
                        val linesArr = obj.optJSONArray("lines")
                        if (linesArr != null && linesArr.length() > 0) {
                            val sb = StringBuilder()
                            for (i in 0 until linesArr.length()) {
                                val lineObj = linesArr.getJSONObject(i)
                                sb.append(lineObj.optString("words", "")).append("\n\n")
                            }
                            lyricsText = sb.toString().trim()
                        } else {
                            lyricsText = obj.optString("syncType", "Instrumental or lyrics unavailable")
                        }
                    } catch (_: Exception) {
                        lyricsText = payload
                    }
                } else {
                    lyricsText = "Lyrics are unavailable for this track."
                }
                lyricsLoadedForUri = track.uri
            }
        }
    }

    // Dynamic gradient background inspired by Swiftify Desktop's Liquid Backdrop
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0C0908))
            .background(
                Brush.verticalGradient(
                    listOf(
                        palette.dominant.copy(alpha = 0.96f),
                        palette.secondary.copy(alpha = 0.98f),
                        Color(0xFF0C0908)
                    )
                )
            )
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(6.dp))

            // ─── Header: Collapse button, Source Context, Options ─────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onCollapse,
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(Color(0x2BFFFFFF))
                ) {
                    Icon(
                        imageVector = Icons.Rounded.KeyboardArrowDown,
                        contentDescription = "Collapse",
                        tint = palette.accent,
                        modifier = Modifier.size(28.dp)
                    )
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "PLAYING FROM",
                        color = Color(0x99FFFFFF),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.3.sp
                    )
                    Text(
                        text = track.album.ifEmpty { "Swiftify" },
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                IconButton(
                    onClick = { /* extra options */ },
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(Color(0x2BFFFFFF))
                ) {
                    Icon(
                        imageVector = Icons.Rounded.MoreVert,
                        contentDescription = "Options",
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // ─── Center: Artwork / Lyrics / Queue ────────────────────────────
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                when (selectedTab) {
                    1 -> {
                        // Lyrics View
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(26.dp))
                                .background(Color(0x3B000000))
                                .padding(20.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(rememberScrollState()),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "Synced Lyrics",
                                    color = palette.accent,
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = lyricsText,
                                    color = Color.White,
                                    fontSize = 18.sp,
                                    lineHeight = 28.sp,
                                    textAlign = TextAlign.Center,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                    2 -> {
                        // Queue View
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(26.dp))
                                .background(Color(0x3B000000))
                                .padding(18.dp)
                        ) {
                            Column(modifier = Modifier.fillMaxSize()) {
                                Text(
                                    text = "Up Next in Queue",
                                    color = palette.accent,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(bottom = 12.dp)
                                )
                                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    itemsIndexed(allTracks) { idx, qTrack ->
                                        val isCurrent = qTrack.uri == track.uri
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(14.dp))
                                                .background(if (isCurrent) palette.accent.copy(alpha = 0.25f) else Color.Transparent)
                                            .clickable {
                                                onSelectTrack(qTrack)
                                                selectedTab = 0
                                            }
                                            .padding(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        AsyncImage(
                                            model = qTrack.artworkUrl,
                                            contentDescription = null,
                                            modifier = Modifier
                                                .size(46.dp)
                                                .clip(RoundedCornerShape(10.dp)),
                                            contentScale = ContentScale.Crop
                                        )
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                qTrack.title,
                                                color = if (isCurrent) palette.accent else Color.White,
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                qTrack.artist,
                                                color = Color(0xAAFFFFFF),
                                                fontSize = 12.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                        if (isCurrent) {
                                            Icon(
                                                imageVector = Icons.Rounded.VolumeUp,
                                                contentDescription = null,
                                                tint = palette.accent,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                else -> {
                    // Album Artwork with Material 3 Expressive Glow & Squircle curvature
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.92f)
                            .aspectRatio(1f)
                            .shadow(
                                elevation = 28.dp,
                                shape = RoundedCornerShape(28.dp),
                                spotColor = palette.accent.copy(alpha = 0.6f),
                                ambientColor = palette.dominant
                            )
                            .clip(RoundedCornerShape(28.dp))
                            .background(Color(0xFF1E1715)),
                        contentAlignment = Alignment.Center
                    ) {
                        AsyncImage(
                            model = track.artworkUrl,
                            contentDescription = track.title,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // ─── Track Title, Artist & Like Heart ────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = track.title,
                    color = Color.White,
                    fontSize = 25.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.clickable { onAlbumClick?.invoke() }
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = track.artist,
                    color = Color(0xCCFFFFFF),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.clickable { onArtistClick?.invoke(track.artist) }
                )
            }

            IconButton(
                onClick = {
                    val newSaved = !isLiked
                    isLiked = newSaved
                    val currentSet = (prefs.getStringSet("saved_track_uris", emptySet()) ?: emptySet()).toMutableSet()
                    if (newSaved) currentSet.add(track.uri) else currentSet.remove(track.uri)
                    prefs.edit().putStringSet("saved_track_uris", currentSet).apply()

                    val token = prefs.getString("access_token", null)
                    val trackId = track.uri.substringAfterLast(":")
                    if (!token.isNullOrEmpty() && trackId.isNotEmpty() && track.uri.startsWith("spotify:track:")) {
                        coroutineScope.launch(Dispatchers.IO) {
                            try {
                                val urlStr = "https://api.spotify.com/v1/me/tracks?ids=$trackId"
                                val conn = URL(urlStr).openConnection() as HttpURLConnection
                                conn.requestMethod = if (newSaved) "PUT" else "DELETE"
                                conn.setRequestProperty("Authorization", "Bearer $token")
                                conn.responseCode
                                conn.disconnect()
                            } catch (e: Exception) {
                                // silent
                            }
                        }
                    }
                },
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(if (isLiked) palette.accent.copy(alpha = 0.25f) else Color(0x22FFFFFF))
            ) {
                Icon(
                    imageVector = if (isLiked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                    contentDescription = "Like",
                    tint = if (isLiked) palette.accent else Color.White,
                    modifier = Modifier.size(26.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // ─── Wavy Live Progress Bar ──────────────────────────────────────────
        WavyProgressSlider(
            progressMs = progressMs,
            totalMs = track.durationMs,
            isPlaying = isPlaying,
            onSeek = onSeek,
            activeColor = palette.accent,
            inactiveColor = Color(0x33FFFFFF),
            modifier = Modifier.fillMaxWidth()
        )

        // Progress Timestamps
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = formatTime(progressMs),
                color = Color(0x99FFFFFF),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = formatTime(track.durationMs),
                color = Color(0x99FFFFFF),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // ─── Main Playback Controls (Material 3 Expressive Shapes matching Desktop) ─
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Shuffle with clover4 shape backing
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clickable { onToggleShuffle() },
                contentAlignment = Alignment.Center
            ) {
                if (isShuffle) {
                    MaterialExpressiveShape(
                        shape = "clover4",
                        color = palette.accent.copy(alpha = 0.35f),
                        modifier = Modifier.size(48.dp)
                    )
                }
                Icon(
                    imageVector = Icons.Rounded.Shuffle,
                    contentDescription = "Shuffle",
                    tint = if (isShuffle) palette.accent else Color(0x99FFFFFF),
                    modifier = Modifier.size(24.dp)
                )
            }

            // Prev with rounded arrowLeft shape backing on click
            var isPrevPressed by remember { mutableStateOf(false) }
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onPress = {
                                isPrevPressed = true
                                tryAwaitRelease()
                                isPrevPressed = false
                                onPrev()
                            }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                if (isPrevPressed) {
                    MaterialExpressiveShape(
                        shape = "arrowLeft",
                        color = Color(0x33FFFFFF),
                        modifier = Modifier.size(52.dp)
                    )
                }
                Icon(
                    imageVector = Icons.Rounded.SkipPrevious,
                    contentDescription = "Previous",
                    tint = Color.White,
                    modifier = Modifier.size(36.dp)
                )
            }

            // Big Play / Pause with dynamic softCookie (paused) and cookie9 (playing) turning
            var isPlayPressed by remember { mutableStateOf(false) }
            val playScale by animateFloatAsState(
                targetValue = if (isPlayPressed) 0.90f else 1.0f,
                animationSpec = spring(dampingRatio = 0.6f, stiffness = 400f),
                label = "playScale"
            )

            val spinTransition = rememberInfiniteTransition(label = "spinTransition")
            val spinAngle by spinTransition.animateFloat(
                initialValue = 0f,
                targetValue = if (isPlaying) (2f * Math.PI.toFloat()) else 0f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 14000, easing = androidx.compose.animation.core.LinearEasing),
                    repeatMode = androidx.compose.animation.core.RepeatMode.Restart
                ),
                label = "spinAngle"
            )

            MaterialExpressiveShape(
                shape = if (isPlaying) "cookie9" else "softCookie",
                color = palette.accent,
                spinAngleRad = if (isPlaying) spinAngle else 0f,
                modifier = Modifier
                    .size(78.dp)
                    .scale(playScale)
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onPress = {
                                isPlayPressed = true
                                tryAwaitRelease()
                                isPlayPressed = false
                                onPlayPause()
                            }
                        )
                    }
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    tint = palette.onAccent,
                    modifier = Modifier.size(40.dp)
                )
            }

            // Next with rounded arrowRight shape backing on click
            var isNextPressed by remember { mutableStateOf(false) }
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onPress = {
                                isNextPressed = true
                                tryAwaitRelease()
                                isNextPressed = false
                                onNext()
                            }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                if (isNextPressed) {
                    MaterialExpressiveShape(
                        shape = "arrowRight",
                        color = Color(0x33FFFFFF),
                        modifier = Modifier.size(52.dp)
                    )
                }
                Icon(
                    imageVector = Icons.Rounded.SkipNext,
                    contentDescription = "Next",
                    tint = Color.White,
                    modifier = Modifier.size(36.dp)
                )
            }

            // Repeat with cookie6 / cookie4 shape backing
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clickable { onToggleRepeat() },
                contentAlignment = Alignment.Center
            ) {
                if (repeatMode > 0) {
                    MaterialExpressiveShape(
                        shape = if (repeatMode == 2) "cookie4" else "cookie6",
                        color = palette.accent.copy(alpha = 0.35f),
                        modifier = Modifier.size(48.dp)
                    )
                }
                Icon(
                    imageVector = if (repeatMode == 2) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                    contentDescription = "Repeat",
                    tint = if (repeatMode > 0) palette.accent else Color(0x99FFFFFF),
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // ─── Mode Selector Pill: Cover / Lyrics / Queue ──────────────────────
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(30.dp))
                .background(Color(0x22FFFFFF))
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            ModeTabChip(
                title = "Cover",
                icon = Icons.Rounded.Album,
                isSelected = selectedTab == 0,
                accentColor = palette.accent,
                onClick = { selectedTab = 0 }
            )
            ModeTabChip(
                title = "Lyrics",
                icon = Icons.Rounded.Mic,
                isSelected = selectedTab == 1,
                accentColor = palette.accent,
                onClick = { selectedTab = 1 }
            )
            ModeTabChip(
                title = "Queue",
                icon = Icons.Rounded.QueueMusic,
                isSelected = selectedTab == 2,
                accentColor = palette.accent,
                onClick = { selectedTab = 2 }
            )
        }

        Spacer(modifier = Modifier.height(10.dp))
    }
}
}

@Composable
private fun ModeTabChip(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isSelected: Boolean,
    accentColor: Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(24.dp))
            .background(if (isSelected) accentColor else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = title,
            tint = if (isSelected) Color(0xFF140E0C) else Color(0xDDFFFFFF),
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = title,
            color = if (isSelected) Color(0xFF140E0C) else Color(0xDDFFFFFF),
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold
        )
    }
}
