package org.swiftify.app

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.*
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.ServerSocket
import java.net.URL
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.Calendar
import java.util.UUID
import kotlin.math.PI
import kotlin.math.sin

// ─── Data ────────────────────────────────────────────────────────────────────

data class TrackItem(
    val title: String,
    val artist: String,
    val album: String,
    val artworkUrl: String,
    val durationMs: Long,
    val uri: String
)

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

fun getBestArtworkUrl(images: org.json.JSONArray?): String {
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

// ─── PKCE helpers ─────────────────────────────────────────────────────────────

private fun generateCodeVerifier(): String {
    val bytes = ByteArray(64)
    SecureRandom().nextBytes(bytes)
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
}

private fun generateCodeChallenge(verifier: String): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII))
    return Base64.getUrlEncoder().withoutPadding().encodeToString(digest)
}

// ─── Spotify OAuth constants matching Swiftify desktop ─────────────────────────

private const val CLIENT_ID = "65b708073fc0480ea92a077233ca87bd"
private const val REDIRECT_PORT = 8898
private const val REDIRECT_URI = "http://127.0.0.1:8898/login"
private const val SCOPES = "streaming user-read-playback-state user-read-recently-played user-library-read user-library-modify playlist-read-private playlist-read-collaborative playlist-modify-private playlist-modify-public user-follow-read user-read-private"
private const val PREFS_NAME = "swiftify_prefs"
private const val KEY_LOGIN_BLOB = "login_blob"
private const val KEY_ACCESS_TOKEN = "access_token"
private const val KEY_REFRESH_TOKEN = "refresh_token"
private const val KEY_TOKEN_EXPIRES_AT = "token_expires_at"
private const val KEY_CACHED_PLAYLISTS = "cached_playlists_json"

private data class PollResult(val pos: Long, val dur: Long, val trackJson: String?, val trackEnded: Boolean)

// ─── MainActivity ─────────────────────────────────────────────────────────────

class MainActivity : ComponentActivity() {

    private lateinit var prefs: SharedPreferences
    private var codeVerifier: String = ""
    private var oauthStateParam: String = ""
    private var authServer: ServerSocket? = null

    // Compose states
    private val authState = mutableStateOf<AuthState>(AuthState.Loading)
    private val accessTokenState = mutableStateOf<String?>(null)
    private val tracksState = mutableStateListOf<TrackItem>()
    private val playlistQueue = mutableListOf<TrackItem>()
    private val customQueue = mutableListOf<TrackItem>()
    private var currentPlaylistIndex: Int = 0
    private val userPlaylistsState = mutableStateListOf<PlaylistItem>()
    private val currentTrackIndex = mutableIntStateOf(0)
    private val livePlayingTrack = mutableStateOf<TrackItem?>(null)
    private val isPlaying = mutableStateOf(false)
    private val progressMs = mutableLongStateOf(0L)
    private val isShuffle = mutableStateOf(false)
    private val repeatMode = mutableIntStateOf(0) // 0 = off, 1 = repeat all, 2 = repeat one
    private val originalQueueBeforeShuffle = mutableListOf<TrackItem>()
    private val stopAtEndOfQueue = mutableStateOf(false)
    private val connectEnabled = mutableStateOf(false)
    private val crossfadeEnabled = mutableStateOf(false)
    private val crossfadeSeconds = mutableIntStateOf(5)
    private val isCrossfading = mutableStateOf(false)
    private val statusMsg = mutableStateOf("")

    private var playbackTickerJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private fun getSpotifyDeviceId(): String {
        var id = prefs.getString("spotify_device_id", null)
        if (id == null) {
            id = UUID.randomUUID().toString()
            prefs.edit().putString("spotify_device_id", id).apply()
        }
        return id
    }

