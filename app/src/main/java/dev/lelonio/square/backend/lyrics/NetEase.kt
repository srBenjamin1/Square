package dev.lelonio.square.backend.lyrics

import dev.lelonio.square.data.LyricLine
import dev.lelonio.square.data.Lyrics
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

/**
 * High-accuracy synchronized LRC lyrics provider backed by NetEase Cloud Music's
 * extensive global library.
 *
 * Provides millisecond-accurate timestamps for millions of tracks that might be missing
 * or unsynced on Spotify and LRCLIB.
 */
object NetEase {

    private val http = OkHttpClient.Builder()
        .callTimeout(4, TimeUnit.SECONDS)
        .build()

    private const val SEARCH_URL = "https://music.163.com/api/search/pc"
    private const val LYRIC_URL = "https://music.163.com/api/song/lyric"
    private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"
    private const val COOKIE = "os=pc; NMTID=00OAVK3xqDG726ITU6jopU6jF2yMk0AAAGCO8l1BA;"

    suspend fun lyrics(
        title: String,
        artist: String,
        durationMs: Long,
    ): Lyrics? = withContext(Dispatchers.IO) {
        val cleanTitle = title.songTitle()
        val artists = artist.allArtists()

        for (candidateArtist in artists) {
            val result = query(cleanTitle, candidateArtist, durationMs)
            if (result != null) return@withContext result
        }

        // If not found with separate artists, try cleanTitle alone
        if (artists.size > 1) {
            query(cleanTitle, "", durationMs)?.let { return@withContext it }
        }

        null
    }

    private fun query(title: String, artist: String, durationMs: Long): Lyrics? {
        val q = if (artist.isBlank()) title else "$title $artist"
        val searchEncoded = runCatching { URLEncoder.encode(q, "UTF-8") }.getOrNull() ?: return null
        val searchReq = Request.Builder()
            .url("$SEARCH_URL?s=$searchEncoded&type=1&offset=0&limit=5")
            .header("User-Agent", USER_AGENT)
            .header("Referer", "https://music.163.com")
            .header("Cookie", COOKIE)
            .build()

        val searchBody = runCatching {
            http.newCall(searchReq).execute().use { resp ->
                if (resp.isSuccessful) resp.body.string() else null
            }
        }.getOrNull() ?: return null

        val root = runCatching { JSONObject(searchBody) }.getOrNull() ?: return null
        val songs = root.optJSONObject("result")?.optJSONArray("songs") ?: return null
        if (songs.length() == 0) return null

        var bestSongId: Long? = null
        var minDiff = Long.MAX_VALUE

        val targetArtistLower = artist.lowercase().trim()
        val hasTargetArtist = targetArtistLower.isNotEmpty()

        for (i in 0 until songs.length().coerceAtMost(10)) {
            val song = songs.optJSONObject(i) ?: continue
            val id = song.optLong("id")
            if (id <= 0) continue

            val songArtists = (0 until (song.optJSONArray("artists")?.length() ?: 0)).mapNotNull {
                song.optJSONArray("artists")?.optJSONObject(it)?.optString("name")?.lowercase()
            }
            val matchesArtist = !hasTargetArtist || songArtists.any { it.contains(targetArtistLower) || targetArtistLower.contains(it) }

            // If we have a target artist, skip songs that don't match it
            if (hasTargetArtist && !matchesArtist) continue

            val songDur = song.optLong("duration", 0L)
            val diff = if (durationMs > 0 && songDur > 0) kotlin.math.abs(songDur - durationMs) else 0L

            if (diff < minDiff) {
                minDiff = diff
                bestSongId = id
            }
        }

        // Only accept if duration is close (within 8 seconds) or duration wasn't specified
        if (durationMs > 0 && minDiff > 8000L && bestSongId != null) {
            return null
        }

        val targetId = bestSongId ?: return null
        val lyricReq = Request.Builder()
            .url("$LYRIC_URL?id=$targetId&lv=1&kv=1&tv=-1")
            .header("User-Agent", USER_AGENT)
            .header("Referer", "https://music.163.com")
            .build()

        val lyricBody = runCatching {
            http.newCall(lyricReq).execute().use { resp ->
                if (resp.isSuccessful) resp.body.string() else null
            }
        }.getOrNull() ?: return null

        val lyricRoot = runCatching { JSONObject(lyricBody) }.getOrNull() ?: return null
        val rawLrc = lyricRoot.optJSONObject("lrc")?.optString("lyric")?.takeIf { it.isNotBlank() }
            ?: return null

        return parseLrc(rawLrc)
    }

    private fun parseLrc(raw: String): Lyrics? {
        val lines = raw.lines().mapNotNull { line ->
            val match = LRC_LINE.find(line) ?: return@mapNotNull null
            val (minutes, seconds, fraction, text) = match.destructured
            val startMs = minutes.toLong() * 60_000 +
                seconds.toLong() * 1_000 +
                fraction.padEnd(3, '0').take(3).toLong()

            val cleanText = text.trim()
            // Filter out metadata lines like [00:00.00] 作曲 : ...
            if (cleanText.startsWith("作词") || cleanText.startsWith("作曲") || cleanText.startsWith("编曲")) {
                return@mapNotNull null
            }
            LyricLine(startTimeMs = startMs, text = cleanText)
        }.filter { it.text.isNotBlank() }

        return lines.takeIf { it.isNotEmpty() }?.let { Lyrics(it, synced = true) }
    }

    private val LRC_LINE = Regex("\\[(\\d{1,2}):(\\d{2})[.:](\\d{2,3})\\](.*)")
}
