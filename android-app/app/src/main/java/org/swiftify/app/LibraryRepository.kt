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
internal const val KEY_CACHED_RECENTLY_PLAYED = "cached_recently_played_json"

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
                cacheRecentlyPlayed(tracks)
                Log.d("API", "Loaded ${tracks.size} recently played tracks from Spotify ✓")
            }
        } catch (e: Exception) {
            Log.e("API", "Failed to load recently played", e)
        }
    }

    fun loadCachedRecentlyPlayed() {
        val cached = prefs.getString(KEY_CACHED_RECENTLY_PLAYED, null)
        if (cached != null && recentlyPlayed.isEmpty()) {
            parseAndApplyRecentlyPlayed(cached)
        }
    }

    fun parseAndApplyRecentlyPlayed(jsonStr: String): Boolean {
        try {
            val arr = JSONArray(jsonStr)
            if (arr.length() > 0) {
                val list = mutableListOf<TrackItem>()
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    val uri = o.optString("uri")
                    val title = o.optString("title")
                    if (uri.isNotEmpty() && title.isNotEmpty()) {
                        list.add(
                            TrackItem(
                                title = title,
                                artist = o.optString("artist"),
                                album = o.optString("album"),
                                artworkUrl = o.optString("artworkUrl"),
                                durationMs = o.optLong("durationMs"),
                                uri = uri,
                                albumUri = o.optString("albumUri"),
                                artistUri = o.optString("artistUri")
                            )
                        )
                    }
                }
                if (list.isNotEmpty()) {
                    recentlyPlayed.clear()
                    recentlyPlayed.addAll(list)
                    Log.d("API", "Restored ${list.size} recently played tracks from offline disk cache ✓")
                    return true
                }
            }
        } catch (e: Exception) {
            Log.e("API", "Failed to parse cached recently played", e)
        }
        return false
    }

    fun cacheRecentlyPlayed(tracks: List<TrackItem>) {
        try {
            val arr = JSONArray()
            for (t in tracks) {
                val o = JSONObject()
                o.put("title", t.title)
                o.put("artist", t.artist)
                o.put("album", t.album)
                o.put("artworkUrl", t.artworkUrl)
                o.put("durationMs", t.durationMs)
                o.put("uri", t.uri)
                o.put("albumUri", t.albumUri)
                o.put("artistUri", t.artistUri)
                arr.put(o)
            }
            prefs.edit().putString(KEY_CACHED_RECENTLY_PLAYED, arr.toString()).apply()
        } catch (e: Exception) {
            Log.e("API", "Failed to cache recently played", e)
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

                    // Asynchronously fetch 1st track album art for playlists lacking cover (e.g. "вайб банутый")
                    scope.launch {
                        for (i in 0 until playlists.size) {
                            val pl = playlists[i]
                            if (pl.artworkUrl.isEmpty() && !pl.uri.contains("collection")) {
                                val sampleTracks = SpotifyApi.fetchAllPlaylistTracks(context, pl.uri, maxTracks = 1)
                                val firstArt = sampleTracks.firstOrNull()?.artworkUrl
                                if (!firstArt.isNullOrEmpty()) {
                                    playlists[i] = pl.copy(artworkUrl = firstArt)
                                }
                            }
                        }
                    }
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
                Toast.makeText(context, context.getString(R.string.toast_created_playlist, newPl.name), Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, context.getString(R.string.toast_failed_create_playlist), Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun deletePlaylist(playlist: PlaylistItem) {
        scope.launch {
            val ok = SpotifyApi.deletePlaylist(context, playlist.uri)
            if (ok) {
                playlists.removeAll { it.uri == playlist.uri }
                Toast.makeText(context, context.getString(R.string.toast_deleted_playlist, playlist.name), Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, context.getString(R.string.toast_failed_delete_playlist), Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun addTrackToPlaylist(playlist: PlaylistItem, track: TrackItem) {
        scope.launch {
            val isLikedSongs = playlist.uri == "spotify:collection" ||
                    playlist.uri.contains("collection") ||
                    playlist.name.equals("Favourite Songs", ignoreCase = true) ||
                    playlist.name.equals("Любимые треки", ignoreCase = true)
            val ok = if (isLikedSongs) {
                SpotifyApi.setTrackSaved(context, track.uri, true)
            } else {
                SpotifyApi.addTrackToPlaylist(context, playlist.uri, track.uri)
            }
            if (ok) {
                Toast.makeText(context, context.getString(R.string.toast_added_to_playlist, track.title, playlist.name), Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, context.getString(R.string.toast_failed_add_to_playlist), Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun removeTrackFromPlaylist(playlist: PlaylistItem, track: TrackItem) {
        scope.launch {
            val isLikedSongs = playlist.uri == "spotify:collection" ||
                    playlist.uri.contains("collection") ||
                    playlist.name.equals("Favourite Songs", ignoreCase = true) ||
                    playlist.name.equals("Любимые треки", ignoreCase = true)
            val ok = if (isLikedSongs) {
                SpotifyApi.setTrackSaved(context, track.uri, false)
            } else {
                SpotifyApi.removeTrackFromPlaylist(context, playlist.uri, track.uri)
            }
            if (ok) {
                Toast.makeText(context, context.getString(R.string.toast_removed_from_playlist, track.title, playlist.name), Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, context.getString(R.string.toast_failed_remove_from_playlist), Toast.LENGTH_SHORT).show()
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
