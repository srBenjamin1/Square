package dev.lelonio.square.data

import org.json.JSONObject

/**
 * The playlists shown under a playlist, read from Spotify's own gateway.
 *
 * The row the web player draws once the last song has scrolled past: lists
 * like the one on screen, picked by Spotify for that list. The Web API has no
 * endpoint for it. The answer comes back in the home page's shape, a
 * collection of sections with one row in it, and carries no heading of its
 * own; the page supplies that.
 *
 * Forgiving in the same way [PlaylistContents] is, and for the same reason.
 */
object RelatedPlaylists {

    /**
     * The row to ask for, alongside the playlist it is about.
     *
     * The same section for every list the web player was seen asking about,
     * a Daily Mix among them, the way the home page's recents are one fixed
     * section too.
     */
    const val SECTION = "spotify:section:0JQ5DAob0LgAOAm50K90Od"

    /** Null when the answer was not that row: a retired query hash, a 401. */
    fun parse(json: String): List<SearchItem>? = runCatching {
        val sections = JSONObject(json)
            .getJSONObject("data")
            .getJSONObject("homeSections")
            .getJSONArray("sections")

        (0 until sections.length()).flatMap { section ->
            val items = sections.optJSONObject(section)
                ?.optJSONObject("sectionItems")
                ?.optJSONArray("items")
                ?: return@flatMap emptyList()
            (0 until items.length()).mapNotNull { index ->
                val data = items.optJSONObject(index)
                    ?.optJSONObject("content")
                    ?.optJSONObject("data")
                    ?: return@mapNotNull null
                if (data.optString("__typename") != "Playlist") return@mapNotNull null
                val uri = data.optString("uri").takeIf { it.startsWith("spotify:playlist:") }
                    ?: return@mapNotNull null
                val name = data.optString("name").trim().takeIf { it.isNotEmpty() }
                    ?: return@mapNotNull null
                // Who made it, when that is a person. Nearly every one of these
                // is the service's own, and its name under every cover would say
                // the same word six times.
                val owner = data.optJSONObject("ownerV2")?.optJSONObject("data")
                    ?.optString("name").orEmpty()
                SearchItem(
                    uri = uri,
                    title = name,
                    subtitle = owner.takeUnless { it == "Spotify" }.orEmpty(),
                    artworkUrl = data.optJSONObject("images")
                        ?.optJSONArray("items")?.optJSONObject(0)
                        ?.optJSONArray("sources")?.optJSONObject(0)
                        ?.optString("url")?.takeIf { it.isNotEmpty() },
                )
            }
        }.distinctBy { it.uri }
    }.getOrNull()
}
