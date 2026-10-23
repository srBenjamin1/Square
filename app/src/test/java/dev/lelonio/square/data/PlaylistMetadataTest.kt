package dev.lelonio.square.data

import org.junit.Assert.*
import org.junit.Test

class PlaylistMetadataTest {
    private val sparse = CatalogTrack(uri = "spotify:track:1", name = "Song", artist = "", album = "", addedAt = "2026-10-23")
    private val full = sparse.copy(artist = "Artist", album = "Album", durationMs = 123000, artworkUrl = "https://cover", addedAt = null)

    @Test fun newlyAddedTrackGetsAllMetadataWithoutLosingMembershipOrDuplicates() {
        val result = PlaylistMetadata.complete(listOf(sparse, sparse), listOf(full))
        assertEquals(listOf(full.copy(addedAt = sparse.addedAt), full.copy(addedAt = sparse.addedAt)), result)
        assertFalse(result.any(PlaylistMetadata::isSparse))
    }
    @Test fun failedLookupRetainsRowsAndSparseEntriesRemainUnstamped() {
        assertEquals(listOf(sparse), PlaylistMetadata.complete(listOf(sparse), emptyList()))
        assertTrue(PlaylistMetadata.complete(listOf(sparse), listOf(sparse)).any(PlaylistMetadata::isSparse))
    }
    @Test fun completeLocalAndYouTubeTracksAreNotReplaced() {
        val local = sparse.copy(uri = "local:track:1")
        val youtube = sparse.copy(uri = "youtube:track:1")
        val tracks = listOf(full, local, youtube)
        assertEquals(tracks, PlaylistMetadata.complete(tracks, listOf(full.copy(name = "Other"), local.copy(artist = "Other"), youtube.copy(artist = "Other"))))
    }
}