    private fun getDeviceName(): String {
        val mfr = Build.MANUFACTURER.replaceFirstChar { it.uppercase() }
        val model = Build.MODEL
        return if (model.startsWith(mfr, ignoreCase = true)) model else "$mfr $model"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        // Request runtime notification permission on Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 101)
            }
        }

        // Instant playlists from disk cache
        prefs.getString(KEY_CACHED_PLAYLISTS, null)?.let {
            parseAndApplyPlaylists(it)
        }

        val devName = getDeviceName()
        SpotifyBridge.init(devName)

        // Apply startup audio, cache & connect settings
        val quality = prefs.getInt("audio_quality_kbps", 320)
        SpotifyBridge.setStreamQuality(quality)
        val norm = prefs.getBoolean("volume_norm", true)
        SpotifyBridge.setVolumeNormalization(norm)
        val connect = prefs.getBoolean("spotify_connect_enabled", false)
        connectEnabled.value = connect
        crossfadeEnabled.value = prefs.getBoolean("crossfade_enabled", false)
        crossfadeSeconds.intValue = prefs.getInt("crossfade_seconds", 5)

        val audioCacheDir = File(cacheDir, "spotify_audio_cache").apply { mkdirs() }
        SpotifyBridge.setAudioCache(audioCacheDir.absolutePath, 1024L * 1024 * 1024)

        // Connect media session & hardware Bluetooth events to playback controller
        PlaybackService.listener = object : PlaybackEventListener {
            override fun onPlay() {
                runOnUiThread {
                    isPlaying.value = true
                    SpotifyBridge.play()
                    syncPlaybackServiceNotification()
                }
            }
            override fun onPause() {
                runOnUiThread {
                    isPlaying.value = false
                    SpotifyBridge.pause()
                    syncPlaybackServiceNotification()
                }
            }
            override fun onNext() {
                runOnUiThread {
                    nextTrack(isAutoAdvance = false)
                }
            }
            override fun onPrevious() {
                runOnUiThread {
                    prevTrack()
                }
            }
            override fun onSeekTo(positionMs: Long) {
                runOnUiThread {
                    progressMs.longValue = positionMs
                    SpotifyBridge.seek(positionMs)
                    syncPlaybackServiceNotification(positionMs)
                }
            }
        }

        setupBridgeCallbacks()
        startPlaybackTicker()

        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Color(0xFFF2A385),
                    background = Color(0xFF110F0E),
                    surface = Color(0xFF1F1A18),
                    surfaceVariant = Color(0xFF28221F),
                    onBackground = Color(0xFFF7F2EE),
                    onSurface = Color(0xFFF7F2EE)
                )
            ) {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    SwiftifyApp(
                        authState = authState.value,
                        tracks = tracksState,
                        userPlaylists = userPlaylistsState,
                        currentTrackIndex = currentTrackIndex.intValue,
                        livePlayingTrack = livePlayingTrack.value,
                        isPlaying = isPlaying.value,
                        progressMs = progressMs.longValue,
                        connectEnabled = connectEnabled.value,
                        statusMsg = statusMsg.value,
                        accessToken = accessTokenState.value,
                        onLoginClick = { startSpotifyLogin() },
                        onTrackClick = { idx -> onTrackClick(idx) },
                        onTrackSelect = { track, contextList -> playTrackItem(track, contextList) },
                        onAddToQueue = { track -> addToQueue(track) },
                        onPlayPause = { togglePlay() },
                        onNext = { nextTrack(isAutoAdvance = false) },
                        onPrev = { prevTrack() },
                        onSeek = { ms -> progressMs.longValue = ms; SpotifyBridge.seek(ms) },
                        onConnectToggle = { v ->
                            connectEnabled.value = v
                            prefs.edit().putBoolean("spotify_connect_enabled", v).apply()
                            SpotifyBridge.setSpotifyConnect(v, devName)
                        },
                        onSetStopAtEnd = { stopAtEndOfQueue.value = it },
                        isShuffle = isShuffle.value,
                        repeatMode = repeatMode.intValue,
                        onToggleShuffle = { toggleShuffle() },
                        onToggleRepeat = { toggleRepeat() },
                        onCrossfadeChanged = { enabled, secs ->
                            crossfadeEnabled.value = enabled
                            crossfadeSeconds.intValue = secs
                            preloadUpcomingTrack()
                        }
                    )
                }
            }
        }

        // Check for stored credentials
        val storedLogin = prefs.getString(KEY_LOGIN_BLOB, null)
        val storedToken = prefs.getString(KEY_ACCESS_TOKEN, null)
        accessTokenState.value = storedToken
        refreshAccessToken()

        if (storedLogin != null) {
            authState.value = AuthState.Connecting
            statusMsg.value = "Reconnecting stored session..."
            SpotifyBridge.connectStored(storedLogin)
        } else if (storedToken != null) {
            authState.value = AuthState.Connecting
            statusMsg.value = "Connecting with token..."
            SpotifyBridge.connect(storedToken)
        } else {
            authState.value = AuthState.LoggedOut
            loadSampleTracks()
        }
    }

    fun refreshAccessToken(force: Boolean = false, onComplete: ((String?) -> Unit)? = null) {
        val refreshToken = prefs.getString(KEY_REFRESH_TOKEN, null)
        if (refreshToken.isNullOrEmpty()) {
            onComplete?.invoke(accessTokenState.value)
            return
        }
        val expiresAt = prefs.getLong(KEY_TOKEN_EXPIRES_AT, 0L)
        if (!force && System.currentTimeMillis() < expiresAt) {
            onComplete?.invoke(accessTokenState.value)
            return
        }
        scope.launch(Dispatchers.IO) {
            try {
                val conn = URL("https://accounts.spotify.com/api/token").openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                conn.connectTimeout = 10000
                conn.readTimeout = 10000
                conn.doOutput = true
                val body = "grant_type=refresh_token&refresh_token=${Uri.encode(refreshToken)}&client_id=$CLIENT_ID"
                conn.outputStream.write(body.toByteArray())
                if (conn.responseCode in 200..299) {
                    val res = conn.inputStream.bufferedReader().readText()
                    val json = JSONObject(res)
                    val newToken = json.getString("access_token")
                    val newRefresh = json.optString("refresh_token")
                    val expiresIn = json.optLong("expires_in", 3600L)
                    val editor = prefs.edit().putString(KEY_ACCESS_TOKEN, newToken)
                    if (newRefresh.isNotEmpty()) {
                        editor.putString(KEY_REFRESH_TOKEN, newRefresh)
                    }
                    editor.putLong(KEY_TOKEN_EXPIRES_AT, System.currentTimeMillis() + (expiresIn - 60) * 1000)
                    editor.apply()
                    withContext(Dispatchers.Main) {
                        accessTokenState.value = newToken
                        onComplete?.invoke(newToken)
                    }
                } else {
                    withContext(Dispatchers.Main) { onComplete?.invoke(accessTokenState.value) }
                }
                conn.disconnect()
            } catch (e: Exception) {
                Log.e("Auth", "Token refresh error", e)
                withContext(Dispatchers.Main) { onComplete?.invoke(accessTokenState.value) }
            }
        }
    }

    private fun startSpotifyLogin() {
        try {
            codeVerifier = generateCodeVerifier()
            val challenge = generateCodeChallenge(codeVerifier)
            oauthStateParam = java.util.UUID.randomUUID().toString()

            // Start loopback HTTP server on 127.0.0.1:8898
            startLocalRedirectServer()

            val url = Uri.Builder()
                .scheme("https")
                .authority("accounts.spotify.com")
                .path("/authorize")
                .appendQueryParameter("client_id", CLIENT_ID)
                .appendQueryParameter("response_type", "code")
                .appendQueryParameter("redirect_uri", REDIRECT_URI)
                .appendQueryParameter("scope", SCOPES)
                .appendQueryParameter("state", oauthStateParam)
                .appendQueryParameter("code_challenge_method", "S256")
                .appendQueryParameter("code_challenge", challenge)
                .build()

            authState.value = AuthState.Connecting
            statusMsg.value = "Waiting for authorization in browser..."
            CustomTabsIntent.Builder().build().launchUrl(this, url)
        } catch (e: Exception) {
            Log.e("Auth", "Failed to start login", e)
            statusMsg.value = "Login init failed: ${e.message}"
        }
    }

    private fun startLocalRedirectServer() {
        try {
            authServer?.close()
        } catch (_: Exception) {}

        scope.launch(Dispatchers.IO) {
            try {
                val server = ServerSocket(REDIRECT_PORT, 1, InetAddress.getByName("127.0.0.1"))
                server.soTimeout = 120_000 // 2 min timeout
                authServer = server
                Log.d("AuthServer", "Listening on http://127.0.0.1:$REDIRECT_PORT/login")

                val socket = server.accept()
                val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
                val requestLine = reader.readLine() ?: ""
                Log.d("AuthServer", "Request: $requestLine")

                // Parse: GET /login?code=... HTTP/1.1
                val urlPart = requestLine.split(" ").getOrNull(1) ?: ""
                val uri = Uri.parse("http://127.0.0.1$urlPart")
                val code = uri.getQueryParameter("code")
                val error = uri.getQueryParameter("error")

                val responseHtml = """
                    <!doctype html><html><head><meta charset="utf-8">
                    <title>Swiftify</title>
                    <style>body{font-family:sans-serif;background:#161413;color:#fff;display:flex;align-items:center;justify-content:center;height:100vh;margin:0;text-align:center;}h2{color:#F2A385;}</style>
                    </head><body><div><h2>✓ Signed In to Swiftify!</h2><p>You can return to the Swiftify app now.</p></div></body></html>
                """.trimIndent()

                val responseBytes = responseHtml.toByteArray(Charsets.UTF_8)
                val out = socket.getOutputStream()
                out.write("HTTP/1.1 200 OK\r\n".toByteArray())
                out.write("Content-Type: text/html; charset=utf-8\r\n".toByteArray())
                out.write("Content-Length: ${responseBytes.size}\r\n".toByteArray())
                out.write("Connection: close\r\n\r\n".toByteArray())
                out.write(responseBytes)
                out.flush()
                socket.close()
                server.close()

                withContext(Dispatchers.Main) {
                    if (code != null) {
                        Log.d("AuthServer", "Captured authorization code!")
                        statusMsg.value = "Exchanging code for token..."
                        scope.launch { exchangeCodeForToken(code) }
                    } else {
                        Log.e("AuthServer", "Auth failed or cancelled: $error")
                        authState.value = AuthState.LoggedOut
                        statusMsg.value = "Auth cancelled: $error"
                    }
                }
            } catch (e: Exception) {
                Log.e("AuthServer", "Server exception", e)
                withContext(Dispatchers.Main) {
                    if (authState.value == AuthState.Connecting) {
                        authState.value = AuthState.LoggedOut
                        statusMsg.value = "Auth timeout or cancelled"
                    }
                }
            }
        }
    }

    private suspend fun exchangeCodeForToken(code: String) {
        try {
            val token = withContext(Dispatchers.IO) {
                val conn = URL("https://accounts.spotify.com/api/token")
                    .openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                conn.connectTimeout = 10000
                conn.readTimeout = 10000
                conn.doOutput = true
                val body = "grant_type=authorization_code" +
                        "&code=${Uri.encode(code)}" +
                        "&redirect_uri=${Uri.encode(REDIRECT_URI)}" +
                        "&client_id=$CLIENT_ID" +
                        "&code_verifier=${Uri.encode(codeVerifier)}"
                conn.outputStream.write(body.toByteArray())
                val response = conn.inputStream.bufferedReader().readText()
                conn.disconnect()
                val json = JSONObject(response)
                val token = json.getString("access_token")
                val refreshToken = json.optString("refresh_token")
                val expiresIn = json.optLong("expires_in", 3600L)
                val editor = prefs.edit().putString(KEY_ACCESS_TOKEN, token)
                if (refreshToken.isNotEmpty()) {
                    editor.putString(KEY_REFRESH_TOKEN, refreshToken)
                }
                editor.putLong(KEY_TOKEN_EXPIRES_AT, System.currentTimeMillis() + (expiresIn - 60) * 1000)
                editor.apply()
                accessTokenState.value = token
                token
            }
            Log.d("Auth", "Access token acquired (${token.length} chars)")
            statusMsg.value = "Connecting audio core..."
            SpotifyBridge.connect(token)
        } catch (e: Exception) {
            Log.e("Auth", "Token exchange failed", e)
            authState.value = AuthState.LoggedOut
            statusMsg.value = "Token exchange failed: ${e.message}"
        }
    }

    private fun setupBridgeCallbacks() {
        SpotifyBridge.onConnectSuccess = { loginBlob ->
            Log.d("Auth", "Connected to Spotify! Login blob length=${loginBlob.length}")
            prefs.edit().putString(KEY_LOGIN_BLOB, loginBlob).apply()
            authState.value = AuthState.LoggedIn
            statusMsg.value = "Connected to Spotify ✓"
            // Enable Spotify Connect now that session is authenticated
            SpotifyBridge.setSpotifyConnect(connectEnabled.value, getDeviceName())
            startPlaybackTicker()
            scope.launch {
                loadRecentlyPlayed()
                loadUserPlaylists()
            }
        }
        SpotifyBridge.onConnectError = { error ->
            Log.e("Auth", "Core connect error: $error")
            prefs.edit().remove(KEY_LOGIN_BLOB).apply()
            authState.value = AuthState.LoggedOut
            statusMsg.value = "Session error: $error"
        }
        SpotifyBridge.onPlayTrackError = { error ->
            Log.e("Playback", "Play track error: $error")
            statusMsg.value = "Playback error: $error"
        }
    }

    private fun startPlaybackTicker() {
        playbackTickerJob?.cancel()
        playbackTickerJob = scope.launch(Dispatchers.IO) {
            while (isActive) {
                var pollDelay = 250L
                if (isPlaying.value) {
                    val pos = SpotifyBridge.playbackPositionMs()
                    val dur = SpotifyBridge.playbackDurationMs()
                    val trackEnded = SpotifyBridge.takeEndOfTrack()

                    // When near the end of track (< 5 seconds), poll fast at 60ms for zero-latency gapless transition
                    if (dur > 0 && dur - pos <= 5000L) {
                        pollDelay = 60L
                    }

                    withContext(Dispatchers.Main) {
                        if (dur > 0) {
                            progressMs.longValue = pos
                        }

                        if (trackEnded) {
                            Log.d("Playback", "Core signaled end of track -> advancing to next track immediately")
                            nextTrack(isAutoAdvance = true)
                        } else if (crossfadeEnabled.value && !isCrossfading.value && dur > 10000L) {
                            val crossMs = crossfadeSeconds.intValue * 1000L
                            if (pos >= dur - crossMs && pos < dur) {
                                val upcoming = getUpcomingTrack()
                                if (upcoming != null) {
                                    isCrossfading.value = true
                                    Log.d("Playback", "Crossfade threshold reached! Overlapping with '${upcoming.title}' (${crossMs}ms)")
                                    SpotifyBridge.crossfadeTrack(upcoming.uri, crossMs)
                                    advanceUpcomingState(upcoming)
                                }
                            }
                        }

                        syncPlaybackServiceNotification(pos, dur)
                    }
                }
                delay(pollDelay)
            }
        }
    }

    private suspend fun loadRecentlyPlayed() {
        try {
            val token = prefs.getString(KEY_ACCESS_TOKEN, null) ?: return
            val tracks = withContext(Dispatchers.IO) {
                val conn = URL("https://api.spotify.com/v1/me/player/recently-played?limit=20")
                    .openConnection() as HttpURLConnection
                conn.connectTimeout = 8000
                conn.readTimeout = 8000
                conn.setRequestProperty("Authorization", "Bearer $token")
                val code = conn.responseCode
                if (code == 401) {
                    conn.disconnect()
                    refreshAccessToken(force = true)
                    return@withContext emptyList<TrackItem>()
                }
                if (code !in 200..299) {
                    conn.disconnect()
                    return@withContext emptyList<TrackItem>()
                }
                val json = JSONObject(conn.inputStream.bufferedReader().readText())
                conn.disconnect()
                val items = json.optJSONArray("items") ?: return@withContext emptyList<TrackItem>()
                (0 until items.length()).mapNotNull { i ->
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
                    TrackItem(
                        title = track.optString("name"),
                        artist = artists,
                        album = alb?.optString("name") ?: "",
                        artworkUrl = artUrl,
                        durationMs = track.optLong("duration_ms"),
                        uri = track.optString("uri")
                    )
                }
            }
            if (tracks.isNotEmpty()) {
                tracksState.clear()
                tracksState.addAll(tracks)
                Log.d("API", "Loaded ${tracks.size} tracks from Spotify (ready, not auto-playing)")
            }
        } catch (e: Exception) {
            Log.e("API", "Failed to load recently played", e)
            if (tracksState.isEmpty()) loadSampleTracks()
        }
    }

    private fun loadSampleTracks() {
        tracksState.clear()
        tracksState.addAll(listOf(
            TrackItem("Ключ", "Midix", "Ты любишь запах крови",
                "https://i.scdn.co/image/ab67616d0000b27344e9d13c687469d32a9f22a8", 147000L, "spotify:track:24RuIkjSDgLFhnscvGHK9W"),
            TrackItem("в запое", "лиззз", "дайте спиться",
                "https://i.scdn.co/image/ab67616d0000b273101567f5c16b6398d56ef7fc", 142000L, "spotify:track:3NWAkoHf4COLmtevWzHLOs"),
            TrackItem("intellectual greed", "brakence", "hypochondriac",
                "https://i.scdn.co/image/ab67616d0000b273cbf54392eca47bb30303db4c", 170000L, "spotify:track:1o0hKMX2d4wIViz31dR7sG"),
            TrackItem("Отпусти меня", "SEREBRO", "СИЛА ТРЕХ",
                "https://i.scdn.co/image/ab67616d0000b273a2eb35c344f7bf4eb5d81c1a", 231000L, "spotify:track:6jDczq41moCrpYCxzDlCnz"),
            TrackItem("UNCA / Composure", "glaive, kurtains", "God Save The Three",
                "https://i.scdn.co/image/ab67616d0000b273212b26d61d694eb660bb7759", 135000L, "spotify:track:3bnfKViVyASdXa1twrMxeC")
        ))
    }



    private fun loadUserPlaylists() {
        // Load immediately from offline disk cache if available (0 ms delay)
        val cached = prefs.getString(KEY_CACHED_PLAYLISTS, null)
        if (cached != null && userPlaylistsState.isEmpty()) {
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

    private fun parseAndApplyPlaylists(jsonStr: String): Boolean {
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
                    userPlaylistsState.clear()
                    userPlaylistsState.addAll(parsed)
                    Log.d("Playlists", "Loaded ${parsed.size} user playlists")
                    return true
                }
            }
        } catch (e: Exception) {
            Log.e("Playlists", "Failed to parse playlists", e)
        }
        return false
    }

    private fun syncVisibleQueue() {
        val list = mutableListOf<TrackItem>()
        val curr = livePlayingTrack.value ?: tracksState.getOrNull(0)
        if (curr != null) {
            list.add(curr)
        }
        // User custom queue ("Play Next")
        list.addAll(customQueue)
        // Playlist remainder
        if (playlistQueue.isNotEmpty() && currentPlaylistIndex in playlistQueue.indices) {
            val remaining = playlistQueue.subList((currentPlaylistIndex + 1).coerceAtMost(playlistQueue.size), playlistQueue.size)
            list.addAll(remaining)
        }
        val capped = list.take(30)
        tracksState.clear()
        tracksState.addAll(capped)
        currentTrackIndex.intValue = 0
    }

    private fun getUpcomingTrack(): TrackItem? {
        if (customQueue.isNotEmpty()) {
            return customQueue.first()
        }
        if (repeatMode.intValue == 2) {
            return livePlayingTrack.value ?: tracksState.getOrNull(0)
        }
        if (currentPlaylistIndex + 1 < playlistQueue.size) {
            return playlistQueue[currentPlaylistIndex + 1]
        }
        if (repeatMode.intValue == 1 && playlistQueue.isNotEmpty()) {
            return playlistQueue[0]
        }
        return null
    }

    private fun preloadUpcomingTrack() {
        val upcoming = getUpcomingTrack() ?: return
        scope.launch(Dispatchers.IO) {
            delay(1000) // Allow current track to enter Playing state first so standby slot is ready
            if (isActive && isPlaying.value) {
                val crossfade = crossfadeEnabled.value
                val ok = SpotifyBridge.preloadTrack(upcoming.uri, crossfade)
                Log.d("Playback", "Preloaded next track '${upcoming.title}' (crossfade=$crossfade, ok=$ok)")
            }
        }
    }

    private fun playTrackInternal(item: TrackItem) {
        livePlayingTrack.value = item
        progressMs.longValue = 0L
        isPlaying.value = true
        isCrossfading.value = false
        SpotifyBridge.playTrack(item.uri)
        syncPlaybackServiceNotification(0L, item.durationMs)
        syncVisibleQueue()
        preloadUpcomingTrack()
    }

    private fun syncPlaybackServiceNotification(pos: Long = progressMs.longValue, dur: Long = 0L) {
        val active = livePlayingTrack.value ?: tracksState.getOrNull(0) ?: return
        val totalDur = if (dur > 0) dur else active.durationMs
        PlaybackService.updateNotification(
            this,
            active.title,
            active.artist,
            active.album,
            active.artworkUrl,
            isPlaying.value,
            pos,
            totalDur
        )
    }

    private fun advanceUpcomingState(upcoming: TrackItem) {
        if (customQueue.isNotEmpty() && customQueue.first().uri == upcoming.uri) {
            customQueue.removeAt(0)
        } else if (currentPlaylistIndex + 1 < playlistQueue.size && playlistQueue[currentPlaylistIndex + 1].uri == upcoming.uri) {
            currentPlaylistIndex++
        } else if (repeatMode.intValue == 1 && playlistQueue.isNotEmpty() && playlistQueue[0].uri == upcoming.uri) {
            currentPlaylistIndex = 0
        }
        livePlayingTrack.value = upcoming
        progressMs.longValue = 0L
        syncVisibleQueue()
        preloadUpcomingTrack()
    }

    private fun addToQueue(item: TrackItem) {
        customQueue.add(item)
        syncVisibleQueue()
        preloadUpcomingTrack()
        Log.d("Queue", "Added '${item.title}' to custom queue (total: ${customQueue.size})")
    }

    private fun playTrackItem(item: TrackItem, queueContext: List<TrackItem>? = null) {
        stopAtEndOfQueue.value = false
        if (!queueContext.isNullOrEmpty()) {
            playlistQueue.clear()
            playlistQueue.addAll(queueContext)
            val idx = playlistQueue.indexOfFirst { it.uri == item.uri }
            currentPlaylistIndex = if (idx >= 0) idx else 0
            customQueue.clear()
        } else {
            val existingIdx = playlistQueue.indexOfFirst { it.uri == item.uri }
            if (existingIdx >= 0) {
                currentPlaylistIndex = existingIdx
            } else {
                currentPlaylistIndex = (currentPlaylistIndex + 1).coerceAtMost(playlistQueue.size)
                playlistQueue.add(currentPlaylistIndex, item)
            }
        }
        playTrackInternal(item)
    }

    private fun onTrackClick(idx: Int) {
        val clicked = tracksState.getOrNull(idx) ?: return
        if (idx == 0 && livePlayingTrack.value?.uri == clicked.uri) {
            return
        }
        val customIdx = customQueue.indexOfFirst { it.uri == clicked.uri }
        if (customIdx >= 0) {
            for (i in 0..customIdx) {
                customQueue.removeAt(0)
            }
            playTrackInternal(clicked)
            return
        }
        val plIdx = playlistQueue.indexOfFirst { it.uri == clicked.uri }
        if (plIdx >= 0) {
            currentPlaylistIndex = plIdx
            playTrackInternal(clicked)
            return
        }
        playTrackInternal(clicked)
    }

    private fun togglePlay() {
        isPlaying.value = !isPlaying.value
        if (isPlaying.value) SpotifyBridge.play() else SpotifyBridge.pause()
        syncPlaybackServiceNotification()
    }

    private fun toggleShuffle() {
        isShuffle.value = !isShuffle.value
        if (isShuffle.value) {
            if (playlistQueue.isNotEmpty()) {
                originalQueueBeforeShuffle.clear()
                originalQueueBeforeShuffle.addAll(playlistQueue)
                val curr = livePlayingTrack.value ?: playlistQueue.getOrNull(currentPlaylistIndex)
                val rest = playlistQueue.filter { it.uri != curr?.uri }.shuffled()
                playlistQueue.clear()
                if (curr != null) playlistQueue.add(curr)
                playlistQueue.addAll(rest)
                currentPlaylistIndex = 0
                syncVisibleQueue()
                preloadUpcomingTrack()
            }
        } else {
            if (originalQueueBeforeShuffle.isNotEmpty()) {
                val curr = livePlayingTrack.value ?: playlistQueue.getOrNull(currentPlaylistIndex)
                playlistQueue.clear()
                playlistQueue.addAll(originalQueueBeforeShuffle)
                val idx = playlistQueue.indexOfFirst { it.uri == curr?.uri }
                currentPlaylistIndex = if (idx >= 0) idx else 0
                syncVisibleQueue()
                preloadUpcomingTrack()
            }
        }
    }

    private fun toggleRepeat() {
        // cycle: 0 (off) -> 1 (repeat all) -> 2 (repeat one) -> 0
        repeatMode.intValue = (repeatMode.intValue + 1) % 3
        preloadUpcomingTrack()
    }

    private fun nextTrack(isAutoAdvance: Boolean = false) {
        if (repeatMode.intValue == 2) {
            // Repeat one: replay current track from start
            SpotifyBridge.seek(0)
            SpotifyBridge.play()
            progressMs.longValue = 0L
            isPlaying.value = true
            syncPlaybackServiceNotification(0L)
            return
        }

        // Custom play-next queue takes priority
        if (customQueue.isNotEmpty()) {
            val next = customQueue.removeAt(0)
            Log.d("Queue", "Playing next from custom queue: ${next.title}")
            playTrackInternal(next)
            return
        }

        // Stop at end of queue (e.g. albums)
        if (stopAtEndOfQueue.value && currentPlaylistIndex + 1 >= playlistQueue.size) {
            Log.d("Queue", "Reached end of album with stopAtEndOfQueue enabled -> pausing")
            SpotifyBridge.pause()
            isPlaying.value = false
            progressMs.longValue = 0L
            syncPlaybackServiceNotification(0L)
            return
        }

        if (currentPlaylistIndex + 1 < playlistQueue.size) {
            currentPlaylistIndex++
            val next = playlistQueue[currentPlaylistIndex]
            Log.d("Queue", "Advancing playlist queue to index $currentPlaylistIndex: ${next.title}")
            playTrackInternal(next)
        } else if (repeatMode.intValue == 1 && playlistQueue.isNotEmpty()) {
            // Repeat all: loop back to 0
            currentPlaylistIndex = 0
            val next = playlistQueue[0]
            Log.d("Queue", "Repeat All: wrapping back to start: ${next.title}")
            playTrackInternal(next)
        } else {
            // Repeat off: STOP PLAYBACK!
            Log.d("Queue", "End of queue reached with Repeat Off -> stopping playback")
            SpotifyBridge.pause()
            isPlaying.value = false
            progressMs.longValue = 0L
            syncPlaybackServiceNotification(0L)
        }
    }

    private fun prevTrack() {
        if (progressMs.longValue > 3000L || repeatMode.intValue == 2) {
            SpotifyBridge.seek(0)
            progressMs.longValue = 0L
            syncPlaybackServiceNotification(0L)
            return
        }
        if (currentPlaylistIndex > 0 && playlistQueue.isNotEmpty()) {
            currentPlaylistIndex--
            val prev = playlistQueue[currentPlaylistIndex]
            playTrackInternal(prev)
        } else if (repeatMode.intValue == 1 && playlistQueue.isNotEmpty()) {
            currentPlaylistIndex = playlistQueue.size - 1
            val prev = playlistQueue[currentPlaylistIndex]
            playTrackInternal(prev)
        } else {
            SpotifyBridge.seek(0)
            progressMs.longValue = 0L
            syncPlaybackServiceNotification(0L)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        PlaybackService.listener = null
        authServer?.close()
        playbackTickerJob?.cancel()
        scope.cancel()
    }
}

