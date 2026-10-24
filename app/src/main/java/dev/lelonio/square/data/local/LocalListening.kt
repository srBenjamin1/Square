package dev.lelonio.square.data.local

import dev.lelonio.square.data.ListeningEvent

/** Views of the existing listening history. Dates added to MediaStore never enter these ranks. */
object LocalListening {
    const val RETENTION_DAYS = 365L
    const val MAX_EVENTS = 20_000
    private const val DAY = 86_400_000L
    enum class Window(val days: Long?) { WEEK(7), MONTH(30), ALL(null) }

    fun retained(events: List<ListeningEvent>, now: Long): List<ListeningEvent> = events
        .filter { it.startedAt >= now - RETENTION_DAYS * DAY }
        .sortedByDescending { it.startedAt }.take(MAX_EVENTS)

    /** Earlier versions filed physical-file events under the last remote owner. Originals stay intact. */
    fun recover(local: List<ListeningEvent>, legacy: List<ListeningEvent>, now: Long): List<ListeningEvent> =
        retained((local + legacy).filter { it.track.uri.startsWith("local:track:") }
            .groupBy { it.id }.values.map { same -> same.maxBy { it.listenedMs } }, now)

    fun remoteOnly(events: List<ListeningEvent>) = events.filterNot { it.track.uri.startsWith("local:track:") }

    fun summarize(catalog: LocalMusicCatalog, events: List<ListeningEvent>, now: Long,
                  window: Window = Window.ALL): Summary {
        val plays = events.asSequence().filter { it.qualified && it.track.uri in catalog.byUri }
            .filter { it.startedAt in (now - RETENTION_DAYS * DAY)..now && (window.days == null || it.startedAt >= now - window.days * DAY) }
            .distinctBy { it.id }.sortedByDescending { it.lastAudibleAt ?: it.startedAt }.toList()
        val recent = plays.mapNotNull { catalog.byUri[it.track.uri] }.distinctBy { it.uri }
        val counts = plays.groupingBy { it.track.uri }.eachCount()
        val top = recent.sortedByDescending { counts[it.uri] ?: 0 } // recency breaks ties
        val albums = recent.mapNotNull { catalog.albumByUri[it.albumUri] }.distinctBy { it.uri }
        val artistsByTrack = catalog.artists.flatMap { a -> a.tracks.map { it.uri to a } }.groupBy({ it.first }, { it.second })
        val artists = recent.flatMap { artistsByTrack[it.uri].orEmpty() }.distinctBy { it.uri }
        val used = plays.mapNotNull { it.contextUri }.distinct().withIndex().associate { it.value to it.index }
        val playlists = catalog.playlists.sortedBy { used[it.uri] ?: Int.MAX_VALUE }
        return Summary(recent, albums, artists, top, counts, playlists)
    }

    data class Summary(val recent: List<LocalTrack>, val albums: List<LocalAlbum>,
        val artists: List<LocalArtist>, val top: List<LocalTrack>, val counts: Map<String, Int>,
        val playlists: List<LocalPlaylist>)
}
