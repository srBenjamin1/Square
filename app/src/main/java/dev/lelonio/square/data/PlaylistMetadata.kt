package dev.lelonio.square.data

/** Keeps playlist order and membership timestamps while filling gateway placeholders. */
internal object PlaylistMetadata {
    fun isSparse(track: CatalogTrack): Boolean =
        track.uri.startsWith("spotify:track:") && track.artist.isBlank() && track.durationMs <= 0L

    fun complete(tracks: List<CatalogTrack>, found: List<CatalogTrack>): List<CatalogTrack> {
        val resolved = found.filterNot(::isSparse).associateBy { it.uri }
        return tracks.map { track ->
            resolved[track.uri]?.takeIf { isSparse(track) }?.copy(addedAt = track.addedAt) ?: track
        }
    }
}
