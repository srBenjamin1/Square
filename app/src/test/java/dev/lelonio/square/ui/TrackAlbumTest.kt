package dev.lelonio.square.ui

import dev.lelonio.square.data.CatalogPlaylist
import dev.lelonio.square.data.CatalogTrack
import dev.lelonio.square.thor.ui.ThorAlbumNavigation
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class TrackAlbumTest {
    private fun track(uri: String, albumUri: String? = null) = CatalogTrack(uri = uri, name = "Song", artist = "Artist", album = "Record", albumUri = albumUri, artworkUrl = "cover")
    @Test fun spotifyWithoutAlbumUsesLookup() = runBlocking {
        val song = track("spotify:track:1")
        val album = CatalogPlaylist("spotify:album:1", "Record")
        assertTrue(canOpenAlbum(song))
        assertEquals(album, trackAlbum(song) { assertEquals(song.uri, it); album })
    }
    @Test fun youtubeUsesProvidedAlbumAndDoesNotAttemptSpotifyLookup() = runBlocking {
        val song = track("youtube:track:1", "youtube:album:1")
        assertTrue(canOpenAlbum(song))
        assertEquals("youtube:album:1", trackAlbum(song) { error("unexpected lookup") }!!.uri)
        assertFalse(canOpenAlbum(song.copy(albumUri = null)))
        assertNull(trackAlbum(song.copy(albumUri = null)) { error("unexpected lookup") })
    }
    @Test fun missingLookupIsSafeAndBlankAlbumIsUnavailable() = runBlocking {
        assertNull(trackAlbum(track("spotify:track:1")) { null })
        assertFalse(canOpenAlbum(track("local:track:1", "")))
    }
    @Test fun topAlbumRequestWaitsForBottomConsumptionAndCannotClearNewerRequest() {
        val first = CatalogPlaylist("spotify:album:1", "First")
        val second = first.copy(uri = "spotify:album:2")
        ThorAlbumNavigation.request(first)
        assertEquals(first, ThorAlbumNavigation.pending.value)
        ThorAlbumNavigation.request(second)
        ThorAlbumNavigation.consume(first)
        assertEquals(second, ThorAlbumNavigation.pending.value)
        ThorAlbumNavigation.consume(second)
        assertNull(ThorAlbumNavigation.pending.value)
    }
}
