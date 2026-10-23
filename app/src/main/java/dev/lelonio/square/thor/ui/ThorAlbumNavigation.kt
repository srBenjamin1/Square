package dev.lelonio.square.thor.ui

import dev.lelonio.square.data.CatalogPlaylist
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** A top-player album link is consumed by the bottom surface's own navigator. */
internal object ThorAlbumNavigation {
    private val mutablePending = MutableStateFlow<CatalogPlaylist?>(null)
    val pending = mutablePending.asStateFlow()
    fun request(album: CatalogPlaylist) { mutablePending.value = album }
    fun consume(album: CatalogPlaylist) { mutablePending.compareAndSet(album, null) }
}
