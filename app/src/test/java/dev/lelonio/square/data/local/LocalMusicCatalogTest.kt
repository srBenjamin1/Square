package dev.lelonio.square.data.local

import dev.lelonio.square.data.MusicSource
import dev.lelonio.square.backend.BackendId
import dev.lelonio.square.data.MetadataDestination
import dev.lelonio.square.ui.player.PlayerMetadata
import dev.lelonio.square.ui.player.metadataState
import org.junit.Assert.*
import org.junit.Test

class LocalMusicCatalogTest {
    private fun track(id: Long, title: String = "Song $id", artist: String = "Artist", album: String = "Record", albumArtist: String = "", albumId: Long = 0, disc: Int = 1, number: Int = 1, folder: String = "external/Music/Record") =
        LocalTrack(id, "content://media/external/audio/media/$id", title, artist, album, albumArtist, albumId,
            track = number, disc = disc, folder = folder, fileName = "$id.flac", size = 100, modifiedSeconds = 1)
    private fun catalog(vararg tracks: LocalTrack) = LocalMusicCatalog(LocalMusicSnapshot(tracks.toList()))

    @Test fun thirdSourceDoesNotBecomeARemoteBackend() {
        assertEquals(listOf("SPOTIFY", "YOUTUBE_MUSIC", "LOCAL"), MusicSource.entries.map { it.name })
        assertEquals(listOf("SPOTIFY", "YOUTUBE_MUSIC"), BackendId.entries.map { it.name })
        assertNull(MusicSource.LOCAL.backend)
    }
    @Test fun oldSelectionsRemainCompatibleAndLocalSelectionSurvivesRestore() {
        for (backend in BackendId.entries) {
            assertEquals(MusicSource.of(backend), MusicSource.restore(null, backend))
            assertEquals(MusicSource.of(backend), MusicSource.restore("unknown", backend))
            for (source in MusicSource.entries) assertEquals(source, MusicSource.restore(source.name, backend))
        }
    }
    @Test fun allSourceTransitionsHaveAStablePersistedRepresentation() {
        for (from in MusicSource.entries) for (to in MusicSource.entries) {
            val oldBackend = from.backend ?: BackendId.YOUTUBE_MUSIC
            val nextBackend = to.backend ?: oldBackend
            assertEquals(to, MusicSource.restore(to.name, nextBackend))
            if (to == MusicSource.LOCAL) assertEquals(oldBackend, nextBackend)
        }
    }
    @Test fun homonymousAlbumsWithDifferentArtistsDoNotMergeWithoutAlbumIdentity() {
        assertEquals(2, catalog(track(1, artist = "One"), track(2, artist = "Two")).albums.size)
    }
    @Test fun albumArtistGroupsCompilationAndDiscNumbersDetermineTheOrder() {
        val a = track(1, artist = "Guest 1", albumArtist = "Various Artists", disc = 2, number = 1)
        val b = track(2, artist = "Guest 2", albumArtist = "Various Artists", disc = 1, number = 8)
        val c = track(3, artist = "Guest 3", albumArtist = "Various Artists", disc = 1, number = 2)
        val album = catalog(a,b,c).albums.single()
        assertEquals("Various Artists", album.artist)
        assertEquals(listOf(3L,2L,1L), album.tracks.map { it.id })
    }
    @Test fun albumArtistProtectsAgainstAStoreIdSharedByHomonyms() {
        assertEquals(2, catalog(track(1, albumArtist = "One", albumId = 50), track(2, albumArtist = "Two", albumId = 50)).albums.size)
    }
    @Test fun mediaStoreAlbumIdentityGroupsCompilationWithoutInventingAlbumArtist() {
        val c = catalog(track(1, artist = "One", albumId = 5), track(2, artist = "Two", albumId = 5))
        assertEquals(1, c.albums.size)
        assertEquals(setOf("One", "Two"), c.albums.single().tracks.map { it.artist }.toSet())
        assertTrue(c.tracks.all { it.albumArtist.isBlank() })
    }
    @Test fun artistWhitespaceAndCaseAreNormalizedWithoutSplittingCompoundNames() {
        val c = catalog(track(1, artist = " Earth, Wind & Fire "), track(2, artist = "earth,  wind & fire"))
        assertEquals(1, c.artists.size)
        assertEquals(2, c.artists.single().tracks.size)
        assertEquals(1, c.tracks.first().catalog().artists.size)
    }
    @Test fun everyLocalArtistKeepsTheirOwnIdentity() {
        val c = catalog(track(1, artist = "Main"), track(2, artist = "Guest"))
        assertEquals(2, c.artists.map { it.uri }.distinct().size)
        for (song in c.tracks) {
            val target = PlayerMetadata.artists(song.catalog().metadataState()).single().destination!!
            assertEquals(song.artistUri, target.uri)
            assertNull(target.service)
        }
    }
    @Test fun genresPreserveTagsAndKeepAnUnknownBucket() {
        val c = catalog(track(1).copy(genre = "Rock"), track(2).copy(genre = " rock "), track(3).copy(genre = ""))
        assertEquals(2, c.genres.size)
        assertEquals(2, c.genres.single { it.name.isNotBlank() }.tracks.size)
        assertEquals("", c.genres.single { it.name.isBlank() }.name)
    }
    @Test fun missingTagsKeepPhysicalIdentityAndFilenameWithoutFabricatingRemoteIds() {
        val t = track(1, title = "", artist = "", album = "")
        assertEquals("1.flac", t.catalog().name)
        assertTrue(t.catalog().artists.isEmpty())
        assertNull(t.catalog().artistUri)
        assertEquals("local:track:1", t.catalog().uri)
    }
    @Test fun embeddedArtIsRepresentedByALazyLocalRequestAndInvalidatedByFileChanges() {
        val t = track(1)
        assertTrue(t.catalog().artworkUrl!!.startsWith("squarelocalart://1/"))
        assertNotEquals(t.artwork, t.copy(modifiedSeconds = 2).artwork)
        assertNotEquals(t.artwork, t.copy(size = 200).artwork)
        assertNotEquals(t.artwork, t.copy(generation = 2).artwork)
        assertNotEquals(t.artwork, t.copy(artRevision = 2).artwork)
        assertEquals(t.fingerprint, t.copy(artRevision = 2).fingerprint)
    }
    @Test fun localArtworkNeverComesFromRemoteAlbumMetadata() {
        assertEquals(track(1).artwork, track(1).catalog().artworkUrl)
        assertFalse(track(1).artwork.startsWith("https://"))
    }
    @Test fun repeatedMediaStoreRowsDoNotDuplicateTracks() {
        val song = track(1)
        assertEquals(1, catalog(song,song).tracks.size)
    }
    @Test fun sortingSupportsAllFiveOrders() {
        val a=track(1,"Z",artist="A",album="B").copy(addedSeconds=2,durationMs=100)
        val b=track(2,"A",artist="Z",album="A").copy(addedSeconds=3,durationMs=50)
        val c=catalog(a,b)
        assertEquals(2L,c.ordered(LocalTrackOrder.TITLE).first().id)
        assertEquals(1L,c.ordered(LocalTrackOrder.ARTIST).first().id)
        assertEquals(2L,c.ordered(LocalTrackOrder.ALBUM).first().id)
        assertEquals(2L,c.ordered(LocalTrackOrder.ADDED).first().id)
        assertEquals(2L,c.ordered(LocalTrackOrder.DURATION).first().id)
    }
    @Test fun searchMatchesAllTermsLocallyIncludingPrivateFilename() {
        val c=catalog(track(1,"Song",artist="Artist",album="Album").copy(fileName="private-file.flac"),track(2,"Other"))
        assertEquals(listOf("local:track:1"),c.search("song PRIVATE-FILE").tracks.map { it.uri })
        assertEquals(2,c.search("artist").tracks.size)
        assertEquals(1,c.search("Album").albums.size)
        assertEquals(0,c.search("missing").count)
        assertEquals(0,c.search(" ").count)
    }
    @Test fun folderTreePreservesHierarchyAndOnlyListsDirectChildren() {
        val c=catalog(track(1,folder="external/Music/Artist/Record"),track(2,folder="sdcard/Music/Record"))
        assertEquals(listOf("external","sdcard"),c.childFolders(""))
        assertEquals(listOf("external/Music/Artist"),c.childFolders("external/Music"))
        assertEquals(1,c.folderTracks("external/Music").size)
        assertEquals(0,c.folderTracks("external/Musi").size)
    }
    @Test fun localDestinationsCannotBecomeRemoteOrCrossEntityKinds() {
        val t=track(1)
        val album=PlayerMetadata.album(t.catalog().metadataState())!!
        assertEquals(t.albumUri,album.uri)
        assertNull(album.service)
        assertNull(MetadataDestination.forTrack(MetadataDestination.Type.ARTIST,t.albumUri,"",t.uri))
        assertNull(MetadataDestination.forTrack(MetadataDestination.Type.ALBUM,"spotify:album:abc","",t.uri))
        assertNull(MetadataDestination.forTrack(MetadataDestination.Type.ALBUM,t.albumUri,"","spotify:track:abc"))
    }
    @Test fun explicitArtistAssociationRequiresAnIdentityAndDoesNotAcceptNameSearches() {
        assertNull(LocalArtistAssociation.profile("Queen"))
        assertNull(LocalArtistAssociation.profile("https://other.example/artist/abc"))
        assertNull(LocalArtistAssociation.profile("https://open.spotify.com/album/abc"))
        assertEquals("spotify:artist:abc",LocalArtistAssociation.profile("https://open.spotify.com/artist/abc?si=ignored"))
        assertEquals("ytmusic:artist:UC_abc-def",LocalArtistAssociation.profile("ytmusic:artist:UC_abc-def"))
        assertEquals("ytmusic:artist:UC_abc-def",LocalArtistAssociation.profile("https://music.youtube.com/channel/UC_abc-def"))
        assertEquals("spotify:artist:abc",LocalArtistAssociation.profile("https://open.spotify.com/intl-es/artist/abc"))
    }
    @Test fun localQueueDoesNotAcceptRemoteTracksOrTheReverse() {
        assertFalse(LocalQueuePolicy.canAppend("local:track:1","spotify:track:abc"))
        assertFalse(LocalQueuePolicy.canAppend("ytmusic:track:abc","local:track:1"))
        assertTrue(LocalQueuePolicy.canAppend("local:track:1","local:track:2"))
        assertTrue(LocalQueuePolicy.canAppend(null,"local:track:1"))
        assertTrue(LocalQueuePolicy.canAppend("spotify:track:1","spotify:track:2"))
    }
}
