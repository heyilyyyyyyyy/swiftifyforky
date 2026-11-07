package org.swiftify.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

enum class PlaybackRepeatMode(val value: Int) {
    OFF(0),
    ALL(1),
    ONE(2);

    fun next(): PlaybackRepeatMode = when (this) {
        OFF -> ALL
        ALL -> ONE
        ONE -> OFF
    }

    companion object {
        fun fromInt(v: Int): PlaybackRepeatMode = when (v) {
            1 -> ALL
            2 -> ONE
            else -> OFF
        }
    }
}

sealed class QueueAdvanceResult {
    data class PlayTrack(val track: TrackItem) : QueueAdvanceResult()
    data class ReplayCurrent(val track: TrackItem) : QueueAdvanceResult()
    object StopPlayback : QueueAdvanceResult()
}

class PlaybackQueueManager {
    companion object {
        val shared = PlaybackQueueManager()
    }

    val playlistQueue = mutableListOf<TrackItem>()
    val customQueue = mutableListOf<TrackItem>()
    val originalQueueBeforeShuffle = mutableListOf<TrackItem>()

    var currentPlaylistIndex: Int = 0
    var currentTrack: TrackItem? = null
    var repeatMode: PlaybackRepeatMode = PlaybackRepeatMode.OFF
    var isShuffle: Boolean = false
    var stopAtEndOfQueue: Boolean = false

    fun setPlaylistContext(tracks: List<TrackItem>, startIndex: Int = 0, stopAtEnd: Boolean = false) {
        playlistQueue.clear()
        playlistQueue.addAll(tracks)
        customQueue.clear()
        originalQueueBeforeShuffle.clear()
        isShuffle = false
        stopAtEndOfQueue = stopAtEnd
        currentPlaylistIndex = if (startIndex in tracks.indices) startIndex else 0
        currentTrack = playlistQueue.getOrNull(currentPlaylistIndex)
    }

    fun addToCustomQueue(track: TrackItem) {
        customQueue.add(track)
    }

    fun removeFromCustomQueue(index: Int): TrackItem? {
        return if (index in customQueue.indices) customQueue.removeAt(index) else null
    }

    fun clearCustomQueue() {
        customQueue.clear()
    }

    fun getUpcomingTrack(): TrackItem? {
        if (customQueue.isNotEmpty()) {
            return customQueue.first()
        }
        if (repeatMode == PlaybackRepeatMode.ONE) {
            return currentTrack
        }
        if (currentPlaylistIndex + 1 < playlistQueue.size) {
            return playlistQueue[currentPlaylistIndex + 1]
        }
        if (repeatMode == PlaybackRepeatMode.ALL && playlistQueue.isNotEmpty()) {
            return playlistQueue[0]
        }
        return null
    }

    fun getVisibleQueue(limit: Int = 30): List<TrackItem> {
        val list = mutableListOf<TrackItem>()
        currentTrack?.let { list.add(it) }
        list.addAll(customQueue)
        if (playlistQueue.isNotEmpty() && currentPlaylistIndex in playlistQueue.indices) {
            val remaining = playlistQueue.subList((currentPlaylistIndex + 1).coerceAtMost(playlistQueue.size), playlistQueue.size)
            list.addAll(remaining)
        }
        return list.take(limit)
    }

    fun next(): QueueAdvanceResult {
        if (repeatMode == PlaybackRepeatMode.ONE) {
            val curr = currentTrack
            return if (curr != null) QueueAdvanceResult.ReplayCurrent(curr) else QueueAdvanceResult.StopPlayback
        }

        if (customQueue.isNotEmpty()) {
            val nextTrack = customQueue.removeAt(0)
            currentTrack = nextTrack
            return QueueAdvanceResult.PlayTrack(nextTrack)
        }

        if (stopAtEndOfQueue && currentPlaylistIndex + 1 >= playlistQueue.size) {
            return QueueAdvanceResult.StopPlayback
        }

        if (currentPlaylistIndex + 1 < playlistQueue.size) {
            currentPlaylistIndex++
            val nextTrack = playlistQueue[currentPlaylistIndex]
            currentTrack = nextTrack
            return QueueAdvanceResult.PlayTrack(nextTrack)
        } else if (repeatMode == PlaybackRepeatMode.ALL && playlistQueue.isNotEmpty()) {
            currentPlaylistIndex = 0
            val nextTrack = playlistQueue[0]
            currentTrack = nextTrack
            return QueueAdvanceResult.PlayTrack(nextTrack)
        } else {
            return QueueAdvanceResult.StopPlayback
        }
    }

