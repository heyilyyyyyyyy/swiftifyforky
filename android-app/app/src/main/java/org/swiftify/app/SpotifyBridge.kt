package org.swiftify.app

import android.util.Log
import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Pointer

// ─── JNA interface matching the real C-ABI in capi.rs ───────────────────────
// Synchronous (fallible) calls return *mut c_char (null = ok, else error msg)
// Async calls return void and push a Completion; collect with take_completion.
// All async calls take (core, request_id: u64, ...string args).
// ────────────────────────────────────────────────────────────────────────────
interface SwiftifyCoreLib : Library {

    // lifecycle
    fun swiftify_core_new(): Pointer?
    fun swiftify_core_free(core: Pointer?)

    // async – connect  (request_id is Long in JNA = u64)
    fun swiftify_core_connect(core: Pointer?, requestId: Long, accessToken: String?, clientId: String?)
    fun swiftify_core_connect_stored(core: Pointer?, requestId: Long, login: String?, clientId: String?)

    // async – playback
    fun swiftify_core_play_track(core: Pointer?, requestId: Long, spotifyUri: String?)

    // async – metadata & library
    fun swiftify_core_album_json(core: Pointer?, requestId: Long, albumUri: String?)
    fun swiftify_core_search_artists_json(core: Pointer?, requestId: Long, query: String?)
    fun swiftify_core_playlist_json(core: Pointer?, requestId: Long, playlistUri: String?)
    fun swiftify_core_artist_top_tracks_json(core: Pointer?, requestId: Long, artistUri: String?)
    fun swiftify_core_lyrics_json(core: Pointer?, requestId: Long, spotifyUri: String?)
    fun swiftify_core_search_tracks_json(core: Pointer?, requestId: Long, query: String?)
    fun swiftify_core_user_playlists_json(core: Pointer?, requestId: Long)
    fun swiftify_core_collection_tracks_json(core: Pointer?, requestId: Long)

    // sync – playback control (return error string or null)
    fun swiftify_core_play(core: Pointer?): Pointer?
    fun swiftify_core_pause(core: Pointer?): Pointer?
    fun swiftify_core_seek(core: Pointer?, positionMs: Int): Pointer?
    fun swiftify_core_set_volume(core: Pointer?, volume: Float): Pointer?
    fun swiftify_core_set_stream_quality(core: Pointer?, kbps: Int): Pointer?
    fun swiftify_core_set_volume_normalization(core: Pointer?, enabled: Boolean): Pointer?
    fun swiftify_core_set_audio_cache(core: Pointer?, path: String?, limitBytes: Long): Pointer?

    // sync – connect toggle
    fun swiftify_core_set_spotify_connect(core: Pointer?, enabled: Boolean, deviceId: String?): Pointer?

    // completion polling
    fun swiftify_core_take_completion(
        core: Pointer?,
        requestId: LongArray?,
        ok: BooleanArray?,
        payload: Array<Pointer?>?
    ): Boolean

    // session queries
    fun swiftify_core_session_is_valid(core: Pointer?): Boolean
    fun swiftify_core_playback_position_ms(core: Pointer?): Int
    fun swiftify_core_playback_duration_ms(core: Pointer?): Int
    fun swiftify_core_take_end_of_track(core: Pointer?): Boolean
    fun swiftify_core_current_track(core: Pointer?): Pointer?

    // string free
    fun swiftify_string_free(str: Pointer?)

    companion object {
        val INSTANCE: SwiftifyCoreLib by lazy {
            Native.load("spotify_core", SwiftifyCoreLib::class.java)
        }
    }
}

// ─── High-level bridge ───────────────────────────────────────────────────────
object SpotifyBridge {
    private const val TAG = "SpotifyBridge"
    private const val CLIENT_ID = "65b708073fc0480ea92a077233ca87bd"

    @Volatile private var coreHandle: Pointer? = null
    @Volatile private var nextRequestId: Long = 1L

    // request IDs for in-flight async calls so we can match completions
    @Volatile var connectRequestId: Long = -1L
    @Volatile var playTrackRequestId: Long = -1L
    private val pendingCompletions = java.util.concurrent.ConcurrentHashMap<Long, (Boolean, String) -> Unit>()

    // callback set by MainActivity to react to completions
    var onConnectSuccess: ((login: String) -> Unit)? = null
    var onConnectError: ((error: String) -> Unit)? = null
    var onPlayTrackError: ((error: String) -> Unit)? = null

