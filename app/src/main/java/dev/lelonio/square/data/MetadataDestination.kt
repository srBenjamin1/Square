package dev.lelonio.square.data

import dev.lelonio.square.backend.BackendId

/** A catalogue identity; display names never resolve or change the destination. */
data class MetadataDestination private constructor(
    val type: Type,
    val uri: String,
    val service: BackendId,
    val name: String,
    val artworkUrl: String?,
) {
    enum class Type { ARTIST, ALBUM }

    fun asPlaylist() = CatalogPlaylist(uri = uri, name = name, artworkUrl = artworkUrl)

    companion object {
        fun serviceOfTrack(uri: String?): BackendId? = when {
            uri?.startsWith("spotify:track:") == true -> BackendId.SPOTIFY
            uri?.startsWith("ytmusic:track:") == true || uri?.startsWith("ytmusic:video:") == true -> BackendId.YOUTUBE_MUSIC
            else -> null
        }

        fun create(type: Type, uri: String?, name: String, expectedService: BackendId?, artworkUrl: String? = null): MetadataDestination? {
            if (uri == null || expectedService == null) return null
            val source = if (expectedService == BackendId.SPOTIFY) "spotify" else "ytmusic"
            val kind = if (type == Type.ARTIST) "artist" else "album"
            val prefix = "$source:$kind:"
            if (!uri.startsWith(prefix)) return null
            val id = uri.removePrefix(prefix)
            if (id.isBlank() || !id.all { it in 'a'..'z' || it in 'A'..'Z' || it in '0'..'9' ||
                    (expectedService == BackendId.YOUTUBE_MUSIC && it in "_-") }) return null
            return MetadataDestination(type, uri, expectedService, name, artworkUrl)
        }
    }
}
