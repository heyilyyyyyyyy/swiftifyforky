package org.swiftify.app

import android.content.Context

/**
 * Throttles playback notification updates: track/state changes are pushed
 * immediately, position-only updates at most once every three seconds.
 */
class PlaybackNotifier(private val context: Context) {
    private var lastTrackUri: String? = null
    private var lastPlaying: Boolean? = null
    private var lastSyncTimeMs: Long = 0L

    fun sync(track: TrackItem?, isPlaying: Boolean, pos: Long, dur: Long, force: Boolean) {
        val active = track ?: return
        val totalDur = if (dur > 0) dur else active.durationMs
        val now = System.currentTimeMillis()
        val trackChanged = active.uri != lastTrackUri
        val stateChanged = isPlaying != lastPlaying

        if (force || trackChanged || stateChanged) {
            lastTrackUri = active.uri
            lastPlaying = isPlaying
            lastSyncTimeMs = now
            PlaybackService.updateNotification(
                context,
                active.title,
                active.artist,
                active.album,
                active.artworkUrl,
                isPlaying,
                pos,
                totalDur
            )
        } else if (now - lastSyncTimeMs >= 3000L) {
            lastSyncTimeMs = now
            PlaybackService.updatePosition(context, pos, isPlaying)
        }
    }
}
