package org.swiftify.app

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import org.json.JSONObject
import org.swiftify.app.design.SwiftifyMotion
import org.swiftify.app.design.SwiftifyThemeMode

@Composable
fun SwiftifyApp(
    authState: AuthState,
    tracks: List<TrackItem>,
    userPlaylists: List<PlaylistItem>,
    currentTrackIndex: Int,
    livePlayingTrack: TrackItem?,
    isPlaying: Boolean,
    progressMs: Long,
    connectEnabled: Boolean,
    statusMsg: String,
    accessToken: String?,
    onLoginClick: () -> Unit,
    onTrackClick: (Int) -> Unit,
    onTrackSelect: (TrackItem, List<TrackItem>?) -> Unit,
    onAddToQueue: (TrackItem) -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrev: () -> Unit,
    onSeek: (Long) -> Unit,
    onConnectToggle: (Boolean) -> Unit,
    onSetStopAtEnd: ((Boolean) -> Unit)? = null,
    isShuffle: Boolean = false,
    repeatMode: Int = 0,
    onToggleShuffle: () -> Unit = {},
    onToggleRepeat: () -> Unit = {},
    onCrossfadeChanged: ((Boolean, Int) -> Unit)? = null,
    customQueue: List<TrackItem> = emptyList(),
    onRemoveFromCustomQueue: ((Int) -> Unit)? = null,
    onClearCustomQueue: (() -> Unit)? = null,
    queueContextName: String = "Swiftify",
    onCreatePlaylist: ((String, String) -> Unit)? = null,
    onDeletePlaylist: ((PlaylistItem) -> Unit)? = null,
    onAddTrackToPlaylist: ((PlaylistItem, TrackItem) -> Unit)? = null,
    onRemoveTrackFromPlaylist: ((PlaylistItem, TrackItem) -> Unit)? = null,
    sleepTimerMinutes: Int = 0,
    sleepTimerRemainingSeconds: Int = 0,
    onSetSleepTimer: ((Int) -> Unit)? = null,
    onLogout: (() -> Unit)? = null,
    recentlyPlayed: List<TrackItem> = emptyList(),
    currentTheme: SwiftifyThemeMode = SwiftifyThemeMode.SPOTIFY,
    onThemeChange: ((SwiftifyThemeMode) -> Unit)? = null
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var isPlayerExpanded by remember { mutableStateOf(false) }
    var activePlaylist by remember { mutableStateOf<PlaylistItem?>(null) }
    var activeArtist by remember { mutableStateOf<ArtistItem?>(null) }
    var heroFlightData by remember { mutableStateOf<HeroFlightData?>(null) }
    var heroTargetRect by remember { mutableStateOf<Rect?>(null) }
    var showAllPlaylists by remember { mutableStateOf(false) }
    var openedFromAllPlaylists by remember { mutableStateOf(false) }
    var activeAlbumUri by remember { mutableStateOf<String?>(null) }
    var activeAlbumTitle by remember { mutableStateOf("Album") }
    var activeAlbumArtwork by remember { mutableStateOf("") }
    var artistPickerList by remember { mutableStateOf<List<ArtistItem>?>(null) }
    val density = LocalDensity.current
    val detailRisePx = with(density) { 14.dp.roundToPx() }

    val openAlbumByTrack: (TrackItem) -> Unit = { t ->
        val albTitle = if (t.album.isNotEmpty()) t.album else t.title
        if (t.albumUri.isNotEmpty()) {
            activePlaylist = null
            activeArtist = null
            showAllPlaylists = false
            activeAlbumUri = t.albumUri
            activeAlbumTitle = albTitle
            activeAlbumArtwork = t.artworkUrl
        } else {
            // Search album or if album is present
            SpotifyBridge.searchTracks(albTitle) { ok, json ->
                var albUri = ""
                var albArt = t.artworkUrl
                if (ok && json.isNotEmpty()) {
                    try {
                        val obj = JSONObject(json)
                        val arr = obj.optJSONArray("tracks")
                        if (arr != null && arr.length() > 0) {
                            for (i in 0 until arr.length()) {
                                val item = arr.getJSONObject(i)
                                val albObj = item.optJSONObject("album")
                                if (albObj != null) {
                                    val uri = albObj.optString("uri")
                                    if (uri.isNotEmpty()) {
                                        albUri = uri
                                        val imgs = albObj.optJSONArray("images")
                                        val best = getBestArtworkUrl(imgs)
                                        if (best.isNotEmpty()) {
                                            albArt = best
                                        }
                                        break
                                    }
                                }
                            }
                        }
                    } catch (_: Exception) {}
                }
                if (albUri.isNotEmpty()) {
                    activePlaylist = null
                    activeArtist = null
                    showAllPlaylists = false
                    activeAlbumUri = albUri
                    activeAlbumTitle = albTitle
                    activeAlbumArtwork = albArt
                }
            }
        }
    }

    val openArtistScreen: (String, String) -> Unit = { artName, artUri ->
        val names = artName.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        if (names.size > 1) {
            val items = names.map { n ->
                ArtistItem(name = n, artworkUrl = "", uri = if (n == names[0]) artUri else "")
            }
            artistPickerList = items
        } else {
            activeArtist = ArtistItem(name = artName, artworkUrl = "", uri = artUri)
        }
    }

    val currentTrack = livePlayingTrack ?: tracks.getOrNull(currentTrackIndex) ?: tracks.firstOrNull()

    // Handle system back gesture
    BackHandler(enabled = isPlayerExpanded || activePlaylist != null || activeArtist != null || activeAlbumUri != null || showAllPlaylists) {
        if (isPlayerExpanded) {
            isPlayerExpanded = false
        } else if (activeAlbumUri != null) {
            activeAlbumUri = null
        } else if (activePlaylist != null) {
            activePlaylist = null
            if (!openedFromAllPlaylists) {
                showAllPlaylists = false
            }
        } else if (activeArtist != null) {
            activeArtist = null
            heroFlightData = null
            heroTargetRect = null
        } else if (showAllPlaylists) {
            showAllPlaylists = false
            openedFromAllPlaylists = false
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        // The hero flyer is drawn over the pages but must stay under the
        // player and the nav bar, as on the desktop (Main.qml). Measure where
        // the bottom bar begins so the overlay can clip itself there.
        var bottomBarTopPx by remember { mutableFloatStateOf(Float.MAX_VALUE) }
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            bottomBar = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.Transparent)
                        .onGloballyPositioned { bottomBarTopPx = it.positionInRoot().y }
                ) {
                    // Floating Mini Player (Independent floating box above navigation)
                    if (currentTrack != null) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 6.dp)
                                .shadow(8.dp, RoundedCornerShape(20.dp), spotColor = Color.Black.copy(alpha = 0.25f))
                                .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f), RoundedCornerShape(20.dp))
                                .clip(RoundedCornerShape(20.dp))
                                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                                .clickable { isPlayerExpanded = true }
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                AsyncImage(
                                    model = currentTrack.artworkUrl,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(RoundedCornerShape(7.dp)),
                                    contentScale = ContentScale.Crop
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = currentTrack.title,
                                        color = Color.White,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = currentTrack.artist,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                SwiftifyPlayButton(
                                    isPlaying = isPlaying,
                                    onClick = onPlayPause,
                                    buttonSize = 42.dp,
                                    iconSize = 22.dp,
                                    primaryColor = MaterialTheme.colorScheme.primary,
                                    onPrimaryColor = MaterialTheme.colorScheme.onPrimary,
                                    secondaryColor = MaterialTheme.colorScheme.secondary,
                                    tertiaryColor = MaterialTheme.colorScheme.tertiary
                                )
                            }
                        }
                    }

                    // Compact Translucent Navigation Bar (Glass effect bleeding off screen, no outline)
                    val navBarBottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
                    val navTopSpacing = 12.dp
                    val navBottomSpacing = if (navBarBottomInset > 0.dp) 4.dp else 12.dp

                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        shape = RectangleShape,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .windowInsetsPadding(WindowInsets.navigationBars)
                        ) {
                            Spacer(modifier = Modifier.height(navTopSpacing))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier.weight(1f),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CompactNavItem(
                                        icon = Icons.Rounded.Home,
                                        label = stringResource(R.string.nav_home),
                                        selected = selectedTab == 0 && activePlaylist == null && activeArtist == null && activeAlbumUri == null && !showAllPlaylists,
                                        onClick = {
                                            selectedTab = 0
                                            activePlaylist = null
                                            activeArtist = null
                                            activeAlbumUri = null
                                            showAllPlaylists = false
                                            openedFromAllPlaylists = false
                                        }
                                    )
                                }
                                Box(
                                    modifier = Modifier.weight(1f),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CompactNavItem(
                                        icon = Icons.Rounded.Search,
                                        label = stringResource(R.string.nav_search),
                                        selected = selectedTab == 1 && activePlaylist == null && activeArtist == null && activeAlbumUri == null && !showAllPlaylists,
                                        onClick = {
                                            selectedTab = 1
                                            activePlaylist = null
                                            activeArtist = null
                                            activeAlbumUri = null
                                            showAllPlaylists = false
                                            openedFromAllPlaylists = false
                                        }
                                    )
                                }
                                Box(
                                    modifier = Modifier.weight(1f),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CompactNavItem(
                                        icon = Icons.Rounded.Settings,
                                        label = stringResource(R.string.nav_settings),
                                        selected = selectedTab == 2 && activePlaylist == null && activeArtist == null && activeAlbumUri == null && !showAllPlaylists,
                                        onClick = {
                                            selectedTab = 2
                                            activePlaylist = null
                                            activeArtist = null
                                            activeAlbumUri = null
                                            showAllPlaylists = false
                                            openedFromAllPlaylists = false
                                        }
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(navBottomSpacing))
                        }
                    }
                }
            }
        ) { padding ->
            Box(modifier = Modifier.fillMaxSize().padding(top = padding.calculateTopPadding())) {
                val currentDestination: ScreenDestination = when {
                    activeArtist != null -> ScreenDestination.Artist(activeArtist!!)
                    activeAlbumUri != null -> ScreenDestination.Album(activeAlbumUri!!, activeAlbumTitle, activeAlbumArtwork)
                    activePlaylist != null -> ScreenDestination.Playlist(activePlaylist!!)
                    showAllPlaylists -> ScreenDestination.AllPlaylists()
                    selectedTab == 1 -> ScreenDestination.Search()
                    selectedTab == 2 -> ScreenDestination.Settings()
                    else -> ScreenDestination.Home()
                }

                AnimatedContent(
                    targetState = currentDestination,
                    transitionSpec = {
                        val isEnteringDetail = targetState is ScreenDestination.Artist || targetState is ScreenDestination.Album || targetState is ScreenDestination.Playlist || targetState is ScreenDestination.AllPlaylists
                        val isExitingDetail = initialState is ScreenDestination.Artist || initialState is ScreenDestination.Album || initialState is ScreenDestination.Playlist || initialState is ScreenDestination.AllPlaylists

                        if (targetState is ScreenDestination.Artist && heroFlightData != null) {
                            // Desktop parity (PageHost.push + enterMotion): the
                            // page behind is hidden at once (no crossfade, or
                            // its avatar would ghost under the flyer), and the
                            // new page fades in (OutCubic 210ms) while rising
                            // 14dp on emphasized decel. The clicked avatar flies
                            // to its hero.
                            (fadeIn(animationSpec = tween(durationMillis = 210, easing = SwiftifyMotion.OutCubic)) +
                                slideInVertically(
                                    initialOffsetY = { detailRisePx },
                                    animationSpec = tween(durationMillis = 400, easing = SwiftifyMotion.EmphasizedDecel)
                                ))
                                .togetherWith(ExitTransition.None)
                        } else if (initialState is ScreenDestination.Playlist && targetState is ScreenDestination.AllPlaylists) {
                            (slideInHorizontally(
                                initialOffsetX = { -(it * 0.15f).toInt() },
                                animationSpec = tween(durationMillis = SwiftifyMotion.DurationMotionFastMs, easing = SwiftifyMotion.EmphasizedDecel)
                            ) + fadeIn(animationSpec = tween(SwiftifyMotion.DurationMotionFastMs)))
                            .togetherWith(
                                slideOutHorizontally(
                                    targetOffsetX = { (it * 0.22f).toInt() },
                                    animationSpec = tween(durationMillis = SwiftifyMotion.DurationMotionDefaultMs, easing = SwiftifyMotion.ExpressiveDefaultSpatial)
                                ) + fadeOut(animationSpec = tween(SwiftifyMotion.DurationMotionDefaultMs))
                            )
                        } else if (initialState is ScreenDestination.AllPlaylists && targetState is ScreenDestination.Playlist) {
                            (slideInHorizontally(
                                initialOffsetX = { (it * 0.22f).toInt() },
                                animationSpec = tween(durationMillis = SwiftifyMotion.DurationMotionDefaultMs, easing = SwiftifyMotion.ExpressiveDefaultSpatial)
                            ) + fadeIn(animationSpec = tween(SwiftifyMotion.DurationMotionDefaultMs)))
                            .togetherWith(
                                slideOutHorizontally(
                                    targetOffsetX = { -(it * 0.15f).toInt() },
                                    animationSpec = tween(durationMillis = SwiftifyMotion.DurationMotionFastMs, easing = SwiftifyMotion.EmphasizedDecel)
                                ) + fadeOut(animationSpec = tween(SwiftifyMotion.DurationMotionFastMs))
                            )
                        } else if (isEnteringDetail && !isExitingDetail) {
                            (slideInHorizontally(
                                initialOffsetX = { (it * 0.22f).toInt() },
                                animationSpec = tween(durationMillis = SwiftifyMotion.DurationMotionDefaultMs, easing = SwiftifyMotion.ExpressiveDefaultSpatial)
                            ) + fadeIn(animationSpec = tween(SwiftifyMotion.DurationMotionDefaultMs)))
                            .togetherWith(
                                slideOutHorizontally(
                                    targetOffsetX = { -(it * 0.12f).toInt() },
                                    animationSpec = tween(durationMillis = SwiftifyMotion.DurationMotionFastMs, easing = SwiftifyMotion.EmphasizedDecel)
                                ) + fadeOut(animationSpec = tween(SwiftifyMotion.DurationMotionFastMs))
                            )
                        } else if (isExitingDetail && !isEnteringDetail) {
                            (slideInHorizontally(
                                initialOffsetX = { -(it * 0.12f).toInt() },
                                animationSpec = tween(durationMillis = SwiftifyMotion.DurationMotionFastMs, easing = SwiftifyMotion.EmphasizedDecel)
                            ) + fadeIn(animationSpec = tween(SwiftifyMotion.DurationMotionFastMs)))
                            .togetherWith(
                                slideOutHorizontally(
                                    targetOffsetX = { (it * 0.22f).toInt() },
                                    animationSpec = tween(durationMillis = SwiftifyMotion.DurationMotionDefaultMs, easing = SwiftifyMotion.ExpressiveDefaultSpatial)
                                ) + fadeOut(animationSpec = tween(SwiftifyMotion.DurationMotionDefaultMs))
                            )
                        } else {
                            fadeIn(animationSpec = tween(SwiftifyMotion.DurationMotionFastMs))
                                .togetherWith(fadeOut(animationSpec = tween(SwiftifyMotion.DurationMotionFastMs)))
                        }
                    },
                    label = "screenTransition"
                ) { dest ->
                    when (dest) {
                        is ScreenDestination.Artist -> {
                            ArtistDetailScreen(
                                artist = dest.artist,
                                livePlayingTrack = livePlayingTrack,
                                isPlaying = isPlaying,
                                onBack = {
                                    activeArtist = null
                                    heroFlightData = null
                                    heroTargetRect = null
                                },
                                onTrackSelect = { selectedTrack, playlistTracks ->
                                    onTrackSelect(selectedTrack, playlistTracks)
                                },
                                onPlayAll = { artistTracks ->
                                    if (artistTracks.isNotEmpty()) {
                                        onTrackSelect(artistTracks.first(), artistTracks)
                                    }
                                },
                                onAddToQueue = onAddToQueue,
                                onAlbumSelect = { albUri, albTitle, albArt ->
                                    activeAlbumUri = albUri
                                    activeAlbumTitle = albTitle
                                    activeAlbumArtwork = albArt
                                },
                                onAvatarPositioned = { rect ->
                                    heroTargetRect = rect
                                },
                                isHeroFlying = heroFlightData != null
                            )
                        }
                        is ScreenDestination.Album -> {
                            AlbumDetailScreen(
                                albumUri = dest.uri,
                                initialTitle = dest.title,
                                initialArtworkUrl = dest.artworkUrl,
                                livePlayingTrack = livePlayingTrack,
                                isPlaying = isPlaying,
                                onBack = { activeAlbumUri = null },
                                onTrackSelect = { selectedTrack, playlistTracks ->
                                    onSetStopAtEnd?.invoke(true)
                                    onTrackSelect(selectedTrack, playlistTracks)
                                },
                                onPlayAll = { albumTracks ->
                                    if (albumTracks.isNotEmpty()) {
                                        onSetStopAtEnd?.invoke(true)
                                        onTrackSelect(albumTracks.first(), albumTracks)
                                    }
                                },
                                onAddToQueue = onAddToQueue,
                                onArtistClick = { aName, aUri ->
                                    openArtistScreen(aName, aUri)
                                }
                            )
                        }
                        is ScreenDestination.Playlist -> {
                            PlaylistDetailScreen(
                                playlist = dest.playlist,
                                tracks = tracks,
                                livePlayingTrack = livePlayingTrack,
                                isPlaying = isPlaying,
                                onBack = {
                                    activePlaylist = null
                                    if (!openedFromAllPlaylists) {
                                        showAllPlaylists = false
                                    }
                                },
                                onTrackSelect = { selectedTrack, playlistTracks ->
                                    onSetStopAtEnd?.invoke(false)
                                    onTrackSelect(selectedTrack, playlistTracks)
                                },
                                onPlayAll = { playlistTracks ->
                                    if (playlistTracks.isNotEmpty()) {
                                        onSetStopAtEnd?.invoke(false)
                                        onTrackSelect(playlistTracks.first(), playlistTracks)
                                    }
                                },
                                onAddToQueue = onAddToQueue,
                                onTrackTitleClick = { track -> openAlbumByTrack(track) },
                                onArtistClick = { artistName, artistUri -> openArtistScreen(artistName, artistUri) },
                                userPlaylists = userPlaylists,
                                onDeletePlaylist = { p ->
                                    activePlaylist = null
                                    onDeletePlaylist?.invoke(p)
                                },
                                onAddTrackToPlaylist = onAddTrackToPlaylist,
                                onRemoveTrackFromPlaylist = onRemoveTrackFromPlaylist
                            )
                        }
                        is ScreenDestination.AllPlaylists -> {
                            AllPlaylistsScreen(
                                playlists = if (userPlaylists.isNotEmpty()) userPlaylists else samplePlaylists,
                                onBack = {
                                    showAllPlaylists = false
                                    openedFromAllPlaylists = false
                                },
                                onPlaylistClick = {
                                    openedFromAllPlaylists = true
                                    activePlaylist = it
                                },
                                onCreatePlaylist = onCreatePlaylist
                            )
                        }
                        is ScreenDestination.Home -> {
                            HomeScreen(
                                authState = authState,
                                tracks = if (recentlyPlayed.isNotEmpty()) recentlyPlayed else tracks,
                                currentTrackIndex = currentTrackIndex,
                                livePlayingTrack = livePlayingTrack,
                                isPlaying = isPlaying,
                                statusMsg = statusMsg,
                                onLoginClick = onLoginClick,
                                onTrackClick = onTrackClick,
                                onPlayPause = onPlayPause,
                                userPlaylists = userPlaylists,
                                onPlaylistClick = {
                                    openedFromAllPlaylists = false
                                    activePlaylist = it
                                },
                                onArtistClick = { activeArtist = it },
                                onArtistClickWithRect = { artist, rect ->
                                    if (rect != null) {
                                        heroFlightData = HeroFlightData(
                                            imageUrl = artist.artworkUrl,
                                            name = artist.name,
                                            fromRect = rect
                                        )
                                        heroTargetRect = null
                                    }
                                    activeArtist = artist
                                },
                                onSeeAllPlaylists = { showAllPlaylists = true }
                            )
                        }
                        is ScreenDestination.Search -> {
                            SearchScreen(
                                accessToken = accessToken,
                                allTracks = tracks,
                                currentTrackIndex = currentTrackIndex,
                                livePlayingTrack = livePlayingTrack,
                                isPlaying = isPlaying,
                                onTrackSelect = { track -> onTrackSelect(track, null) },
                                onAddToQueue = onAddToQueue,
                                onArtistSelect = { artist ->
                                    activeArtist = artist
                                },
                                onAlbumSelect = { track ->
                                    openAlbumByTrack(track)
                                },
                                onPlaylistSelect = {
                                    openedFromAllPlaylists = false
                                    activePlaylist = it
                                }
                            )
                        }
                        is ScreenDestination.Settings -> {
                            SettingsScreen(
                                connectEnabled = connectEnabled,
                                onConnectToggle = onConnectToggle,
                                onReauthorize = onLoginClick,
                                onCrossfadeChanged = onCrossfadeChanged,
                                onLogout = onLogout,
                                currentTheme = currentTheme,
                                onThemeChange = onThemeChange
                            )
                        }
                    }
                }
            }
        }

        // Full Screen Player
        if (currentTrack != null) {
            AnimatedVisibility(
                visible = isPlayerExpanded,
                enter = slideInVertically(initialOffsetY = { it }),
                exit = slideOutVertically(targetOffsetY = { it })
            ) {
                ExpandedPlayer(
                    track = currentTrack,
                    allTracks = tracks,
                    isPlaying = isPlaying,
                    progressMs = progressMs,
                    onCollapse = { isPlayerExpanded = false },
                    onPlayPause = onPlayPause,
                    onNext = onNext,
                    onPrev = onPrev,
                    onSeek = onSeek,
                    onSelectTrack = { track -> onTrackSelect(track, null) },
                    onArtistClick = { artistName, artistUri ->
                        isPlayerExpanded = false
                        openArtistScreen(artistName, artistUri)
                    },
                    onAlbumClick = {
                        isPlayerExpanded = false
                        openAlbumByTrack(currentTrack)
                    },
                    isShuffle = isShuffle,
                    repeatMode = repeatMode,
                    onToggleShuffle = onToggleShuffle,
                    onToggleRepeat = onToggleRepeat,
                    customQueue = customQueue,
                    onRemoveFromCustomQueue = onRemoveFromCustomQueue,
                    onClearCustomQueue = onClearCustomQueue,
                    queueContextName = queueContextName,
                    sleepTimerMinutes = sleepTimerMinutes,
                    sleepTimerRemainingSeconds = sleepTimerRemainingSeconds,
                    onSetSleepTimer = onSetSleepTimer,
                    userPlaylists = userPlaylists,
                    onAddTrackToPlaylist = onAddTrackToPlaylist
                )
            }

            // Artist Picker Bottom Sheet for songs with multiple artists
            if (artistPickerList != null) {
                ArtistPickerBottomSheet(
                    artists = artistPickerList!!,
                    onDismiss = { artistPickerList = null },
                    onArtistSelect = { sel ->
                        artistPickerList = null
                        activeArtist = sel
                    }
                )
            }
        }

        // Hero Flight Overlay
        heroFlightData?.let { flight ->
            HeroFlightOverlay(
                flightData = flight,
                targetRect = heroTargetRect,
                clipBottomPx = bottomBarTopPx,
                onFlightFinished = {
                    heroFlightData = null
                }
            )
        }
    }
}
