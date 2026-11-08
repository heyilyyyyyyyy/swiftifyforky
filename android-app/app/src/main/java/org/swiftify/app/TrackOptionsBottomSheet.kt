package org.swiftify.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import org.swiftify.app.design.GoogleSansFlexFamily

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackOptionsBottomSheet(
    track: TrackItem,
    isSaved: Boolean,
    onDismiss: () -> Unit,
    onToggleSave: () -> Unit,
    onAddToQueue: () -> Unit,
    onGoToArtist: () -> Unit,
    onGoToAlbum: () -> Unit,
    onAddToPlaylist: (() -> Unit)? = null,
    onRemoveFromPlaylist: (() -> Unit)? = null,
    onShare: (() -> Unit)? = null
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 12.dp)
                    .width(40.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(MaterialTheme.colorScheme.outlineVariant)
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp)
        ) {
            // Header with artwork, title, and artist
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AsyncImage(
                    model = track.artworkUrl,
                    contentDescription = null,
                    modifier = Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Crop
                )
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = track.title,
                        fontFamily = GoogleSansFlexFamily,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = track.artist,
                        fontFamily = GoogleSansFlexFamily,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            HorizontalDivider(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Action: Go to Artist
            TrackOptionRow(
                icon = Icons.Rounded.Person,
                title = "Go to artist",
                subtitle = track.artist,
                onClick = {
                    onDismiss()
                    onGoToArtist()
                }
            )

            // Action: Go to Album
            TrackOptionRow(
                icon = Icons.Rounded.Album,
                title = "Go to album",
                subtitle = track.album.ifEmpty { "Release" },
                onClick = {
                    onDismiss()
                    onGoToAlbum()
                }
            )

            // Action: Add / Remove Favorite
            TrackOptionRow(
                icon = if (isSaved) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                iconTint = if (isSaved) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                title = if (isSaved) "Remove from Liked Songs" else "Save to Liked Songs",
                onClick = {
                    onToggleSave()
                    onDismiss()
                }
            )

            // Action: Add to Queue
            TrackOptionRow(
                icon = Icons.Rounded.QueueMusic,
                title = "Add to Queue",
                onClick = {
                    onDismiss()
                    onAddToQueue()
                }
            )

            // Action: Add to Playlist
            if (onAddToPlaylist != null) {
                TrackOptionRow(
                    icon = Icons.AutoMirrored.Rounded.PlaylistAdd,
                    title = "Add to playlist...",
                    onClick = {
                        onDismiss()
                        onAddToPlaylist()
                    }
                )
            }

            // Action: Remove from this Playlist
            if (onRemoveFromPlaylist != null) {
                TrackOptionRow(
                    icon = Icons.Rounded.DeleteOutline,
                    iconTint = MaterialTheme.colorScheme.error,
                    title = "Remove from this playlist",
                    onClick = {
                        onDismiss()
                        onRemoveFromPlaylist()
                    }
                )
            }

            // Action: Share
            if (onShare != null) {
                TrackOptionRow(
                    icon = Icons.Rounded.Share,
                    title = "Share song",
                    onClick = {
                        onDismiss()
                        onShare()
                    }
                )
            }
        }
    }
}

@Composable
private fun TrackOptionRow(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    iconTint: Color = MaterialTheme.colorScheme.onSurface,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(18.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontFamily = GoogleSansFlexFamily,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium
            )
            if (!subtitle.isNullOrEmpty()) {
                Text(
                    text = subtitle,
                    fontFamily = GoogleSansFlexFamily,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