    fun advanceCrossfade(upcoming: TrackItem) {
        if (customQueue.isNotEmpty() && customQueue.first().uri == upcoming.uri) {
            customQueue.removeAt(0)
        } else if (currentPlaylistIndex + 1 < playlistQueue.size && playlistQueue[currentPlaylistIndex + 1].uri == upcoming.uri) {
            currentPlaylistIndex++
        } else if (repeatMode == PlaybackRepeatMode.ALL && playlistQueue.isNotEmpty() && playlistQueue[0].uri == upcoming.uri) {
            currentPlaylistIndex = 0
        }
        currentTrack = upcoming
    }

    fun previous(currentPosMs: Long = 0L): QueueAdvanceResult {
        val curr = currentTrack
        if (currentPosMs > 3000L || repeatMode == PlaybackRepeatMode.ONE || curr == null) {
            return if (curr != null) QueueAdvanceResult.ReplayCurrent(curr) else QueueAdvanceResult.StopPlayback
        }

        if (currentPlaylistIndex > 0 && playlistQueue.isNotEmpty()) {
            currentPlaylistIndex--
            val prev = playlistQueue[currentPlaylistIndex]
            currentTrack = prev
            return QueueAdvanceResult.PlayTrack(prev)
        } else if (repeatMode == PlaybackRepeatMode.ALL && playlistQueue.isNotEmpty()) {
            currentPlaylistIndex = playlistQueue.size - 1
            val prev = playlistQueue[currentPlaylistIndex]
            currentTrack = prev
            return QueueAdvanceResult.PlayTrack(prev)
        } else {
            return QueueAdvanceResult.ReplayCurrent(curr)
        }
    }

    fun toggleShuffle() {
        isShuffle = !isShuffle
        if (isShuffle) {
            if (playlistQueue.isNotEmpty()) {
                originalQueueBeforeShuffle.clear()
                originalQueueBeforeShuffle.addAll(playlistQueue)
                val curr = currentTrack ?: playlistQueue.getOrNull(currentPlaylistIndex)
                val rest = playlistQueue.filter { it.uri != curr?.uri }.shuffled()
                playlistQueue.clear()
                if (curr != null) playlistQueue.add(curr)
                playlistQueue.addAll(rest)
                currentPlaylistIndex = 0
            }
        } else {
            if (originalQueueBeforeShuffle.isNotEmpty()) {
                val curr = currentTrack ?: playlistQueue.getOrNull(currentPlaylistIndex)
                playlistQueue.clear()
                playlistQueue.addAll(originalQueueBeforeShuffle)
                val idx = playlistQueue.indexOfFirst { it.uri == curr?.uri }
                currentPlaylistIndex = if (idx >= 0) idx else 0
            }
        }
    }

    fun saveState(context: Context) {
        try {
            val prefs = context.getSharedPreferences("swiftify_queue_state", Context.MODE_PRIVATE)
            val jsonArray = JSONArray()
            for (track in playlistQueue) {
                val obj = JSONObject()
                obj.put("title", track.title)
                obj.put("artist", track.artist)
                obj.put("album", track.album)
                obj.put("artworkUrl", track.artworkUrl)
                obj.put("durationMs", track.durationMs)
                obj.put("uri", track.uri)
                obj.put("albumUri", track.albumUri)
                obj.put("artistUri", track.artistUri)
                jsonArray.put(obj)
            }
            prefs.edit()
                .putString("queue_json", jsonArray.toString())
                .putInt("current_index", currentPlaylistIndex)
                .putInt("repeat_mode", repeatMode.value)
                .putBoolean("is_shuffle", isShuffle)
                .putBoolean("stop_at_end", stopAtEndOfQueue)
                .apply()
        } catch (_: Exception) {}
    }

    fun restoreState(context: Context): Boolean {
        try {
            val prefs = context.getSharedPreferences("swiftify_queue_state", Context.MODE_PRIVATE)
            val jsonStr = prefs.getString("queue_json", null) ?: return false
            val jsonArray = JSONArray(jsonStr)
            val restored = mutableListOf<TrackItem>()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                restored.add(
                    TrackItem(
                        title = obj.optString("title"),
                        artist = obj.optString("artist"),
                        album = obj.optString("album"),
                        artworkUrl = obj.optString("artworkUrl"),
                        durationMs = obj.optLong("durationMs"),
                        uri = obj.optString("uri"),
                        albumUri = obj.optString("albumUri"),
                        artistUri = obj.optString("artistUri")
                    )
                )
            }
            if (restored.isNotEmpty()) {
                playlistQueue.clear()
                playlistQueue.addAll(restored)
                val idx = prefs.getInt("current_index", 0)
                currentPlaylistIndex = if (idx in restored.indices) idx else 0
                currentTrack = playlistQueue.getOrNull(currentPlaylistIndex)
                repeatMode = PlaybackRepeatMode.fromInt(prefs.getInt("repeat_mode", 0))
                isShuffle = prefs.getBoolean("is_shuffle", false)
                stopAtEndOfQueue = prefs.getBoolean("stop_at_end", false)
                return true
            }
        } catch (_: Exception) {}
        return false
    }
}
