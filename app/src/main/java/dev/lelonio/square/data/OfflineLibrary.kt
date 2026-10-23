package dev.lelonio.square.data

/** The two device shelves stay available alongside labelled downloaded playlists. */
internal object OfflineLibrary {
    fun playlists(owners: Collection<String>, labelOf: (String) -> DownloadStore.OwnerLabel?): List<CatalogPlaylist> =
        owners.mapNotNull { uri ->
            labelOf(uri)?.let { CatalogPlaylist(uri = uri, name = it.name, artworkUrl = it.artworkUrl) }
        }

    fun withDeviceShelves(playlists: List<CatalogPlaylist>, shelves: List<CatalogPlaylist>): List<CatalogPlaylist> =
        shelves + playlists.filterNot { playlist -> shelves.any { it.uri == playlist.uri } }.distinctBy { it.uri }
}
