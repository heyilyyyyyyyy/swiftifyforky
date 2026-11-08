package org.swiftify.app

import java.net.URL
import java.net.HttpURLConnection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import android.content.Context
import android.net.Uri
import android.content.ClipboardManager
import android.content.ClipData
import android.widget.Toast
import androidx.compose.ui.res.stringResource

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import org.swiftify.app.design.GoogleSansFlexFamily
import org.swiftify.app.design.SwiftifyMotion
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import org.json.JSONObject
import kotlin.math.abs

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
    onArtistClick: ((name: String, uri: String) -> Unit)? = null,
    onAlbumClick: (() -> Unit)? = null,
    isShuffle: Boolean = false,
    repeatMode: Int = 0, // 0 = off, 1 = repeat all, 2 = repeat one
    onToggleShuffle: () -> Unit = {},
    onToggleRepeat: () -> Unit = {},
    customQueue: List<TrackItem> = emptyList(),
    onRemoveFromCustomQueue: ((Int) -> Unit)? = null,
    onClearCustomQueue: (() -> Unit)? = null,
    queueContextName: String = "Swiftify",
    sleepTimerMinutes: Int = 0,
    sleepTimerRemainingSeconds: Int = 0,
    onSetSleepTimer: ((Int) -> Unit)? = null,
    userPlaylists: List<PlaylistItem> = emptyList(),
    onAddTrackToPlaylist: ((PlaylistItem, TrackItem) -> Unit)? = null
) {
    val context = LocalContext.current
    val palette = rememberTrackPalette(context, track.artworkUrl)

    var showOptionsMenu by remember { mutableStateOf(false) }
    var showSleepTimerDialog by remember { mutableStateOf(false) }
    var showAudioInfoDialog by remember { mutableStateOf(false) }
    var showAddToPlaylistDialog by remember { mutableStateOf(false) }

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

    // Fetch lyrics when lyrics tab is opened or track changes
    LaunchedEffect(selectedTab, track.uri) {
        if (lyricsLoadedForUri != track.uri) {
            lyricLines = emptyList()
            isLineSynced = false
            lyricsErrorMsg = null
        }
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
                        lyricsErrorMsg = "Lyrics are unavailable for this track."
                        isLineSynced = false
                        lyricLines = emptyList()
                    }
                } else {
                    lyricsErrorMsg = "Lyrics are unavailable for this track."
                    isLineSynced = false
                    lyricLines = emptyList()
                }
                lyricsLoadedForUri = track.uri
            }
        }
    }

    // Dynamic gradient background inspired by Swiftify Desktop's Liquid Backdrop
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF000000))
            .background(
                Brush.verticalGradient(
                    listOf(
                        palette.dominant.copy(alpha = 0.96f),
                        palette.secondary.copy(alpha = 0.98f),
                        Color(0xFF000000)
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
                // Collapse button: Arrow Down with arrowDown Material Expressive shape backing & spring scale
                val collapseInteraction = remember { MutableInteractionSource() }
                val isCollapsePressed by collapseInteraction.collectIsPressedAsState()
                val collapseScale by animateFloatAsState(
                    targetValue = if (isCollapsePressed) 0.85f else 1.0f,
                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
                    label = "collapseScale"
                )
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .scale(collapseScale)
                        .clickable(
                            interactionSource = collapseInteraction,
                            indication = null,
                            role = Role.Button,
                            onClick = onCollapse
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    MaterialExpressiveShape(
                        shape = "arrowDown",
                        color = Color(0x2BFFFFFF),
                        modifier = Modifier.fillMaxSize()
                    )
                    MaterialSymbol(
                        symbol = MaterialSymbols.KeyboardArrowDown,
                        contentDescription = "Collapse",
                        tint = Color.White,
                        size = 24.dp
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
                    if (sleepTimerMinutes > 0 || sleepTimerMinutes == -1) {
                        Spacer(modifier = Modifier.height(3.dp))
                        val timerLabel = if (sleepTimerMinutes == -1) "End of track" else "%d:%02d".format(sleepTimerRemainingSeconds / 60, sleepTimerRemainingSeconds % 60)
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .background(Color(0x3BFFFFFF))
                                .clickable { showSleepTimerDialog = true }
                                .padding(horizontal = 8.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Bedtime,
                                contentDescription = "Sleep Timer",
                                tint = palette.accent,
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = timerLabel,
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Box {
                    val optionsInteraction = remember { MutableInteractionSource() }
                    val isOptionsPressed by optionsInteraction.collectIsPressedAsState()
                    val optionsScale by animateFloatAsState(
                        targetValue = if (isOptionsPressed) 0.85f else 1.0f,
                        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
                        label = "optionsScale"
                    )
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .scale(optionsScale)
                            .clickable(
                                interactionSource = optionsInteraction,
                                indication = null,
                                role = Role.Button,
                                onClick = { showOptionsMenu = true }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        MaterialExpressiveShape(
                            shape = "flower8",
                            color = Color(0x2BFFFFFF),
                            modifier = Modifier.fillMaxSize()
                        )
                        MaterialSymbol(
                            symbol = MaterialSymbols.MoreVert,
                            contentDescription = "Options",
                            tint = Color.White,
                            size = 22.dp
                        )
                    }

                    DropdownMenu(
                        expanded = showOptionsMenu,
                        onDismissRequest = { showOptionsMenu = false },
                        modifier = Modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    ) {
                        DropdownMenuItem(
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Rounded.Bedtime,
                                    contentDescription = null,
                                    tint = if (sleepTimerMinutes != 0) palette.accent else Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            },
                            text = {
                                val label = if (sleepTimerMinutes == 0) "Sleep Timer" else if (sleepTimerMinutes == -1) "Sleep Timer: End of track" else "Sleep Timer: %d:%02d".format(sleepTimerRemainingSeconds / 60, sleepTimerRemainingSeconds % 60)
                                Text(label, color = if (sleepTimerMinutes != 0) palette.accent else Color.White, fontSize = 13.sp)
                            },
                            onClick = {
                                showOptionsMenu = false
                                showSleepTimerDialog = true
                            }
                        )
                        DropdownMenuItem(
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Rounded.Info,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            },
                            text = { Text(stringResource(R.string.menu_audio_info), color = Color.White, fontSize = 13.sp) },
                            onClick = {
                                showOptionsMenu = false
                                showAudioInfoDialog = true
                            }
                        )
                        DropdownMenuItem(
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Rounded.Album,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            },
                            text = { Text(stringResource(R.string.menu_go_to_album), color = Color.White, fontSize = 13.sp) },
                            onClick = {
                                showOptionsMenu = false
                                onAlbumClick?.invoke()
                            }
                        )
                        DropdownMenuItem(
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Rounded.Person,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            },
                            text = { Text(stringResource(R.string.menu_go_to_artist), color = Color.White, fontSize = 13.sp) },
                            onClick = {
                                showOptionsMenu = false
                                onArtistClick?.invoke(track.artist, track.artistUri)
                            }
                        )
                        if (userPlaylists.isNotEmpty() && onAddTrackToPlaylist != null) {
                            DropdownMenuItem(
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Rounded.PlaylistAdd,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(20.dp)
                                    )
                                },
                                text = { Text(stringResource(R.string.menu_add_to_playlist), color = Color.White, fontSize = 13.sp) },
                                onClick = {
                                    showOptionsMenu = false
                                    showAddToPlaylistDialog = true
                                }
                            )
                        }
                        DropdownMenuItem(
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Rounded.ContentCopy,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            },
                            text = { Text(stringResource(R.string.menu_copy_uri), color = Color.White, fontSize = 13.sp) },
                            onClick = {
                                showOptionsMenu = false
                                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                cm?.setPrimaryClip(ClipData.newPlainText("Spotify URI", track.uri))
                                Toast.makeText(context, context.getString(R.string.toast_copied_uri), Toast.LENGTH_SHORT).show()
                            }
                        )
                    }
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
                                modifier = Modifier.fillMaxSize(),
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
                            var lastLineIdx by remember { mutableIntStateOf(-1) }
                            var rippleTrigger by remember { mutableIntStateOf(0) }
                            var scrollDeltaSign by remember { mutableFloatStateOf(1f) }

                            // Initial positioning: jump directly to currently active line once measured
                            LaunchedEffect(isLineSynced, lyricLines.size, selectedTab) {
                                if (selectedTab == 1 && isLineSynced && activeLineIdx in lyricLines.indices) {
                                    snapshotFlow { lyricsListState.layoutInfo.totalItemsCount }
                                        .filter { it > 0 }
                                        .first()
                                    lyricsListState.scrollToItem(activeLineIdx, 0)
                                }
                            }

                            // Follow playback: smoothly animate scroll maintaining active line at top
                            LaunchedEffect(activeLineIdx, isLineSynced) {
                                if (isLineSynced && activeLineIdx in lyricLines.indices) {
                                    scrollDeltaSign = if (activeLineIdx > lastLineIdx) 1f else -1f
                                    lastLineIdx = activeLineIdx
                                    rippleTrigger++

                                    lyricsListState.animateScrollToItem(
                                        index = activeLineIdx,
                                        scrollOffset = 0
                                    )
                                }
                            }

                            BoxWithConstraints(
                                modifier = Modifier.fillMaxSize()
                            ) {
                                LazyColumn(
                                    state = lyricsListState,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .graphicsLayer {
                                            compositingStrategy = CompositingStrategy.Offscreen
                                        }
                                        .drawWithContent {
                                            drawContent()
                                            drawRect(
                                                brush = Brush.verticalGradient(
                                                    0.0f to Color.Transparent,
                                                    0.08f to Color.Black,
                                                    0.92f to Color.Black,
                                                    1.0f to Color.Transparent
                                                ),
                                                blendMode = BlendMode.DstIn
                                            )
                                        },
                                    contentPadding = PaddingValues(
                                        top = 32.dp,
                                        bottom = (maxHeight - 80.dp).coerceAtLeast(140.dp)
                                    ),
                                    verticalArrangement = Arrangement.spacedBy(16.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    itemsIndexed(lyricLines, key = { index, line -> "$index-${line.startTimeMs}" }) { index, line ->
                                        val isCurrent = isLineSynced && (index == activeLineIdx)
                                        val isPast = isLineSynced && (index < activeLineIdx)

                                        // Desktop wave ripple stagger: lines near active line move first
                                        val dist = abs(index - activeLineIdx).coerceAtMost(8)
                                        val staggerDelayMs = dist * 16

                                        val lineLag = remember { Animatable(0f) }
                                        LaunchedEffect(rippleTrigger) {
                                            if (rippleTrigger > 0 && isLineSynced) {
                                                lineLag.snapTo(12f * scrollDeltaSign)
                                                kotlinx.coroutines.delay(staggerDelayMs.toLong())
                                                lineLag.animateTo(
                                                    targetValue = 0f,
                                                    animationSpec = spring(
                                                        dampingRatio = 0.62f,
                                                        stiffness = 340f
                                                    )
                                                )
                                            }
                                        }

                                        // Active line spring growth (matches Desktop: the sung line grows on a spring, centered)
                                        val lineScale by animateFloatAsState(
                                            targetValue = if (isCurrent) 1.04f else 0.96f,
                                            animationSpec = spring(dampingRatio = 0.55f, stiffness = 320f),
                                            label = "lyricScale"
                                        )
                                        val pillBg by animateColorAsState(
                                            targetValue = if (isCurrent) palette.accent.copy(alpha = 0.22f) else Color.Transparent,
                                            animationSpec = tween(durationMillis = 320, easing = SwiftifyMotion.EmphasizedDecel),
                                            label = "lyricPillBg"
                                        )
                                        val textColor by animateColorAsState(
                                            targetValue = when {
                                                !isLineSynced -> Color.White
                                                isCurrent -> Color.White
                                                isPast -> Color(0x4DFFFFFF)
                                                else -> Color(0x73FFFFFF)
                                            },
                                            animationSpec = tween(durationMillis = 240),
                                            label = "lyricTextColor"
                                        )

                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .graphicsLayer {
                                                    scaleX = lineScale
                                                    scaleY = lineScale
                                                    translationY = lineLag.value
                                                    transformOrigin = TransformOrigin.Center
                                                }
                                                .clickable(
                                                    interactionSource = remember { MutableInteractionSource() },
                                                    indication = null
                                                ) {
                                                    if (line.startTimeMs > 0L) {
                                                        onSeek(line.startTimeMs)
                                                    }
                                                },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            // Desktop WavyPill scalloped bubble around the active line
                                            Box(
                                                modifier = Modifier
                                                    .wrapContentSize()
                                                    .padding(horizontal = 20.dp)
                                                    .drawBehind {
                                                        if (isLineSynced && pillBg.alpha > 0.005f) {
                                                            val ampPx = 1.6.dp.toPx()
                                                            val wavePx = 15.dp.toPx()
                                                            val path = buildWavyPillPath(
                                                                width = size.width,
                                                                height = size.height,
                                                                amplitude = ampPx,
                                                                wavelength = wavePx,
                                                                phase = 0f,
                                                                cornerRadius = 32.dp.toPx()
                                                            )
                                                            drawPath(
                                                                path = path,
                                                                color = pillBg
                                                            )
                                                        }
                                                    }
                                                    .padding(horizontal = 22.dp, vertical = 9.dp)
                                            ) {
                                                Text(
                                                    text = if (line.words.isNotEmpty()) line.words else "♪",
                                                    fontFamily = GoogleSansFlexFamily,
                                                    color = textColor,
                                                    fontSize = if (isCurrent) 24.sp else 19.sp,
                                                    fontWeight = if (isCurrent) FontWeight.Black else FontWeight.Bold,
                                                    lineHeight = if (isCurrent) 32.sp else 26.sp,
                                                    textAlign = TextAlign.Center
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        } else {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = lyricsErrorMsg ?: "Lyrics are unavailable for this track.",
                                    fontFamily = GoogleSansFlexFamily,
                                    color = Color(0xCCFFFFFF),
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Medium,
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
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
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
                    fontFamily = GoogleSansFlexFamily,
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
                    fontFamily = GoogleSansFlexFamily,
                    color = Color(0xCCFFFFFF),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.clickable { onArtistClick?.invoke(track.artist, track.artistUri) }
                )
            }

            IconButton(
                onClick = {
                    val prevSaved = isLiked
                    val newSaved = !prevSaved
                    isLiked = newSaved
                    coroutineScope.launch {
                        val success = SpotifyApi.setTrackSaved(context, track.uri, newSaved)
                        if (!success) {
                            isLiked = prevSaved
                            Toast.makeText(context, context.getString(R.string.toast_failed_saved_tracks), Toast.LENGTH_SHORT).show()
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
            // Shuffle with clover4 shape backing and spring bounce animation
            val shuffleInteraction = remember { MutableInteractionSource() }
            val isShufflePressed by shuffleInteraction.collectIsPressedAsState()
            val shuffleScale by animateFloatAsState(
                targetValue = if (isShufflePressed) 0.82f else 1.0f,
                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
                label = "shuffleScale"
            )
            val cloverAlpha by animateFloatAsState(
                targetValue = if (isShuffle) 1.0f else if (isShufflePressed) 0.25f else 0.0f,
                animationSpec = tween(durationMillis = 180),
                label = "cloverAlpha"
            )
            val cloverScale by animateFloatAsState(
                targetValue = if (isShuffle) 1.0f else 0.40f,
                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
                label = "cloverScale"
            )
            val shuffleIconTint by animateColorAsState(
                targetValue = if (isShuffle) palette.onAccent else Color(0x99FFFFFF),
                animationSpec = tween(durationMillis = 180),
                label = "shuffleTint"
            )
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .scale(shuffleScale)
                    .clickable(
                        interactionSource = shuffleInteraction,
                        indication = null,
                        role = Role.Button,
                        onClick = onToggleShuffle
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (cloverAlpha > 0.01f) {
                    MaterialExpressiveShape(
                        shape = "clover4",
                        color = palette.accent.copy(alpha = cloverAlpha),
                        modifier = Modifier
                            .size(46.dp)
                            .graphicsLayer {
                                scaleX = cloverScale
                                scaleY = cloverScale
                            }
                    )
                }
                MaterialSymbol(
                    symbol = MaterialSymbols.Shuffle,
                    contentDescription = "Shuffle",
                    tint = shuffleIconTint,
                    size = 22.dp
                )
            }

            // Prev with Material Expressive arrowLeft shape backing and spring animation
            val prevInteraction = remember { MutableInteractionSource() }
            val isPrevPressed by prevInteraction.collectIsPressedAsState()
            val prevShapeScale by animateFloatAsState(
                targetValue = if (isPrevPressed) 1.0f else 0.7f,
                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium)
            )
            val prevShapeAlpha by animateFloatAsState(
                targetValue = if (isPrevPressed) 0.35f else 0.0f,
                animationSpec = tween(durationMillis = 150)
            )
            val prevIconScale by animateFloatAsState(
                targetValue = if (isPrevPressed) 0.85f else 1.0f,
                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium)
            )
            val prevScale by animateFloatAsState(
                targetValue = if (isPrevPressed) 0.88f else 1.0f,
                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium)
            )
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .scale(prevScale)
                    .clickable(
                        interactionSource = prevInteraction,
                        indication = null,
                        role = Role.Button,
                        onClick = onPrev
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (prevShapeAlpha > 0.01f) {
                    MaterialExpressiveShape(
                        shape = "arrowLeft",
                        color = palette.accent.copy(alpha = prevShapeAlpha),
                        modifier = Modifier
                            .size(46.dp)
                            .graphicsLayer {
                                scaleX = prevShapeScale
                                scaleY = prevShapeScale
                            }
                    )
                }
                MaterialSymbol(
                    symbol = MaterialSymbols.SkipPrevious,
                    contentDescription = "Previous",
                    tint = Color.White,
                    size = 26.dp,
                    modifier = Modifier.graphicsLayer {
                        scaleX = prevIconScale
                        scaleY = prevIconScale
                    }
                )
            }

            // Play/Pause button with cookie morphing, continuous rotation, glyph pop, and confetti burst
            SwiftifyPlayButton(
                isPlaying = isPlaying,
                onClick = onPlayPause,
                buttonSize = 78.dp,
                iconSize = 40.dp,
                primaryColor = palette.accent,
                onPrimaryColor = palette.onAccent,
                secondaryColor = palette.accent.copy(alpha = 0.7f),
                tertiaryColor = palette.accent
            )

            // Next with Material Expressive arrowRight shape backing and spring animation
            val nextInteraction = remember { MutableInteractionSource() }
            val isNextPressed by nextInteraction.collectIsPressedAsState()
            val nextShapeScale by animateFloatAsState(
                targetValue = if (isNextPressed) 1.0f else 0.7f,
                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium)
            )
            val nextShapeAlpha by animateFloatAsState(
                targetValue = if (isNextPressed) 0.35f else 0.0f,
                animationSpec = tween(durationMillis = 150)
            )
            val nextIconScale by animateFloatAsState(
                targetValue = if (isNextPressed) 0.85f else 1.0f,
                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium)
            )
            val nextScale by animateFloatAsState(
                targetValue = if (isNextPressed) 0.88f else 1.0f,
                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium)
            )
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .scale(nextScale)
                    .clickable(
                        interactionSource = nextInteraction,
                        indication = null,
                        role = Role.Button,
                        onClick = onNext
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (nextShapeAlpha > 0.01f) {
                    MaterialExpressiveShape(
                        shape = "arrowRight",
                        color = palette.accent.copy(alpha = nextShapeAlpha),
                        modifier = Modifier
                            .size(46.dp)
                            .graphicsLayer {
                                scaleX = nextShapeScale
                                scaleY = nextShapeScale
                            }
                    )
                }
                MaterialSymbol(
                    symbol = MaterialSymbols.SkipNext,
                    contentDescription = "Next",
                    tint = Color.White,
                    size = 26.dp,
                    modifier = Modifier.graphicsLayer {
                        scaleX = nextIconScale
                        scaleY = nextIconScale
                    }
                )
            }

            // Repeat with cookie6 / cookie4 shape backing and spring bounce animation
            val repeatInteraction = remember { MutableInteractionSource() }
            val isRepeatPressed by repeatInteraction.collectIsPressedAsState()
            val repeatScale by animateFloatAsState(
                targetValue = if (isRepeatPressed) 0.82f else 1.0f,
                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
                label = "repeatScale"
            )
            val repeatAlpha by animateFloatAsState(
                targetValue = if (repeatMode > 0) 1.0f else if (isRepeatPressed) 0.25f else 0.0f,
                animationSpec = tween(durationMillis = 180),
                label = "repeatAlpha"
            )
            val repeatShapeScale by animateFloatAsState(
                targetValue = if (repeatMode > 0) 1.0f else 0.40f,
                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
                label = "repeatShapeScale"
            )
            val repeatMorphProgress by animateFloatAsState(
                targetValue = if (repeatMode == 2) 1.0f else 0.0f,
                animationSpec = spring(dampingRatio = 0.55f, stiffness = 320f),
                label = "repeatMorph"
            )
            val repeatIconRotation by animateFloatAsState(
                targetValue = if (repeatMode == 2) 360f else 0f,
                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
                label = "repeatIconRotation"
            )
            val repeatIconTint by animateColorAsState(
                targetValue = if (repeatMode > 0) palette.onAccent else Color(0x99FFFFFF),
                animationSpec = tween(durationMillis = 180),
                label = "repeatTint"
            )
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .scale(repeatScale)
                    .clickable(
                        interactionSource = repeatInteraction,
                        indication = null,
                        role = Role.Button,
                        onClick = onToggleRepeat
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (repeatAlpha > 0.01f) {
                    MorphedExpressiveShape(
                        fromShape = "cookie6",
                        toShape = "cookie4",
                        morphProgress = repeatMorphProgress,
                        color = palette.accent.copy(alpha = repeatAlpha),
                        modifier = Modifier
                            .size(46.dp)
                            .graphicsLayer {
                                scaleX = repeatShapeScale
                                scaleY = repeatShapeScale
                            }
                    )
                }

                Box(
                    modifier = Modifier.graphicsLayer {
                        rotationZ = repeatIconRotation
                    },
                    contentAlignment = Alignment.Center
                ) {
                    Crossfade(
                        targetState = repeatMode == 2,
                        animationSpec = tween(180),
                        label = "repeatIconCrossfade"
                    ) { isSingleTrack ->
                        MaterialSymbol(
                            symbol = if (isSingleTrack) MaterialSymbols.RepeatOne else MaterialSymbols.Repeat,
                            contentDescription = "Repeat",
                            tint = repeatIconTint,
                            size = 22.dp
                        )
                    }
                }
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

    if (showSleepTimerDialog) {
        val options = listOf(
            0 to "Off",
            5 to "5 minutes",
            15 to "15 minutes",
            30 to "30 minutes",
            45 to "45 minutes",
            60 to "1 hour",
            -1 to "End of this track"
        )
        AlertDialog(
            onDismissRequest = { showSleepTimerDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.Bedtime,
                        contentDescription = null,
                        tint = palette.accent,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(stringResource(R.string.title_sleep_timer), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                }
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                    options.forEach { (minutes, label) ->
                        val isSelected = sleepTimerMinutes == minutes
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isSelected) palette.accent.copy(alpha = 0.2f) else Color.Transparent)
                                .clickable {
                                    showSleepTimerDialog = false
                                    onSetSleepTimer?.invoke(minutes)
                                }
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = label,
                                color = if (isSelected) palette.accent else Color.White,
                                fontSize = 15.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Rounded.Check,
                                    contentDescription = null,
                                    tint = palette.accent,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            confirmButton = {
                TextButton(onClick = { showSleepTimerDialog = false }) {
                    Text(stringResource(R.string.btn_close), color = palette.accent, fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    if (showAudioInfoDialog) {
        AlertDialog(
            onDismissRequest = { showAudioInfoDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.Info,
                        contentDescription = null,
                        tint = palette.accent,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(stringResource(R.string.menu_audio_info), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                }
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                    AudioInfoRow("Track", track.title)
                    AudioInfoRow("Artist", track.artist)
                    AudioInfoRow("Album", track.album)
                    AudioInfoRow("Codec", "Ogg Vorbis 320 kbps (High Quality)")
                    AudioInfoRow("Audio Output", "AAudio Direct Stereo (Native Rust)")
                    AudioInfoRow("Sample Rate", "44,100 Hz / 16-bit Float")
                    AudioInfoRow("URI", track.uri)
                }
            },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            confirmButton = {
                TextButton(onClick = { showAudioInfoDialog = false }) {
                    Text(stringResource(R.string.btn_close), color = palette.accent, fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    if (showAddToPlaylistDialog) {
        AlertDialog(
            onDismissRequest = { showAddToPlaylistDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.PlaylistAdd,
                        contentDescription = null,
                        tint = palette.accent,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(stringResource(R.string.menu_add_to_playlist), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                }
            },
            text = {
                if (userPlaylists.isEmpty()) {
                    Text(stringResource(R.string.no_editable_playlists), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
                } else {
                    Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                        userPlaylists.forEach { pl ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable {
                                        showAddToPlaylistDialog = false
                                        onAddTrackToPlaylist?.invoke(pl, track)
                                    }
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                AsyncImage(
                                    model = pl.artworkUrl,
                                    contentDescription = null,
                                    modifier = Modifier.size(40.dp).clip(RoundedCornerShape(8.dp)),
                                    contentScale = ContentScale.Crop
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(pl.name, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                                    Text(pl.owner, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, maxLines = 1)
                                }
                            }
                        }
                    }
                }
            },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            confirmButton = {
                TextButton(onClick = { showAddToPlaylistDialog = false }) {
                    Text(stringResource(R.string.cancel), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        )
    }
}
}

@Composable
private fun AudioInfoRow(label: String, value: String) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(label.uppercase(), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        Text(value, color = Color.White, fontSize = 13.sp)
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
            tint = if (isSelected) Color.Black else Color(0xDDFFFFFF),
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = title,
            color = if (isSelected) Color.Black else Color(0xDDFFFFFF),
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold
        )
    }
}
