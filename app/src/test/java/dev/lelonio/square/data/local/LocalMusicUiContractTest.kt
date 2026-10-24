package dev.lelonio.square.data.local

import org.junit.Assert.*
import org.junit.Test
import java.io.File

/** Structural guards supplement the domain workflow tests; they do not render Android UI. */
class LocalMusicUiContractTest {
    private fun source(name:String):String = listOf(File("src/main/java/dev/lelonio/square/$name"),File("app/src/main/java/dev/lelonio/square/$name")).first { it.isFile }.readText()
    @Test fun originalExplorerAndPermissionPathRemainIndependentOfSelectedSource() {
        val vm=source("ui/MainViewModel.kt")
        assertTrue(vm.contains("if (playlist.uri == LocalLibrary.CONTEXT_URI)"))
        val legacy=vm.substringAfter("private fun openLocalFiles(").substringBefore("fun onLocalPermissionAnswered")
        assertTrue(legacy.contains("LocalLibrary.tracks(getApplication())"))
        assertFalse(legacy.contains("setSource"))
        assertFalse(legacy.contains("LocalMusicScreen"))
        assertTrue(source("data/LocalLibrary.kt").contains("READ_MEDIA_AUDIO"))
    }
    @Test fun allServiceChoiceSurfacesIncludeLocalWithoutAddingAnotherRemoteBackend() {
        assertTrue(source("ui/settings/SettingsScreen.kt").contains("MusicSource.entries.forEachIndexed"))
        assertTrue(source("ui/onboarding/BackendChoiceScreen.kt").contains("onChoose(dev.lelonio.square.data.MusicSource.LOCAL)"))
        assertTrue(source("ui/home/HomeScreen.kt").contains("LocalMusicHeader"))
        assertTrue(source("ui/SquareApp.kt").contains("onSwitchSource = switchMusicSource"))
        assertFalse(source("backend/MusicBackend.kt").contains("YOUTUBE_MUSIC, LOCAL"))
    }
    @Test fun sourcePersistenceRetainsTheLastRemoteBackendForLocalBrowsing() {
        val prefs=source("data/PreferencesStore.kt")
        assertTrue(prefs.contains("MusicSource.restore(prefs.getString(KEY_MUSIC_SOURCE, null), _backend.value)"))
        val setter=prefs.substringAfter("fun setSource(value: MusicSource)").substringBefore("private val _backendChosen")
        assertTrue(setter.contains("value.backend?.let { setBackend(it); return }"))
        assertTrue(setter.contains("putString(KEY_MUSIC_SOURCE, value.name)"))
        assertFalse(setter.contains("_backend.value ="))
    }
    @Test fun localTabsShareExistingHostAndSearchIsHandledBeforeAnyRemoteLookup() {
        val app=source("ui/SquareApp.kt")
        for(tab in listOf("HOME","LIBRARY","RECENT")) assertTrue(app.contains("localContent(LocalLanding.$tab)"))
        assertTrue(app.contains("offline = offlineNow && musicSource != MusicSource.LOCAL"))
        val vm=source("ui/MainViewModel.kt").substringAfter("fun onSearchQuery(query: String)").substringBefore("fun loadMoreSearch")
        assertTrue(vm.indexOf("if (browsingLocal)") < vm.indexOf("SpotifyLinkParser.parse(query)"))
        assertTrue(vm.contains("catalog.search(query)"))
    }
    @Test fun artistPagesReuseOriginalPhotoAndLogoHeaderRatherThanANewAvatarScreen() {
        val vm=source("ui/MainViewModel.kt").substringAfter("private fun openOrganizedLocal(").substringBefore("suspend fun associateLocalArtist")
        assertTrue(vm.contains("DetailKind.ARTIST"))
        assertTrue(vm.contains("heroUrl = picture(visual?.heroFile, visual?.heroUrl)"))
        assertTrue(vm.contains("logoUrl = picture(visual?.logoFile, visual?.logoUrl)"))
        assertTrue(source("ui/library/PlaylistScreen.kt").contains("private fun ArtistHeader("))
        assertTrue(source("ui/library/PlaylistScreen.kt").contains("logoUrl != null"))
    }
    @Test fun indexAndNewUiDoNotConstructPlayersOrSessionsOrCopyAudio() {
        for(file in listOf("data/local/LocalLibraryIndexer.kt","data/local/LocalMusicRepository.kt","ui/local/LocalMusicScreen.kt")) {
            val content=source(file)
            assertFalse(content.contains("ExoPlayer.Builder"))
            assertFalse(content.contains("MediaSession.Builder"))
            assertFalse(content.contains("copyTo("))
        }
        assertTrue(source("playback/PlaybackService.kt").contains("LocalPlayerFactory.create(playbackHost)"))
        assertFalse(source("ui/local/LocalMusicScreen.kt").contains("repository.refresh("))
    }
    @Test fun newTouchAndControllerTargetsUseOriginalFocusComponentsAndTransport() {
        val local=source("ui/local/LocalMusicScreen.kt")
        assertTrue(local.contains("ThorFocusScene("))
        assertTrue(local.contains("FilterChip(text, selected, onClick)"))
        assertTrue(local.contains("TrackRow("))
        assertTrue(source("ui/SquareApp.kt").contains("onPlay(tracks, index, null, false, localLabel, 0L)"))
        val app=source("ui/SquareApp.kt")
        assertTrue(app.contains("dev.lelonio.square.backend.lyrics.LyricsQuery("))
        assertFalse(source("data/local/LocalMusicRepository.kt").contains("Lrc"))
        val topPanels=app.substringAfter("allowedPanels = if (playerOnly)").substringBefore("showPanelTabs")
        assertFalse(topPanels.contains("PlayerPanel.INFO"))
    }
    @Test fun originalHeaderOpensDirectChoicesInsteadOfSwitchingThroughAnotherBackend() {
        val app=source("ui/SquareApp.kt")
        assertTrue(app.contains("MusicSource.entries.forEach { source ->"))
        assertTrue(app.contains("preferences.setSource(source)"))
        assertFalse(app.contains("MusicSource.entries[(musicSource.ordinal"))
        assertTrue(app.contains("ThorFocusScene(\"music-source-picker\""))
        val home=source("ui/home/HomeScreen.kt")
        assertTrue(home.contains("onClick = { onSwitchSource(sourceAnchor) }"))
    }

}
