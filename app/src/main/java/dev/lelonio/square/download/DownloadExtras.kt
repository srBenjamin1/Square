package dev.lelonio.square.download

import kotlinx.coroutines.Dispatchers
import org.json.JSONObject
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URL

/**
 * Everything about a downloaded song that is not the song.
 *
 * The words, the Canvas, the cover and what the artist is. Offline these are
 * the difference between a player that works and a player that looks broken:
 * the audio plays either way, but a blank cover over a track with no lyrics and
 * an empty artist page reads as an app that has lost its connection to itself.
 *
 * ### Where it plugs in
 *
 * Inside [dev.lelonio.square.data.Catalog], which is the one funnel every one of
 * these already goes through. A successful answer is written down on the way
 * past; a failed one is looked up here. So nothing else in the app has to know
 * this exists, and no screen grew an offline branch of its own.
 *
 * ### What is kept, and what is not
 *
 * Only for tracks that are actually downloaded — see [isKept], which the
 * container wires to the download index. Caching the lyrics of everything ever
 * opened would be a second, unbounded store with no owner and nothing to sweep
 * it, which is exactly the shape of thing that quietly fills a phone.
 *
 * A stored answer is a fallback and never a preference. Online the request is
 * still made and its answer still wins: lyrics get corrected, Canvases get
 * replaced, and a cache that shadowed them would keep showing yesterday's.
 * The one exception is the cover, where the bytes cannot differ — the URL names
 * the image — so a local copy is used straight away and saves the round trip.
 */
object DownloadExtras {

    private var root: File? = null

    /** Whether a track is downloaded, and therefore worth keeping extras for. */
    private var kept: (String) -> Boolean = { false }

    fun attach(downloadsRoot: File, isKept: (String) -> Boolean) {
        root = File(downloadsRoot, "extras")
        kept = isKept
        forgetArtAnswers()
    }

    // ------------------------------------------------------------ the answers

    fun rememberLyrics(trackUri: String, raw: String) = remember("lyrics", trackUri, raw)

    fun lyrics(trackUri: String): String? = recall("lyrics", trackUri)

    fun rememberCanvas(trackUri: String, raw: String) = remember("canvas", trackUri, raw)

    fun canvas(trackUri: String): String? = recall("canvas", trackUri)

    /**
     * Artist descriptions are kept for anyone with a downloaded track, so the
     * page behind a song is not empty offline. Not gated on [isKept]: the URI
     * here is the artist's, and the track it was reached from is long out of
     * scope by the time this is called.
     */
    fun rememberArtist(artistUri: String, raw: String) {
        val file = fileFor("artist", artistUri) ?: return
        runCatching { file.parentFile?.mkdirs(); file.writeText(raw) }
    }

    fun artist(artistUri: String): String? = recall("artist", artistUri)

    private fun remember(kind: String, uri: String, raw: String) {
        if (!kept(uri)) return
        val file = fileFor(kind, uri) ?: return
        runCatching { file.parentFile?.mkdirs(); file.writeText(raw) }
    }

    private fun recall(kind: String, uri: String): String? {
        val file = fileFor(kind, uri) ?: return null
        return runCatching { file.takeIf(File::exists)?.readText() }.getOrNull()
    }

    // -------------------------------------------------------------- the files

    /**
     * The local copy of an image or a Canvas, if there is one.
     *
     * Named after the URL rather than the track, because both are addressed
     * that way and the same cover is shared by every song on a record: keying
     * on the track would store one album's art a dozen times.
     */
    fun fileOf(url: String, kind: String): File? =
        if (kind == "art") artCache.computeIfAbsent(url) { Kept(look(kind, it)) }.value
        else look(kind, url)

    private fun look(kind: String, key: String): File? =
        fileFor(kind, key)?.takeIf(File::exists)

    /**
     * Answers about covers, remembered.
     *
     * This one is asked from inside composition — every artwork in every row
     * checks whether it has a local copy before deciding what to load — and the
     * answer costs a stat on the filesystem. On a list being flung that is disk
     * I/O on the frame's own thread, dozens of times a second, for an answer
     * that changes when a download finishes and at no other moment.
     *
     * A wrapper rather than a nullable value, because a map cannot remember
     * that the answer was "there is none".
     */
    private class Kept(val value: File?)

    private val artCache = java.util.concurrent.ConcurrentHashMap<String, Kept>()

    /** Called whenever a cover appears or goes, so the answers stay true. */
    private fun forgetArtAnswers() = artCache.clear()

    /**
     * Fetches and keeps one file. Answers with what is already there.
     *
     * Deliberately plain: a CDN GET with no headers, no client to configure and
     * no cache of its own. Everything this fetches is immutable and addressed
     * by content — Spotify's image and Canvas URLs name the file — so there is
     * nothing for a smarter client to be smarter about.
     */
    suspend fun keep(url: String, kind: String): File? = withContext(Dispatchers.IO) {
        if (url.isBlank()) return@withContext null
        val file = fileFor(kind, url) ?: return@withContext null
        if (file.exists() && file.length() > 0) return@withContext file

        runCatching {
            file.parentFile?.mkdirs()
            // Through a part file, so an interrupted fetch never leaves
            // something half-written where a reader would take it for whole.
            val part = File(file.parentFile, "${file.name}.part")
            URL(url).openStream().use { input ->
                part.outputStream().use(input::copyTo)
            }
            if (!part.renameTo(file)) {
                part.delete()
                return@runCatching null
            }
            if (kind == "art") forgetArtAnswers()
            file
        }.getOrNull()
    }

