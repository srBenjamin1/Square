package dev.lelonio.square.ui

import dev.lelonio.square.backend.BackendId
import dev.lelonio.square.data.CatalogPlaylist
import dev.lelonio.square.data.CatalogTrack
import dev.lelonio.square.data.MetadataDestination
import kotlinx.coroutines.CancellationException

private fun albumDestination(track: CatalogTrack, album: CatalogPlaylist) = MetadataDestination.create(
    MetadataDestination.Type.ALBUM, album.uri, album.name,
    MetadataDestination.serviceOfTrack(track.uri), album.artworkUrl,
)

internal fun canOpenAlbum(track: CatalogTrack): Boolean =
    MetadataDestination.create(MetadataDestination.Type.ALBUM, track.albumUri, track.album,
        MetadataDestination.serviceOfTrack(track.uri)) != null ||
        MetadataDestination.serviceOfTrack(track.uri) == BackendId.SPOTIFY

internal suspend fun trackAlbum(track: CatalogTrack, lookup: suspend (String) -> CatalogPlaylist?): CatalogPlaylist? {
    val direct = track.albumUri?.let { CatalogPlaylist(it, track.album, artworkUrl = track.artworkUrl) }
    if (direct != null && track.album.isNotBlank() && albumDestination(track, direct) != null) return direct
    if (MetadataDestination.serviceOfTrack(track.uri) != BackendId.SPOTIFY) return null
    return lookup(track.uri)?.takeIf { albumDestination(track, it) != null }
}

/** Resolve by the track ID already known to the engine; OAuth Web API is only a fallback. */
internal suspend fun resolveSpotifyAlbum(
    trackUri: String,
    nativeTracks: suspend (List<String>) -> List<CatalogTrack>,
    webAlbum: suspend (String) -> CatalogPlaylist?,
    trace: (String, String) -> Unit = { _, _ -> },
): CatalogPlaylist? {
    if (MetadataDestination.serviceOfTrack(trackUri) != BackendId.SPOTIFY) return null
    val track = try {
        nativeTracks(listOf(trackUri)).firstOrNull { it.uri == trackUri }
    } catch (failure: Exception) {
        if (failure is CancellationException) throw failure
        trace("album.native", "failed")
        null
    }
    if (track != null) {
        val direct = track.albumUri?.let { CatalogPlaylist(it, track.album, artworkUrl = track.artworkUrl) }
        if (direct != null && albumDestination(track, direct) != null) {
            trace("album.native", "resolved")
            return direct
        }
    }
    trace("album.native", "missing")
    return try {
        val found = webAlbum(trackUri)
        val valid = found?.takeIf {
            MetadataDestination.create(MetadataDestination.Type.ALBUM, it.uri, it.name, BackendId.SPOTIFY) != null
        }
        trace("album.web", if (valid != null) "resolved" else "missing")
        valid
    } catch (failure: Exception) {
        if (failure is CancellationException) throw failure
        trace("album.web", "failed")
        null
    }
}
