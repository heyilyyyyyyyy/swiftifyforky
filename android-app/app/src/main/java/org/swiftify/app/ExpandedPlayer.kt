package org.swiftify.app

import java.net.URL
import java.net.HttpURLConnection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import android.content.Context
import android.net.Uri

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
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

data class LyricLine(
    val startTimeMs: Long,
    val endTimeMs: Long,
    val words: String
)

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
    onToggleRepeat: () -> Unit = {},
    customQueue: List<TrackItem> = emptyList(),
    onRemoveFromCustomQueue: ((Int) -> Unit)? = null,
    onClearCustomQueue: (() -> Unit)? = null,
    queueContextName: String = "Swiftify"
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
        if (track.uri.startsWith("spotify:track:")) {
            val saved = SpotifyApi.isTrackSaved(context, track.uri)
            isLiked = saved
        }
    }
    var selectedTab by remember { mutableIntStateOf(0) } // 0: Artwork, 1: Lyrics, 2: Queue

    var lyricLines by remember { mutableStateOf<List<LyricLine>>(emptyList()) }
    var isLineSynced by remember { mutableStateOf(false) }
    var lyricsLoadedForUri by remember { mutableStateOf("") }
    var lyricsErrorMsg by remember { mutableStateOf<String?>(null) }
    var isLoadingLyrics by remember { mutableStateOf(false) }

    // Fetch lyrics when lyrics tab is opened
    LaunchedEffect(selectedTab, track.uri) {
        if (selectedTab == 1 && lyricsLoadedForUri != track.uri) {
            isLoadingLyrics = true
            lyricsErrorMsg = null
            SpotifyBridge.fetchLyrics(track.uri) { ok, payload ->
                isLoadingLyrics = false
                if (ok && payload.isNotEmpty()) {
                    try {
                        val obj = JSONObject(payload)
                        val syncTypeStr = obj.optString("syncType", "unsynced")
                        val linesArr = obj.optJSONArray("lines")
                        val parsed = mutableListOf<LyricLine>()
                        if (linesArr != null) {
                            for (i in 0 until linesArr.length()) {
                                val lineObj = linesArr.getJSONObject(i)
                                val s = lineObj.optLong("startTimeMs", 0L)
                                val e = lineObj.optLong("endTimeMs", 0L)
                                val w = lineObj.optString("words", "").trim()
                                if (w.isNotEmpty()) {
                                    parsed.add(LyricLine(s, e, w))
                                }
                            }
                        }
                        lyricLines = parsed
                        isLineSynced = (syncTypeStr == "lineSynced") && parsed.isNotEmpty()
                        if (parsed.isEmpty()) {
                            lyricsErrorMsg = obj.optString("syncType", "Instrumental or lyrics unavailable")
                        }
                    } catch (e: Exception) {
                        lyricsErrorMsg = payload
                        isLineSynced = false
                    }
                } else {
                    lyricsErrorMsg = "Lyrics are unavailable for this track."
                    isLineSynced = false
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
                        // Lyrics View (Synced & Plain with auto-scroll and seek)
                        if (isLoadingLyrics) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(RoundedCornerShape(26.dp))
                                    .background(Color(0x3B000000)),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(color = palette.accent, strokeWidth = 3.dp)
                            }
                        } else if (lyricLines.isNotEmpty()) {
                            val activeLineIdx = remember(progressMs, lyricLines) {
                                var idx = -1
                                for (i in lyricLines.indices) {
                                    if (progressMs >= lyricLines[i].startTimeMs) {
                                        idx = i
                                    } else {
                                        break
                                    }
                                }
                                idx
                            }
                            val lyricsListState = rememberLazyListState()

                            LaunchedEffect(activeLineIdx) {
                                if (activeLineIdx >= 0 && isLineSynced) {
                                    lyricsListState.animateScrollToItem((activeLineIdx - 1).coerceAtLeast(0))
                                }
                            }

                            LazyColumn(
                                state = lyricsListState,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(RoundedCornerShape(26.dp))
                                    .background(Color(0x3B000000))
                                    .padding(horizontal = 20.dp),
                                contentPadding = PaddingValues(top = 28.dp, bottom = 48.dp),
                                verticalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                item {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = if (isLineSynced) "SYNCED LYRICS" else "LYRICS",
                                            color = palette.accent,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            letterSpacing = 1.2.sp
                                        )
                                        if (isLineSynced) {
                                            Text(
                                                text = "Tap line to seek",
                                                color = Color(0x88FFFFFF),
                                                fontSize = 11.sp
                                            )
                                        }
                                    }
                                }
                                itemsIndexed(lyricLines) { index, line ->
                                    val isCurrent = isLineSynced && (index == activeLineIdx)
                                    val isPast = isLineSynced && (index < activeLineIdx)

                                    val fontSize = if (isCurrent) 22.sp else 17.sp
                                    val textColor = when {
                                        !isLineSynced -> Color.White
                                        isCurrent -> Color.White
                                        isPast -> Color(0x66FFFFFF)
                                        else -> Color(0x88FFFFFF)
                                    }
                                    val fontWeight = if (isCurrent) FontWeight.ExtraBold else FontWeight.SemiBold

                                    Text(
                                        text = line.words,
                                        color = textColor,
                                        fontSize = fontSize,
                                        fontWeight = fontWeight,
                                        lineHeight = if (isCurrent) 32.sp else 26.sp,
                                        textAlign = TextAlign.Start,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(12.dp))
                                            .clickable {
                                                if (line.startTimeMs > 0L) {
                                                    onSeek(line.startTimeMs)
                                                }
                                            }
                                            .padding(vertical = 4.dp, horizontal = 6.dp)
                                    )
                                }
                            }
                        } else {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(RoundedCornerShape(26.dp))
                                    .background(Color(0x3B000000))
                                    .padding(24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = lyricsErrorMsg ?: "No lyrics available for this song.",
                                    color = Color(0xCCFFFFFF),
                                    fontSize = 16.sp,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                    2 -> {
                        // Queue View (Now Playing, Play Next / Custom Queue, and Upcoming Tracks)
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(26.dp))
                                .background(Color(0x3B000000))
                                .padding(16.dp)
                        ) {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // Now Playing Header
                                item {
                                    Text(
                                        text = "NOW PLAYING",
                                        color = palette.accent,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 1.2.sp,
                                        modifier = Modifier.padding(bottom = 4.dp)
                                    )
                                }

                                // Now Playing Card
                                item {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(14.dp))
                                            .background(palette.accent.copy(alpha = 0.22f))
                                            .padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        AsyncImage(
                                            model = track.artworkUrl,
                                            contentDescription = null,
                                            modifier = Modifier
                                                .size(46.dp)
                                                .clip(RoundedCornerShape(10.dp)),
                                            contentScale = ContentScale.Crop
                                        )
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                track.title,
                                                color = palette.accent,
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                track.artist,
                                                color = Color.White,
                                                fontSize = 12.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                        Icon(
                                            imageVector = Icons.Rounded.VolumeUp,
                                            contentDescription = null,
                                            tint = palette.accent,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }

                                // Custom Queue Section ("Play Next")
                                if (customQueue.isNotEmpty()) {
                                    item {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(top = 12.dp, bottom = 4.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "NEXT IN QUEUE (${customQueue.size})",
                                                color = Color.White,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                letterSpacing = 1.1.sp
                                            )
                                            if (onClearCustomQueue != null) {
                                                Text(
                                                    text = "Clear",
                                                    color = palette.accent,
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(6.dp))
                                                        .clickable { onClearCustomQueue() }
                                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                    }

                                    itemsIndexed(customQueue) { idx, qTrack ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(Color(0x22FFFFFF))
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
                                                    .size(40.dp)
                                                    .clip(RoundedCornerShape(8.dp)),
                                                contentScale = ContentScale.Crop
                                            )
                                            Spacer(modifier = Modifier.width(10.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    qTrack.title,
                                                    color = Color.White,
                                                    fontSize = 13.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Text(
                                                    qTrack.artist,
                                                    color = Color(0xAAFFFFFF),
                                                    fontSize = 11.sp,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                            IconButton(
                                                onClick = { onRemoveFromCustomQueue?.invoke(idx) },
                                                modifier = Modifier.size(28.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Rounded.Close,
                                                    contentDescription = "Remove",
                                                    tint = Color(0xAAFFFFFF),
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }
                                    }
                                }

                                // Upcoming from Context
                                val upcomingFromContext = allTracks.filter { it.uri != track.uri }
                                if (upcomingFromContext.isNotEmpty()) {
                                    item {
                                        Text(
                                            text = "NEXT FROM: ${queueContextName.uppercase()}",
                                            color = Color.White,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            letterSpacing = 1.1.sp,
                                            modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
                                        )
                                    }

                                    itemsIndexed(upcomingFromContext) { _, qTrack ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(12.dp))
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
                                                    .size(40.dp)
                                                    .clip(RoundedCornerShape(8.dp)),
                                                contentScale = ContentScale.Crop
                                            )
                                            Spacer(modifier = Modifier.width(10.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    qTrack.title,
                                                    color = Color.White,
                                                    fontSize = 13.sp,
                                                    fontWeight = FontWeight.Medium,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Text(
                                                    qTrack.artist,
                                                    color = Color(0x99FFFFFF),
                                                    fontSize = 11.sp,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                            Text(
                                                text = formatTime(qTrack.durationMs),
                                                color = Color(0x88FFFFFF),
                                                fontSize = 12.sp
                                            )
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
                    coroutineScope.launch {
                        SpotifyApi.setTrackSaved(context, track.uri, newSaved)
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
