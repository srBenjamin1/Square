package dev.lelonio.square.data.local

import dev.lelonio.square.data.MusicSource
import dev.lelonio.square.ui.SourceTabs
import dev.lelonio.square.ui.Routes
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class LocalRefinementNavigationTest {
    private fun source(name: String) = listOf(File("src/main/java/dev/lelonio/square/$name"), File("app/src/main/java/dev/lelonio/square/$name")).first { it.isFile }.readText()
    @Test fun localBarContainsExactlyFourOrderedDestinations() {
        assertEquals(listOf(Routes.HOME, Routes.NEW, Routes.LIBRARY, Routes.SEARCH), SourceTabs.routes(MusicSource.LOCAL))
    }
    @Test fun bothRemoteBarsKeepRadioAndTheirOriginalOrder() {
        for (source in listOf(MusicSource.SPOTIFY, MusicSource.YOUTUBE_MUSIC))
            assertEquals(listOf(Routes.HOME, Routes.NEW, Routes.RADIO, Routes.LIBRARY, Routes.SEARCH), SourceTabs.routes(source))
    }
    @Test fun shouldersWrapAndSkipRadioInLocal() {
        assertEquals(Routes.LIBRARY, SourceTabs.next(MusicSource.LOCAL, Routes.NEW, 1))
        assertEquals(Routes.NEW, SourceTabs.next(MusicSource.LOCAL, Routes.LIBRARY, -1))
        assertEquals(Routes.HOME, SourceTabs.next(MusicSource.LOCAL, Routes.SEARCH, 1))
        assertEquals(Routes.SEARCH, SourceTabs.next(MusicSource.LOCAL, Routes.HOME, -1))
    }
    @Test fun shouldersStillReachRemoteRadio() {
        for (source in listOf(MusicSource.SPOTIFY, MusicSource.YOUTUBE_MUSIC)) {
            assertEquals(Routes.RADIO, SourceTabs.next(source, Routes.NEW, 1))
            assertEquals(Routes.RADIO, SourceTabs.next(source, Routes.LIBRARY, -1))
        }
    }
    @Test fun switchingFromRemoteRadioSelectsLocalHomeAndRadioReturnsOnRemote() {
        assertEquals(Routes.HOME, SourceTabs.valid(MusicSource.LOCAL, Routes.RADIO))
        assertEquals(Routes.RADIO, SourceTabs.valid(MusicSource.SPOTIFY, Routes.RADIO))
        assertEquals(Routes.RADIO, SourceTabs.valid(MusicSource.YOUTUBE_MUSIC, Routes.RADIO))
    }
    @Test fun glassAndGamepadUseTheSameProductionDestinationPolicy() {
        val app = source("ui/SquareApp.kt")
        assertTrue(app.contains("SourceTabs.routes(musicSource)"))
        assertTrue(app.contains("SourceTabs.next(latestSource, activeTab, delta)"))
        assertTrue(app.contains("val tabRoutes = visibleTabs"))
        assertEquals(2, "if (Routes.RADIO in visibleTabs)".toRegex(RegexOption.LITERAL).findAll(app).count())
        assertFalse(app.contains("localContent(LocalLanding.MIX)"))
        assertTrue(app.contains("SourceTabs.valid(musicSource, route.orEmpty())"))
    }
    @Test fun localCardsFiltersAndHeadingAreTheActualSharedComponents() {
        val local = source("ui/local/LocalMusicScreen.kt")
        for (component in listOf("PlaylistTile(", "TrackTile(", "ArtistTile(", "ArtistCell(", "GridTile(", "LibraryTitle(", "Heading(", "FilterChip(")) assertTrue(component, local.contains(component))
        assertFalse(local.contains("fun LocalCard("))
        assertFalse(local.contains("LocalLanding.MIX"))
        assertTrue(local.contains("ThorLazyRow("))
        assertTrue(local.contains("local_history_empty"))
        assertTrue(local.contains("if (history.recent.isNotEmpty())"))
    }
    @Test fun localViewportAndSharedPaddingAccountForFloatingControlsOnce() {
        val local = source("ui/local/LocalMusicScreen.kt")
        assertFalse(local.contains(".padding(bottom = padding.calculateBottomPadding())"))
        assertTrue(local.contains("bottom = padding.calculateBottomPadding() + 20.dp"))
        assertFalse(source("ui/SquareApp.kt").contains("localChromeHeight"))
        assertFalse(local.contains(".clipToBounds()"))
        assertTrue(local.contains("LocalLayoutPolicy.forWindow"))
        val app = source("ui/SquareApp.kt")
        assertTrue(app.contains("measuredBarHeight.value = with(density) { it.height.toDp() } -"))
        assertTrue(app.contains("BottomBarHeight + navBar + 8.dp"))
        assertTrue(app.contains("if (playback.hasItem) MiniPlayerHeight else 0.dp"))
    }
    @Test fun historyIsRecordedByTheExistingServiceAndNotByLocalComposeEffects() {
        val vm = source("ui/MainViewModel.kt")
        assertTrue(vm.contains("val localListeningEvents = container.listeningStore.localEvents"))
        val record = vm.substringAfter("fun recordPlayed(track: CatalogTrack)").substringBefore("/**")
        assertTrue(record.contains("if (LocalLibrary.isLocal(track.uri)) return@launch"))
        val service = source("playback/PlaybackService.kt").substringAfter("private fun startListeningRecorder()")
        assertTrue(service.contains("item?.mediaId?.startsWith(\"local:track:\")"))
        assertFalse(source("ui/local/LocalMusicScreen.kt").contains("listeningStore.record"))
        assertTrue(source("data/ListeningStore.kt").contains("dev.lelonio.square.data.local.LocalListening.retained"))
    }
    @Test fun verifiedArtistAssociationUsesExistingApiAndArtworkProvidersWithoutScraping() {
        val vm = source("ui/MainViewModel.kt").substringAfter("suspend fun associateLocalArtist").substringBefore("private fun openLocalFiles")
        assertTrue(vm.contains("LocalArtistAssociation.profile(input)"))
        assertTrue(vm.contains("fetchArtistMetadata(id)"))
        assertTrue(vm.contains("container.youtubeBackend.artistPage(remote)"))
        assertTrue(vm.contains("AppleCatalog.artist(facts.first)"))
        assertTrue(vm.contains("heroUrl = facts.second ?: artwork?.heroUrl"))
        assertFalse(vm.contains("SpotifyWebArtist"))
        val header = source("ui/library/PlaylistScreen.kt").substringAfter("private fun ArtistHeader(")
        assertTrue(header.contains("logoUrl != null && !logoFailed"))
        assertTrue(header.contains("onError = { logoFailed = true }"))
    }
}