    fun init() {
        if (coreHandle == null) {
            try {
                coreHandle = SwiftifyCoreLib.INSTANCE.swiftify_core_new()
                Log.d(TAG, "SwiftifyCore initialized: $coreHandle")
                // Do NOT enable Spotify Connect during initial token auth:
                // Spirc Connect requires stored credentials, otherwise AP returns BAD_REQUEST.
                SwiftifyCoreLib.INSTANCE.swiftify_core_set_spotify_connect(
                    coreHandle, false, "Nothing Phone (1)"
                )
            } catch (e: Throwable) {
                Log.e(TAG, "Failed to initialize SwiftifyCore", e)
            }
        }
    }

    private fun nextId(): Long {
        val id = nextRequestId
        nextRequestId++
        return id
    }

    // ── auth ──────────────────────────────────────────────────────────────────

    fun connect(accessToken: String) {
        val core = coreHandle ?: return
        val id = nextId()
        connectRequestId = id
        SwiftifyCoreLib.INSTANCE.swiftify_core_connect(core, id, accessToken, CLIENT_ID)
        Log.d(TAG, "swiftify_core_connect dispatched (request=$id)")
    }

    fun connectStored(login: String) {
        val core = coreHandle ?: return
        val id = nextId()
        connectRequestId = id
        SwiftifyCoreLib.INSTANCE.swiftify_core_connect_stored(core, id, login, CLIENT_ID)
        Log.d(TAG, "swiftify_core_connect_stored dispatched (request=$id)")
    }

    // ── playback ─────────────────────────────────────────────────────────────

    fun playTrack(uri: String) {
        val core = coreHandle ?: return
        val id = nextId()
        playTrackRequestId = id
        SwiftifyCoreLib.INSTANCE.swiftify_core_play_track(core, id, uri)
        Log.d(TAG, "swiftify_core_play_track($uri) dispatched (request=$id)")
    }

    fun play() {
        coreHandle?.let { h ->
            val err = SwiftifyCoreLib.INSTANCE.swiftify_core_play(h)
            if (err != null) SwiftifyCoreLib.INSTANCE.swiftify_string_free(err)
        }
    }

    fun pause() {
        coreHandle?.let { h ->
            val err = SwiftifyCoreLib.INSTANCE.swiftify_core_pause(h)
            if (err != null) SwiftifyCoreLib.INSTANCE.swiftify_string_free(err)
        }
    }

    fun seek(posMs: Long) {
        coreHandle?.let { h ->
            val err = SwiftifyCoreLib.INSTANCE.swiftify_core_seek(h, posMs.toInt())
            if (err != null) SwiftifyCoreLib.INSTANCE.swiftify_string_free(err)
        }
    }

    fun setSpotifyConnect(enabled: Boolean) {
        coreHandle?.let { h ->
            val err = SwiftifyCoreLib.INSTANCE.swiftify_core_set_spotify_connect(h, enabled, "Nothing Phone (1)")
            if (err != null) SwiftifyCoreLib.INSTANCE.swiftify_string_free(err)
        }
    }

    fun setStreamQuality(kbps: Int) {
        coreHandle?.let { h ->
            val err = SwiftifyCoreLib.INSTANCE.swiftify_core_set_stream_quality(h, kbps)
            if (err != null) SwiftifyCoreLib.INSTANCE.swiftify_string_free(err)
        }
    }

    fun setVolumeNormalization(enabled: Boolean) {
        coreHandle?.let { h ->
            val err = SwiftifyCoreLib.INSTANCE.swiftify_core_set_volume_normalization(h, enabled)
            if (err != null) SwiftifyCoreLib.INSTANCE.swiftify_string_free(err)
        }
    }

    fun setAudioCache(path: String, limitBytes: Long) {
        coreHandle?.let { h ->
            val err = SwiftifyCoreLib.INSTANCE.swiftify_core_set_audio_cache(h, path, limitBytes)
            if (err != null) SwiftifyCoreLib.INSTANCE.swiftify_string_free(err)
        }
    }

    fun fetchPlaylist(uri: String, onResult: (Boolean, String) -> Unit) {
        val core = coreHandle ?: return
        val id = nextId()
        pendingCompletions[id] = onResult
        SwiftifyCoreLib.INSTANCE.swiftify_core_playlist_json(core, id, uri)
    }

