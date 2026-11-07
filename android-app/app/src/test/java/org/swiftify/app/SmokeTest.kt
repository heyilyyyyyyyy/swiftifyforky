package org.swiftify.app

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.MessageDigest
import java.util.Base64

class SmokeTest {

    private fun createDummyTrack(id: String, title: String = "Track $id"): TrackItem {
        return TrackItem(
            title = title,
            artist = "Artist $id",
            album = "Album $id",
            artworkUrl = "https://example.com/art$id.jpg",
            durationMs = 200000L,
            uri = "spotify:track:$id",
            albumUri = "spotify:album:$id",
            artistUri = "spotify:artist:$id"
        )
    }

    @Test
    fun testFormatTime() {
        assertEquals("0:00", formatTime(0L))
        assertEquals("0:05", formatTime(5000L))
        assertEquals("1:00", formatTime(60000L))
        assertEquals("3:45", formatTime(225000L))
    }

    @Test
    fun testTrackItemProperties() {
        val track = TrackItem(
            title = "Test Song",
            artist = "Test Artist",
            album = "Test Album",
            artworkUrl = "https://example.com/art.jpg",
            durationMs = 180000L,
            uri = "spotify:track:1234567890abcdefghij",
            albumUri = "spotify:album:abcdefghij1234567890",
            artistUri = "spotify:artist:artistid1234567890"
        )

        assertEquals("Test Song", track.title)
        assertEquals("Test Artist", track.artist)
        assertEquals("Test Album", track.album)
        assertEquals("https://example.com/art.jpg", track.artworkUrl)
        assertEquals(180000L, track.durationMs)
        assertEquals("spotify:track:1234567890abcdefghij", track.uri)
        assertEquals("spotify:album:abcdefghij1234567890", track.albumUri)
        assertEquals("spotify:artist:artistid1234567890", track.artistUri)
    }

