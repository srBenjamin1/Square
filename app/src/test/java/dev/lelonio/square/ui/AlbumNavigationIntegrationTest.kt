package dev.lelonio.square.ui

import dev.lelonio.square.backend.BackendId
import dev.lelonio.square.data.CatalogPlaylist
import dev.lelonio.square.data.CatalogTrack
import dev.lelonio.square.data.MetadataDestination
import dev.lelonio.square.thor.ui.MetadataNavigationQueue
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test

class AlbumNavigationIntegrationTest {
    private val song = CatalogTrack("spotify:track:song", "Song", "Artist", album = "Record")
    private val full = song.copy(albumUri = "spotify:album:record")

    @Test fun missingSessionAlbumResolvesWithoutWebApiThenTravelsToLowerHostAfterToolsClose() = runBlocking {
        val events = mutableListOf<String>()
        val queue = MetadataNavigationQueue()
        val closed = CompletableDeferred<Unit>()
        var toolsOpen = true
        val history = mutableListOf("library")
        val album = trackAlbum(song) { uri ->
            resolveSpotifyAlbum(uri, nativeTracks = { requested ->
                assertEquals(listOf(song.uri), requested); listOf(full)
            }, webAlbum = { error("Optional Web API must not be required") })
        }!!
        val destination = MetadataDestination.create(MetadataDestination.Type.ALBUM, album.uri, album.name, BackendId.SPOTIFY)!!
        val request = queue.request(destination)
        val receiver = async(start = CoroutineStart.UNDISPATCHED) {
            val received = queue.pending.first { it.isNotEmpty() }.first()
            val handler = MetadataNavigationHandler(
                activeService = { BackendId.SPOTIFY },
                closeTools = { events += "closing"; closed.await(); toolsOpen = false; events += "closed" },
                openPage = { target ->
                    assertFalse(toolsOpen)
                    history += target.uri
                    events += "opened"
                },
            )
            assertTrue(handler.open(received.destination))
            queue.consume(received.id)
        }
        assertEquals(listOf("closing"), events)
        assertEquals(listOf("library"), history)
        assertEquals(request, queue.pending.value.single())
        closed.complete(Unit)
        receiver.await()
        assertEquals(listOf("closing", "closed", "opened"), events)
        assertEquals(listOf("library", "spotify:album:record"), history)
        history.removeAt(history.lastIndex)
        assertEquals("library", history.last())
        assertTrue(queue.pending.value.isEmpty())
    }
    @Test fun innerTubeAlbumUsesItsExistingIdWithoutSpotifyLookup() = runBlocking {
        val youtube = song.copy(uri = "ytmusic:track:song", albumUri = "ytmusic:album:MPRE123")
        val album = trackAlbum(youtube) { error("No Spotify lookup for YouTube") }!!
        val destination = MetadataDestination.create(MetadataDestination.Type.ALBUM, album.uri, album.name, BackendId.YOUTUBE_MUSIC)!!
        val events = mutableListOf<String>()
        val handler = MetadataNavigationHandler({ BackendId.YOUTUBE_MUSIC }, { events += "closed" }, { events += it.uri })
        assertTrue(handler.open(destination))
        assertEquals(listOf("closed", "ytmusic:album:MPRE123"), events)
    }
    @Test fun nativeFailureUsesExistingWebApiByTrackId() = runBlocking {
        val result = resolveSpotifyAlbum(song.uri, { error("offline native catalogue") }, { uri ->
            assertEquals(song.uri, uri); CatalogPlaylist("spotify:album:web", "Record")
        })
        assertEquals("spotify:album:web", result!!.uri)
    }
    @Test fun unrelatedNativeTrackCannotSupplyTheAlbum() = runBlocking {
        val result = resolveSpotifyAlbum(song.uri, { listOf(full.copy(uri = "spotify:track:other")) }, { null })
        assertNull(result)
    }
    @Test fun unavailableOrForeignResultsCannotGenerateNavigation() = runBlocking {
        assertNull(resolveSpotifyAlbum(song.uri, { emptyList() }, { null }))
        assertNull(resolveSpotifyAlbum(song.uri, { listOf(full.copy(albumUri = "ytmusic:album:MPRE123")) }, {
            CatalogPlaylist("ytmusic:album:MPRE123", "Other service")
        }))
        assertNull(trackAlbum(song.copy(uri = "local:track:1", albumUri = "spotify:album:record")) { error("No local lookup") })
    }
    @Test fun cancellationIsNotTurnedIntoASecondCatalogueRequest() = runBlocking {
        try {
            resolveSpotifyAlbum(song.uri, { throw CancellationException("cancelled") }, { error("No fallback after cancellation") })
            fail("Expected cancellation")
        } catch (_: CancellationException) { }
    }
    @Test fun backendMismatchDoesNotCloseToolsOrOpenWrongCatalogue() = runBlocking {
        val destination = MetadataDestination.create(MetadataDestination.Type.ALBUM, "spotify:album:record", "Record", BackendId.SPOTIFY)!!
        val handler = MetadataNavigationHandler({ BackendId.YOUTUBE_MUSIC }, { error("Must not close tools") }, { error("Must not open page") })
        assertFalse(handler.open(destination))
    }
    @Test fun backendChangeWhileClosingCannotOpenAStaleDestination() = runBlocking {
        var backend = BackendId.SPOTIFY
        val destination = MetadataDestination.create(MetadataDestination.Type.ARTIST, "spotify:artist:artist", "Artist", backend)!!
        val handler = MetadataNavigationHandler({ backend }, { backend = BackendId.YOUTUBE_MUSIC }, { error("Must not open stale page") })
        assertFalse(handler.open(destination))
    }
}
