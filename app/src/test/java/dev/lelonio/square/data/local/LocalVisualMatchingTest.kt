package dev.lelonio.square.data.local

import dev.lelonio.square.data.AppleCatalog
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class LocalVisualMatchingTest {
    private val one=LocalTrack(1,"content://one","Song One","Artist","Album",durationMs=60_000)
    private val two=one.copy(id=2,title="Song Two",durationMs=70_000)
    private val snapshot=LocalMusicSnapshot(listOf(one,two))
    private val artist=LocalMusicCatalog(snapshot).artists.single()
    private fun candidate(t:LocalTrack=one, id:String="101",album:String="201")=LocalSongCandidate(t.id.toString(),t.title,t.artist,t.album,t.durationMs,id,album)
    private val visual=LocalArtistVisual(artist.uri,"applemusic:artist:101","Artist",heroUrl="https://catalog/photo",logoUrl="https://catalog/logo",automatic=true)
    private fun resolution(status:LocalResolutionStatus=LocalResolutionStatus.VERIFIED)=LocalArtistResolution(artist.uri,status,LocalVisualMatching.evidence(artist.tracks),100)
    @Test fun twoDistinctLocalSongsVerifyTheSameArtistIdentity() {
        assertEquals(LocalResolutionStatus.VERIFIED to "101",LocalVisualMatching.artist(artist,listOf(candidate(one),candidate(two))))
    }
    @Test fun oneExactSongVerifiesWithoutACompleteAlbum() {
        assertEquals(LocalResolutionStatus.VERIFIED,LocalVisualMatching.artist(artist,listOf(candidate())).first)
        assertEquals(LocalResolutionStatus.NOT_FOUND,LocalVisualMatching.artist(artist,emptyList()).first)
    }
    @Test fun duplicateSearchResultsKeepOneIdentity() {
        assertEquals(LocalResolutionStatus.VERIFIED,LocalVisualMatching.artist(artist,listOf(candidate(),candidate())).first)
    }
    @Test fun homonymsWithTwoPlausibleIdentitiesRemainAmbiguous() {
        val candidates=listOf(candidate(one),candidate(two),candidate(one,"102"),candidate(two,"102"))
        assertEquals(LocalResolutionStatus.AMBIGUOUS to null,LocalVisualMatching.artist(artist,candidates))
    }
    @Test fun anotherSongCanDisambiguateAHomonym() {
        assertEquals(LocalResolutionStatus.VERIFIED to "101",LocalVisualMatching.artist(artist,listOf(candidate(one),candidate(two),candidate(one,"102"))))
    }
    @Test fun artistIdentityUsesAlbumWhileMotionCanResolveAnAlternativeEdition() {
        assertEquals(LocalResolutionStatus.NOT_FOUND,LocalVisualMatching.artist(artist,listOf(candidate().copy(album="Different album"))).first)
        assertFalse(LocalVisualMatching.song(one,candidate().copy(artist="Namesake")))
        assertFalse(LocalVisualMatching.song(one,candidate().copy(name="Different song")))
    }
    @Test fun durationIsSecondaryAndNeverRequiredForMotion() {
        assertTrue(LocalVisualMatching.song(one,candidate().copy(durationMs=63_000)))
        assertTrue(LocalVisualMatching.song(one.copy(durationMs=0),candidate()))
        assertTrue(LocalVisualMatching.song(one,candidate().copy(durationMs=0)))
    }
    @Test fun editionAndLiveQualifiersAreNotStripped() {
        assertEquals("201",LocalVisualMatching.uniqueAlbum(one,listOf(candidate().copy(album="Album (Deluxe)"))))
        assertNull(LocalVisualMatching.uniqueAlbum(one,listOf(candidate().copy(name="Song One (Live)"))))
    }
    @Test fun ambiguousAlbumEditionsNeverProvideMotion() {
        assertNull(LocalVisualMatching.uniqueAlbum(one,listOf(candidate(),candidate(album="202"))))
    }
    @Test fun duplicateRowsOfOneExactEditionStillSelectTheSameAlbumId() {
        assertEquals("201",LocalVisualMatching.uniqueAlbum(one,listOf(candidate(),candidate())))
    }
    @Test fun missingCatalogIdentityIsNotVerification() {
        assertEquals(LocalResolutionStatus.NOT_FOUND,LocalVisualMatching.artist(artist,listOf(candidate().copy(artistId=""))).first)
        assertFalse(LocalVisualMatching.song(one,candidate().copy(id="")))
        assertNull(LocalVisualMatching.uniqueAlbum(one,listOf(candidate().copy(albumId=""))))
    }
    @Test fun caseAndWhitespaceNormalizeWithoutDroppingMeaningfulWords() {
        assertTrue(LocalVisualMatching.song(one,candidate().copy(name=" song   ONE ",artist=" ARTIST ")))
        assertFalse(LocalVisualMatching.song(one,candidate().copy(artist="Artist & Guest")))
    }
    @Test fun manualAssociationAlwaysWinsOverAsyncAutomaticResults() {
        val manual=snapshot.copy(visuals=listOf(visual.copy(automatic=false)))
        assertEquals(manual,LocalArtistPolicy.apply(manual,resolution(),visual))
    }
    @Test fun manualRejectionPreventsAutomaticReassociation() {
        val rejected=snapshot.copy(resolutions=listOf(resolution(LocalResolutionStatus.REJECTED)))
        assertEquals(rejected,LocalArtistPolicy.apply(rejected,resolution(),visual))
    }
    @Test fun verifiedAutomaticIdentityCanBeAppliedAndRetainsItsOrigin() {
        val result=LocalArtistPolicy.apply(snapshot,resolution(),visual)
        assertTrue(result.visuals.single().automatic)
        assertEquals(LocalResolutionStatus.VERIFIED,result.resolutions.single().status)
    }
    @Test fun staleEvidenceCannotOverrideNewTagsOrChangedFolderSelection() {
        val changed=snapshot.copy(tracks=listOf(one.copy(album="New album"),two))
        assertEquals(changed,LocalArtistPolicy.apply(changed,resolution(),visual))
        val hidden=snapshot.copy(folders=listOf(LocalMusicFolder("folder","Selected")),folderMode=LocalFolderMode.SELECTED)
        assertEquals(hidden,LocalArtistPolicy.apply(hidden,resolution(),visual))
    }
    @Test fun retryGenerationSurvivesAutomaticProgressAndCompletion() {
        val requested=snapshot.copy(resolutions=listOf(resolution(LocalResolutionStatus.PENDING).copy(retryGeneration=99)))
        val result=LocalArtistPolicy.apply(requested,resolution(),visual)
        assertEquals(99L,result.resolutions.single().retryGeneration)
    }
    @Test fun automaticVisualsAndMotionMetadataPersistWithoutReplacingLocalCovers() {
        val artwork=AppleCatalog.Album(null,null,motionUrl="https://catalog/portrait.m3u8",motionSquareUrl="https://catalog/square.m3u8")
        val match=LocalMotionMatch(one.uri,LocalVisualMatching.evidence(listOf(one)),100,"201",artwork)
        val data=LocalArtistPolicy.apply(snapshot,resolution(),visual).copy(motions=listOf(match))
        val restored=Json.decodeFromString<LocalMusicSnapshot>(Json.encodeToString(data))
        assertEquals(data,restored)
        assertEquals(one.artwork,LocalMusicCatalog(restored).byUri.getValue(one.uri).catalog().artworkUrl)
        assertNull(restored.motions.single().artwork?.coverUrl)
    }
    @Test fun oldManualAssociationsStayManualAfterSchemaUpgrade() {
        val old="""{"artistUri":"local:artist:x","remoteUri":"spotify:artist:known","remoteName":"Artist"}"""
        assertFalse(Json.decodeFromString<LocalArtistVisual>(old).automatic)
    }
    @Test fun contradictedAutomaticIdentityIsRetainedForReviewAndHidden() {
        val prior=snapshot.copy(visuals=listOf(visual))
        val result=LocalArtistPolicy.apply(prior,resolution(LocalResolutionStatus.AMBIGUOUS),null)
        assertTrue(result.visuals.single().needsReview)
        assertNull(LocalMusicCatalog(result).artistArtwork(artist.uri,false))
        assertEquals(LocalResolutionStatus.REVIEW_REQUIRED,result.resolutions.single().status)
    }

    @Test fun copiesOfOneSongNeverProvideIndependentArtistEvidence() {
        val copy=one.copy(id=99)
        val a=LocalMusicCatalog(LocalMusicSnapshot(listOf(one,copy))).artists.single()
        assertEquals(LocalResolutionStatus.VERIFIED,LocalVisualMatching.artist(a,listOf(candidate(one))).first)
        val alternate=one.copy(id=100,album="Another edition")
        val sameTitle=LocalMusicCatalog(LocalMusicSnapshot(listOf(one,alternate))).artists.single()
        assertEquals(LocalResolutionStatus.VERIFIED,LocalVisualMatching.artist(sameTitle,listOf(candidate(one),candidate(alternate))).first)
    }
    @Test fun anUnverifiedOrDifferentArtistsPictureCannotCrossThePersistenceBoundary() {
        assertTrue(LocalArtistPolicy.apply(snapshot,resolution(LocalResolutionStatus.AMBIGUOUS),visual).visuals.isEmpty())
        assertTrue(LocalArtistPolicy.apply(snapshot,resolution(),visual.copy(artistUri="another-artist")).visuals.isEmpty())
    }
}
