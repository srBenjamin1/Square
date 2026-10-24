package dev.lelonio.square.data

import dev.lelonio.square.backend.BackendId

/** Browsing is independent of the player: local files already have their own playback path. */
enum class MusicSource(val backend: BackendId?) {
    SPOTIFY(BackendId.SPOTIFY), YOUTUBE_MUSIC(BackendId.YOUTUBE_MUSIC), LOCAL(null);

    companion object {
        fun of(backend: BackendId) = entries.first { it.backend == backend }
        fun restore(saved: String?, backend: BackendId): MusicSource =
            entries.firstOrNull { it.name == saved } ?: of(backend)
    }
}
