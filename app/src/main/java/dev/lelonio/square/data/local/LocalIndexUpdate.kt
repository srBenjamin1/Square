package dev.lelonio.square.data.local

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** Shared incremental policy; Android only supplies rows and reads changed file tags. */
internal suspend fun updateLocalIndex(
    previous: List<LocalTrack>,
    rows: List<LocalTrack>,
    force: Boolean,
    enrich: suspend (LocalTrack) -> LocalTrack,
    progress: (Int, Int) -> Unit = { _, _ -> },
): List<LocalTrack> {
    val old = previous.associateBy { it.id }
    val unique = rows.distinctBy { it.id }
    return unique.mapIndexed { index, row ->
        currentCoroutineContext().ensureActive()
        val cached = old[row.id]?.takeIf { !force && it.fingerprint == row.fingerprint &&
            (it.contentUri == row.contentUri || it.storageKey.isNotBlank() && it.storageKey == row.storageKey) }
        val track = cached?.copy(contentUri = row.contentUri, storageKey = row.storageKey,
            documentKey = row.documentKey, treeUris = row.treeUris, available = row.available) ?: enrich(row)
        progress(index + 1, unique.size)
        track
    }
}
