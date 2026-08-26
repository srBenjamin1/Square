package dev.lelonio.square.backend.lyrics

import java.net.URLEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray

/**
 * The lyrics in the language the app is read in, for the songs that ship none.
 *
 * A TTML document sometimes carries a subtitle track a label had made, and that
 * is always the better text — a translation of the song rather than of its
 * sentences. Most do not, and most songs are in a language somebody is not
 * reading: the rest are put through Google's own translation endpoint, the one
 * its browser extension talks to, which needs no key and answers a batch of
 * lines at a time.
 *
 * Machine translation of a lyric is what it sounds like, and that is the point
 * of asking for it explicitly: nothing here runs until the toggle over the
 * lyrics is turned on.
 */
object Translate {

    private val http = OkHttpClient()

    /**
     * @param target a two-letter language, as [dev.lelonio.square.data.LanguageStore.language]
     *   gives it.
     * @return one translation per line of [texts], in the same order, or null
     *   if the endpoint could not be reached at all. A line that came back
     *   untranslated is empty rather than a copy of itself.
     */
    suspend fun lines(texts: List<String>, target: String): List<String>? =
        withContext(Dispatchers.IO) {
            // The gaps between verses are lines too, and there is nothing in
            // them to translate. Sent, they would each cost a slot in a batch
            // and come back as themselves.
            val wanted = texts.withIndex().filter { it.value.isNotBlank() }
            if (wanted.isEmpty()) return@withContext null

            val out = arrayOfNulls<String>(texts.size)
            var reached = false

            for (batch in wanted.chunkedByLength()) {
                val answers = translate(batch.map { it.value }, target) ?: continue
                reached = true
                batch.forEachIndexed { at, line ->
                    val answer = answers.getOrNull(at).orEmpty()
                    // A translation identical to the line is the endpoint
                    // saying the song is already in this language, and a second
                    // copy of the line under the line is noise.
                    if (answer.isNotBlank() && !answer.equals(line.value, ignoreCase = true)) {
                        out[line.index] = answer
                    }
                }
            }

            if (!reached) null else out.map { it.orEmpty() }
        }

    /**
     * One request: several lines out, the same number back.
     *
     * The lines are sent as repeated `q` parameters rather than joined by
     * newlines, which is the other way this endpoint is used. Joined, a line
     * the translator decides to merge or split takes every line after it out of
     * step with the song, and a lyric sheet one line out is worse than none.
     */
    private fun translate(texts: List<String>, target: String): List<String>? {
        val url = buildString {
            append(BASE_URL)
            append("?client=dict-chrome-ex&sl=auto&tl=").append(encode(target))
            texts.forEach { append("&q=").append(encode(it)) }
        }

        val body = runCatching {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)
                .build()
            http.newCall(request).execute().use { response ->
                if (response.isSuccessful) response.body?.string() else null
            }
        }.getOrNull() ?: return null

        // `[["translated","detected-language"], ...]`, one entry per line sent.
        val array = runCatching { JSONArray(body) }.getOrNull() ?: return null
        return (0 until array.length()).map { at ->
            array.optJSONArray(at)?.optString(0).orEmpty()
        }
    }

    /**
     * Batches, small enough that the whole request fits in a URL.
     *
     * Everything here goes in the query string, and a query string has a length
     * a server will refuse past. A verse or two per request keeps well inside
     * it, and a song is a handful of requests either way.
     */
    private fun List<IndexedValue<String>>.chunkedByLength(): List<List<IndexedValue<String>>> {
        val batches = mutableListOf<List<IndexedValue<String>>>()
        var batch = mutableListOf<IndexedValue<String>>()
        var length = 0

        for (line in this) {
            if (batch.isNotEmpty() && length + line.value.length > BATCH_CHARS) {
                batches += batch
                batch = mutableListOf()
                length = 0
            }
            batch += line
            length += line.value.length
        }
        if (batch.isNotEmpty()) batches += batch
        return batches
    }

    private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")

    private const val BATCH_CHARS = 1_200

    private const val BASE_URL = "https://clients5.google.com/translate_a/t"

    private const val USER_AGENT =
        "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/139.0.0.0 Mobile Safari/537.36"
}
