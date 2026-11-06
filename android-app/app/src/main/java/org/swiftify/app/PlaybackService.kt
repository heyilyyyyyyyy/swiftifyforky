package org.swiftify.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.core.app.NotificationCompat
import androidx.media.session.MediaButtonReceiver
import coil.ImageLoader
import coil.request.ImageRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

interface PlaybackEventListener {
    fun onPlay()
    fun onPause()
    fun onNext()
    fun onPrevious()
    fun onSeekTo(positionMs: Long)
}

class PlaybackService : Service(), AudioManager.OnAudioFocusChangeListener {

    companion object {
        const val CHANNEL_ID = "swiftify_playback_channel"
        const val NOTIFICATION_ID = 1001

        const val ACTION_PLAY = "org.swiftify.app.ACTION_PLAY"
        const val ACTION_PAUSE = "org.swiftify.app.ACTION_PAUSE"
        const val ACTION_NEXT = "org.swiftify.app.ACTION_NEXT"
        const val ACTION_PREV = "org.swiftify.app.ACTION_PREV"
        const val ACTION_UPDATE = "org.swiftify.app.ACTION_UPDATE"

        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_ARTIST = "extra_artist"
        const val EXTRA_ALBUM = "extra_album"
        const val EXTRA_ARTWORK_URL = "extra_artwork_url"
        const val EXTRA_IS_PLAYING = "extra_is_playing"
        const val EXTRA_POSITION_MS = "extra_position_ms"
        const val EXTRA_DURATION_MS = "extra_duration_ms"

        @Volatile
        var listener: PlaybackEventListener? = null

        fun updateNotification(
            context: Context,
            title: String,
            artist: String,
            album: String = "",
            artworkUrl: String,
            isPlaying: Boolean,
            positionMs: Long = 0L,
            durationMs: Long = 0L
        ) {
            val intent = Intent(context, PlaybackService::class.java).apply {
                action = ACTION_UPDATE
                putExtra(EXTRA_TITLE, title)
                putExtra(EXTRA_ARTIST, artist)
                putExtra(EXTRA_ALBUM, album)
                putExtra(EXTRA_ARTWORK_URL, artworkUrl)
                putExtra(EXTRA_IS_PLAYING, isPlaying)
                putExtra(EXTRA_POSITION_MS, positionMs)
                putExtra(EXTRA_DURATION_MS, durationMs)
            }
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                android.util.Log.e("PlaybackService", "Failed to startForegroundService", e)
            }
        }
    }

    private var mediaSession: MediaSessionCompat? = null
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private var currentTitle = "Swiftify"
    private var currentArtist = "Connecting..."
    private var currentAlbum = ""
    private var currentArtUrl = ""
    private var currentPosMs = 0L
    private var currentDurMs = 0L
    private var isPlaying = false
    private var cachedBitmap: Bitmap? = null

    private var audioManager: AudioManager? = null
    private var audioFocusRequest: AudioFocusRequest? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var isNoisyReceiverRegistered = false

    private val noisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) {
                android.util.Log.d("PlaybackService", "Audio becoming noisy -> pausing playback")
                handlePause()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        audioManager = getSystemService(Context.AUDIO_SERVICE) as? AudioManager

        val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager
        wakeLock = powerManager?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "swiftify:playback_wakelock")

        mediaSession = MediaSessionCompat(this, "SwiftifyMediaSession").apply {
            setFlags(
                MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS or
                        MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS
            )

            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlay() {
                    handlePlay()
                }

                override fun onPause() {
                    handlePause()
                }

                override fun onSkipToNext() {
                    handleNext()
                }

                override fun onSkipToPrevious() {
                    handlePrevious()
                }

                override fun onSeekTo(pos: Long) {
                    handleSeek(pos)
                }

                override fun onStop() {
                    handlePause()
                    abandonAudioFocus()
                    stopForeground(true)
                }
            })
            isActive = true
        }
    }

    private fun handlePlay() {
        if (requestAudioFocus()) {
            isPlaying = true
            acquireWakeLock()
            registerNoisyReceiver()
            SpotifyBridge.play()
            listener?.onPlay()
            updateNotificationInternal()
        }
    }

    private fun handlePause() {
        isPlaying = false
        releaseWakeLock()
        unregisterNoisyReceiver()
        SpotifyBridge.pause()
        listener?.onPause()
        updateNotificationInternal()
    }

    private fun handleNext() {
        listener?.onNext()
    }

    private fun handlePrevious() {
        listener?.onPrevious()
    }

    private fun handleSeek(pos: Long) {
        currentPosMs = pos
        SpotifyBridge.seek(pos)
        listener?.onSeekTo(pos)
        updatePlaybackState()
    }

    private fun requestAudioFocus(): Boolean {
        val am = audioManager ?: return true
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setOnAudioFocusChangeListener(this)
                .build()
            audioFocusRequest = req
            am.requestAudioFocus(req) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        } else {
            @Suppress("DEPRECATION")
            am.requestAudioFocus(this, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN) ==
                    AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        }
    }

    private fun abandonAudioFocus() {
        val am = audioManager ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest?.let { am.abandonAudioFocusRequest(it) }
        } else {
            @Suppress("DEPRECATION")
            am.abandonAudioFocus(this)
        }
    }

    override fun onAudioFocusChange(focusChange: Int) {
        when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS -> {
                android.util.Log.d("PlaybackService", "AudioFocus LOSS -> pause")
                handlePause()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                android.util.Log.d("PlaybackService", "AudioFocus LOSS_TRANSIENT -> pause")
                handlePause()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                android.util.Log.d("PlaybackService", "AudioFocus LOSS_TRANSIENT_CAN_DUCK -> ducking volume")
                SpotifyBridge.seek(currentPosMs) // keep position
            }
            AudioManager.AUDIOFOCUS_GAIN -> {
                android.util.Log.d("PlaybackService", "AudioFocus GAIN -> resume")
                handlePlay()
            }
        }
    }

    private fun registerNoisyReceiver() {
        if (!isNoisyReceiverRegistered) {
            registerReceiver(noisyReceiver, IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY))
            isNoisyReceiverRegistered = true
        }
    }

    private fun unregisterNoisyReceiver() {
        if (isNoisyReceiverRegistered) {
            try {
                unregisterReceiver(noisyReceiver)
            } catch (_: Exception) {}
            isNoisyReceiverRegistered = false
        }
    }

    private fun acquireWakeLock() {
        try {
            if (wakeLock?.isHeld == false) {
                wakeLock?.acquire(3 * 3600 * 1000L) // 3 hours max safe timeout
            }
        } catch (_: Exception) {}
    }

    private fun releaseWakeLock() {
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        } catch (_: Exception) {}
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Handle media button broadcast events from Bluetooth or headphones
        MediaButtonReceiver.handleIntent(mediaSession, intent)

        if (intent == null) {
            // Process was recreated: ensure ongoing notification is kept alive
            updateNotificationInternal()
            return START_STICKY
        }

        when (intent.action) {
            ACTION_PLAY -> handlePlay()
            ACTION_PAUSE -> handlePause()
            ACTION_NEXT -> handleNext()
            ACTION_PREV -> handlePrevious()
            ACTION_UPDATE -> {
                currentTitle = intent.getStringExtra(EXTRA_TITLE) ?: currentTitle
                currentArtist = intent.getStringExtra(EXTRA_ARTIST) ?: currentArtist
                currentAlbum = intent.getStringExtra(EXTRA_ALBUM) ?: currentAlbum
                val newUrl = intent.getStringExtra(EXTRA_ARTWORK_URL) ?: ""
                val wasPlaying = isPlaying
                isPlaying = intent.getBooleanExtra(EXTRA_IS_PLAYING, isPlaying)
                currentPosMs = intent.getLongExtra(EXTRA_POSITION_MS, currentPosMs)
                currentDurMs = intent.getLongExtra(EXTRA_DURATION_MS, currentDurMs)

                if (isPlaying && !wasPlaying) {
                    acquireWakeLock()
                    registerNoisyReceiver()
                } else if (!isPlaying && wasPlaying) {
                    releaseWakeLock()
                    unregisterNoisyReceiver()
                }

                if (newUrl.isNotEmpty() && newUrl != currentArtUrl) {
                    currentArtUrl = newUrl
                    loadArtworkBitmap(newUrl)
                } else {
                    updateNotificationInternal()
                }
            }
        }
        return START_STICKY
    }

    private fun loadArtworkBitmap(url: String) {
        scope.launch(Dispatchers.IO) {
            try {
                val loader = ImageLoader(this@PlaybackService)
                val req = ImageRequest.Builder(this@PlaybackService)
                    .data(url)
                    .allowHardware(false)
                    .build()
                val result = loader.execute(req).drawable
                if (result is BitmapDrawable) {
                    cachedBitmap = result.bitmap
                }
            } catch (_: Exception) {}
            launch(Dispatchers.Main) {
                updateNotificationInternal()
            }
        }
    }

    private fun updatePlaybackState() {
        val stateBuilder = PlaybackStateCompat.Builder()
            .setActions(
                PlaybackStateCompat.ACTION_PLAY or
                        PlaybackStateCompat.ACTION_PAUSE or
                        PlaybackStateCompat.ACTION_PLAY_PAUSE or
                        PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                        PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
                        PlaybackStateCompat.ACTION_SEEK_TO or
                        PlaybackStateCompat.ACTION_STOP
            )
            .setState(
                if (isPlaying) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED,
                currentPosMs,
                if (isPlaying) 1.0f else 0.0f
            )
        mediaSession?.setPlaybackState(stateBuilder.build())
    }

    private fun updateNotificationInternal() {
        updatePlaybackState()

        val metaBuilder = MediaMetadataCompat.Builder()
            .putString(MediaMetadataCompat.METADATA_KEY_TITLE, currentTitle)
            .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, currentArtist)
            .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, currentAlbum)
            .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, currentDurMs)

        cachedBitmap?.let {
            metaBuilder.putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, it)
            metaBuilder.putBitmap(MediaMetadataCompat.METADATA_KEY_ART, it)
        }
        mediaSession?.setMetadata(metaBuilder.build())

        val prevIntent = PendingIntent.getService(
            this, 10,
            Intent(this, PlaybackService::class.java).apply { action = ACTION_PREV },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val prevAction = NotificationCompat.Action.Builder(
            android.R.drawable.ic_media_previous, "Previous", prevIntent
        ).build()

        val playPauseIntent = PendingIntent.getService(
            this, 11,
            Intent(this, PlaybackService::class.java).apply {
                action = if (isPlaying) ACTION_PAUSE else ACTION_PLAY
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val playPauseAction = NotificationCompat.Action.Builder(
            if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
            if (isPlaying) "Pause" else "Play",
            playPauseIntent
        ).build()

        val nextIntent = PendingIntent.getService(
            this, 12,
            Intent(this, PlaybackService::class.java).apply { action = ACTION_NEXT },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val nextAction = NotificationCompat.Action.Builder(
            android.R.drawable.ic_media_next, "Next", nextIntent
        ).build()

        val activityIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(currentTitle)
            .setContentText(currentArtist)
            .setSubText(if (currentAlbum.isNotEmpty()) currentAlbum else null)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentIntent(activityIntent)
            .setOngoing(isPlaying)
            .addAction(prevAction)
            .addAction(playPauseAction)
            .addAction(nextAction)
            .setStyle(
                androidx.media.app.NotificationCompat.MediaStyle()
                    .setMediaSession(mediaSession?.sessionToken)
                    .setShowActionsInCompactView(0, 1, 2)
            )

        cachedBitmap?.let { builder.setLargeIcon(it) }

        startForeground(NOTIFICATION_ID, builder.build())
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Swiftify Playback Controls",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Active music playback controls and artwork"
                setShowBadge(false)
            }
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        releaseWakeLock()
        unregisterNoisyReceiver()
        abandonAudioFocus()
        mediaSession?.release()
        scope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
