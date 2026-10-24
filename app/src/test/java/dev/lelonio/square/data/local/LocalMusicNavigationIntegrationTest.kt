package dev.lelonio.square.data.local

import dev.lelonio.square.backend.BackendId
import dev.lelonio.square.data.MusicSource
import dev.lelonio.square.data.MetadataDestination
import dev.lelonio.square.thor.ui.MetadataNavigationQueue
import dev.lelonio.square.ui.MetadataNavigationHandler
import dev.lelonio.square.ui.player.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class LocalMusicNavigationIntegrationTest {
    @Test fun indexedAudioTravelsThroughOriginalCatalogMetadataAndLowerNavigation()=runBlocking {
        val row=LocalTrack(5,"content://media/external/audio/media/5","Local song",artist="Local artist",album="Local album",durationMs=150000,fileName="song.flac")
        val indexed=updateLocalIndex(emptyList(),listOf(row),false,{it.copy(albumArtist="Local artist",genre="Rock")})
        val catalog=LocalMusicCatalog(LocalMusicSnapshot(indexed))
        val track=catalog.search("local song").tracks.single()
        assertEquals(row.contentUri,indexed.single().contentUri)
        assertEquals("local:track:5",track.uri)
        assertEquals("Local song",track.name)
        assertEquals("Local artist",track.artist)
        assertEquals(150000,track.durationMs)
        for(target in listOf(PlayerMetadata.artists(track.metadataState()).single().destination!!,PlayerMetadata.album(track.metadataState())!!)) {
            val queue=MetadataNavigationQueue()
            val request=queue.request(target)
            var closed=false
            val history=mutableListOf("library")
            val handler=MetadataNavigationHandler({BackendId.YOUTUBE_MUSIC},{closed=true},{destination->
                assertTrue(closed)
                assertTrue(catalog.tracksOf(destination.uri).isNotEmpty())
                history+=destination.uri
            })
            assertTrue(queue.deliver(request,handler::open))
            assertTrue(queue.pending.value.isEmpty())
            assertEquals(2,history.size)
            history.removeAt(history.lastIndex)
            assertEquals("library",history.single())
        }
    }
    @Test fun localAndRemoteDestinationsStayInTheirOwnCatalog() {
        val track=LocalTrack(1,"content://one","Song",artist="Artist",album="Record").catalog()
        assertNull(MetadataDestination.create(MetadataDestination.Type.ARTIST,track.artistUri,"Artist",BackendId.SPOTIFY))
        assertNotNull(PlayerMetadata.album(track.metadataState()))
        assertNull(PlayerMetadata.album(track.copy(albumUri="ytmusic:album:MPREid").metadataState()))
    }
    @Test fun sourceBrowsingRetainsLocalPlaybackWheneverTheExistingEnginesAllowIt() {
        assertFalse(LocalSourcePlaybackPolicy.needsSpotifyPlayer(MusicSource.LOCAL,true,false))
        assertFalse(LocalSourcePlaybackPolicy.needsSpotifyPlayer(MusicSource.YOUTUBE_MUSIC,true,false))
        assertFalse(LocalSourcePlaybackPolicy.needsSpotifyPlayer(MusicSource.SPOTIFY,true,true))
        assertTrue(LocalSourcePlaybackPolicy.needsSpotifyPlayer(MusicSource.SPOTIFY,true,false))
        assertFalse(LocalSourcePlaybackPolicy.needsSpotifyPlayer(MusicSource.SPOTIFY,false,false))
    }
    @Test fun localEffectsAreAvailableEvenWhenTheLastSpotifyAccountNeedsTheWebSdk() {
        assertFalse(LocalSourcePlaybackPolicy.sdkRestrictions(true,"local:track:1"))
        assertTrue(LocalSourcePlaybackPolicy.sdkRestrictions(true,"spotify:track:1"))
        assertFalse(LocalSourcePlaybackPolicy.sdkRestrictions(false,"spotify:track:1"))
    }

}
