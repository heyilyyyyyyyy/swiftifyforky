package org.swiftify.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.os.Build
import android.os.IBinder
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.core.app.NotificationCompat
import coil.ImageLoader
import coil.request.ImageRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class PlaybackService : Service() {

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
        const val EXTRA_ARTWORK_URL = "extra_artwork_url"
        const val EXTRA_IS_PLAYING = "extra_is_playing"

        fun updateNotification(
            context: Context,
            title: String,
            artist: String,
            artworkUrl: String,
            isPlaying: Boolean
        ) {
            val intent = Intent(context, PlaybackService::class.java).apply {
                action = ACTION_UPDATE
                putExtra(EXTRA_TITLE, title)
                putExtra(EXTRA_ARTIST, artist)
                putExtra(EXTRA_ARTWORK_URL, artworkUrl)
                putExtra(EXTRA_IS_PLAYING, isPlaying)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }

    private var mediaSession: MediaSessionCompat? = null
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var currentTitle = "Swiftify"
    private var currentArtist = "Connecting..."
    private var currentArtUrl = ""
    private var isPlaying = false
    private var cachedBitmap: Bitmap? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()

        mediaSession = MediaSessionCompat(this, "SwiftifyMediaSession").apply {
            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlay() {
                    SpotifyBridge.play()
                    updateNotificationInternal()
                }

                override fun onPause() {
                    SpotifyBridge.pause()
                    updateNotificationInternal()
                }

                override fun onSkipToNext() {
                    // Handled in MainActivity or via broadcast
                }

                override fun onSkipToPrevious() {
                    // Handled in MainActivity or via broadcast
                }
            })
            isActive = true
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY -> {
                SpotifyBridge.play()
                isPlaying = true
                updateNotificationInternal()
            }
            ACTION_PAUSE -> {
                SpotifyBridge.pause()
                isPlaying = false
                updateNotificationInternal()
            }
            ACTION_UPDATE -> {
                currentTitle = intent.getStringExtra(EXTRA_TITLE) ?: currentTitle
                currentArtist = intent.getStringExtra(EXTRA_ARTIST) ?: currentArtist
                val newUrl = intent.getStringExtra(EXTRA_ARTWORK_URL) ?: ""
                isPlaying = intent.getBooleanExtra(EXTRA_IS_PLAYING, false)

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

    private fun updateNotificationInternal() {
        val playPauseAction = if (isPlaying) {
            val pauseIntent = PendingIntent.getService(
                this, 1,
                Intent(this, PlaybackService::class.java).apply { action = ACTION_PAUSE },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            NotificationCompat.Action.Builder(
                android.R.drawable.ic_media_pause, "Pause", pauseIntent
            ).build()
        } else {
            val playIntent = PendingIntent.getService(
                this, 2,
                Intent(this, PlaybackService::class.java).apply { action = ACTION_PLAY },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            NotificationCompat.Action.Builder(
                android.R.drawable.ic_media_play, "Play", playIntent
            ).build()
        }

        val activityIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stateBuilder = PlaybackStateCompat.Builder()
            .setActions(
                PlaybackStateCompat.ACTION_PLAY or
                        PlaybackStateCompat.ACTION_PAUSE or
                        PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                        PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS
            )
            .setState(
                if (isPlaying) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED,
                SpotifyBridge.playbackPositionMs(),
                1f
            )
        mediaSession?.setPlaybackState(stateBuilder.build())

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(currentTitle)
            .setContentText(currentArtist)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentIntent(activityIntent)
            .setOngoing(isPlaying)
            .addAction(playPauseAction)
            .setStyle(
                androidx.media.app.NotificationCompat.MediaStyle()
                    .setMediaSession(mediaSession?.sessionToken)
                    .setShowActionsInCompactView(0)
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
        mediaSession?.release()
        scope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
