package dev.lelonio.square.data.local

import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract as D
import android.provider.MediaStore
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** Reads authorized document trees only; no filesystem traversal, writes or audio copies. */
class LocalSafIndexer(private val context: Context) {
    data class Result(val tracks: List<LocalTrack>, val folders: List<LocalMusicFolder>)
    suspend fun scan(folders: List<LocalMusicFolder>, previous: List<LocalTrack>, force: Boolean,
                     enrich: (LocalTrack) -> LocalTrack): Result {
        val resolver = context.contentResolver
        val tracks = mutableListOf<LocalTrack>()
        val old = previous.associateBy { it.documentKey }
        val metadata = mutableMapOf<String, LocalTrack>()
        val grants = resolver.persistedUriPermissions.filter { it.isReadPermission }.map { it.uri.toString() }.toSet()
        val checked = folders.map { folder ->
            val found = mutableListOf<LocalTrack>()
            try {
                check(folder.uri in grants)
                val tree = Uri.parse(folder.uri)
                val pending = ArrayDeque<Pair<String, String>>()
                val root = D.getTreeDocumentId(tree)
                pending.add(root to folder.name)
                val visited = mutableSetOf<String>()
                while (pending.isNotEmpty()) {
                    currentCoroutineContext().ensureActive()
                    val (parent, path) = pending.removeFirst()
                    if (!visited.add(parent)) continue
                    val children = D.buildChildDocumentsUriUsingTree(tree, parent)
                    resolver.query(children, arrayOf(D.Document.COLUMN_DOCUMENT_ID, D.Document.COLUMN_DISPLAY_NAME,
                        D.Document.COLUMN_MIME_TYPE, D.Document.COLUMN_SIZE, D.Document.COLUMN_LAST_MODIFIED), null, null, null)?.use { c ->
                        while (c.moveToNext()) {
                            currentCoroutineContext().ensureActive()
                            val id = c.getString(0); val name = c.getString(1).orEmpty(); val mime = c.getString(2).orEmpty()
                            if (mime == D.Document.MIME_TYPE_DIR) { pending.add(id to "$path/$name"); continue }
                            if (!mime.startsWith("audio/") && name.substringAfterLast('.', "").lowercase() !in setOf("mp3","flac","m4a","aac","ogg","opus","wav","aiff","alac")) continue
                            val uri = D.buildDocumentUriUsingTree(tree, id)
                            val identity = LocalFolderPolicy.documentKey(tree.authority.orEmpty(), id)
                            val storage = if (tree.authority == "com.android.externalstorage.documents" && ':' in id)
                                LocalFolderPolicy.storageKey(id.substringBefore(':'), id.substringAfter(':')) else ""
                            val mediaId = if (Build.VERSION.SDK_INT >= 29) runCatching { MediaStore.getMediaUri(context, uri)?.lastPathSegment?.toLongOrNull() }.getOrNull() else null
                            val previousTrack = old[identity] ?: previous.firstOrNull { storage.isNotBlank() && it.storageKey == storage }
                            val size = c.getLong(3); val modified = c.getLong(4) / 1_000
                            val row = LocalTrack(id = previousTrack?.id ?: mediaId ?: LocalFolderPolicy.safId(identity), contentUri = uri.toString(),
                                title = name.substringBeforeLast('.'), fileName = name, folder = path, mimeType = mime,
                                size = size, modifiedSeconds = modified, addedSeconds = previousTrack?.addedSeconds ?: 0,
                                storageKey = storage, documentKey = identity, treeUris = listOf(folder.uri), mediaReferenceId = mediaId,
                                rowSignature = localKey("row", "$identity|$name|$size|$modified"))
                            val unchanged = previousTrack != null && previousTrack.size == size && previousTrack.modifiedSeconds == modified
                            val cached = metadata[identity]
                            val enriched = if (cached != null) cached.copy(treeUris = row.treeUris) else if (unchanged && !force) previousTrack!!.copy(contentUri = row.contentUri, documentKey = identity,
                                storageKey = storage, treeUris = row.treeUris, available = true, mediaReferenceId = mediaId) else enrich(row).copy(artRevision = if (force) System.currentTimeMillis() else 0)
                            metadata[identity] = enriched
                            found += enriched
                        }
                    } ?: error("Document tree unavailable")
                }
                tracks += found
                folder.copy(accessible = true)
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                tracks += previous.filter { folder.uri in it.treeUris }.map { it.copy(available = false) }
                folder.copy(accessible = false)
            }
        }
        return Result(tracks, checked)
    }
}
