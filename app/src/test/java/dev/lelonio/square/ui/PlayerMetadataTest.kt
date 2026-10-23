package dev.lelonio.square.ui

import dev.lelonio.square.data.CatalogArtist
import dev.lelonio.square.ui.player.PlaybackState
import dev.lelonio.square.ui.player.PlayerMetadata
import org.junit.Assert.*
import org.junit.Test

class PlayerMetadataTest {
    private val state = PlaybackState(mediaId = "spotify:track:song", artist = "First, Guest, Second",
        artistUri = "spotify:artist:first", artists = listOf(
            CatalogArtist("First", "spotify:artist:first"), CatalogArtist("Guest"),
            CatalogArtist("Second", "spotify:artist:second")), album = "Record", albumUri = "spotify:album:record")

    @Test fun eachArtistRetainsTheirOwnIdAndAnUnknownGuestCannotOpenTheFirstArtist() {
        val links = PlayerMetadata.artists(state)
        assertEquals(listOf("First", "Guest", "Second"), links.map { it.name })
        assertEquals("spotify:artist:first", links[0].destination!!.uri)
        assertNull(links[1].destination)
        assertEquals("spotify:artist:second", links[2].destination!!.uri)
    }
    @Test fun homonymsAreNotMergedOrResolvedByName() {
        val links = PlayerMetadata.artists(state.copy(artists = listOf(
            CatalogArtist("Same name", "spotify:artist:one"), CatalogArtist("Same name", "spotify:artist:two"))))
        assertEquals(listOf("spotify:artist:one", "spotify:artist:two"), links.map { it.destination!!.uri })
    }
    @Test fun youtubeArtistAndAlbumUseOnlyInnerTubeBrowseIds() {
        val youtube = state.copy(mediaId = "ytmusic:track:song", artists = listOf(
            CatalogArtist("Artist", "ytmusic:artist:UC123")), albumUri = "ytmusic:album:MPRE123")
        assertEquals("ytmusic:artist:UC123", PlayerMetadata.artists(youtube).single().destination!!.uri)
        assertEquals("ytmusic:album:MPRE123", PlayerMetadata.album(youtube)!!.uri)
        assertNull(PlayerMetadata.album(youtube.copy(albumUri = "spotify:album:record")))
    }
    @Test fun localMetadataStaysReadableEvenWithStaleRemoteIds() {
        val local = state.copy(mediaId = "local:track:1")
        assertEquals(listOf("First", "Guest", "Second"), PlayerMetadata.artists(local).map { it.name })
        assertTrue(PlayerMetadata.artists(local).all { it.destination == null })
        assertNull(PlayerMetadata.album(local))
    }
    @Test fun missingOrForeignIdentitiesStayPlainAndSingleArtistCanUseExistingPrimaryId() {
        val single = state.copy(artists = emptyList(), artist = "First")
        assertEquals("spotify:artist:first", PlayerMetadata.artists(single).single().destination!!.uri)
        assertNull(PlayerMetadata.artists(single.copy(artistUri = null)).single().destination)
        assertNull(PlayerMetadata.artists(state.copy(artists = listOf(CatalogArtist("Name", "ytmusic:artist:UC123")))).single().destination)
    }
    @Test fun contributorDataRemainsInformationalEvenWhenTheProviderHasAnArtistUri() {
        val people = listOf(
            dev.lelonio.square.backend.spotify.SpotifyCredits.Person("Composer", listOf("composer"), null),
            dev.lelonio.square.backend.spotify.SpotifyCredits.Person("Producer", listOf("producer"), "spotify:artist:producer"),
        )
        val credits = dev.lelonio.square.backend.spotify.SpotifyCredits.Credits("Song",
            listOf(dev.lelonio.square.backend.spotify.SpotifyCredits.Role("Writers", people)), listOf("Label"))
        assertEquals(listOf("Composer", "Producer"), credits.roles.single().people.map { it.name })
        assertEquals(listOf("composer", "producer"), credits.roles.single().people.flatMap { it.subroles })
        assertEquals("spotify:artist:producer", credits.roles.single().people[1].uri)
        assertEquals(listOf("Label"), credits.sources)
    }
    @Test fun albumAndArtistHaveIndependentDestinations() {
        assertEquals("spotify:album:record", PlayerMetadata.album(state)!!.uri)
        assertEquals("spotify:artist:first", PlayerMetadata.artists(state).first().destination!!.uri)
        assertNull(PlayerMetadata.album(state.copy(albumUri = "")))
    }
}
