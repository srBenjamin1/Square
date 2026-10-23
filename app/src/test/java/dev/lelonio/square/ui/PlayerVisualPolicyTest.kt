package dev.lelonio.square.ui

import dev.lelonio.square.data.AppleCatalog
import dev.lelonio.square.data.PreferencesStore.CoverSource
import dev.lelonio.square.ui.player.PlayerVisualPolicy
import org.junit.Assert.*
import org.junit.Test

class PlayerVisualPolicyTest {
    private val art = AppleCatalog.Album(null, "still", motionUrl = "tall.m3u8", motionSquareUrl = "square.m3u8")
    private fun motion(landscape: Boolean, source: CoverSource = CoverSource.SPOTIFY, enabled: Boolean = true,
        visible: Boolean = true, video: Boolean = false, artwork: AppleCatalog.Album? = art) =
        PlayerVisualPolicy.motionUrl(artwork, landscape, enabled, source, visible, video)

    @Test fun landscapeUsesAppleSquareRegardlessOfPortraitSource() {
        assertEquals("square.m3u8", motion(true, CoverSource.SPOTIFY))
        assertEquals("square.m3u8", motion(true, CoverSource.APPLE_MUSIC))
    }
    @Test fun portraitKeepsSavedProviderSelectionAndOriginalTallVariant() {
        assertNull(motion(false, CoverSource.SPOTIFY))
        assertEquals("tall.m3u8", motion(false, CoverSource.APPLE_MUSIC))
    }
    @Test fun landscapeWithoutSquareCanContainTallAndWithoutMotionUsesStill() {
        assertEquals("tall.m3u8", motion(true, artwork = art.copy(motionSquareUrl = null)))
        assertNull(motion(true, artwork = art.copy(motionUrl = null, motionSquareUrl = null)))
        assertNull(motion(true, artwork = null))
    }
    @Test fun disabledHiddenAndMusicVideoNeverCreateMovingCover() {
        for (landscape in listOf(false, true)) {
            assertNull(motion(landscape, CoverSource.APPLE_MUSIC, enabled = false))
            assertNull(motion(landscape, CoverSource.APPLE_MUSIC, visible = false))
            assertNull(motion(landscape, CoverSource.APPLE_MUSIC, video = true))
        }
    }
    @Test fun canvasIsOnlyForPortraitSongs() {
        assertTrue(PlayerVisualPolicy.canvasAllowed(false, false))
        assertFalse(PlayerVisualPolicy.canvasAllowed(true, false))
        assertFalse(PlayerVisualPolicy.canvasAllowed(false, true))
        assertFalse(PlayerVisualPolicy.canvasAllowed(true, true))
    }
    @Test fun fullscreenNeverReplacesBottomNavigator() {
        assertTrue(PlayerVisualPolicy.fullScreenVideo(true, true, true))
        assertFalse(PlayerVisualPolicy.fullScreenVideo(true, true, false))
        assertFalse(PlayerVisualPolicy.fullScreenVideo(false, true, true))
        assertFalse(PlayerVisualPolicy.fullScreenVideo(true, false, true))
    }
}
