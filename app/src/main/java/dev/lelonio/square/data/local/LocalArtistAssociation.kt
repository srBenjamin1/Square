package dev.lelonio.square.data.local

import dev.lelonio.square.backend.BackendId
import dev.lelonio.square.data.MetadataDestination
import java.net.URI

object LocalArtistAssociation {
    fun profile(input: String): String? {
        val text = input.trim()
        val uri = when {
            text.startsWith("spotify:artist:") || text.startsWith("ytmusic:artist:") -> text
            else -> runCatching {
                val link = URI(text)
                if (link.scheme != "https" || link.userInfo != null || link.port != -1) return null
                val parts = link.path.trim('/').split('/')
                val segments = if (parts.firstOrNull()?.startsWith("intl-") == true) parts.drop(1) else parts
                when (link.host) {
                    "open.spotify.com" -> {
                        if (segments.size != 2 || segments[0] != "artist") return null
                        "spotify:artist:" + segments[1]
                    }
                    "music.youtube.com", "www.youtube.com", "youtube.com" -> {
                        if (segments.size != 2 || segments[0] !in listOf("channel", "browse") || !segments[1].startsWith("UC")) return null
                        "ytmusic:artist:" + segments[1]
                    }
                    else -> return null
                }
            }.getOrNull() ?: return null
        }
        val backend = if (uri.startsWith("spotify:")) BackendId.SPOTIFY else BackendId.YOUTUBE_MUSIC
        return MetadataDestination.create(MetadataDestination.Type.ARTIST, uri, "", backend)?.uri
    }
}
