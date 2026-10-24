package dev.lelonio.square.data.local

import android.content.ContentUris
import android.content.Context
import android.media.MediaMetadataRetriever
import android.os.Build
import android.provider.MediaStore
import dev.lelonio.square.data.LocalLibrary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/** Only changed audio files are opened. MediaStore supplies the list, including removals. */
class LocalLibraryIndexer(private val context: Context) {
    suspend fun scan(previous: List<LocalTrack>, force: Boolean, folders: List<LocalMusicFolder> = emptyList(),
                     progress: (Int, Int) -> Unit): LocalSafIndexer.Result = withContext(Dispatchers.IO) {
        val mediaAllowed = LocalLibrary.granted(context)
        val collection = if (Build.VERSION.SDK_INT >= 29) MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
            else MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val fields = mutableListOf("_id", "title", "artist", "album", "album_id", "duration", "date_added", "date_modified", "_size", "_display_name", "mime_type", "track", "year", "_data")
        if (Build.VERSION.SDK_INT >= 29) fields += listOf("relative_path", "volume_name")
        if (Build.VERSION.SDK_INT >= 30) fields += "generation_modified"
        val selection = buildString {
            append("is_ringtone=0 AND is_notification=0 AND is_alarm=0 AND is_podcast=0")
            if (Build.VERSION.SDK_INT >= 29) append(" AND is_audiobook=0")
            if (Build.VERSION.SDK_INT >= 31) append(" AND is_recording=0")
        }
        val rows: List<LocalTrack> = if (!mediaAllowed) emptyList() else context.contentResolver.query(collection, fields.toTypedArray(), selection, null, "date_added DESC")?.use { c ->
            fun text(name: String): String = c.getColumnIndex(name).takeIf { it >= 0 }?.let { c.getString(it) }.orEmpty().takeUnless { it == MediaStore.UNKNOWN_STRING }.orEmpty()
            fun number(name: String): Long = c.getColumnIndex(name).takeIf { it >= 0 }?.let { c.getLong(it) } ?: 0L
            buildList<LocalTrack> {
                while (c.moveToNext()) {
                    currentCoroutineContext().ensureActive()
                    val id = number("_id")
                    val file = text("_data")
                    val relative = text("relative_path").trim('/')
                    val folder = if (relative.isNotBlank()) text("volume_name").ifBlank { "external" } + "/" + relative
                        else file.substringBeforeLast('/', "").trimStart('/')
                    val encodedTrack = number("track").toInt()
                    add(LocalTrack(id = id, contentUri = ContentUris.withAppendedId(collection, id).toString(),
                        title = text("title"), artist = text("artist"), album = text("album"), mediaAlbumId = number("album_id"),
                        durationMs = number("duration"), fileName = text("_display_name"), folder = folder, filePath = file.takeIf(String::isNotBlank),
                        size = number("_size"), mimeType = text("mime_type"), addedSeconds = number("date_added"), modifiedSeconds = number("date_modified"),
                        storageKey = if (relative.isNotBlank()) LocalFolderPolicy.storageKey(text("volume_name"), relative + "/" + text("_display_name"))
                            else if (file.startsWith("/storage/emulated/0/")) LocalFolderPolicy.storageKey("primary", file.removePrefix("/storage/emulated/0/")) else file,
                        generation = number("generation_modified"), track = encodedTrack % 1000, disc = encodedTrack / 1000,
                        year = text("year").takeUnless { it == "0" }.orEmpty(),
                        rowSignature = localKey("row", listOf(text("title"), text("artist"), text("album"), text("year"),
                            text("_data"), text("_display_name"), text("mime_type"), number("track").toString(), number("duration").toString(), number("album_id").toString()).joinToString("\u0000"))))
                }
            }
        } ?: error("MediaStore query unavailable")
        val artRevision = if (force) System.currentTimeMillis() else 0L
        val media = updateLocalIndex(previous, rows, force, { enrich(it).copy(artRevision = artRevision) }, progress)
        val documents = LocalSafIndexer(context).scan(folders, previous, force, ::enrich)
        val inaccessibleMedia = if (!mediaAllowed) previous.filter { it.documentKey == null }.map { it.copy(available = false) } else emptyList()
        LocalSafIndexer.Result(LocalFolderPolicy.merge(media + inaccessibleMedia, documents.tracks, previous), documents.folders)
    }

    private fun enrich(row: LocalTrack): LocalTrack {
        val reader = MediaMetadataRetriever()
        return try {
            reader.setDataSource(context, android.net.Uri.parse(row.contentUri))
            fun tag(key: Int) = reader.extractMetadata(key)?.trim().orEmpty()
            fun part(key: Int) = tag(key).substringBefore('/').toIntOrNull()
            row.copy(
                title = tag(MediaMetadataRetriever.METADATA_KEY_TITLE).ifBlank { row.title.ifBlank { row.fileName } },
                artist = tag(MediaMetadataRetriever.METADATA_KEY_ARTIST).ifBlank { row.artist },
                album = tag(MediaMetadataRetriever.METADATA_KEY_ALBUM).ifBlank { row.album },
                albumArtist = tag(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST),
                genre = tag(MediaMetadataRetriever.METADATA_KEY_GENRE),
                track = part(MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER) ?: row.track,
                disc = part(MediaMetadataRetriever.METADATA_KEY_DISC_NUMBER) ?: row.disc,
                year = tag(MediaMetadataRetriever.METADATA_KEY_YEAR).ifBlank { tag(MediaMetadataRetriever.METADATA_KEY_DATE).take(4) }.ifBlank { row.year },
                durationMs = tag(MediaMetadataRetriever.METADATA_KEY_DURATION).toLongOrNull() ?: row.durationMs,
            )
        } catch (_: Exception) {
            // A malformed tag must not remove a readable audio file from the collection.
            row.copy(title = row.title.ifBlank { row.fileName })
        } finally { runCatching { reader.release() } }
    }
}
