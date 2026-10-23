package dev.lelonio.square.data

import dev.lelonio.square.backend.BackendId
import org.junit.Assert.*
import org.junit.Test

class MetadataDestinationTest {
    @Test fun spotifyAndYouTubeKeepTheirRealCatalogueAddressAndType() {
        for ((service, prefix) in listOf(BackendId.SPOTIFY to "spotify", BackendId.YOUTUBE_MUSIC to "ytmusic")) {
            for (type in MetadataDestination.Type.entries) {
                val kind = type.name.lowercase()
                val uri = "$prefix:$kind:abc123"
                val destination = MetadataDestination.create(type, uri, "Same name", service, "cover")!!
                assertEquals(uri, destination.asPlaylist().uri)
                assertEquals(service, destination.service)
                assertEquals(type, destination.type)
                assertEquals("Same name", destination.asPlaylist().name)
                assertEquals("cover", destination.asPlaylist().artworkUrl)
            }
        }
    }
    @Test fun identitiesCannotCrossServicesOrDestinationKinds() {
        assertNull(MetadataDestination.create(MetadataDestination.Type.ARTIST, "ytmusic:artist:UC123", "Name", BackendId.SPOTIFY))
        assertNull(MetadataDestination.create(MetadataDestination.Type.ALBUM, "spotify:album:abc", "Name", BackendId.YOUTUBE_MUSIC))
        assertNull(MetadataDestination.create(MetadataDestination.Type.ARTIST, "spotify:album:abc", "Name", BackendId.SPOTIFY))
        assertNull(MetadataDestination.create(MetadataDestination.Type.ALBUM, "ytmusic:artist:UC123", "Name", BackendId.YOUTUBE_MUSIC))
    }
    @Test fun invalidMissingAndExternalAddressesAreNotLinks() {
        for (uri in listOf(null, "", "spotify:artist:", "spotify:artist:abc def", "spotify:artist:abc/def",
            "spotify:artist:abc:extra", "https://open.spotify.com/artist/abc", "local:artist:abc")) {
            assertNull(MetadataDestination.create(MetadataDestination.Type.ARTIST, uri, "Name", BackendId.SPOTIFY))
        }
        assertNull(MetadataDestination.create(MetadataDestination.Type.ARTIST, "spotify:artist:abc", "Name", null))
    }
    @Test fun sourceComesFromTrackIdentityAndNeverLocalNames() {
        assertEquals(BackendId.SPOTIFY, MetadataDestination.serviceOfTrack("spotify:track:abc"))
        assertEquals(BackendId.YOUTUBE_MUSIC, MetadataDestination.serviceOfTrack("ytmusic:track:abc"))
        assertEquals(BackendId.YOUTUBE_MUSIC, MetadataDestination.serviceOfTrack("ytmusic:video:abc"))
        for (uri in listOf(null, "local:track:1", "spotify:local:Artist:Album:Song:200", "content://music/1", "spotify:episode:abc")) {
            assertNull(MetadataDestination.serviceOfTrack(uri))
        }
    }
    @Test fun youtubeBrowseIdsAllowTheExistingAlphabet() {
        assertNotNull(MetadataDestination.create(MetadataDestination.Type.ARTIST, "ytmusic:artist:UC_abc-def", "Name", BackendId.YOUTUBE_MUSIC))
        assertNotNull(MetadataDestination.create(MetadataDestination.Type.ALBUM, "ytmusic:album:MPREb_abc-def", "Name", BackendId.YOUTUBE_MUSIC))
    }
}
