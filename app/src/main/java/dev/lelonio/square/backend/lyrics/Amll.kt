package dev.lelonio.square.backend.lyrics

import dev.lelonio.square.data.Lyrics
import dev.lelonio.square.nativecore.NativeBridge
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

/**
 * TTML lyrics addressed by the track itself.
 *
 * A hand-built, public-domain archive whose files are named after the Spotify
 * track they belong to, which is what makes it worth asking before the archives
 * that are searched: every other source is given a title, an artist and a
 * length and has to work out which recording is meant — and works it out wrongly
 * for live takes, remasters and remixes often enough to matter. Here the key is
 * the id already in the queue, so a hit is the right recording by construction.
 *
 * The documents are the same TTML the rest of this package reads, so the
 * parsing, the translations and the word timings all come from [Ttml].
 *
 * Most of the catalogue is not in it. A miss costs one request and falls
 * through to the sources that were always there.
 */
object Amll {

    private val http = OkHttpClient.Builder()
        .callTimeout(5, TimeUnit.SECONDS)
        .build()

    suspend fun lyrics(trackUri: String): Lyrics? = withContext(Dispatchers.IO) {
        val id = trackUri.takeIf { it.startsWith("spotify:track:") }?.substringAfterLast(':')
            ?: return@withContext null

        document(id)?.let { return@withContext it }

        // The same song, under the ids of its other pressings.
        //
        // A recording is on Spotify many times over — the single, the album,
        // the remaster, the copy licensed for one market — and each of those
        // has its own id. An archive addressed by id therefore holds the song
        // and still misses, purely because the listener is playing a different
        // edition of it. Spotify's own metadata lists the copies it relinks
        // between, and reading that list costs one call to the session that is
        // already open.
        for (other in alternatives(trackUri)) {
            val alternative = other.takeIf { it.startsWith("spotify:track:") }
                ?.substringAfterLast(':')
                ?: continue
            if (alternative == id) continue
            document(alternative)?.let { return@withContext it }
        }
        null
    }

    private fun document(id: String): Lyrics? {
        val raw = runCatching {
            val request = Request.Builder().url("$BASE/$id.ttml").build()
            http.newCall(request).execute().use { response ->
                if (response.isSuccessful) response.body?.string() else null
            }
        }.getOrNull() ?: return null

        return Ttml.parse(raw)?.also {
            android.util.Log.i(
                TAG,
                "$id: ${it.lines.size} lines, " +
                    "${it.lines.count { line -> line.words.isNotEmpty() }} timed by word",
            )
        }
    }

    private fun alternatives(trackUri: String): List<String> = runCatching {
        val answer = NativeBridge.trackRelatives(trackUri) ?: return emptyList()
        val array = JSONObject(answer).optJSONArray("alternatives") ?: return emptyList()
        (0 until array.length()).mapNotNull { array.optString(it).takeIf(String::isNotEmpty) }
    }.getOrDefault(emptyList())

    private const val BASE =
        "https://raw.githubusercontent.com/amll-dev/amll-ttml-db/main/spotify-lyrics"

    private const val TAG = "SquareAmll"
}
