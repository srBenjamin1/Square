package dev.lelonio.square.ui

import dev.lelonio.square.data.CatalogArtist
import dev.lelonio.square.data.CatalogPlaylist
import dev.lelonio.square.data.CatalogTrack
import dev.lelonio.square.ui.player.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class ResolvedPlayerMetadataTest {
    private val sparse = CatalogTrack("spotify:track:song", "Song", "Main, Guest")
    private val full = sparse.copy(album = "Actual record", albumUri = "spotify:album:record",
        artists = listOf(CatalogArtist("Main", "spotify:artist:main"), CatalogArtist("Guest", "spotify:artist:guest")))

    @Test fun sparseSessionGetsActualAlbumAndSeparateArtistsBeforeAnyTap() = runBlocking {
        val result = resolvePlayerMetadata(sparse, { assertEquals(sparse.uri, it); full }, { error("Already resolved") })
        assertEquals("Actual record", result.album)
        assertEquals("spotify:album:record", PlayerMetadata.album(result)!!.uri)
        assertEquals(listOf("spotify:artist:main", "spotify:artist:guest"), PlayerMetadata.artists(result).map { it.destination!!.uri })
    }
    @Test fun existingAlbumResolverFillsMissingNameEvenWhenAlbumIdAlreadyExists() = runBlocking {
        val result = resolvePlayerMetadata(sparse.copy(albumUri = full.albumUri), { null }, {
            assertEquals(sparse.uri, it); CatalogPlaylist(full.albumUri!!, "Actual record")
        })
        assertEquals("Actual record", result.album)
        assertNotNull(PlayerMetadata.album(result))
    }
    @Test fun innerTubeIdentityIsResolvedByExactSongAndKeepsItsService() = runBlocking {
        val source = sparse.copy(uri = "ytmusic:video:abc_DEF-123")
        val result = resolvePlayerMetadata(source, {
            assertEquals(source.uri, it)
            source.copy(album = "YT record", albumUri = "ytmusic:album:MPRE_abc-123",
                artists = listOf(CatalogArtist("Guest", "ytmusic:artist:UC_guest-123")))
        }, { error("No Spotify album lookup") })
        assertEquals("YT record", result.album)
        assertEquals("ytmusic:artist:UC_guest-123", PlayerMetadata.artists(result).single().destination!!.uri)
        assertEquals("ytmusic:album:MPRE_abc-123", PlayerMetadata.album(result)!!.uri)
    }
    @Test fun localNamesRemainVisibleWithoutAnyRemoteLookup() = runBlocking {
        val result = resolvePlayerMetadata(full.copy(uri = "content://media/audio/12"), { error("No remote lookup") }, { error("No remote album") })
        assertEquals("Actual record", result.album)
        assertTrue(PlayerMetadata.artists(result).all { it.destination == null })
        assertNull(PlayerMetadata.album(result))
    }
    @Test fun unresolvedNamesRemainReadableWithoutFabricatedLinks() = runBlocking {
        val result = resolvePlayerMetadata(sparse.copy(album = "Known name"), { null }, { null })
        assertEquals("Known name", result.album)
        assertNull(PlayerMetadata.album(result))
        assertTrue(PlayerMetadata.artists(result).all { it.destination == null })
    }
    @Test fun unrelatedSongCannotSupplyNamesOrIdentities() = runBlocking {
        val result = resolvePlayerMetadata(sparse, { full.copy(uri = "spotify:track:other") }, { null })
        assertEquals("", result.album)
        assertNull(PlayerMetadata.album(result))
    }
    @Test fun completeIdentityDoesNotFetchAgain() = runBlocking {
        val result = resolvePlayerMetadata(full, { error("No repeated request") }, { error("No repeated album request") })
        assertEquals("Actual record", result.album)
    }
    @Test fun cancellationDoesNotResolveOrStartAFallback() = runBlocking {
        try {
            resolvePlayerMetadata(sparse, { throw CancellationException() }, { error("No fallback") })
            fail("Expected cancellation")
        } catch (_: CancellationException) { }
    }
    @Test fun joinedBylineWithoutIndividualIdentitiesCannotOpenTheFirstArtist() {
        val state = sparse.copy(artistUri = "spotify:artist:main").metadataState()
        assertNull(PlayerMetadata.artists(state).single().destination)
    }
}
