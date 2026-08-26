package dev.lelonio.square.backend.lyrics

import dev.lelonio.square.data.Lyrics
import java.net.URLEncoder
import kotlin.math.abs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

/**
 * Synced TTML lyrics from the archive lossless.wtf reads, asked before anything else.
 *
 * The other two sources each give up something. LrcLib is LRC, so a line is the
 * smallest thing it can time, and its timings are community-entered and often
 * a beat out. Spotify's own are line-synced too, and only exist for a track
 * Spotify itself holds. This archive publishes TTML — the format Apple Music
 * ships, timed down to the word and mastered against the release rather than
 * typed along to it — so where it has a track it is the better answer, and it
 * is asked first for every track on every backend.
 *
 * Two requests: an index is searched by title, artist and length and answers
 * with the releases it holds, each naming the URL of its document; the document
 * itself is then fetched from the storage host.
 *
 * A miss is ordinary: the archive is far smaller than LrcLib's, and most of the
 * catalogue is not in it. Everything here answers null rather than raising, and
 * the caller falls through to the source it used before.
 *
 * @see Ttml for what is done with the document once it arrives.
 */
object Lossless {

    private val http = OkHttpClient()

    /**
     * @param durationMs tells versions apart — a single, an album cut and a live
     *   take share a title and an artist, and the length is what separates
     *   them. Sent as seconds, and only when it is known.
     */
    suspend fun lyrics(title: String, artist: String, durationMs: Long): Lyrics? =
        withContext(Dispatchers.IO) {
            val cleaned = title.songTitle()
            val results = search(cleaned, artist, durationMs)
            if (results == null) {
                android.util.Log.i(TAG, "no match for $cleaned / $artist (${durationMs / 1000}s)")
                return@withContext null
            }
            val url = pick(results, durationMs) ?: return@withContext null
            val parsed = Ttml.parse(get(url) ?: return@withContext null)
            android.util.Log.i(
                TAG,
                "$url: ${parsed?.lines?.size ?: 0} lines, " +
                    "${parsed?.lines?.count { it.words.isNotEmpty() } ?: 0} timed by word, " +
                    "${parsed?.lines?.count { !it.translation.isNullOrBlank() } ?: 0} translated",
            )
            parsed
        }

    /**
     * The index's answer, or null when it holds nothing for the track.
     *
     * Matching is on title, artist and length, the same identity the other
     * lyrics sources are searched by — the archive is not keyed on any id
     * Square holds, since a YouTube video id and a Spotify uri each mean
     * nothing to the other.
     */
    private fun search(title: String, artist: String, durationMs: Long): List<JSONObject>? {
        val url = buildString {
            append(SEARCH_URL).append("?track=").append(encode(title))
            append("&artist=").append(encode(artist))
            if (durationMs > 0) append("&duration=").append(durationMs / 1000)
        }
        val body = get(url) ?: return null
        val results = runCatching { JSONObject(body).optJSONArray("results") }.getOrNull()
            ?: return null
        return (0 until results.length())
            .mapNotNull { results.optJSONObject(it) }
            .takeIf { it.isNotEmpty() }
    }

    /**
     * The document to fetch out of everything the index returned.
     *
     * Releases of the same song are timed to different masters, so the one
     * whose length matches what is playing is the one whose timings will land.
     * Word timing wins ties: a release timed to the word is what this source is
     * asked for in the first place, and a line-timed one is no better than what
     * the caller already had.
     */
    private fun pick(results: List<JSONObject>, durationMs: Long): String? = results
        .filter { it.optString("lyricsUrl").startsWith("http") }
        .minByOrNull { result ->
            val byWord = if (result.optString("timing_type") == "word") 0 else 1
            val drift = if (durationMs > 0) {
                abs(result.optLong("duration") * 1000 - durationMs)
            } else {
                0L
            }
            byWord * 1_000_000L + drift
        }
        ?.optString("lyricsUrl")

    /** Null on anything but a 200, which includes the 404 for "no match". */
    private fun get(url: String): String? = runCatching {
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/ttml+xml, application/xml, application/json")
            // The archive answers the web player its pages run in, so the
            // request is made to look like one of those pages making it rather
            // than wearing a library's default agent.
            .header("User-Agent", USER_AGENT)
            .header("Referer", "$PLAYER_URL/")
            .header("Origin", PLAYER_URL)
            .build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) null else response.body?.string()?.takeIf(String::isNotBlank)
        }
    }.getOrNull()

    private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")

    private const val TAG = "Lossless"

    private const val SEARCH_URL = "https://lyrics-api.binimum.org/"

    private const val PLAYER_URL = "https://lossless.wtf"

    private const val USER_AGENT =
        "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/139.0.0.0 Mobile Safari/537.36"
}
