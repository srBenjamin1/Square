package dev.lelonio.square.data.util

sealed class SpotifyLink(val rawId: String) {
    abstract val uri: String

    data class Track(val id: String) : SpotifyLink(id) {
        override val uri: String get() = "spotify:track:$id"
    }
    data class Album(val id: String) : SpotifyLink(id) {
        override val uri: String get() = "spotify:album:$id"
    }
    data class Playlist(val id: String) : SpotifyLink(id) {
        override val uri: String get() = "spotify:playlist:$id"
    }
    data class Artist(val id: String) : SpotifyLink(id) {
        override val uri: String get() = "spotify:artist:$id"
    }
}

object SpotifyLinkParser {
    private val HTTP_REGEX = Regex(
        """https?://(?:open\.)?spotify\.com/(?:intl-[a-zA-Z]+/)?(track|album|playlist|artist)/([a-zA-Z0-9]+)""",
        RegexOption.IGNORE_CASE,
    )
    private val URI_REGEX = Regex(
        """spotify:(track|album|playlist|artist):([a-zA-Z0-9]+)""",
        RegexOption.IGNORE_CASE,
    )

    fun parse(input: String): SpotifyLink? {
        val trimmed = input.trim()
        val httpMatch = HTTP_REGEX.find(trimmed)
        if (httpMatch != null) {
            val type = httpMatch.groupValues[1].lowercase()
            val id = httpMatch.groupValues[2]
            return create(type, id)
        }
        val uriMatch = URI_REGEX.find(trimmed)
        if (uriMatch != null) {
            val type = uriMatch.groupValues[1].lowercase()
            val id = uriMatch.groupValues[2]
            return create(type, id)
        }
        return null
    }

    private fun create(type: String, id: String): SpotifyLink? {
        return when (type) {
            "track" -> SpotifyLink.Track(id)
            "album" -> SpotifyLink.Album(id)
            "playlist" -> SpotifyLink.Playlist(id)
            "artist" -> SpotifyLink.Artist(id)
            else -> null
        }
    }
}
