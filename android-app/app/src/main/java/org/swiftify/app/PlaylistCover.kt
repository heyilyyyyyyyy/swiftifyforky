package org.swiftify.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage

/**
 * Standardized, expressive playlist cover resolver matching Spotify & Swiftify Desktop:
 * 1. Liked Songs ("Favourite Songs" / "spotify:collection"): Iconic purple-to-blue gradient with centered white heart.
 * 2. Playlists with custom cover: Full-resolution custom cover artwork.
 * 3. Playlists without cover (e.g. "вайб банутый"): Dynamic 2x2 collage of first 4 distinct track covers or 1st track fallback.
 * 4. Empty playlist: Expressive themed placeholder with musical glyph.
 */
@Composable
fun PlaylistCover(
    playlist: PlaylistItem,
    modifier: Modifier = Modifier,
    tracks: List<TrackItem> = emptyList(),
    shape: Shape = RoundedCornerShape(12.dp),
    iconSize: Dp = 32.dp
) {
    val isLikedSongs = playlist.uri == "spotify:collection" ||
            playlist.uri.contains("collection") ||
            playlist.name.equals("Favourite Songs", ignoreCase = true) ||
            playlist.name.equals("Любимые треки", ignoreCase = true)

    if (isLikedSongs) {
        Box(
            modifier = modifier
                .clip(shape)
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            Color(0xFF450AF5),
                            Color(0xFF8E2DE2),
                            Color(0xFF7B2CBF)
                        )
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.Favorite,
                contentDescription = playlist.name,
                tint = Color.White,
                modifier = Modifier.size(iconSize)
            )
        }
        return
    }

    val directCover = playlist.artworkUrl.takeIf { it.isNotEmpty() }
    val trackCovers = remember(tracks) {
        tracks.map { it.artworkUrl }.filter { it.isNotEmpty() }.distinct().take(4)
    }

    when {
        directCover != null -> {
            AsyncImage(
                model = directCover,
                contentDescription = playlist.name,
                modifier = modifier.clip(shape),
                contentScale = ContentScale.Crop
            )
        }
        trackCovers.size >= 4 -> {
            Box(modifier = modifier.clip(shape)) {
                Column(modifier = Modifier.fillMaxSize()) {
                    Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        AsyncImage(
                            model = trackCovers[0],
                            contentDescription = null,
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                            contentScale = ContentScale.Crop
                        )
                        AsyncImage(
                            model = trackCovers[1],
                            contentDescription = null,
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                            contentScale = ContentScale.Crop
                        )
                    }
                    Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        AsyncImage(
                            model = trackCovers[2],
                            contentDescription = null,
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                            contentScale = ContentScale.Crop
                        )
                        AsyncImage(
                            model = trackCovers[3],
                            contentDescription = null,
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                            contentScale = ContentScale.Crop
                        )
                    }
                }
            }
        }
        trackCovers.isNotEmpty() -> {
            AsyncImage(
                model = trackCovers.first(),
                contentDescription = playlist.name,
                modifier = modifier.clip(shape),
                contentScale = ContentScale.Crop
            )
        }
        else -> {
            Box(
                modifier = modifier
                    .clip(shape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.MusicNote,
                    contentDescription = playlist.name,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(iconSize)
                )
            }
        }
    }
}
