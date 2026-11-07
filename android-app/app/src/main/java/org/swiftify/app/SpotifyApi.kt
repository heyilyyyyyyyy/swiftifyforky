package org.swiftify.app

import android.content.Context
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object SpotifyApi {
    private const val TAG = "SpotifyApi"
    private const val CLIENT_ID = "65b708073fc0480ea92a077233ca87bd"
    private const val PREFS_NAME = "swiftify_prefs"

    suspend fun getValidToken(context: Context, forceRefresh: Boolean = false): String? = withContext(Dispatchers.IO) {
        val token = SecureStorage.getAccessToken(context)
        val expiresAt = SecureStorage.getTokenExpiresAt(context)
        val refreshToken = SecureStorage.getRefreshToken(context)

        if (!forceRefresh && !token.isNullOrEmpty() && System.currentTimeMillis() < expiresAt) {
            return@withContext token
        }

        if (refreshToken.isNullOrEmpty()) {
            return@withContext token
        }

        try {
            val conn = URL("https://accounts.spotify.com/api/token").openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            conn.connectTimeout = 8000
            conn.readTimeout = 8000
            conn.doOutput = true
            val body = "grant_type=refresh_token&refresh_token=${Uri.encode(refreshToken)}&client_id=$CLIENT_ID"
            conn.outputStream.write(body.toByteArray())
            if (conn.responseCode in 200..299) {
                val res = conn.inputStream.bufferedReader().readText()
                val json = JSONObject(res)
                val newToken = json.getString("access_token")
                val newRefresh = json.optString("refresh_token")
                val expiresIn = json.optLong("expires_in", 3600L)
                SecureStorage.saveTokens(context, newToken, newRefresh, expiresIn)
                conn.disconnect()
                return@withContext newToken
            }
            conn.disconnect()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to refresh token", e)
        }
        return@withContext token
    }

    suspend fun isTrackSaved(context: Context, trackUri: String): Boolean = withContext(Dispatchers.IO) {
        val trackId = trackUri.removePrefix("spotify:track:").trim()
        if (trackId.isEmpty()) return@withContext false

        var token = getValidToken(context)
        if (token.isNullOrEmpty()) return@withContext false

        for (attempt in 0..1) {
            try {
                val url = URL("https://api.spotify.com/v1/me/tracks/contains?ids=$trackId")
                val conn = url.openConnection() as HttpURLConnection
                conn.connectTimeout = 6000
                conn.readTimeout = 6000
                conn.setRequestProperty("Authorization", "Bearer $token")
                val code = conn.responseCode
                if (code in 200..299) {
                    val text = conn.inputStream.bufferedReader().readText()
                    conn.disconnect()
                    val arr = JSONArray(text)
                    if (arr.length() > 0) {
                        val saved = arr.getBoolean(0)
                        updateLocalLikedCache(context, trackUri, saved)
                        return@withContext saved
                    }
                } else if (code == 401 && attempt == 0) {
                    conn.disconnect()
                    token = getValidToken(context, forceRefresh = true)
                    continue
                }
                conn.disconnect()
            } catch (e: Exception) {
                Log.e(TAG, "isTrackSaved error: ${e.message}")
            }
            break
        }
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val cached = prefs.getStringSet("saved_track_uris", emptySet()) ?: emptySet()
        return@withContext cached.contains(trackUri)
    }

    suspend fun setTrackSaved(context: Context, trackUri: String, saved: Boolean): Boolean = withContext(Dispatchers.IO) {
        val trackId = trackUri.removePrefix("spotify:track:").trim()
        if (trackId.isEmpty()) return@withContext false

        // Optimistically update local cache
        updateLocalLikedCache(context, trackUri, saved)

        var token = getValidToken(context)
        if (token.isNullOrEmpty()) return@withContext false

        for (attempt in 0..1) {
            try {
                val url = URL("https://api.spotify.com/v1/me/tracks?ids=$trackId")
                val conn = url.openConnection() as HttpURLConnection
                conn.connectTimeout = 8000
                conn.readTimeout = 8000
                conn.requestMethod = if (saved) "PUT" else "DELETE"
                conn.setRequestProperty("Authorization", "Bearer $token")
                conn.setRequestProperty("Content-Length", "0")
                val code = conn.responseCode
                Log.d(TAG, "setTrackSaved $trackId saved=$saved -> HTTP $code")
                if (code in 200..299) {
                    conn.disconnect()
                    return@withContext true
                } else if (code == 401 && attempt == 0) {
                    conn.disconnect()
                    token = getValidToken(context, forceRefresh = true)
                    continue
                } else if (code == 429 && attempt == 0) {
                    val retrySec = conn.getHeaderField("Retry-After")?.toLongOrNull() ?: 2L
                    conn.disconnect()
                    kotlinx.coroutines.delay((retrySec.coerceIn(1L, 5L)) * 1000L)
                    continue
                }
                conn.disconnect()
            } catch (e: Exception) {
                Log.e(TAG, "setTrackSaved error", e)
            }
            break
        }
        return@withContext false
    }

    private fun updateLocalLikedCache(context: Context, trackUri: String, saved: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val set = (prefs.getStringSet("saved_track_uris", emptySet()) ?: emptySet()).toMutableSet()
        if (saved) set.add(trackUri) else set.remove(trackUri)
        prefs.edit().putStringSet("saved_track_uris", set).apply()
    }

    suspend fun searchPlaylists(context: Context, query: String): List<PlaylistItem> = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return@withContext emptyList()
        var token = getValidToken(context)
        if (token.isNullOrEmpty()) return@withContext emptyList()

        for (attempt in 0..1) {
            try {
                val encodedQuery = Uri.encode(trimmed)
                val url = URL("https://api.spotify.com/v1/search?q=$encodedQuery&type=playlist&limit=20")
                val conn = url.openConnection() as HttpURLConnection
                conn.connectTimeout = 8000
                conn.readTimeout = 8000
                conn.setRequestProperty("Authorization", "Bearer $token")
                val code = conn.responseCode
                if (code in 200..299) {
                    val text = conn.inputStream.bufferedReader().readText()
                    conn.disconnect()
                    val root = JSONObject(text)
                    val playlistsObj = root.optJSONObject("playlists")
                    val items = playlistsObj?.optJSONArray("items") ?: return@withContext emptyList()
                    val list = mutableListOf<PlaylistItem>()
                    for (i in 0 until items.length()) {
                        val p = items.optJSONObject(i) ?: continue
                        val name = p.optString("name")
                        val uri = p.optString("uri")
                        val ownerObj = p.optJSONObject("owner")
                        val owner = ownerObj?.optString("display_name", "Spotify") ?: "Spotify"
                        val images = p.optJSONArray("images")
                        val artUrl = getBestArtworkUrl(images)
                        if (name.isNotEmpty() && uri.isNotEmpty()) {
                            list.add(PlaylistItem(name = name, owner = owner, artworkUrl = artUrl, uri = uri))
                        }
                    }
                    return@withContext list
                } else if (code == 401 && attempt == 0) {
                    conn.disconnect()
                    token = getValidToken(context, forceRefresh = true)
                    continue
                }
                conn.disconnect()
            } catch (e: Exception) {
                Log.e(TAG, "searchPlaylists error: ${e.message}")
            }
            break
        }
        return@withContext emptyList()
    }

    suspend fun fetchSavedTracks(context: Context, offset: Int = 0, limit: Int = 50): List<TrackItem> = withContext(Dispatchers.IO) {
        var token = getValidToken(context)
        if (token.isNullOrEmpty()) return@withContext emptyList()

        for (attempt in 0..1) {
            try {
                val url = URL("https://api.spotify.com/v1/me/tracks?offset=$offset&limit=$limit")
                val conn = url.openConnection() as HttpURLConnection
                conn.connectTimeout = 8000
                conn.readTimeout = 8000
                conn.setRequestProperty("Authorization", "Bearer $token")
                val code = conn.responseCode
                if (code in 200..299) {
                    val text = conn.inputStream.bufferedReader().readText()
                    conn.disconnect()
                    return@withContext parseTracksResponse(text)
                } else if (code == 401 && attempt == 0) {
                    conn.disconnect()
                    token = getValidToken(context, forceRefresh = true)
                    continue
                }
                conn.disconnect()
            } catch (e: Exception) {
                Log.e(TAG, "fetchSavedTracks error: ${e.message}")
            }
            break
        }
        return@withContext emptyList()
    }

    suspend fun fetchPlaylistTracks(context: Context, playlistId: String, offset: Int = 0, limit: Int = 50): List<TrackItem> = withContext(Dispatchers.IO) {
        var token = getValidToken(context)
        if (token.isNullOrEmpty()) return@withContext emptyList()

        val cleanId = playlistId.removePrefix("spotify:playlist:").trim()
        if (cleanId.isEmpty()) return@withContext emptyList()

        for (attempt in 0..1) {
            try {
                val url = URL("https://api.spotify.com/v1/playlists/$cleanId/tracks?offset=$offset&limit=$limit")
                val conn = url.openConnection() as HttpURLConnection
                conn.connectTimeout = 8000
                conn.readTimeout = 8000
                conn.setRequestProperty("Authorization", "Bearer $token")
                val code = conn.responseCode
                if (code in 200..299) {
                    val text = conn.inputStream.bufferedReader().readText()
                    conn.disconnect()
                    return@withContext parseTracksResponse(text)
                } else if (code == 401 && attempt == 0) {
                    conn.disconnect()
                    token = getValidToken(context, forceRefresh = true)
                    continue
                }
                conn.disconnect()
            } catch (e: Exception) {
                Log.e(TAG, "fetchPlaylistTracks error: ${e.message}")
            }
            break
        }
        return@withContext emptyList()
    }

    private fun parseTracksResponse(jsonStr: String): List<TrackItem> {
        val list = mutableListOf<TrackItem>()
        try {
            val root = JSONObject(jsonStr)
            val items = root.optJSONArray("items") ?: return list
            for (i in 0 until items.length()) {
                val itemObj = items.optJSONObject(i) ?: continue
                val track = itemObj.optJSONObject("track") ?: itemObj
                val name = track.optString("name")
                val uri = track.optString("uri")
                val durationMs = track.optLong("duration_ms", 0L)
                val albumObj = track.optJSONObject("album")
                val albumName = albumObj?.optString("name") ?: ""
                val albumUri = albumObj?.optString("uri") ?: ""
                val images = albumObj?.optJSONArray("images")
                val artUrl = getBestArtworkUrl(images)
                val artistsArr = track.optJSONArray("artists")
                val artistName = if (artistsArr != null && artistsArr.length() > 0) {
                    artistsArr.getJSONObject(0).optString("name")
                } else "Unknown"
                val artistUri = if (artistsArr != null && artistsArr.length() > 0) {
                    artistsArr.getJSONObject(0).optString("uri")
                } else ""

                if (name.isNotEmpty() && uri.isNotEmpty()) {
                    list.add(TrackItem(name, artistName, albumName, artUrl, durationMs, uri, albumUri, artistUri))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "parseTracksResponse error: ${e.message}")
        }
        return list
    }
}
