package dev.lelonio.square.ui.player

import dev.lelonio.square.data.CatalogTrack
import dev.lelonio.square.data.CatalogPlaylist
import dev.lelonio.square.data.MetadataDestination
import dev.lelonio.square.ui.trackAlbum
import kotlinx.coroutines.CancellationException

/** Only metadata presentation changes: transport, artwork and playback state remain untouched. */
internal data class ResolvedPlayerMetadata(val state: PlaybackState, val resolving: Boolean = false)

internal fun PlaybackState.metadataTrack() = CatalogTrack(
    uri = mediaId.orEmpty(), name = title, artist = artist, album = album,
    albumUri = albumUri, artistUri = artistUri, artists = artists,
)

internal fun CatalogTrack.metadataState() = PlaybackState(
    mediaId = uri, title = name, artist = artist, album = album,
    albumUri = albumUri, artistUri = artistUri, artists = artists,
)

internal suspend fun resolvePlayerMetadata(
    source: CatalogTrack,
    lookup: suspend (String) -> CatalogTrack?,
    albumLookup: suspend (String) -> CatalogPlaylist?,
): PlaybackState {
    if (MetadataDestination.serviceOfTrack(source.uri) == null) return source.metadataState()
    val known = source.metadataState()
    val needsIdentity = PlayerMetadata.artists(known).isEmpty() ||
        PlayerMetadata.artists(known).any { it.destination == null } ||
        source.album.isBlank() || PlayerMetadata.album(known) == null
    val found = if (needsIdentity) try {
        lookup(source.uri)?.takeIf { it.uri == source.uri }
    } catch (failure: Exception) {
        if (failure is CancellationException) throw failure
        null
    } else null
    // All individual identities come from this exact song, never from a name search.
    val merged = source.copy(
        artist = found?.artist?.takeIf { it.isNotBlank() } ?: source.artist,
        artists = found?.artists?.takeIf { it.isNotEmpty() } ?: source.artists,
        artistUri = found?.artistUri ?: source.artistUri,
        album = found?.album?.takeIf { it.isNotBlank() } ?: source.album,
        albumUri = found?.albumUri ?: source.albumUri,
    )
    val album = if (merged.album.isBlank() || PlayerMetadata.album(merged.metadataState()) == null) try {
        trackAlbum(merged, albumLookup)
    } catch (failure: Exception) {
        if (failure is CancellationException) throw failure
        null
    } else null
    return merged.copy(
        album = album?.name?.takeIf { it.isNotBlank() } ?: merged.album,
        albumUri = album?.uri ?: merged.albumUri,
    ).metadataState()
}
