package dev.lelonio.square.ui.player

import dev.lelonio.square.data.AppleCatalog
import dev.lelonio.square.data.PreferencesStore

internal object PlayerVisualPolicy {
    fun motionUrl(
        artwork: AppleCatalog.Album?, landscape: Boolean, enabled: Boolean,
        source: PreferencesStore.CoverSource, visible: Boolean, musicVideo: Boolean,
    ): String? = when {
        !enabled || !visible || musicVideo -> null
        landscape -> artwork?.motionSquareUrl ?: artwork?.motionUrl
        source == PreferencesStore.CoverSource.APPLE_MUSIC -> artwork?.motionUrl
        else -> null
    }

    fun motionEnabledForTrack(uri: String?, enabled: Boolean, source: PreferencesStore.CoverSource, offline: Boolean, landscape: Boolean = false): Boolean =
        enabled && (uri?.startsWith("local:track:") != true || (landscape || source == PreferencesStore.CoverSource.APPLE_MUSIC) && !offline)

    fun canvasAllowed(landscape: Boolean, musicVideo: Boolean): Boolean = !landscape && !musicVideo

    fun fullScreenVideo(landscape: Boolean, musicVideo: Boolean, showExpandedPlayer: Boolean): Boolean =
        landscape && musicVideo && showExpandedPlayer
}
