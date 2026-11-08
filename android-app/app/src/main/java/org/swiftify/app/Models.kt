package org.swiftify.app

import androidx.compose.ui.geometry.Rect
import org.json.JSONArray

// ─── Data Models ─────────────────────────────────────────────────────────────

data class TrackItem(
    val title: String,
    val artist: String,
    val album: String,
    val artworkUrl: String,
    val durationMs: Long,
    val uri: String,
    val albumUri: String = "",
    val artistUri: String = ""
)

data class IndexedTrack(val track: TrackItem, val key: String)

data class PlaylistItem(
    val name: String,
    val owner: String,
    val artworkUrl: String,
    val uri: String = ""
) {
    val subtitle: String get() = owner
    val coverUrl: String get() = artworkUrl
}

data class ArtistItem(
    val name: String,
    val artworkUrl: String,
    val uri: String = ""
)

fun getBestArtworkUrl(images: JSONArray?): String {
    if (images == null || images.length() == 0) return ""
    var bestUrl = ""
    var bestWidth = -1
    for (i in 0 until images.length()) {
        val obj = images.optJSONObject(i) ?: continue
        val url = obj.optString("url")
        val width = obj.optInt("width", 0)
        if (width > bestWidth && url.isNotEmpty()) {
            bestWidth = width
            bestUrl = url
        }
    }
    return if (bestUrl.isNotEmpty()) bestUrl else images.optJSONObject(0)?.optString("url") ?: ""
}

fun formatTime(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%d:%02d", minutes, seconds)
}

// ─── Auth State ───────────────────────────────────────────────────────────────

sealed class AuthState {
    object Loading : AuthState()
    object LoggedOut : AuthState()
    object Connecting : AuthState()
    object LoggedIn : AuthState()
}

// ─── Navigation Destinations ──────────────────────────────────────────────────

sealed class ScreenDestination {
    data class Home(val id: Int = 0) : ScreenDestination()
    data class Search(val id: Int = 0) : ScreenDestination()
    data class Settings(val id: Int = 0) : ScreenDestination()
    data class AllPlaylists(val id: Int = 0) : ScreenDestination()
    data class Artist(val artist: ArtistItem) : ScreenDestination()
    data class Album(val uri: String, val title: String, val artworkUrl: String) : ScreenDestination()
    data class Playlist(val playlist: PlaylistItem) : ScreenDestination()
}

data class HeroFlightData(
    val imageUrl: String,
    val name: String,
    val fromRect: Rect
)

// ─── Sample Fallback Data ────────────────────────────────────────────────────

val samplePlaylists = listOf(
    PlaylistItem("вайб банутый", "alert('Hey, I Love You!')", "https://i.scdn.co/image/ab67616d0000b27364c8b41faf576a0bab551fb9", "spotify:playlist:15tGlcjsEAxEAqhW57w5yf"),
    PlaylistItem("shma", "свегпрогуччекилиррпсихотер...", "https://i.scdn.co/image/ab67616d0000b27301b854c4411bca4150f2ab50", "spotify:playlist:1DedHxjtlx3vGoj1D6Ihsr"),
    PlaylistItem("When I feel like the alpha 😈🐺", "Leigha ❣️", "https://i.scdn.co/image/ab67616d0000b273187c3b1239c0faeeb0a66d8e", "spotify:playlist:4nR6ftxgWGeehg8Ea2ClgW"),
    PlaylistItem("glaive and shit", "alert('Hey, I Love You!')", "https://i.scdn.co/image/ab67616d0000b273d4ee79f87cbb5bbfa4b9d031", "spotify:playlist:5eYCAj4nXl5BUugqevozIh"),
    PlaylistItem("Your All-Time Top Songs", "Spotify", "https://pickasso.spotifycdn.com/image/ab67c0de0000deef/dt/v1/img/atts/atts/2017/en", "spotify:playlist:37i9dQZF1CKMFBxUbxRIAg"),
    PlaylistItem("Your Top Songs 2025", "Spotify", "https://i.scdn.co/image/ab67706f00000002b8d5231c6f1d244975d04586", "spotify:playlist:37i9dQZEVXcXHWVVT0lfDq")
)

val sampleArtists = listOf(
    ArtistItem("лиззз", "https://i.scdn.co/image/ab6761610000e5eb408ed62ccf98127a1689c396", "spotify:artist:4w6m5DoTABuW3kJgVb9dUo"),
    ArtistItem("brakence", "https://i.scdn.co/image/ab6761610000e5ebe1dce3b56c044ae242ea558f", "spotify:artist:5JZPSVFMUk4HGDdx68lN77"),
    ArtistItem("SEREBRO", "https://i.scdn.co/image/ab6761610000e5eb42a3f73b48158b68f1728970", "spotify:artist:1Ulwacsns9Me7dz4OVwkiQ"),
    ArtistItem("glaive", "https://i.scdn.co/image/ab6761610000e5ebf400be855a03bee882dc9dc3", "spotify:artist:4cJKDGSv4Dz9QycXYmo565"),
    ArtistItem("Midix", "https://i.scdn.co/image/ab6761610000e5eb26c2c1b9c094b22357fcb9c7", "spotify:artist:6Y5o021VaeAm5iRncZhLei")
)
