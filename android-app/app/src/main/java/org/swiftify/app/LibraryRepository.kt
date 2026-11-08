package org.swiftify.app

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import android.widget.Toast
import androidx.compose.runtime.snapshots.SnapshotStateList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

internal const val KEY_CACHED_PLAYLISTS = "cached_playlists_json"

/**
 * Network-backed library data: recently played tracks, playlists and playlist
 * mutations. Owns no playback state; Compose lists are passed in so the UI
 * keeps observing the same snapshots it always did.
 */
class LibraryRepository(
    private val context: Context,
    private val prefs: SharedPreferences,
    private val scope: CoroutineScope,
    private val recentlyPlayed: SnapshotStateList<TrackItem>,
    private val playlists: SnapshotStateList<PlaylistItem>
) {
    suspend fun loadRecentlyPlayed() {
        try {
            val token = SpotifyApi.getValidToken(context) ?: return
            val tracks = withContext(Dispatchers.IO) {
                val conn = URL("https://api.spotify.com/v1/me/player/recently-played?limit=20")
                    .openConnection() as HttpURLConnection
                conn.connectTimeout = 8000
                conn.readTimeout = 8000
                conn.setRequestProperty("Authorization", "Bearer $token")
                val code = conn.responseCode
                if (code == 401) {
                    conn.disconnect()
                    val refreshedToken = SpotifyApi.getValidToken(context, forceRefresh = true)
                    if (refreshedToken == null) return@withContext emptyList<TrackItem>()
                    val retryConn = URL("https://api.spotify.com/v1/me/player/recently-played?limit=20")
                        .openConnection() as HttpURLConnection
                    retryConn.connectTimeout = 8000
                    retryConn.readTimeout = 8000
                    retryConn.setRequestProperty("Authorization", "Bearer $refreshedToken")
                    if (retryConn.responseCode !in 200..299) {
                        retryConn.disconnect()
                        return@withContext emptyList<TrackItem>()
                    }
                    val retryJson = JSONObject(retryConn.inputStream.bufferedReader().readText())
                    retryConn.disconnect()
                    val retryItems = retryJson.optJSONArray("items") ?: return@withContext emptyList<TrackItem>()
                    return@withContext parseRecentlyPlayedItems(retryItems)
                }
                if (code !in 200..299) {
                    conn.disconnect()
                    return@withContext emptyList<TrackItem>()
                }
                val json = JSONObject(conn.inputStream.bufferedReader().readText())
                conn.disconnect()
                val items = json.optJSONArray("items") ?: return@withContext emptyList<TrackItem>()
                parseRecentlyPlayedItems(items)
            }
            if (tracks.isNotEmpty()) {
                recentlyPlayed.clear()
                recentlyPlayed.addAll(tracks)
                Log.d("API", "Loaded ${tracks.size} recently played tracks from Spotify ✓")
            }
        } catch (e: Exception) {
            Log.e("API", "Failed to load recently played", e)
        }
    }

    fun loadUserPlaylists() {
        // Load immediately from offline disk cache if available (0 ms delay)
        val cached = prefs.getString(KEY_CACHED_PLAYLISTS, null)
        if (cached != null && playlists.isEmpty()) {
            parseAndApplyPlaylists(cached)
        }

        // Fetch fresh list in background (Stale-While-Revalidate)
        SpotifyBridge.fetchUserPlaylists { ok, payload ->
            if (ok && payload.isNotEmpty()) {
                if (parseAndApplyPlaylists(payload)) {
                    prefs.edit().putString(KEY_CACHED_PLAYLISTS, payload).apply()
                }
            }
        }
    }

    fun parseAndApplyPlaylists(jsonStr: String): Boolean {
        try {
            val obj = JSONObject(jsonStr)
            val arr = obj.optJSONArray("playlists")
            if (arr != null && arr.length() > 0) {
                val parsed = mutableListOf<PlaylistItem>()
                for (i in 0 until arr.length()) {
                    val p = arr.getJSONObject(i)
                    val uri = p.optString("uri")
                    val name = p.optString("name")
                    val owner = p.optString("owner", "Spotify")
                    val artwork = p.optString("artwork_url")
                    if (uri.isNotEmpty() && name.isNotEmpty()) {
                        parsed.add(PlaylistItem(name, owner, artwork, uri))
                    }
                }
                if (parsed.isNotEmpty()) {
                    playlists.clear()
                    playlists.addAll(parsed)
                    Log.d("Playlists", "Loaded ${parsed.size} user playlists")
                    return true
                }
            }
        } catch (e: Exception) {
            Log.e("Playlists", "Failed to parse playlists", e)
        }
        return false
    }

    fun createPlaylist(name: String, description: String) {
        scope.launch {
            val newPl = SpotifyApi.createPlaylist(context, name, description)
            if (newPl != null) {
                playlists.add(0, newPl)
                Toast.makeText(context, "Created playlist '${newPl.name}'", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "Failed to create playlist", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun deletePlaylist(playlist: PlaylistItem) {
        scope.launch {
            val ok = SpotifyApi.deletePlaylist(context, playlist.uri)
            if (ok) {
                playlists.removeAll { it.uri == playlist.uri }
                Toast.makeText(context, "Deleted playlist '${playlist.name}'", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "Failed to delete playlist", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun addTrackToPlaylist(playlist: PlaylistItem, track: TrackItem) {
        scope.launch {
            val ok = SpotifyApi.addTrackToPlaylist(context, playlist.uri, track.uri)
            if (ok) {
                Toast.makeText(context, "Added '${track.title}' to ${playlist.name}", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "Failed to add track to playlist", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun removeTrackFromPlaylist(playlist: PlaylistItem, track: TrackItem) {
        scope.launch {
            val ok = SpotifyApi.removeTrackFromPlaylist(context, playlist.uri, track.uri)
            if (ok) {
                Toast.makeText(context, "Removed '${track.title}' from ${playlist.name}", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "Failed to remove track from playlist", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun parseRecentlyPlayedItems(items: JSONArray): List<TrackItem> {
        return (0 until items.length()).mapNotNull { i ->
            val item = items.optJSONObject(i) ?: return@mapNotNull null
            val track = item.optJSONObject("track") ?: return@mapNotNull null
            val artistsArr = track.optJSONArray("artists")
            val artists = if (artistsArr != null) {
                (0 until artistsArr.length()).joinToString(", ") { j ->
                    artistsArr.optJSONObject(j)?.optString("name") ?: ""
                }
            } else ""
            val alb = track.optJSONObject("album")
            val images = alb?.optJSONArray("images")
            val artUrl = getBestArtworkUrl(images)
            val albumUri = alb?.optString("uri") ?: ""
            val artistUri = artistsArr?.optJSONObject(0)?.optString("uri") ?: ""
            TrackItem(
                title = track.optString("name"),
                artist = artists,
                album = alb?.optString("name") ?: "",
                artworkUrl = artUrl,
                durationMs = track.optLong("duration_ms"),
                uri = track.optString("uri"),
                albumUri = albumUri,
                artistUri = artistUri
            )
        }
    }
}
