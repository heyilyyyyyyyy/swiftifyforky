package org.swiftify.app

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import org.swiftify.app.design.GoogleSansFlexFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import java.util.Calendar

// ─── Home Screen (Swiftify Desktop Style) ──────────────────────────────────────

@Composable
fun HomeScreen(
    authState: AuthState,
    tracks: List<TrackItem>,
    userPlaylists: List<PlaylistItem>,
    currentTrackIndex: Int,
    livePlayingTrack: TrackItem?,
    isPlaying: Boolean,
    statusMsg: String,
    onLoginClick: () -> Unit,
    onTrackClick: (Int) -> Unit,
    onPlayPause: () -> Unit,
    onPlaylistClick: (PlaylistItem) -> Unit,
    onArtistClick: (ArtistItem) -> Unit,
    onArtistClickWithRect: ((ArtistItem, Rect?) -> Unit)? = null,
    onSeeAllPlaylists: () -> Unit
) {
    val scrollState = rememberScrollState()
    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    val part = when {
        hour < 5 -> 3
        hour < 12 -> 0
        hour < 17 -> 1
        hour < 21 -> 2
        else -> 3
    }
    val greetingText = listOf(
        stringResource(R.string.good_morning),
        stringResource(R.string.good_afternoon),
        stringResource(R.string.good_evening),
        stringResource(R.string.up_late)
    )[part]
    val greetingIcon = when (part) {
        0 -> Icons.Rounded.WbSunny
        1 -> Icons.Rounded.LightMode
        2 -> Icons.Rounded.WbTwilight
        else -> Icons.Rounded.DarkMode
    }

    val spotlightTrack = livePlayingTrack ?: tracks.getOrNull(currentTrackIndex) ?: tracks.firstOrNull()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        // Top Header
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Swiftify",
                    fontFamily = GoogleSansFlexFamily,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.5).sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = greetingIcon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = greetingText,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.MoreVert,
                    contentDescription = "Options",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        // Auth status or Login Card if logged out
        when (authState) {
            is AuthState.LoggedOut -> {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                    shape = RoundedCornerShape(17.dp)
                ) {
                    Column(modifier = Modifier.padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Connect Spotify", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Spotify Premium required for full playback", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                        Spacer(modifier = Modifier.height(14.dp))
                        Button(
                            onClick = onLoginClick,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            shape = CircleShape
                        ) {
                            Text("Log in with Spotify", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
            is AuthState.Loading, is AuthState.Connecting -> {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), color = MaterialTheme.colorScheme.primary, strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(statusMsg.ifEmpty { "Connecting Spotify..." }, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                }
            }
            is AuthState.LoggedIn -> {}
        }

        // Continue Listening (Hero Spotlight Card - Material 3 Expressive)
        if (spotlightTrack != null) {
            val heroPalette = rememberTrackPalette(LocalContext.current, spotlightTrack.artworkUrl)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(16.dp, RoundedCornerShape(26.dp), spotColor = heroPalette.accent.copy(alpha = 0.3f))
                    .clip(RoundedCornerShape(26.dp))
                    .background(
                        Brush.verticalGradient(
                            listOf(heroPalette.dominant.copy(alpha = 0.85f), heroPalette.secondary.copy(alpha = 0.95f))
                        )
                    )
                    .padding(18.dp)
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(118.dp)
                                .shadow(12.dp, RoundedCornerShape(20.dp), spotColor = heroPalette.accent.copy(alpha = 0.4f))
                                .clip(RoundedCornerShape(20.dp))
                        ) {
                            AsyncImage(
                                model = spotlightTrack.artworkUrl,
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (isPlaying) "NOW PLAYING" else "CONTINUE LISTENING",
                                color = heroPalette.accent,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 1.2.sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = spotlightTrack.title,
                                color = Color.White,
                                fontSize = 21.sp,
                                fontWeight = FontWeight.Black,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = spotlightTrack.artist,
                                color = Color(0xDDFFFFFF),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = spotlightTrack.album,
                                color = Color(0x88FFFFFF),
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Button(
                                    onClick = onPlayPause,
                                    colors = ButtonDefaults.buttonColors(containerColor = heroPalette.accent),
                                    shape = RoundedCornerShape(999.dp),
                                    contentPadding = PaddingValues(horizontal = 18.dp, vertical = 6.dp),
                                    modifier = Modifier.height(38.dp)
                                ) {
                                    Icon(
                                        imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                        contentDescription = null,
                                        tint = heroPalette.onAccent,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = if (isPlaying) "Pause" else stringResource(R.string.resume),
                                        color = heroPalette.onAccent,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    WavyLine(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                        color = heroPalette.accent.copy(alpha = 0.35f)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        val homeContext = LocalContext.current
        val savedTrackCount = remember {
            homeContext.getSharedPreferences("swiftify_prefs", Context.MODE_PRIVATE)
                .getStringSet("saved_track_uris", emptySet())?.size ?: 0
        }

        // Two Quick Shortcuts: Favourite Songs & Your Playlists
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Favourite Songs
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(17.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .clickable {
                        onPlaylistClick(
                            PlaylistItem(
                                name = "Favourite Songs",
                                owner = if (savedTrackCount > 0) "$savedTrackCount tracks" else "Your Collection",
                                artworkUrl = "https://i.scdn.co/image/ab67616d0000b27344e9d13c687469d32a9f22a8",
                                uri = "spotify:collection"
                            )
                        )
                    }
                    .padding(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ShapeBadge(
                        shape = "heart",
                        icon = Icons.Rounded.Star,
                        size = 38.dp,
                        iconSize = 19.dp,
                        shapeAlpha = 0.32f,
                        glyphAlpha = 0.95f
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.fav_songs), fontFamily = GoogleSansFlexFamily, color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                        Text(
                            text = if (savedTrackCount > 0) "Tracks: $savedTrackCount" else "Collection",
                            fontFamily = GoogleSansFlexFamily,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.sp,
                            maxLines = 1
                        )
                    }
                    Icon(
                        imageVector = Icons.Rounded.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // Your Playlists Shortcut
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(17.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .clickable { onSeeAllPlaylists() }
                    .padding(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ShapeBadge(
                        shape = "bun",
                        icon = Icons.Rounded.LibraryMusic,
                        size = 38.dp,
                        iconSize = 20.dp,
                        shapeAlpha = 0.32f,
                        glyphAlpha = 0.95f
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.your_playlists), fontFamily = GoogleSansFlexFamily, color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                        val count = if (userPlaylists.isNotEmpty()) userPlaylists.size else 5
                        Text("Collections: $count", fontFamily = GoogleSansFlexFamily, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp, maxLines = 1)
                    }
                    Icon(
                        imageVector = Icons.Rounded.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Section 1: Your Playlists
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ShapeBadge(
                shape = "bun",
                icon = Icons.Rounded.QueueMusic,
                size = 34.dp,
                iconSize = 18.dp,
                shapeAlpha = 0.30f,
                glyphAlpha = 0.90f
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(stringResource(R.string.your_playlists), fontFamily = GoogleSansFlexFamily, color = MaterialTheme.colorScheme.onSurface, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            WavyLine(
                modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
            )
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .clickable { onSeeAllPlaylists() }
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text("→ ${stringResource(R.string.see_all)}", fontFamily = GoogleSansFlexFamily, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, fontWeight = FontWeight.Medium)
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        val displayPlaylists = if (userPlaylists.isNotEmpty()) userPlaylists else samplePlaylists
        LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            items(displayPlaylists) { playlist ->
                Column(modifier = Modifier.width(136.dp).clickable { onPlaylistClick(playlist) }) {
                    AsyncImage(
                        model = playlist.artworkUrl,
                        contentDescription = null,
                        modifier = Modifier
                            .size(136.dp)
                            .clip(RoundedCornerShape(12.dp)),
                        contentScale = ContentScale.Crop
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = playlist.name,
                        fontFamily = GoogleSansFlexFamily,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = playlist.owner,
                        fontFamily = GoogleSansFlexFamily,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Section 2: Recently Played
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ShapeBadge(
                shape = "clamShell",
                icon = Icons.Rounded.History,
                size = 34.dp,
                iconSize = 18.dp,
                shapeAlpha = 0.30f,
                glyphAlpha = 0.90f
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(stringResource(R.string.recently_played), fontFamily = GoogleSansFlexFamily, color = MaterialTheme.colorScheme.onSurface, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            WavyLine(
                modifier = Modifier.weight(1f).padding(start = 12.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            itemsIndexed(tracks) { index, track ->
                val isCurrent = (livePlayingTrack != null && livePlayingTrack.uri == track.uri) || (livePlayingTrack == null && index == currentTrackIndex)
                Column(
                    modifier = Modifier
                        .width(136.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onTrackClick(index) }
                ) {
                    Box(
                        modifier = Modifier
                            .size(136.dp)
                            .shadow(if (isCurrent && isPlaying) 12.dp else 4.dp, RoundedCornerShape(12.dp), spotColor = Color.Black.copy(alpha = 0.3f))
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    ) {
                        AsyncImage(
                            model = track.artworkUrl,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                        if (isCurrent && isPlaying) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(8.dp)
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.VolumeUp,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = track.title,
                        color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = track.artist,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Section 3: Artists in Your Rotation
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ShapeBadge(
                shape = "cookie12",
                icon = Icons.Rounded.Person,
                size = 34.dp,
                iconSize = 18.dp,
                shapeAlpha = 0.30f,
                glyphAlpha = 0.90f
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(stringResource(R.string.artists_in_rotation), fontFamily = GoogleSansFlexFamily, color = MaterialTheme.colorScheme.onSurface, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            WavyLine(
                modifier = Modifier.weight(1f).padding(start = 12.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            items(sampleArtists) { artist ->
                var avatarCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
                Column(
                    modifier = Modifier
                        .width(136.dp)
                        .clickable {
                            val rect = avatarCoordinates?.takeIf { it.isAttached }?.let { coords ->
                                val pos = coords.positionInRoot()
                                Rect(pos.x, pos.y, pos.x + coords.size.width, pos.y + coords.size.height)
                            }
                            if (onArtistClickWithRect != null) {
                                onArtistClickWithRect(artist, rect)
                            } else {
                                onArtistClick(artist)
                            }
                        },
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    SquiggleAvatar(
                        imageUrl = artist.artworkUrl,
                        contentDescription = artist.name,
                        modifier = Modifier
                            .size(136.dp)
                            .onGloballyPositioned { coords ->
                                avatarCoordinates = coords
                            },
                        ringColor = MaterialTheme.colorScheme.primary,
                        waves = 20,
                        amplitude = 1.3.dp,
                        strokeWidth = 2.0.dp,
                        gap = 4.0.dp,
                        continuousRotation = isPlaying
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = artist.name,
                        fontFamily = GoogleSansFlexFamily,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(140.dp))
    }
}
