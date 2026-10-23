package dev.lelonio.square.ui

import android.os.Bundle
import dev.lelonio.square.data.CatalogTrack

/** Uses the same metadata keys read by PlaybackState and written by MainActivity. */
internal data class CatalogIdentityExtras(
    val values: Map<String, String>,
    val lists: Map<String, List<String>>,
) {
    fun toBundle() = Bundle().apply {
        values.forEach { (key, value) -> putString(key, value) }
        lists.forEach { (key, value) -> putStringArrayList(key, ArrayList(value)) }
    }
}

internal fun catalogIdentityExtras(track: CatalogTrack) = CatalogIdentityExtras(
    values = buildMap {
        track.albumUri?.let { put(EXTRA_ALBUM_URI, it) }
        track.artistUri?.let { put(EXTRA_ARTIST_URI, it) }
    },
    lists = mapOf(
        EXTRA_ARTIST_NAMES to track.artists.map { it.name },
        EXTRA_ARTIST_URIS to track.artists.map { it.uri.orEmpty() },
    ),
)
