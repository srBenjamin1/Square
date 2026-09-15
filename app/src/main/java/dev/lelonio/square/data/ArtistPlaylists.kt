package dev.lelonio.square.data

import org.json.JSONObject

/**
 * The playlists an artist is in, read from Spotify's own gateway.
 *
 * The row the desktop client heads "Featuring" on an artist's page, and the
 * one place "This Is" can be found without guessing: it comes first, Spotify's
 * own, followed by the artist's radio and the editorial lists they are on. The
 * Web API has no endpoint for it, and searching for "This Is" plus a name
 * finds everything anybody ever titled after them.
 *
 * Forgiving in the same way [PlaylistContents] is, and for the same reason.
 */
object ArtistPlaylists {

    /** Null when the answer was not an artist's page: a retired query hash, a 401. */
    fun parse(json: String): List<SearchItem>? = runCatching {
        val items = JSONObject(json)
            .getJSONObject("data")
            .getJSONObject("artistUnion")
            .getJSONObject("relatedContent")
            .getJSONObject("featuringV2")
            .getJSONArray("items")

        (0 until items.length()).mapNotNull { index ->
            val data = items.optJSONObject(index)?.optJSONObject("data") ?: return@mapNotNull null
            // The row mixes in what it could not resolve as "GenericError".
            if (data.optString("__typename") != "Playlist") return@mapNotNull null
            val uri = data.optString("uri").takeIf { it.startsWith("spotify:playlist:") }
                ?: return@mapNotNull null
            val name = data.optString("name").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            SearchItem(
                uri = uri,
                title = name,
                subtitle = plain(data.optString("description")),
                artworkUrl = data.optJSONObject("images")
                    ?.optJSONArray("items")?.optJSONObject(0)
                    ?.optJSONArray("sources")?.optJSONObject(0)
                    ?.optString("url")?.takeIf { it.isNotEmpty() },
            )
        }
            // "This Is" first when the row did not already put it there: it is
            // the one everybody means by "the artist's playlist".
            .sortedBy { if (it.uri.startsWith(THIS_IS) || it.title.startsWith("This Is")) 0 else 1 }
    }.getOrNull()

    /** Descriptions can carry markup, links to an Instagram account for one. */
    private fun plain(text: String): String =
        android.text.Html.fromHtml(text, android.text.Html.FROM_HTML_MODE_LEGACY).toString().trim()

    /** Every "This Is" playlist so far has lived under this prefix. */
    private const val THIS_IS = "spotify:playlist:37i9dQZF1DZ06"
}
