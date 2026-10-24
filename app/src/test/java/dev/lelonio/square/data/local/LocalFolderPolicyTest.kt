package dev.lelonio.square.data.local

import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files

class LocalFolderPolicyTest {
    private val a = LocalMusicFolder("content://tree/a", "A")
    private val b = LocalMusicFolder("content://tree/b", "B")
    private val media = LocalTrack(17, "content://media/17", "Song", "Artist", "Album", storageKey = "primary|Music/A/song.flac", durationMs = 60_000)
    private val saf = media.copy(id = -99, contentUri = "content://documents/song", documentKey = "provider|song", treeUris = listOf(a.uri))
    @Test fun noConfiguredFoldersKeepsAllDeviceMusicByDefault() {
        assertEquals(listOf(media), LocalMusicCatalog(LocalMusicSnapshot(listOf(media))).tracks)
    }
    @Test fun emptyRestrictivePreferenceRecoversToGeneralMode() {
        assertEquals(LocalFolderMode.ALL, LocalFolderPolicy.effective(LocalFolderMode.SELECTED, emptyList()))
        assertEquals(listOf(media), LocalFolderPolicy.visible(LocalMusicSnapshot(listOf(media), folderMode = LocalFolderMode.SELECTED)))
    }
    @Test fun generalModeIncludesMediaAndAdditionalSafAudio() {
        val extra = saf.copy(storageKey = "cloud|different", id = -100)
        assertEquals(2, LocalMusicCatalog(LocalMusicSnapshot(LocalFolderPolicy.merge(listOf(media), listOf(extra), emptyList()), folders = listOf(a))).tracks.size)
    }
    @Test fun restrictiveModeUsesUnionOfSelectedTreesRecursively() {
        val extra = saf.copy(id = -100, storageKey = "different", treeUris = listOf(b.uri), folder = "B/sub/sub")
        val all = listOf(media.copy(storageKey = "outside"), saf, extra)
        val s = LocalMusicSnapshot(all, folders = listOf(a,b), folderMode = LocalFolderMode.SELECTED)
        assertEquals(listOf(saf, extra), LocalFolderPolicy.visible(s))
        assertEquals(3, s.tracks.size)
    }
    @Test fun deletingLastFolderRestoresGeneralWithoutDeletingAudioRecordsOrPlaylists() {
        val p = LocalPlaylist("one", "One", listOf(media.uri))
        val s = LocalMusicSnapshot(listOf(media), playlists = listOf(p), folders = listOf(a), folderMode = LocalFolderMode.SELECTED)
        val removed = s.copy(folders = emptyList())
        assertEquals(LocalFolderMode.ALL, LocalFolderPolicy.effective(removed.folderMode, removed.folders))
        assertEquals(listOf(media), LocalFolderPolicy.visible(removed))
        assertEquals(p, removed.playlists.single())
    }
    @Test fun revokedPermissionsFallbackToGeneralAndKeepRemainingAccessibleAudio() {
        val lost = a.copy(accessible = false)
        val s = LocalMusicSnapshot(listOf(media, saf.copy(available = false)), folders = listOf(lost), folderMode = LocalFolderMode.SELECTED)
        assertEquals(listOf(media), LocalFolderPolicy.visible(s))
    }
    @Test fun oneRevokedFolderDoesNotDisableOtherSelectedFolders() {
        val s = LocalMusicSnapshot(listOf(saf, media.copy(treeUris = listOf(b.uri))), folders = listOf(a.copy(accessible=false),b), folderMode = LocalFolderMode.SELECTED)
        assertEquals(LocalFolderMode.SELECTED, LocalFolderPolicy.effective(s.folderMode, s.folders))
        assertEquals(listOf(media.uri), LocalFolderPolicy.visible(s).map { it.uri })
    }
    @Test fun overlappingTreesProduceOneTrackWithBothAuthorizations() {
        val merged = LocalFolderPolicy.merge(emptyList(), listOf(saf, saf.copy(treeUris = listOf(b.uri))), emptyList())
        assertEquals(1, merged.size)
        assertEquals(setOf(a.uri,b.uri), merged.single().treeUris.toSet())
    }
    @Test fun mediaAndSafSamePhysicalPathKeepExistingMediaIdentifier() {
        val merged = LocalFolderPolicy.merge(listOf(media), listOf(saf), listOf(media))
        assertEquals(1, merged.size)
        assertEquals(media.uri, merged.single().uri)
        assertEquals(saf.contentUri, merged.single().contentUri)
        assertEquals(listOf(a.uri), merged.single().treeUris)
    }
    @Test fun aPreviouslyUnindexedSafFileKeepsItsIdentifierWhenMediaStoreFindsIt() {
        val merged = LocalFolderPolicy.merge(listOf(media), listOf(saf), listOf(saf))
        assertEquals(saf.uri, merged.single().uri)
    }
    @Test fun providerMediaReferenceDeduplicatesWithoutGuessingFilenames() {
        val document = saf.copy(storageKey = "", mediaReferenceId = media.id)
        val merged = LocalFolderPolicy.merge(listOf(media), listOf(document), emptyList())
        assertEquals(1, merged.size)
        assertEquals(media.uri, merged.single().uri)
    }
    @Test fun newProviderAliasStillPreservesThePreviousSafUri() {
        val old = saf.copy(storageKey = "", mediaReferenceId = null)
        val discovered = old.copy(mediaReferenceId = media.id)
        assertEquals(old.uri, LocalFolderPolicy.merge(listOf(media), listOf(discovered), listOf(old)).single().uri)
    }
    @Test fun identicalNamesAtDifferentLocationsAreDifferentFiles() {
        val extra = saf.copy(storageKey = "primary|Other/song.flac")
        assertEquals(2, LocalFolderPolicy.merge(listOf(media), listOf(extra), emptyList()).size)
    }
    @Test fun safWorksWithoutMediaStoreReadPermission() {
        val inaccessibleMedia = media.copy(available=false)
        val result = LocalFolderPolicy.merge(listOf(inaccessibleMedia), listOf(saf), listOf(media))
        assertTrue(result.single().available)
        assertEquals(saf.contentUri, result.single().contentUri)
    }
    @Test fun inaccessibleSafNeverReplacesAnAccessibleMediaStoreUri() {
        val result = LocalFolderPolicy.merge(listOf(media), listOf(saf.copy(available=false)), listOf(media))
        assertEquals(media.contentUri, result.single().contentUri)
        assertTrue(result.single().available)
    }
    @Test fun stableNegativeDocumentIdsDoNotCollideWithOldPositiveMediaIds() {
        val key = LocalFolderPolicy.documentKey("provider", "primary:Music/song")
        assertTrue(LocalFolderPolicy.safId(key)<0)
        assertEquals(LocalFolderPolicy.safId(key),LocalFolderPolicy.safId(key))
        assertNotEquals(LocalFolderPolicy.safId(key),LocalFolderPolicy.safId(key+"other"))
        assertEquals("local:track:17",media.uri)
    }
    @Test fun externalPrimaryAndMicroSdVolumeAliasesRemainComparable() {
        assertEquals(LocalFolderPolicy.storageKey("external_primary","Music/song"),LocalFolderPolicy.storageKey("primary","/Music/song"))
        assertEquals(LocalFolderPolicy.storageKey("ABCD-1234","Music/song"),LocalFolderPolicy.storageKey("abcd-1234","Music/song"))
    }
    @Test fun folderModeGrantsAndInvisiblePlaylistReferencesSurviveRestart() {
        val dir=Files.createTempDirectory("local-saf-policy").toFile()
        try {
            val p=LocalPlaylist("one","One",listOf(media.uri,saf.uri))
            val s=LocalMusicSnapshot(listOf(media,saf),playlists=listOf(p),folders=listOf(a,b.copy(accessible=false)),folderMode=LocalFolderMode.SELECTED)
            val store=LocalMusicStore(java.io.File(dir,"index.json"));store.write(s)
            val restored=LocalMusicStore(java.io.File(dir,"index.json")).read()
            assertEquals(s,restored)
            assertEquals(2,restored.playlists.single().tracks.size)
            assertEquals(listOf(saf.uri),LocalMusicCatalog(restored).tracks.map { it.uri })
        } finally {dir.deleteRecursively()}
    }
    @Test fun deletedSafFilesDisappearButTheirPlaylistReferenceRemains() {
        val p=LocalPlaylist("p","P",listOf(saf.uri))
        val s=LocalMusicSnapshot(listOf(saf.copy(available=false)),playlists=listOf(p))
        assertTrue(LocalMusicCatalog(s).tracks.isEmpty())
        assertEquals(1,LocalMusicCatalog(s).missing(p))
        assertEquals(listOf(saf.uri),p.tracks)
    }
}