    /**
     * What a cover URL is really about, so every size of it lands on one file.
     *
     * A Spotify image id is forty hex characters: sixteen that say what size it
     * is, then twenty-four that say which picture. `ab67616d00001e02…` is the
     * 300px print and `ab67616d0000b273…` the 640px one, and everything after
     * those first sixteen is identical.
     *
     * Keyed on the whole URL, the row thumbnail and the player cover would be
     * two files for one picture, and neither would be found by code holding the
     * other spelling — which is exactly what happened: the covers were saved
     * under the large URL and looked up under the small one, so offline the
     * notification had no picture at all. Keyed on the tail, one download
     * answers for every size of it.
     */
    private fun coverKey(url: String): String {
        val id = url.substringAfterLast('/')
        return if (id.length == ID_LENGTH && id.all { it.isDigit() || it in 'a'..'f' }) {
            id.takeLast(ID_LENGTH - SIZE_PREFIX)
        } else {
            url
        }
    }

    /** A Spotify image id, and how much of the front of it is the size. */
    private const val ID_LENGTH = 40
    private const val SIZE_PREFIX = 16

    /**
     * The cover as a `file://` for anything that wants a URI rather than a
     * file — the media session's metadata, above all, which is what draws the
     * picture in the notification and the quick settings panel.
     */
    fun artworkUri(url: String?): android.net.Uri? {
        val kept = url?.let { fileOf(it, "art") } ?: return null
        return android.net.Uri.fromFile(kept)
    }

    /** Drops everything kept for a track that is no longer downloaded. */
    fun forget(trackUri: String) {
        listOf("lyrics", "canvas").forEach { kind ->
            fileFor(kind, trackUri)?.let { runCatching { it.delete() } }
        }
    }

    /**
     * What the extras take up, in bytes.
     *
     * Worth counting separately from the audio: the Canvases are video, and a
     * few hundred songs' worth of them is not a rounding error next to the
     * music. Walks the directory, so it belongs off the main thread.
     */
    fun bytes(): Long =
        root?.walkTopDown()?.filter(File::isFile)?.sumOf(File::length) ?: 0L

    /**
     * Deletes what belongs to songs that are no longer downloaded.
     *
     * The audio has an index to be reconciled against; the extras have only
     * their file names, which are hashes and cannot be read backwards. So the
     * set of names that *should* exist is rebuilt from the tracks that survive,
     * and anything else in the directory goes.
     *
     * It matters most for the Canvases. A cover is tens of kilobytes and a
     * Canvas is a few megabytes of video, so a library that has been added to
     * and removed from a few times leaves far more behind here than the index
     * ever accounted for — measured on the test phone at a hundred and thirty
     * megabytes for songs that were long gone.
     *
     * Artist descriptions are left alone: they are keyed by artist rather than
     * by track, they are a few kilobytes each, and an artist with nothing
     * downloaded today may well have something tomorrow.
     */
    fun sweep(trackUris: Collection<String>, coverUrls: Collection<String>) {
        if (root == null) return

        val keepLyrics = trackUris.mapNotNullTo(mutableSetOf()) { fileFor("lyrics", it)?.name }
        val keepCanvas = trackUris.mapNotNullTo(mutableSetOf()) { fileFor("canvas", it)?.name }
        val keepArt = coverUrls.mapNotNullTo(mutableSetOf()) { fileFor("art", it)?.name }

        // Read before the Canvas answers are pruned: a video is named after the
        // URL inside the answer that points at it, so the answers are the only
        // way to know which videos are still wanted.
        val keepVideo = trackUris.mapNotNullTo(mutableSetOf()) { uri ->
            recall("canvas", uri)
                ?.let { raw -> runCatching { JSONObject(raw).optString("url") }.getOrNull() }
                ?.takeIf { it.isNotBlank() }
                ?.let { fileFor("video", it)?.name }
        }

        forgetArtAnswers()
        prune("lyrics", keepLyrics)
        prune("canvas", keepCanvas)
        prune("art", keepArt)
        prune("video", keepVideo)
    }

    private fun prune(kind: String, keep: Set<String>) {
        val dir = root?.let { File(it, kind) } ?: return
        dir.listFiles()?.forEach { file ->
            if (file.name !in keep) runCatching { file.delete() }
        }
    }

    /** Everything goes, when the downloads do. */
    fun clear() {
        forgetArtAnswers()
        root?.let { runCatching { it.deleteRecursively() } }
    }

    private fun fileFor(kind: String, key: String): File? {
        val root = root ?: return null
        // Hashed rather than sanitised: a URI is not a legal file name, and any
        // escaping scheme would have to survive the characters it escapes.
        val name = (if (kind == "art") coverKey(key) else key).hashCode().toUInt().toString(16)
        val extension = when (kind) {
            "art" -> "jpg"
            "video" -> "mp4"
            else -> "json"
        }
        return File(File(root, kind), "$name.$extension")
    }
}