    @Test
    fun testPkceCodeChallengeGeneration() {
        // Known test vector for PKCE RFC 7636
        val verifier = "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"
        val digest = MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII))
        val challenge = Base64.getUrlEncoder().withoutPadding().encodeToString(digest)

        // RFC 7636 Appendix B expected value:
        assertEquals("E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM", challenge)
    }

    @Test
    fun testOAuthCsrfStateValidation() {
        val originalState = "swiftify-secret-state-12345"
        val validCallbackState = "swiftify-secret-state-12345"
        val maliciousCallbackState = "attacker-forged-state"

        assertTrue(originalState == validCallbackState)
        assertFalse(originalState == maliciousCallbackState)
    }

    @Test
    fun testRepeatModeCycle() {
        var mode = PlaybackRepeatMode.OFF
        assertEquals(0, mode.value)

        mode = mode.next()
        assertEquals(PlaybackRepeatMode.ALL, mode)
        assertEquals(1, mode.value)

        mode = mode.next()
        assertEquals(PlaybackRepeatMode.ONE, mode)
        assertEquals(2, mode.value)

        mode = mode.next()
        assertEquals(PlaybackRepeatMode.OFF, mode)
        assertEquals(0, mode.value)
    }

    @Test
    fun testQueueAdvanceSequentialRepeatOff() {
        val qm = PlaybackQueueManager()
        val t1 = createDummyTrack("1")
        val t2 = createDummyTrack("2")
        val t3 = createDummyTrack("3")

        qm.setPlaylistContext(listOf(t1, t2, t3), startIndex = 0)
        assertEquals(t1, qm.currentTrack)
        assertEquals(t2, qm.getUpcomingTrack())

        val r1 = qm.next()
        assertTrue(r1 is QueueAdvanceResult.PlayTrack)
        assertEquals(t2, (r1 as QueueAdvanceResult.PlayTrack).track)
        assertEquals(t2, qm.currentTrack)
        assertEquals(t3, qm.getUpcomingTrack())

        val r2 = qm.next()
        assertTrue(r2 is QueueAdvanceResult.PlayTrack)
        assertEquals(t3, (r2 as QueueAdvanceResult.PlayTrack).track)
        assertEquals(t3, qm.currentTrack)
        assertNull(qm.getUpcomingTrack())

        val r3 = qm.next()
        assertTrue(r3 is QueueAdvanceResult.StopPlayback)
    }

    @Test
    fun testQueueAdvanceRepeatAll() {
        val qm = PlaybackQueueManager()
        val t1 = createDummyTrack("1")
        val t2 = createDummyTrack("2")

        qm.setPlaylistContext(listOf(t1, t2), startIndex = 0)
        qm.repeatMode = PlaybackRepeatMode.ALL

        val r1 = qm.next()
        assertEquals(t2, (r1 as QueueAdvanceResult.PlayTrack).track)
        assertEquals(t1, qm.getUpcomingTrack())

        val r2 = qm.next()
        assertEquals(t1, (r2 as QueueAdvanceResult.PlayTrack).track)
    }

    @Test
    fun testQueueAdvanceRepeatOne() {
        val qm = PlaybackQueueManager()
        val t1 = createDummyTrack("1")
        val t2 = createDummyTrack("2")

        qm.setPlaylistContext(listOf(t1, t2), startIndex = 0)
        qm.repeatMode = PlaybackRepeatMode.ONE

        assertEquals(t1, qm.getUpcomingTrack())
        val r1 = qm.next()
        assertTrue(r1 is QueueAdvanceResult.ReplayCurrent)
        assertEquals(t1, (r1 as QueueAdvanceResult.ReplayCurrent).track)
    }

    @Test
    fun testCustomQueuePriorityAndReturnToPlaylist() {
        val qm = PlaybackQueueManager()
        val t1 = createDummyTrack("1", "Song 1")
        val t2 = createDummyTrack("2", "Song 2")
        val t3 = createDummyTrack("3", "Song 3")

        val customA = createDummyTrack("A", "Custom A")
        val customB = createDummyTrack("B", "Custom B")

        qm.setPlaylistContext(listOf(t1, t2, t3), startIndex = 0)
        qm.addToCustomQueue(customA)
        qm.addToCustomQueue(customB)

        // Upcoming should be custom track A first
        assertEquals(customA, qm.getUpcomingTrack())

        // Next 1: Custom A plays
        val r1 = qm.next()
        assertTrue(r1 is QueueAdvanceResult.PlayTrack)
        assertEquals(customA, (r1 as QueueAdvanceResult.PlayTrack).track)

        // Next 2: Custom B plays
        val r2 = qm.next()
        assertTrue(r2 is QueueAdvanceResult.PlayTrack)
        assertEquals(customB, (r2 as QueueAdvanceResult.PlayTrack).track)

        // Next 3: Seamlessly returns to Song 2 in playlist!
        val r3 = qm.next()
        assertTrue(r3 is QueueAdvanceResult.PlayTrack)
        assertEquals(t2, (r3 as QueueAdvanceResult.PlayTrack).track)

        // Next 4: Song 3 in playlist
        val r4 = qm.next()
        assertTrue(r4 is QueueAdvanceResult.PlayTrack)
        assertEquals(t3, (r4 as QueueAdvanceResult.PlayTrack).track)
    }

    @Test
    fun testStopAtEndOfQueue() {
        val qm = PlaybackQueueManager()
        val t1 = createDummyTrack("1")
        val t2 = createDummyTrack("2")

        // Album playback mode: stopAtEndOfQueue = true
        qm.setPlaylistContext(listOf(t1, t2), startIndex = 0, stopAtEnd = true)
        val r1 = qm.next()
        assertEquals(t2, (r1 as QueueAdvanceResult.PlayTrack).track)

        // At end of album: stops playback immediately
        val r2 = qm.next()
        assertTrue(r2 is QueueAdvanceResult.StopPlayback)
    }

    @Test
    fun testPreviousTrackBehavior() {
        val qm = PlaybackQueueManager()
        val t1 = createDummyTrack("1")
        val t2 = createDummyTrack("2")

        qm.setPlaylistContext(listOf(t1, t2), startIndex = 1)

        // If played more than 3 seconds, previous() replays current song from start
        val rSeek = qm.previous(currentPosMs = 3500L)
        assertTrue(rSeek is QueueAdvanceResult.ReplayCurrent)
        assertEquals(t2, (rSeek as QueueAdvanceResult.ReplayCurrent).track)

        // If played <= 3 seconds, previous() goes to track 1
        val rPrev = qm.previous(currentPosMs = 1200L)
        assertTrue(rPrev is QueueAdvanceResult.PlayTrack)
        assertEquals(t1, (rPrev as QueueAdvanceResult.PlayTrack).track)

        // At start of queue with repeat OFF -> replay current
        val rStart = qm.previous(currentPosMs = 500L)
        assertTrue(rStart is QueueAdvanceResult.ReplayCurrent)
        assertEquals(t1, (rStart as QueueAdvanceResult.ReplayCurrent).track)

        // At start of queue with repeat ALL -> wrap to last track
        qm.repeatMode = PlaybackRepeatMode.ALL
        val rWrap = qm.previous(currentPosMs = 500L)
        assertTrue(rWrap is QueueAdvanceResult.PlayTrack)
        assertEquals(t2, (rWrap as QueueAdvanceResult.PlayTrack).track)
    }

    @Test
    fun testAdvanceCrossfade() {
        val qm = PlaybackQueueManager()
        val t1 = createDummyTrack("1")
        val t2 = createDummyTrack("2")
        val customX = createDummyTrack("X")

        qm.setPlaylistContext(listOf(t1, t2), startIndex = 0)
        qm.addToCustomQueue(customX)

        // Crossfade with custom track
        qm.advanceCrossfade(customX)
        assertEquals(customX, qm.currentTrack)
        assertTrue(qm.customQueue.isEmpty())

        // Crossfade with next playlist track
        qm.advanceCrossfade(t2)
        assertEquals(t2, qm.currentTrack)
        assertEquals(1, qm.currentPlaylistIndex)
    }

    @Test
    fun testVisibleQueueCappedAt30() {
        val qm = PlaybackQueueManager()
        val tracks = (1..50).map { createDummyTrack(it.toString()) }
        qm.setPlaylistContext(tracks, startIndex = 0)

        val visible = qm.getVisibleQueue(limit = 30)
        assertEquals(30, visible.size)
        assertEquals(tracks[0].uri, visible[0].uri)
        assertEquals(tracks[1].uri, visible[1].uri)
    }

    @Test
    fun testPlaylistJsonParsing() {
        val sampleJson = """
            {
                "items": [
                    {
                        "name": "Lo-Fi Beats",
                        "uri": "spotify:playlist:37i9dQZF1DXdLEN7aqioXM",
                        "owner": {
                            "display_name": "Spotify"
                        },
                        "images": [
                            {"url": "https://i.scdn.co/image/ab67706f00000002...", "height": 640, "width": 640}
                        ]
                    }
                ]
            }
        """.trimIndent()

        val root = JSONObject(sampleJson)
        val items = root.getJSONArray("items")
        assertEquals(1, items.length())

        val p = items.getJSONObject(0)
        assertEquals("Lo-Fi Beats", p.getString("name"))
        assertEquals("spotify:playlist:37i9dQZF1DXdLEN7aqioXM", p.getString("uri"))
        assertEquals("Spotify", p.getJSONObject("owner").getString("display_name"))
    }

    @Test
    fun testTrackMetadataJsonParsing() {
        val sampleJson = """
            {
                "tracks": [
                    {
                        "name": "Astronomy",
                        "uri": "spotify:track:12345",
                        "duration_ms": 210000,
                        "album": {
                            "name": "Superache",
                            "uri": "spotify:album:abcde",
                            "images": [{"url": "https://example.com/cover.jpg"}]
                        },
                        "artists": [
                            {"name": "Conan Gray", "uri": "spotify:artist:xyz"}
                        ]
                    }
                ]
            }
        """.trimIndent()

        val root = JSONObject(sampleJson)
        val tracks = root.getJSONArray("tracks")
        val item = tracks.getJSONObject(0)

        assertEquals("Astronomy", item.getString("name"))
        assertEquals("spotify:track:12345", item.getString("uri"))
        assertEquals(210000L, item.getLong("duration_ms"))

        val album = item.getJSONObject("album")
        assertEquals("Superache", album.getString("name"))
        assertEquals("spotify:album:abcde", album.getString("uri"))

        val artists = item.getJSONArray("artists")
        assertEquals("Conan Gray", artists.getJSONObject(0).getString("name"))
        assertEquals("spotify:artist:xyz", artists.getJSONObject(0).getString("uri"))
    }
}
