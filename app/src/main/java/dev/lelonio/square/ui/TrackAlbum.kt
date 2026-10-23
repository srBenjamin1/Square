package dev.lelonio.square.ui

import dev.lelonio.square.data.CatalogPlaylist
import dev.lelonio.square.data.CatalogTrack

internal fun canOpenAlbum(track: CatalogTrack): Boolean =
    !track.albumUri.isNullOrBlank() || track.uri.startsWith("spotify:track:")

internal suspend fun trackAlbum(track: CatalogTrack, lookup: suspend (String) -> CatalogPlaylist?): CatalogPlaylist? =
    track.albumUri?.takeIf { it.isNotBlank() }?.let { uri ->
        CatalogPlaylist(uri = uri, name = track.album, artworkUrl = track.artworkUrl)
    } ?: if (track.uri.startsWith("spotify:track:")) lookup(track.uri) else null
