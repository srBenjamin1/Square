package dev.lelonio.square.ui

import dev.lelonio.square.ui.components.spotifyLargeUrl
import org.junit.Assert.*
import org.junit.Test

class PlaylistArtworkTest {
    @Test fun knownSpotifyFamiliesGetLargestRendition() {
        val tail = "0123456789abcdef01234567"
        for ((family, rendition) in mapOf("ab67706f" to "00000004", "ab67706c" to "0000bebb", "ab67616d" to "000082c1", "ab676161" to "0000e5eb")) {
            assertEquals("https://i.scdn.co/image/$family$rendition$tail", spotifyLargeUrl("https://i.scdn.co/image/${family}00000002$tail"))
        }
    }
    @Test fun mosaicsAreLargerAndOtherProvidersAreUnchanged() {
        assertEquals("https://mosaic.scdn.co/640/picture", spotifyLargeUrl("https://mosaic.scdn.co/300/picture"))
        assertEquals("https://youtube/cover", spotifyLargeUrl("https://youtube/cover"))
    }
}
