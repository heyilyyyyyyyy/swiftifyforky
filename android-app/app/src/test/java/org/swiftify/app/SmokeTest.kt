package org.swiftify.app

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.MessageDigest
import java.util.Base64

class SmokeTest {

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
