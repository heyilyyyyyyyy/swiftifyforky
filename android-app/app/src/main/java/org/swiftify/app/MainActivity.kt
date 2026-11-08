package org.swiftify.app

import org.swiftify.app.design.*
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.LayoutCoordinates
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
    private val customQueue = mutableStateListOf<TrackItem>()
    private val queueContextName = mutableStateOf("Swiftify")
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

    private val sleepTimerMinutes = mutableIntStateOf(0)
    private val sleepTimerRemainingSeconds = mutableIntStateOf(0)
    private var sleepTimerJob: Job? = null

    private var playbackTickerJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private fun setSleepTimer(minutes: Int) {
        sleepTimerJob?.cancel()
        sleepTimerMinutes.intValue = minutes
        if (minutes <= 0) {
            sleepTimerRemainingSeconds.intValue = 0
            if (minutes == 0) {
                Toast.makeText(this, "Sleep timer turned off", Toast.LENGTH_SHORT).show()
            }
        } else {
            sleepTimerRemainingSeconds.intValue = minutes * 60
            Toast.makeText(this, "Sleep timer set for $minutes min", Toast.LENGTH_SHORT).show()
            sleepTimerJob = lifecycleScope.launch {
                while (sleepTimerRemainingSeconds.intValue > 0) {
                    delay(1000L)
                    sleepTimerRemainingSeconds.intValue--
                }
                SpotifyBridge.pause()
                isPlaying.value = false
                sleepTimerMinutes.intValue = 0
                sleepTimerRemainingSeconds.intValue = 0
                syncPlaybackServiceNotification(0L)
                Toast.makeText(applicationContext, "Sleep timer finished. Playback paused.", Toast.LENGTH_LONG).show()
            }
        }
    }

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
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
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

        // Apply startup 6-band equalizer DSP settings
        val eqEnabled = prefs.getBoolean("equalizer_enabled", false)
        if (eqEnabled) {
            val gainsStr = prefs.getString("equalizer_gains", null)
            val gains = gainsStr?.split(",")?.mapNotNull { it.toFloatOrNull() }?.toFloatArray()
                ?: floatArrayOf(0f, 0f, 0f, 0f, 0f, 0f)
            SpotifyBridge.setEqualizer(gains)
            Log.d("DSP", "Equalizer restored from prefs: ${gains.joinToString()}")
        }

        // Connect media session & hardware Bluetooth events to playback controller
        PlaybackService.listener = object : PlaybackEventListener {
            override fun onPlay() {
                runOnUiThread {
                    if (!isPlaying.value) {
                        togglePlay()
                    }
                }
            }
            override fun onPause() {
                runOnUiThread {
                    if (isPlaying.value) {
                        togglePlay()
                    }
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
            org.swiftify.app.design.SwiftifyTheme {
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
                            SpotifyBridge.setSpotifyConnect(v, getSpotifyDeviceId())
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
                        },
                        customQueue = customQueue,
                        onRemoveFromCustomQueue = { idx -> removeFromCustomQueue(idx) },
                        onClearCustomQueue = { clearCustomQueue() },
                        queueContextName = queueContextName.value,
                        onCreatePlaylist = { n, d -> createPlaylist(n, d) },
                        onDeletePlaylist = { p -> deletePlaylist(p) },
                        onAddTrackToPlaylist = { p, t -> addTrackToPlaylist(p, t) },
                        onRemoveTrackFromPlaylist = { p, t -> removeTrackFromPlaylist(p, t) },
                        sleepTimerMinutes = sleepTimerMinutes.intValue,
                        sleepTimerRemainingSeconds = sleepTimerRemainingSeconds.intValue,
                        onSetSleepTimer = { mins -> setSleepTimer(mins) },
                        onLogout = { performLogout() }
                    )
                }
            }
        }

        // Check for stored credentials via SecureStorage
        val storedLogin = SecureStorage.getLoginBlob(this)
        val storedToken = SecureStorage.getAccessToken(this)
        accessTokenState.value = storedToken
        SpotifyApi.onTokenUpdated = { token ->
            accessTokenState.value = token
        }
        refreshAccessToken()

        // Restore playback queue state if recreating after process death or configuration change
        if (playlistQueue.isEmpty()) {
            if (PlaybackQueueManager.shared.restoreState(this)) {
                playlistQueue.clear()
                playlistQueue.addAll(PlaybackQueueManager.shared.playlistQueue)
                currentPlaylistIndex = PlaybackQueueManager.shared.currentPlaylistIndex
                livePlayingTrack.value = PlaybackQueueManager.shared.currentTrack
                repeatMode.intValue = PlaybackQueueManager.shared.repeatMode.value
                isShuffle.value = PlaybackQueueManager.shared.isShuffle
                stopAtEndOfQueue.value = PlaybackQueueManager.shared.stopAtEndOfQueue
            }
        }

        if (storedLogin != null) {
            authState.value = AuthState.Connecting
            statusMsg.value = "Reconnecting stored session (EncryptedSharedPreferences)..."
            SpotifyBridge.connectStored(storedLogin)
        } else if (storedToken != null) {
            authState.value = AuthState.Connecting
            statusMsg.value = "Connecting with token..."
            SpotifyBridge.connect(storedToken)
        } else {
            authState.value = AuthState.LoggedOut
        }
    }

    fun refreshAccessToken(force: Boolean = false, onComplete: ((String?) -> Unit)? = null) {
        scope.launch {
            val token = SpotifyApi.getValidToken(this@MainActivity, forceRefresh = force)
            withContext(Dispatchers.Main) {
                if (token != null) {
                    accessTokenState.value = token
                }
                onComplete?.invoke(token)
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
                val returnedState = uri.getQueryParameter("state")
                val isStateValid = returnedState != null && returnedState == oauthStateParam

                val responseHtml = if (isStateValid && code != null) {
                    """
                    <!doctype html><html><head><meta charset="utf-8">
                    <title>Swiftify</title>
                    <style>body{font-family:sans-serif;background:#161413;color:#fff;display:flex;align-items:center;justify-content:center;height:100vh;margin:0;text-align:center;}h2{color:#F2A385;}</style>
                    </head><body><div><h2>✓ Signed In to Swiftify!</h2><p>You can return to the Swiftify app now.</p></div></body></html>
                    """.trimIndent()
                } else {
                    """
                    <!doctype html><html><head><meta charset="utf-8">
                    <title>Swiftify</title>
                    <style>body{font-family:sans-serif;background:#161413;color:#fff;display:flex;align-items:center;justify-content:center;height:100vh;margin:0;text-align:center;}h2{color:#FF6B6B;}</style>
                    </head><body><div><h2>✕ Authentication Failed</h2><p>${if (!isStateValid) "Invalid security state (CSRF verification failed)" else (error ?: "Cancelled")}</p></div></body></html>
                    """.trimIndent()
                }

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
                    if (!isStateValid) {
                        Log.e("AuthServer", "Auth failed: state mismatch (expected $oauthStateParam, got $returnedState)")
                        authState.value = AuthState.LoggedOut
                        statusMsg.value = "Security error: invalid OAuth state parameter"
                        Toast.makeText(this@MainActivity, "Authentication rejected: invalid state", Toast.LENGTH_LONG).show()
                    } else if (code != null) {
                        Log.d("AuthServer", "Captured authorization code with validated CSRF state ✓")
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
                SecureStorage.saveTokens(this@MainActivity, token, refreshToken, expiresIn)
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
            SecureStorage.setLoginBlob(this@MainActivity, loginBlob)
            authState.value = AuthState.LoggedIn
            statusMsg.value = "Connected to Spotify ✓"
            // Enable Spotify Connect with persistent unique device ID
            SpotifyBridge.setSpotifyConnect(connectEnabled.value, getSpotifyDeviceId())
            startPlaybackTicker()
            scope.launch {
                loadRecentlyPlayed()
                loadUserPlaylists()
            }
        }
        SpotifyBridge.onConnectError = { error ->
            Log.e("Auth", "Core connect error: $error")
            val isCredentialError = error.contains("BadCredentials", ignoreCase = true) ||
                    error.contains("InvalidCredentials", ignoreCase = true) ||
                    error.contains("UserNotAllowed", ignoreCase = true)
            if (isCredentialError) {
                SecureStorage.clearAuth(this@MainActivity)
                authState.value = AuthState.LoggedOut
                statusMsg.value = "Session error: $error"
            } else {
                // Transient network failure or offline mode: preserve user credentials!
                statusMsg.value = "Connection error: $error"
                if (authState.value == AuthState.Connecting) {
                    authState.value = AuthState.LoggedOut
                }
            }
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
                                if (upcoming != null && upcoming.uri != lastCrossfadeTriggeredUri) {
                                    isCrossfading.value = true
                                    lastCrossfadeTriggeredUri = upcoming.uri
                                    Log.d("Playback", "Crossfade threshold reached! Overlapping with '${upcoming.title}' (${crossMs}ms)")
                                    try {
                                        SpotifyBridge.crossfadeTrack(upcoming.uri, crossMs)
                                        advanceUpcomingState(upcoming)
                                    } catch (e: Exception) {
                                        Log.e("Playback", "Crossfade error, resetting flag", e)
                                        isCrossfading.value = false
                                    }
                                }
                            }
                        }

                        syncPlaybackServiceNotification(pos, dur, force = false)
                    }
                }
                delay(pollDelay)
            }
        }
    }

    private suspend fun loadRecentlyPlayed() {
        try {
            val token = SpotifyApi.getValidToken(this@MainActivity) ?: return
            val tracks = withContext(Dispatchers.IO) {
                val conn = URL("https://api.spotify.com/v1/me/player/recently-played?limit=20")
                    .openConnection() as HttpURLConnection
                conn.connectTimeout = 8000
                conn.readTimeout = 8000
                conn.setRequestProperty("Authorization", "Bearer $token")
                val code = conn.responseCode
                if (code == 401) {
                    conn.disconnect()
                    val refreshedToken = SpotifyApi.getValidToken(this@MainActivity, forceRefresh = true)
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
                tracksState.clear()
                tracksState.addAll(tracks)
                Log.d("API", "Loaded ${tracks.size} recently played tracks from Spotify ✓")
            }
        } catch (e: Exception) {
            Log.e("API", "Failed to load recently played", e)
        }
    }

    private fun parseRecentlyPlayedItems(items: org.json.JSONArray): List<TrackItem> {
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

    private fun ensureSessionValid() {
        if (!SpotifyBridge.isSessionValid()) {
            val storedLogin = SecureStorage.getLoginBlob(this)
            val storedToken = SecureStorage.getAccessToken(this)
            Log.d("Playback", "Session is invalid -> reconnecting stored session")
            if (storedLogin != null) {
                SpotifyBridge.connectStored(storedLogin)
            } else if (storedToken != null) {
                SpotifyBridge.connect(storedToken)
            }
        }
    }

    private var lastNotifTrackUri: String? = null
    private var lastNotifPlaying: Boolean? = null
    private var lastNotifSyncTimeMs: Long = 0L
    private var lastCrossfadeTriggeredUri: String? = null

    private fun playTrackInternal(item: TrackItem) {
        livePlayingTrack.value = item
        progressMs.longValue = 0L
        isPlaying.value = true
        isCrossfading.value = false
        lastCrossfadeTriggeredUri = null
        PlaybackQueueManager.shared.currentTrack = item
        PlaybackQueueManager.shared.currentPlaylistIndex = currentPlaylistIndex
        ensureSessionValid()
        SpotifyBridge.playTrack(item.uri)
        syncPlaybackServiceNotification(0L, item.durationMs, force = true)
        syncVisibleQueue()
        preloadUpcomingTrack()
    }

    private fun syncPlaybackServiceNotification(pos: Long = progressMs.longValue, dur: Long = 0L, force: Boolean = false) {
        val active = livePlayingTrack.value ?: tracksState.getOrNull(0) ?: return
        val totalDur = if (dur > 0) dur else active.durationMs
        val now = System.currentTimeMillis()
        val trackChanged = active.uri != lastNotifTrackUri
        val stateChanged = isPlaying.value != lastNotifPlaying

        if (force || trackChanged || stateChanged) {
            lastNotifTrackUri = active.uri
            lastNotifPlaying = isPlaying.value
            lastNotifSyncTimeMs = now
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
        } else if (now - lastNotifSyncTimeMs >= 3000L) {
            lastNotifSyncTimeMs = now
            PlaybackService.updatePosition(this, pos, isPlaying.value)
        }
    }

    private fun advanceUpcomingState(upcoming: TrackItem) {
        if (customQueue.isNotEmpty() && customQueue.first().uri == upcoming.uri) {
            customQueue.removeAt(0)
        } else if (currentPlaylistIndex + 1 < playlistQueue.size && playlistQueue[currentPlaylistIndex + 1].uri == upcoming.uri) {
            currentPlaylistIndex++
        } else if (repeatMode.intValue == 1 && playlistQueue.isNotEmpty() && playlistQueue[0].uri == upcoming.uri) {
            currentPlaylistIndex = 0
        }
        PlaybackQueueManager.shared.advanceCrossfade(upcoming)
        livePlayingTrack.value = upcoming
        progressMs.longValue = 0L
        isCrossfading.value = false
        syncVisibleQueue()
        preloadUpcomingTrack()
        syncPlaybackServiceNotification(0L, upcoming.durationMs, force = true)
    }

    private fun addToQueue(item: TrackItem) {
        customQueue.add(item)
        PlaybackQueueManager.shared.addToCustomQueue(item)
        syncVisibleQueue()
        preloadUpcomingTrack()
        Log.d("Queue", "Added '${item.title}' to custom queue (total: ${customQueue.size})")
    }

    private fun removeFromCustomQueue(index: Int) {
        if (index in customQueue.indices) {
            val removed = customQueue.removeAt(index)
            PlaybackQueueManager.shared.removeFromCustomQueue(index)
            syncVisibleQueue()
            preloadUpcomingTrack()
            Log.d("Queue", "Removed '${removed.title}' from custom queue")
        }
    }

    private fun clearCustomQueue() {
        customQueue.clear()
        PlaybackQueueManager.shared.clearCustomQueue()
        syncVisibleQueue()
        preloadUpcomingTrack()
        Log.d("Queue", "Cleared custom queue")
    }

    private fun createPlaylist(name: String, description: String) {
        scope.launch {
            val newPl = SpotifyApi.createPlaylist(this@MainActivity, name, description)
            if (newPl != null) {
                userPlaylistsState.add(0, newPl)
                Toast.makeText(this@MainActivity, "Created playlist '${newPl.name}'", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this@MainActivity, "Failed to create playlist", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun deletePlaylist(playlist: PlaylistItem) {
        scope.launch {
            val ok = SpotifyApi.deletePlaylist(this@MainActivity, playlist.uri)
            if (ok) {
                userPlaylistsState.removeAll { it.uri == playlist.uri }
                Toast.makeText(this@MainActivity, "Deleted playlist '${playlist.name}'", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this@MainActivity, "Failed to delete playlist", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun addTrackToPlaylist(playlist: PlaylistItem, track: TrackItem) {
        scope.launch {
            val ok = SpotifyApi.addTrackToPlaylist(this@MainActivity, playlist.uri, track.uri)
            if (ok) {
                Toast.makeText(this@MainActivity, "Added '${track.title}' to ${playlist.name}", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this@MainActivity, "Failed to add track to playlist", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun removeTrackFromPlaylist(playlist: PlaylistItem, track: TrackItem) {
        scope.launch {
            val ok = SpotifyApi.removeTrackFromPlaylist(this@MainActivity, playlist.uri, track.uri)
            if (ok) {
                Toast.makeText(this@MainActivity, "Removed '${track.title}' from ${playlist.name}", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this@MainActivity, "Failed to remove track from playlist", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun playTrackItem(item: TrackItem, queueContext: List<TrackItem>? = null, contextName: String? = null, stopAtEnd: Boolean = stopAtEndOfQueue.value) {
        stopAtEndOfQueue.value = stopAtEnd
        if (!contextName.isNullOrEmpty()) {
            queueContextName.value = contextName
        } else if (item.album.isNotEmpty()) {
            queueContextName.value = item.album
        }
        if (!queueContext.isNullOrEmpty()) {
            isShuffle.value = false
            originalQueueBeforeShuffle.clear()
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
        PlaybackQueueManager.shared.setPlaylistContext(
            tracks = playlistQueue.toList(),
            startIndex = currentPlaylistIndex,
            stopAtEnd = stopAtEndOfQueue.value
        )
        PlaybackQueueManager.shared.saveState(this)
        playTrackInternal(item)
    }

    private fun onTrackClick(idx: Int) {
        val clicked = tracksState.getOrNull(idx) ?: return
        if (idx == 0 && livePlayingTrack.value?.uri == clicked.uri) {
            if (!isPlaying.value) {
                togglePlay()
            }
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
        val curr = livePlayingTrack.value
        if (!isPlaying.value) {
            isPlaying.value = true
            ensureSessionValid()
            if (curr != null) {
                val ok = SpotifyBridge.play()
                if (!ok) {
                    Log.d("Playback", "SpotifyBridge.play() failed or player was stopped -> reloading track '${curr.title}'")
                    val pos = progressMs.longValue
                    playTrackInternal(curr)
                    if (pos > 1000L) {
                        scope.launch {
                            delay(300)
                            SpotifyBridge.seek(pos)
                        }
                    }
                } else {
                    syncPlaybackServiceNotification(force = true)
                }
            } else if (tracksState.isNotEmpty()) {
                playTrackItem(tracksState[0], tracksState.toList())
            }
        } else {
            isPlaying.value = false
            SpotifyBridge.pause()
            syncPlaybackServiceNotification(force = true)
        }
    }

    private fun toggleShuffle() {
        isShuffle.value = !isShuffle.value
        PlaybackQueueManager.shared.isShuffle = isShuffle.value
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
                PlaybackQueueManager.shared.playlistQueue.clear()
                PlaybackQueueManager.shared.playlistQueue.addAll(playlistQueue)
                PlaybackQueueManager.shared.currentPlaylistIndex = 0
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
                PlaybackQueueManager.shared.playlistQueue.clear()
                PlaybackQueueManager.shared.playlistQueue.addAll(playlistQueue)
                PlaybackQueueManager.shared.currentPlaylistIndex = currentPlaylistIndex
                syncVisibleQueue()
                preloadUpcomingTrack()
            }
        }
    }

    private fun toggleRepeat() {
        // cycle: 0 (off) -> 1 (repeat all) -> 2 (repeat one) -> 0
        repeatMode.intValue = (repeatMode.intValue + 1) % 3
        PlaybackQueueManager.shared.repeatMode = PlaybackRepeatMode.fromInt(repeatMode.intValue)
        preloadUpcomingTrack()
    }

    private fun nextTrack(isAutoAdvance: Boolean = false) {
        if (isAutoAdvance && sleepTimerMinutes.intValue == -1) {
            Log.d("SleepTimer", "End of track reached with End-of-Track Sleep Timer -> pausing")
            SpotifyBridge.pause()
            isPlaying.value = false
            sleepTimerMinutes.intValue = 0
            sleepTimerRemainingSeconds.intValue = 0
            progressMs.longValue = 0L
            syncPlaybackServiceNotification(0L)
            Toast.makeText(this, "Sleep timer finished. Playback paused at end of track.", Toast.LENGTH_SHORT).show()
            return
        }

        if (repeatMode.intValue == 2) {
            // Repeat one: replay current track from start
            val curr = livePlayingTrack.value
            if (curr != null) {
                playTrackInternal(curr)
            } else {
                SpotifyBridge.seek(0)
                SpotifyBridge.play()
            }
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

    private fun performLogout() {
        SpotifyBridge.pause()
        isPlaying.value = false
        livePlayingTrack.value = null
        progressMs.longValue = 0L
        PlaybackService.listener?.onPause()
        syncPlaybackServiceNotification(0L)
        SecureStorage.clearAuth(this)
        prefs.edit()
            .remove(KEY_LOGIN_BLOB)
            .remove(KEY_ACCESS_TOKEN)
            .remove(KEY_REFRESH_TOKEN)
            .remove(KEY_TOKEN_EXPIRES_AT)
            .remove(KEY_CACHED_PLAYLISTS)
            .remove("saved_track_uris")
            .apply()
        accessTokenState.value = null
        userPlaylistsState.clear()
        tracksState.clear()
        customQueue.clear()
        playlistQueue.clear()
        authState.value = AuthState.LoggedOut
        Toast.makeText(this, "Logged out of Spotify", Toast.LENGTH_SHORT).show()
    }

    override fun onResume() {
        super.onResume()
        val qTrack = PlaybackQueueManager.shared.currentTrack
        if (qTrack != null && qTrack.uri != livePlayingTrack.value?.uri) {
            livePlayingTrack.value = qTrack
            currentPlaylistIndex = PlaybackQueueManager.shared.currentPlaylistIndex
            syncVisibleQueue()
        }
    }

    override fun onSaveInstanceState(outState: android.os.Bundle) {
        super.onSaveInstanceState(outState)
        PlaybackQueueManager.shared.saveState(this)
    }

    override fun onDestroy() {
        PlaybackQueueManager.shared.saveState(this)
        super.onDestroy()
        PlaybackService.listener = null
        authServer?.close()
        playbackTickerJob?.cancel()
        scope.cancel()
    }
}
