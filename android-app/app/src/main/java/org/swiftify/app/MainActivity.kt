package org.swiftify.app

import org.swiftify.app.design.*
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import kotlinx.coroutines.*
import java.io.File
import java.util.UUID

// ─── Preferences ──────────────────────────────────────────────────────────────

private const val PREFS_NAME = "swiftify_prefs"
private const val KEY_LOGIN_BLOB = "login_blob"
private const val KEY_ACCESS_TOKEN = "access_token"
private const val KEY_REFRESH_TOKEN = "refresh_token"
private const val KEY_TOKEN_EXPIRES_AT = "token_expires_at"

private data class PollResult(val pos: Long, val dur: Long, val trackJson: String?, val trackEnded: Boolean)

// ─── MainActivity ─────────────────────────────────────────────────────────────

class MainActivity : ComponentActivity() {

    private lateinit var prefs: SharedPreferences
    private lateinit var library: LibraryRepository

    // Compose states
    private val authState = mutableStateOf<AuthState>(AuthState.Loading)
    private val accessTokenState = mutableStateOf<String?>(null)
    private val currentThemeMode = mutableStateOf(SwiftifyThemeMode.HEYILY)
    private val tracksState = mutableStateListOf<TrackItem>()
    private val recentlyPlayedState = mutableStateListOf<TrackItem>()
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

    private val auth by lazy {
        SpotifyAuth(
            context = this,
            scope = scope,
            currentAuthState = { authState.value },
            onAuthState = { authState.value = it },
            onStatus = { statusMsg.value = it },
            onToken = { accessTokenState.value = it }
        )
    }
    private val notifier by lazy { PlaybackNotifier(this) }

    private fun setSleepTimer(minutes: Int) {
        sleepTimerJob?.cancel()
        sleepTimerMinutes.intValue = minutes
        if (minutes <= 0) {
            sleepTimerRemainingSeconds.intValue = 0
            if (minutes == 0) {
                Toast.makeText(this, getString(R.string.toast_sleep_timer_off), Toast.LENGTH_SHORT).show()
            }
        } else {
            sleepTimerRemainingSeconds.intValue = minutes * 60
            Toast.makeText(this, getString(R.string.toast_sleep_timer_set, minutes), Toast.LENGTH_SHORT).show()
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
                Toast.makeText(applicationContext, getString(R.string.toast_sleep_timer_finished), Toast.LENGTH_LONG).show()
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
        library = LibraryRepository(this, prefs, scope, recentlyPlayedState, userPlaylistsState)

        // Request runtime notification permission on Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 101)
            }
        }

        // Instant playlists and recently played from offline disk cache (0 ms)
        prefs.getString(KEY_CACHED_PLAYLISTS, null)?.let {
            library.parseAndApplyPlaylists(it)
        }
        library.loadCachedRecentlyPlayed()
        val savedTheme = prefs.getString("app_theme", null)
        if (savedTheme == null || !prefs.contains("custom_heyily_applied")) {
            prefs.edit().putString("app_theme", "heyily").putBoolean("custom_heyily_applied", true).apply()
            currentThemeMode.value = SwiftifyThemeMode.HEYILY
        } else {
            currentThemeMode.value = SwiftifyThemeMode.fromId(savedTheme)
        }

        val devName = getDeviceName()
        SpotifyBridge.init(devName)
        SpotifyBridge.setVolume(1.0f)

        // Apply startup audio, cache & connect settings
        val quality = prefs.getInt("audio_quality_kbps", 320)
        SpotifyBridge.setStreamQuality(quality)
        val norm = prefs.getBoolean("volume_norm", false)
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
            org.swiftify.app.design.SwiftifyTheme(themeMode = currentThemeMode.value) {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    SwiftifyApp(
                        authState = authState.value,
                        tracks = tracksState,
                        recentlyPlayed = recentlyPlayedState,
                        userPlaylists = userPlaylistsState,
                        currentTheme = currentThemeMode.value,
                        onThemeChange = { mode ->
                            currentThemeMode.value = mode
                            prefs.edit().putString("app_theme", mode.id).apply()
                        },
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
                        onCreatePlaylist = { n, d -> library.createPlaylist(n, d) },
                        onDeletePlaylist = { p -> library.deletePlaylist(p) },
                        onAddTrackToPlaylist = { p, t -> library.addTrackToPlaylist(p, t) },
                        onRemoveTrackFromPlaylist = { p, t -> library.removeTrackFromPlaylist(p, t) },
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
        auth.refreshAccessToken()

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
                syncCustomQueue()
                progressMs.longValue = PlaybackQueueManager.shared.currentPositionMs
                if (PlaybackQueueManager.shared.contextName.isNotEmpty()) {
                    queueContextName.value = PlaybackQueueManager.shared.contextName
                }
                syncVisibleQueue()
            }
        }