// ─── Auth state ───────────────────────────────────────────────────────────────

sealed class AuthState {
    object Loading : AuthState()
    object LoggedOut : AuthState()
    object Connecting : AuthState()
    object LoggedIn : AuthState()
}

// ─── Composable UI ────────────────────────────────────────────────────────────

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
    onCrossfadeChanged: ((Boolean, Int) -> Unit)? = null
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var isPlayerExpanded by remember { mutableStateOf(false) }
    var activePlaylist by remember { mutableStateOf<PlaylistItem?>(null) }
    var activeArtist by remember { mutableStateOf<ArtistItem?>(null) }
    var showAllPlaylists by remember { mutableStateOf(false) }
    var activeAlbumUri by remember { mutableStateOf<String?>(null) }
    var activeAlbumTitle by remember { mutableStateOf("Album") }
    var activeAlbumArtwork by remember { mutableStateOf("") }
    var artistPickerList by remember { mutableStateOf<List<ArtistItem>?>(null) }

    
    val openAlbumByTrack: (TrackItem) -> Unit = { t ->
        // Search album or if album is present
        val albTitle = if (t.album.isNotEmpty()) t.album else t.title
        // Search album or fetch album uri
        SpotifyBridge.searchTracks(albTitle) { ok, json ->
            var albUri = ""
            var albArt = t.artworkUrl
            if (ok && json.isNotEmpty()) {
                try {
                    val obj = org.json.JSONObject(json)
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
    androidx.activity.compose.BackHandler(enabled = isPlayerExpanded || activePlaylist != null || activeArtist != null || activeAlbumUri != null || showAllPlaylists) {
        if (isPlayerExpanded) {
            isPlayerExpanded = false
        } else if (activeAlbumUri != null) {
            activeAlbumUri = null
        } else if (activePlaylist != null) {
            activePlaylist = null
        } else if (activeArtist != null) {
            activeArtist = null
        } else if (showAllPlaylists) {
            showAllPlaylists = false
        }
    }


    Box(modifier = Modifier.fillMaxSize().background(Color(0xFF110F0E))) {
        Scaffold(
            containerColor = Color(0xFF110F0E),
            bottomBar = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.Transparent)
                ) {
                    // Floating Mini Player (Independent floating box above navigation)
                    if (currentTrack != null) {
                        val miniPalette = rememberTrackPalette(androidx.compose.ui.platform.LocalContext.current, currentTrack.artworkUrl)
                        val miniSpinTransition = rememberInfiniteTransition(label = "miniSpinTransition")
                        val miniSpinAngle by miniSpinTransition.animateFloat(
                            initialValue = 0f,
                            targetValue = if (isPlaying) (2f * Math.PI.toFloat()) else 0f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(durationMillis = 14000, easing = LinearEasing),
                                repeatMode = RepeatMode.Restart
                            ),
                            label = "miniSpinAngle"
                        )
                        var isMiniPlayPressed by remember { mutableStateOf(false) }
                        val miniPlayScale by animateFloatAsState(
                            targetValue = if (isMiniPlayPressed) 0.88f else 1.0f,
                            animationSpec = spring(dampingRatio = 0.6f, stiffness = 400f),
                            label = "miniPlayScale"
                        )

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 6.dp)
                                .shadow(16.dp, RoundedCornerShape(22.dp), spotColor = miniPalette.accent.copy(alpha = 0.45f))
                                .clip(RoundedCornerShape(22.dp))
                                .background(
                                    Brush.horizontalGradient(
                                        listOf(
                                            miniPalette.dominant.copy(alpha = 0.94f),
                                            miniPalette.secondary.copy(alpha = 0.94f)
                                        )
                                    )
                                )
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
                                        .clip(RoundedCornerShape(12.dp)),
                                    contentScale = ContentScale.Crop
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = currentTrack.title,
                                        color = Color.White,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = currentTrack.artist,
                                        color = Color(0xCCFFFFFF),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                MaterialExpressiveShape(
                                    shape = if (isPlaying) "cookie9" else "softCookie",
                                    color = miniPalette.accent,
                                    spinAngleRad = if (isPlaying) miniSpinAngle else 0f,
                                    modifier = Modifier
                                        .size(42.dp)
                                        .scale(miniPlayScale)
                                        .pointerInput(Unit) {
                                            detectTapGestures(
                                                onPress = {
                                                    isMiniPlayPressed = true
                                                    tryAwaitRelease()
                                                    isMiniPlayPressed = false
                                                    onPlayPause()
                                                }
                                            )
                                        }
                                ) {
                                    Icon(
                                        imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                        contentDescription = if (isPlaying) "Pause" else "Play",
                                        tint = miniPalette.onAccent,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }
                        }
                    }

                    // Compact Translucent Navigation Bar (Glass effect bleeding off screen, no outline)
                    Surface(
                        color = Color(0xD9161311),
                        shape = RectangleShape,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .windowInsetsPadding(WindowInsets.navigationBars)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(58.dp)
                                    .padding(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.SpaceAround,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CompactNavItem(
                                    icon = Icons.Rounded.Home,
                                    label = "Home",
                                    selected = selectedTab == 0 && activePlaylist == null && activeArtist == null && activeAlbumUri == null && !showAllPlaylists,
                                    onClick = {
                                        selectedTab = 0
                                        activePlaylist = null
                                        activeArtist = null
                                        activeAlbumUri = null
                                        showAllPlaylists = false
                                    }
                                )
                                CompactNavItem(
                                    icon = Icons.Rounded.Search,
                                    label = "Search",
                                    selected = selectedTab == 1 && activePlaylist == null && activeArtist == null && activeAlbumUri == null && !showAllPlaylists,
                                    onClick = {
                                        selectedTab = 1
                                        activePlaylist = null
                                        activeArtist = null
                                        activeAlbumUri = null
                                        showAllPlaylists = false
                                    }
                                )
                                CompactNavItem(
                                    icon = Icons.Rounded.Settings,
                                    label = "Settings",
                                    selected = selectedTab == 2 && activePlaylist == null && activeArtist == null && activeAlbumUri == null && !showAllPlaylists,
                                    onClick = {
                                        selectedTab = 2
                                        activePlaylist = null
                                        activeArtist = null
                                        activeAlbumUri = null
                                        showAllPlaylists = false
                                    }
                                )
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                        }
                    }
                }
            }
        ) { padding ->
            Box(modifier = Modifier.fillMaxSize().padding(top = padding.calculateTopPadding())) {
                if (showAllPlaylists) {
                    AllPlaylistsScreen(
                        playlists = if (userPlaylists.isNotEmpty()) userPlaylists else samplePlaylists,
                        onBack = { showAllPlaylists = false },
                        onPlaylistClick = {
                            showAllPlaylists = false
                            activePlaylist = it
                        }
                    )
                } else if (activeArtist != null) {
                    ArtistDetailScreen(
                        artist = activeArtist!!,
                        livePlayingTrack = livePlayingTrack,
                        isPlaying = isPlaying,
                        onBack = { activeArtist = null },
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
                        }
                    )
                } else if (activeAlbumUri != null) {
                    AlbumDetailScreen(
                        albumUri = activeAlbumUri!!,
                        initialTitle = activeAlbumTitle,
                        initialArtworkUrl = activeAlbumArtwork,
                        livePlayingTrack = livePlayingTrack,
                        isPlaying = isPlaying,
                        onBack = { activeAlbumUri = null },
                        onTrackSelect = { selectedTrack, playlistTracks ->
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
                } else if (activePlaylist != null) {
                    PlaylistDetailScreen(
                        playlist = activePlaylist!!,
                        tracks = tracks,
                        livePlayingTrack = livePlayingTrack,
                        isPlaying = isPlaying,
                        onBack = { activePlaylist = null },
                        onTrackSelect = { selectedTrack, playlistTracks ->
                            onTrackSelect(selectedTrack, playlistTracks)
                        },
                        onPlayAll = { playlistTracks ->
                            if (playlistTracks.isNotEmpty()) {
                                onTrackSelect(playlistTracks.first(), playlistTracks)
                            }
                        },
                        onAddToQueue = onAddToQueue,
                        onTrackTitleClick = { track -> openAlbumByTrack(track) },
                        onArtistClick = { artistName -> openArtistScreen(artistName, "") }
                    )
                } else {
                    when (selectedTab) {
                        0 -> HomeScreen(
                            authState = authState,
                            tracks = tracks,
                            currentTrackIndex = currentTrackIndex,
                            livePlayingTrack = livePlayingTrack,
                            isPlaying = isPlaying,
                            statusMsg = statusMsg,
                            onLoginClick = onLoginClick,
                            onTrackClick = onTrackClick,
                            onPlayPause = onPlayPause,
                            userPlaylists = userPlaylists,
                            onPlaylistClick = { activePlaylist = it },
                            onArtistClick = { activeArtist = it },
                            onSeeAllPlaylists = { showAllPlaylists = true }
                        )
                        1 -> SearchScreen(
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
                            onPlaylistSelect = { activePlaylist = it }
                        )
                        2 -> SettingsScreen(
                            connectEnabled = connectEnabled,
                            onConnectToggle = onConnectToggle,
                            onReauthorize = onLoginClick,
                            onCrossfadeChanged = onCrossfadeChanged
                        )
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
                    onArtistClick = { artistName ->
                        isPlayerExpanded = false
                        openArtistScreen(artistName, "")
                    },
                    onAlbumClick = {
                        isPlayerExpanded = false
                        openAlbumByTrack(currentTrack)
                    },
                    isShuffle = isShuffle,
                    repeatMode = repeatMode,
                    onToggleShuffle = onToggleShuffle,
                    onToggleRepeat = onToggleRepeat
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
    }
}

// ─── Wavy Line Component ──────────────────────────────────────────────────────

@Composable
fun WavyLine(
    modifier: Modifier = Modifier,
    color: Color = Color(0x33F2A385),
    wavelength: Float = 14.2f,
    amplitude: Float = 1.3f,
    strokeWidth: Float = 2.0f
) {
    Canvas(modifier = modifier.height((amplitude * 2 + strokeWidth * 2 + 4f).dp)) {
        val width = size.width
        val midY = size.height / 2f
        val wavelengthPx = wavelength.dp.toPx()
        val ampPx = amplitude.dp.toPx()
        val strokePx = strokeWidth.dp.toPx()

        val path = Path()
        var first = true
        val step = 1.5f
        var x = 0f
        while (x <= width) {
            val y = midY + sin((x / wavelengthPx) * 2f * PI.toFloat()) * ampPx
            if (first) {
                path.moveTo(x, y)
                first = false
            } else {
                path.lineTo(x, y)
            }
            x += step
        }
        drawPath(
            path = path,
            color = color,
            style = Stroke(
                width = strokePx,
                cap = StrokeCap.Round,
                join = StrokeJoin.Round
            )
        )
    }
}

@Composable
fun Cookie12Border(
    modifier: Modifier = Modifier,
    color: Color = Color(0x66F2A385),
    lobes: Int = 12,
    strokeWidth: Dp = 1.6.dp
) {
    Canvas(modifier = modifier) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val maxR = (minOf(size.width, size.height) / 2f) - strokeWidth.toPx()
        val amp = maxR * 0.08f
        val baseR = maxR - amp
        val path = Path()
        val steps = 180
        for (i in 0..steps) {
            val theta = (i.toFloat() / steps) * (2f * Math.PI.toFloat())
            val r = baseR + amp * kotlin.math.cos(lobes * theta)
            val x = cx + r * kotlin.math.cos(theta)
            val y = cy + r * kotlin.math.sin(theta)
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        drawPath(
            path = path,
            color = color,
            style = Stroke(
                width = strokeWidth.toPx(),
                cap = StrokeCap.Round,
                join = StrokeJoin.Round
            )
        )
    }
}

// ─── Playlists & Artists data ────────────────────────────────────────────────

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
    val greetingText = listOf("Good morning.", "Good afternoon.", "Good evening.", "Up late...")[part]
    val greetingShape = listOf("sunny", "softBurst", "flower", "ghostish")[part]
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
                    color = Color(0xFFF7F2EE),
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = (-0.8).sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(Color(0x29F2A385)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = greetingIcon,
                            contentDescription = null,
                            tint = Color(0xFFF2A385),
                            modifier = Modifier.size(15.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = greetingText,
                        color = Color(0xFFA59B94),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF26201E)),
                contentAlignment = Alignment.Center
            ) {
                Text("⋮", color = Color(0xFFCDC2BB), fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
        }

        // Auth status or Login Card if logged out
        when (authState) {
            is AuthState.LoggedOut -> {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF26201E)),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Column(modifier = Modifier.padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Connect Spotify", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Spotify Premium required for full playback", color = Color(0xFFA59B94), fontSize = 13.sp)
                        Spacer(modifier = Modifier.height(14.dp))
                        Button(
                            onClick = onLoginClick,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF2A385)),
                            shape = RoundedCornerShape(999.dp)
                        ) {
                            Text("Log in with Spotify", color = Color(0xFF251510), fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
            is AuthState.Loading, is AuthState.Connecting -> {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color(0xFFF2A385), strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(statusMsg.ifEmpty { "Connecting Spotify..." }, color = Color(0xFFA59B94), fontSize = 13.sp)
                }
            }
            is AuthState.LoggedIn -> {}
        }

        // Continue Listening (Hero Spotlight Card - Material 3 Expressive)
        if (spotlightTrack != null) {
            val heroPalette = rememberTrackPalette(androidx.compose.ui.platform.LocalContext.current, spotlightTrack.artworkUrl)
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
                                        text = if (isPlaying) "Pause" else "Resume",
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

        // Two Quick Shortcuts: Favourite Songs & Your Playlists
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Favourite Songs
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color(0xFF26201E))
                    .clickable {
                        onPlaylistClick(
                            PlaylistItem(
                                name = "Favourite Songs",
                                owner = "3,495 favourited tracks",
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
                        Text("Favourite Songs", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                        Text("Favourited: 3495", color = Color(0xFF9E948E), fontSize = 11.sp, maxLines = 1)
                    }
                    Text("›", color = Color(0xFF8F8580), fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
            }

            // Your Playlists Shortcut
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color(0xFF26201E))
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
                        Text("Your Playlists", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                        val count = if (userPlaylists.isNotEmpty()) userPlaylists.size else 5
                        Text("Collections: $count", color = Color(0xFF9E948E), fontSize = 11.sp, maxLines = 1)
                    }
                    Text("›", color = Color(0xFF8F8580), fontSize = 18.sp, fontWeight = FontWeight.Bold)
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
            Text("Your Playlists", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            WavyLine(
                modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
                color = Color(0x33F2A385)
            )
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(Color(0xFF2E2725))
                    .clickable { onSeeAllPlaylists() }
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text("→ See All", color = Color(0xFFD8CDC7), fontSize = 12.sp, fontWeight = FontWeight.Medium)
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
                            .clip(RoundedCornerShape(18.dp)),
                        contentScale = ContentScale.Crop
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = playlist.name,
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = playlist.owner,
                        color = Color(0xFF9E948E),
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
            Text("Recently Played", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            WavyLine(
                modifier = Modifier.weight(1f).padding(start = 12.dp),
                color = Color(0x33F2A385)
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            itemsIndexed(tracks) { index, track ->
                val isCurrent = (livePlayingTrack != null && livePlayingTrack.uri == track.uri) || (livePlayingTrack == null && index == currentTrackIndex)
                Column(modifier = Modifier.width(136.dp).clickable { onTrackClick(index) }) {
                    Box(
                        modifier = Modifier
                            .size(136.dp)
                            .shadow(if (isCurrent && isPlaying) 16.dp else 6.dp, RoundedCornerShape(22.dp), spotColor = Color(0x66F2A385))
                            .clip(RoundedCornerShape(22.dp))
                            .background(Color(0xFF201B19))
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
                                    .background(Color(0xFFF2A385)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.VolumeUp,
                                    contentDescription = null,
                                    tint = Color(0xFF1B110E),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = track.title,
                        color = if (isCurrent) Color(0xFFF2A385) else Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = track.artist,
                        color = Color(0xFFA59B94),
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
            Text("Artists in Your Rotation", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            WavyLine(
                modifier = Modifier.weight(1f).padding(start = 12.dp),
                color = Color(0x33F2A385)
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            items(sampleArtists) { artist ->
                Column(
                    modifier = Modifier
                        .width(136.dp)
                        .clickable {
                            onArtistClick(artist)
                        },
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    SquiggleAvatar(
                        imageUrl = artist.artworkUrl,
                        contentDescription = artist.name,
                        modifier = Modifier.size(136.dp),
                        ringColor = Color(0xFFF2A385),
                        waves = 20,
                        amplitude = 1.3.dp,
                        strokeWidth = 2.0.dp,
                        gap = 4.0.dp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = artist.name,
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
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

fun formatTime(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%d:%02d", minutes, seconds)
}

@Composable
private fun CompactNavItem(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val accentColor = Color(0xFFF2A385)
    val inactiveColor = Color(0xFF8A807A)
    val activeBg = Color(0x2EF2A385)

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) activeBg else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (selected) accentColor else inactiveColor,
            modifier = Modifier.size(20.dp)
        )
        if (selected) {
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                color = accentColor,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
