package dev.lelonio.square.data.local

import dev.lelonio.square.data.AppleCatalog
import dev.lelonio.square.data.PreferencesStore.CoverSource
import dev.lelonio.square.ui.player.PlayerVisualPolicy
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class LocalVisualCorrectionsTest {
    private val one = LocalTrack(1, "content://one", "Song", "Artist", "Album", durationMs = 0)
    private fun c(t: LocalTrack = one, artist: String = "11", album: String = "21", duration: Long = 0) =
        LocalSongCandidate(t.id.toString(), t.title, t.artist, t.album, duration, artist, album)
    private fun artist(vararg tracks: LocalTrack) = LocalMusicCatalog(LocalMusicSnapshot(tracks.toList())).artists.single()
    private fun art(portrait: String? = "portrait", square: String? = "square") = AppleCatalog.Album(null, null, motionUrl = portrait, motionSquareUrl = square)
    private fun source(path: String) = listOf(File("src/main/java/dev/lelonio/square/$path"), File("app/src/main/java/dev/lelonio/square/$path")).first { it.isFile }.readText()
    @Test fun oneSongWithNoAlbumOrDurationStillIdentifiesAnArtist() {
        val t = one.copy(album = "")
        assertEquals(LocalResolutionStatus.VERIFIED to "11", LocalVisualMatching.artist(artist(t), listOf(c(t))))
    }
    @Test fun threeIndependentSongsReinforceOneIdentity() {
        val two = one.copy(id = 2, title = "Two")
        val three = one.copy(id = 3, title = "Three")
        assertEquals(LocalResolutionStatus.VERIFIED to "11", LocalVisualMatching.artist(artist(one, two, three), listOf(c(), c(two), c(three))))
    }
    @Test fun conflictingAdditionalSongDoesNotSelectAPlurality() {
        val two = one.copy(id = 2, title = "Two")
        val three = one.copy(id = 3, title = "Three")
        assertEquals(LocalResolutionStatus.AMBIGUOUS, LocalVisualMatching.artist(artist(one, two, three), listOf(c(), c(two), c(three, artist = "12"))).first)
    }
    @Test fun knownAlbumDisambiguatesAHomonym() {
        val candidates = listOf(c(), c(artist = "12").copy(album = "Another album"))
        assertEquals(LocalResolutionStatus.VERIFIED to "11", LocalVisualMatching.artist(artist(one), candidates))
    }
    @Test fun motionDoesNotRequireTheArtistRelationshipWhenSongAndAlbumAreKnown() {
        assertEquals("21", LocalVisualMatching.uniqueAlbum(one, listOf(c().copy(artistId = ""))))
        assertEquals(LocalResolutionStatus.NOT_FOUND, LocalVisualMatching.artist(artist(one), listOf(c().copy(artistId = ""))).first)
    }
    @Test fun missingAlbumCanResolveMotionFromOneExactSong() {
        assertEquals("21", LocalVisualMatching.uniqueAlbum(one.copy(album = ""), listOf(c())))
    }
    @Test fun knownAlbumOutranksDurationOfAnotherEdition() {
        val t = one.copy(durationMs = 60_000)
        assertEquals("21", LocalVisualMatching.uniqueAlbum(t, listOf(c(duration = 90_000), c(album = "22", duration = 60_000).copy(album = "Other edition"))))
    }
    @Test fun durationBreaksAnOtherwiseEquivalentEditionTie() {
        assertEquals("22", LocalVisualMatching.uniqueAlbum(one.copy(durationMs = 60_000), listOf(c(duration = 90_000), c(album = "22", duration = 60_000))))
    }
    @Test fun missingDurationDoesNotInventAnEditionPreference() {
        assertNull(LocalVisualMatching.uniqueAlbum(one, listOf(c(duration = 90_000), c(album = "22", duration = 60_000))))
    }
    @Test fun equivalentEditionsReuseTheirSharedMotion() {
        assertEquals(art(), LocalVisualMatching.sharedMotion(listOf(art(), art())))
    }
    @Test fun differentAnimationsKeepStaticFallback() {
        assertNull(LocalVisualMatching.sharedMotion(listOf(art(), art("different", "different"))))
    }
    @Test fun commonSquareResourceIsSafeDespiteDifferentPortraitResources() {
        assertEquals(art(null), LocalVisualMatching.sharedMotion(listOf(art(), art("other"))))
    }
    @Test fun anEditionWithoutMotionCannotBorrowAnUnsharedAnimation() {
        assertNull(LocalVisualMatching.sharedMotion(listOf(art(), art(null, null))))
        assertNull(LocalVisualMatching.sharedMotion(emptyList()))
    }
    @Test fun pendingWorkRetainsEarlierIdentitySoContradictionsCanBeDetected() {
        val a = artist(one)
        val v = LocalArtistVisual(a.uri, "applemusic:artist:11", a.name, automatic = true)
        val s = LocalMusicSnapshot(listOf(one), visuals = listOf(v))
        val pending = LocalArtistPolicy.apply(s, LocalArtistResolution(a.uri, LocalResolutionStatus.PENDING, LocalVisualMatching.evidence(a.tracks)), null)
        assertEquals(listOf(v), pending.visuals)
        val changed = LocalArtistPolicy.apply(pending, LocalArtistResolution(a.uri, LocalResolutionStatus.VERIFIED, LocalVisualMatching.evidence(a.tracks)), v.copy(remoteUri = "applemusic:artist:12"))
        assertEquals(v.remoteUri, changed.visuals.single().remoteUri)
        assertTrue(changed.visuals.single().needsReview)
        assertEquals(LocalResolutionStatus.REVIEW_REQUIRED, changed.resolutions.single().status)
    }
    @Test fun refreshingTheSameIdentityKeepsCachedPortraitAndLogoFiles() {
        val a = artist(one)
        val v = LocalArtistVisual(a.uri, "applemusic:artist:11", a.name, heroUrl = "photo", logoUrl = "logo", heroFile = "cached-photo", logoFile = "cached-logo", automatic = true)
        val s = LocalMusicSnapshot(listOf(one), visuals = listOf(v))
        val refreshed = LocalArtistPolicy.apply(s, LocalArtistResolution(a.uri, LocalResolutionStatus.VERIFIED, LocalVisualMatching.evidence(a.tracks)), v.copy(heroFile = null, logoFile = null))
        assertEquals(v, refreshed.visuals.single())
    }
    @Test fun verificationOfOriginalIdentityClearsReview() {
        val a = artist(one)
        val v = LocalArtistVisual(a.uri, "applemusic:artist:11", a.name, automatic = true)
        val s = LocalMusicSnapshot(listOf(one), visuals = listOf(v.copy(needsReview = true)))
        assertFalse(LocalArtistPolicy.apply(s, LocalArtistResolution(a.uri, LocalResolutionStatus.VERIFIED, LocalVisualMatching.evidence(a.tracks)), v).visuals.single().needsReview)
    }
    @Test fun menuActionsFollowManualRejectedAndAutomaticStates() {
        val v = LocalArtistVisual("a", "remote", "Artist")
        val manual = LocalArtistControls("a", LocalResolutionStatus.MANUAL, v).actions
        assertTrue(manual.containsAll(listOf(LocalArtistControls.Action.INFORMATION, LocalArtistControls.Action.STATUS, LocalArtistControls.Action.CHANGE, LocalArtistControls.Action.UNLINK)))
        assertFalse(manual.contains(LocalArtistControls.Action.RETRY))
        assertFalse(manual.contains(LocalArtistControls.Action.REJECT))
        val rejected = LocalArtistControls("a", LocalResolutionStatus.REJECTED, null).actions
        assertTrue(rejected.contains(LocalArtistControls.Action.LINK))
        assertTrue(rejected.contains(LocalArtistControls.Action.RETRY))
        assertFalse(rejected.contains(LocalArtistControls.Action.REJECT))
        val review = LocalArtistControls("a", LocalResolutionStatus.REVIEW_REQUIRED, v).actions
        assertTrue(review.contains(LocalArtistControls.Action.REJECT))
        assertTrue(review.contains(LocalArtistControls.Action.RETRY))
    }
    @Test fun associationControlsBelongToOriginalInformationButtonAndAnIsolatedMenuScene() {
        val grid = source("ui/local/LocalMusicScreen.kt")
        assertFalse(grid.contains("local_associate_artist"))
        assertFalse(grid.contains("local_clear_association"))
        val page = source("ui/library/PlaylistScreen.kt")
        assertTrue(page.contains("if (artistControls != null) artistMenuOpen"))
        assertTrue(page.contains("BackHandler(artistMenuOpen)"))
        assertTrue(page.contains("LocalThorScrollPriority provides 2"))
        assertTrue(page.contains("ThorFocusScene(\"local-artist-information:"))
        assertTrue(page.contains("onExit = { cancelFocusChange() }"))
        assertTrue(page.contains("localArtistStatusLabel(it.status)"))
    }
    @Test fun fullViewportAndFinalPaddingPreserveFocusVisibilityWithFloatingChrome() {
        val grid = source("ui/local/LocalMusicScreen.kt")
        assertTrue(grid.contains("modifier = Modifier.fillMaxSize(),\n        contentPadding"))
        assertTrue(grid.contains("bottom = padding.calculateBottomPadding() + 20.dp"))
        assertTrue(grid.contains("LocalThorObscuredBottom provides padding.calculateBottomPadding()"))
        val root = source("ui/SquareApp.kt")
        assertFalse(root.contains("localListPadding"))
        assertTrue(root.contains("if (playback.hasItem) MiniPlayerHeight else 0.dp"))
        assertTrue(root.contains("if (!tabBarScroll.isInline)"))
        val focus = source("thor/input/ThorFocus.kt")
        assertTrue(focus.contains("bounds[0].height + obscuredBottom"))
        assertTrue(focus.contains("if (!LocalThorEnabled.current) return@composed this"))
    }
    @Test fun landscapeUsesAppleRegardlessOfPortraitProviderButRespectsOfflineAndSwitch() {
        assertTrue(PlayerVisualPolicy.motionEnabledForTrack(one.uri, true, CoverSource.SPOTIFY, false, true))
        assertFalse(PlayerVisualPolicy.motionEnabledForTrack(one.uri, true, CoverSource.SPOTIFY, true, true))
        assertFalse(PlayerVisualPolicy.motionEnabledForTrack(one.uri, false, CoverSource.APPLE_MUSIC, false, true))
        assertNull(PlayerVisualPolicy.motionUrl(art(), true, true, CoverSource.APPLE_MUSIC, true, true))
        assertEquals("square", PlayerVisualPolicy.motionUrl(art(), true, true, CoverSource.SPOTIFY, true, false))
    }
    @Test fun updatedEvidenceInvalidatesRestrictiveNegativeCache() {
        val old = localKey("evidence", "${one.title}/${one.artist}/${one.album}/${one.albumArtist}/${one.durationMs}")
        assertNotEquals(old, LocalVisualMatching.evidence(listOf(one)))
    }
}