        if (storedLogin != null || storedToken != null) {
            scope.launch {
                library.loadRecentlyPlayed()
                library.loadUserPlaylists()
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

    private fun startSpotifyLogin() {
        auth.startLogin(this)
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
                library.loadRecentlyPlayed()
                library.loadUserPlaylists()
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

    private var lastCrossfadeTriggeredUri: String? = null

    private fun playTrackInternal(item: TrackItem) {
        livePlayingTrack.value = item
        progressMs.longValue = 0L
        isPlaying.value = true
        isCrossfading.value = false
        lastCrossfadeTriggeredUri = null
        val existingIdx = recentlyPlayedState.indexOfFirst { it.uri == item.uri }
        if (existingIdx >= 0) {
            recentlyPlayedState.removeAt(existingIdx)
        }
        recentlyPlayedState.add(0, item)
        library.cacheRecentlyPlayed(recentlyPlayedState.take(20))
        PlaybackQueueManager.shared.currentTrack = item
        PlaybackQueueManager.shared.currentPlaylistIndex = currentPlaylistIndex
        ensureSessionValid()
        SpotifyBridge.playTrack(item.uri)
        syncPlaybackServiceNotification(0L, item.durationMs, force = true)
        syncVisibleQueue()
        preloadUpcomingTrack()
    }

    private fun syncPlaybackServiceNotification(pos: Long = progressMs.longValue, dur: Long = 0L, force: Boolean = false) {
        notifier.sync(
            track = livePlayingTrack.value ?: tracksState.getOrNull(0),
            isPlaying = isPlaying.value,
            pos = pos,
            dur = dur,
            force = force
        )
    }

    private fun syncCustomQueue() {
        customQueue.clear()
        customQueue.addAll(PlaybackQueueManager.shared.customQueue)
    }

    private fun advanceUpcomingState(upcoming: TrackItem) {
        PlaybackQueueManager.shared.advanceCrossfade(upcoming)
        syncCustomQueue()
        if (currentPlaylistIndex + 1 < playlistQueue.size && playlistQueue[currentPlaylistIndex + 1].uri == upcoming.uri) {
            currentPlaylistIndex++
        } else if (repeatMode.intValue == 1 && playlistQueue.isNotEmpty() && playlistQueue[0].uri == upcoming.uri) {
            currentPlaylistIndex = 0
        }
        livePlayingTrack.value = upcoming
        progressMs.longValue = 0L
        isCrossfading.value = false
        syncVisibleQueue()
        preloadUpcomingTrack()
        PlaybackQueueManager.shared.currentPositionMs = 0L
        PlaybackQueueManager.shared.isPlayingState = true
        PlaybackQueueManager.shared.saveState(this)
        syncPlaybackServiceNotification(0L, upcoming.durationMs, force = true)
    }

    private fun addToQueue(item: TrackItem) {
        PlaybackQueueManager.shared.addToCustomQueue(item)
        syncCustomQueue()
        syncVisibleQueue()
        preloadUpcomingTrack()
        PlaybackQueueManager.shared.saveState(this)
        Log.d("Queue", "Added '${item.title}' to custom queue (total: ${customQueue.size})")
    }

    private fun removeFromCustomQueue(index: Int) {
        val removed = PlaybackQueueManager.shared.removeFromCustomQueue(index)
        syncCustomQueue()
        syncVisibleQueue()
        preloadUpcomingTrack()
        PlaybackQueueManager.shared.saveState(this)
        if (removed != null) {
            Log.d("Queue", "Removed '${removed.title}' from custom queue")
        }
    }

    private fun clearCustomQueue() {
        PlaybackQueueManager.shared.clearCustomQueue()
        syncCustomQueue()
        syncVisibleQueue()
        preloadUpcomingTrack()
        PlaybackQueueManager.shared.saveState(this)
        Log.d("Queue", "Cleared custom queue")
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
            PlaybackQueueManager.shared.clearCustomQueue()
            syncCustomQueue()
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
            stopAtEnd = stopAtEndOfQueue.value,
            contextTitle = queueContextName.value
        )
        PlaybackQueueManager.shared.currentPositionMs = 0L
        PlaybackQueueManager.shared.isPlayingState = true
        PlaybackQueueManager.shared.saveState(this)
        playTrackInternal(item)
    }

    private fun onTrackClick(idx: Int) {
        val list = if (recentlyPlayedState.isNotEmpty()) recentlyPlayedState else tracksState
        val clicked = list.getOrNull(idx) ?: return
        if (idx == 0 && livePlayingTrack.value?.uri == clicked.uri) {
            if (!isPlaying.value) {
                togglePlay()
            }
            return
        }
        val customTarget = PlaybackQueueManager.shared.consumeCustomQueueUpTo(clicked.uri)
        if (customTarget != null) {
            syncCustomQueue()
            PlaybackQueueManager.shared.saveState(this)
            playTrackInternal(clicked)
            return
        }
        val plIdx = playlistQueue.indexOfFirst { it.uri == clicked.uri }
        if (plIdx >= 0) {
            currentPlaylistIndex = plIdx
            playTrackInternal(clicked)
            return
        }
        playTrackItem(clicked, list.toList())
    }

    private fun togglePlay() {
        val curr = livePlayingTrack.value
        if (!isPlaying.value) {
            isPlaying.value = true
            ensureSessionValid()
            if (curr != null) {
                if (curr.durationMs > 0 && progressMs.longValue >= curr.durationMs) {
                    SpotifyBridge.seek(0)
                    progressMs.longValue = 0L
                }
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
            } else if (recentlyPlayedState.isNotEmpty()) {
                playTrackItem(recentlyPlayedState[0], recentlyPlayedState.toList())
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
            Toast.makeText(this, getString(R.string.toast_sleep_timer_finished_track), Toast.LENGTH_SHORT).show()
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
        if (PlaybackQueueManager.shared.customQueue.isNotEmpty()) {
            val next = PlaybackQueueManager.shared.customQueue.removeAt(0)
            syncCustomQueue()
            PlaybackQueueManager.shared.currentTrack = next
            PlaybackQueueManager.shared.currentPositionMs = 0L
            PlaybackQueueManager.shared.isPlayingState = true
            PlaybackQueueManager.shared.saveState(this)
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
            .remove(KEY_CACHED_RECENTLY_PLAYED)
            .remove("saved_track_uris")
            .apply()
        accessTokenState.value = null
        userPlaylistsState.clear()
        tracksState.clear()
        recentlyPlayedState.clear()
        customQueue.clear()
        playlistQueue.clear()
        authState.value = AuthState.LoggedOut
        Toast.makeText(this, getString(R.string.toast_logged_out), Toast.LENGTH_SHORT).show()
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
        PlaybackQueueManager.shared.currentPositionMs = progressMs.longValue
        PlaybackQueueManager.shared.isPlayingState = isPlaying.value
        PlaybackQueueManager.shared.contextName = queueContextName.value
        PlaybackQueueManager.shared.saveState(this)
    }

    override fun onDestroy() {
        PlaybackQueueManager.shared.currentPositionMs = progressMs.longValue
        PlaybackQueueManager.shared.isPlayingState = isPlaying.value
        PlaybackQueueManager.shared.contextName = queueContextName.value
        PlaybackQueueManager.shared.saveState(this)
        super.onDestroy()
        PlaybackService.listener = null
        auth.close()
        playbackTickerJob?.cancel()
        scope.cancel()
    }
}
