package dev.lelonio.square.data.local

import kotlinx.serialization.Serializable

@Serializable enum class LocalFolderMode { ALL, SELECTED }
@Serializable data class LocalMusicFolder(val uri: String, val name: String, val accessible: Boolean = true)

/** Permission loss cannot silently turn the collection into an empty restrictive filter. */
object LocalFolderPolicy {
    fun effective(mode: LocalFolderMode, folders: List<LocalMusicFolder>): LocalFolderMode =
        if (mode == LocalFolderMode.SELECTED && folders.none { it.accessible }) LocalFolderMode.ALL else mode
    fun visible(snapshot: LocalMusicSnapshot): List<LocalTrack> =
        if (effective(snapshot.folderMode, snapshot.folders) == LocalFolderMode.ALL) snapshot.tracks.filter { it.available }
        else snapshot.tracks.filter { track -> track.available && track.treeUris.any { tree -> snapshot.folders.any { it.uri == tree && it.accessible } } }
    fun documentKey(authority: String, id: String) = "$authority|$id"
    fun storageKey(volume: String, path: String) = "${if (volume == "external_primary") "primary" else volume.lowercase()}|${path.trimStart('/')}"
    fun safId(key: String): Long = -(localKey("document", key).takeLast(15).toLong(16) + 1)

    /** A provider identity/path is evidence; filename or title equality is not. Preserve previous IDs. */
    fun merge(media: List<LocalTrack>, documents: List<LocalTrack>, previous: List<LocalTrack>): List<LocalTrack> {
        fun raw(t: LocalTrack) = t.storageKey.ifBlank { t.documentKey ?: t.contentUri }
        val mediaKeys = media.filter { it.id >= 0 }.associate { it.id to raw(it) }
        fun canonical(t: LocalTrack) = t.storageKey.ifBlank { mediaKeys[t.mediaReferenceId ?: t.id] ?: raw(t) }
        val aliases = documents.mapNotNull { t -> t.documentKey?.let { it to canonical(t) } }.toMap()
        fun key(t: LocalTrack) = aliases[t.documentKey] ?: canonical(t)
        val prior = previous.associateBy(::key)
        return (media + documents).groupBy(::key).map { (identity, rows) ->
            val old = prior[identity]
            val source = rows.firstOrNull { it.documentKey != null && it.available } ?: rows.firstOrNull { it.available } ?: rows.first()
            val base = rows.firstOrNull { it.id >= 0 } ?: source
            val trees = rows.flatMap { it.treeUris }.distinct()
            // Use the explicitly authorized document for playback when available, keeping the old URI.
            base.copy(id = old?.id ?: base.id, contentUri = source.contentUri,
                documentKey = source.documentKey, treeUris = trees, available = source.available,
                title = source.title.ifBlank { base.title }, artist = source.artist.ifBlank { base.artist }, album = source.album.ifBlank { base.album },
                durationMs = source.durationMs.takeIf { it > 0 } ?: base.durationMs,
                track = source.track.takeIf { it > 0 } ?: base.track, disc = source.disc.takeIf { it > 0 } ?: base.disc,
                year = source.year.ifBlank { base.year }, mediaReferenceId = source.mediaReferenceId ?: base.mediaReferenceId,
                albumArtist = source.albumArtist.ifBlank { base.albumArtist }, genre = source.genre.ifBlank { base.genre })
        }
    }
}
