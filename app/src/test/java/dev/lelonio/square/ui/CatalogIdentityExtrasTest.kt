package dev.lelonio.square.ui

import dev.lelonio.square.data.CatalogArtist
import dev.lelonio.square.data.CatalogTrack
import dev.lelonio.square.ui.player.PlaybackState
import dev.lelonio.square.ui.player.PlayerMetadata
import org.junit.Assert.*
import org.junit.Test

class CatalogIdentityExtrasTest {
    @Test fun bothServicesKeepAlbumAndEveryArtistAcrossTheThorMetadataContract() {
        for (prefix in listOf("spotify", "ytmusic")) {
            val track = CatalogTrack(uri = "$prefix:track:song", name = "Song", artist = "First, Guest, Second",
                artistUri = "$prefix:artist:first", album = "Album", albumUri = "$prefix:album:album",
                artists = listOf(CatalogArtist("First", "$prefix:artist:first"), CatalogArtist("Guest"),
                    CatalogArtist("Second", "$prefix:artist:second")))
            val extras = catalogIdentityExtras(track)
            val state = PlaybackState(mediaId = track.uri, artist = track.artist, album = track.album,
                albumUri = extras.values[EXTRA_ALBUM_URI], artistUri = extras.values[EXTRA_ARTIST_URI],
                artists = extras.lists.getValue(EXTRA_ARTIST_NAMES).zip(extras.lists.getValue(EXTRA_ARTIST_URIS)) { name, uri ->
                    CatalogArtist(name, uri)
                })
            assertEquals(track.albumUri, PlayerMetadata.album(state)!!.uri)
            assertEquals(listOf("$prefix:artist:first", null, "$prefix:artist:second"),
                PlayerMetadata.artists(state).map { it.destination?.uri })
        }
    }
    @Test fun missingIdentityDoesNotInventAnAddress() {
        val extras = catalogIdentityExtras(CatalogTrack("local:track:1", "Song", "Artist", album = "Album"))
        assertFalse(extras.values.containsKey(EXTRA_ALBUM_URI))
        assertFalse(extras.values.containsKey(EXTRA_ARTIST_URI))
        assertTrue(extras.lists.getValue(EXTRA_ARTIST_NAMES).isEmpty())
    }
}