    fun fetchArtistTopTracks(uri: String, onResult: (Boolean, String) -> Unit) {
        val core = coreHandle ?: return
        val id = nextId()
        pendingCompletions[id] = onResult
        SwiftifyCoreLib.INSTANCE.swiftify_core_artist_top_tracks_json(core, id, uri)
    }

    fun fetchLyrics(uri: String, onResult: (Boolean, String) -> Unit) {
        val core = coreHandle ?: return
        val id = nextId()
        pendingCompletions[id] = onResult
        SwiftifyCoreLib.INSTANCE.swiftify_core_lyrics_json(core, id, uri)
    }

    fun searchTracks(query: String, onResult: (Boolean, String) -> Unit) {
        val core = coreHandle ?: return
        val id = nextId()
        pendingCompletions[id] = onResult
        SwiftifyCoreLib.INSTANCE.swiftify_core_search_tracks_json(core, id, query)
    }

    fun fetchAlbum(uri: String, onResult: (Boolean, String) -> Unit) {
        val core = coreHandle ?: return
        val id = nextId()
        pendingCompletions[id] = onResult
        SwiftifyCoreLib.INSTANCE.swiftify_core_album_json(core, id, uri)
    }

    fun searchArtists(query: String, onResult: (Boolean, String) -> Unit) {
        val core = coreHandle ?: return
        val id = nextId()
        pendingCompletions[id] = onResult
        SwiftifyCoreLib.INSTANCE.swiftify_core_search_artists_json(core, id, query)
    }

    fun fetchUserPlaylists(onResult: (Boolean, String) -> Unit) {
        val core = coreHandle ?: return
        val id = nextId()
        pendingCompletions[id] = onResult
        SwiftifyCoreLib.INSTANCE.swiftify_core_user_playlists_json(core, id)
    }

    fun fetchCollectionTracks(onResult: (Boolean, String) -> Unit) {
        val core = coreHandle ?: return
        val id = nextId()
        pendingCompletions[id] = onResult
        SwiftifyCoreLib.INSTANCE.swiftify_core_collection_tracks_json(core, id)
    }

    // ── polling ──────────────────────────────────────────────────────────────

    /** Call from a coroutine loop ~200 ms. Dispatches completions to callbacks. */
    fun pollCompletions() {
        val core = coreHandle ?: return
        val reqId = LongArray(1)
        val ok = BooleanArray(1)
        val payload = arrayOfNulls<Pointer>(1)

        while (SwiftifyCoreLib.INSTANCE.swiftify_core_take_completion(core, reqId, ok, payload)) {
            val id = reqId[0]
            val success = ok[0]
            val payloadPtr = payload[0]
            val text = if (payloadPtr != null) {
                val s = payloadPtr.getString(0, "UTF-8")
                SwiftifyCoreLib.INSTANCE.swiftify_string_free(payloadPtr)
                s
            } else ""

            Log.d(TAG, "completion id=$id ok=$success payload='$text'")

            when (id) {
                connectRequestId -> {
                    if (success) onConnectSuccess?.invoke(text)
                    else onConnectError?.invoke(text)
                }
                playTrackRequestId -> {
                    if (!success) onPlayTrackError?.invoke(text)
                }
                else -> {
                    pendingCompletions.remove(id)?.invoke(success, text)
                }
            }
        }
    }

    // ── queries ───────────────────────────────────────────────────────────────

    fun isSessionValid(): Boolean =
        SwiftifyCoreLib.INSTANCE.swiftify_core_session_is_valid(coreHandle)

    fun playbackPositionMs(): Long =
        SwiftifyCoreLib.INSTANCE.swiftify_core_playback_position_ms(coreHandle).toLong()

    fun playbackDurationMs(): Long =
        SwiftifyCoreLib.INSTANCE.swiftify_core_playback_duration_ms(coreHandle).toLong()

    fun takeEndOfTrack(): Boolean =
        SwiftifyCoreLib.INSTANCE.swiftify_core_take_end_of_track(coreHandle)

    fun currentTrackJson(): String? {
        val handle = coreHandle ?: return null
        val ptr = SwiftifyCoreLib.INSTANCE.swiftify_core_current_track(handle) ?: return null
        val str = ptr.getString(0)
        SwiftifyCoreLib.INSTANCE.swiftify_string_free(ptr)
        return if (str.isNotEmpty()) str else null
    }
}
