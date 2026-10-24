package dev.lelonio.square.data.local

import dev.lelonio.square.data.MusicSource

/** Spotify's native catalog needs its engine; YouTube and local browsing do not. */
internal object LocalSourcePlaybackPolicy {
    fun sdkRestrictions(configured: Boolean, trackUri: String?): Boolean =
        configured && trackUri?.startsWith("local:track:") != true

    fun needsSpotifyPlayer(source: MusicSource, playingLocal: Boolean, hasParkedSpotify: Boolean): Boolean =
        source == MusicSource.SPOTIFY && playingLocal && !hasParkedSpotify
}
