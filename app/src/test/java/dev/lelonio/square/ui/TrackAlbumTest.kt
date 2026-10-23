package dev.lelonio.square.ui

import dev.lelonio.square.data.CatalogPlaylist
import dev.lelonio.square.data.CatalogTrack
import dev.lelonio.square.thor.ui.MetadataNavigationQueue
import dev.lelonio.square.data.MetadataDestination
import dev.lelonio.square.backend.BackendId
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
        val song = track("ytmusic:track:1", "ytmusic:album:1")
        assertTrue(canOpenAlbum(song))
        assertEquals("ytmusic:album:1", trackAlbum(song) { error("unexpected lookup") }!!.uri)
        assertFalse(canOpenAlbum(song.copy(albumUri = null)))
        assertNull(trackAlbum(song.copy(albumUri = null)) { error("unexpected lookup") })
    }
    @Test fun missingLookupIsSafeAndBlankAlbumIsUnavailable() = runBlocking {
        assertNull(trackAlbum(track("spotify:track:1")) { null })
        assertFalse(canOpenAlbum(track("local:track:1", "")))
    }
    @Test fun topAlbumRequestWaitsForBottomConsumptionAndCannotClearNewerRequest() {
        val queue = MetadataNavigationQueue()
        val first = MetadataDestination.create(MetadataDestination.Type.ALBUM, "spotify:album:1", "First", BackendId.SPOTIFY)!!
        val second = MetadataDestination.create(MetadataDestination.Type.ALBUM, "spotify:album:2", "Second", BackendId.SPOTIFY)!!
        val request = queue.request(first)
        assertEquals(first, queue.pending.value.first().destination)
        val newer = queue.request(second)
        queue.consume(request.id)
        assertEquals(second, queue.pending.value.single().destination)
        queue.consume(newer.id)
        assertTrue(queue.pending.value.isEmpty())
    }
}
