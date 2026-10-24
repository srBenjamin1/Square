package dev.lelonio.square.data.local

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import coil.ImageLoader
import coil.decode.DataSource
import coil.decode.ImageSource
import coil.fetch.Fetcher
import coil.fetch.SourceResult
import coil.request.Options
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okio.buffer
import okio.source
import java.io.File

/** Embedded artwork is opened lazily by the same Coil loader used throughout Square. */
class LocalArtworkRepository(private val context: Context, private val music: LocalMusicRepository) {
    private val lock = Mutex()
    private val directory = File(context.cacheDir, "local-music-art")
    private val maxPicture = 20 * 1024 * 1024
    val factory = Fetcher.Factory<Uri> { data, options, _ ->
        if (data.scheme != "squarelocalart") null else Fetcher {
            withContext(Dispatchers.IO) {
                val file = artwork(data) ?: return@withContext null
                SourceResult(ImageSource(file.source().buffer(), options.context), null, DataSource.DISK)
            }
        }
    }

    private suspend fun artwork(uri: Uri): File? = lock.withLock {
        val key = localKey("art", uri.toString()).substringAfterLast(':')
        directory.mkdirs()
        val file = File(directory, key)
        if (file.isFile) { file.setLastModified(System.currentTimeMillis()); return@withLock file }
        val miss = File(directory, "$key.missing")
        if (miss.isFile) return@withLock null
        val catalog = music.awaitCatalog()
        val track = catalog.allByUri["local:track:${uri.host}"] ?: return@withLock null
        val embedded = picture(track)
        val sidecar = if (embedded == null) track.filePath?.let { path ->
            listOf("cover.jpg", "folder.jpg", "cover.png", "Folder.jpg", "Cover.jpg").firstNotNullOfOrNull { name ->
                runCatching { File(File(path).parentFile, name).takeIf { it.isFile && it.length() in 1..maxPicture.toLong() }?.readBytes() }.getOrNull()
            }
        } else null
        val other = if (embedded == null && sidecar == null && track.album.isNotBlank()) catalog.albumByUri[track.albumUri]?.tracks
            ?.asSequence()?.filterNot { it.id == track.id }?.mapNotNull(::picture)?.firstOrNull() else null
        val albumStore = if (embedded == null && sidecar == null && other == null && track.mediaAlbumId > 0) runCatching {
            context.contentResolver.openInputStream(Uri.parse("content://media/external/audio/albumart/${track.mediaAlbumId}"))?.use { stream ->
                readBounded(stream)
            }
        }.getOrNull() else null
        val bytes = embedded ?: sidecar ?: other ?: albumStore
        if (bytes == null) { miss.createNewFile(); trim(); return@withLock null }
        val temporary = File(directory, "$key.tmp")
        temporary.writeBytes(bytes)
        if (!temporary.renameTo(file)) { temporary.delete(); return@withLock null }
        trim()
        file
    }
    private fun picture(track: LocalTrack): ByteArray? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, Uri.parse(track.contentUri))
            retriever.embeddedPicture?.takeIf { it.size <= maxPicture }
        } catch (_: Exception) { null } finally { runCatching { retriever.release() } }
    }
    private fun readBounded(stream: java.io.InputStream): ByteArray? {
        val output = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(16 * 1024)
        while (true) {
            val count = stream.read(buffer)
            if (count < 0) return output.toByteArray().takeIf { it.isNotEmpty() }
            if (output.size() + count > maxPicture) return null
            output.write(buffer, 0, count)
        }
    }
    private fun trim() {
        val files = directory.listFiles().orEmpty().sortedBy { it.lastModified() }
        var bytes = files.sumOf { it.length() }
        var count = files.size
        for (file in files) {
            if (bytes <= 128L * 1024 * 1024 && count <= 4_000) break
            bytes -= file.length(); count--; file.delete()
        }
    }

    /** Optional, explicit association only. Images use existing catalog URLs and HTTP client. */
    suspend fun cacheVisual(visual: LocalArtistVisual, client: okhttp3.OkHttpClient): LocalArtistVisual = withContext(Dispatchers.IO) {
        val folder = File(context.filesDir, "local-music/artists").apply { mkdirs() }
        fun save(url: String?, kind: String): String? {
            if (url == null || !url.startsWith("https://")) return null
            return runCatching {
                // Concurrent associations must never overwrite another identity's cached portrait.
                val identity = localKey("artist-art", visual.remoteUri + "|" + url).substringAfterLast(':')
                val file = File(folder, visual.artistUri.substringAfterLast(':') + "-$identity-$kind")
                fun keep(bytes: ByteArray): String? {
                    val options = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
                    if (options.outWidth <= 0 || options.outHeight <= 0) return null
                    val temporary = File(folder, file.name + ".tmp")
                    temporary.writeBytes(bytes)
                    if (!temporary.renameTo(file)) { temporary.delete(); return null }
                    return file.absolutePath
                }
                val cached = coil.Coil.imageLoader(context).diskCache?.openSnapshot(url)?.use { snapshot ->
                    snapshot.data.toFile().inputStream().use(::readBounded)
                }
                if (cached != null) return@runCatching keep(cached)
                if (dev.lelonio.square.playback.OfflineMode.active.value) return@runCatching null
                client.newCall(okhttp3.Request.Builder().url(url).build()).execute().use { response ->
                    if (!response.isSuccessful) return@use null
                    val body = response.body ?: return@use null
                    val bytes = body.byteStream().use(::readBounded) ?: return@use null
                    keep(bytes)
                }
            }.getOrNull()
        }
        visual.copy(heroFile = save(visual.heroUrl, "hero"), logoFile = save(visual.logoUrl, "logo"))
    }
}
