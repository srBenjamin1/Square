package dev.lelonio.square.data.local

import dev.lelonio.square.data.*
import dev.lelonio.square.ui.local.LocalLayoutPolicy
import dev.lelonio.square.ui.player.PlayerVisualPolicy
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class LocalPresentationIntegrationTest {
    private fun source(name:String)=listOf(File("src/main/java/dev/lelonio/square/$name"),File("app/src/main/java/dev/lelonio/square/$name")).first { it.isFile }.readText()
    @Test fun compactWindowsUseSmallerSharedTilesWithoutResolutionConstants() {
        val phone=LocalLayoutPolicy.forWindow(412,915)
        val thorLike=LocalLayoutPolicy.forWindow(640,360)
        val landscape=LocalLayoutPolicy.forWindow(915,412)
        assertFalse(phone.compact); assertTrue(thorLike.compact); assertTrue(landscape.compact)
        assertEquals(152f,phone.albumSize)
        assertTrue(thorLike.albumSize<phone.albumSize)
        assertTrue(thorLike.songSize>=88f)
        assertTrue(thorLike.artistSize>=80f)
    }
    @Test fun portraitAndLargeWindowsKeepOriginalSizes() {
        assertFalse(LocalLayoutPolicy.forWindow(600,960).compact)
        assertFalse(LocalLayoutPolicy.forWindow(1280,800).compact)
        assertEquals(152f,LocalLayoutPolicy.forWindow(1280,800).albumSize)
    }
    @Test fun smallLandscapeNeverShrinksCardsBelowTheirLowerBounds() {
        val tiny=LocalLayoutPolicy.forWindow(600,240)
        assertEquals(96f,tiny.albumSize)
        assertEquals(88f,tiny.songSize)
        assertEquals(80f,tiny.artistSize)
    }
    @Test fun localMotionRespectsPreferenceOfflineAndVideoExclusivity() {
        val apple=PreferencesStore.CoverSource.APPLE_MUSIC
        val spotify=PreferencesStore.CoverSource.SPOTIFY
        val uri="local:track:-123"
        assertFalse(PlayerVisualPolicy.motionEnabledForTrack(uri,false,apple,false))
        assertFalse(PlayerVisualPolicy.motionEnabledForTrack(uri,true,apple,true))
        assertFalse(PlayerVisualPolicy.motionEnabledForTrack(uri,true,spotify,false))
        assertTrue(PlayerVisualPolicy.motionEnabledForTrack(uri,true,apple,false))
        val art=AppleCatalog.Album(null,null,motionUrl="portrait",motionSquareUrl="square")
        assertEquals("square",PlayerVisualPolicy.motionUrl(art,true,true,apple,true,false))
        assertNull(PlayerVisualPolicy.motionUrl(art,true,true,apple,true,true))
        assertNull(PlayerVisualPolicy.motionUrl(art,false,true,apple,false,false))
    }
    @Test fun remoteMotionSelectionKeepsItsOriginalBehavior() {
        for (uri in listOf("spotify:track:one","ytmusic:track:one")) {
            assertTrue(PlayerVisualPolicy.motionEnabledForTrack(uri,true,PreferencesStore.CoverSource.SPOTIFY,true))
            assertFalse(PlayerVisualPolicy.motionEnabledForTrack(uri,false,PreferencesStore.CoverSource.APPLE_MUSIC,false))
        }
    }
    @Test fun safTrackUsesTheSameCatalogPlaylistMetadataDestinationsAndQueuePolicy() {
        val track=LocalTrack(LocalFolderPolicy.safId("provider|one"),"content://provider/document/one","Song","Artist","Album",durationMs=60_000,
            documentKey="provider|one",treeUris=listOf("folder"))
        val list=LocalPlaylist("p","Playlist").add(listOf(track.uri))
        val catalog=LocalMusicCatalog(LocalMusicSnapshot(listOf(track),playlists=listOf(list),folders=listOf(LocalMusicFolder("folder","Folder")),folderMode=LocalFolderMode.SELECTED))
        val song=catalog.playlistTracks(list).single().catalog()
        assertEquals(track.uri,song.uri)
        assertTrue(song.uri.startsWith("local:track:-"))
        assertNotNull(MetadataDestination.forTrack(MetadataDestination.Type.ARTIST,track.artistUri,"Artist",track.uri))
        assertNotNull(MetadataDestination.forTrack(MetadataDestination.Type.ALBUM,track.albumUri,"Album",track.uri))
        assertEquals(track.contentUri,catalog.allByUri.getValue(song.uri).contentUri)
        assertEquals(listOf(song.uri),list.tracks)
    }
    @Test fun pickerPersistsReadGrantsAndOnlyExtendsTheExistingPlayerUriResolver() {
        val repo=source("data/local/LocalMusicRepository.kt")
        assertTrue(repo.contains("takePersistableUriPermission"))
        assertTrue(repo.contains("FLAG_GRANT_READ_URI_PERMISSION"))
        assertTrue(repo.contains("releasePersistableUriPermission"))
        assertTrue(source("ui/settings/LocalMusicFoldersSection.kt").contains("ActivityResultContracts.OpenDocumentTree()"))
        assertTrue(source("playback/LocalPlayerFactory.kt").contains("app.localMusic.playableUri(uri)"))
        assertTrue(source("playback/LocalPlayerFactory.kt").contains("content ?: LocalLibrary.contentUri(uri)"))
        assertFalse(source("data/local/LocalSafIndexer.kt").contains("MANAGE_EXTERNAL_STORAGE"))
        assertFalse(source("data/local/LocalSafIndexer.kt").contains("deleteDocument"))
    }
    @Test fun automaticResolutionAndImagesAreBoundedAndDoNotConstructPlayers() {
        val resolver=source("data/local/LocalVisualResolver.kt")
        assertTrue(resolver.contains(".take(3)"))
        assertTrue(resolver.contains("Semaphore(2)"))
        assertTrue(resolver.contains("distinctUntilChanged().collectLatest"))
        assertTrue(resolver.contains("OfflineMode.active"))
        assertFalse(resolver.contains("ExoPlayer.Builder"))
        val apple=source("data/AppleCatalog.kt")
        assertTrue(apple.contains("LocalVisualMatching.ranked(track, candidates)"))
        assertTrue(apple.contains("heroUrl = null, coverUrl = null"))
        assertTrue(source("ui/local/LocalMusicScreen.kt").contains("onArtistVisible"))
    }
}
