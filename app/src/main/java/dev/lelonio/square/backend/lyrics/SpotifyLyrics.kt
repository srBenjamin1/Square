package dev.lelonio.square.backend.lyrics

import dev.lelonio.square.data.Catalog
import dev.lelonio.square.data.Lyrics
import dev.lelonio.square.download.DownloadExtras
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

/**
 * The words behind a Spotify track, and the copy kept beside a download.
 *
 * The chain itself is not new — the TTML archive, then lossless.wtf, then
 * Spotify's own — but it now has two callers rather than one: the player, which
 * asks when a song starts, and the download queue, which asks so that the same
 * song has its words in a tunnel. One function so the two cannot drift: a
 * listener who downloads a song and then plays it offline must be shown the
 * lyrics they would have been shown online, not a different source's.
 *
 * Storing is part of asking, for the same reason. Every answer that arrives for
 * a downloaded track is written down on the way past — [DownloadExtras] decides
 * which tracks those are — so nothing else has to remember to do it, and a song
 * whose words were fetched while the player was showing them is a song that
 * keeps them.
 */
object SpotifyLyrics {

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * @param allowNetwork false offline, where the only lyrics are the kept ones.
     */
    suspend fun lyrics(
        uri: String,
        title: String,
        artist: String,
        durationMs: Long,
        allowNetwork: Boolean = true,
    ): Lyrics? {
        if (!allowNetwork) return kept(uri)

        val found = Amll.lyrics(uri)
            ?: Lossless.lyrics(title, artist, durationMs)
            ?: Catalog.lyrics(uri)

        return withContext(Dispatchers.IO) {
            // Nothing found is an answer worth keeping too, so a downloaded
            // song with no words is not looked up again on every turn of the
            // queue; see DownloadExtras.note.
            if (found == null) {
                DownloadExtras.note("lyrics", uri)
                return@withContext kept(uri)
            }
            runCatching { DownloadExtras.rememberLyrics(uri, json.encodeToString(found)) }
            found
        }
    }

    /** What was written down for this track, if it was ever asked about. */
    suspend fun kept(uri: String): Lyrics? = withContext(Dispatchers.IO) {
        val raw = DownloadExtras.lyrics(uri) ?: return@withContext null
        runCatching { json.decodeFromString<Lyrics>(raw) }.getOrNull()
    }
}
