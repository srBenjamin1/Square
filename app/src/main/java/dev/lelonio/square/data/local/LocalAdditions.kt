package dev.lelonio.square.data.local

/** MediaStore incorporation dates only. Unknown dates never become fabricated releases. */
data class LocalAdditions(val songs: List<LocalTrack>, val albums: List<LocalAlbum>, val artists: List<LocalArtist>) {
    companion object {
        fun from(catalog: LocalMusicCatalog) = LocalAdditions(
            catalog.ordered(LocalTrackOrder.ADDED).filter { it.addedSeconds > 0 },
            catalog.albums.filter { a -> a.tracks.any { it.addedSeconds > 0 } }
                .sortedByDescending { a -> a.tracks.maxOf { it.addedSeconds } },
            catalog.artists.filter { a -> a.tracks.any { it.addedSeconds > 0 } }
                .sortedByDescending { a -> a.tracks.maxOf { it.addedSeconds } },
        )
    }
}
