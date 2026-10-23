package dev.lelonio.square.ui.player

import dev.lelonio.square.data.CatalogArtist
import dev.lelonio.square.data.MetadataDestination

internal object PlayerMetadata {
    data class Artist(val name: String, val destination: MetadataDestination?)

    fun artists(state: PlaybackState): List<Artist> {
        val source = MetadataDestination.serviceOfTrack(state.mediaId)
        val credits = state.artists.ifEmpty {
            listOfNotNull(state.artist.takeIf { it.isNotBlank() }?.let { CatalogArtist(it, state.artistUri.takeUnless { _ -> it.contains(", ") || it.contains(" & ") }) })
        }
        return credits.map { artist ->
            Artist(artist.name, MetadataDestination.create(MetadataDestination.Type.ARTIST, artist.uri, artist.name, source))
        }
    }

    fun album(state: PlaybackState): MetadataDestination? = MetadataDestination.create(
        MetadataDestination.Type.ALBUM, state.albumUri, state.album,
        MetadataDestination.serviceOfTrack(state.mediaId), state.artworkUrl,
    )


}
